package com.xtrmetl.etl.connector;

import java.util.List;
import java.util.Map;

/**
 * Reference-only design type for a possible future Qlik Sense / Qlik Cloud integration.
 *
 * <p>This type is not registered in the production target registry and has no Spring-bound
 * production configuration. It is retained only to preserve an explicit design reference while
 * mightyETL has no supported Qlik row-write, reload, or data-file execution path. As a scaffold,
 * {@link #write} always refuses execution.</p>
 *
 * <p>A production Qlik integration requires a separately reviewed reload/data-file contract,
 * maintained client implementation, credential boundary, lifecycle semantics, operator guidance,
 * and realistic integration evidence before this type may become production-discoverable.</p>
 *
 * @see docs/connectors/qlik-sense.md
 */
public final class QlikSenseTargetConnector extends AbstractScaffoldTargetConnector {

    public QlikSenseTargetConnector() {
        super(
                "qlik-sense",
                "Qlik Sense",
                List.of("tenant-url", "api-key", "app-id"),
                List.of("mode"),
                Map.of(
                        "client", "qlik-rest-reload",
                        "plannedPath", "indirect: land in warehouse then trigger app reload",
                        "driverOnClasspath", false,
                        "docs", "docs/connectors/qlik-sense.md"
                )
        );
    }
}
