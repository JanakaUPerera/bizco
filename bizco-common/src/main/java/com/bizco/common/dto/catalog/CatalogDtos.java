package com.bizco.common.dto.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryCreateRequest(String name, Long parentId, String description) {
    }

    public record CategoryUpdateRequest(String name, Long parentId, String description, boolean active, long version) {
    }

    public record CategoryResponse(Long categoryId, String name, Long parentId, String parentName, String description,
                                   boolean active, Instant createdAt, Instant updatedAt, long version) {
    }

    public record UomResponse(Long uomId, String code, String name, String category, boolean active) {
    }

    public record ProductCreateRequest(String sku, String barcode, String name, String description, Long categoryId,
                                       Long uomId, String productType, String taxCategory, BigDecimal costPrice,
                                       BigDecimal sellingPrice, BigDecimal wholesalePrice, BigDecimal reorderPoint,
                                       String imagePath) {
    }

    public record ProductUpdateRequest(String sku, String barcode, String name, String description, Long categoryId,
                                       Long uomId, String productType, String taxCategory, BigDecimal costPrice,
                                       BigDecimal sellingPrice, BigDecimal wholesalePrice, BigDecimal reorderPoint,
                                       boolean active, String imagePath, long version) {
    }

    public record ProductSummaryResponse(UUID productId, String sku, String barcode, String name, String categoryName,
                                         String uomCode, String productType, String taxCategory,
                                         BigDecimal sellingPrice, BigDecimal wholesalePrice, BigDecimal costPrice,
                                         BigDecimal reorderPoint, BigDecimal physicalStock, BigDecimal reservedStock,
                                         BigDecimal availableStock, boolean active, long version) {
    }

    public record ProductDetailResponse(UUID productId, String sku, String barcode, String name, String description,
                                        Long categoryId, String categoryName, Long uomId, String uomCode,
                                        String productType, String taxCategory, BigDecimal costPrice,
                                        BigDecimal sellingPrice, BigDecimal wholesalePrice, BigDecimal reorderPoint,
                                        boolean active, String imagePath, Instant createdAt, Instant updatedAt,
                                        long version) {
    }

    public record ProductSearchResponse(List<ProductSummaryResponse> data, int page, int size,
                                        long totalElements, int totalPages) {
    }

    public record ProductPriceResolutionResponse(UUID productId, String tier, BigDecimal unitPrice) {
    }

    public record ServiceCreateRequest(String serviceCode, String name, String description, String category,
                                       BigDecimal basePrice, Integer estimatedDurationMinutes,
                                       boolean requiresEstimate, Integer warrantyDays) {
    }

    public record ServiceUpdateRequest(String serviceCode, String name, String description, String category,
                                       BigDecimal basePrice, Integer estimatedDurationMinutes,
                                       boolean requiresEstimate, Integer warrantyDays, boolean active, long version) {
    }

    public record ServiceResponse(UUID serviceId, String serviceCode, String name, String description, String category,
                                  BigDecimal basePrice, int estimatedDurationMinutes, boolean requiresEstimate,
                                  int warrantyDays, boolean active, Instant createdAt, Instant updatedAt,
                                  long version) {
    }

    public record ServiceSearchResponse(List<ServiceResponse> data, int page, int size,
                                        long totalElements, int totalPages) {
    }
}
