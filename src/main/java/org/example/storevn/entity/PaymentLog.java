package org.example.storevn.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.example.storevn.enums.PaymentEventSource;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payment_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    java.util.UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    PaymentEventSource source;

    @Column(name = "txn_ref", length = 100)
    String txnRef;

    @Column(name = "vnp_transaction_no", length = 50)
    String vnpTransactionNo;

    Long amount;

    @Column(name = "vnp_response_code", length = 10)
    String vnpResponseCode;

    @Column(name = "vnp_bank_code", length = 50)
    String vnpBankCode;

    @Column(name = "signature_valid", nullable = false)
    Boolean signatureValid;

    @Column(name = "rsp_code", length = 10)
    String rspCode;

    @Column(name = "response_message", length = 100)
    String responseMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false)
    String rawPayload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
}
