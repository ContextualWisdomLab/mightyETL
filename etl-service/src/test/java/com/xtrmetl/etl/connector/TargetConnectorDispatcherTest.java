package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetConnectorDispatcherTest {

    @Test
    void refusesWriteWhenDisabled() {
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(new TargetConnectorRegistry(), new ConnectorProperties());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> dispatcher.dispatch("databricks", List.of())
        );
        assertTrue(ex.getMessage().contains("disabled"));
    }

    @Test
    void refusesWriteWhenScaffoldEnabledWithCompleteConfig() {
        ConnectorProperties props = new ConnectorProperties();
        props.getDatabricks().setEnabled(true);
        props.getDatabricks().setHost("h");
        props.getDatabricks().setHttpPath("/sql");
        props.getDatabricks().setToken("t");
        props.getDatabricks().setCatalog("c");
        props.getDatabricks().setSchema("s");
        props.getDatabricks().setTable("tbl");
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(new TargetConnectorRegistry(), props);

        assertThrows(UnsupportedOperationException.class,
                () -> dispatcher.dispatch("databricks", List.of()));
    }

    @Test
    void failsValidationWhenScaffoldEnabledButConfigIncomplete() {
        ConnectorProperties props = new ConnectorProperties();
        props.getDatabricks().setEnabled(true);
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(new TargetConnectorRegistry(), props);

        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch("databricks", List.of()));
    }

    @Test
    void catalogShowsAllConnectorsDisabledByDefault() {
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(new TargetConnectorRegistry(), new ConnectorProperties());

        assertEquals(3, dispatcher.catalog().size());
        assertTrue(dispatcher.catalog().stream().noneMatch(row -> Boolean.TRUE.equals(row.get("writable"))));
    }
}
