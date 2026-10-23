package org.example.storevn.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.example.storevn.enums.OrderStatus;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
// Khi cập nhật trạng thái đơn, Hibernate chỉ ghi các cột đã thay đổi thay vì ghi lại cả bản ghi.
@DynamicUpdate
@Table(name = "store_order")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreOrder extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    java.util.UUID id;

    @Column(name = "order_code", nullable = false, unique = true, length = 35)
    String orderCode;

    @Column(name = "plan_id", nullable = false)
    Long planId;

    @Column(name = "plan_code", nullable = false, length = 50)
    String planCode;

    @Column(name = "plan_name", nullable = false, length = 150)
    String planName;

    @Column(name = "duration_months", nullable = false)
    Integer durationMonths;

    @Column(name = "cycle_days", nullable = false)
    Integer cycleDays;

    @Column(name = "plan_price", nullable = false)
    Long planPrice;

    @Builder.Default
    @Column(name = "sim_fee", nullable = false)
    Long simFee = 0L;

    @Builder.Default
    @Column(name = "discount", nullable = false)
    Long discount = 0L;

    @Column(name = "amount", nullable = false)
    Long amount;

    @Column(nullable = false, length = 15)
    String msisdn;

    @Column(name = "customer_name", nullable = false, length = 150)
    String customerName;

    @Column(name = "customer_phone", nullable = false, length = 15)
    String customerPhone;

    @Column(name = "customer_email", length = 150)
    String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    OrderStatus status;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "paid_at")
    Instant paidAt;

    @Column(name = "activated_at")
    Instant activatedAt;

    @Column(name = "valid_until")
    Instant validUntil;
}
