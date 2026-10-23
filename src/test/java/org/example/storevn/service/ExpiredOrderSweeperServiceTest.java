package org.example.storevn.service;

import org.example.storevn.entity.StoreOrder;
import org.example.storevn.enums.OrderStatus;
import org.example.storevn.repository.StoreOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiredOrderSweeperServiceTest {

    @Mock
    private StoreOrderRepository storeOrderRepository;

    @Test
    void expiresPendingOrdersInBoundedBatch() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);
        ExpiredOrderSweeper sweeper = new ExpiredOrderSweeper(storeOrderRepository, clock);
        StoreOrder order = StoreOrder.builder().status(OrderStatus.PENDING_PAYMENT).build();
        when(storeOrderRepository.findByStatusAndExpiresAtBefore(
                eq(OrderStatus.PENDING_PAYMENT), eq(clock.instant()), any()))
                .thenReturn(List.of(order));

        sweeper.expirePendingOrders();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(storeOrderRepository).findByStatusAndExpiresAtBefore(
                eq(OrderStatus.PENDING_PAYMENT), eq(clock.instant()), any());
    }
}

