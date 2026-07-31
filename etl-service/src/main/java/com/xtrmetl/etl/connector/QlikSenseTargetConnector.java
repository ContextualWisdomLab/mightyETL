package com.xtrmetl.etl.connector;

import java.util.List;
import java.util.Map;

/**
 * Qlik Sense / Qlik Cloud target surface (reload-trigger first).
 *
 * <p>Ships config contract + validation + catalog hooks. No Qlik REST client wired;
 * {@link #write} always refuses (SCAFFOLD).</p>
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
