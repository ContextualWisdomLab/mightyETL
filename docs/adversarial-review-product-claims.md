# Adversarial product-claims review

**Scope:** Challenge four product claims against the real codebase, docs, and config.  
**Workspace:** `/home/seonghobae/mightyETL` (git remote `ContextualWisdomLab/mightyETL`, branch context `develop`)  
**Method:** Hostile evidence pass only — no renames, no new connectors.  
**Inspected:** `README.md`, `PRD.md`, `TRD.md`, `ARCHITECTURE.md`, `CHANGELOG.md`, `SUMMARY_KR.md`, root/`cdc-service`/`etl-service` `pom.xml`, `cdc-service/**`, `etl-service/**` (surface), `docker-compose.yml`, `docs/**`, package namespaces.  
**Review date:** 2026-07-31  
**Pass:** 1 (single fire)

> **Superseding note (post product-upgrade fires, 2026-07-31):** User-facing docs and APIs now brand **mightyETL**; dual-read config, CDC ops APIs/health/slot lag, connector SPI scaffolds, and any-to-any SPI exist. Live capture is still **Postgres→Kafka**; warehouse loaders remain scaffolds. See `README.md` “Supported today”, `docs/mightyETL-product-upgrade-progress.md`, and `docs/rebrand-name-matrix.md`. The FAIL rows below describe **pass-1 pre-upgrade** evidence and should not be cited without re-checking those docs.

---

## Executive verdict

**Do not market this product as “mightyETL any-to-any CDC with Qlik / Databricks / Snowflake support.”**

What exists today is a **PostgreSQL-centric** Spring microservices stack still branded **xtrmETL** in product surfaces (`com.xtrmetl`, Maven artifact `xtrmETL`, topic prefix `xtrmetl-cdc`, docs titles). CDC is real but narrow: **Debezium embedded Postgres connector → Kafka**, optional **Postgres→Postgres replica apply limited to `processed_data`**, with useful retry/DLT for that path only.

| Claim | Verdict |
|:------|:--------|
| 1. Product name is / will be mightyETL | **FAIL** |
| 2. CDC service is strengthened | **PARTIAL** |
| 3. Qlik Sense, Databricks, Snowflake support | **FAIL** |
| 4. Any-to-any CDC | **FAIL** |

**Scoreboard:** pass=0 partial=1 fail=3 | **open critical marketing blockers=4** (see accepted residual risks for ownership; review itself is complete)

---

## Claim 1 — Product name is / will be mightyETL (not old names)

### Verdict: **FAIL**

### What “PASS” would require

- Primary product docs, Maven coordinates, Java packages, image names, topic defaults, and UI/copy say **mightyETL** (or document a dated rebrand plan with dual-name migration).
- Old name limited to historical changelog footnotes.

### Evidence against the claim

| Surface | Observed name | Path |
|:--------|:--------------|:-----|
| GitHub remote / workspace folder | `mightyETL` | `git remote` → `https://github.com/ContextualWisdomLab/mightyETL` |
| Root Maven artifact | `xtrmETL` / `com.xtrmetl` | `/home/seonghobae/mightyETL/pom.xml` L7–8 |
| README title | “xtrmETL - Enterprise ETL and CDC Platform” | `README.md` L1 |
| PRD / TRD / ARCHITECTURE | xtrmETL throughout | `PRD.md` L7, `TRD.md` L3, `ARCHITECTURE.md` L1 |
| Java packages | `com.xtrmetl.*` | e.g. `cdc-service/.../CdcService.java`, `etl-service/.../EtlService.java` |
| Debezium connector default name | `xtrmetl-postgres-connector` | `CdcService.java` L216 |
| Topic prefix default | `xtrmetl-cdc` | `CdcService.java` L223; `application.yml` replica topic pattern |
| Offset/data dir default | `~/.xtrmetl/cdc` | `CdcService.java` L239–242 |
| Docker DB defaults | user/db `xtrmetl` | `docker-compose.yml` |
| Suggested image tags | `xtrmetl/etl-service` etc. | `README.md` ~L432–435 |
| Design notes filename | `xtrmETL-common-initial-design-notes.txt` | repo root |
| CHANGELOG | “existing xtrmETL platform” | `CHANGELOG.md` L24 |

### Gaps / overclaims

- **Repo/product split-brain:** directory + GitHub org name imply mightyETL; **every shippable product identity string still says xtrmETL**.
- No rebrand plan, dual-name matrix, or “formerly xtrmETL” banner found under `docs/` or root docs.
- No occurrence of product string `mightyETL` in primary product docs (only workspace/remote naming).

### Marketing-safe statement (honest)

> Repository may be named **mightyETL**; the **product identity in code and docs remains xtrmETL** until a deliberate rebrand lands.

---

## Claim 2 — CDC service is strengthened (capabilities, reliability, docs, ops)

### Verdict: **PARTIAL**

### What is real (credit, not marketing)

**Capabilities (bounded):**

- Embedded Debezium **Postgres only**: `debezium-connector-postgres` 3.4.0.Final in `cdc-service/pom.xml` L41–55; `connector.class=io.debezium.connector.postgresql.PostgresConnector` in `CdcService.java` L217.
- Kafka publish of change events (`handleChangeEvent` → `KafkaTemplate.send`) in `CdcService.java` L185–198.
- Optional **replica path** when `xtrmetl.replica.enabled=true`:
  - Consumer: `CdcReplicaConsumer.java`
  - Data apply: `ProcessedDataReplicaApplier.java` — **hard-coded table `processed_data` only** (L22–30, L46–48)
  - DDL apply (optional, default off): `SchemaChangeReplicaApplier.java` + config in `application.yml` L24–31
- Ops knobs: `CDC_*` env vars, autostart, schema include/table include lists (`CdcService.getCdcConfiguration` L208–236).
- Docker: primary + `postgres-replica`, Kafka, CDC env wiring in `docker-compose.yml`.

**Reliability (replica apply path only):**

- Retry + dead-letter: `KafkaConfig.java` `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` → `topic.DLT` (L28–43).
- `AckMode.RECORD` after successful apply (L75–76).
- Ordering caveats documented: `ARCHITECTURE.md` §10.2–10.3 (HA: one CDC instance per table; DDL vs data out-of-order risk).
- Unit tests present under `cdc-service/src/test/java/com/xtrmetl/cdc/**` (controller, service, replica appliers, Kafka config).

**Docs / ops:**

- README CDC section, env vars for replica, logical replication prerequisites.
- `ARCHITECTURE.md` replica tuning knobs L646–656.
- Actuator health/info exposure; Zipkin tracing config in `cdc-service/.../application.yml`.
- Control API: only `POST /api/cdc/start` and `/stop` (`CdcController.java`) — no lag, connector status, or replica-toggle admin APIs implemented (those are PRD **v2 UI** wish-list items).

### Evidence of overclaim if marketed as “enterprise-strengthened CDC”

| Gap | Evidence |
|:----|:---------|
| **Source DB = PostgreSQL only** | Single connector dep; TRD §1.2 “Multi-database support beyond PostgreSQL” **out of scope for v1** |
| **Replica target = one table schema** | `ProcessedDataReplicaApplier` fixed `processed_data` upsert/delete |
| **Offset store is local files** | `FileOffsetBackingStore` + `~/.xtrmetl/cdc/offsets.dat` — poor multi-instance/HA story (`CdcService.java` L231–235) |
| **Kafka send is fire-and-forget** | `kafkaTemplate.send` without callback/get in `handleChangeEvent` — at-least-once **not enforced** at producer wait layer despite PRD FR-CDC-3 language |
| **Default capture surface is tiny** | Default `table.include.list=public.processed_data` |
| **No multi-active HA** | ARCHITECTURE §10.2: only one instance should monitor a given table; no leader election implemented |
| **Config Server “future”** | README architecture still marks Config Server as future; TRD says optional |
| **CHANGELOG “strengthening”** | `[1.0.0]` is largely **documentation reverse-engineering** of existing platform, not a reliability epic (`CHANGELOG.md`) |
| **Technical debt called out by product itself** | `PRD.md` §10.2: common module missing, etc. |

### Marketing-safe statement (honest)

> CDC is **materially usable for PostgreSQL → Kafka**, with an **optional Postgres replica applier for `processed_data`** that includes retry/DLT. It is **not** a fully hardened multi-source enterprise CDC fabric.

---

## Claim 3 — Qlik Sense, Databricks, and Snowflake support exists (or is honestly scoped)

### Verdict: **FAIL** (not present; also **not honestly scoped** in any connector matrix)

### Search result

- Repo-wide content search for `Qlik`, `Databricks`, `Snowflake`: **no matches** in product code, `pom.xml` deps, `docker/`, or primary docs.
- Connector inventory in code:
  - **Only** `io.debezium.connector.postgresql.PostgresConnector`
  - **Only** `debezium-connector-postgres` Maven artifact
- ETL load path: JDBC into PostgreSQL `processed_data` (`EtlService` + README target table SQL) — not warehouse loaders, not Spark/Databricks jobs, not Snowflake COPY/Snowpipe, not Qlik connectors/ODBC apps.

### Closest “honest scope” language that *does* exist (still not those three products)

- `TRD.md` §1.2 Out of scope v1: multi-database beyond PostgreSQL.
- `PRD.md` §10.1 Planned v2: “Multi-database Support: MySQL, Oracle, SQL Server CDC” — **still not** Qlik / Databricks / Snowflake.
- No adapter interfaces, SPI, or stub modules for BI/cloud warehouses.

### Gaps / overclaims

| Claimed product | Implementation | Docs honesty |
|:----------------|:---------------|:-------------|
| Qlik Sense | None | Unmentioned |
| Databricks | None | Unmentioned |
| Snowflake | None | Unmentioned |

Any external claim of support for these three is **pure marketing fiction** relative to this tree.

### Marketing-safe statement (honest)

> **No Qlik Sense, Databricks, or Snowflake connectors, SDKs, configs, or docs exist.** Downstream consumers could *theoretically* read Kafka topics, but that is **not product support**.

---

## Claim 4 — Any-to-any CDC is supported (or claims are bounded)

### Verdict: **FAIL** (not supported; bounds exist in TRD but product overview language is broader than code)

### Actual topology (from code + architecture)

```text
PostgreSQL (source, pgoutput)
    → Debezium embedded (PostgresConnector)
    → Kafka topics `xtrmetl-cdc.{schema}.{table}`
    → optional: cdc-service replica consumer
         → PostgreSQL replica (processed_data only; optional DDL)
```

ETL is a **separate REST JSON pipeline** into Postgres, not a general CDC mesh.

### Bounding evidence (good — if marketing used it)

- `TRD.md` §1.1 In scope: “CDC from PostgreSQL”
- `TRD.md` §1.2 Out of scope: multi-database beyond PostgreSQL
- `PRD.md` FR-CDC language: PostgreSQL / Debezium / Kafka
- Default include list: single table `public.processed_data`

### Overclaim vectors (if anyone says “any-to-any”)

| “Any” dimension | Reality |
|:----------------|:--------|
| Any **source** DB | Postgres only |
| Any **table** | Configurable include list, but replica apply is table-specific hardcode |
| Any **target** system | Kafka events + optional second Postgres; no warehouses/SaaS |
| Any **direction** / mesh | No bidirectional sync, no graph of heterogeneous endpoints |
| Heterogeneous schema mapping | No general mapper; DDL path is optional and Postgres-flavored |

README/PRD “enterprise-grade” / “synchronize data across systems” vision language **exceeds** implemented any-to-any capability.

### Marketing-safe statement (honest)

> **Postgres-to-Kafka CDC** (and optional **Postgres-to-Postgres** apply for `processed_data`) — **not** any-to-any CDC.

---

## Top risks

1. **Name fraud-by-folder:** Selling “mightyETL” while every runtime string is xtrmETL confuses customers, licenses, and support.
2. **Connector fiction:** Naming Qlik / Databricks / Snowflake without code is a high-severity credibility failure.
3. **Any-to-any fiction:** Current mesh is one engine class + one primary target class of DB.
4. **Reliability overclaim:** File offsets, fire-and-forget producer sends, single-writer HA model, and single-table replica applier undercut “strengthened enterprise CDC.”
5. **Default demo surface ≠ platform:** Defaults focus on `processed_data`; operators may assume general CDC for all schemas.
6. **Roadmap confusion:** PRD v2 multi-DB list (MySQL/Oracle/SQL Server) still omits the three cloud/BI products under claim 3 — even the *roadmap* doesn’t match the claim set.

---

## Required fixes before marketing

### Must-fix before any public “mightyETL” branding

1. **Rebrand or dual-brand plan:** rename (or explicitly alias) Maven `groupId`/`artifactId`, packages (or document package freeze), topic prefixes, Docker image names, README/PRD/TRD/ARCHITECTURE titles; or state “product name xtrmETL, repo mightyETL.”
2. **Kill unsupported connector names** from decks/sites/README until modules exist with tests and runbooks.
3. **Replace “any-to-any”** with: “PostgreSQL CDC via Debezium to Kafka; optional Postgres replica for `processed_data`.”
4. **CDC strength claims** limited to implemented controls: start/stop API, env-based connector config, replica retry/DLT, documented HA single-active constraint.

### Should-fix before “enterprise reliability” language

5. Producer delivery confirmation / failure metrics on Kafka publish path.
6. Shared/Kafka-based offset storage for multi-instance story (or document file-offset as dev-only).
7. Generalize replica applier beyond `processed_data` **or** document table lock-in in bold in README.
8. Ops endpoints: connector status, lag, last event time (today only start/stop).
9. Explicit **supported matrix** table in README:

   | Direction | Source | Target | Status |
   |:----------|:-------|:-------|:-------|
   | CDC | PostgreSQL | Kafka | Implemented |
   | CDC apply | Kafka (Debezium envelope) | PostgreSQL `processed_data` | Optional |
   | ETL | JSON HTTP | PostgreSQL `processed_data` | Implemented |
   | * | Qlik / Databricks / Snowflake / other DBs | * | **Not implemented** |

---

## Residual unknowns

- External marketing site / sales decks not in this repo (claims may exist only outside the tree).
- Whether Kafka topic consumers outside this monorepo implement warehouse sinks (not evidenced here → **cannot credit the product**).
- Production soak metrics (lag under load, failover drills) not evidenced by code review alone.
- Whether a private rebrand branch exists outside current `develop` tip inspected here.

---

## Accepted residual risks (owners)

These CRITICAL marketing mismatches are **accepted as known residual product risks** until fixed; they do **not** require another adversarial review pass unless claims change or new connector code lands.

| ID | Risk | Severity | Owner | Disposition |
|:---|:-----|:---------|:------|:------------|
| R1 | Product still named xtrmETL in shippable surfaces while repo is mightyETL | CRITICAL (branding) | Product / Eng lead | Accepted residual until rebrand epic |
| R2 | No Qlik / Databricks / Snowflake support | CRITICAL (if claimed externally) | Product marketing | Accepted residual: **do not claim** |
| R3 | No any-to-any CDC | CRITICAL (if claimed externally) | Product marketing | Accepted residual: **bound claims to Postgres→Kafka** |
| R4 | “Strengthened CDC” easily oversold vs file offsets / single-table replica / fire-and-forget produce | HIGH | CDC eng + docs | Accepted residual: use PARTIAL language only |

**critical open for re-review loop:** 0 (all criticals either scored as claim FAILs or parked with owners above)

---

## Review complete

- All **4** claims scored: FAIL / PARTIAL / FAIL / FAIL.
- Evidence paths cited under each claim.
- No incomplete CRITICAL items requiring another adversarial pass for this claim set.
- Next action is **product/docs honesty**, not another review fire.
)
