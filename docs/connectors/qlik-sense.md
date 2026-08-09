# Qlik Sense integration (planned)

## Intent

Integrate mightyETL with Qlik Sense / Qlik Cloud only through a product boundary that matches Qlik's actual APIs and operating model. Until that boundary is implemented and verified, Qlik is not published as a mightyETL target connector.

## Status

| Item | State |
|:-----|:------|
| Product claim | **Not supported in runtime** |
| Production target registry | **Not registered** |
| Catalog API (`GET /api/etl/connectors`) | **Not advertised** |
| Reference SPI class | `QlikSenseTargetConnector` remains as non-registered design/reference code |
| Config binding (`xtrmetl.connectors.qlik-sense.*`) | Historical/reference surface only; not a production capability claim |
| Qlik reload API client | **No** |
| Qlik data-file upload/import client | **No** |
| Direct QVD writer | **No** |
| Live row-write path | **No** |

## Product boundary

The previous scaffold modeled Qlik as a `TargetConnector.write(...)` row sink even though it never implemented a Qlik client and always refused writes. That shape is no longer advertised through the production registry.

Current Qlik Cloud APIs expose product-specific operations such as application reload orchestration and data-file upload/import. A future mightyETL integration should therefore be designed explicitly around one of those supported boundaries instead of pretending that Qlik is a transactional warehouse row sink.

## Planned integration shapes

1. **Warehouse + reload orchestration:** mightyETL lands data in a supported warehouse or database, then a separately designed Qlik integration triggers and observes an application reload.
2. **Data-file workflow:** mightyETL produces a governed file artifact and uses Qlik's supported data-file APIs before a reload, with explicit idempotency, authentication, tenancy, retry, audit, and cleanup contracts.

Neither path is implemented by the current `TargetConnector` SPI. A future implementation requires a separate PRD/TRD/API contract and realistic integration tests before production registration.

## Historical configuration surface

The reference class still documents the historical keys `tenant-url`, `api-key`, `app-id`, and optional `mode`. Those keys do not grant production support while the connector is absent from `TargetConnectorRegistry`.

## Limitations

- No Qlik API client or SDK integration is shipped.
- No reload automation, file upload/import workflow, QVD writer, or tenant-aware Qlik execution exists.
- Qlik must remain absent from production connector discovery until an actual supported integration path has been implemented and verified.
