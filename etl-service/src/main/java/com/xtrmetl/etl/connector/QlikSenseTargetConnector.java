package com.xtrmetl.etl.connector;

/**
 * Scaffold for Qlik Sense reload trigger / future push integration.
 *
 * @see docs/connectors/qlik-sense.md
 */
public final class QlikSenseTargetConnector extends AbstractScaffoldTargetConnector {

    public QlikSenseTargetConnector() {
        super("qlik-sense", "Qlik Sense");
    }
}
