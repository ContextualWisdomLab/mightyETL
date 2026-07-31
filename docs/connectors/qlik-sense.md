# Qlik Sense connector (scaffold)

## Intent

Push or expose change data for Qlik Sense (SaaS or client-managed) so apps can reload against an up-to-date source.

## Status

| Item | State |
|:-----|:------|
| Product claim | **Not supported in runtime** (`ConnectorStatus.SCAFFOLD`) |
| SPI class | `QlikSenseTargetConnector` |
| Config binding (`xtrmetl.connectors.qlik-sense.*`) | Yes |
| Required-key validation | Yes (tenant-url, api-key, app-id) |
| Catalog API fields | Yes (`GET /api/etl/connectors`) |
| Qlik REST / qrs API client | **No** |
| Direct QVD writer | **No** |
| Live write path | **Refused** (`UnsupportedOperationException`) |

## Planned integration shapes (choose later)

1. **Indirect (recommended first):** mightyETL lands data in Postgres/Snowflake/Databricks; Qlik connects to that warehouse. Connector only triggers **reload** via Qlik API.
2. **Direct:** REST bulk load into a Qlik data connection (product-specific; higher complexity).

## Config keys

| Key | Required | Description |
|:----|:--------:|:------------|
| `tenant-url` | yes | Qlik Cloud tenant or Sense enterprise base URL |
| `api-key` | yes | Auth (**secret**) |
| `app-id` | yes | App to reload |
| `mode` | no | `reload-only` \| `push` (push not designed yet) |

## Limitations (honest)

- No Qlik SDK dependency, no reload automation, no multi-tenant isolation.
- Prefer documenting Qlik → Postgres/Kafka as the supported path until a live client ships.
