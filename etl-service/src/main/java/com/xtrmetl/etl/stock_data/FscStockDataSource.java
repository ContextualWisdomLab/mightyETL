package com.xtrmetl.etl.stock_data;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Complete-result FSC stock collector with a host-owned governed transport.
 * No network access, credentials, database writes or background tasks are implicit.
 */
public final class FscStockDataSource {
    /** Secret-free official operation identity; never append a credential here. */
    public static final URI SOURCE_ENDPOINT = URI.create("https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo");
    private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_BATCH_BYTES = 16 * 1024 * 1024;
    private final StockDataTransport sourceTransport;
    private final String credentialReference;
    private final Clock observationClock;

    /**
     * Construct an explicit source adapter without fetching or resolving secrets.
     *
     * @param sourceTransport deployment-approved transport; no default is provided
     * @param credentialReference opaque local secret reference, not the actual service key
     * @param observationClock injected clock for collection evidence
     */
    public FscStockDataSource(StockDataTransport sourceTransport, String credentialReference, Clock observationClock) {
        if (sourceTransport == null || observationClock == null || credentialReference == null
                || !credentialReference.matches("[a-z][a-z0-9_]{2,63}")) {
            throw new StockDataException("invalid_query");
        }
        this.sourceTransport = sourceTransport;
        this.credentialReference = credentialReference;
        this.observationClock = observationClock;
    }

    /**
     * Fetch and validate every page before exposing any result. The operation does
     * not retry, publish, write a database, adjust prices or infer trading calendars.
     *
     * @param sourceQuery explicit bounded date range, optional ISIN and budgets
     * @return complete records and immutable original-page evidence, or an explicit empty result
     * @throws StockDataException on any transport, provider, record or completeness failure
     */
    public StockBatch collectStockData(StockQuery sourceQuery) {
        if (sourceQuery == null) {
            throw new StockDataException("invalid_query");
        }
        List<StockPriceRecord> priceRecords = new ArrayList<>();
        List<RawStockPage> rawPages = new ArrayList<>();
        Set<String> recordIdentities = new HashSet<>();
        int expectedTotal = -1;
        int batchBytes = 0;
        for (int pageNumber = 1; pageNumber <= sourceQuery.maximumPages(); pageNumber++) {
            byte[] rawBody = fetchBody(pageRequest(sourceQuery, pageNumber));
            batchBytes += rawBody.length;
            if (batchBytes > MAX_BATCH_BYTES) {
                throw new StockDataException("body_too_large");
            }
            Instant collectedAt = observationClock.instant();
            var decodedPage = StockPageDecoder.decodePage(rawBody, sourceQuery, pageNumber);
            if (expectedTotal == -1) {
                expectedTotal = decodedPage.totalCount();
                if (expectedTotal > sourceQuery.maximumRecords()
                        || expectedTotal > (long) sourceQuery.pageSize() * sourceQuery.maximumPages()) {
                    throw new StockDataException("incomplete_result");
                }
            }
            if (decodedPage.totalCount() != expectedTotal
                    || decodedPage.priceRecords().size() != Math.min(sourceQuery.pageSize(), expectedTotal - priceRecords.size())) {
                throw new StockDataException("incomplete_result");
            }
            for (StockPriceRecord priceRecord : decodedPage.priceRecords()) {
                String recordIdentity = priceRecord.referenceDate() + ":" + priceRecord.isinCode();
                if (!recordIdentities.add(recordIdentity)) {
                    throw new StockDataException("duplicate_record");
                }
                priceRecords.add(priceRecord);
            }
            rawPages.add(new RawStockPage(pageNumber, collectedAt, rawBody));
            if (priceRecords.size() == expectedTotal) {
                return new StockBatch(sourceQuery, priceRecords, rawPages);
            }
        }
        throw new StockDataException("incomplete_result");
    }

    private StockDataTransport.PageRequest pageRequest(StockQuery sourceQuery, int pageNumber) {
        Map<String, String> publicParameters = new LinkedHashMap<>();
        publicParameters.put("resultType", "xml");
        publicParameters.put("numOfRows", Integer.toString(sourceQuery.pageSize()));
        publicParameters.put("pageNo", Integer.toString(pageNumber));
        if (sourceQuery.fromDate().equals(sourceQuery.toDate())) {
            publicParameters.put("basDt", sourceQuery.fromDate().format(DateTimeFormatter.BASIC_ISO_DATE));
        } else {
            publicParameters.put("beginBasDt", sourceQuery.fromDate().format(DateTimeFormatter.BASIC_ISO_DATE));
            publicParameters.put("endBasDt", sourceQuery.toDate().format(DateTimeFormatter.BASIC_ISO_DATE));
        }
        if (sourceQuery.isinCode() != null) {
            publicParameters.put("isinCd", sourceQuery.isinCode());
        }
        return new StockDataTransport.PageRequest(SOURCE_ENDPOINT, publicParameters, credentialReference, pageNumber);
    }

    private byte[] fetchBody(StockDataTransport.PageRequest pageRequest) {
        if (Thread.currentThread().isInterrupted()) {
            throw new StockDataException("cancelled");
        }
        StockDataTransport.PageResponse pageResponse;
        try {
            pageResponse = sourceTransport.fetchPage(pageRequest);
        } catch (InterruptedException failureValue) {
            Thread.currentThread().interrupt();
            throw new StockDataException("cancelled");
        } catch (IOException | RuntimeException failureValue) {
            throw new StockDataException("transport_failure");
        }
        if (pageResponse == null) {
            throw new StockDataException("transport_failure");
        }
        try (pageResponse) {
            if (Thread.currentThread().isInterrupted()) {
                throw new StockDataException("cancelled");
            }
            if (pageResponse.statusCode() == 429) {
                throw new StockDataException("rate_limited");
            }
            if (pageResponse.statusCode() != 200) {
                throw new StockDataException("provider_rejected");
            }
            requireXmlContentType(pageResponse.contentType());
            byte[] rawBody = pageResponse.bodyStream().readNBytes(MAX_PAGE_BYTES + 1);
            if (rawBody.length > MAX_PAGE_BYTES) {
                throw new StockDataException("body_too_large");
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new StockDataException("cancelled");
            }
            return rawBody;
        } catch (StockDataException failureValue) {
            // Try-with-resources can attach a credential-bearing close failure.
            throw new StockDataException(failureValue.errorCode());
        } catch (IOException | RuntimeException failureValue) {
            throw new StockDataException("transport_failure");
        }
    }

    private static void requireXmlContentType(String contentType) {
        if (contentType == null || contentType.length() > 256) {
            throw new StockDataException("invalid_content_type");
        }
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        if (!normalizedType.matches("(?:application|text)/xml(?:\\s*;\\s*charset\\s*=\\s*(?:utf-8|\"utf-8\"))?\\s*")) {
            throw new StockDataException("invalid_content_type");
        }
    }

    /**
     * Inclusive, bounded source query. Empty provider responses are not holiday evidence.
     *
     * @param fromDate inclusive reference-date lower bound
     * @param toDate inclusive upper bound, at most 365 days after the lower bound
     * @param isinCode optional exact ISIN; null collects all returned instruments
     * @param pageSize between 1 and 1,000 rows
     * @param maximumPages between 1 and 100 pages, with no truncation on excess
     * @param maximumRecords between 1 and 10,000 records, with no truncation on excess
     */
    public record StockQuery(LocalDate fromDate, LocalDate toDate, String isinCode,
                             int pageSize, int maximumPages, int maximumRecords) {
        /** Validate all query bounds before the first transport call. */
        public StockQuery {
            if (fromDate == null || toDate == null || fromDate.isAfter(toDate)
                    || fromDate.getYear() < 1900 || toDate.getYear() > 9999
                    || ChronoUnit.DAYS.between(fromDate, toDate) > 365
                    || (isinCode != null && !isinCode.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]"))
                    || pageSize < 1 || pageSize > 1000 || maximumPages < 1 || maximumPages > 100
                    || maximumRecords < 1 || maximumRecords > 10000) {
                throw new StockDataException("invalid_query");
            }
        }
    }

    /** Immutable raw response evidence; it deliberately contains no request credential. */
    public static final class RawStockPage {
        private final int pageNumber;
        private final Instant collectedAt;
        private final byte[] rawBody;
        private final String sha256Digest;

        private RawStockPage(int pageNumber, Instant collectedAt, byte[] rawBody) {
            this.pageNumber = pageNumber;
            this.collectedAt = collectedAt;
            this.rawBody = rawBody.clone();
            try {
                this.sha256Digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawBody));
            } catch (NoSuchAlgorithmException failureValue) {
                throw new StockDataException("digest_unavailable");
            }
        }

        /** Identify the original page within the collection.
         * @return provider page number
         */
        public int pageNumber() { return pageNumber; }
        /** Keep observation time separate from the provider reference date.
         * @return complete-response observation time
         */
        public Instant collectedAt() { return collectedAt; }
        /** Read the original response without exposing mutable internal storage.
         * @return a defensive copy of the exact response bytes
         */
        public byte[] rawBody() { return rawBody.clone(); }
        /** Identify the original page independently of later field normalization.
         * @return lowercase SHA-256 of the raw body
         */
        public String sha256Digest() { return sha256Digest; }
    }

    /** A complete source result, created only after every requested page is validated. */
    public static final class StockBatch {
        private final StockQuery sourceQuery;
        private final List<StockPriceRecord> priceRecords;
        private final List<RawStockPage> rawPages;

        private StockBatch(StockQuery sourceQuery, List<StockPriceRecord> priceRecords, List<RawStockPage> rawPages) {
            this.sourceQuery = sourceQuery;
            this.priceRecords = List.copyOf(priceRecords);
            this.rawPages = List.copyOf(rawPages);
        }

        /** Recover the exact query whose pagination completed.
         * @return completed query
         */
        public StockQuery sourceQuery() { return sourceQuery; }
        /** Read validated observations without fabricated gap-fill.
         * @return immutable source records
         */
        public List<StockPriceRecord> priceRecords() { return priceRecords; }
        /** Access raw evidence for authorized archival or reprocessing.
         * @return immutable complete-page evidence
         */
        public List<RawStockPage> rawPages() { return rawPages; }
        /** Distinguish this daily publication from a realtime feed.
         * @return delayed-daily classification
         */
        public String freshnessClass() { return "delayed_daily"; }
        /** Avoid asserting corporate-action adjustment without provider evidence.
         * @return provider-unspecified adjustment semantics
         */
        public String adjustmentBasis() { return "provider_unspecified"; }
        /** Expose the currency of this provider profile.
         * @return KRW currency code
         */
        public String currencyCode() { return "KRW"; }
        /** Interpret reference dates in the source market time zone.
         * @return Asia/Seoul reference zone
         */
        public String referenceZone() { return "Asia/Seoul"; }
        /** Distinguish an empty publication from a populated completed result.
         * @return source-result state, not market-calendar status
         */
        public String resultState() { return priceRecords.isEmpty() ? "empty_source_result" : "complete"; }
    }
}
