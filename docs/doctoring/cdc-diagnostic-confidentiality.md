# CDC diagnostic confidentiality

## Status and scope

This doctoring note defines the evidence and design boundary for CDC diagnostics that originate from untrusted connector, database, parser, or replicated business data. It is not a claim that every related active pull request is shipped. Protected `develop` remains authoritative for implemented behavior; active repairs such as #170, #171, #172, and #174 remain unshipped until protected integration.

The control is **purpose-bound diagnostic minimization**, not blanket masking of business data. CDC computation may require row identifiers, DDL, connector metadata, or raw event values to perform its authorized task. Ordinary API responses, status payloads, and logs have a narrower operational purpose and therefore should carry finite outcome classifications rather than unnecessary raw values.

## Threat boundary

The following values are sensitive or high-risk diagnostic material unless a separately reviewed purpose requires them:

- row identifiers and other high-cardinality business identifiers;
- JDBC/database connection strings, hosts, usernames, password-like parameters, and provider coordinates;
- raw DDL, SQL, schema/object names, literals, comments, storage paths, or tenant/customer labels;
- raw Debezium key/value JSON and replicated payload fragments;
- parser, driver, broker, database, filesystem, and connector exception messages or stack traces;
- access tokens, session identifiers, secrets, or credential-adjacent values.

Length truncation is not a confidentiality control. A truncated connection string, DDL statement, row identifier, or parser exception can still disclose the sensitive value.

## Required controls

1. **Stable public/status failures.** Public API and health/status representations use bounded, non-sensitive error classifications and messages. They do not concatenate `Exception.getMessage()` or driver diagnostics.
2. **Finite ordinary logs.** Logs retain outcome, subsystem, and a bounded topic/service classification only when operationally necessary. Raw row identifiers, DDL, key/value JSON, SQL, connection strings, exception messages, and stack traces are excluded by default.
3. **Preserve in-process causality where needed.** Programmatic callers may retain the original exception or suppressed exception when the existing execution contract requires causal handling. The exception object does not need to be serialized into ordinary logs merely because it remains available in-process.
4. **No regex-only masking boundary.** Secret-pattern replacement is not the primary design because it cannot enumerate every sensitive identifier or provider diagnostic. Prefer not transporting the untrusted diagnostic in the first place.
5. **Purpose limitation.** Business values remain available to the authorized replication computation. Observability receives only the minimum information required for operation, incident classification, and safe support.
6. **Tests at the real boundary.** Log/API/status tests inject realistic sensitive diagnostics and prove both non-disclosure and preservation of functional behavior such as JDBC execution, fallback, lifecycle, or failure propagation.

## Current traceability

| Work item | Boundary | Canonical maturity |
|---|---|---|
| #170 | replication-slot status and probe diagnostics | `active_pr` |
| #171 | schema-change DDL and execution/parser diagnostics in logs | `active_pr` |
| #172 | CDC stop failure text returned by the API | `active_pr` |
| #173 / #174 | replicated row identifiers and parser diagnostics in `ProcessedDataReplicaApplier` | `active_pr` |

These rows are dated implementation evidence, not a substitute for the canonical Security, Threat Model, Test Strategy, Operability, and Traceability graph maintained through #149/#159.

## Security mapping

MITRE **CWE-532** identifies insertion of sensitive information into a log file as a weakness. The OWASP Logging Cheat Sheet similarly cautions against directly recording sensitive personal data, passwords, access tokens, database connection strings, and other secrets. mightyETL applies those principles by separating computational authority from diagnostic transport instead of destructively masking data required for the authorized ETL/CDC workflow.

## Acceptance implications

A CDC diagnostic-confidentiality repair is incomplete when it merely shortens, partially masks, or relocates a raw diagnostic. Acceptance requires realistic RED evidence at the intended source boundary, the narrowest root-cause GREEN repair, focused and full behavioral verification, current security/dependency/SBOM evidence, and canonical documentation/traceability reconciliation. GitHub synthetic-merge execution is useful compatibility evidence but is not relabeled literal-source proof when governance requires the literal pull-request head.

## References — APA 7

The MITRE Corporation. (2026). *CWE-532: Insertion of sensitive information into log file (Version 4.20).* Common Weakness Enumeration. https://cwe.mitre.org/data/definitions/532.html

The OWASP Foundation. (n.d.). *OWASP Logging Cheat Sheet.* OWASP Cheat Sheet Series. Retrieved August 10, 2026, from https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html
