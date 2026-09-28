package com.saleh.enezi.manager;

import org.junit.Test;
import static org.junit.Assert.*;

public class StockManagerTest {
    @Test public void newStockIsIncomingMinusOutgoing() { assertEquals(85.0, StockManager.calculateNewStock(100, 20, 35), 0.001); }
    @Test public void remainingStock() { assertEquals(70.0, StockManager.calculateRemaining(100, 30), 0.001); }
    @Test public void stockValidation() {
        assertTrue(StockManager.isStockValid(0));
        assertFalse(StockManager.isStockValid(-1));
    }
}