package com.saleh.enezi.manager;

public final class StockManager {
    private StockManager() {
    }

    public static double calculateRemaining(double stockQty, double soldQty) {
        return stockQty - soldQty;
    }

    public static double calculateNewStock(double currentQty, double incomingQty, double outgoingQty) {
        return currentQty + incomingQty - outgoingQty;
    }

    public static boolean isStockValid(double quantity) {
        return quantity >= 0;
    }
}
