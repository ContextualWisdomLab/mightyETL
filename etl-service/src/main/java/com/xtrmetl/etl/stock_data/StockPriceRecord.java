package com.xtrmetl.etl.stock_data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Map;

/**
 * Source-preserving stock observation, not an adjusted-price or trading signal.
 *
 * @param referenceDate provider business/reference date, not observation time
 * @param shortCode exact short code, retaining leading zeroes
 * @param isinCode exact provider ISIN text
 * @param instrumentName provider instrument label
 * @param marketCategory provider market label, not a guessed exchange MIC
 * @param openPrice exact provider open value
 * @param highPrice exact provider high value
 * @param lowPrice exact provider low value
 * @param closePrice exact provider close value
 * @param tradingVolume exact share count
 * @param tradingValue exact KRW trading value
 * @param sourceFields all bounded provider row fields, including unmapped fields
 * @param sourcePageNumber page containing the original observation
 */
public record StockPriceRecord(LocalDate referenceDate, String shortCode, String isinCode,
        String instrumentName, String marketCategory, BigDecimal openPrice,
        BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice,
        BigInteger tradingVolume, BigInteger tradingValue, Map<String, String> sourceFields,
        int sourcePageNumber) {
    /** Retain an immutable copy of the provider fields. */
    public StockPriceRecord {
        sourceFields = Map.copyOf(sourceFields);
    }

    /** @return a credential-free identity summary, not the provider field payload */
    @Override
    public String toString() {
        return "StockPriceRecord[referenceDate=" + referenceDate + ", sourcePageNumber=" + sourcePageNumber + "]";
    }
}
