package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies that the Spring-managed source registry receives discovered connector beans.
 *
 * <p>This test reaches the actual Spring constructor-selection boundary instead of directly
 * instantiating {@link CdcSourceRegistry}. It prevents a public no-argument constructor from
 * silently bypassing the {@code ObjectProvider<CdcSourceConnector>} integration path.</p>
 */
class CdcSourceRegistrySpringWiringTest {

    @Test
    void springContextRegistersDiscoveredSourceConnectorBean() {
        TestSourceConnector sourceConnector = new TestSourceConnector();

        try (AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()) {
            applicationContext.registerBean(CdcSourceConnector.class, () -> sourceConnector);
            applicationContext.register(CdcSourceRegistry.class);
            applicationContext.refresh();

            CdcSourceRegistry sourceRegistry = applicationContext.getBean(CdcSourceRegistry.class);

            assertSame(
                    sourceConnector,
                    sourceRegistry.find(sourceConnector.sourceId()).orElseThrow(),
                    "Spring must construct the registry through its connector-provider constructor"
            );
        }
    }

    private static final class TestSourceConnector implements CdcSourceConnector {

        @Override
        public String sourceId() {
            return "test_source";
        }

        /** @deprecated compatibility fixture for the historical SPI accessor. */
        @Override
        @Deprecated(forRemoval = false)
        public String id() {
            return sourceId();
        }

        @Override
        public String displayName() {
            return "Test source";
        }

        @Override
        public SourceCapabilities capabilities() {
            return new SourceCapabilities("test", Set.of("test_database"), false);
        }

        @Override
        public void validate(Map<String, String> sourceConfig) {
            // No configuration is required for this constructor-selection regression fixture.
        }

        @Override
        public void start(Map<String, String> sourceConfig) {
            // No runtime capture is required for this constructor-selection regression fixture.
        }

        @Override
        public void stop() {
            // No runtime capture is started by this constructor-selection regression fixture.
        }
    }
}
