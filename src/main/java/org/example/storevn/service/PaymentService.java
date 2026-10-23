package org.example.storevn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.storevn.dto.request.CreatePaymentRequest;
import org.example.storevn.dto.response.CreatePaymentResponse;
import org.example.storevn.dto.response.VnpayIpnResponse;
import org.example.storevn.entity.PaymentAttempt;
import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;
import org.example.storevn.enums.PaymentConfirmedVia;
import org.example.storevn.enums.PaymentEventSource;
import org.example.storevn.enums.PaymentStatus;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.example.storevn.exception.OrderExpiredException;
import org.example.storevn.repository.PaymentAttemptRepository;
import org.example.storevn.repository.StoreOrderRepository;
import org.example.storevn.vnpay.OrderInfoSanitizer;
import org.example.storevn.vnpay.VnpayProperties;
import org.example.storevn.vnpay.VnpaySigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class PaymentService {

    private static final ZoneId GMT7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StoreOrderRepository storeOrderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentLogService paymentLogService;
    private final VnpayProperties vnpayProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String publicBaseUrl;
    private final String webUrl;

    public PaymentService(
            StoreOrderRepository storeOrderRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentLogService paymentLogService,
            VnpayProperties vnpayProperties,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${storefront.public-base-url:http://localhost:8081}") String publicBaseUrl,
            @Value("${storefront.web-url:http://localhost:5173}") String webUrl) {
        this.storeOrderRepository = storeOrderRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentLogService = paymentLogService;
        this.vnpayProperties = vnpayProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
        this.webUrl = webUrl;
    }

    /**
     * Tạo lượt thanh toán, dựng URL và ký dữ liệu trước khi chuyển khách sang VNPAY.
     * Mỗi lần thử có txnRef riêng để có thể đối soát và thử lại độc lập.
     */
    @Transactional(noRollbackFor = OrderExpiredException.class)
    public CreatePaymentResponse createPayment(String orderCode, CreatePaymentRequest request, String clientIp) {
        StoreOrder order = storeOrderRepository.findLockedByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_ALREADY_PAID);
        }

        if (order.getStatus() == OrderStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.ORDER_CANNOT_PAY);
        }

        Instant paymentNow = clock.instant();
        if (order.getStatus() == OrderStatus.EXPIRED || !paymentNow.isBefore(order.getExpiresAt())) {
            if (order.getStatus() != OrderStatus.EXPIRED) {
                order.setStatus(OrderStatus.EXPIRED);
            }
            throw new OrderExpiredException();
        }

        // Đơn bị hủy/thất bại được phép thử lại; lượt thanh toán cũ vẫn được giữ để đối soát.
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FAILED) {
            order.setStatus(OrderStatus.PENDING_PAYMENT);
        } else if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.ORDER_CANNOT_PAY);
        }

        if (paymentAttemptRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING).isPresent()) {
            throw new AppException(ErrorCode.ORDER_CANNOT_PAY);
        }

        long attemptNo = paymentAttemptRepository.countByOrderId(order.getId()) + 1;
        String txnRef = String.format("%s%02d", order.getOrderCode(), attemptNo);
        while (paymentAttemptRepository.existsByTxnRef(txnRef)) {
            attemptNo++;
            txnRef = String.format("%s%02d", order.getOrderCode(), attemptNo);
        }
        //
        ZonedDateTime now = paymentNow.atZone(GMT7);
        String createDate = now.format(DATE_FORMATTER);
        Instant vnpExpireAt = paymentNow.plus(15, ChronoUnit.MINUTES);
        if (order.getExpiresAt().isBefore(vnpExpireAt)) {
            vnpExpireAt = order.getExpiresAt();
        }
        String expireDate = vnpExpireAt.atZone(GMT7).format(DATE_FORMATTER);

        String ipAddr = clientIp;
        if (ipAddr == null || ipAddr.isBlank() || "0:0:0:0:0:0:0:1".equals(ipAddr) || "localhost".equalsIgnoreCase(ipAddr)) {
            ipAddr = "127.0.0.1";
        }

        String baseUrl = publicBaseUrl != null ? publicBaseUrl.trim() : "";
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) ? "http://" + baseUrl : "https://" + baseUrl;
        }
        String returnUrl = baseUrl + "/api/v1/payments/vnpay/return";
        String orderInfo = OrderInfoSanitizer.sanitize("Thanh toan goi cuoc " + order.getPlanCode() + " don " + order.getOrderCode());

        if (vnpayProperties.getHashSecret() == null || vnpayProperties.getHashSecret().isBlank()
                || vnpayProperties.getTmnCode() == null || vnpayProperties.getTmnCode().isBlank()) {
            throw new AppException(ErrorCode.VNPAY_CONFIG_MISSING);
        }

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", vnpayProperties.getVersion());
        params.put("vnp_Command", vnpayProperties.getCommand());
        params.put("vnp_TmnCode", vnpayProperties.getTmnCode());
        params.put("vnp_Amount", String.valueOf(order.getAmount() * 100));
        params.put("vnp_CurrCode", vnpayProperties.getCurrCode());
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", vnpayProperties.getDefaultOrderType());
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddr);
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", expireDate);

        if (request != null && request.getBankCode() != null && !request.getBankCode().isBlank()) {
            params.put("vnp_BankCode", request.getBankCode().trim());
        }

        String hashData = VnpaySigner.buildHashData(params);
        String secureHash = VnpaySigner.hmacSHA512(vnpayProperties.getHashSecret(), hashData);
        String paymentUrl = vnpayProperties.getPayUrl() + "?" + hashData + "&vnp_SecureHash=" + secureHash;

        PaymentAttempt attempt = PaymentAttempt.builder()
                .order(order)
                .txnRef(txnRef)
                .amount(order.getAmount())
                .vnpCreateDate(createDate)
                .vnpExpireDate(expireDate)
                .status(PaymentStatus.PENDING)
                .vnpBankCode(request != null ? request.getBankCode() : null)
                .build();
        paymentAttemptRepository.save(attempt);

        log.info("Created payment attempt {} for order {} with amount {}", txnRef, orderCode, order.getAmount());
        return CreatePaymentResponse.builder()
                .txnRef(txnRef)
                .paymentUrl(paymentUrl)
                .build();
    }

    /**
     * Xử lý IPN phía máy chủ. Đây là callback chính thức dùng để cập nhật trạng thái thanh toán.
     */
    @Transactional
    public VnpayIpnResponse processIpn(Map<String, String> params) {
        String payloadJson = toJson(params);
        String txnRef = params.get("vnp_TxnRef");
        String secureHash = params.get("vnp_SecureHash");
        String vnpTxnNo = params.get("vnp_TransactionNo");
        Long vnpAmount = params.get("vnp_Amount") != null ? parseAmount(params.get("vnp_Amount")) / 100 : null;
        String vnpResponseCode = params.get("vnp_ResponseCode");
        String vnpBankCode = params.get("vnp_BankCode");

        // Bước 1: chỉ xử lý dữ liệu có chữ ký hợp lệ.
        boolean signatureValid = VnpaySigner.verify(params, vnpayProperties.getHashSecret(), secureHash);
        if (!signatureValid) {
            logIpnResponse(txnRef, vnpTxnNo, vnpAmount, vnpResponseCode, vnpBankCode,
                    false, VnpayIpnResponse.invalidSignature(), payloadJson);
            log.warn("IPN failed: Invalid signature for txnRef {}", txnRef);
            return VnpayIpnResponse.invalidSignature();
        }

        // Bước 2: khóa lượt thanh toán để hai IPN cùng txnRef không cập nhật đồng thời.
        Optional<PaymentAttempt> attemptOpt = (txnRef != null)
                ? paymentAttemptRepository.findLockedByTxnRef(txnRef)
                : Optional.empty();

        if (attemptOpt.isEmpty() || !vnpayProperties.getTmnCode().equals(params.get("vnp_TmnCode"))) {
            logIpnResponse(txnRef, vnpTxnNo, vnpAmount, vnpResponseCode, vnpBankCode,
                    true, VnpayIpnResponse.orderNotFound(), payloadJson);
            log.warn("IPN failed: TxnRef {} not found or TMN mismatch", txnRef);
            return VnpayIpnResponse.orderNotFound();
        }

        PaymentAttempt attempt = attemptOpt.get();
        StoreOrder order = attempt.getOrder();

        // Bước 3: đối chiếu số tiền VNPAY (đơn vị xu) với số tiền lưu theo VND.
        if (vnpAmount == null || vnpAmount.longValue() != attempt.getAmount().longValue()) {
            logIpnResponse(txnRef, vnpTxnNo, vnpAmount, vnpResponseCode, vnpBankCode,
                    true, VnpayIpnResponse.invalidAmount(), payloadJson);
            log.warn("IPN failed: Amount mismatch for txnRef {}. Expected {}, got {}", txnRef, attempt.getAmount(), vnpAmount);
            return VnpayIpnResponse.invalidAmount();
        }

        String transactionStatus = params.get("vnp_TransactionStatus");

        // IPN có thể gửi lặp; trạng thái cuối rồi thì không áp dụng kết quả lần nữa.
        if (attempt.getStatus() != PaymentStatus.PENDING) {
            logIpnResponse(txnRef, vnpTxnNo, vnpAmount, vnpResponseCode, vnpBankCode,
                    true, VnpayIpnResponse.alreadyConfirmed(), payloadJson);
            log.info("IPN: Attempt {} already confirmed with status {}. Idempotent bypass.", txnRef, attempt.getStatus());
            return VnpayIpnResponse.alreadyConfirmed();
        }

        // Bước 5: lưu thông tin VNPAY, chuyển trạng thái và ghi dấu vết IPN.
        attempt.setVnpResponseCode(vnpResponseCode);
        attempt.setVnpTransactionStatus(transactionStatus);
        attempt.setVnpTransactionNo(vnpTxnNo);
        attempt.setVnpBankCode(vnpBankCode);
        attempt.setVnpCardType(params.get("vnp_CardType"));
        attempt.setVnpPayDate(params.get("vnp_PayDate"));

        applyIpnResult(order, attempt, vnpResponseCode, transactionStatus);

        logIpnResponse(txnRef, vnpTxnNo, vnpAmount, vnpResponseCode, vnpBankCode,
                true, VnpayIpnResponse.success(), payloadJson);
        return VnpayIpnResponse.success();
    }

    private void logIpnResponse(String txnRef, String vnpTxnNo, Long amount, String vnpResponseCode,
                                String vnpBankCode, boolean signatureValid, VnpayIpnResponse response,
                                String payloadJson) {
        paymentLogService.logIndependent(PaymentEventSource.IPN, txnRef, vnpTxnNo, amount,
                vnpResponseCode, vnpBankCode, signatureValid, response.getRspCode(),
                response.getMessage(), payloadJson);
    }

    /**
     * Return URL chỉ phục vụ trình duyệt hiển thị kết quả; không dùng nó để xác nhận thanh toán.
     * IPN và QueryDR là nguồn server-to-server cập nhật trạng thái; Return chỉ điều hướng trình duyệt.
     */
    @Transactional
    public String processReturn(Map<String, String> params) {
        String payloadJson = toJson(params);
        String txnRef = params.get("vnp_TxnRef");
        String secureHash = params.get("vnp_SecureHash");
        String vnpTxnNo = params.get("vnp_TransactionNo");
        Long vnpAmount = params.get("vnp_Amount") != null ? parseAmount(params.get("vnp_Amount")) / 100 : null;
        String responseCode = params.get("vnp_ResponseCode");
        String vnpBankCode = params.get("vnp_BankCode");

        boolean signatureValid = VnpaySigner.verify(params, vnpayProperties.getHashSecret(), secureHash);
        paymentLogService.log(PaymentEventSource.RETURN, txnRef, vnpTxnNo, vnpAmount, responseCode, vnpBankCode, signatureValid, responseCode, payloadJson);

        if (!signatureValid) {
            log.warn("Return URL verification failed: Invalid checksum for txnRef {}", txnRef);
            return webUrl + "/result?error=invalid_signature";
        }

        Optional<PaymentAttempt> attemptOpt = (txnRef != null)
                ? paymentAttemptRepository.findWithOrderByTxnRef(txnRef)
                : Optional.empty();

        if (attemptOpt.isEmpty()) {
            log.warn("Return URL failed: TxnRef {} not found", txnRef);
            return webUrl + "/result?error=order_not_found";
        }

        PaymentAttempt attempt = attemptOpt.get();
        StoreOrder order = attempt.getOrder();
        if (!vnpayProperties.getTmnCode().equals(params.get("vnp_TmnCode"))
                || vnpAmount == null || vnpAmount.longValue() != attempt.getAmount().longValue()) {
            log.warn("Return URL rejected for txnRef {} due to merchant or amount mismatch", txnRef);
            return webUrl + "/result?error=invalid_payment_response";
        }

        // Return URL chỉ giúp trình duyệt hiển thị; không được dùng làm căn cứ đổi trạng thái.
        return webUrl + "/result?orderCode=" + order.getOrderCode();
    }

    /** Áp dụng kết quả QueryDR; caller chỉ gọi sau khi chữ ký response đã hợp lệ. */
    @Transactional
    public String processQueryDr(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        Optional<PaymentAttempt> attemptOpt = txnRef == null
                ? Optional.empty() : paymentAttemptRepository.findLockedByTxnRef(txnRef);
        if (attemptOpt.isEmpty()) return "ORDER_NOT_FOUND";
        PaymentAttempt attempt = attemptOpt.get();
        if (!vnpayProperties.getTmnCode().equals(params.get("vnp_TmnCode"))) return "TMN_CODE_MISMATCH";
        if (attempt.getStatus() != PaymentStatus.PENDING)
            return "ALREADY_FINAL_" + attempt.getStatus();

        String queryCode = params.get("vnp_ResponseCode");
        if ("91".equals(queryCode) && isPaymentWindowExpired(attempt)) {
            attempt.setStatus(PaymentStatus.EXPIRED);
            StoreOrder order = attempt.getOrder();
            if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PENDING_REVIEW) {
                order.setStatus(clock.instant().isAfter(order.getExpiresAt())
                        ? OrderStatus.EXPIRED : OrderStatus.FAILED);
            }
            return "EXPIRED_NOT_FOUND_AT_VNPAY";
        }
        if (!"00".equals(queryCode)) return "QUERYDR_ERROR_" + queryCode;
        Long amount = params.get("vnp_Amount") == null ? null : parseAmount(params.get("vnp_Amount")) / 100;
        if (amount == null || amount.longValue() != attempt.getAmount().longValue()) return "AMOUNT_MISMATCH";
        if (!"01".equals(params.get("vnp_TransactionType"))) {
            attempt.setStatus(PaymentStatus.REVIEW_REQUIRED);
            StoreOrder order = attempt.getOrder();
            if (order.getStatus() != OrderStatus.PAID) {
                order.setStatus(OrderStatus.PENDING_REVIEW);
            }
            log.error("Unexpected VNPAY transaction type for payment txnRef {}: {}",
                    txnRef, params.get("vnp_TransactionType"));
            return "UNEXPECTED_TRANSACTION_TYPE_REVIEW_REQUIRED";
        }

        String transactionStatus = params.get("vnp_TransactionStatus");
        if (transactionStatus == null
                || !Set.of("00", "01", "02", "04", "05", "06", "07", "09").contains(transactionStatus)) {
            return "UNKNOWN_TRANSACTION_STATUS";
        }
        attempt.setVnpResponseCode(queryCode);
        attempt.setVnpTransactionStatus(transactionStatus);
        attempt.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        attempt.setVnpBankCode(params.get("vnp_BankCode"));
        attempt.setVnpPayDate(params.get("vnp_PayDate"));
        applyVnpayResult(attempt.getOrder(), attempt, queryCode, transactionStatus,
                PaymentConfirmedVia.QUERYDR, true);
        return "QUERYDR_APPLIED_" + attempt.getStatus();
    }

    private boolean isPaymentWindowExpired(PaymentAttempt attempt) {
        try {
            Instant expiresAt = LocalDateTime.parse(attempt.getVnpExpireDate(), DATE_FORMATTER)
                    .atZone(GMT7).toInstant();
            return !clock.instant().isBefore(expiresAt);
        } catch (RuntimeException e) {
            log.warn("Invalid VNPAY expiry date for txnRef {}", attempt.getTxnRef());
            return false;
        }
    }

    private void applyIpnResult(StoreOrder order, PaymentAttempt attempt, String responseCode,
                                String transactionStatus) {
        applyVnpayResult(order, attempt, responseCode, transactionStatus, PaymentConfirmedVia.IPN, false);
    }

    private void applyVnpayResult(StoreOrder order, PaymentAttempt attempt, String responseCode,
                                  String transactionStatus, PaymentConfirmedVia confirmedVia, boolean queryDr) {
        if (requiresManualReview(responseCode, transactionStatus, queryDr)) {
            attempt.setStatus(PaymentStatus.REVIEW_REQUIRED);
            if (order.getStatus() != OrderStatus.PAID) {
                order.setStatus(OrderStatus.PENDING_REVIEW);
            }
            log.error("Payment requires manual reconciliation for txnRef {} (responseCode={}, transactionStatus={})",
                    attempt.getTxnRef(), responseCode, transactionStatus);
            return;
        }

        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            attempt.setStatus(PaymentStatus.SUCCESS);
            attempt.setConfirmedVia(confirmedVia);
            confirmOrderPaid(order, clock.instant());
            log.info("Payment SUCCESS for txnRef {}, order {}", attempt.getTxnRef(), order.getOrderCode());
            return;
        }

        if ("24".equals(responseCode)) {
            attempt.setStatus(PaymentStatus.FAILED);
            if (order.getStatus() != OrderStatus.PAID) {
                order.setStatus(OrderStatus.CANCELLED);
            }
            log.info("Customer cancelled payment attempt {}", attempt.getTxnRef());
            return;
        }

        if ("01".equals(transactionStatus)) {
            // Giao dịch thanh toán chưa hoàn tất; giữ lượt thanh toán mở để chờ IPN/kết quả sau.
            log.info("Payment still processing for txnRef {}", attempt.getTxnRef());
            return;
        }

        attempt.setStatus(PaymentStatus.FAILED);
        if (order.getStatus() != OrderStatus.PAID) {
            order.setStatus(OrderStatus.FAILED);
        }
        log.warn("Payment FAILED for txnRef {} (responseCode={}, transactionStatus={})",
                attempt.getTxnRef(), responseCode, transactionStatus);
    }

    private boolean requiresManualReview(String responseCode, String transactionStatus, boolean queryDr) {
        // Mã 07 từ IPN hoặc trạng thái có rủi ro tiền/gian lận cần đối soát thủ công.
        return (!queryDr && "07".equals(responseCode))
                || "05".equals(transactionStatus)
                || "06".equals(transactionStatus)
                || "04".equals(transactionStatus)
                || "07".equals(transactionStatus)
                || "09".equals(transactionStatus);
    }

    /**
     * Đánh dấu đơn đã thanh toán và tính thời điểm kích hoạt, ngày hết hạn gói.
     */
    private void confirmOrderPaid(StoreOrder order, Instant paidAt) {
        if (order.getStatus() != OrderStatus.PAID) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(paidAt);
            order.setActivatedAt(paidAt);

            // Ngày hiệu lực được tính theo múi giờ Việt Nam để nhất quán với chu kỳ gói.
            ZonedDateTime vnHigher = paidAt.atZone(GMT7).plusMonths(order.getDurationMonths());
            order.setValidUntil(vnHigher.toInstant());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private long parseAmount(String amountStr) {
        try {
            return Long.parseLong(amountStr);
        } catch (Exception e) {
            return 0L;
        }
    }
}
