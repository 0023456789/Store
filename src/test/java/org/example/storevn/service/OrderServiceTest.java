package org.example.storevn.service;

import org.example.storevn.catalog.AdminSkyCatalogClient;
import org.example.storevn.dto.request.CreateOrderRequest;
import org.example.storevn.dto.response.StoreOrderResponse;
import org.example.storevn.dto.response.StorePlanResponse;
import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.example.storevn.repository.StoreOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private AdminSkyCatalogClient catalogClient;

    @Mock
    private StoreOrderRepository storeOrderRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(catalogClient, storeOrderRepository, Clock.systemUTC(), 15L);
    }

    @Test
    @DisplayName("createOrder with active plan snapshots plan attributes, sets TTL, and generates order code")
    void createOrder_activePlan_orderCreatedWithSnapshotAndExpiry() {
        StorePlanResponse plan = StorePlanResponse.builder()
                .id(10L)
                .code("SKY89")
                .name("Gói Sky89")
                .price(89000L)
                .durationMonths(1)
                .cycleDays(30)
                .build();

        when(catalogClient.findActiveByCode("SKY89")).thenReturn(Optional.of(plan));
        when(storeOrderRepository.existsByOrderCode(anyString())).thenReturn(false);
        when(storeOrderRepository.save(any(StoreOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .planCode("SKY89")
                .msisdn("0987654321")
                .customerName("Nguyen Van A")
                .customerPhone("0912345678")
                .customerEmail("customer@example.com")
                .build();

        Instant before = Instant.now();
        StoreOrderResponse response = orderService.createOrder(request);
        Instant after = Instant.now();

        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).matches("^ORD[A-F0-9]{32}$");
        assertThat(response.getPlanCode()).isEqualTo("SKY89");
        assertThat(response.getPlanName()).isEqualTo("Gói Sky89");
        assertThat(response.getDurationMonths()).isEqualTo(1);
        assertThat(response.getCycleDays()).isEqualTo(30);
        assertThat(response.getPlanPrice()).isEqualTo(89000L);
        assertThat(response.getSimFee()).isEqualTo(0L);
        assertThat(response.getDiscount()).isEqualTo(0L);
        assertThat(response.getAmount()).isEqualTo(89000L);
        assertThat(response.getMsisdn()).isEqualTo("0987654321");
        assertThat(response.getCustomerName()).isEqualTo("Nguyen Van A");
        assertThat(response.getCustomerPhone()).isEqualTo("0912345678");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // Hạn thanh toán của đơn đồng nhất với thời hạn 15 phút gửi sang VNPAY.
        assertThat(response.getExpiresAt())
                .isAfter(before.plus(14, ChronoUnit.MINUTES))
                .isBefore(after.plus(16, ChronoUnit.MINUTES));

        ArgumentCaptor<StoreOrder> orderCaptor = ArgumentCaptor.forClass(StoreOrder.class);
        verify(storeOrderRepository).save(orderCaptor.capture());
        StoreOrder saved = orderCaptor.getValue();
        assertThat(saved.getPlanId()).isEqualTo(10L);
        assertThat(saved.getAmount()).isEqualTo(89000L);
    }

    @Test
    @DisplayName("createOrder with inactive plan throws PLAN_INACTIVE error")
    void createOrder_inactivePlan_throwsPlanInactiveException() {
        when(catalogClient.findActiveByCode("INACTIVE_PLAN")).thenReturn(Optional.empty());
        when(catalogClient.existsByCode("INACTIVE_PLAN")).thenReturn(true);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .planCode("INACTIVE_PLAN")
                .msisdn("0987654321")
                .customerName("Nguyen Van A")
                .customerPhone("0912345678")
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_INACTIVE);

        verify(storeOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder with non-existent plan throws PLAN_NOT_FOUND error")
    void createOrder_nonExistentPlan_throwsPlanNotFoundException() {
        when(catalogClient.findActiveByCode("UNKNOWN")).thenReturn(Optional.empty());
        when(catalogClient.existsByCode("UNKNOWN")).thenReturn(false);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .planCode("UNKNOWN")
                .msisdn("0987654321")
                .customerName("Nguyen Van A")
                .customerPhone("0912345678")
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_NOT_FOUND);

        verify(storeOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrderByCode with matching phone returns order")
    void getOrderByCode_matchingPhone_returnsOrder() {
        StoreOrder order = StoreOrder.builder()
                .orderCode("ORD123456789")
                .planId(1L)
                .planCode("SKY89")
                .planName("Sky89")
                .durationMonths(1)
                .cycleDays(30)
                .planPrice(89000L)
                .simFee(0L)
                .discount(0L)
                .amount(89000L)
                .msisdn("0987654321")
                .customerName("Nguyen Van A")
                .customerPhone("0912345678")
                .status(OrderStatus.PENDING_PAYMENT)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();

        when(storeOrderRepository.findByOrderCode("ORD123456789")).thenReturn(Optional.of(order));

        StoreOrderResponse response = orderService.getOrderByCode("ORD123456789", "0912345678");

        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("ORD123456789");
        assertThat(response.getCustomerPhone()).isEqualTo("0912345678");
    }

    @Test
    @DisplayName("getOrderByCode with mismatched phone throws ORDER_PHONE_MISMATCH")
    void getOrderByCode_mismatchedPhone_throwsOrderPhoneMismatch() {
        StoreOrder order = StoreOrder.builder()
                .orderCode("ORD123456789")
                .customerPhone("0912345678")
                .build();

        when(storeOrderRepository.findByOrderCode("ORD123456789")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderByCode("ORD123456789", "0988888888"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_PHONE_MISMATCH);
    }

    @Test
    @DisplayName("getOrderByCode when order does not exist throws ORDER_NOT_FOUND")
    void getOrderByCode_orderNotFound_throwsOrderNotFound() {
        when(storeOrderRepository.findByOrderCode("NONEXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderByCode("NONEXISTENT", "0912345678"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
    }
}
