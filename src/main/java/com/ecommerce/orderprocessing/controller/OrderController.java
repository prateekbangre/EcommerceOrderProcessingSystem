package com.ecommerce.orderprocessing.controller;

import com.ecommerce.orderprocessing.dto.CreateOrderRequest;
import com.ecommerce.orderprocessing.dto.ErrorResponse;
import com.ecommerce.orderprocessing.dto.OrderResponse;
import com.ecommerce.orderprocessing.dto.PagedResponse;
import com.ecommerce.orderprocessing.model.OrderStatus;
import com.ecommerce.orderprocessing.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders V1", description = "Production API endpoints for order management, pagination, lifecycle control, and duplicate prevention")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new customer order with line items and optional idempotency key.
     */
    @Operation(
            summary = "Create an order",
            description = "Creates a new order in PENDING status. Recalculates line item subtotals and order total amount. Rejects duplicate submissions with identical idempotency keys."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payload or validation failure",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate order conflict detected",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @Parameter(description = "Optional idempotency key to prevent accidental duplicate submission")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        OrderResponse createdOrder = orderService.createOrder(request, idempotencyKey);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdOrder.id())
                .toUri();

        return ResponseEntity.created(location).body(createdOrder);
    }

    /**
     * Retrieves an order by its unique UUID.
     */
    @Operation(
            summary = "Get order by ID",
            description = "Retrieves an existing order and its line items by UUID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID format",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Order UUID identifier", required = true)
            @PathVariable("id") UUID id
    ) {
        OrderResponse orderResponse = orderService.getOrderById(id);
        return ResponseEntity.ok(orderResponse);
    }

    /**
     * Lists orders with pagination and optional status filter.
     */
    @Operation(
            summary = "List and filter orders with pagination",
            description = "Fetches a paginated slice of orders, optionally filtered by status (PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated orders retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PagedResponse.class)))
    })
    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponse>> getOrders(
            @Parameter(description = "Optional status filter (e.g., PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED)")
            @RequestParam(name = "status", required = false) OrderStatus status,
            @Parameter(description = "Pagination parameters (page, size, sort)")
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PagedResponse<OrderResponse> orders = orderService.getOrders(status, pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * Cancels an order. Only orders in PENDING status can be cancelled.
     */
    @Operation(
            summary = "Cancel an order",
            description = "Transitions an order to CANCELLED status. Strict validation allows cancellation only when current status is PENDING."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Order cannot be cancelled because it is not in PENDING state",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "Order UUID identifier to cancel", required = true)
            @PathVariable("id") UUID id
    ) {
        OrderResponse cancelledOrder = orderService.cancelOrder(id);
        return ResponseEntity.ok(cancelledOrder);
    }
}
