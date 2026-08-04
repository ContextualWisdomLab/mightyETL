package com.xtrmetl.etl.job;

import com.xtrmetl.etl.controller.EtlJobController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines the fail-closed activation contract for the incomplete durable-job HTTP surface.
 *
 * <p>The intake resource persists sensitive payloads, while execution and terminal payload
 * clearing remain outside this bounded slice. The controller must therefore stay absent unless an
 * operator explicitly accepts that temporary boundary by enabling the documented boolean property.</p>
 */
class EtlJobIntakeActivationTest {

    /**
     * Requires explicit operator opt-in instead of exposing a permanently pending queue by default.
     */
    @Test
    void requiresExplicitBooleanOptInForTheJobController() {
        ConditionalOnBooleanProperty condition = EtlJobController.class.getAnnotation(
                ConditionalOnBooleanProperty.class
        );

        assertNotNull(condition);
        assertArrayEquals(new String[]{"intake-enabled"}, condition.name());
        assertArrayEquals(new String[0], condition.value());
        assertTrue(condition.havingValue());
        assertFalse(condition.matchIfMissing());
        org.junit.jupiter.api.Assertions.assertEquals("xtrmetl.etl.jobs", condition.prefix());
    }
}
