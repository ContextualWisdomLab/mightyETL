package com.xtrmetl.etl.stock_data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Dependency-free unit contracts, also executed by the repository's JUnit suite. */
public final class StockDataContractChecks {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-07T07:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate SOURCE_DATE = LocalDate.of(2026, 9, 4);
    private static int assertionCount;

    private StockDataContractChecks() { }

    /** Run the same tests without downloading a second test framework. */
    public static void main(String[] commandArguments) throws Exception {
        verifyAll();
        System.out.println("stock-data assertions passed: " + assertionCount);
    }

    /** Exercise production query, transport-boundary, parser, and completeness behavior. */
    public static void verifyAll() throws Exception {
        assertionCount = 0;
        verifyCompleteCollection();
        verifyEmptyAndSingleton();
        verifyQueryRejection();
        verifyInvalidPages();
        verifyInvalidRecords();
        verifyHostileXml();
        verifyTransportFailures();
        verifyResourceBounds();
        verifyLateCancellationAndSafeFormatting();
    }

    private static FscStockDataSource.StockQuery sourceQuery(int pageSize) {
        return new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE, null, pageSize, 100, 10000);
    }

    private static FscStockDataSource sourceFor(List<String> responseBodies, List<StockDataTransport.PageRequest> observedRequests) {
        return new FscStockDataSource(requestValue -> {
            observedRequests.add(requestValue);
            return new StockDataTransport.PageResponse(200, "application/xml; charset=UTF-8",
                    new ByteArrayInputStream(responseBodies.get(requestValue.pageNumber() - 1).getBytes(StandardCharsets.UTF_8)));
        }, "fsc_stock_key", FIXED_CLOCK);
    }

    private static void verifyCompleteCollection() {
        List<StockDataTransport.PageRequest> observedRequests = new ArrayList<>();
        String firstBody = pageXml(1, 1, 2, itemXml("005930", "KR7005930003", "20260904"));
        String secondBody = pageXml(2, 1, 2, itemXml("000660", "KR7000660001", "20260904"));
        var sourceBatch = sourceFor(List.of(firstBody, secondBody), observedRequests).collectStockData(sourceQuery(1));
        requireEqual(2, sourceBatch.priceRecords().size(), "all pages are collected");
        requireEqual(2, observedRequests.size(), "exact request count");
        requireEqual("005930", sourceBatch.priceRecords().get(0).shortCode(), "leading zero preserved");
        requireEqual(new BigDecimal("70000.25"), sourceBatch.priceRecords().get(0).closePrice(), "decimal is exact");
        requireEqual("9007199254740993", sourceBatch.priceRecords().get(0).tradingValue().toString(), "large values are exact");
        requireEqual(FIXED_CLOCK.instant(), sourceBatch.rawPages().get(0).collectedAt(), "observation time preserved");
        requireEqual(firstBody, new String(sourceBatch.rawPages().get(0).rawBody(), StandardCharsets.UTF_8), "raw body preserved");
        requireEqual(64, sourceBatch.rawPages().get(0).sha256Digest().length(), "digest exists");
        requireEqual("provider_unspecified", sourceBatch.adjustmentBasis(), "adjustment not invented");
        requireEqual("delayed_daily", sourceBatch.freshnessClass(), "not realtime");
        requireEqual("xml", observedRequests.get(0).publicParameters().get("resultType"), "XML selected");
        requireEqual("20260904", observedRequests.get(0).publicParameters().get("basDt"), "explicit date");
        requireEqual(null, observedRequests.get(0).sourceEndpoint().getRawQuery(), "endpoint contains no credentials");
        requireEqual(false, observedRequests.get(0).publicParameters().containsKey("serviceKey"), "no key materialization");
        byte[] exposedBytes = sourceBatch.rawPages().get(0).rawBody();
        exposedBytes[0] = 0;
        requireEqual(firstBody, new String(sourceBatch.rawPages().get(0).rawBody(), StandardCharsets.UTF_8), "raw body immutable");
        expectException(UnsupportedOperationException.class, () -> sourceBatch.priceRecords().clear());
        expectException(UnsupportedOperationException.class, () -> observedRequests.get(0).publicParameters().put("serviceKey", "secret"));
        List<StockDataTransport.PageRequest> filteredRequests = new ArrayList<>();
        var filteredQuery = new FscStockDataSource.StockQuery(SOURCE_DATE.minusDays(1), SOURCE_DATE, "KR7005930003", 1, 1, 1);
        sourceFor(List.of(pageXml(1, 1, 1, itemXml("005930", "KR7005930003", "20260904"))), filteredRequests).collectStockData(filteredQuery);
        requireEqual("20260903", filteredRequests.get(0).publicParameters().get("beginBasDt"), "range lower bound");
        requireEqual("20260904", filteredRequests.get(0).publicParameters().get("endBasDt"), "range upper bound");
        requireEqual("KR7005930003", filteredRequests.get(0).publicParameters().get("isinCd"), "exact ISIN filter");
    }

    private static void verifyEmptyAndSingleton() {
        var emptyBatch = sourceFor(List.of(pageXml(1, 10, 0, "")), new ArrayList<>()).collectStockData(sourceQuery(10));
        requireEqual(0, emptyBatch.priceRecords().size(), "empty is not a fabricated candle");
        requireEqual("empty_source_result", emptyBatch.resultState(), "empty does not imply holiday");
        var oneBatch = sourceFor(List.of(pageXml(1, 10, 1, itemXml("005930", "KR7005930003", "20260904"))), new ArrayList<>()).collectStockData(sourceQuery(10));
        requireEqual(1, oneBatch.priceRecords().size(), "singleton retained");
        String noTrades = itemXml("005930", "KR7005930003", "20260904")
                .replace("<mkp>70000</mkp>", "<mkp>0</mkp>").replace("<hipr>71000</hipr>", "<hipr>0</hipr>")
                .replace("<lopr>69000</lopr>", "<lopr>0</lopr>").replace("<trqu>1000</trqu>", "<trqu>0</trqu>")
                .replace("<trPrc>9007199254740993</trPrc>", "<trPrc>0</trPrc>");
        requireEqual(BigDecimal.ZERO, sourceFor(List.of(pageXml(1, 10, 1, noTrades)), new ArrayList<>()).collectStockData(sourceQuery(10)).priceRecords().get(0).openPrice(), "source zero with no trades preserved");
    }

    private static void verifyQueryRejection() {
        for (int invalidSize : new int[]{0, -1, 1001}) {
            expectCode("invalid_query", () -> sourceQuery(invalidSize));
        }
        expectCode("invalid_query", () -> new FscStockDataSource.StockQuery(null, SOURCE_DATE, null, 10, 100, 10000));
        expectCode("invalid_query", () -> new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE.minusDays(1), null, 10, 100, 10000));
        expectCode("invalid_query", () -> new FscStockDataSource.StockQuery(SOURCE_DATE.minusDays(366), SOURCE_DATE, null, 10, 100, 10000));
        expectCode("invalid_query", () -> new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE, "bad&serviceKey=secret", 10, 100, 10000));
        expectCode("invalid_query", () -> new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE, null, 10, 0, 10000));
        expectCode("invalid_query", () -> new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE, null, 10, 101, 10000));
        expectCode("invalid_query", () -> new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE, null, 10, 100, 0));
        expectCode("invalid_query", () -> new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE, null, 10, 100, 10001));
        expectCode("invalid_query", () -> new FscStockDataSource(null, "fsc_stock_key", FIXED_CLOCK));
        expectCode("invalid_query", () -> new FscStockDataSource(requestValue -> null, "raw/key+material=", FIXED_CLOCK));
        expectCode("invalid_query", () -> sourceFor(List.of(), new ArrayList<>()).collectStockData(null));
    }

    private static void verifyInvalidPages() {
        String validItem = itemXml("005930", "KR7005930003", "20260904");
        for (String invalidBody : List.of(
                pageXml(2, 10, 1, validItem), pageXml(1, 9, 1, validItem),
                pageXml(1, 10, 2, validItem), pageXml(1, 10, 0, validItem),
                pageXml(1, 10, -1, ""), pageXml(1, 10, 1, validItem).replace("<pageNo>1</pageNo>", "<pageNo>1</pageNo><pageNo>1</pageNo>"))) {
            expectFailure(() -> sourceFor(List.of(invalidBody), new ArrayList<>()).collectStockData(sourceQuery(10)));
        }
        expectCode("incomplete_result", () -> sourceFor(List.of(pageXml(1, 1, 2, validItem), pageXml(2, 1, 3, validItem)), new ArrayList<>()).collectStockData(sourceQuery(1)));
        expectCode("duplicate_record", () -> sourceFor(List.of(pageXml(1, 1, 2, validItem), pageXml(2, 1, 2, validItem)), new ArrayList<>()).collectStockData(sourceQuery(1)));
        expectCode("incomplete_result", () -> sourceFor(List.of(pageXml(1, 1, 2, validItem)), new ArrayList<>()).collectStockData(new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE, null, 1, 1, 2)));
        expectCode("incomplete_result", () -> sourceFor(List.of(pageXml(1, 1, 2, validItem)), new ArrayList<>()).collectStockData(new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE, null, 1, 2, 1)));
        expectCode("provider_rejected", () -> sourceFor(List.of("<response><header><resultCode>30</resultCode><resultMsg>secret</resultMsg></header></response>"), new ArrayList<>()).collectStockData(sourceQuery(10)));
    }

    private static void verifyInvalidRecords() {
        String validItem = itemXml("005930", "KR7005930003", "20260904");
        for (String invalidItem : List.of(
                validItem.replace("70000.25", "-"), validItem.replace("70000.25", "NaN"), validItem.replace("70000.25", "1e1000"),
                validItem.replace("70000.25", "-1"), validItem.replace("70000.25", "99999"),
                validItem.replace("<trqu>1000</trqu>", "<trqu>-1</trqu>"),
                validItem.replace("<trqu>1000</trqu>", "<trqu>1.5</trqu>"),
                validItem.replace("20260904", "20260905"), validItem.replace("20260904", "20260230"),
                validItem.replace("<clpr>70000.25</clpr>", ""),
                validItem.replace("<clpr>70000.25</clpr>", "<clpr>1</clpr><clpr>2</clpr>"),
                validItem.replace("<itmsNm>단위시험종목</itmsNm>", "<itmsNm></itmsNm>"),
                validItem.replace("KR7005930003", "not-an-isin"))) {
            expectFailure(() -> sourceFor(List.of(pageXml(1, 10, 1, invalidItem)), new ArrayList<>()).collectStockData(sourceQuery(10)));
        }
        var filteredQuery = new FscStockDataSource.StockQuery(SOURCE_DATE, SOURCE_DATE, "KR7000660001", 10, 100, 10000);
        expectCode("invalid_record", () -> sourceFor(List.of(pageXml(1, 10, 1, validItem)), new ArrayList<>()).collectStockData(filteredQuery));
    }

    private static void verifyHostileXml() {
        for (String hostileBody : List.of("<html>login</html>", "<response>",
                "<!DOCTYPE response [<!ENTITY payload SYSTEM 'file:///etc/passwd'>]><response>&payload;</response>",
                "<response xmlns='https://attacker.invalid'><header/></response>",
                "<response>" + "<nest>".repeat(40) + "</nest>".repeat(40) + "</response>")) {
            expectFailure(() -> sourceFor(List.of(hostileBody), new ArrayList<>()).collectStockData(sourceQuery(10)));
        }
    }

    private static void verifyTransportFailures() {
        expectCode("transport_failure", () -> new FscStockDataSource(requestValue -> { throw new IOException("https://provider.invalid/?serviceKey=secret"); }, "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
        expectCode("transport_failure", () -> new FscStockDataSource(requestValue -> { throw new IllegalStateException("secret"); }, "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
        expectCode("transport_failure", () -> new FscStockDataSource(requestValue -> null, "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
        for (int statusCode : new int[]{301, 401, 403, 500}) {
            boolean[] closedBody = {false};
            expectCode("provider_rejected", () -> new FscStockDataSource(requestValue -> new StockDataTransport.PageResponse(statusCode, "application/xml", trackedBody("secret", closedBody)), "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
            requireEqual(true, closedBody[0], "rejected HTTP body closed");
        }
        expectCode("rate_limited", () -> new FscStockDataSource(requestValue -> new StockDataTransport.PageResponse(429, "application/xml", new ByteArrayInputStream(new byte[0])), "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
        for (String contentType : new String[]{null, "text/html", "application/xml; charset=ISO-8859-1"}) {
            expectCode("invalid_content_type", () -> new FscStockDataSource(requestValue -> new StockDataTransport.PageResponse(200, contentType, new ByteArrayInputStream(new byte[0])), "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
        }
        expectCode("cancelled", () -> new FscStockDataSource(requestValue -> { throw new InterruptedException("secret"); }, "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
        requireEqual(true, Thread.interrupted(), "interrupt restored and cleared by test");
        expectCode("invalid_xml", () -> new FscStockDataSource(requestValue -> new StockDataTransport.PageResponse(200, "application/xml", new ByteArrayInputStream(new byte[]{(byte)0xff})), "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
    }

    private static void verifyLateCancellationAndSafeFormatting() {
        var requestValue = new StockDataTransport.PageRequest(FscStockDataSource.SOURCE_ENDPOINT, Map.of(), "fsc_stock_key", 1);
        requireEqual(false, requestValue.toString().contains("fsc_stock_key"), "credential reference not formatted");
        boolean[] closedBody = {false};
        try {
            expectCode("cancelled", () -> new FscStockDataSource(pageRequest -> {
                Thread.currentThread().interrupt();
                return new StockDataTransport.PageResponse(200, "application/xml", trackedBody(pageXml(1, 10, 0, ""), closedBody));
            }, "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
        } finally {
            requireEqual(true, Thread.interrupted(), "late cancellation remains signalled");
        }
        requireEqual(true, closedBody[0], "late cancelled body closed");
    }

    private static void verifyResourceBounds() {
        boolean[] closedBody = {false};
        expectCode("body_too_large", () -> new FscStockDataSource(requestValue -> new StockDataTransport.PageResponse(200, "application/xml", trackedBody(" ".repeat(2 * 1024 * 1024 + 1), closedBody)), "fsc_stock_key", FIXED_CLOCK).collectStockData(sourceQuery(10)));
        requireEqual(true, closedBody[0], "oversized body closed");
    }

    private static InputStream trackedBody(String bodyText, boolean[] closedBody) {
        return new ByteArrayInputStream(bodyText.getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() throws IOException { closedBody[0] = true; super.close(); }
        };
    }

    private static String pageXml(int pageNumber, int pageSize, int totalCount, String itemContent) {
        return "<response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header><body><numOfRows>" + pageSize + "</numOfRows><pageNo>" + pageNumber + "</pageNo><totalCount>" + totalCount + "</totalCount><items>" + itemContent + "</items></body></response>";
    }

    private static String itemXml(String shortCode, String isinCode, String sourceDate) {
        return "<item><basDt>" + sourceDate + "</basDt><srtnCd>" + shortCode + "</srtnCd><isinCd>" + isinCode + "</isinCd><itmsNm>단위시험종목</itmsNm><mrktCtg>KOSPI</mrktCtg><clpr>70000.25</clpr><vs>-8</vs><fltRt>-4.57</fltRt><mkp>70000</mkp><hipr>71000</hipr><lopr>69000</lopr><trqu>1000</trqu><trPrc>9007199254740993</trPrc><lstgStCnt>10000</lstgStCnt><mrktTotAmt>700002500</mrktTotAmt></item>";
    }

    private static void requireEqual(Object expectedValue, Object actualValue, String assertionName) {
        assertionCount++;
        if (!java.util.Objects.equals(expectedValue, actualValue)) {
            throw new AssertionError(assertionName + ": expected " + expectedValue + " got " + actualValue);
        }
    }

    private static void expectFailure(Runnable operationCall) {
        assertionCount++;
        try { operationCall.run(); } catch (StockDataException failureValue) {
            requireEqual(null, failureValue.getCause(), "no unsafe cause");
            requireEqual(false, failureValue.toString().contains("secret"), "no leaked secret");
            return;
        }
        throw new AssertionError("expected a stock-data failure");
    }

    private static void expectCode(String expectedCode, Runnable operationCall) {
        assertionCount++;
        try { operationCall.run(); } catch (StockDataException failureValue) {
            requireEqual(expectedCode, failureValue.errorCode(), "stable error code");
            requireEqual(null, failureValue.getCause(), "no unsafe cause");
            requireEqual(false, failureValue.toString().contains("secret"), "no leaked secret");
            return;
        }
        throw new AssertionError("expected " + expectedCode);
    }

    private static void expectException(Class<? extends RuntimeException> expectedType, Runnable operationCall) {
        assertionCount++;
        try { operationCall.run(); } catch (RuntimeException failureValue) {
            if (expectedType.isInstance(failureValue)) { return; }
            throw failureValue;
        }
        throw new AssertionError("expected " + expectedType.getSimpleName());
    }
}
