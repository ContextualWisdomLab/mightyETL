# FSC Stock Data Source Implementation Plan

> For agentic workers: use the execution and verification skills for each source change.

**Goal:** Allow mightyETL callers to collect a bounded, complete FSC daily-stock result through an explicitly supplied governed transport, without importing a browser or giving the collector credentials.

**Architecture:** A Java provider anti-corruption adapter fits the existing Java ETL host. It owns query construction, XML decoding, exact decimal conversion, complete-result validation and raw-response provenance. EgressWeave remains outbound-policy and transport authority; no HTTP client or Python runtime is embedded here. This slice is not a stock exchange, trading engine, numerical analytics core or a market-data system of record.

**Tech Stack:** Existing Java host; JDK XML, date, decimal and digest APIs; existing JUnit Jupiter test dependency. No dependency or runtime-version change.

**Spec:** `docs/adr/stock_data_source_boundary.md`.

## Global constraints

- Start from `develop@e550688c80f0dcf4677c0fbe50bd3341429106fb` and preserve its tree and other writers.
- No HTTP client, credential materialization, redirect following, service registration or scheduled network work in this feature.
- No fabricated prices or fixture-based live-support claim. Synthetic inputs are unit-test-only.
- Java is provider/host glue, not a new numerical or security runtime. Future Rust transport belongs to EgressWeave and must be released before adoption.
- All-or-error within 366 calendar days, 100 pages, 10,000 records and 16 MiB aggregate XML; 2 MiB per response.
- No endpoint, provider exception, service key, XML fragment or input value in ordinary errors.

## Task 1 — Executable contract and RED

Create `etl-service/src/test/java/com/xtrmetl/etl/stock_data/StockDataContractChecks.java` with real source calls, synthetic XML and a close-tracking transport. Compile it against the absent implementation and retain the missing-source diagnostics as interface RED, not a passing behavior test.

```sh
javac -d /tmp/stock_classes etl-service/src/test/java/com/xtrmetl/etl/stock_data/StockDataContractChecks.java
```

## Task 2 — Provider implementation and GREEN

Create `StockDataException`, `StockDataTransport`, `StockPriceRecord`, `StockPageDecoder`, and `FscStockDataSource` in the matching production package. Validate shape, field multiplicity, UTF-8, DTD/entity prohibition, page identity/count, stable totals, bounds, duplicate instrument/date identities, and OHLC ranges without changing source values. Retain raw pages and SHA-256 digests. Catch transport diagnostics only at the external boundary and preserve interruption.

```sh
javac -Xlint:all -Werror -d /tmp/stock_classes etl-service/src/main/java/com/xtrmetl/etl/stock_data/*.java etl-service/src/test/java/com/xtrmetl/etl/stock_data/StockDataContractChecks.java
java -cp /tmp/stock_classes com.xtrmetl.etl.stock_data.StockDataContractChecks
```

## Task 3 — Existing CI integration and disclosure

Add `FscStockDataSourceTest` as a JUnit entrypoint to the same executable contracts, without replacing other tests or changing CI. Add scoped ADR, usage/field mapping, gap evidence and changelog fragment. Run the commands above, Javadoc with errors on warnings, and the existing full Maven test command where the Java 25 toolchain and dependencies are available. Open one Draft PR; local Java 21 subset success does not establish Java 25/Maven/hosted or live-provider acceptance.

## Required continuation

A released EgressWeave transport binding, provider-approved credential, primary wire-guide receipt and a real keyed FSC retrieval are release gates, not preconditions to writing the provider adapter. Record the owner issue and precise conformance expectations rather than shipping an unrestricted network fallback.
