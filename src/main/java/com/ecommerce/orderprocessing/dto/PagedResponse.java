package com.ecommerce.orderprocessing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Generic paginated response wrapper")
public record PagedResponse<T>(
        @Schema(description = "List of items in the current page")
        List<T> content,

        @Schema(description = "Current page number (zero-based)", example = "0")
        int pageNumber,

        @Schema(description = "Page size", example = "10")
        int pageSize,

        @Schema(description = "Total number of elements across all pages", example = "42")
        long totalElements,

        @Schema(description = "Total number of pages", example = "5")
        int totalPages,

        @Schema(description = "Whether this is the first page", example = "true")
        boolean isFirst,

        @Schema(description = "Whether this is the last page", example = "false")
        boolean isLast,

        @Schema(description = "Whether there is a subsequent page", example = "true")
        boolean hasNext,

        @Schema(description = "Whether there is a previous page", example = "false")
        boolean hasPrevious
) {
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
