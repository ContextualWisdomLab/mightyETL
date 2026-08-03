package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises fail-closed connector dispatch and deterministic resource lifecycle behavior.
 */
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
        ConnectorProperties props = enabledDatabricksProperties();
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
        assertTrue(dispatcher.catalog().stream().noneMatch(row -> Boolean.TRUE.equals(row.get("opened"))));
    }

    @Test
    void opensSupportedConnectorOnceBeforeWritingAndClosesExactlyOnce() {
        RecordingConnector connector = new RecordingConnector(ConnectorStatus.SUPPORTED, false);
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        registry.register(connector);
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(registry, enabledDatabricksProperties());

        dispatcher.dispatch("databricks", List.of());
        dispatcher.dispatch("databricks", List.of());

        assertEquals(List.of("open", "write", "write"), connector.events);
        assertTrue(Boolean.TRUE.equals(databricksCatalogRow(dispatcher).get("opened")));
        assertTrue(Boolean.TRUE.equals(databricksCatalogRow(dispatcher).get("writable")));

        dispatcher.closeOpenedConnectors();
        dispatcher.closeOpenedConnectors();

        assertEquals(List.of("open", "write", "write", "close"), connector.events);
        Map<String, Object> closedRow = databricksCatalogRow(dispatcher);
        assertFalse(Boolean.TRUE.equals(closedRow.get("opened")));
        assertFalse(Boolean.TRUE.equals(closedRow.get("writable")));
    }

    @Test
    void retriesSupportedConnectorOpenAfterFailure() {
        RecordingConnector connector = new RecordingConnector(ConnectorStatus.SUPPORTED, true);
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        registry.register(connector);
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(registry, enabledDatabricksProperties());

        assertThrows(IllegalStateException.class,
                () -> dispatcher.dispatch("databricks", List.of()));
        dispatcher.dispatch("databricks", List.of());

        assertEquals(List.of("open", "open", "write"), connector.events);
        dispatcher.closeOpenedConnectors();
        assertEquals(List.of("open", "open", "write", "close"), connector.events);
    }

    @Test
    void refusesUnsupportedConnectorBeforeOpenOrWrite() {
        RecordingConnector connector = new RecordingConnector(ConnectorStatus.UNSUPPORTED, false);
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        registry.register(connector);
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(registry, enabledDatabricksProperties());

        assertThrows(UnsupportedOperationException.class,
                () -> dispatcher.dispatch("databricks", List.of()));

        assertTrue(connector.events.isEmpty());
    }

    @Test
    void serializesWritesToTheSameConnector() throws Exception {
        SerializingProbeConnector connector = new SerializingProbeConnector();
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        registry.register(connector);
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(registry, enabledDatabricksProperties());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch secondDispatchEntered = new CountDownLatch(1);

        try {
            CompletableFuture<Void> firstDispatch = CompletableFuture.runAsync(
                    () -> dispatcher.dispatch("databricks", List.of()),
                    executor
            );
            assertTrue(connector.firstWriteStarted.await(5, TimeUnit.SECONDS));

            CompletableFuture<Void> secondDispatch = CompletableFuture.runAsync(() -> {
                secondDispatchEntered.countDown();
                dispatcher.dispatch("databricks", List.of());
            }, executor);
            assertTrue(secondDispatchEntered.await(5, TimeUnit.SECONDS));

            try {
                assertThrows(TimeoutException.class,
                        () -> connector.secondWriteStartedFuture(executor)
                                .get(250, TimeUnit.MILLISECONDS));
            } finally {
                connector.releaseFirstWrite.countDown();
            }

            firstDispatch.get(5, TimeUnit.SECONDS);
            secondDispatch.get(5, TimeUnit.SECONDS);

            assertEquals(1, connector.maxConcurrentWrites.get());
            assertEquals(2, connector.writeCalls.get());
            dispatcher.closeOpenedConnectors();
        } finally {
            connector.releaseFirstWrite.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shutdownWaitsForInFlightWriteAndRejectsLaterDispatches() throws Exception {
        BlockingConnector connector = new BlockingConnector();
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        registry.register(connector);
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(registry, enabledDatabricksProperties());
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<Void> dispatchFuture = CompletableFuture.runAsync(
                    () -> dispatcher.dispatch("databricks", List.of()),
                    executor
            );
            assertTrue(connector.writeStarted.await(5, TimeUnit.SECONDS));

            CompletableFuture<Void> closeFuture = CompletableFuture.runAsync(
                    dispatcher::closeOpenedConnectors,
                    executor
            );
            try {
                assertThrows(TimeoutException.class,
                        () -> closeFuture.get(250, TimeUnit.MILLISECONDS));
            } finally {
                connector.releaseWrite.countDown();
            }

            dispatchFuture.get(5, TimeUnit.SECONDS);
            closeFuture.get(5, TimeUnit.SECONDS);

            List<String> completedLifecycle = List.of("open", "write-start", "write-end", "close");
            assertEquals(completedLifecycle, connector.events());
            assertThrows(IllegalStateException.class,
                    () -> dispatcher.dispatch("databricks", List.of()));
            assertEquals(completedLifecycle, connector.events());
        } finally {
            connector.releaseWrite.countDown();
            executor.shutdownNow();
        }
    }

    private static Map<String, Object> databricksCatalogRow(TargetConnectorDispatcher dispatcher) {
        return dispatcher.catalog().stream()
                .filter(row -> "databricks".equals(row.get("id")))
                .findFirst()
                .orElseThrow();
    }

    private static ConnectorProperties enabledDatabricksProperties() {
        ConnectorProperties props = new ConnectorProperties();
        props.getDatabricks().setEnabled(true);
        props.getDatabricks().setHost("h");
        props.getDatabricks().setHttpPath("/sql");
        props.getDatabricks().setToken("t");
        props.getDatabricks().setCatalog("c");
        props.getDatabricks().setSchema("s");
        props.getDatabricks().setTable("tbl");
        return props;
    }

    private static final class RecordingConnector implements TargetConnector {
        private final ConnectorStatus status;
        private final boolean failFirstOpen;
        private final List<String> events = new ArrayList<>();
        private int openAttempts;

        private RecordingConnector(ConnectorStatus status, boolean failFirstOpen) {
            this.status = status;
            this.failFirstOpen = failFirstOpen;
        }

        @Override
        public String id() {
            return "databricks";
        }

        @Override
        public String displayName() {
            return "Recording connector";
        }

        @Override
        public ConnectorStatus status() {
            return status;
        }

        @Override
        public void validate(Map<String, String> config) {
            // The fake intentionally has no external configuration contract.
        }

        @Override
        public void open(Map<String, String> config) {
            events.add("open");
            openAttempts++;
            if (failFirstOpen && openAttempts == 1) {
                throw new IllegalStateException("simulated open failure");
            }
        }

        @Override
        public void write(List<ChangeRecord> batch) {
            events.add("write");
        }

        @Override
        public void close() {
            events.add("close");
        }
    }

    private static final class SerializingProbeConnector implements TargetConnector {
        private final AtomicInteger writeCalls = new AtomicInteger();
        private final AtomicInteger concurrentWrites = new AtomicInteger();
        private final AtomicInteger maxConcurrentWrites = new AtomicInteger();
        private final CountDownLatch firstWriteStarted = new CountDownLatch(1);
        private final CountDownLatch secondWriteStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstWrite = new CountDownLatch(1);

        @Override
        public String id() {
            return "databricks";
        }

        @Override
        public String displayName() {
            return "Serializing probe";
        }

        @Override
        public ConnectorStatus status() {
            return ConnectorStatus.SUPPORTED;
        }

        @Override
        public void validate(Map<String, String> config) {
            // The fake intentionally has no external configuration contract.
        }

        @Override
        public void write(List<ChangeRecord> batch) {
            int call = writeCalls.incrementAndGet();
            int active = concurrentWrites.incrementAndGet();
            maxConcurrentWrites.accumulateAndGet(active, Math::max);
            try {
                if (call == 1) {
                    firstWriteStarted.countDown();
                    if (!releaseFirstWrite.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test write release timed out");
                    }
                } else {
                    secondWriteStarted.countDown();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test write interrupted", exception);
            } finally {
                concurrentWrites.decrementAndGet();
            }
        }

        private CompletableFuture<Void> secondWriteStartedFuture(ExecutorService executor) {
            return CompletableFuture.runAsync(() -> {
                try {
                    if (!secondWriteStarted.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("second write did not start");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test wait interrupted", exception);
                }
            }, executor);
        }
    }

    private static final class BlockingConnector implements TargetConnector {
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch writeStarted = new CountDownLatch(1);
        private final CountDownLatch releaseWrite = new CountDownLatch(1);

        @Override
        public String id() {
            return "databricks";
        }

        @Override
        public String displayName() {
            return "Blocking connector";
        }

        @Override
        public ConnectorStatus status() {
            return ConnectorStatus.SUPPORTED;
        }

        @Override
        public void validate(Map<String, String> config) {
            // The fake intentionally has no external configuration contract.
        }

        @Override
        public void open(Map<String, String> config) {
            events.add("open");
        }

        @Override
        public void write(List<ChangeRecord> batch) {
            events.add("write-start");
            writeStarted.countDown();
            try {
                if (!releaseWrite.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test write release timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test write interrupted", exception);
            }
            events.add("write-end");
        }

        @Override
        public void close() {
            events.add("close");
        }

        private List<String> events() {
            synchronized (events) {
                return List.copyOf(events);
            }
        }
    }
}
