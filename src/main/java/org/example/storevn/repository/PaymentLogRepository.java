package org.example.storevn.repository;

import org.example.storevn.entity.PaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentLogRepository extends JpaRepository<PaymentLog, java.util.UUID> {
    List<PaymentLog> findByTxnRef(String txnRef);
    List<PaymentLog> findByVnpTransactionNo(String vnpTransactionNo);
}
