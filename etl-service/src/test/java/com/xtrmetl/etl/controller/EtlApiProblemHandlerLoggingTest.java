package com.xtrmetl.etl.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that ordinary ETL failure logs retain bounded classifications without resource IDs.
 */
@ExtendWith(OutputCaptureExtension.class)
class EtlApiProblemHandlerLoggingTest {

    private static final String JOB_RECORD_ID = "0198f4cf-41c8-7f52-9e5d-private-job-marker";

    @Test
    void targetFailureLogDoesNotRepublishOwnerScopedResourceIdentifier(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/etl/jobs/" + JOB_RECORD_ID
        );
        EtlApiProblemHandler handler = new EtlApiProblemHandler();

        handler.handleTargetFailure(
                new DataIntegrityViolationException("synthetic database diagnostic"),
                request
        );

        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains("ETL target failure"));
        assertFalse(logs.contains(JOB_RECORD_ID));
    }
}
