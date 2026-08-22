package com.bizco.server.inventory.application;

import com.bizco.common.dto.inventory.StockDtos.LowStockSummaryResponse;
import com.bizco.common.dto.inventory.StockDtos.StockLevelResponse;
import com.bizco.common.dto.inventory.StockDtos.StockLevelSearchResponse;
import com.bizco.common.dto.inventory.StockDtos.StockMovementResponse;
import com.bizco.common.dto.inventory.StockDtos.StockMovementSearchResponse;
import com.bizco.server.catalog.domain.Product;
import com.bizco.server.catalog.infrastructure.ProductRepository;
import com.bizco.server.inventory.domain.StockMovement;
import com.bizco.server.inventory.domain.StockReferenceType;
import com.bizco.server.inventory.infrastructure.StockLevelRepository;
import com.bizco.server.inventory.infrastructure.StockLevelRow;
import com.bizco.server.inventory.infrastructure.StockMovementRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of Inventory: movement history, on-hand/reserved/available levels, and low-stock
 *  (12.3, 12.5, 12.8 - REC-STK-001..003). Never mutates {@code stock_movements}; see
 *  {@link StockPostingService} for the write path. */
@Service
public class StockQueryService {

    private final StockMovementRepository stockMovementRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;

    public StockQueryService(final StockMovementRepository stockMovementRepository,
                             final StockLevelRepository stockLevelRepository,
                             final ProductRepository productRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public StockMovementSearchResponse movementHistory(final UUID productId, final int page, final int size) {
        return movementHistory(productId, null, page, size);
    }

    /** Phase 6 Week 19 (task 19.3): {@code productVariantId}, when given, narrows history to that
     *  one variant - the new variant-aware stock screen's drill-down; {@code null} keeps today's
     *  whole-product history for every other caller. */
    @Transactional(readOnly = true)
    public StockMovementSearchResponse movementHistory(final UUID productId, final UUID productVariantId,
                                                        final int page, final int size) {
        final Pageable pageable = pageable(page, size);
        final Page<StockMovement> result = productVariantId != null
                ? stockMovementRepository.findByProductVariantIdOrderByCreatedAtDesc(productVariantId, pageable)
                : stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
        return toMovementResponse(result);
    }

    @Transactional(readOnly = true)
    public StockMovementSearchResponse movementsFor(final StockReferenceType referenceType, final UUID referenceId,
                                                     final int page, final int size) {
        final Page<StockMovement> result = stockMovementRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                referenceType, referenceId, pageable(page, size));
        return toMovementResponse(result);
    }

    @Transactional(readOnly = true)
    public StockLevelResponse levelFor(final UUID productId) {
        return toLevelResponse(stockLevelRepository.levelFor(productId));
    }

    @Transactional(readOnly = true)
    public StockLevelRepository.VariantStockLevel levelForVariant(final UUID productVariantId) {
        return stockLevelRepository.levelForVariant(productVariantId);
    }

    @Transactional(readOnly = true)
    public StockLevelSearchResponse search(final String q, final int page, final int size) {
        final Page<StockLevelRow> result = stockLevelRepository.search(blankToNull(q), pageable(page, size));
        return new StockLevelSearchResponse(result.getContent().stream().map(this::toLevelResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** REC-STK-003: the low-stock report and the dashboard low-stock count must agree because both
     *  read this same query. */
    @Transactional(readOnly = true)
    public StockLevelSearchResponse lowStock(final int page, final int size) {
        final Page<StockLevelRow> result = stockLevelRepository.lowStock(pageable(page, size));
        return new StockLevelSearchResponse(result.getContent().stream().map(this::toLevelResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LowStockSummaryResponse lowStockSummary() {
        return new LowStockSummaryResponse(stockLevelRepository.lowStockCount());
    }

    private StockMovementSearchResponse toMovementResponse(final Page<StockMovement> result) {
        final Map<UUID, Product> products = productRepository
                .findAllById(result.getContent().stream().map(StockMovement::getProductId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(Product::getId, Function.identity()));
        final List<StockMovementResponse> data = result.getContent().stream().map(movement -> {
            final Product product = products.get(movement.getProductId());
            return new StockMovementResponse(movement.getId(), movement.getProductId(),
                    product == null ? null : product.getSku(), product == null ? null : product.getName(),
                    movement.getMovementType().name(), movement.getQuantity(), movement.getReferenceType().name(),
                    movement.getReferenceId(), movement.getSourceLineId(), movement.getNotes(),
                    movement.getCreatedBy(), movement.getCreatedAt());
        }).toList();
        return new StockMovementSearchResponse(data, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    private StockLevelResponse toLevelResponse(final StockLevelRow row) {
        final boolean lowStock = row.reorderPoint() != null
                && row.availableStock().compareTo(row.reorderPoint()) <= 0;
        return new StockLevelResponse(row.productId(), row.productVariantId(), row.sku(), row.name(),
                row.reorderPoint(), row.physicalStock(), row.reservedStock(), row.availableStock(), lowStock);
    }

    private Pageable pageable(final int page, final int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
