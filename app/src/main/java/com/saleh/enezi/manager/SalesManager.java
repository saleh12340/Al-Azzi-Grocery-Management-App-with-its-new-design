package com.saleh.enezi.manager;

public final class SalesManager {
    private SalesManager() {
    }

    public static double calculateLineTotal(double quantity, double unitPrice) {
        return quantity * unitPrice;
    }

    public static double calculateChange(double amountPaid, double totalAmount) {
        return Math.max(0, amountPaid - totalAmount);
    }

    public static boolean isValidSale(double quantity, double unitPrice, double amountPaid) {
        return quantity > 0 && unitPrice >= 0 && amountPaid >= 0;
    }

    public static double calculateDiscount(double totalAmount, double discountPercent) {
        if (discountPercent < 0) return totalAmount;
        return totalAmount - (totalAmount * Math.min(discountPercent, 100) / 100.0);
    }
}
