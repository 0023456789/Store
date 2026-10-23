package org.example.storevn.service;

import lombok.extern.slf4j.Slf4j;
import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;
import org.example.storevn.repository.StoreOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Dọn trạng thái các đơn và lượt thanh toán đã hết thời hạn chờ. */
@Service
@Slf4j
public class ExpiredOrderSweeper {

    private static final int BATCH_SIZE = 100;

    private final StoreOrderRepository storeOrderRepository;
    private final Clock clock;

    public ExpiredOrderSweeper(StoreOrderRepository storeOrderRepository, Clock clock) {
        this.storeOrderRepository = storeOrderRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${storefront.orders.expiry-sweep-interval-ms:60000}")
    @Transactional
    public void expirePendingOrders() {
        Instant cutoff = clock.instant();
        List<StoreOrder> expiredOrders = storeOrderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.PENDING_PAYMENT, cutoff, PageRequest.of(0, BATCH_SIZE));

        for (StoreOrder order : expiredOrders) {
            order.setStatus(OrderStatus.EXPIRED);
        }

        if (!expiredOrders.isEmpty()) {
            log.info("Marked {} pending orders as expired", expiredOrders.size());
        }
    }
}
