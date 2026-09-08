package com.xtrmetl.etl.stock_data;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

/**
 * Host-supplied acquisition port. Implementations must use an approved, released
 * EgressWeave binding for exact destination/TLS policy, credential resolution,
 * transport budgets and provider-wide throttling. This interface itself proves
 * none of those controls and deliberately has no unrestricted default client.
 */
@FunctionalInterface
public interface StockDataTransport {
    /**
     * Fetch exactly one page without redirects or retrying provider rejections.
     *
     * @param pageRequest immutable public query and opaque credential reference
     * @return response body whose ownership transfers to the collector
     * @throws IOException when transport cannot complete the request
     * @throws InterruptedException when the caller cancels acquisition
     */
    PageResponse fetchPage(PageRequest pageRequest) throws IOException, InterruptedException;

    /**
     * A credential-free request description; the transport materializes serviceKey.
     *
     * @param sourceEndpoint the fixed FSC HTTPS endpoint, never a user-supplied URL
     * @param publicParameters query values that exclude credentials
     * @param credentialReference deployment-owned secret reference, not key material
     * @param pageNumber expected response page number
     */
    record PageRequest(URI sourceEndpoint, Map<String, String> publicParameters,
                       String credentialReference, int pageNumber) {
        /** Create an immutable request and retain the secret-free parameter map. */
        public PageRequest {
            publicParameters = Map.copyOf(publicParameters);
        }

        /** Describe the page without credential references or caller parameters.
         * @return finite page identity only
         */
        @Override
        public String toString() {
            return "PageRequest[pageNumber=" + pageNumber + "]";
        }
    }

    /**
     * Transfer-decoded, identity-content-coded response from the governed transport.
     *
     * @param statusCode HTTP response status
     * @param contentType original media type, with optional UTF-8 charset
     * @param bodyStream unconsumed response body; close on every outcome
     */
    record PageResponse(int statusCode, String contentType, InputStream bodyStream) implements AutoCloseable {
        /** Validate the owned stream without exposing it to diagnostic formatting. */
        public PageResponse {
            if (bodyStream == null) {
                throw new StockDataException("transport_failure");
            }
        }

        /**
         * Release the underlying connection/body on success and on failure.
         * @throws IOException if the supplied stream fails to close
         */
        @Override
        public void close() throws IOException {
            bodyStream.close();
        }

        /** Describe the response without formatting its stream.
         * @return bounded HTTP status metadata
         */
        @Override
        public String toString() {
            return "PageResponse[statusCode=" + statusCode + "]";
        }
    }
}
