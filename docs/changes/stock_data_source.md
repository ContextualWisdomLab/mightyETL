# Unreleased candidate: FSC stock-source acquisition

Add a Java provider adapter in the existing mightyETL ETL host for bounded FSC stock queries, typed exact values, complete-result pagination validation, immutable raw-page evidence and safe cancellation/error/stream handling. No new runtime dependency or workflow is introduced; JUnit invokes the same focused executable contracts.

The source requires an explicit approved transport. It is not yet a released/live-provider-verified capability. EgressWeave #246 owns the missing immutable cross-language transport binding. The full primary wire guide, real keyed retrieval, Java 25 reactor, coverage, security and independent review remain release gates.

Hourly fire 2026-09-08: review findings on Draft #333 were verified against `06ccc7f`. Late cancellation after body read can no longer return a completed batch; undeclared structural XML children are rejected; contract checks now cover cumulative batch-byte overflow, read/close failures, and an exact SHA-256 digest. The code-quality comment on `StockBatch.priceRecords()` remains a false positive: the private constructor already stores `List.copyOf`. No HTTP client was added.

This fragment is supplied to canonical documentation PR #149 rather than rewriting its concurrently owned root CHANGELOG/PRD/TRD/README/AGENTS/CLAUDE files. Merge it into the root changelog only with the actual integrated feature and its evidence; do not backdate a release or mark a Proposed ADR Accepted solely because code exists.
