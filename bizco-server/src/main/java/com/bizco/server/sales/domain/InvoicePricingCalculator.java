package com.bizco.server.sales.domain;

import com.bizco.server.catalog.domain.TaxCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure calculation domain service for {@code Invoice} (DomainModel.md &sect;9.7). Stateless and
 * has no persistence dependency, so it can be reused identically by draft line preview, the
 * {@code /preview} endpoint, and eventual posting - the same numbers a cashier previews are the
 * numbers that get snapshotted.
 *
 * <p>Rounding rule (DatabaseDesign.md &sect;33.2): every stored monetary output (discount,
 * taxable, VAT, line total) is independently rounded to 2 decimal places with
 * {@link RoundingMode#HALF_UP}; nothing here uses binary floating point.
 *
 * <p>Invoice-level discount allocation (DatabaseDesign.md &sect;33.3): allocated proportionally
 * across lines by each line's pre-invoice-discount taxable amount. Naive proportional rounding
 * would not guarantee the allocations sum to exactly the invoice discount amount, so every line
 * except the last (by line number) is rounded normally, and the last line receives the exact
 * remainder - guaranteeing {@code SUM(line allocations) == invoice discount amount} by
 * construction, not by coincidence.
 */
public final class InvoicePricingCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public InvoiceCalculation calculate(final List<LineInput> lines, final DiscountInput invoiceDiscount) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An invoice must have at least one line to calculate");
        }
        final List<LineInput> ordered = lines.stream()
                .sorted((a, b) -> Integer.compare(a.lineNumber(), b.lineNumber()))
                .toList();

        final List<PreDiscountLine> preDiscountLines = ordered.stream().map(this::calculateLineBeforeInvoiceDiscount).toList();
        final BigDecimal eligibleTaxableTotal = sum(preDiscountLines, PreDiscountLine::taxableAmount);
        final BigDecimal invoiceDiscountAmount = round(discountAmount(eligibleTaxableTotal, invoiceDiscount));

        final List<LineResult> results = allocateInvoiceDiscount(preDiscountLines, invoiceDiscountAmount, eligibleTaxableTotal);

        final BigDecimal taxableAmount = sum(results, LineResult::taxableAmount);
        final BigDecimal vatAmount = sum(results, LineResult::vatAmount);
        final BigDecimal totalAmount = sum(results, LineResult::lineTotalInclVat);

        return new InvoiceCalculation(results, eligibleTaxableTotal, invoiceDiscountAmount, taxableAmount, vatAmount, totalAmount);
    }

    private PreDiscountLine calculateLineBeforeInvoiceDiscount(final LineInput line) {
        final BigDecimal gross = line.quantity().multiply(line.unitPrice());
        final BigDecimal discountAmount = round(discountAmount(gross, line.discount()));
        final BigDecimal taxableAmount = round(gross.subtract(discountAmount));
        return new PreDiscountLine(line, discountAmount, taxableAmount);
    }

    private List<LineResult> allocateInvoiceDiscount(final List<PreDiscountLine> preDiscountLines,
                                                      final BigDecimal invoiceDiscountAmount,
                                                      final BigDecimal eligibleTaxableTotal) {
        final List<LineResult> results = new ArrayList<>(preDiscountLines.size());
        BigDecimal allocatedSoFar = BigDecimal.ZERO;
        for (int i = 0; i < preDiscountLines.size(); i++) {
            final PreDiscountLine preDiscountLine = preDiscountLines.get(i);
            final boolean isLastLine = i == preDiscountLines.size() - 1;
            final BigDecimal allocatedDiscount;
            if (isLastLine) {
                allocatedDiscount = invoiceDiscountAmount.subtract(allocatedSoFar);
            } else if (eligibleTaxableTotal.compareTo(BigDecimal.ZERO) == 0) {
                allocatedDiscount = round(BigDecimal.ZERO);
            } else {
                final BigDecimal ratio = preDiscountLine.taxableAmount().divide(eligibleTaxableTotal, 10, ROUNDING);
                allocatedDiscount = round(invoiceDiscountAmount.multiply(ratio));
            }
            allocatedSoFar = allocatedSoFar.add(allocatedDiscount);
            results.add(finalizeLine(preDiscountLine, allocatedDiscount));
        }
        return results;
    }

    private LineResult finalizeLine(final PreDiscountLine preDiscountLine, final BigDecimal allocatedInvoiceDiscount) {
        final LineInput line = preDiscountLine.input();
        final BigDecimal finalTaxable = round(preDiscountLine.taxableAmount().subtract(allocatedInvoiceDiscount));
        final BigDecimal vatAmount = line.taxCategory() == TaxCategory.STANDARD
                ? round(finalTaxable.multiply(line.vatRate()).divide(BigDecimal.valueOf(100), 10, ROUNDING))
                : round(BigDecimal.ZERO);
        final BigDecimal lineTotal = round(finalTaxable.add(vatAmount));
        return new LineResult(line.lineNumber(), preDiscountLine.discountAmount(), allocatedInvoiceDiscount,
                finalTaxable, line.taxCategory(), line.vatRate(), vatAmount, lineTotal);
    }

    /** Computes a discount amount, never exceeding the base it is applied against. */
    private BigDecimal discountAmount(final BigDecimal base, final DiscountInput discount) {
        if (discount == null || discount.type() == DiscountType.NONE || base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        final BigDecimal raw = discount.type() == DiscountType.PERCENTAGE
                ? base.multiply(discount.value()).divide(BigDecimal.valueOf(100), 10, ROUNDING)
                : discount.value();
        return raw.min(base).max(BigDecimal.ZERO);
    }

    private BigDecimal round(final BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    private <T> BigDecimal sum(final List<T> items, final java.util.function.Function<T, BigDecimal> extractor) {
        return items.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record DiscountInput(DiscountType type, BigDecimal value) {
        public static final DiscountInput NONE = new DiscountInput(DiscountType.NONE, BigDecimal.ZERO);
    }

    public record LineInput(int lineNumber, BigDecimal quantity, BigDecimal unitPrice, DiscountInput discount,
                            TaxCategory taxCategory, BigDecimal vatRate) {
    }

    private record PreDiscountLine(LineInput input, BigDecimal discountAmount, BigDecimal taxableAmount) {
    }

    public record LineResult(int lineNumber, BigDecimal lineDiscountAmount, BigDecimal invoiceDiscountAllocated,
                             BigDecimal taxableAmount, TaxCategory taxCategory, BigDecimal vatRate,
                             BigDecimal vatAmount, BigDecimal lineTotalInclVat) {
    }

    public record InvoiceCalculation(List<LineResult> lines, BigDecimal subtotal, BigDecimal discountAmount,
                                     BigDecimal taxableAmount, BigDecimal vatAmount, BigDecimal totalAmount) {
    }
}
