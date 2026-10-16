package org.example.storevn.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.example.storevn.enums.PaymentEventSource;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "payment_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    PaymentEventSource source;

    @Column(name = "txn_ref", length = 100)
    String txnRef;

    @Column(name = "signature_valid", nullable = false)
    Boolean signatureValid;

    @Column(name = "rsp_code", length = 2)
    String rspCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    String payload;

    @Builder.Default
    @Column(name = "received_at", nullable = false, updatable = false)
    Instant receivedAt = Instant.now();

    @PrePersist
    protected void onPrePersist() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
