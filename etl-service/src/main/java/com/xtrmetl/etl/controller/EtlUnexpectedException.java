package com.xtrmetl.etl.controller;

import java.util.Objects;

/**
 * Marks an unexpected runtime failure that escaped the synchronous ETL service invocation.
 *
 * <p>The dedicated wrapper prevents the controller advice from treating unrelated Spring MVC,
 * routing, connector-catalog, or response-negotiation exceptions as ETL internal failures. The
 * original cause is retained for server-side diagnostics and is never copied into the public
 * RFC 9457 response.</p>
 */
final class EtlUnexpectedException extends RuntimeException {

    /**
     * Creates the internal boundary exception with a stable non-sensitive message.
     *
     * @param cause unexpected runtime failure raised by the ETL service
     */
    EtlUnexpectedException(RuntimeException cause) {
        super("etl_internal_error", Objects.requireNonNull(cause, "cause must not be null"));
    }
}
