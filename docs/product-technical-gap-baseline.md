# Product / technical gap baseline

Scope: stock-data source acquisition delta only, observed 2026-09-07. This is not a complete mightyETL readiness assessment. Protected baseline: `develop@e550688c80f0dcf4677c0fbe50bd3341429106fb`; canonical broader documentation remains in open Draft PR #149. Open work is candidate/proposed, not shipped truth.

| Buyer gap | Candidate action | Evidence / remaining gate |
|---|---|---|
| No discovered stock-source adapter | `FscStockDataSource.collectStockData` and typed provider ACL | Added source and executable unit contracts; not protected/released until PR integration |
| A partial history can look complete | Validate page identity, total, row count and duplicate date/ISIN; fail whole call | Focused synthetic unit tests; no upstream snapshot-isolation claim |
| Lost leading zeroes or numeric precision | Strings, `BigDecimal`, `BigInteger`; retain raw pages | Leading-zero, fractional-price and >2^53 assertions |
| Delayed/empty data confused with live trading | `delayed_daily`, `empty_source_result`, source date separate from collection time | Official FSC portal notice; no market-calendar inference |
| No verified released cross-language HTTP authority | Explicit transport port, no automatic network implementation | [EgressWeave #246](https://github.com/ContextualWisdomLab/EgressWeave/issues/246): owner runtime, release and consumer conformance required |
| Provider wire/profile not fully verified | Bound the candidate mapping and preserve unknown fields/raw bytes | Official portal inspected; full primary guide and actual keyed known-day retrieval still required |
| Test/release acceptance incomplete | Existing JUnit entrypoint; warning-free local compile and Javadoc | Local Java 21 subset only; full Java 25 Maven, coverage, security, review, immutable release not proven |
| Durable stock store / revision history absent | Do not mutate generic `processed_data` or create cross-service SQL | Market-data domain owner, archive/revision API, migrations and real DB tests remain separate work |

Source documentation: [ADR](adr/stock_data_source_boundary.md), [PRD/TRD/API/UML slice](stock_data/stock_source_specification.md), [doctoring](doctoring/fsc_stock_data_sources.md), [change fragment](changes/stock_data_source.md), [implementation plan](superpowers/plans/2026-09-07-stock-data-source.md).

No release, live data capture, new schedule, independent approval or 100% coverage is asserted by this document. No existing PR was closed, superseded, force-pushed or stripped of valid delta for this feature.
