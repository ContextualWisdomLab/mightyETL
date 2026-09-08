package com.xtrmetl.etl.stock_data;

import org.junit.jupiter.api.Test;

/** Runs the identical source-acquisition contracts in the existing Maven reactor. */
class FscStockDataSourceTest {
    /** No new workflow or alternative success path replaces existing repository CI. */
    @Test
    void validatesStockAcquisitionContracts() throws Exception {
        StockDataContractChecks.verifyAll();
    }
}
