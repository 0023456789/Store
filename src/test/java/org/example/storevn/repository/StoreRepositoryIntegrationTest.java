package org.example.storevn.repository;

import org.example.storevn.entity.PaymentAttempt;
import org.example.storevn.entity.PaymentEvent;
import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;
import org.example.storevn.enums.PaymentConfirmedVia;
import org.example.storevn.enums.PaymentEventSource;
import org.example.storevn.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StoreRepositoryIntegrationTest {

    @Autowired
    private StoreOrderRepository orderRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    private StoreOrder createSampleOrder(String orderCode, String phone, OrderStatus status, Instant expiresAt) {
        return StoreOrder.builder()
                .orderCode(orderCode)
                .planId(1L)
                .planCode("SKY89")
                .planName("Gói Sky89")
                .durationMonths(1)
                .cycleDays(30)
                .planPrice(89000L)
                .simFee(0L)
                .discount(0L)
                .amount(89000L)
                .msisdn("0987654321")
                .customerName("Nguyen Van A")
                .customerPhone(phone)
                .customerEmail("a@example.com")
                .status(status)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    @DisplayName("save persists StoreOrder and generates id and audit timestamps")
    void save_validOrder_persistsAndGeneratesId() {
        StoreOrder order = createSampleOrder("ORD10001", "0912345678", OrderStatus.PENDING_PAYMENT,
                Instant.now().plus(30, ChronoUnit.MINUTES));

        StoreOrder saved = orderRepository.saveAndFlush(order);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals("ORD10001", saved.getOrderCode());

        Optional<StoreOrder> found = orderRepository.findByOrderCode("ORD10001");
        assertTrue(found.isPresent());
        assertEquals("0987654321", found.get().getMsisdn());
    }

    @Test
    @DisplayName("findByOrderCodeAndCustomerPhone returns order only when phone matches")
    void findByOrderCodeAndCustomerPhone_matchingAndMismatching_handlesAccessProperly() {
        StoreOrder order = createSampleOrder("ORD10002", "0912345678", OrderStatus.PENDING_PAYMENT,
                Instant.now().plus(30, ChronoUnit.MINUTES));
        orderRepository.saveAndFlush(order);

        Optional<StoreOrder> match = orderRepository.findByOrderCodeAndCustomerPhone("ORD10002", "0912345678");
        assertTrue(match.isPresent());

        Optional<StoreOrder> mismatch = orderRepository.findByOrderCodeAndCustomerPhone("ORD10002", "0999999999");
        assertTrue(mismatch.isEmpty(), "Should not return order when phone does not match");
    }

    @Test
    @DisplayName("findByStatusAndExpiresAtBefore returns expired pending orders")
    void findByStatusAndExpiresAtBefore_expiredOrders_returnsMatchingOrders() {
        Instant now = Instant.now();
        StoreOrder expiredOrder = createSampleOrder("ORD_EXP_1", "0912345678", OrderStatus.PENDING_PAYMENT,
                now.minus(5, ChronoUnit.MINUTES));
        StoreOrder validOrder = createSampleOrder("ORD_VAL_1", "0912345678", OrderStatus.PENDING_PAYMENT,
                now.plus(25, ChronoUnit.MINUTES));

        orderRepository.saveAllAndFlush(List.of(expiredOrder, validOrder));

        List<StoreOrder> expired = orderRepository.findByStatusAndExpiresAtBefore(OrderStatus.PENDING_PAYMENT, now);
        assertEquals(1, expired.size());
        assertEquals("ORD_EXP_1", expired.get(0).getOrderCode());
    }

    @Test
    @DisplayName("save PaymentAttempt persists record linked to order")
    void save_paymentAttempt_persistsWithOrderReference() {
        StoreOrder order = createSampleOrder("ORD10003", "0912345678", OrderStatus.PENDING_PAYMENT,
                Instant.now().plus(30, ChronoUnit.MINUTES));
        StoreOrder savedOrder = orderRepository.saveAndFlush(order);

        PaymentAttempt attempt = PaymentAttempt.builder()
                .order(savedOrder)
                .txnRef("TXN202610160001")
                .amount(89000L)
                .vnpCreateDate("20261016090000")
                .vnpExpireDate("20261016091500")
                .status(PaymentStatus.PENDING)
                .build();

        PaymentAttempt savedAttempt = paymentAttemptRepository.saveAndFlush(attempt);
        assertNotNull(savedAttempt.getId());
        assertNotNull(savedAttempt.getCreatedAt());

        Optional<PaymentAttempt> found = paymentAttemptRepository.findByTxnRef("TXN202610160001");
        assertTrue(found.isPresent());
        assertEquals(savedOrder.getId(), found.get().getOrder().getId());
        assertEquals(PaymentStatus.PENDING, found.get().getStatus());

        List<PaymentAttempt> orderAttempts = paymentAttemptRepository.findByOrderId(savedOrder.getId());
        assertEquals(1, orderAttempts.size());
    }

    @Test
    @DisplayName("save PaymentEvent persists raw payload and metadata")
    void save_paymentEvent_persistsRawPayload() {
        PaymentEvent event = PaymentEvent.builder()
                .source(PaymentEventSource.IPN)
                .txnRef("TXN202610160002")
                .signatureValid(true)
                .rspCode("00")
                .payload("{\"vnp_ResponseCode\":\"00\",\"vnp_Amount\":\"8900000\"}")
                .build();

        PaymentEvent saved = paymentEventRepository.saveAndFlush(event);
        assertNotNull(saved.getId());
        assertNotNull(saved.getReceivedAt());

        List<PaymentEvent> events = paymentEventRepository.findByTxnRef("TXN202610160002");
        assertEquals(1, events.size());
        assertEquals("00", events.get(0).getRspCode());
        assertTrue(events.get(0).getPayload().contains("8900000"));
    }
}
