package com.saleh.enezi.manager;

public final class PurchaseManager {
    private PurchaseManager() {
    }

    public static double calculateLineTotal(double quantity, double unitCost) {
        return quantity * unitCost;
    }

    public static double calculateNetPurchase(double subtotal, double shipping, double otherCosts) {
        return subtotal + shipping + otherCosts;
    }

    public static boolean isValidPurchase(double quantity, double unitCost) {
        return quantity > 0 && unitCost >= 0;
    }
}
