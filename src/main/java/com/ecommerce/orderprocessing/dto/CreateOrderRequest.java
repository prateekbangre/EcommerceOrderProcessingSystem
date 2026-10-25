package com.ecommerce.orderprocessing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Payload to create a new customer order")
public record CreateOrderRequest(
        @Schema(description = "Customer email address", example = "candidate@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email must be a valid email address")
        String customerEmail,

        @Schema(description = "List of line items included in the order", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<OrderItemDTO> items
) {
}
