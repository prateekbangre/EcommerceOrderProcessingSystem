package com.ecommerce.orderprocessing.dto;

import com.ecommerce.orderprocessing.model.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Order line item details")
public record OrderItemDTO(
        @Schema(description = "Unique identifier of the order item", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Product Stock Keeping Unit (SKU)", example = "PROD-101", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Product SKU is required")
        String productSku,

        @Schema(description = "Quantity of the product (minimum 1)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        @Schema(description = "Unit price of the product (greater than 0)", example = "29.99", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
        BigDecimal unitPrice,

        @Schema(description = "Computed subtotal (quantity * unit price)", example = "59.98")
        BigDecimal subtotal
) {
    public OrderItemDTO(String productSku, Integer quantity, BigDecimal unitPrice) {
        this(null, productSku, quantity, unitPrice, null);
    }

    public static OrderItemDTO fromEntity(OrderItem item) {
        if (item == null) {
            return null;
        }
        return new OrderItemDTO(
                item.getId(),
                item.getProductSku(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }

    public OrderItem toEntity() {
        return new OrderItem(productSku, quantity, unitPrice);
    }
}
