package com.saleh.enezi.manager;

import org.junit.Test;
import static org.junit.Assert.*;

public class SalesManagerTest {
    @Test public void lineTotal() { assertEquals(1250.0, SalesManager.calculateLineTotal(5, 250), 0.001); }
    @Test public void changeNeverNegative() { assertEquals(0.0, SalesManager.calculateChange(900, 1000), 0.001); }
    @Test public void discountIsCappedAt100() { assertEquals(0.0, SalesManager.calculateDiscount(1000, 120), 0.001); }
    @Test public void saleValidationRejectsInvalidValues() {
        assertTrue(SalesManager.isValidSale(1, 10, 0));
        assertFalse(SalesManager.isValidSale(0, 10, 0));
        assertFalse(SalesManager.isValidSale(1, -1, 0));
    }
}