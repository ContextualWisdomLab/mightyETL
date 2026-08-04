package com.xtrmetl.etl.service;

import java.util.Objects;

/**
 * Signals a deterministic ETL request rejection without exposing diagnostic text to clients.
 *
 * <p>The exception message is the stable machine code. Optional causes are retained for
 * server-side diagnostics only and must never be copied into HTTP responses.</p>
 */
public final class EtlRequestException extends RuntimeException {

    private final EtlRequestError error;

    /**
     * Creates a cause-free request rejection.
     *
     * @param error stable request-failure classification
     */
    public EtlRequestException(EtlRequestError error) {
        this(error, null);
    }

    /**
     * Creates a request rejection with an optional diagnostic cause.
     *
     * @param error stable request-failure classification
     * @param cause optional server-side diagnostic cause
     */
    public EtlRequestException(EtlRequestError error, Throwable cause) {
        super(requireError(error).errorCode(), cause);
        this.error = error;
    }

    /**
     * Returns the immutable request-failure classification.
     *
     * @return typed ETL request error
     */
    public EtlRequestError error() {
        return error;
    }

    private static EtlRequestError requireError(EtlRequestError error) {
        return Objects.requireNonNull(error, "error must not be null");
    }
}
