# Qlik Sense connector (scaffold)

## Intent

Push or expose change data for Qlik Sense (SaaS or client-managed) so apps can reload against an up-to-date source.

## Status

| Item | State |
|:-----|:------|
| Product claim | **Not supported in runtime** |
| Docs + SPI stub | Yes — `QlikSenseTargetConnector` |
| Qlik REST / qrs API client | No |
| Direct QVD writer | No |

## Planned integration shapes (choose later)

1. **Indirect (recommended first):** mightyETL lands data in Postgres/Snowflake/Databricks; Qlik connects to that warehouse. Connector only triggers **reload** via Qlik API.
2. **Direct:** REST bulk load into a Qlik data connection (product-specific; higher complexity).

## Config keys (proposed)

| Key | Description |
|:----|:------------|
| `tenant-url` | Qlik Cloud tenant or Sense enterprise base URL |
| `api-key` / OAuth client | Auth (**secret**) |
| `app-id` | App to reload |
| `mode` | `reload-only` \| `push` (push not designed yet) |

## Limitations (honest)

- No Qlik SDK dependency, no reload automation, no multi-tenant isolation.
- Prefer documenting Qlik → Postgres/Kafka as the supported path until the SPI is implemented.
