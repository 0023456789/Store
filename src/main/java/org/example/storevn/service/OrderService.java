package org.example.storevn.service;

import lombok.extern.slf4j.Slf4j;
import org.example.storevn.catalog.AdminSkyCatalogClient;
import org.example.storevn.dto.request.CreateOrderRequest;
import org.example.storevn.dto.response.StoreOrderResponse;
import org.example.storevn.dto.response.StorePlanResponse;
import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.example.storevn.repository.StoreOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Slf4j
public class OrderService {

    private static final String ORDER_CODE_PREFIX = "ORD";

    private final AdminSkyCatalogClient catalogClient;
    private final StoreOrderRepository storeOrderRepository;
    private final Clock clock;
    private final long orderTtlMinutes;

    public OrderService(
            AdminSkyCatalogClient catalogClient,
            StoreOrderRepository storeOrderRepository,
            Clock clock,
            @Value("${storefront.order-ttl-minutes:15}") long orderTtlMinutes) {
        this.catalogClient = catalogClient;
        this.storeOrderRepository = storeOrderRepository;
        this.clock = clock;
        this.orderTtlMinutes = orderTtlMinutes;
    }

    public StoreOrderResponse createOrder(CreateOrderRequest request) {
        StorePlanResponse plan = catalogClient.findActiveByCode(request.getPlanCode())
                .orElseGet(() -> {
                    if (catalogClient.existsByCode(request.getPlanCode())) {
                        throw new AppException(ErrorCode.PLAN_INACTIVE);
                    }
                    throw new AppException(ErrorCode.PLAN_NOT_FOUND);
                });

        long planPrice = plan.getPrice();
        long simFee = 0L;
        long discount = 0L;
        long amount = planPrice + simFee - discount;

        Instant expiresAt = clock.instant().plus(orderTtlMinutes, ChronoUnit.MINUTES);
        String orderCode = generateUniqueOrderCode();

        StoreOrder order = StoreOrder.builder()
                .orderCode(orderCode)
                .planId(plan.getId())
                .planCode(plan.getCode())
                .planName(plan.getName())
                .durationMonths(plan.getDurationMonths())
                .cycleDays(plan.getCycleDays() != null ? plan.getCycleDays() : 30)
                .planPrice(planPrice)
                .simFee(simFee)
                .discount(discount)
                .amount(amount)
                .msisdn(request.getMsisdn().trim())
                .customerName(request.getCustomerName().trim())
                .customerPhone(request.getCustomerPhone().trim())
                .customerEmail(request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()
                        ? request.getCustomerEmail().trim() : null)
                .status(OrderStatus.PENDING_PAYMENT)
                .expiresAt(expiresAt)
                .build();

        StoreOrder savedOrder = storeOrderRepository.save(order);
        log.info("Created store order {} for plan {}", orderCode, plan.getCode());
        return StoreOrderResponse.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public StoreOrderResponse getOrderByCode(String orderCode, String phone) {
        StoreOrder order = storeOrderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (phone == null || !phone.trim().equals(order.getCustomerPhone())) {
            throw new AppException(ErrorCode.ORDER_PHONE_MISMATCH);
        }

        return StoreOrderResponse.from(order);
    }

    private String generateUniqueOrderCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            // UUID v4 tạo mã công khai khó dò; tiền tố ORD giúp nhận diện đây là mã đơn.
            String code = ORDER_CODE_PREFIX + UUID.randomUUID().toString().replace("-", "").toUpperCase();
            if (!storeOrderRepository.existsByOrderCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate unique order code after multiple attempts");
    }
}
