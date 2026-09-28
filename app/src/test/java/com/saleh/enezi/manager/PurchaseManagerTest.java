package com.saleh.enezi.manager;

import org.junit.Test;
import static org.junit.Assert.*;

public class PurchaseManagerTest {
    @Test public void lineTotal() { assertEquals(3000.0, PurchaseManager.calculateLineTotal(12, 250), 0.001); }
    @Test public void netPurchaseIncludesExtraCosts() { assertEquals(3150.0, PurchaseManager.calculateNetPurchase(3000, 100, 50), 0.001); }
    @Test public void purchaseValidation() {
        assertTrue(PurchaseManager.isValidPurchase(1, 10));
        assertFalse(PurchaseManager.isValidPurchase(0, 10));
        assertFalse(PurchaseManager.isValidPurchase(1, -1));
    }
}