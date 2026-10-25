package com.ecommerce.orderprocessing.repository;

import com.ecommerce.orderprocessing.model.Order;
import com.ecommerce.orderprocessing.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Atomically updates all orders in currentStatus to newStatus directly in the database.
     * Prevents race conditions and lost updates without loading entire collections into memory.
     *
     * @param currentStatus the current status to match (e.g. PENDING)
     * @param newStatus     the new status to set (e.g. PROCESSING)
     * @param updatedAt     the timestamp marking the transition
     * @return number of affected order rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :updatedAt WHERE o.status = :currentStatus")
    int updateOrderStatusBatch(
            @Param("currentStatus") OrderStatus currentStatus,
            @Param("newStatus") OrderStatus newStatus,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * Finds orders filtered by status, ordered by newest first (list unpaged).
     *
     * @param status the order status filter
     * @return list of matching orders
     */
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    /**
     * Finds orders filtered by status with full pagination and sorting support.
     *
     * @param status   the order status filter
     * @param pageable pagination parameters
     * @return page of matching orders
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Finds an order by its unique idempotency key.
     *
     * @param idempotencyKey unique key provided by client
     * @return optional containing the order if found
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /**
     * Checks if an order with the given idempotency key exists.
     *
     * @param idempotencyKey unique key provided by client
     * @return true if an order already exists with this key
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Finds all orders ordered by creation timestamp descending.
     *
     * @return list of all orders
     */
    List<Order> findAllByOrderByCreatedAtDesc();
}
