# Product–Technical Gap Baseline

**Status:** Baseline (living document — update on every merge).
**Date (UTC):** 2026-09-09
**Exact heads:**
- `HEAD (repair/replication-slot-safe-errors-8f96517)`: `0c7137ca1f4c7337938e0c1337f6aa55275b51f3`
- `develop`: `e550688c80f0dcf4677c0fbe50bd3341429106fb`
- `PR #321 base snapshot`: `d6c6665163eabe1b5eca80556c6963bafd6b2625`
**Sources:** `PRD.md` v1.0 (2026-01-08), `TRD.md`, `ARCHITECTURE.md`,
open PR queue (52 open on 2026-09-09), PR #321 CI evidence,
`docs/hourly-pr-disposition.md`.
**Goal linkage:** $20B sale quality + customer-perceived gap closure.
Autonomous KPI loop defined in session goal `ses_f7c4a19d9ffeCt5BGYGKSlk7J9`.

## 1. Method

- Treat AI review comments as hypotheses; change only on code/command evidence
  (`AGENTS.md`).
- Trace every gap to `PRD`/`TRD` clause + current module/API + PR/issue + exact head.
- No `Close` without merge or validated successor full-delta inheritance.
- No force-push; stacked branches stay mergeable; single-writer delta is
  integration, not discard.

## 2. PRD/TRD trace (current evidence)

| ID | Requirement | Current evidence | Verdict |
|----|-------------|------------------|---------|
| FR-CDC-1 | PG env connection, validate on startup | `cdc-service` `EnvUtils`, `ReplicationSlotProbe.probeConfiguredSlot()` reads `CDC_SLOT_NAME` default `xtrmetl_slot` | Meets intent; residual gap G-02 on Config Server authority |
| FR-CDC-2/3 | INSERT/UPDATE/DELETE capture, ordered, Kafka `xtrmetl-cdc.{schema}.{table}`, at-least-once | `ARCHITECTURE.md` §3.2/§8.1, Debezium 3.4.0.Final pinned; replica path `AckMode.RECORD`, `concurrency=1`, retry+DLT | Meets with tuning knobs; ordering caveat documented (schema vs data race) |
| FR-CDC-4 | `POST /api/cdc/start`, `POST /api/cdc/stop`, safe concurrent start/stop | `CdcController`, safe-error fixes (#172, #170) keep diagnostics out of API responses | Meets; confidentiality hardening in progress (#321, #277) |
| FR-ETL-1..4 | `POST /api/etl/process` JSON array, `id` required, uppercase NAME / lowercase EMAIL / 2-decimal AMOUNT, `processed_data` insert, rollback | `EtlService`, transport admission rebuild (#334, #292), amount refresh (#316), UTF-8 plain-text responses (#236) | Meets; transport/amount UTF-8 deltas under review |
| FR-ETL-5 | Parallel `CompletableFuture`, bounded pool, per-record failure isolation | `ARCHITECTURE.md` §3.1; durable-job stack (#254, #256, #143–#148, #135) rebuilds cancellation/replay/poll/lease | Partially meets; stacked chain must land in order |
| FR-AUTH-1..3 | `/auth/signup`, `/auth/signin`, BCrypt, JWT 1h, 401/403, USER/ADMIN | ETL Basic→JWT fail-closed (#287); gateway JWT resource server (#142); legacy auth bootstrap retired (#155) | Meets after #287+#142 land; do not adopt before release |
| FR-DISC-1 / FR-GATE-1 | Eureka register/heartbeat, gateway `/etl/**`→ETL, `/cdc/**`→CDC + JWT filter | `eureka-server`, `zuul-gateway`; gateway docs/tests (#224) | Meets; gateway reactive JWT (#47 stack) still open |
| NFR-PERF-1..3 | CDC lag <1s, ETL >1000 rps, API p95 <500ms | No k6 p95≤20ms evidence in repo yet | Gap G-10 (measurement missing) |
| NFR-REL-1..3 | 99.9% uptime, zero CDC loss, retry 3×1s backoff | Retry/DLT, idempotent retries (`docs/etl/idempotent-retries.md`), bounded batches | Meets pattern; needs chaos/ledger proof |
| NFR-SEC-1..4 | JWT everywhere, RBAC, BCrypt, no hardcoded creds | Gitleaks + secret-scan SKIPPED on PR #321; confidentiality PRs (#321, #277, #176, #174, #192) | Gap G-01 residual; enable secret gate before sale-ready claim |
| NFR-OBS-1..3 | Sleuth+Zipkin, structured logs+correlation, Micrometer | Zipkin transport restored (#167), Replit Zipkin retired (#169), verbose observability local-only (#307) | Meets; keep prod logs free of diagnostics |
| NFR-MAINT-1/2 | Coverage >80%, API/deployment docs | JaCoCo non-vacuous gate (#164); bootstrap docs (#226, #224, #207, #206, #230); machine-readable contracts (#278) | Gap G-03/G-06 open until merged |

## 3. Architecture / UML delta

Text-UML (current, no diagram renderer required):

```text
Client --HTTPS/JWT--> ZuulGateway:8080 --Eureka lookup--> {EtlService:8000, CdcService:8001}
CdcService --Debezium pgoutput--> PostgreSQL:source --publish--> Kafka:9092 --AckMode.RECORD--> ReplicaApply
EtlService --JDBC--> PostgreSQL:target(processed_data, users, roles, user_roles)
Eureka:8761 <--register/heartbeat-- {Gateway, ETL, CDC}; Config:8888 optional; Zipkin:9412 traces
```

Deltas under review: gateway JWT resource server, ETL intake admission
before MVC materialization, durable-job lease-fenced worker, Config Server
explicit repository authority (reject demo/templated/blank authority).

## 4. Gaps (G-xx)

- **G-01 Diagnostic confidentiality residual:** `ReplicationSlotProbe.safeMessage()`
  leaked up to 200 chars of driver diagnostics. Fixed on #321 to stable
  `query_failed` + `Replication slot state unavailable` + DEBUG-only
  exception-class logging. Siblings: DDL redaction (#277), target connector
  logs (#176), replica logs (#174), stop errors (#172), probe leakage (#170),
  DLT headers (#192), DLT poison loop (#197).
- **G-02 Config authority:** blank/demo/templated Config Server authority must
  fail closed. PRs #322, #189, #327, #328. Consumer must use port/ACL/test
  double until owner RED→GREEN→immutable release.
- **G-03 Coverage authority:** repo-wide coverage ownership gap (#323);
  JaCoCo gate was vacuous (#164). Keep gate non-vacuous; do not hide with
  baseline updates.
- **G-04 Scaffold discovery:** MySQL/SQL Server/Qlik scaffolds must stay out
  of production discovery/registry (#158, #163, #156). Verify via contract tests.
- **G-05 AuthN/Z cutover:** ETL Basic→JWT (#287) + gateway JWT (#142) must land
  together; otherwise mixed-mode auth gap.
- **G-06 Contracts/docs:** machine-readable contracts (#278), EnvUtils/bootstrap
  docs (#230, #226, #224, #207, #206), amount integrity (#316), UTF-8 responses
  (#236), Jackson iteration (#325), semantic identifiers (#329).
- **G-07 Review/Checks throughput:** `REVIEW_REQUIRED` + orchestration verdicts
  (`noema-review` 429, `opencode-review` missing exact-head verdict,
  CodeQL `pending`, `strix` 6h timeout) block merge while core CI is green.
  Mitigation: rerun-failed-jobs (done 2026-09-09 for #321), keep hourly
  disposition (`docs/hourly-pr-disposition.md`) as merge-only, never invent code.
- **G-08 Sale-ready surface:** canonical architecture baseline (#149) exists;
  Web UI/admin console remains v2 non-goal per `PRD.md` §10.1.1 and `TRD.md` §1.2.
  Do not promise console for $20B sale; sell API+gateway+Eureka+trace story.
- **G-09 Multi-DB:** MySQL/Oracle/SQL Server CDC out of scope for v1
  (`TRD.md` §1.2). Any `Buyer` placeholder must become real domain name.
- **G-10 Performance proof:** PRD p95<500ms exists; repo instruction p95≤20ms
  via async+k6 E2E has no checked-in k6 evidence. No sample-size reduction,
  no excluded measurements, no unrealistic warm cache.
- **G-11 Supply chain:** Maven Wrapper SHA-256 pinned (#319), Jackson 2.21.5
  baseline (#160), SBOM CycloneDX, Scorecard/Trivy/OSV green on #321; Replit
  bootstrap retired. Keep `orchestrator/free` + exact-SHA reusable workflows.

## 5. Measures (M-xx → PR)

- **M-01** Land #321 (confidential probe) after orchestration reruns green.
- **M-02** Land #322/#189/#327/#328 as one Config-authority unit (non-force restack).
- **M-03** Land #164+#323 (coverage gate + inventory) without baseline hiding.
- **M-04** Land #158/#163/#156 (scaffold removal) with discovery contract tests.
- **M-05** Land #287+#142 together (JWT cutover) + retire legacy bootstrap #155.
- **M-06** Land durable-job stack in dependency order #144→#145→#146→#148→#254→#256→#135→#143.
- **M-07** Land #278 (contracts) + #329 (identifiers) + #325 (Jackson) + #316/#236 (amount/UTF-8).
- **M-08** Keep hourly disposition green-only; rerun transient 429/`pending`/timeout, never force-push.
- **M-09** Add k6 E2E + p95 evidence before claiming ≤20ms; keep Rust hot-path
  option open per repo instruction §6–§7 with ADR scope/rationale/removal criteria.

## 6. KPI linkage (autonomous, no re-ask)

- **K1 Queue:** 52 → 0 via merge or validated successor full-delta inheritance only.
- **K2 Green:** 100% merged PRs have all required checks `success` (no skipped-required).
- **K3 Coverage:** JaCoCo non-vacuous; touched modules keep docstring/test/boundary discipline.
- **K4 Confidentiality:** zero diagnostic/secret leakage; Semgrep/Strix/CodeQL clean.
- **K5 Baseline:** this file updated on every merge with new exact heads + PR links.
- **K6 Hygiene:** no force-push, no concurrent-push race, stacked PRs mergeable,
  manual workarounds codified, `PYTHONPATH`/Actions RCA logged.
- **K7 Know-how:** `AGENTS.md`/`CLAUDE.md` + owner runbook updated; unrun stays unverified.

## 7. Next actions (owner boundaries)

1. Watch #321 reruns (`noema-review`, `opencode-review`, CodeQL compat, `strix`);
   merge only via hourly disposition or maintainer squash when green + threads resolved.
2. Rebase awareness: #321 base `d6c6665` is behind `develop e550688c`; prefer
   non-force `git merge origin/develop` or restack if conflicts, never discard delta.
3. Proceed to #322 (Config authority) review while #321 checks run — do not stall
   whole loop on review/Checks wait.
4. Record hourly-disposition outcomes in `docs/hourly-pr-disposition.md` annex if behavior changes.

## 8. Verification log

- 2026-09-09: PR #321 code review — CodeRabbit actionable item already satisfied
  by `0c7137ca` (DEBUG classification assert + secrecy asserts).
- 2026-09-09: `ReplicationSlotProbeConfidentialityTest` local GREEN on
  Temurin-25.0.4 (`JAVA_HOME` set explicitly; default `java` is 21 and cannot
  run class file v69 — see §9 Know-how).
- 2026-09-09: Core CI on #321 head `0c7137ca` GREEN
  (`test ubuntu/macos/windows`, `Analyze java-kotlin`, `dependency-review`,
  `sbom`, `Semgrep`, `Trivy`, `Scorecard`, `CodeRabbit`, `Devin` pass/skipped-trial).
  Failures isolated to orchestration: `noema-review` 429, `opencode-review`
  missing exact-head verdict, CodeQL `pending`, `strix` infra timeout.
  `rerun-failed-jobs` dispatched via API for `34206598766`, `34206600838`.
- 2026-09-09: Local `java -version` default is Temurin-21; full `./mvnw -B test`
  not run (scope = touched module only). Full suite remains to be verified
  before sale-ready claim.

## 9. Know-how pointer

Detail lives in owner runbook (this file §8 + PR bodies); summary duplicated to
`AGENTS.md`/`CLAUDE.md` on change. Never store secrets/PII here.

## References

- Debezium. (n.d.). *Debezium documentation*. https://debezium.io/documentation/
- Spring. (n.d.). *Spring Cloud documentation*. https://spring.io/projects/spring-cloud
- Apache Kafka. (n.d.). *Kafka documentation*. https://kafka.apache.org/documentation/
- PostgreSQL. (n.d.). *Logical replication*. https://www.postgresql.org/docs/current/logical-replication.html
