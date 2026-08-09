# Architecture Decision Records

This index contains status-bearing decisions that govern mightyETL beyond one feature branch. Feature-specific design notes remain useful evidence, but they do not replace canonical ADRs.

| ADR | Status | Decision |
| --- | --- | --- |
| [0001](0001-canonical-documentation-and-status.md) | Accepted | Canonical documentation graph and explicit implementation-status taxonomy |
| [0002](0002-atomic-etl-and-idempotency.md) | Accepted | Whole-batch synchronous transaction and principal-scoped idempotency |
| [0003](0003-durable-job-database-authority.md) | Accepted | PostgreSQL-owned durable-job state, non-destructive stack integration |
| [0004](0004-cdc-delivery-and-lifecycle-truth.md) | Accepted with known gaps | CDC delivery/progress and graceful-stop truthfulness |
| [0005](0005-gateway-identity-boundary.md) | Accepted with known gaps | Fail-closed deployment identity is governing; protected example token remains a tracked implementation gap until the Resource Server path integrates |
| [0006](0006-exact-evidence-and-agent-authority.md) | Accepted | Exact-source evidence, separated agent authorities, writer lease/CAS |
| [0007](0007-standalone-msa-and-connector-truth.md) | Accepted | Standalone + modular MSA operation and honest connector capability |
| [0008](0008-purpose-bound-pii-controls.md) | Accepted | Purpose-bound PII access instead of blanket masking |

## Status semantics

- **Proposed** — under active design review.
- **Accepted** — governing decision for future work.
- **Accepted with known gaps** — decision is governing, while protected implementation still has explicitly tracked gaps.
- **Superseded** — replaced by a later ADR; never silently delete it.
- **Rejected** — considered and not adopted.

An ADR does not make active-PR code shipped. Product implementation status remains separately tracked in `docs/TRACEABILITY.md`.

## ADR update trigger

Write or update an ADR when a change alters a public API/persistence model, security/trust boundary, lifecycle authority, deployment topology, autonomous GitHub authority, compatibility contract, release evidence semantics, or a cross-feature data-governance principle.
