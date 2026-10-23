package org.example.storevn.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreOrderResponse {
    String orderCode;
    String planCode;
    String planName;
    Integer durationMonths;
    Integer cycleDays;
    Long planPrice;
    Long simFee;
    Long discount;
    Long amount;
    String msisdn;
    String customerName;
    String customerPhone;
    String customerEmail;
    OrderStatus status;
    Instant expiresAt;
    Instant paidAt;
    Instant createdAt;

    public static StoreOrderResponse from(StoreOrder order) {
        if (order == null) return null;
        return StoreOrderResponse.builder()
                .orderCode(order.getOrderCode())
                .planCode(order.getPlanCode())
                .planName(order.getPlanName())
                .durationMonths(order.getDurationMonths())
                .cycleDays(order.getCycleDays())
                .planPrice(order.getPlanPrice())
                .simFee(order.getSimFee())
                .discount(order.getDiscount())
                .amount(order.getAmount())
                .msisdn(order.getMsisdn())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .customerEmail(order.getCustomerEmail())
                .status(order.getStatus())
                .expiresAt(order.getExpiresAt())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
