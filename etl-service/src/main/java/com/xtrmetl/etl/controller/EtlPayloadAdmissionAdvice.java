package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Enforces the synchronous ETL payload byte limit before Spring MVC materializes a request body.
 *
 * <p>Known oversized bodies are rejected from their {@code Content-Length} metadata without reading
 * the entity. Unknown-length or understated bodies are wrapped in a byte-counting stream that reads
 * at most one byte beyond the configured limit before raising the existing typed payload error. The
 * service-level admission check remains in place as defense in depth.</p>
 */
@ControllerAdvice(assignableTypes = EtlController.class)
public final class EtlPayloadAdmissionAdvice extends RequestBodyAdviceAdapter {

    private final EtlBatchProperties batchProperties;

    /**
     * Creates the MVC transport admission guard.
     *
     * @param batchProperties bounded ETL request limits shared with the service layer
     */
    public EtlPayloadAdmissionAdvice(EtlBatchProperties batchProperties) {
        this.batchProperties = Objects.requireNonNull(
                batchProperties,
                "batchProperties must not be null"
        );
    }

    /**
     * Applies admission control to string request bodies handled by {@link EtlController}.
     *
     * @param methodParameter controller method parameter receiving the request body
     * @param targetType declared request-body target type
     * @param converterType selected HTTP message converter type
     * @return {@code true} only for the synchronous ETL string body
     */
    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return String.class.equals(methodParameter.getParameterType());
    }

    /**
     * Rejects known oversized entities and bounds streaming reads before conversion to a String.
     *
     * @param inputMessage request headers and body selected by Spring MVC
     * @param parameter controller parameter receiving the body
     * @param targetType declared request-body target type
     * @param converterType selected HTTP message converter type
     * @return the original headers with a byte-bounded request stream
     * @throws IOException when the underlying request stream cannot be obtained
     */
    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) throws IOException {
        int maximumBytes = batchProperties.getMaxPayloadBytes();
        long contentLength = inputMessage.getHeaders().getContentLength();
        if (contentLength > maximumBytes) {
            throw payloadTooLarge();
        }
        return new BoundedHttpInputMessage(inputMessage, maximumBytes);
    }

    private static EtlRequestException payloadTooLarge() {
        return new EtlRequestException(EtlRequestError.PAYLOAD_TOO_LARGE);
    }

    private static final class BoundedHttpInputMessage implements HttpInputMessage {

        private final HttpInputMessage delegate;
        private final InputStream body;

        private BoundedHttpInputMessage(HttpInputMessage delegate, int maximumBytes) throws IOException {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
            this.body = new BoundedInputStream(delegate.getBody(), maximumBytes);
        }

        @Override
        public InputStream getBody() {
            return body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }

    private static final class BoundedInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        private BoundedInputStream(InputStream delegate, long maximumBytes) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
            this.remaining = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0L) {
                int extraByte = delegate.read();
                if (extraByte == -1) {
                    return -1;
                }
                throw payloadTooLarge();
            }

            int value = delegate.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            if (remaining == 0L) {
                return rejectExtraByte();
            }

            int boundedLength = (int) Math.min((long) length, remaining + 1L);
            int read = delegate.read(bytes, offset, boundedLength);
            if (read == -1) {
                return -1;
            }
            if (read > remaining) {
                throw payloadTooLarge();
            }
            remaining -= read;
            return read;
        }

        private int rejectExtraByte() throws IOException {
            if (delegate.read() == -1) {
                return -1;
            }
            throw payloadTooLarge();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
