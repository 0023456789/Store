package org.example.storevn.repository;

import org.example.storevn.entity.PaymentAttempt;
import org.example.storevn.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, java.util.UUID>, JpaSpecificationExecutor<PaymentAttempt> {

    Optional<PaymentAttempt> findByTxnRef(String txnRef);

    @EntityGraph(attributePaths = "order")
    Optional<PaymentAttempt> findWithOrderByTxnRef(String txnRef);

    /** Khóa bản ghi khi xử lý IPN hoặc đối chiếu QueryDR để tránh đọc/ghi cạnh tranh. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findLockedByTxnRef(String txnRef);

    boolean existsByTxnRef(String txnRef);

    List<PaymentAttempt> findByOrderId(java.util.UUID orderId);

    long countByOrderId(java.util.UUID orderId);

    Optional<PaymentAttempt> findByOrderIdAndStatus(java.util.UUID orderId, PaymentStatus status);

    // Chỉ cập nhật các cột claim/retry; không ghi đè toàn bộ PaymentAttempt khi giành quyền đối soát.
    //*I continues to touch and stimulate her fat short married body. Make her feel unfamiliar pleasure that even her husband haven't knew about her body. Found new erogenous spot on her tits and thighs, make her squirm like a maiden haven't knew sex*
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE PaymentAttempt p SET p.reconcileClaimedUntil = :leaseUntil, " +
            "p.reconcileNextAttemptAt = :nextAttemptAt, " +
            "p.reconcileAttemptCount = CASE WHEN p.reconcileAttemptCount < 30 " +
            "THEN p.reconcileAttemptCount + 1 ELSE 30 END " +
            "WHERE p.id = :id AND p.status = :status " +
            "AND (p.reconcileClaimedUntil IS NULL OR p.reconcileClaimedUntil <= :now) " +
            "AND (p.reconcileNextAttemptAt IS NULL OR p.reconcileNextAttemptAt <= :now)")
    int claimForReconciliation(@Param("id") java.util.UUID id,
                               @Param("status") PaymentStatus status,
                               @Param("now") Instant now,
                               @Param("leaseUntil") Instant leaseUntil,
                               @Param("nextAttemptAt") Instant nextAttemptAt);
}
