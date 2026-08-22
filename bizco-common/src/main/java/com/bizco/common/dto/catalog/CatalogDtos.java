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

    public record BrandCreateRequest(String name, String description, String logoPath) {
    }

    public record BrandUpdateRequest(String name, String description, String logoPath, boolean active, long version) {
    }

    public record BrandResponse(Long brandId, String name, String description, String logoPath, boolean active,
                                Instant createdAt, Instant updatedAt, long version) {
    }

    public record AttributeCreateRequest(String name, String dataType) {
    }

    public record AttributeResponse(Long attributeId, String name, String dataType, Instant createdAt) {
    }

    public record AttributeValueCreateRequest(String value) {
    }

    public record AttributeValueResponse(Long attributeValueId, Long attributeId, String value) {
    }

    public record CategoryAttributeAssignRequest(Long attributeId, boolean required) {
    }

    public record CategoryAttributeResponse(Long categoryAttributeId, Long categoryId, Long attributeId,
                                            String attributeName, String dataType, boolean required) {
    }

    public record ProductCreateRequest(String sku, String barcode, String name, String description, Long categoryId,
                                       Long brandId, Long uomId, String productType, String taxCategory,
                                       BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal wholesalePrice,
                                       BigDecimal reorderPoint, String imagePath) {
    }

    public record ProductUpdateRequest(String sku, String barcode, String name, String description, Long categoryId,
                                       Long brandId, Long uomId, String productType, String taxCategory,
                                       BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal wholesalePrice,
                                       BigDecimal reorderPoint, boolean active, String imagePath, long version) {
    }

    public record ProductSummaryResponse(UUID productId, String sku, String barcode, String name, String categoryName,
                                         String brandName, String uomCode, String productType, String taxCategory,
                                         BigDecimal sellingPrice, BigDecimal wholesalePrice, BigDecimal costPrice,
                                         BigDecimal reorderPoint, BigDecimal physicalStock, BigDecimal reservedStock,
                                         BigDecimal availableStock, boolean active, long version) {
    }

    /** Phase 6 Week 19 (task 19.1): {@code GET /products/barcode/{barcode}} result. {@code
     *  resolvedVariantId} always names one concrete variant (the matched variant itself, or the
     *  product's default variant when the barcode only matched at the product level);
     *  {@code variantSpecific} tells the caller whether that resolution is already unambiguous
     *  (a variant's own barcode matched) or whether the product may still have other variants
     *  worth prompting for (a product-level barcode matched). */
    public record ProductBarcodeResponse(ProductSummaryResponse product, UUID resolvedVariantId,
                                         boolean variantSpecific) {
    }

    public record ProductDetailResponse(UUID productId, String sku, String barcode, String name, String description,
                                        Long categoryId, String categoryName, Long brandId, String brandName,
                                        Long uomId, String uomCode, String productType, String taxCategory,
                                        BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal wholesalePrice,
                                        BigDecimal reorderPoint, boolean active, String imagePath, Instant createdAt,
                                        Instant updatedAt, long version) {
    }

    public record ProductSearchResponse(List<ProductSummaryResponse> data, int page, int size,
                                        long totalElements, int totalPages) {
    }

    public record ProductPriceResolutionResponse(UUID productId, String tier, BigDecimal unitPrice) {
    }

    public record VariantCreateRequest(String sku, String barcode, String variantLabel, BigDecimal costPrice,
                                       BigDecimal sellingPrice, BigDecimal wholesalePrice, BigDecimal reorderPoint,
                                       String imagePath) {
    }

    public record VariantUpdateRequest(String sku, String barcode, String variantLabel, BigDecimal costPrice,
                                       BigDecimal sellingPrice, BigDecimal wholesalePrice, BigDecimal reorderPoint,
                                       boolean active, String imagePath, long version) {
    }

    public record VariantResponse(UUID productVariantId, UUID productId, String sku, String barcode,
                                  String variantLabel, BigDecimal costPrice, BigDecimal sellingPrice,
                                  BigDecimal wholesalePrice, BigDecimal reorderPoint, boolean active,
                                  String imagePath, boolean defaultVariant, Instant createdAt, Instant updatedAt,
                                  long version) {
    }

    public record VariantAttributeSelection(Long attributeId, List<Long> attributeValueIds) {
    }

    public record VariantGenerateRequest(List<VariantAttributeSelection> selections) {
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
