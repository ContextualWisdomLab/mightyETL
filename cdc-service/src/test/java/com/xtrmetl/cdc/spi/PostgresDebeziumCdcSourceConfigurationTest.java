package com.xtrmetl.cdc.spi;

import com.xtrmetl.cdc.service.CdcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guards the PostgreSQL source SPI against silently ignored per-call configuration.
 */
class PostgresDebeziumCdcSourceConfigurationTest {

    @Test
    void rejectsCallerConfigurationBeforeStartingDeploymentConfiguredService() {
        CdcService service = mock(CdcService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<CdcService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        PostgresDebeziumCdcSource source = new PostgresDebeziumCdcSource(provider);
        String rejectedSecret = "buyer-secret-password-8472";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> source.start(Map.of("database.password", rejectedSecret))
        );

        assertEquals(
                "postgres-debezium uses deployment-owned configuration; per-call config must be empty",
                failure.getMessage()
        );
        assertFalse(failure.getMessage().contains(rejectedSecret));
        assertFalse(failure.getMessage().contains("database.password"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsNullConfigurationBeforeServiceLookup() {
        CdcService service = mock(CdcService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<CdcService> provider = mock(ObjectProvider.class);
        PostgresDebeziumCdcSource source = new PostgresDebeziumCdcSource(provider);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> source.start(null)
        );

        assertEquals("config must not be null", failure.getMessage());
        verifyNoInteractions(provider, service);
    }

    @Test
    void emptyConfigurationStartsDeploymentConfiguredService() {
        CdcService service = mock(CdcService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<CdcService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        PostgresDebeziumCdcSource source = new PostgresDebeziumCdcSource(provider);

        source.start(Map.of());

        verify(provider).getIfAvailable();
        verify(service).start();
    }
}
