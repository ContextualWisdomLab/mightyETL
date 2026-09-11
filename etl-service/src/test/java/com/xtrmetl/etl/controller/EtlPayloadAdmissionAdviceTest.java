package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.StringHttpMessageConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the transport-level ETL payload guard without relying on MVC implementation details.
 */
class EtlPayloadAdmissionAdviceTest {

    private static final int MAXIMUM_BYTES = 8;

    @Test
    void supportsOnlyStringRequestParameters() throws Exception {
        EtlPayloadAdmissionAdvice advice = advice();

        assertTrue(advice.supports(
                requestBodyParameter(),
                String.class,
                StringHttpMessageConverter.class
        ));
        assertFalse(advice.supports(
                integerParameter(),
                Integer.class,
                StringHttpMessageConverter.class
        ));
    }

    @Test
    void requiresBatchProperties() {
        assertThrows(NullPointerException.class, () -> new EtlPayloadAdmissionAdvice(null));
    }

    @Test
    void rejectsKnownOversizedBodyWithoutReadingAnyEntityByte() throws Exception {
        EtlPayloadAdmissionAdvice advice = advice();
        CountingInputStream body = new CountingInputStream(new byte[MAXIMUM_BYTES + 1]);
        TestHttpInputMessage inputMessage = new TestHttpInputMessage(body, MAXIMUM_BYTES + 1L);

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> advice.beforeBodyRead(
                        inputMessage,
                        requestBodyParameter(),
                        String.class,
                        StringHttpMessageConverter.class
                )
        );

        assertSame(EtlRequestError.PAYLOAD_TOO_LARGE, exception.error());
        assertEquals(0, body.bytesRead());
    }

    @Test
    void preservesHeadersAndRejectsUnknownLengthBodyAfterOnlyLimitPlusOneByte() throws Exception {
        EtlPayloadAdmissionAdvice advice = advice();
        CountingInputStream body = new CountingInputStream(new byte[MAXIMUM_BYTES + 32]);
        TestHttpInputMessage inputMessage = new TestHttpInputMessage(body, -1L);
        inputMessage.getHeaders().set("X-Test-Header", "preserved");
        HttpInputMessage boundedMessage = bounded(advice, inputMessage);

        assertSame(inputMessage.getHeaders(), boundedMessage.getHeaders());
        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> boundedMessage.getBody().readAllBytes()
        );

        assertSame(EtlRequestError.PAYLOAD_TOO_LARGE, exception.error());
        assertEquals(MAXIMUM_BYTES + 1, body.bytesRead());
    }

    @Test
    void readsExactLimitToEndWithoutFalsePositive() throws Exception {
        EtlPayloadAdmissionAdvice advice = advice();
        byte[] payload = "12345678".getBytes(StandardCharsets.UTF_8);
        CountingInputStream body = new CountingInputStream(payload);
        HttpInputMessage boundedMessage = bounded(advice, new TestHttpInputMessage(body, -1L));

        assertArrayEquals(payload, boundedMessage.getBody().readAllBytes());
        assertEquals(MAXIMUM_BYTES, body.bytesRead());
    }

    @Test
    void supportsSingleByteReadsAtAndBeyondTheLimit() throws Exception {
        EtlPayloadAdmissionAdvice advice = advice();
        HttpInputMessage exact = bounded(
                advice,
                new TestHttpInputMessage(new ByteArrayInputStream("12345678".getBytes(StandardCharsets.UTF_8)), -1L)
        );
        InputStream exactBody = exact.getBody();
        for (int index = 0; index < MAXIMUM_BYTES; index++) {
            assertEquals('1' + index, exactBody.read());
        }
        assertEquals(-1, exactBody.read());

        HttpInputMessage oversized = bounded(
                advice,
                new TestHttpInputMessage(new ByteArrayInputStream("123456789".getBytes(StandardCharsets.UTF_8)), -1L)
        );
        InputStream oversizedBody = oversized.getBody();
        for (int index = 0; index < MAXIMUM_BYTES; index++) {
            oversizedBody.read();
        }
        assertThrows(EtlRequestException.class, oversizedBody::read);
    }

    @Test
    void handlesZeroLengthBulkReadAndEndOfStreamAtTheLimit() throws Exception {
        EtlPayloadAdmissionAdvice advice = advice();
        InputStream body = bounded(
                advice,
                new TestHttpInputMessage(new ByteArrayInputStream("12345678".getBytes(StandardCharsets.UTF_8)), -1L)
        ).getBody();
        byte[] buffer = new byte[MAXIMUM_BYTES];

        assertEquals(0, body.read(buffer, 0, 0));
        assertEquals(MAXIMUM_BYTES, body.read(buffer, 0, buffer.length));
        assertEquals(-1, body.read(buffer, 0, 1));
    }

    @Test
    void propagatesBodyAcquisitionIOExceptionUnchanged() throws Exception {
        EtlPayloadAdmissionAdvice advice = advice();
        IOException expected = new IOException("test stream acquisition failure");
        HttpInputMessage failingMessage = new HttpInputMessage() {
            @Override
            public InputStream getBody() throws IOException {
                throw expected;
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };

        IOException actual = assertThrows(
                IOException.class,
                () -> bounded(advice, failingMessage)
        );
        assertSame(expected, actual);
    }

    @Test
    void closesTheUnderlyingRequestBody() throws Exception {
        EtlPayloadAdmissionAdvice advice = advice();
        CloseTrackingInputStream delegate = new CloseTrackingInputStream(new byte[0]);
        InputStream body = bounded(advice, new TestHttpInputMessage(delegate, -1L)).getBody();

        body.close();

        assertTrue(delegate.closed());
    }

    private static HttpInputMessage bounded(
            EtlPayloadAdmissionAdvice advice,
            HttpInputMessage inputMessage
    ) throws Exception {
        return advice.beforeBodyRead(
                inputMessage,
                requestBodyParameter(),
                String.class,
                StringHttpMessageConverter.class
        );
    }

    private static EtlPayloadAdmissionAdvice advice() {
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxPayloadBytes(MAXIMUM_BYTES);
        return new EtlPayloadAdmissionAdvice(properties);
    }

    private static MethodParameter requestBodyParameter() throws NoSuchMethodException {
        Method method = EtlController.class.getMethod(
                "processData",
                String.class,
                String.class,
                Principal.class
        );
        return new MethodParameter(method, 0);
    }

    private static MethodParameter integerParameter() throws NoSuchMethodException {
        Method method = EtlPayloadAdmissionAdviceTest.class.getDeclaredMethod("integerBody", Integer.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static void integerBody(Integer value) {
        // Reflection target used only to prove the RequestBodyAdvice type filter.
    }

    private static final class TestHttpInputMessage implements HttpInputMessage {

        private final HttpHeaders headers = new HttpHeaders();
        private final InputStream body;

        private TestHttpInputMessage(InputStream body, long contentLength) {
            this.body = body;
            if (contentLength >= 0L) {
                headers.setContentLength(contentLength);
            }
        }

        @Override
        public InputStream getBody() {
            return body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    private static class CountingInputStream extends ByteArrayInputStream {

        private int bytesRead;

        private CountingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read() {
            int value = super.read();
            if (value != -1) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                bytesRead += read;
            }
            return read;
        }

        private int bytesRead() {
            return bytesRead;
        }
    }

    private static final class CloseTrackingInputStream extends CountingInputStream {

        private boolean closed;

        private CloseTrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean closed() {
            return closed;
        }
    }
}
