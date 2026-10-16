package org.example.storevn.repository;

import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {

    Optional<StoreOrder> findByOrderCode(String orderCode);

    Optional<StoreOrder> findByOrderCodeAndCustomerPhone(String orderCode, String customerPhone);

    List<StoreOrder> findByStatusAndExpiresAtBefore(OrderStatus status, Instant cutoff);
}
