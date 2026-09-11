package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Converts covered ETL endpoint failures into stable, non-sensitive RFC 9457 responses.
 *
 * <p>The advice is scoped to the synchronous and durable-job ETL controllers; unrelated
 * controllers retain their existing exception contracts. Every client-visible field is fixed by a
 * typed definition. Raw exception messages, SQL details, credentials, paths, causes, and stack
 * traces are never copied into the HTTP body. Ordinary failure logs keep bounded classifications
 * without request resource paths or owner-scoped identifiers. Covered responses use
 * {@code Cache-Control: no-store} so authenticated operational failures are not retained by shared
 * or private caches. Spring MVC routing and response-negotiation failures are deliberately not
 * captured by a broad exception handler and therefore retain their framework-owned status
 * semantics.</p>
 */
@RestControllerAdvice(assignableTypes = {EtlController.class, EtlJobController.class})
public class EtlApiProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(EtlApiProblemHandler.class);

    private static final URI TARGET_UNAVAILABLE_TYPE = URI.create(
            "urn:mightyetl:problem:etl-target-unavailable"
    );
    private static final URI TARGET_FAILURE_TYPE = URI.create(
            "urn:mightyetl:problem:etl-target-failure"
    );
    private static final URI INTERNAL_ERROR_TYPE = URI.create(
            "urn:mightyetl:problem:etl-internal-error"
    );

    /**
     * Renders a deterministic request rejection using its enum-owned problem metadata.
     *
     * @param exception typed request rejection
     * @param request current servlet request
     * @return RFC 9457 response with a stable machine code
     */
    @ExceptionHandler(EtlRequestException.class)
    public ResponseEntity<ProblemDetail> handleRequestFailure(
            EtlRequestException exception,
            HttpServletRequest request
    ) {
        EtlRequestError error = exception.error();
        log.debug("Rejected ETL request code={}", error.errorCode());
        return problem(
                error.status(),
                error.type(),
                error.title(),
                error.detail(),
                error.errorCode(),
                request
        );
    }

    /**
     * Renders missing or unreadable request bodies as invalid JSON.
     *
     * @param exception Spring MVC body-conversion failure
     * @param request current servlet request
     * @return invalid-JSON problem response
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        log.debug(
                "Rejected unreadable ETL request exceptionType={}",
                exception.getClass().getName()
        );
        EtlRequestError error = EtlRequestError.INVALID_JSON;
        return problem(
                error.status(),
                error.type(),
                error.title(),
                error.detail(),
                error.errorCode(),
                request
        );
    }

    /**
     * Renders an exhausted transient target failure as a retryable service outage.
     *
     * @param exception transient data-access failure
     * @param request current servlet request
     * @return service-unavailable problem response
     */
    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<ProblemDetail> handleTransientTargetFailure(
            TransientDataAccessException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Transient ETL target failure exceptionType={}",
                exception.getClass().getName()
        );
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                TARGET_UNAVAILABLE_TYPE,
                "ETL target unavailable",
                "The ETL target is temporarily unavailable.",
                "etl_target_unavailable",
                request
        );
    }

    /**
     * Renders a non-transient target failure without exposing database diagnostics.
     *
     * @param exception target data-access failure
     * @param request current servlet request
     * @return target-failure problem response
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleTargetFailure(
            DataAccessException exception,
            HttpServletRequest request
    ) {
        log.error(
                "ETL target failure exceptionType={}",
                exception.getClass().getName()
        );
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                TARGET_FAILURE_TYPE,
                "ETL target failure",
                "The ETL target could not process the request.",
                "etl_target_failure",
                request
        );
    }

    /**
     * Renders an unexpected failure raised inside an ETL service invocation boundary.
     *
     * @param exception dedicated wrapper around the original unexpected runtime failure
     * @param request current servlet request
     * @return generic internal-error problem response
     */
    @ExceptionHandler(EtlUnexpectedException.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedFailure(
            EtlUnexpectedException exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unexpected ETL failure exceptionType={}",
                exception.getCause().getClass().getName()
        );
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                INTERNAL_ERROR_TYPE,
                "ETL internal error",
                "The ETL request could not be processed.",
                "etl_internal_error",
                request
        );
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            String errorCode,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
