package com.ecommerce.orderprocessing.dto;

import com.ecommerce.orderprocessing.model.Order;
import com.ecommerce.orderprocessing.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Schema(description = "Representation of an order response")
public record OrderResponse(
        @Schema(description = "Unique identifier of the order", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        UUID id,

        @Schema(description = "Customer email address", example = "candidate@example.com")
        String customerEmail,

        @Schema(description = "Optional idempotency key associated with order", example = "idem-9a8b-7c6d-5e4f")
        String idempotencyKey,

        @Schema(description = "Current lifecycle status of the order", example = "PENDING")
        OrderStatus status,

        @Schema(description = "Total monetary amount for the order", example = "109.97")
        BigDecimal totalAmount,

        @Schema(description = "Order creation timestamp in UTC", example = "2026-10-23T04:00:00Z")
        Instant createdAt,

        @Schema(description = "Last update timestamp in UTC", example = "2026-10-23T04:00:00Z")
        Instant updatedAt,

        @Schema(description = "Collection of line items in this order")
        List<OrderItemDTO> items
) {
    public static OrderResponse fromEntity(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderItemDTO> itemDTOs = order.getItems() != null
                ? order.getItems().stream()
                        .map(OrderItemDTO::fromEntity)
                        .toList()
                : Collections.emptyList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerEmail(),
                order.getIdempotencyKey(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                itemDTOs
        );
    }
}
