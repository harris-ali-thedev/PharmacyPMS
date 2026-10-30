package com.pharmacy.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutTotalsTest {
    private BigDecimal n(String value) { return new BigDecimal(value); }
    private CheckoutTotals.Input line(String price, int qty, String discount, String tax) {
        return new CheckoutTotals.Input(n(price), qty, n(discount), n(tax));
    }
    @Test void decimalPricesDoNotDrift() {
        var total = CheckoutTotals.calculate(List.of(line("0.10", 3, "0", "0")), CheckoutTotals.Discount.NONE, n("0"), n("100"));
        assertEquals(n("0.30"), total.grandTotal());
    }
    @Test void fixedDiscountAllocatesEveryPaisaAndTaxesAfterDiscount() {
        var total = CheckoutTotals.calculate(List.of(line("1", 1, "0", "10"), line("1", 1, "0", "10"), line("1", 1, "0", "10")),
                CheckoutTotals.Discount.FIXED, n("1"), n("100"));
        assertEquals(n("1.00"), total.discount());
        assertEquals(n("2.00"), total.taxableBase());
        assertEquals(n("0.21"), total.tax());
        assertEquals(n("2.21"), total.grandTotal());
        assertEquals(total.discount(), total.lines().stream().map(CheckoutTotals.Line::discount).reduce(n("0.00"), BigDecimal::add));
    }
    @Test void productDiscountPrecedesRoleLimitedCartDiscount() {
        var total = CheckoutTotals.calculate(List.of(line("100", 1, "10", "15")), CheckoutTotals.Discount.PERCENT, n("90"), n("5"));
        assertEquals(n("14.50"), total.discount());
        assertEquals(n("12.83"), total.tax());
        assertEquals(n("98.33"), total.grandTotal());
    }
    @Test void oversizedDiscountCannotCreateNegativeSale() {
        var total = CheckoutTotals.calculate(List.of(line("10", 1, "100", "20")), CheckoutTotals.Discount.FIXED, n("1000"), n("100"));
        assertEquals(n("0.00"), total.grandTotal());
    }
    @Test void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CheckoutTotals.calculate(List.of(line("10", -1, "0", "0")), CheckoutTotals.Discount.NONE, n("0"), n("100")));
        assertThrows(IllegalArgumentException.class, () -> CheckoutTotals.calculate(List.of(line("10", 1, "101", "0")), CheckoutTotals.Discount.NONE, n("0"), n("100")));
    }
}
