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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("createOrder should successfully create, calculate subtotal and total, and persist order")
    void testCreateOrder_Success() {
        // Given
        OrderItemDTO item1 = new OrderItemDTO("PROD-101", 2, new BigDecimal("29.99"));
        OrderItemDTO item2 = new OrderItemDTO("PROD-202", 1, new BigDecimal("49.99"));
        CreateOrderRequest request = new CreateOrderRequest("customer@example.com", List.of(item1, item2));

        UUID generatedId = UUID.randomUUID();
        when(orderRepository.existsByIdempotencyKey("idem-key-123")).thenReturn(false);
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(generatedId);
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());
            return order;
        });

        // When
        OrderResponse response = orderService.createOrder(request, "idem-key-123");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(generatedId);
        assertThat(response.customerEmail()).isEqualTo("customer@example.com");
        assertThat(response.idempotencyKey()).isEqualTo("idem-key-123");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.items()).hasSize(2);
        // (2 * 29.99) + (1 * 49.99) = 59.98 + 49.99 = 109.97
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("109.97"));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getCustomerEmail()).isEqualTo("customer@example.com");
        assertThat(savedOrder.getIdempotencyKey()).isEqualTo("idem-key-123");
        assertThat(savedOrder.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("createOrder should throw DuplicateOrderException when idempotency key exists at DB pre-check")
    void testCreateOrder_ThrowsDuplicateOrderException_WhenIdempotencyKeyExists() {
        // Given
        OrderItemDTO item = new OrderItemDTO("PROD-101", 1, new BigDecimal("29.99"));
        CreateOrderRequest request = new CreateOrderRequest("customer@example.com", List.of(item));

        when(orderRepository.existsByIdempotencyKey("duplicate-key")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request, "duplicate-key"))
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessageContaining("duplicate-key");

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createOrder should throw DuplicateOrderException when DataIntegrityViolation occurs at DB insert time")
    void testCreateOrder_ThrowsDuplicateOrderException_WhenDataIntegrityViolationOccursAtDbTime() {
        // Given
        OrderItemDTO item = new OrderItemDTO("PROD-101", 1, new BigDecimal("29.99"));
        CreateOrderRequest request = new CreateOrderRequest("customer@example.com", List.of(item));

        when(orderRepository.existsByIdempotencyKey("concurrent-key")).thenReturn(false);
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation on idempotency_key"));

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request, "concurrent-key"))
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessageContaining("concurrent-key");
    }

    @Test
    @DisplayName("createOrder should throw IllegalArgumentException when duplicate SKUs exist in same order")
    void testCreateOrder_ThrowsIllegalArgumentException_WhenDuplicateSKUsInOrder() {
        // Given
        OrderItemDTO item1 = new OrderItemDTO("PROD-101", 1, new BigDecimal("29.99"));
        OrderItemDTO item2 = new OrderItemDTO("PROD-101", 2, new BigDecimal("29.99"));
        CreateOrderRequest request = new CreateOrderRequest("customer@example.com", List.of(item1, item2));

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate product SKU 'PROD-101' detected");

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("getOrderById should return OrderResponse when order exists")
    void testGetOrderById_Success() {
        // Given
        UUID orderId = UUID.randomUUID();
        Order order = new Order("customer@example.com");
        order.setId(orderId);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.addItem(new OrderItem("PROD-1", 1, new BigDecimal("15.00")));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // When
        OrderResponse response = orderService.getOrderById(orderId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.customerEmail()).isEqualTo("customer@example.com");
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("getOrderById should throw OrderNotFoundException when order does not exist")
    void testGetOrderById_NotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.getOrderById(nonExistentId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(nonExistentId.toString());
        verify(orderRepository).findById(nonExistentId);
    }

    @Test
    @DisplayName("getOrders with pagination and status should delegate to findByStatus with Pageable")
    void testGetOrders_PaginatedWithStatus() {
        // Given
        Order order = new Order("pending@example.com");
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findByStatus(OrderStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        // When
        PagedResponse<OrderResponse> result = orderService.getOrders(OrderStatus.PENDING, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).findByStatus(OrderStatus.PENDING, pageable);
    }

    @Test
    @DisplayName("getOrders with pagination without status should delegate to findAll with Pageable")
    void testGetOrders_PaginatedWithoutStatus() {
        // Given
        Order order = new Order("all@example.com");
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.PROCESSING);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        // When
        PagedResponse<OrderResponse> result = orderService.getOrders(null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        verify(orderRepository).findAll(pageable);
    }

    @Test
    @DisplayName("getOrders unpaged with status should filter by status")
    void testGetOrders_UnpagedWithStatusFilter() {
        // Given
        Order order = new Order("pending@example.com");
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        when(orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PENDING))
                .thenReturn(List.of(order));

        // When
        List<OrderResponse> result = orderService.getOrders(OrderStatus.PENDING);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).findByStatusOrderByCreatedAtDesc(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("cancelOrder should successfully cancel an order when its status is PENDING")
    void testCancelOrder_Success_WhenPending() {
        // Given
        UUID orderId = UUID.randomUUID();
        Order order = new Order("cancel@example.com");
        order.setId(orderId);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // When
        OrderResponse response = orderService.cancelOrder(orderId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"})
    @DisplayName("cancelOrder should throw InvalidOrderStateException when order is not PENDING")
    void testCancelOrder_ThrowsException_WhenNonPendingStatus(OrderStatus nonPendingStatus) {
        // Given
        UUID orderId = UUID.randomUUID();
        Order order = new Order("user@example.com");
        order.setId(orderId);
        order.setStatus(nonPendingStatus);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Cannot cancel order")
                .hasMessageContaining(nonPendingStatus.name());

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelOrder should throw OrderNotFoundException when order does not exist")
    void testCancelOrder_ThrowsException_WhenOrderNotFound() {
        // Given
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("processPendingOrders should invoke atomic repository batch update")
    void testProcessPendingOrders_Success() {
        // Given
        when(orderRepository.updateOrderStatusBatch(eq(OrderStatus.PENDING), eq(OrderStatus.PROCESSING), any(Instant.class)))
                .thenReturn(5);

        // When
        int count = orderService.processPendingOrders();

        // Then
        assertThat(count).isEqualTo(5);
        verify(orderRepository).updateOrderStatusBatch(eq(OrderStatus.PENDING), eq(OrderStatus.PROCESSING), any(Instant.class));
    }
}
