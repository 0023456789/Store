package org.example.storevn.repository;

import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoreOrderRepository extends JpaRepository<StoreOrder, java.util.UUID> {

    Optional<StoreOrder> findByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StoreOrder> findLockedByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    Optional<StoreOrder> findByOrderCodeAndCustomerPhone(String orderCode, String customerPhone);

    List<StoreOrder> findByStatusAndExpiresAtBefore(OrderStatus status, Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StoreOrder> findByStatusAndExpiresAtBefore(OrderStatus status, Instant cutoff, Pageable pageable);
}
