# ADR: Stock-data source acquisition and transport ownership

Status: Proposed. Date: 2026-09-07. Canonical identity: `stock_data_source_boundary` (semantic filename avoids collisions with the numbered ADR stack in PR #149).

## Problem and evidence

The user requested stock-data collection through CWL libraries. At protected `develop@e550688c80f0dcf4677c0fbe50bd3341429106fb`, mightyETL has a Java ETL host and database/CDC connectors, but no discovered stock-source implementation. An organization code search and a focused open-PR search did not identify an existing stock collector; this is an inventory observation, not proof about private deployments. EgressWeave's protected README describes a Python HTTPX library. OriginWeave's protected README explicitly says its HTTP adapters are not shipped; its release listing returned no releases.

The FSC public-data portal describes daily stock-price/volume data and explicitly states that reference-day data is supplied after 13:00 on the following business day. The introductory marketing phrase about realtime information must not override that operational notice. A Friday result may arrive on Monday or later when holidays intervene. An empty response is not proof of a holiday or a completed publication.

## Alternatives and choice

1. HTML scraping in a product consumer: rejected. It would duplicate acquisition policy, depend on page markup and tempt unsupported fallback after authorization failures.
2. Python/yfinance inside the Java service: rejected. It adds a second consumer runtime and an unverified provider/licensing path for convenience.
3. A new market-data service and financial database: deferred. No independent domain-truth or transaction boundary is needed just to retrieve provider records.
4. A source adapter in mightyETL, behind an explicit governed transport: chosen. The Java code is provider anti-corruption/host glue, not a new numerical analytics or security runtime. It uses JDK exact-value types without rounding or financial inference. Rust remains the implementation policy for new shared numerical and security/performance runtimes. A future Rust network binding belongs to EgressWeave, not a copied consumer client.

## Implemented candidate contract

`FscStockDataSource.collectStockData` accepts an inclusive reference-date range, optional exact ISIN, and bounded pagination budgets. It submits secret-free page requests through `StockDataTransport`; the host transport must resolve a deployment-owned credential reference and materialize `serviceKey` once. There is no built-in HTTP client, network fallback, Spring registration, scheduler, REST endpoint, SQL write or LLM call.

The adapter validates the UTF-8 XML envelope and success code, response page number and size, stable total count, exact expected row count, date/filter membership, identifier grammar, duplicate date/ISIN identity, bounded exact numeric fields and OHLC ranges. It retains source zeroes only when consistent with the range rules; absent/invalid values never become zero. This is not full exchange-rule validation or ISIN-checksum certification.

A complete batch is returned only after every page passes. Raw transfer-decoded XML bytes and SHA-256 digests are retained with per-page `collectedAt`. The provider reference date, observation time, `Asia/Seoul`, `KRW`, `delayed_daily`, and `provider_unspecified` adjustment basis remain distinct. No holiday, publication timestamp, corporate-action adjustment or realtime quote is inferred.

Bounds are consumer safety budgets, not claimed provider quotas: 366 inclusive calendar days, 1,000 rows/page, 100 pages, 10,000 records, 2 MiB/page and 16 MiB raw bytes/batch. Exceeding a budget fails the request instead of silently truncating. Large backfills must be partitioned explicitly by the host and retain separate collection receipts.

XML processing denies DTDs, external entities, schemas and XInclude, limits element depth, and suppresses provider/parser diagnostics. Transport errors, including close/suppressed failures, do not expose URLs, keys or payloads. Cancellation is checked before acquisition and after body delivery/read; response ownership is closed on every outcome.

## Ownership and interoperability

mightyETL owns provider query/field mapping and collection receipts. EgressWeave owns destination authorization, actual socket/DNS pinning, TLS, credentials, transport deadlines, rate/concurrency limits and response framing. The interface is a port, not evidence that those controls have run. Owner issue [EgressWeave #246](https://github.com/ContextualWisdomLab/EgressWeave/issues/246) specifies the missing released cross-language binding and hostile/live conformance gates.

A financial product owns market-data revisions, trading decisions and its database. This adapter does not write `processed_data` or route stock prices through the existing generic AMOUNT transformation. Context Graph Contracts and Enterprise Architecture Core remain read-only dependencies for this slice; no domain schema is copied into their repositories. A future catalog publication must reference a released source contract rather than copy observations into a catalog truth store.

## Risks, acceptance and rollback

Stable counts and duplicate checks detect common pagination corruption but do not prove an upstream transactional snapshot. The provider can revise data without changing its total count. Preserve raw pages and separate per-page observation times; do not advertise snapshot isolation or point-in-time backtest safety.

Local verification covers synthetic unit inputs only. A provider key and real source response were not available. The official portal was inspected, but the complete primary wire guide was not retrieved; wire-profile approval and actual keyed retrieval remain release gates. Full Java 25 Maven CI, measured 100% production coverage, independent review, security/provenance and immutable release evidence are still required. Java 21 focused compilation is development evidence, not a replacement for the repository's Java 25 support gate.

Before production use, complete #246, adopt its immutable release, verify the primary wire profile and run a known-day FSC request plus rate-limit/credential/close-path conformance. There is no automatic activation to roll back. Removing this package removes only the new source capability; existing ETL/CDC behavior and stored data are unchanged.

Root PRD/TRD/README/AGENTS/CLAUDE/CHANGELOG remain in canonical documentation PR #149's ownership. This path-disjoint ADR and the linked stock-specific specification supply the feature delta for ordinary later integration, not a competing whole-file rewrite.

References and source-to-test mapping: [doctoring](../doctoring/fsc_stock_data_sources.md). Usage/specification: [stock source](../stock_data/stock_source_specification.md). Current scope: [gap baseline](../product-technical-gap-baseline.md).
