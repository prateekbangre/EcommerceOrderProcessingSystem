package com.ecommerce.orderprocessing.exception;

import com.ecommerce.orderprocessing.model.OrderStatus;

import java.util.UUID;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(UUID orderId, OrderStatus currentStatus, String targetAction) {
        super(String.format("Cannot %s order with id '%s' because current status is '%s'. Only orders in 'PENDING' status can be cancelled.",
                targetAction, orderId, currentStatus));
    }
}
