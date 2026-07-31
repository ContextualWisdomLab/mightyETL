# mightyETL rebrand name matrix

**Product name:** mightyETL  
**Former name:** xtrmETL  
**Status:** User-facing docs/UI strings use mightyETL. Runtime identifiers migrate gradually.

## Policy

| Layer | Policy |
|:------|:-------|
| Docs, README, PRD, marketing copy | **mightyETL** |
| Suggested Docker image org | `mightyetl/...` |
| Java package (`com.xtrmetl.*`) | **Frozen** until a coordinated module rename epic |
| Maven `groupId` / `artifactId` | **Frozen** (`com.xtrmetl` / `xtrmETL`) for binary/repo stability |
| Spring config prefix `xtrmetl.*` | **Dual-read** with `mightyetl.*` (modern preferred) via `MightyEtlConfigAliasEnvironmentPostProcessor` |
| Kafka topic prefix default `xtrmetl-cdc` | Prefer env override; default kept for existing deployments |
| Debezium connector name / offset dir | Prefer env override; defaults may still say `xtrmetl` |
| Docker Compose DB user/db defaults | `xtrmetl` (dev defaults; override via `.env`) |
| Historical design notes filename | Keep `xtrmETL-common-initial-design-notes.txt` as archive |

## Dual-read config (implemented)

Operators may set either prefix for known keys (see `MightyEtlConfigAliasEnvironmentPostProcessor` in
`cdc-service` / `etl-service`):

| Prefer | Also accepted |
|:-------|:--------------|
| `mightyetl.cdc.autostart` | `xtrmetl.cdc.autostart` |
| `mightyetl.replica.enabled` | `xtrmetl.replica.enabled` |
| `mightyetl.connectors.databricks.enabled` | `xtrmetl.connectors.databricks.enabled` |
| …other keys listed in the post-processor | same under `xtrmetl.*` |

**Rule:** if both are set, **`mightyetl.*` wins** and is mirrored onto `xtrmetl.*` for binding.

## Migration checklist (future)

- [x] Dual-read config: accept `mightyetl.*` with fallback to `xtrmetl.*` (known keys)
- [ ] Topic prefix default → `mightyetl-cdc` behind a feature flag / major version
- [ ] Package rename or explicit public API module `com.mightyetl.*`
- [ ] Maven coordinates rename with BOM redirect or new major version
- [ ] Compose/image defaults → `mightyetl`
- [ ] Changelog entry for each breaking identifier change

## Honest statement

> **mightyETL** is the product name. Docs and dual-read config use mightyETL naming; Java packages and Maven coordinates still use legacy **xtrmetl / xtrmETL** identifiers until a dedicated rename epic.
