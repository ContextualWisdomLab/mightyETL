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
        TestSourceConnector connector = new TestSourceConnector();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CdcSourceConnector.class, () -> connector);
            context.register(CdcSourceRegistry.class);
            context.refresh();

            CdcSourceRegistry registry = context.getBean(CdcSourceRegistry.class);

            assertSame(
                    connector,
                    registry.find(connector.id()).orElseThrow(),
                    "Spring must construct the registry through its connector-provider constructor"
            );
        }
    }

    private static final class TestSourceConnector implements CdcSourceConnector {

        @Override
        public String id() {
            return "test_source";
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
        public void validate(Map<String, String> config) {
            // No configuration is required for this constructor-selection regression fixture.
        }

        @Override
        public void start(Map<String, String> config) {
            // No runtime capture is required for this constructor-selection regression fixture.
        }

        @Override
        public void stop() {
            // No runtime capture is started by this constructor-selection regression fixture.
        }
    }
}
