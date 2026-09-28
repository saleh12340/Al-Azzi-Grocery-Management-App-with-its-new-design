package com.saleh.enezi.manager;

public final class SupplierManager {
    private SupplierManager() {
    }

    public static double calculateBalance(double purchases, double payments) {
        return purchases - payments;
    }

    public static boolean isValidSupplier(String name) {
        return name != null && !name.trim().isEmpty();
    }

    public static double calculatePaymentDue(double totalInvoice, double alreadyPaid) {
        return Math.max(0, totalInvoice - alreadyPaid);
    }
}
