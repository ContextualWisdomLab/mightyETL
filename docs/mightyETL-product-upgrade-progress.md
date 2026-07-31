# mightyETL product upgrade progress

**Workspace:** `/home/seonghobae/mightyETL`  
**Branch preference:** `develop`  
**Last fire:** 2026-07-31 (warehouse/BI connector surfaces)  
**Policy:** Product deltas disposed via PR merge. No secrets.

## Goals checklist

| # | Goal | Status | Notes |
|:-:|:-----|:------:|:------|
| G1 | Rename product branding/docs/UI strings xtrmETL → mightyETL | **partial** (accepted residual) | User-facing docs, README matrix, dual-read `mightyetl.*`, POM `<name>`, status APIs say mightyETL. **Out of scope residual:** Java packages, Maven `artifactId`, Compose DB defaults `xtrmetl`. |
| G2 | Strengthen cdc-service / PostgreSQL primary | **done** (accepted residual for HA) | PG→Kafka capture, status/sources/targets, slot lag, replica allow-list, ops docs, health. Residual: multi-process HA. |
| G3 | Qlik / Databricks / Snowflake | **partial** (accepted residual) | SPI + YAML binding + required-key validation + catalog API fields + write guards + unit tests on shipped classes. **Residual:** live SaaS clients (credentials). |
| G4 | Any-to-any CDC | **partial** (accepted residual) | Source/target SPI; live path **Postgres→Kafka** only. |

## This fire (warehouse BI surfaces)

- `TargetConnector`: `requiredConfigKeys`, `optionalConfigKeys`, `writeRefusalReason`, `describeIntegration`
- Per-connector required keys (Databricks / Snowflake / Qlik Sense)
- `ConnectorProperties` full binding surface + `configMap()`
- `TargetConnectorDispatcher`: validate bound config before scaffold write refusal; catalog enrichment
- `application.yml` env-backed keys (secrets via env only)
- `WarehouseBiConnectorSurfaceTest` + dispatcher/registry/controller updates
- Honest docs: `docs/connectors/*`, README matrix

## Remaining work → accepted out-of-scope

| Item | Goal | Acceptance |
|:-----|:-----|:-----------|
| Maven / Java package rename | G1 | Dedicated binary-breaking epic |
| Compose/volume rename `xtrmetl` → `mightyetl` | G1 | Operator churn; `.env` override |
| Production Databricks/Snowflake/Qlik loaders | G3 | External accounts, secrets, cost controls |
| Multi-source Debezium engines | G4 | Connector JARs + isolation design |

## Verification

| Check | Result |
|:------|:-------|
| `./mvnw -B -pl cdc-service,etl-service -am test` (Temurin 25 docker) | **BUILD SUCCESS** (cdc Tests run: 99, Failures: 0) |
| Claims | Scaffold language only; write refused |
| open_prs | 0 after disposition |

## Marketing-safe summary

mightyETL is a Postgres-centric CDC/ETL platform with honest warehouse/BI connector **contracts** (config + validation + catalog) — not production SaaS warehouse loaders until credentials and live clients ship.
