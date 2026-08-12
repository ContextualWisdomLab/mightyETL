package com.xtrmetl.cdc.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that Java 25 can initialize Mockito's inline mock maker without
 * dynamically attaching an agent after the test JVM has started.
 */
final class MockitoStartupAgentContractTest {

    @Test
    void mocksFinalClassWhenDynamicAgentLoadingIsDisabled() {
        Assumptions.assumeTrue(
                Runtime.version().feature() >= 25,
                "The fail-closed dynamic-agent compatibility probe starts with Java 25");

        FinalCollaborator collaborator = mock(FinalCollaborator.class);
        when(collaborator.value()).thenReturn("startup-agent");

        assertEquals("startup-agent", collaborator.value());
    }

    private static final class FinalCollaborator {
        String value() {
            return "production-value";
        }
    }
}
