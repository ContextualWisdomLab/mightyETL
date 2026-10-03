# API and Event Contract

**Protected baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Last reconciled:** 2026-08-09

This file is the canonical HTTP/event compatibility entry point. Detailed feature runbooks remain authoritative for implementation-specific limits, but may not contradict this contract.

## 1. Compatibility policy

- Existing public paths and stable machine-readable error codes are compatibility contracts once released.
- Breaking changes require an ADR, version/migration plan, rollback evidence, and a changelog entry.
- Open-PR behavior is marked `active_pr` and does not change protected-develop compatibility.
- Identifiers are opaque. A UUID-shaped resource ID grants no authority by itself.
- HTTP status/header behavior follows RFC 9110; typed problems follow RFC 9457.
- Optional structured `Idempotency-Key` normalization uses the RFC 9651 String form supported by production code while retaining its documented legacy safe raw representation.

## 2. Synchronous ETL — `implemented_on_develop`

### `POST /api/etl/process`

Request body: bounded UTF-8 JSON array.

Optional header:

```http
Idempotency-Key: "550e8400-e29b-41d4-a716-446655440000"
```

Unkeyed success:

```http
HTTP/1.1 200 OK
Content-Type: text/plain
```

Keyed success additionally returns:

```http
Idempotency-Replayed: false
```

or `true` when an identical committed principal-scoped request is replayed.

Keyed requests require an authenticated principal. The client key/principal are never returned as authority tokens or stored raw in the replay ledger.

### Atomicity

All rows are validated/transformed before target writes. The accepted target writes share one Spring transaction. A failed accepted request must not leave a committed prefix of `processed_data`.

## 3. ETL connector catalog — `implemented_on_develop`

### `GET /api/etl/connectors`

Returns an operator-safe structure containing:

- `product=mightyETL`;
- `primaryLoadPath=postgresql`;
- connector runtime/support metadata;
- documentation pointer.

The response must not expose connector secrets. A `scaffoldOnly`/equivalent support marker is authoritative and must not be rewritten as production support in marketing/docs.

## 4. Durable job intake/status — `implemented_on_develop`, feature-gated

The entire controller is disabled unless durable intake is explicitly enabled.

### `POST /api/etl/jobs`

Requires:

- authenticated principal;
- bounded `Idempotency-Key`;
- bounded JSON-array body satisfying ETL admission rules.

First accepted submission:

```http
HTTP/1.1 202 Accepted
Location: /api/etl/jobs/<opaque-id>
Cache-Control: no-store
Idempotency-Replayed: false
Content-Type: application/json
```

The body contains the opaque job ID, current `PENDING` status, and status URL. `202 Accepted` does not assert execution completion.

Identical committed replay returns the same resource with `Idempotency-Replayed: true`. Different payload/key reuse fails closed using the stable problem taxonomy.

### `GET /api/etl/jobs/{job_record_id}`

The implementation route variable is `jobRecordId`; this document uses descriptive `job_record_id` notation for the opaque resource.

Requirements:

- authenticated principal first;
- owner-scoped selection independent of resource-ID syntax;
- malformed/missing/foreign-owned targets share the owner-safe not-found surface;
- `Cache-Control: no-store`;
- no raw payload, principal, idempotency key/hash, SQL, internal exception, or target secret in the operator representation.

Protected-develop job vocabulary: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`.

## 5. Durable job active-PR API additions

The following remain `active_pr`:

- #144: `GET /api/etl/jobs` owner-scoped keyset pagination;
- #145: `Retry-After` on active status when local worker execution exists;
- #146: weak `ETag` and authenticated `If-None-Match` conditional status;
- #147: `POST /api/etl/jobs/{job_record_id}/cancellation` and `CANCELLED`;
- #148: terminal-job replay endpoint/lineage contract.

Clients must not depend on these endpoints/headers/states from protected develop until their PRs integrate.

## 6. CDC control/status — `implemented_on_develop`

### `POST /api/cdc/start`

Requests idempotent start of the embedded engine. Success text is currently `CDC process started`.

### `POST /api/cdc/stop`

Requests engine close. `known_gap`: protected develop returns success after `CdcService.stop()` clears references, not after it proves the asynchronous engine Future returned. Issue #141 owns the planned contract repair.

### `GET /api/cdc/status`

Returns operator-safe status including runtime/autostart, source metadata, replica configuration, replication-slot probe, configured/registered sources, and registered targets. No secret fields are a supported response contract.

### `GET /api/cdc/sources`

Returns registered CDC source descriptors and support state.

### `GET /api/cdc/targets`

Returns registered CDC target descriptors and `scaffoldOnly` state.

## 7. CDC event contract — `implemented_on_develop`

Live publication uses the raw Debezium JSON key/value and Debezium destination topic for compatibility. Optional canonical mapping is observational on protected develop.

Delivery semantics are replay-tolerant/at-least-once; protected develop does not claim that broker acknowledgement precedes Debezium progress. PR #139 is the `active_pr` acknowledged-delivery boundary.

## 8. Problem Details

`EtlApiProblemHandler` owns stable RFC 9457 response shaping for covered ETL failures. A problem response may include public classification fields such as status, type, title, detail, `instance`, and `errorCode`, but not:

- SQL or database exception text;
- Java exception class/message as client detail;
- credentials or bearer tokens;
- raw principal;
- raw idempotency/cancellation key or stored hash;
- request payload;
- lease identity;
- connector secret/target credentials.

The protected implementation sets RFC 9457 `instance` to the request URI through `ProblemDetail.setInstance(...)`; it does not expose a separate public `path` alias.

## 9. Authentication reality

Protected develop's gateway `JwtAuthenticationFilter` is a placeholder that recognizes literal `valid_token`; it is a `known_gap` and not a production JWT contract. Historical `POST /auth/signin` and `POST /auth/signup` designs are `superseded`, not implemented interfaces. PR #142 is the `active_pr` Resource Server JWT replacement.

## 10. Versioning rules

- A representation can gain optional fields/headers only when older clients can ignore them safely or the API is explicitly versioned.
- A new lifecycle state requires persistence, reader, API, polling/cache, rollback, and old-binary compatibility evidence.
- An event change requires producer/consumer compatibility evidence and a canary/rollback plan.
- Breaking connector SPI changes require adapters or a major-version boundary.

## 11. References

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP Semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/info/rfc9110

Nottingham, M., & Kamp, P.-H. (2024). *Structured Field Values for HTTP* (RFC 9651). RFC Editor. https://www.rfc-editor.org/info/rfc9651

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem Details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/info/rfc9457
