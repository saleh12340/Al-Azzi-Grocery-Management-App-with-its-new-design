package com.saleh.enezi.manager;

import org.junit.Test;
import static org.junit.Assert.*;

public class SupplierManagerTest {
    @Test public void balanceIsPurchasesMinusPayments() { assertEquals(7000.0, SupplierManager.calculateBalance(10000, 3000), 0.001); }
    @Test public void dueNeverNegative() { assertEquals(0.0, SupplierManager.calculatePaymentDue(1000, 1200), 0.001); }
    @Test public void supplierNameValidation() {
        assertTrue(SupplierManager.isValidSupplier("مورد"));
        assertFalse(SupplierManager.isValidSupplier("  "));
        assertFalse(SupplierManager.isValidSupplier(null));
    }
}