package com.pharmacy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Currency calculations use decimal arithmetic; the final line absorbs allocation remainders. */
public final class CheckoutTotals {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    public enum Discount { NONE, FIXED, PERCENT }
    public record Input(BigDecimal unitPrice, int quantity, BigDecimal discountPercent, BigDecimal taxPercent) {}
    public record Line(BigDecimal gross, BigDecimal discount, BigDecimal taxable, BigDecimal tax) {}
    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal taxableBase,
                         BigDecimal cartDiscount, BigDecimal discountableSubtotal, BigDecimal grandTotal, List<Line> lines) {}

    private CheckoutTotals() {}
    public static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal percent(BigDecimal base, BigDecimal rate) { return money(base.multiply(rate).divide(HUNDRED)); }

    public static Totals calculate(List<Input> inputs, Discount mode, BigDecimal requested, BigDecimal maximumPercent) {
        if (requested.signum() < 0 || maximumPercent.signum() < 0) throw new IllegalArgumentException("Discount cannot be negative.");
        List<BigDecimal> gross = new ArrayList<>(), productDiscounts = new ArrayList<>(), bases = new ArrayList<>();
        BigDecimal subtotal = ZERO, discountable = ZERO;
        for (Input input : inputs) {
            if (input.quantity <= 0 || input.unitPrice.signum() < 0 || input.discountPercent.signum() < 0
                    || input.discountPercent.compareTo(HUNDRED) > 0 || input.taxPercent.signum() < 0)
                throw new IllegalArgumentException("Invalid quantity, price, discount or tax.");
            BigDecimal amount = money(input.unitPrice.multiply(BigDecimal.valueOf(input.quantity)));
            BigDecimal discount = percent(amount, input.discountPercent);
            BigDecimal base = amount.subtract(discount);
            gross.add(amount); productDiscounts.add(discount); bases.add(base);
            subtotal = subtotal.add(amount); discountable = discountable.add(base);
        }
        BigDecimal cartDiscount = switch (mode) {
            case NONE -> ZERO;
            case FIXED -> money(requested).min(discountable);
            case PERCENT -> percent(discountable, requested.min(maximumPercent).min(HUNDRED));
        };
        BigDecimal remainingDiscount = cartDiscount, remainingBase = discountable;
        BigDecimal totalDiscount = ZERO, tax = ZERO, taxable = ZERO;
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            BigDecimal base = bases.get(i);
            BigDecimal allocated = remainingBase.signum() == 0 ? ZERO
                    : base.compareTo(remainingBase) == 0 ? remainingDiscount
                    : remainingDiscount.multiply(base).divide(remainingBase, 2, RoundingMode.HALF_UP).min(base);
            remainingDiscount = remainingDiscount.subtract(allocated);
            remainingBase = remainingBase.subtract(base);
            BigDecimal lineDiscount = productDiscounts.get(i).add(allocated);
            BigDecimal lineBase = base.subtract(allocated);
            BigDecimal lineTax = percent(lineBase, inputs.get(i).taxPercent);
            lines.add(new Line(gross.get(i), lineDiscount, lineBase, lineTax));
            totalDiscount = totalDiscount.add(lineDiscount); tax = tax.add(lineTax); taxable = taxable.add(lineBase);
        }
        return new Totals(subtotal, totalDiscount, tax, taxable, cartDiscount, discountable,
                subtotal.subtract(totalDiscount).add(tax), List.copyOf(lines));
    }
}
