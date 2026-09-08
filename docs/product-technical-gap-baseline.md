# Product / technical gap baseline

Observed 2026-09-08. Protected baseline: `develop@e550688c80f0dcf4677c0fbe50bd3341429106fb`. Canonical broader documentation remains in open Draft PR #149. Open work is candidate/proposed, not shipped truth. Keep Product Requirements Document names and case: Change Data Capture (CDC), Extract-Transform-Load (ETL), JWT, Eureka, Config Server.

## PRD vs evidence

| PRD capability | Current evidence | Remaining gap |
|---|---|---|
| Change Data Capture from PostgreSQL | Live path Postgres→Kafka; slot probe, replica allow-list, ops docs | Multi-source Debezium engines; MySQL/SQL Server are discovery scaffolds |
| Extract-Transform-Load JSON pipeline | Bounded batches, idempotent retries, RFC 9457 errors, Flyway schema authority | Durable job worker/replay/cancellation still stacked as drafts; amount integrity refresh is draft #316 |
| JWT authentication and RBAC | Gateway placeholder-token repair is draft #142; ETL JWT fail-closed is draft #287 | Production JWT resource-server path not on `develop` |
| Qlik Sense / Databricks / Snowflake | SPI + YAML + required-key validation + catalog; writes refused | Live SaaS loaders need credentials and real clients |
| Any-to-any CDC | Source/target SPI; live Postgres→Kafka only | Do not market warehouse loaders as supported |
| Stock observations (this fire) | Candidate `FscStockDataSource` on Draft #333 | No released transport; no live keyed FSC retrieval |

## TRD / UML

Ports remain: Gateway 8080, ETL 8000, CDC 8001, Eureka 8761, Zipkin 9412. Java 25 / Spring Boot 3.5.9 / Debezium 3.4.0.Final. UML for stock collection is the sequence in `docs/stock_data/stock_source_specification.md`; the transport participant is an owner port, not a shipped service. Broader architecture diagrams stay with Draft #149.

## Connector / owner linkage

Chicken-and-egg is broken with a minimum port, not a consumer HTTP clone. mightyETL owns `StockDataTransport` and FSC field ACL. [EgressWeave #246](https://github.com/ContextualWisdomLab/EgressWeave/issues/246) owns destination authorization, TLS, credentials, deadlines, rate limits, and the missing released cross-language binding. OriginWeave HTTP adapters remain unshipped. No default Java HTTP, curl, or Python client is added in this consumer.

Warehouse connectors stay scaffolds. CDC registry snapshot immutability is issue #246 in this repo (distinct from EgressWeave #246).

## Open actions (2026-09-08)

Non-draft PRs targeting `develop` are all `mergeable_state=blocked`; none merged this fire.

| ID | State | Note |
|---|---|---|
| PR #333 | draft | Stock-data candidate; this fire repairs verified review findings |
| PR #330 | blocked | Reusable dependency-review caller; waits on ContextualWisdomLab/.github#1724 |
| PR #328 | needs-review | Config Server authority successor of #327/#322; not merged, so predecessors stay open |
| PR #327 / #322 | needs-review | Do not merge; #328 claims succession but is not protected-integrated |
| PR #326 | needs-review | Hourly central PR maintenance; stale base `d6c6665` |
| PR #321 | needs-review | Replication-probe confidentiality; stale base, checks from 2026-08-15 |
| PR #329 | draft | CDC semantic identifiers |
| Draft stack #254/#256 and older durable-job PRs | draft | Stacked on non-`develop` bases; restack later, do not close |
| Issue #247 | open | ETL request-size limits before full body materialization |
| Issue #252 | open | Fail closed before protected merges on non-qualifying evidence |

Displayed closed is not done. No PR was closed this fire.

## Stock-data candidate (Draft #333)

| Buyer gap | Candidate action | Evidence / remaining gate |
|---|---|---|
| No discovered stock-source adapter | `FscStockDataSource.collectStockData` and typed provider ACL | Added source and executable unit contracts; not protected/released until PR integration |
| A partial history can look complete | Validate page identity, total, row count and duplicate date/ISIN; fail whole call | Focused synthetic unit tests; no upstream snapshot-isolation claim |
| Lost leading zeroes or numeric precision | Strings, `BigDecimal`, `BigInteger`; retain raw pages | Leading-zero, fractional-price and >2^53 assertions |
| Delayed/empty data confused with live trading | `delayed_daily`, `empty_source_result`, source date separate from collection time | Official FSC portal notice; no market-calendar inference |
| Malformed provider envelope accepted | Reject undeclared/duplicate structural XML children | Review P1 on #333; envelope tests added this fire |
| Late cancel returned success | Check interrupt after decode and before batch return | Clock.instant() interrupt contract added this fire |
| No verified released cross-language HTTP authority | Explicit transport port, no automatic network implementation | EgressWeave #246: owner runtime, release and consumer conformance required |
| Provider wire/profile not fully verified | Bound the candidate mapping and preserve unknown fields/raw bytes | Official portal inspected; full primary guide and actual keyed known-day retrieval still required |
| Test/release acceptance incomplete | Existing JUnit entrypoint; warning-free local compile and Javadoc | Local Java 21 subset; full Java 25 Maven, coverage, security, review, immutable release not proven |
| Durable stock store / revision history absent | Do not mutate generic `processed_data` or create cross-service SQL | Market-data domain owner, archive/revision API, migrations and real DB tests remain separate work |

Source documentation: [ADR](adr/stock_data_source_boundary.md) (Proposed, not Accepted), [PRD/TRD/API/UML slice](stock_data/stock_source_specification.md), [doctoring](doctoring/fsc_stock_data_sources.md), [change fragment](changes/stock_data_source.md), [implementation plan](superpowers/plans/2026-09-07-stock-data-source.md).

This fire does not mark the ADR Accepted, claim UI completeness, force-push, or advertise live stock crawling. No existing PR was closed, superseded, or stripped of valid delta.
