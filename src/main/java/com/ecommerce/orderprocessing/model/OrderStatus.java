package com.ecommerce.orderprocessing.model;

/**
 * Order status representing the strict lifecycle transitions:
 * PENDING -> PROCESSING -> SHIPPED -> DELIVERED
 * Orders in PENDING can also transition to CANCELLED.
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
