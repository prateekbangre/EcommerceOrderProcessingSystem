package com.ecommerce.orderprocessing.service;

import com.ecommerce.orderprocessing.dto.CreateOrderRequest;
import com.ecommerce.orderprocessing.dto.OrderItemDTO;
import com.ecommerce.orderprocessing.dto.OrderResponse;
import com.ecommerce.orderprocessing.dto.PagedResponse;
import com.ecommerce.orderprocessing.exception.DuplicateOrderException;
import com.ecommerce.orderprocessing.exception.InvalidOrderStateException;
import com.ecommerce.orderprocessing.exception.OrderNotFoundException;
import com.ecommerce.orderprocessing.model.Order;
import com.ecommerce.orderprocessing.model.OrderItem;
import com.ecommerce.orderprocessing.model.OrderStatus;
import com.ecommerce.orderprocessing.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Creates a new order without an idempotency key.
     *
     * @param request the order creation payload
     * @return OrderResponse with generated UUID and computed values
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        return createOrder(request, null);
    }

    /**
     * Creates a new order with duplicate submission validation handled strictly at the DB level.
     * The idempotency key is enforced via the database unique constraint and immediate flush.
     *
     * @param request        the order creation payload (pure business data)
     * @param idempotencyKey optional client idempotency key from HTTP header or caller
     * @return OrderResponse with generated UUID and computed values
     * @throws DuplicateOrderException  if an order with the same idempotency key already exists at DB time
     * @throws IllegalArgumentException if duplicate SKUs are present in the item list
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        log.info("Creating order for customer: {}", request.customerEmail());

        String trimmedKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : null;

        // 1. Pre-check at DB level if idempotency key is provided
        if (trimmedKey != null && orderRepository.existsByIdempotencyKey(trimmedKey)) {
            log.warn("Duplicate order detected at DB pre-check with idempotency key: {}", trimmedKey);
            throw new DuplicateOrderException(
                    "An order with idempotency key '" + trimmedKey + "' has already been placed."
            );
        }

        // 2. Validate against duplicate SKUs within the same order
        if (request.items() != null) {
            Set<String> seenSkus = new HashSet<>();
            for (OrderItemDTO itemDto : request.items()) {
                String normalizedSku = itemDto.productSku().trim().toUpperCase();
                if (!seenSkus.add(normalizedSku)) {
                    throw new IllegalArgumentException(
                            "Duplicate product SKU '" + itemDto.productSku() + "' detected in order items. Combine quantities into a single item."
                    );
                }
            }
        }

        // 3. Construct Order entity
        Order order = new Order(request.customerEmail(), trimmedKey);
        if (request.items() != null) {
            for (OrderItemDTO itemDto : request.items()) {
                OrderItem item = itemDto.toEntity();
                order.addItem(item);
            }
        }

        // 4. Atomic database persistence with unique constraint enforcement at DB time
        try {
            Order savedOrder = orderRepository.saveAndFlush(order);
            log.info("Successfully created order with id: {}", savedOrder.getId());
            return OrderResponse.fromEntity(savedOrder);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Database unique constraint violation for idempotency key: {}", trimmedKey, ex);
            throw new DuplicateOrderException(
                    "An order with idempotency key '" + trimmedKey + "' has already been placed."
            );
        }
    }

    /**
     * Retrieves an order by its unique UUID.
     *
     * @param id the UUID of the order
     * @return OrderResponse representing the order
     * @throws OrderNotFoundException if no order matches the ID
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.fromEntity(order);
    }

    /**
     * Retrieves orders with pagination and sorting, optionally filtered by status.
     *
     * @param status   optional status filter
     * @param pageable page request with page, size, and sorting
     * @return PagedResponse containing OrderResponse page
     */
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrders(OrderStatus status, Pageable pageable) {
        Page<Order> orderPage;
        if (status != null) {
            orderPage = orderRepository.findByStatus(status, pageable);
        } else {
            orderPage = orderRepository.findAll(pageable);
        }

        return PagedResponse.of(orderPage.map(OrderResponse::fromEntity));
    }

    /**
     * Retrieves unpaged orders, optionally filtered by status.
     *
     * @param status optional status to filter by
     * @return list of matching OrderResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(OrderStatus status) {
        List<Order> orders;
        if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }

        return orders.stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    /**
     * Cancels an order. Cancellation is strictly allowed only when the order is in PENDING status.
     * Uses REPEATABLE_READ isolation level to guard against phantom reads and concurrent status updates.
     *
     * @param id the UUID of the order to cancel
     * @return OrderResponse with updated CANCELLED status
     * @throws OrderNotFoundException      if order is not found
     * @throws InvalidOrderStateException if order status is anything other than PENDING
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public OrderResponse cancelOrder(UUID id) {
        log.info("Attempting to cancel order id: {}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Cancellation rejected for order id: {}. Current status is: {}", id, order.getStatus());
            throw new InvalidOrderStateException(id, order.getStatus(), "cancel");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        Order updatedOrder = orderRepository.save(order);
        log.info("Order id: {} successfully transitioned to CANCELLED", id);

        return OrderResponse.fromEntity(updatedOrder);
    }

    /**
     * Atomically transitions all PENDING orders to PROCESSING directly in the database.
     *
     * @return count of orders shifted to PROCESSING
     */
    @Transactional
    public int processPendingOrders() {
        log.info("Executing atomic transition of PENDING orders to PROCESSING");
        int updatedCount = orderRepository.updateOrderStatusBatch(
                OrderStatus.PENDING,
                OrderStatus.PROCESSING,
                Instant.now()
        );
        log.info("Successfully shifted {} order(s) from PENDING to PROCESSING", updatedCount);
        return updatedCount;
    }
}
