package org.example.storevn.repository;

import org.example.storevn.entity.PaymentAttempt;
import org.example.storevn.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByTxnRef(String txnRef);

    List<PaymentAttempt> findByOrderId(Long orderId);

    Optional<PaymentAttempt> findByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
