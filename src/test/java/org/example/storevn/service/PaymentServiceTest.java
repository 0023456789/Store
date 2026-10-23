package org.example.storevn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.storevn.dto.request.CreatePaymentRequest;
import org.example.storevn.dto.response.CreatePaymentResponse;
import org.example.storevn.dto.response.VnpayIpnResponse;
import org.example.storevn.entity.PaymentAttempt;
import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;
import org.example.storevn.enums.PaymentConfirmedVia;
import org.example.storevn.enums.PaymentStatus;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.example.storevn.repository.PaymentAttemptRepository;
import org.example.storevn.service.PaymentLogService;
import org.example.storevn.repository.StoreOrderRepository;
import org.example.storevn.vnpay.VnpayProperties;
import org.example.storevn.vnpay.VnpaySigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private StoreOrderRepository storeOrderRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private PaymentLogService paymentLogService;

    private VnpayProperties vnpayProperties;
    private PaymentService paymentService;

    private static final String SECRET = "TESTSECRETKEY0123456789";
    private static final String TMN = "TESTCODE";

    @BeforeEach
    void setUp() {
        vnpayProperties = new VnpayProperties();
        vnpayProperties.setTmnCode(TMN);
        vnpayProperties.setHashSecret(SECRET);
        vnpayProperties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");

        paymentService = new PaymentService(
                storeOrderRepository,
                paymentAttemptRepository,
                paymentLogService,
                vnpayProperties,
                new ObjectMapper(),
                Clock.systemUTC(),
                "http://localhost:8081",
                "http://localhost:5173"
        );
    }

    @Test
    @DisplayName("createPayment builds signed VNPAY URL and saves pending attempt")
    void createPayment_validOrder_generatesSignedUrlAndSavesAttempt() {
        StoreOrder order = StoreOrder.builder()
                .id(uuid(1))
                .orderCode("ORDTEST12345")
                .planCode("SKY89")
                .amount(89000L)
                .status(OrderStatus.PENDING_PAYMENT)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();

        when(storeOrderRepository.findLockedByOrderCode("ORDTEST12345")).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.countByOrderId(uuid(1))).thenReturn(0L);
        when(paymentAttemptRepository.existsByTxnRef(anyString())).thenReturn(false);

        CreatePaymentRequest request = new CreatePaymentRequest("NCB");
        CreatePaymentResponse response = paymentService.createPayment("ORDTEST12345", request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getTxnRef()).isEqualTo("ORDTEST1234501");
        assertThat(response.getPaymentUrl()).contains("vnp_SecureHash=");
        assertThat(response.getPaymentUrl()).contains("vnp_Amount=8900000");
        assertThat(response.getPaymentUrl()).contains("vnp_BankCode=NCB");
        assertThat(response.getPaymentUrl()).contains("vnp_ExpireDate=");

        verify(paymentAttemptRepository).save(any(PaymentAttempt.class));
    }

    @Test
    @DisplayName("createPayment caps VNPAY expiry at the order expiry")
    void createPayment_expiryDoesNotOutliveOrder() {
        Instant orderExpiry = Instant.now().plus(5, ChronoUnit.MINUTES);
        StoreOrder order = StoreOrder.builder().id(uuid(2)).orderCode("ORDEXP123")
                .planCode("SKY89").amount(89000L).status(OrderStatus.PENDING_PAYMENT)
                .expiresAt(orderExpiry).build();
        when(storeOrderRepository.findLockedByOrderCode("ORDEXP123")).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.countByOrderId(uuid(2))).thenReturn(0L);
        when(paymentAttemptRepository.existsByTxnRef(anyString())).thenReturn(false);

        CreatePaymentResponse response = paymentService.createPayment("ORDEXP123", null, "127.0.0.1");

        String expectedExpiry = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(orderExpiry);
        assertThat(response.getPaymentUrl()).contains("vnp_ExpireDate=" + expectedExpiry);
    }

    @Test
    @DisplayName("expired order is marked expired before ORDER_EXPIRED is returned")
    void createPayment_expiredOrderPersistsExpiredStatus() {
        StoreOrder order = StoreOrder.builder().id(uuid(3)).orderCode("ORDEXPIRED")
                .status(OrderStatus.PENDING_PAYMENT).expiresAt(Instant.now().minusSeconds(1)).build();
        when(storeOrderRepository.findLockedByOrderCode("ORDEXPIRED")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createPayment("ORDEXPIRED", null, "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_EXPIRED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("transaction statuses 05 and 06 require manual reconciliation")
    void processIpn_refundStatusesRequireManualReview() {
        for (String transactionStatus : new String[]{"05", "06"}) {
            StoreOrder order = StoreOrder.builder().orderCode("ORDREF" + transactionStatus)
                    .status(OrderStatus.PENDING_PAYMENT).build();
            PaymentAttempt attempt = PaymentAttempt.builder().txnRef("TXNREF" + transactionStatus)
                    .amount(89000L).order(order).status(PaymentStatus.PENDING).build();
            Map<String, String> params = new HashMap<>();
            params.put("vnp_TmnCode", TMN);
            params.put("vnp_TxnRef", attempt.getTxnRef());
            params.put("vnp_Amount", "8900000");
            params.put("vnp_ResponseCode", "00");
            params.put("vnp_TransactionStatus", transactionStatus);
            params.put("vnp_SecureHash", VnpaySigner.sign(params, SECRET));
            when(paymentAttemptRepository.findLockedByTxnRef(attempt.getTxnRef())).thenReturn(Optional.of(attempt));

            paymentService.processIpn(params);

            assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.REVIEW_REQUIRED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_REVIEW);
        }
    }

    @Test
    @DisplayName("IPN cannot automatically resolve a transaction already requiring manual review")
    void processIpn_reviewRequiredAttemptCannotBePromotedToPaid() {
        StoreOrder order = StoreOrder.builder().orderCode("ORDLOCKREVIEW")
                .status(OrderStatus.PENDING_REVIEW).build();
        PaymentAttempt attempt = PaymentAttempt.builder().txnRef("TXNLOCKREVIEW")
                .amount(89000L).order(order).status(PaymentStatus.REVIEW_REQUIRED).build();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", attempt.getTxnRef());
        params.put("vnp_Amount", "8900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", VnpaySigner.sign(params, SECRET));
        when(paymentAttemptRepository.findLockedByTxnRef(attempt.getTxnRef())).thenReturn(Optional.of(attempt));

        VnpayIpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("02");
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.REVIEW_REQUIRED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("createPayment on already paid order throws ORDER_ALREADY_PAID")
    void createPayment_alreadyPaid_throwsException() {
        StoreOrder order = StoreOrder.builder()
                .orderCode("ORDTEST12345")
                .status(OrderStatus.PAID)
                .build();

        when(storeOrderRepository.findLockedByOrderCode("ORDTEST12345")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createPayment("ORDTEST12345", null, "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_ALREADY_PAID);
    }

    @Test
    @DisplayName("processIpn with invalid signature returns 97")
    void processIpn_invalidSignature_returns97() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "TXN123");
        params.put("vnp_SecureHash", "invalid_signature");

        VnpayIpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("97");
        assertThat(response.getMessage()).isEqualTo("Invalid Checksum");
    }

    @Test
    @DisplayName("processIpn when order does not exist returns 01")
    void processIpn_orderNotFound_returns01() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", "TXN999");
        params.put("vnp_Amount", "8900000");
        String hash = VnpaySigner.sign(params, SECRET);
        params.put("vnp_SecureHash", hash);

        when(paymentAttemptRepository.findLockedByTxnRef("TXN999")).thenReturn(Optional.empty());

        VnpayIpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("01");
        assertThat(response.getMessage()).isEqualTo("Order not found");
    }

    @Test
    @DisplayName("processIpn with mismatched amount returns 04")
    void processIpn_mismatchedAmount_returns04() {
        PaymentAttempt attempt = PaymentAttempt.builder()
                .txnRef("TXN123")
                .amount(89000L)
                .status(PaymentStatus.PENDING)
                .build();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", "TXN123");
        params.put("vnp_Amount", "5000000"); // 50,000 VND instead of 89,000
        String hash = VnpaySigner.sign(params, SECRET);
        params.put("vnp_SecureHash", hash);

        when(paymentAttemptRepository.findLockedByTxnRef("TXN123")).thenReturn(Optional.of(attempt));

        VnpayIpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("04");
        assertThat(response.getMessage()).isEqualTo("Invalid Amount");
    }

    @Test
    @DisplayName("processIpn keeps an in-progress VNPAY transaction pending")
    void processIpn_inProgress_keepsAttemptPending() {
        StoreOrder order = StoreOrder.builder()
                .id(uuid(1)).orderCode("ORDPROG123").status(OrderStatus.PENDING_PAYMENT).build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .txnRef("ORDPROG12301").amount(89000L).order(order).status(PaymentStatus.PENDING).build();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", "ORDPROG12301");
        params.put("vnp_Amount", "8900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "01");
        params.put("vnp_SecureHash", VnpaySigner.sign(params, SECRET));
        when(paymentAttemptRepository.findLockedByTxnRef("ORDPROG12301")).thenReturn(Optional.of(attempt));

        VnpayIpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("00");
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(storeOrderRepository, never()).save(order);
    }

    @Test
    @DisplayName("processReturn reports suspicious transactions without changing server-side payment state")
    void processReturn_suspiciousTransaction_requiresManualReview() {
        StoreOrder order = StoreOrder.builder()
                .id(uuid(1)).orderCode("ORDREVIEW123").amount(89000L).status(OrderStatus.PENDING_PAYMENT).build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .txnRef("ORDREVIEW12301").amount(89000L).order(order).status(PaymentStatus.PENDING).build();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", "ORDREVIEW12301");
        params.put("vnp_Amount", "8900000");
        params.put("vnp_ResponseCode", "07");
        params.put("vnp_TransactionStatus", "07");
        params.put("vnp_TransactionNo", "14123456");
        params.put("vnp_SecureHash", VnpaySigner.sign(params, SECRET));
        when(paymentAttemptRepository.findWithOrderByTxnRef("ORDREVIEW12301")).thenReturn(Optional.of(attempt));

        String redirectUrl = paymentService.processReturn(params);

        assertThat(redirectUrl).isEqualTo("http://localhost:5173/result?orderCode=ORDREVIEW123");
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(storeOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("processIpn with already processed attempt returns 02 (idempotent)")
    void processIpn_alreadyConfirmed_returns02() {
        PaymentAttempt attempt = PaymentAttempt.builder()
                .txnRef("TXN123")
                .amount(89000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", "TXN123");
        params.put("vnp_Amount", "8900000");
        String hash = VnpaySigner.sign(params, SECRET);
        params.put("vnp_SecureHash", hash);

        when(paymentAttemptRepository.findLockedByTxnRef("TXN123")).thenReturn(Optional.of(attempt));

        VnpayIpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("02");
        assertThat(response.getMessage()).isEqualTo("Order already confirmed");
    }

    @Test
    @DisplayName("processIpn with successful payment confirms attempt, sets order PAID and simulates activation")
    void processIpn_successfulPayment_confirmsOrderAndActivation() {
        StoreOrder order = StoreOrder.builder()
                .id(uuid(1))
                .orderCode("ORD123456")
                .durationMonths(1)
                .amount(89000L)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        PaymentAttempt attempt = PaymentAttempt.builder()
                .txnRef("ORD12345601")
                .amount(89000L)
                .order(order)
                .status(PaymentStatus.PENDING)
                .build();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", "ORD12345601");
        params.put("vnp_Amount", "8900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14123456");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_PayDate", "20261016120000");
        String hash = VnpaySigner.sign(params, SECRET);
        params.put("vnp_SecureHash", hash);

        when(paymentAttemptRepository.findLockedByTxnRef("ORD12345601")).thenReturn(Optional.of(attempt));

        VnpayIpnResponse response = paymentService.processIpn(params);

        assertThat(response.getRspCode()).isEqualTo("00");
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(attempt.getConfirmedVia()).isEqualTo(PaymentConfirmedVia.IPN);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(order.getActivatedAt()).isNotNull();
        assertThat(order.getValidUntil()).isNotNull();
        assertThat(order.getValidUntil()).isAfter(order.getActivatedAt());

        // Các entity được tải trong transaction; Hibernate ghi thay đổi bằng dirty checking.
        verify(paymentAttemptRepository, never()).save(any());
        verify(storeOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("processReturn with valid checksum reports gateway result but leaves confirmation to IPN")
    void processReturn_validSuccess_redirectsWithoutConfirmingPayment() {
        StoreOrder order = StoreOrder.builder()
                .id(uuid(1))
                .orderCode("ORDRET123")
                .durationMonths(1)
                .amount(89000L)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        PaymentAttempt attempt = PaymentAttempt.builder()
                .txnRef("ORDRET12301")
                .amount(89000L)
                .order(order)
                .status(PaymentStatus.PENDING)
                .build();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", "ORDRET12301");
        params.put("vnp_Amount", "8900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "99887766");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_PayDate", "20261016140000");
        String hash = VnpaySigner.sign(params, SECRET);
        params.put("vnp_SecureHash", hash);

        when(paymentAttemptRepository.findWithOrderByTxnRef("ORDRET12301")).thenReturn(Optional.of(attempt));

        String redirectUrl = paymentService.processReturn(params);

        assertThat(redirectUrl).isEqualTo("http://localhost:5173/result?orderCode=ORDRET123");
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(attempt.getConfirmedVia()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getPaidAt()).isNull();
        assertThat(order.getActivatedAt()).isNull();
        verify(paymentAttemptRepository, never()).save(any());
        verify(storeOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("processReturn with invalid checksum redirects to error page")
    void processReturn_invalidChecksum_redirectsToErrorPage() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "ORDRET12301");
        params.put("vnp_SecureHash", "invalid_hash");

        String redirectUrl = paymentService.processReturn(params);

        assertThat(redirectUrl).isEqualTo("http://localhost:5173/result?error=invalid_signature");
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("processReturn when user cancels redirects without changing payment state")
    void processReturn_userCancelled_closesAttemptForRetry() {
        StoreOrder order = StoreOrder.builder()
                .id(uuid(1))
                .orderCode("ORDCAN123")
                .amount(89000L)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        PaymentAttempt attempt = PaymentAttempt.builder()
                .txnRef("ORDCAN12301")
                .amount(89000L)
                .order(order)
                .status(PaymentStatus.PENDING)
                .build();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", "ORDCAN12301");
        params.put("vnp_Amount", "8900000");
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_TransactionNo", "14123456");
        String hash = VnpaySigner.sign(params, SECRET);
        params.put("vnp_SecureHash", hash);

        when(paymentAttemptRepository.findWithOrderByTxnRef("ORDCAN12301")).thenReturn(Optional.of(attempt));

        String redirectUrl = paymentService.processReturn(params);

        assertThat(redirectUrl).isEqualTo("http://localhost:5173/result?orderCode=ORDCAN123");
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(paymentAttemptRepository, never()).save(any());
        verify(storeOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("processReturn when bank declines reports failure and leaves order state to IPN")
    void processReturn_bankFailed_reportsFailureWithoutChangingState() {
        StoreOrder order = StoreOrder.builder()
                .id(uuid(1))
                .orderCode("ORDFAIL123")
                .amount(89000L)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        PaymentAttempt attempt = PaymentAttempt.builder()
                .txnRef("ORDFAIL12301")
                .amount(89000L)
                .order(order)
                .status(PaymentStatus.PENDING)
                .build();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN);
        params.put("vnp_TxnRef", "ORDFAIL12301");
        params.put("vnp_Amount", "8900000");
        params.put("vnp_ResponseCode", "51");
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_TransactionNo", "14123456");
        String hash = VnpaySigner.sign(params, SECRET);
        params.put("vnp_SecureHash", hash);

        when(paymentAttemptRepository.findWithOrderByTxnRef("ORDFAIL12301")).thenReturn(Optional.of(attempt));

        String redirectUrl = paymentService.processReturn(params);

        assertThat(redirectUrl).isEqualTo("http://localhost:5173/result?orderCode=ORDFAIL123");
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(paymentAttemptRepository, never()).save(any());
        verify(storeOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPayment allows retrying for CANCELLED order within TTL and resets to PENDING_PAYMENT")
    void createPayment_cancelledOrderWithinTtl_allowsRetry() {
        StoreOrder order = StoreOrder.builder()
                .id(uuid(1))
                .orderCode("ORDRETRY123")
                .planCode("SKY89")
                .amount(89000L)
                .status(OrderStatus.CANCELLED)
                .expiresAt(Instant.now().plus(20, ChronoUnit.MINUTES))
                .build();

        when(storeOrderRepository.findLockedByOrderCode("ORDRETRY123")).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.countByOrderId(uuid(1))).thenReturn(1L);
        when(paymentAttemptRepository.existsByTxnRef(anyString())).thenReturn(false);

        CreatePaymentResponse response = paymentService.createPayment("ORDRETRY123", null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getTxnRef()).isEqualTo("ORDRETRY12302");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // Order đã được tải trong transaction nên không cần gọi save lần nữa.
        verify(storeOrderRepository, never()).save(any());
        verify(paymentAttemptRepository).save(any(PaymentAttempt.class));
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }
}

