package org.example.storevn.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.example.storevn.enums.PaymentConfirmedVia;
import org.example.storevn.enums.PaymentStatus;

@Entity
@Table(name = "payment_attempt")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentAttempt extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    StoreOrder order;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 40)
    String txnRef;

    @Column(nullable = false)
    Long amount;

    @Column(name = "vnp_create_date", nullable = false, length = 14)
    String vnpCreateDate;

    @Column(name = "vnp_expire_date", nullable = false, length = 14)
    String vnpExpireDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PaymentStatus status;

    @Column(name = "vnp_response_code", length = 2)
    String vnpResponseCode;

    @Column(name = "vnp_transaction_status", length = 2)
    String vnpTransactionStatus;

    @Column(name = "vnp_transaction_no", length = 15)
    String vnpTransactionNo;

    @Column(name = "vnp_bank_code", length = 20)
    String vnpBankCode;

    @Column(name = "vnp_card_type", length = 20)
    String vnpCardType;

    @Column(name = "vnp_pay_date", length = 14)
    String vnpPayDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmed_via", length = 10)
    PaymentConfirmedVia confirmedVia;
}
