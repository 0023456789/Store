package org.example.storevn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.storevn.entity.PaymentAttempt;
import org.example.storevn.enums.PaymentEventSource;
import org.example.storevn.enums.PaymentStatus;
import org.example.storevn.repository.PaymentAttemptRepository;
import org.example.storevn.vnpay.VnpayProperties;
import org.example.storevn.vnpay.VnpayQueryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");

    @Mock private PaymentAttemptRepository attempts;
    @Mock private PaymentLogService logs;
    @Mock private VnpayQueryClient queryClient;
    @Mock private PaymentService paymentService;

    private VnpayProperties properties;
    private ObjectMapper objectMapper;
    private PaymentReconciliationService service;

    @BeforeEach
    void setUp() {
        properties = new VnpayProperties();
        properties.setTmnCode("TMN");
        properties.setHashSecret("secret");
        properties.setServerIp("203.0.113.10");
        objectMapper = new ObjectMapper();
        service = new PaymentReconciliationService(attempts, logs, properties, queryClient,
                paymentService, Clock.fixed(NOW, ZoneOffset.UTC), objectMapper);
    }

    @Test
    void reconcilePendingPayments_doesNothingWhenDisabledOrUnconfigured() {
        properties.setReconcileEnabled(false);
        service.reconcilePendingPayments();
        verifyNoInteractions(attempts, logs, queryClient, paymentService);

        properties.setReconcileEnabled(true);
        properties.setServerIp("");
        service.reconcilePendingPayments();
        verifyNoInteractions(attempts, logs, queryClient, paymentService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconcilePendingPayments_claimsCandidateQueriesVnpayAndAppliesSignedResponse() throws Exception {
        UUID attemptId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(attemptId).txnRef("TXN-41").amount(89000L)
                .vnpCreateDate("20261021100000").status(PaymentStatus.PENDING)
                .build();
        when(attempts.findAll(ArgumentMatchers.<Specification<PaymentAttempt>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(attempt)));
        when(attempts.claimForReconciliation(eq(attemptId), eq(PaymentStatus.PENDING), eq(NOW),
                eq(NOW.plusSeconds(45)), eq(NOW.plusSeconds(60)))).thenReturn(1);
        when(attempts.findByTxnRef("TXN-41")).thenReturn(Optional.of(attempt));
        when(queryClient.buildRequest(attempt)).thenReturn("request-json");
        var response = objectMapper.readTree("""
                {"vnp_TxnRef":"TXN-41","vnp_Amount":"8900000","vnp_ResponseCode":"00",
                 "vnp_BankCode":"NCB","vnp_TransactionNo":"VN-41"}
                """);
        when(queryClient.query("request-json"))
                .thenReturn(new VnpayQueryClient.QueryResult("response-json", response, true));
        when(paymentService.processQueryDr(any())).thenReturn("QUERYDR_APPLIED_SUCCESS");

        service.reconcilePendingPayments();

        verify(attempts).findAll(ArgumentMatchers.<Specification<PaymentAttempt>>any(),
                eq(PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "createdAt"))));
        verify(attempts).claimForReconciliation(eq(attemptId), eq(PaymentStatus.PENDING), eq(NOW),
                eq(NOW.plusSeconds(45)), eq(NOW.plusSeconds(60)));
        verify(paymentService).processQueryDr(argThat(params -> "TXN-41".equals(params.get("vnp_TxnRef"))));
        verify(logs).log(eq(PaymentEventSource.QUERYDR), eq("TXN-41"), isNull(), eq(89000L),
                isNull(), isNull(), eq(true), eq("REQUEST"), eq("request-json"));
        verify(logs).log(eq(PaymentEventSource.QUERYDR), eq("TXN-41"), eq("VN-41"), eq(89000L),
                eq("00"), eq("NCB"), eq(true), eq("00"), contains("QUERYDR_APPLIED_SUCCESS"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconcilePendingPayments_doesNotApplyResponseWithInvalidSignature() throws Exception {
        UUID attemptId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(attemptId).txnRef("TXN-42").amount(89000L).status(PaymentStatus.PENDING)
                .build();
        when(attempts.findAll(ArgumentMatchers.<Specification<PaymentAttempt>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(attempt)));
        when(attempts.claimForReconciliation(eq(attemptId), eq(PaymentStatus.PENDING), eq(NOW),
                eq(NOW.plusSeconds(45)), eq(NOW.plusSeconds(60)))).thenReturn(1);
        when(attempts.findByTxnRef("TXN-42")).thenReturn(Optional.of(attempt));
        when(queryClient.buildRequest(attempt)).thenReturn("request-json");
        when(queryClient.query("request-json")).thenReturn(new VnpayQueryClient.QueryResult(
                "response-json", objectMapper.readTree("{\"vnp_TxnRef\":\"TXN-42\"}"), false));

        service.reconcilePendingPayments();

        verify(paymentService, never()).processQueryDr(any());
        verify(logs).log(eq(PaymentEventSource.QUERYDR), eq("TXN-42"), isNull(), eq(89000L),
                isNull(), isNull(), eq(true), eq("REQUEST"), eq("request-json"));
        verify(logs).log(eq(PaymentEventSource.QUERYDR), eq("TXN-42"), isNull(), isNull(),
                isNull(), isNull(), eq(false), isNull(), contains("INVALID_RESPONSE_SIGNATURE"));
    }
}
