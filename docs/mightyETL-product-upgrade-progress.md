# mightyETL product upgrade progress

**Workspace:** `/home/seonghobae/mightyETL`  
**Branch preference:** `develop`  
**Last fire:** 2026-07-31 (fire 6 — README support matrix + program complete)  
**Policy:** No commit/push unless a human asks. No secrets.

## Goals checklist

| # | Goal | Status | Notes |
|:-:|:-----|:------:|:------|
| G1 | Rename product branding/docs/UI strings xtrmETL → mightyETL | **partial** (accepted residual) | User-facing docs, README matrix, dual-read `mightyetl.*`, POM `<name>`, status APIs say mightyETL. **Out of scope residual:** Java packages, Maven `artifactId`, Compose DB defaults `xtrmetl`. |
| G2 | Strengthen cdc-service | **partial** (accepted residual) | Status/sources/targets APIs, slot lag probe, replica table allow-list, ops docs, `cdcEngine` health, SPI start/stop, DLT/retry (pre-existing). **Out of scope residual:** arbitrary-schema replica, multi-process HA controller. |
| G3 | Qlik / Databricks / Snowflake | **partial** (accepted residual) | Docs + SPI + config keys + catalog API + write guard. **Out of scope residual:** real SaaS clients (need credentials/drivers). |
| G4 | Any-to-any CDC | **partial** (accepted residual) | Source/target SPI, factory, YAML sources, canonical mapper counters, mysql/sqlserver scaffolds. Live path **Postgres→Kafka** only (`anyToAny: false`). **Out of scope residual:** multi-engine start, canonical Kafka cutover. |

## Files touched this fire

- `README.md` — honest **Supported today** matrix + links
- `docker-compose.yml` — mightyETL header; document legacy DB default names
- `CHANGELOG.md` — note
- This progress file — **Complete** section finalized

## Remaining work → accepted out-of-scope

All leftovers are either breaking epics or external SaaS. None are small production-safe slices under the original four goals.

| Item | Goal | Acceptance |
|:-----|:-----|:-----------|
| Maven `artifactId` / `groupId` / Java package rename | G1 | Dedicated binary-breaking epic |
| Compose/volume rename `xtrmetl` → `mightyetl` | G1 | Operator churn; override via `.env` today |
| Arbitrary column-map JDBC replica | G2 | Needs product design beyond `(id,data)` |
| Multi-source Debezium engines in one process | G4 | Connector JARs + isolation design |
| Canonical Kafka payload default | G4 | Breaking for current consumers |
| Production Databricks/Snowflake/Qlik loaders | G3 | External accounts, secrets, cost controls |

## Blockers / accepted limitations

| Item | Limitation | Acceptance |
|:-----|:-----------|:-----------|
| Full package/Maven coordinate rename | Wide blast radius | **Accepted** |
| True multi-DB capture runtime | Only Postgres Debezium live | **Accepted** — scaffolds + honesty matrix |
| SaaS warehouse loaders | No cloud credentials in CI | **Accepted** — SPI/catalog; writes refused |
| Full multi-source start | Process/slot isolation | **Accepted** — factory validates types only |
| Canonical event bus cutover | Consumer compatibility | **Accepted** — optional map counters only |
| Agent JDK 21 vs project 25 | Local compile may fail | Re-run tests on Java 25 |

## Complete

**Program status for scheduler exit: COMPLETE under exit rule (2).**

1. G1–G4 are **not** fully implemented as marketing “done” claims — they remain honest **partials**.
2. **Every remaining item** is listed above under **Remaining work → accepted out-of-scope** and **Blockers / accepted limitations**.
3. **No high-value in-repo slice** remains for the original four goals without starting an epic or requiring external SaaS.

In-repo deliverables landed across fires:

- Branding: docs, README matrix, dual-read config, POM name, API product fields  
- CDC: status/sources/targets, slot lag, health, replica tables, SPI lifecycle, ops docs  
- Connectors: Qlik/Databricks/Snowflake SPI + config + catalog + guards  
- Any-to-any: SPI, factory, scaffolds, mapper counters; live path still PG→Kafka  

**Marketing-safe summary:** mightyETL is a Postgres-centric CDC/ETL platform with honest scaffolds for warehouses and multi-source CDC — not an any-to-any SaaS integration product yet.

## Verification

| Check | Result |
|:------|:-------|
| Maven tests | Prefer `./mvnw -B -pl cdc-service,etl-service -am test` on **Java 25** |
| This fire | Docs/compose only — Maven not required |
