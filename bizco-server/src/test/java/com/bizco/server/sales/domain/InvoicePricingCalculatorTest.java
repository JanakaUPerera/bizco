package com.bizco.server.sales.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bizco.server.catalog.domain.TaxCategory;
import com.bizco.server.sales.domain.InvoicePricingCalculator.DiscountInput;
import com.bizco.server.sales.domain.InvoicePricingCalculator.InvoiceCalculation;
import com.bizco.server.sales.domain.InvoicePricingCalculator.LineInput;
import com.bizco.server.sales.domain.InvoicePricingCalculator.LineResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoicePricingCalculatorTest {

    private final InvoicePricingCalculator calculator = new InvoicePricingCalculator();

    @Test
    void taxCalc001StandardVatLineAt18Percent() {
        final InvoiceCalculation result = calculator.calculate(
                List.of(line(1, "1", "1000.00", TaxCategory.STANDARD, "18")), DiscountInput.NONE);

        final LineResult line = result.lines().get(0);
        assertEquals(new BigDecimal("1000.00"), line.taxableAmount());
        assertEquals(new BigDecimal("180.00"), line.vatAmount());
        assertEquals(new BigDecimal("1180.00"), line.lineTotalInclVat());
        assertEquals(new BigDecimal("180.00"), result.vatAmount());
        assertEquals(new BigDecimal("1180.00"), result.totalAmount());
    }

    @Test
    void taxCalc002ExemptLineHasZeroVat() {
        final InvoiceCalculation result = calculator.calculate(
                List.of(line(1, "1", "1000.00", TaxCategory.EXEMPT, "18")), DiscountInput.NONE);

        assertEquals(BigDecimal.ZERO.setScale(2), result.lines().get(0).vatAmount());
    }

    @Test
    void taxCalc003ZeroRatedLineHasZeroVatAndKeepsCategory() {
        final InvoiceCalculation result = calculator.calculate(
                List.of(line(1, "1", "1000.00", TaxCategory.ZERO_RATED, "18")), DiscountInput.NONE);

        final LineResult line = result.lines().get(0);
        assertEquals(BigDecimal.ZERO.setScale(2), line.vatAmount());
        assertEquals(TaxCategory.ZERO_RATED, line.taxCategory());
    }

    @Test
    void taxRound001HalfUpRoundsMidpointsUpRegardlessOfParity() {
        // 1 x 33.345 = 33.345, the exact midpoint between 33.34 and 33.35.
        // HALF_UP always rounds up here; HALF_EVEN would round down to 33.34 since 4 is even.
        final InvoiceCalculation result = calculator.calculate(
                List.of(line(1, "1", "33.345", TaxCategory.EXEMPT, "0")), DiscountInput.NONE);

        assertEquals(new BigDecimal("33.35"), result.lines().get(0).taxableAmount());
    }

    @Test
    void taxRound002HeaderTotalsReconcileToSumOfLines() {
        final InvoiceCalculation result = calculator.calculate(List.of(
                line(1, "3", "10.333", TaxCategory.STANDARD, "18"),
                line(2, "1", "7.777", TaxCategory.STANDARD, "18"),
                line(3, "2", "5.555", TaxCategory.EXEMPT, "18")), DiscountInput.NONE);

        final BigDecimal sumVat = result.lines().stream().map(LineResult::vatAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal sumTotal = result.lines().stream().map(LineResult::lineTotalInclVat).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(sumVat, result.vatAmount());
        assertEquals(sumTotal, result.totalAmount());
    }

    @Test
    void taxDisc001InvoiceDiscountAllocationSumsExactlyWithDeterministicRemainder() {
        final InvoiceCalculation result = calculator.calculate(List.of(
                        line(1, "1", "333.33", TaxCategory.STANDARD, "0"),
                        line(2, "1", "333.33", TaxCategory.STANDARD, "0"),
                        line(3, "1", "333.34", TaxCategory.STANDARD, "0")),
                new DiscountInput(DiscountType.PERCENTAGE, new BigDecimal("10")));

        final List<LineResult> lines = result.lines();
        assertEquals(new BigDecimal("33.33"), lines.get(0).invoiceDiscountAllocated());
        assertEquals(new BigDecimal("33.33"), lines.get(1).invoiceDiscountAllocated());
        assertEquals(new BigDecimal("33.34"), lines.get(2).invoiceDiscountAllocated());

        final BigDecimal sumAllocated = lines.stream().map(LineResult::invoiceDiscountAllocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(result.discountAmount(), sumAllocated);
        assertEquals(new BigDecimal("100.00"), result.discountAmount());

        final BigDecimal sumTaxable = lines.stream().map(LineResult::taxableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(sumTaxable, result.taxableAmount());
    }

    @Test
    void lineLevelPercentageDiscountReducesTaxableAmount() {
        final InvoiceCalculation result = calculator.calculate(List.of(
                new LineInput(1, new BigDecimal("2"), new BigDecimal("500.00"),
                        new DiscountInput(DiscountType.PERCENTAGE, new BigDecimal("10")),
                        TaxCategory.STANDARD, new BigDecimal("18"))), DiscountInput.NONE);

        final LineResult line = result.lines().get(0);
        assertEquals(new BigDecimal("100.00"), line.lineDiscountAmount());
        assertEquals(new BigDecimal("900.00"), line.taxableAmount());
        assertEquals(new BigDecimal("162.00"), line.vatAmount());
    }

    @Test
    void fixedDiscountNeverExceedsLineGrossAmount() {
        final InvoiceCalculation result = calculator.calculate(List.of(
                new LineInput(1, BigDecimal.ONE, new BigDecimal("50.00"),
                        new DiscountInput(DiscountType.FIXED, new BigDecimal("500.00")),
                        TaxCategory.EXEMPT, BigDecimal.ZERO)), DiscountInput.NONE);

        final LineResult line = result.lines().get(0);
        assertEquals(new BigDecimal("50.00"), line.lineDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), line.taxableAmount());
    }

    @Test
    void emptyLineListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of(), DiscountInput.NONE));
    }

    private LineInput line(final int lineNumber, final String quantity, final String unitPrice,
                           final TaxCategory taxCategory, final String vatRate) {
        return new LineInput(lineNumber, new BigDecimal(quantity), new BigDecimal(unitPrice), DiscountInput.NONE,
                taxCategory, new BigDecimal(vatRate));
    }
}
