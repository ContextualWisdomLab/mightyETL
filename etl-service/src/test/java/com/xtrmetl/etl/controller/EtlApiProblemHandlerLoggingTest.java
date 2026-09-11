package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every ordinary ETL failure log retains bounded classifications without
 * republishing owner-scoped resource identifiers from the request path.
 */
@ExtendWith(OutputCaptureExtension.class)
class EtlApiProblemHandlerLoggingTest {

    private static final String JOB_RECORD_ID = "0198f4cf-41c8-7f52-9e5d-private-job-marker";

    private static Logger handlerLogger;

    private static Level originalLevel;

    @BeforeAll
    static void raiseHandlerLogLevel() {
        handlerLogger = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(EtlApiProblemHandler.class);
        originalLevel = handlerLogger.getLevel();
        handlerLogger.setLevel(Level.DEBUG);
    }

    @AfterAll
    static void restoreHandlerLogLevel() {
        handlerLogger.setLevel(originalLevel);
    }

    private static MockHttpServletRequest jobScopedRequest() {
        return new MockHttpServletRequest("GET", "/api/etl/jobs/" + JOB_RECORD_ID);
    }

    private static void assertBoundedLog(String logs, String classification) {
        assertTrue(logs.contains(classification), "missing classification: " + classification);
        assertFalse(logs.contains(JOB_RECORD_ID), "log republished owner-scoped identifier");
    }

    @Test
    void requestFailureLogDoesNotRepublishOwnerScopedResourceIdentifier(CapturedOutput output) {
        EtlApiProblemHandler handler = new EtlApiProblemHandler();

        handler.handleRequestFailure(
                new EtlRequestException(EtlRequestError.INVALID_RECORD),
                jobScopedRequest()
        );

        assertBoundedLog(output.getOut() + output.getErr(), "Rejected ETL request");
    }

    @Test
    void unreadableBodyLogDoesNotRepublishOwnerScopedResourceIdentifier(CapturedOutput output) {
        EtlApiProblemHandler handler = new EtlApiProblemHandler();

        handler.handleUnreadableBody(
                new HttpMessageNotReadableException(
                        "synthetic parse diagnostic",
                        new MockHttpInputMessage(new byte[0])
                ),
                jobScopedRequest()
        );

        assertBoundedLog(output.getOut() + output.getErr(), "Rejected unreadable ETL request");
    }

    @Test
    void transientTargetFailureLogDoesNotRepublishOwnerScopedResourceIdentifier(CapturedOutput output) {
        EtlApiProblemHandler handler = new EtlApiProblemHandler();

        handler.handleTransientTargetFailure(
                new TransientDataAccessResourceException("synthetic transient diagnostic"),
                jobScopedRequest()
        );

        assertBoundedLog(output.getOut() + output.getErr(), "Transient ETL target failure");
    }

    @Test
    void targetFailureLogDoesNotRepublishOwnerScopedResourceIdentifier(CapturedOutput output) {
        EtlApiProblemHandler handler = new EtlApiProblemHandler();

        handler.handleTargetFailure(
                new DataIntegrityViolationException("synthetic database diagnostic"),
                jobScopedRequest()
        );

        assertBoundedLog(output.getOut() + output.getErr(), "ETL target failure");
    }

    @Test
    void unexpectedFailureLogDoesNotRepublishOwnerScopedResourceIdentifier(CapturedOutput output) {
        EtlApiProblemHandler handler = new EtlApiProblemHandler();

        handler.handleUnexpectedFailure(
                new EtlUnexpectedException(new IllegalStateException("synthetic internal diagnostic")),
                jobScopedRequest()
        );

        assertBoundedLog(output.getOut() + output.getErr(), "Unexpected ETL failure");
    }
}
