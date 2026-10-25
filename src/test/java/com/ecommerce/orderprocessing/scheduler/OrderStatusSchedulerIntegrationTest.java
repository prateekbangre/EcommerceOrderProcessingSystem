package com.ecommerce.orderprocessing.scheduler;

import com.ecommerce.orderprocessing.model.Order;
import com.ecommerce.orderprocessing.model.OrderItem;
import com.ecommerce.orderprocessing.model.OrderStatus;
import com.ecommerce.orderprocessing.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class OrderStatusSchedulerIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusScheduler orderStatusScheduler;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("processPendingOrdersJob should atomically transition all PENDING orders to PROCESSING while preserving others")
    void testAtomicStatusTransitionsOnSchedulerTrigger() {
        // Given: Seed H2 database with orders in various initial states
        Order pendingOrder1 = createOrderWithItem("pending1@example.com", OrderStatus.PENDING, "SKU-1", 1, new BigDecimal("19.99"));
        Order pendingOrder2 = createOrderWithItem("pending2@example.com", OrderStatus.PENDING, "SKU-2", 2, new BigDecimal("49.99"));
        Order pendingOrder3 = createOrderWithItem("pending3@example.com", OrderStatus.PENDING, "SKU-3", 3, new BigDecimal("9.99"));

        Order shippedOrder = createOrderWithItem("shipped@example.com", OrderStatus.SHIPPED, "SKU-4", 1, new BigDecimal("99.99"));
        Order deliveredOrder = createOrderWithItem("delivered@example.com", OrderStatus.DELIVERED, "SKU-5", 1, new BigDecimal("120.00"));
        Order cancelledOrder = createOrderWithItem("cancelled@example.com", OrderStatus.CANCELLED, "SKU-6", 1, new BigDecimal("35.50"));

        orderRepository.saveAll(List.of(
                pendingOrder1,
                pendingOrder2,
                pendingOrder3,
                shippedOrder,
                deliveredOrder,
                cancelledOrder
        ));

        assertThat(orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PENDING)).hasSize(3);
        assertThat(orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PROCESSING)).isEmpty();

        // When: Explicitly fire the cron logic
        orderStatusScheduler.processPendingOrdersJob();

        // Then: Verify atomic status transitions
        // 1. All previously PENDING orders must now be in PROCESSING status
        List<Order> processingOrders = orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PROCESSING);
        assertThat(processingOrders).hasSize(3);

        List<UUID> processingIds = processingOrders.stream().map(Order::getId).toList();
        assertThat(processingIds).containsExactlyInAnyOrder(
                pendingOrder1.getId(),
                pendingOrder2.getId(),
                pendingOrder3.getId()
        );

        // 2. Zero orders should remain in PENDING status
        List<Order> remainingPending = orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PENDING);
        assertThat(remainingPending).isEmpty();

        // 3. Other entity states must remain strictly untouched
        Order reloadedShipped = orderRepository.findById(shippedOrder.getId()).orElseThrow();
        assertThat(reloadedShipped.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        Order reloadedDelivered = orderRepository.findById(deliveredOrder.getId()).orElseThrow();
        assertThat(reloadedDelivered.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        Order reloadedCancelled = orderRepository.findById(cancelledOrder.getId()).orElseThrow();
        assertThat(reloadedCancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // 4. Triggering the job again when there are no PENDING orders should safely do nothing
        orderStatusScheduler.processPendingOrdersJob();
        assertThat(orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PROCESSING)).hasSize(3);
    }

    private Order createOrderWithItem(
            String email,
            OrderStatus status,
            String sku,
            int quantity,
            BigDecimal price
    ) {
        Order order = new Order(email);
        order.setStatus(status);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.addItem(new OrderItem(sku, quantity, price));
        return order;
    }
}
