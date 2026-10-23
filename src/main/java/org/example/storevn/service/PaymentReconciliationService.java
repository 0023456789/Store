package org.example.storevn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.storevn.entity.PaymentAttempt;
import org.example.storevn.enums.PaymentEventSource;
import org.example.storevn.enums.PaymentStatus;
import org.example.storevn.repository.PaymentAttemptRepository;
import org.example.storevn.vnpay.VnpayProperties;
import org.example.storevn.vnpay.VnpayQueryClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
/** Đối soát giao dịch đang chờ bằng QueryDR khi IPN chậm hoặc thất lạc. */
public class PaymentReconciliationService {

    private final PaymentAttemptRepository attempts;
    private final PaymentLogService logs;
    private final VnpayProperties properties;
    private final VnpayQueryClient queryClient;
    private final PaymentService paymentService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${vnpay.reconcile-interval-ms:60000}")
    public void reconcilePendingPayments() {
        // Chỉ chạy khi đủ cấu hình; giới hạn 100 lượt mỗi nhịp để tránh chiếm tài nguyên kéo dài.
        if (!properties.isReconcileEnabled() || properties.getTmnCode().isBlank()
                || properties.getHashSecret().isBlank() || properties.getServerIp().isBlank()) return;

        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.getReconcileGraceMinutes(), ChronoUnit.MINUTES);
        var candidates = attempts.findAll(reconciliationCandidates(PaymentStatus.PENDING, cutoff, now),
                PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "createdAt"))).getContent();
        for (PaymentAttempt candidate : candidates) {
            if (!claim(candidate, now)) continue;
            reconcile(candidate.getTxnRef());
        }
    }

    private Specification<PaymentAttempt> reconciliationCandidates(PaymentStatus status, Instant cutoff, Instant now) {
        // Chỉ lấy lượt đã chờ qua thời gian đệm, đến hạn thử lại và không bị tiến trình khác giữ lease.
        return (root, query, criteria) -> criteria.and(
                criteria.equal(root.get("status"), status),
                criteria.lessThan(root.<Instant>get("createdAt"), cutoff),
                criteria.or(criteria.isNull(root.get("reconcileNextAttemptAt")),
                        criteria.lessThanOrEqualTo(root.<Instant>get("reconcileNextAttemptAt"), now)),
                criteria.or(criteria.isNull(root.get("reconcileClaimedUntil")),
                        criteria.lessThanOrEqualTo(root.<Instant>get("reconcileClaimedUntil"), now)));
    }

    private boolean claim(PaymentAttempt attempt, Instant now) {
        // Cập nhật claim nguyên tử để nhiều instance ứng dụng không cùng gọi QueryDR cho một lượt.
        // Khoảng chờ tăng dần, tối đa 30 phút, nhằm giảm gọi lặp khi VNPAY đang lỗi.
        int retry = Math.min(attempt.getReconcileAttemptCount(), 5);
        long delayMinutes = Math.min(30L, 1L << retry);
        return attempts.claimForReconciliation(attempt.getId(), PaymentStatus.PENDING, now,
                now.plusSeconds(45), now.plus(delayMinutes, ChronoUnit.MINUTES)) == 1;
    }

    private void reconcile(String txnRef) {
        // Gọi mạng ngoài transaction cập nhật trạng thái; lỗi kết nối không giữ khóa bản ghi lâu.
        PaymentAttempt attempt = attempts.findByTxnRef(txnRef).orElse(null);
        if (attempt == null || attempt.getStatus() != PaymentStatus.PENDING) return;

        try {
            String requestJson = queryClient.buildRequest(attempt);
            logs.log(PaymentEventSource.QUERYDR, txnRef, null, attempt.getAmount(),
                    null, null, true, "REQUEST", requestJson);
            VnpayQueryClient.QueryResult result = queryClient.query(requestJson);
            Map<String, String> response = result.response().properties().stream().collect(
                    java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().asText(), (a, b) -> b));
            String outcome = result.signatureValid()
                    ? paymentService.processQueryDr(response)
                    : "INVALID_RESPONSE_SIGNATURE";
            logs.log(PaymentEventSource.QUERYDR, txnRef,
                    response.get("vnp_TransactionNo"), parseAmount(response.get("vnp_Amount")),
                    response.get("vnp_ResponseCode"), response.get("vnp_BankCode"),
                    result.signatureValid(), response.get("vnp_ResponseCode"),
                    toJson(Map.of("outcome", outcome, "response", result.response())));
        } catch (Exception e) {
            log.warn("VNPAY QueryDR failed for txnRef {}: {}", txnRef, e.getMessage());
            logs.log(PaymentEventSource.QUERYDR, txnRef, null, null, null, null,
                    false, "99", toJson(Map.of("error", String.valueOf(e.getMessage()))));
        }
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private static Long parseAmount(String value) {
        try { return value == null ? null : Long.parseLong(value) / 100; }
        catch (NumberFormatException e) { return null; }
    }
}
