package com.ecommerce.orderprocessing.controller;

import com.ecommerce.orderprocessing.dto.CreateOrderRequest;
import com.ecommerce.orderprocessing.dto.OrderItemDTO;
import com.ecommerce.orderprocessing.model.Order;
import com.ecommerce.orderprocessing.model.OrderItem;
import com.ecommerce.orderprocessing.model.OrderStatus;
import com.ecommerce.orderprocessing.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/orders should return 201 Created with Location header and calculated totals")
    void testCreateOrderEndpoint_Success() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "candidate@example.com",
                List.of(
                        new OrderItemDTO("PROD-101", 2, new BigDecimal("29.99")),
                        new OrderItemDTO("PROD-202", 1, new BigDecimal("49.99"))
                )
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-test-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/orders/")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.customerEmail", is("candidate@example.com")))
                .andExpect(jsonPath("$.idempotencyKey", is("idem-test-001")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalAmount", is(109.97)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].subtotal", is(59.98)))
                .andExpect(jsonPath("$.items[1].subtotal", is(49.99)));
    }

    @Test
    @DisplayName("POST /api/v1/orders with duplicate idempotency key at DB time should return 409 Conflict")
    void testCreateOrderEndpoint_DuplicateOrderConflict() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "candidate@example.com",
                List.of(new OrderItemDTO("PROD-101", 1, new BigDecimal("20.00")))
        );

        // First call with Idempotency-Key header - should succeed at DB time
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "duplicate-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotencyKey", is("duplicate-idempotency-key")));

        // Second call with same idempotency key - must return 409 Conflict enforced at DB time
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "duplicate-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("duplicate-idempotency-key")));
    }

    @Test
    @DisplayName("POST /api/v1/orders with duplicate SKUs in items should return 400 Bad Request")
    void testCreateOrderEndpoint_DuplicateSkuRejection() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "candidate@example.com",
                List.of(
                        new OrderItemDTO("SAME-SKU", 1, new BigDecimal("10.00")),
                        new OrderItemDTO("SAME-SKU", 2, new BigDecimal("10.00"))
                )
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Duplicate product SKU 'SAME-SKU' detected")));
    }

    @Test
    @DisplayName("POST /api/v1/orders should return 400 Bad Request when validation fails on email and items")
    void testCreateOrderEndpoint_ValidationFailure() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "not-an-email",
                List.of()
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.customerEmail", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.items", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/orders should return 400 Bad Request on negative quantity or zero price")
    void testCreateOrderEndpoint_InvalidItemValues() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "candidate@example.com",
                List.of(new OrderItemDTO("PROD-1", -1, new BigDecimal("0.00")))
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} should return 200 OK for existing order")
    void testGetOrderById_Success() throws Exception {
        Order order = new Order("candidate@example.com");
        order.setStatus(OrderStatus.PENDING);
        order.addItem(new OrderItem("PROD-1", 1, new BigDecimal("10.00")));
        order = orderRepository.save(order);

        mockMvc.perform(get("/api/v1/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(order.getId().toString())))
                .andExpect(jsonPath("$.customerEmail", is("candidate@example.com")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} should return 404 Not Found for non-existing order")
    void testGetOrderById_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/orders/{id}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} should return 400 Bad Request for malformed UUID string")
    void testGetOrderById_MalformedUUID() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", "not-a-valid-uuid-12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("invalid value")));
    }

    @Test
    @DisplayName("GET /api/v1/orders should return paginated orders")
    void testGetOrders_Paginated() throws Exception {
        Order order1 = new Order("user1@example.com");
        order1.setStatus(OrderStatus.PENDING);
        order1.addItem(new OrderItem("SKU-1", 1, new BigDecimal("10.00")));

        Order order2 = new Order("user2@example.com");
        order2.setStatus(OrderStatus.PROCESSING);
        order2.addItem(new OrderItem("SKU-2", 1, new BigDecimal("20.00")));

        orderRepository.saveAll(List.of(order1, order2));

        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.pageNumber", is(0)))
                .andExpect(jsonPath("$.pageSize", is(10)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/orders?status=PENDING should return filtered paginated results")
    void testGetOrders_FilteredByStatus() throws Exception {
        Order pendingOrder = new Order("pending@example.com");
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.addItem(new OrderItem("SKU-1", 1, new BigDecimal("10.00")));

        Order shippedOrder = new Order("shipped@example.com");
        shippedOrder.setStatus(OrderStatus.SHIPPED);
        shippedOrder.addItem(new OrderItem("SKU-2", 1, new BigDecimal("20.00")));

        orderRepository.saveAll(List.of(pendingOrder, shippedOrder));

        mockMvc.perform(get("/api/v1/orders")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].customerEmail", is("pending@example.com")))
                .andExpect(jsonPath("$.content[0].status", is("PENDING")));
    }

    @Test
    @DisplayName("GET /api/v1/orders with out-of-range page should return empty content with totalElements intact")
    void testGetOrders_OutOfRangePageCornerCase() throws Exception {
        Order order = new Order("user@example.com");
        order.setStatus(OrderStatus.PENDING);
        order.addItem(new OrderItem("SKU-1", 1, new BigDecimal("10.00")));
        orderRepository.save(order);

        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "99")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.pageNumber", is(99)));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel should succeed for PENDING order")
    void testCancelOrder_Success() throws Exception {
        Order order = new Order("cancel@example.com");
        order.setStatus(OrderStatus.PENDING);
        order.addItem(new OrderItem("PROD-1", 1, new BigDecimal("10.00")));
        order = orderRepository.save(order);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(order.getId().toString())))
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel should return 400 Bad Request when order is PROCESSING")
    void testCancelOrder_FailureWhenProcessing() throws Exception {
        Order order = new Order("processing@example.com");
        order.setStatus(OrderStatus.PROCESSING);
        order.addItem(new OrderItem("PROD-1", 1, new BigDecimal("10.00")));
        order = orderRepository.save(order);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Cannot cancel order")));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel should return 404 Not Found for non-existing order")
    void testCancelOrder_FailureWhenNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }
}
