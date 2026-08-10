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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that HTTP payload admission rejects oversized input without draining the request stream.
 */
class EtlPayloadAdmissionAdviceTest {

    private static final int MAXIMUM_BYTES = 8;

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
    void rejectsUnknownLengthBodyAfterReadingOnlyLimitPlusOneByte() throws Exception {
        EtlPayloadAdmissionAdvice advice = advice();
        CountingInputStream body = new CountingInputStream(new byte[MAXIMUM_BYTES + 32]);
        TestHttpInputMessage inputMessage = new TestHttpInputMessage(body, -1L);
        HttpInputMessage boundedMessage = advice.beforeBodyRead(
                inputMessage,
                requestBodyParameter(),
                String.class,
                StringHttpMessageConverter.class
        );

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
        TestHttpInputMessage inputMessage = new TestHttpInputMessage(body, -1L);
        HttpInputMessage boundedMessage = advice.beforeBodyRead(
                inputMessage,
                requestBodyParameter(),
                String.class,
                StringHttpMessageConverter.class
        );

        assertArrayEquals(payload, boundedMessage.getBody().readAllBytes());
        assertEquals(MAXIMUM_BYTES, body.bytesRead());
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

    private static final class CountingInputStream extends ByteArrayInputStream {

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
}
