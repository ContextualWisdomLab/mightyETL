package com.xtrmetl.etl.connector;

/**
 * Lifecycle maturity of a target connector implementation.
 */
public enum ConnectorStatus {
    /** Production-ready path used by the running service. */
    SUPPORTED,
    /** Contract and docs only; write path not implemented. */
    SCAFFOLD,
    /** Explicitly not available. */
    UNSUPPORTED
}
