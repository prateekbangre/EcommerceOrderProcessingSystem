package com.ecommerce.orderprocessing.scheduler;

import com.ecommerce.orderprocessing.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusScheduler.class);

    private final OrderService orderService;

    public OrderStatusScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Periodically transitions all PENDING orders to PROCESSING status.
     * Scheduled via configurable cron (default every 5 minutes).
     */
    @Scheduled(cron = "${order.processing.cron:0 */5 * * * *}")
    public void processPendingOrdersJob() {
        log.info("OrderStatusScheduler triggered: starting batch transition...");
        int count = orderService.processPendingOrders();
        log.info("OrderStatusScheduler completed: {} order(s) shifted to PROCESSING", count);
    }
}
