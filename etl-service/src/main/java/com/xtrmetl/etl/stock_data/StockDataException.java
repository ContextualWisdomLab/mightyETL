package com.xtrmetl.etl.stock_data;

/** A finite, credential-free failure at the stock acquisition boundary. */
public final class StockDataException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** Finite classification retained by this serializable failure. */
    private final String errorCode;

    StockDataException(String errorCode) {
        super("Stock data acquisition failed: " + errorCode, null, false, true);
        this.errorCode = errorCode;
    }

    /** Classify the failure without exporting source diagnostics.
     * @return stable machine error code
     */
    public String errorCode() {
        return errorCode;
    }
}
