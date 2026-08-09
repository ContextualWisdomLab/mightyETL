# Durable-job cancellation replay identity domain separation

## Decision

mightyETL never stores a raw cancellation `Idempotency-Key`. It stores one lowercase SHA-256 replay identity computed from an explicit versioned domain, the authenticated-principal hash, the durable job identifier, and the normalized key:

```text
SHA-256(
  "mightyetl:durable-job-cancellation:v1:"
  || principal_scope_hash
  || ":"
  || job_record_id
  || ":"
  || normalized_cancellation_key
)
```

This value is used only to prove that a later request addresses the same principal, the same job, and the same semantic cancellation key. It is not an authentication credential and grants no job authority; every transition and replay read independently binds the owner hash and job identifier in SQL.

## Threat addressed

Hashing the raw client key alone would hide its plaintext but preserve equality across every row. A database observer could correlate two jobs or tenants that reused the same cancellation key even though ordinary API representations never reveal the key or hash.

The versioned contextual prefix and explicit principal/job components partition the replay identity. The same normalized raw key therefore produces:

- the same stored hash for the same principal and job, preserving deterministic replay;
- a different stored hash for another job in the same principal namespace;
- a different stored hash for the same job-shaped identifier under another principal namespace;
- a different stored hash after a deliberate future domain-version change.

The implementation does not claim to use cSHAKE, TupleHash, or another NIST SP 800-185 primitive. It uses the existing SHA-256 utility with an unambiguous fixed-layout contextual input. NIST SP 800-185 is cited as primary methodological evidence for customization and tuple/domain separation concepts, not as an implementation-conformance claim. NIST announced in March 2025 that SP 800-185 will be revised; until a replacement is finalized, this document cites the current final publication and the revision decision separately.

## Compatibility boundary

The exact domain string is persisted protocol behavior:

```text
mightyetl:durable-job-cancellation:v1:
```

Changing it would make every existing cancelled row fail same-key replay comparison. A future `v2` requires an explicit migration and dual-read compatibility window or a documented replay-breaking release. Silent replacement of the prefix is prohibited.

The current concatenation is unambiguous because:

- `principal_scope_hash` is exactly 64 lowercase hexadecimal characters;
- the separator is a literal colon;
- `job_record_id` is the canonical UUID text form;
- the second separator is a literal colon;
- the normalized cancellation key follows the bounded safe-ASCII profile and is the final component.

If a later version introduces variable-width or independently nested components, use explicit length prefixes or a tuple-hash construction rather than extending this layout informally.

## Test-first evidence

`EtlJobCancellationKeyDomainIntegrationTest` uses the same raw cancellation key for:

1. two different jobs owned by one principal;
2. one job owned by another principal.

The test requires three distinct 64-character stored hashes. Existing service-integration tests separately prove that a quoted and legacy-raw representation of the same key on the same job replay one cancellation, while a genuinely different key fails with `etl_job_cancellation_key_reused`.

## Privacy and logging

The raw cancellation key and resulting hash remain absent from:

- HTTP response bodies and headers;
- RFC 9457 problem details;
- ordinary logs;
- metric labels;
- status, list, polling, and ETag representations;
- worker lease models.

The hash is a replay identity stored in `cancellation_key_hash`; it is not safe to publish merely because it is one-way. Database access, backups, exports, and support tooling must treat it as internal pseudonymous security data.

## Rollback

Rolling application code back across this change can make same-key replay behavior inconsistent if an older binary derives a raw-key-only hash. Keep the domain-separated implementation deployed while rows created by it are active. A rollback requires either:

- retaining the new comparison algorithm in the older release line; or
- a reviewed data migration with explicit compatibility evidence.

Never rewrite hashes from user-supplied guesses and never log candidate keys while diagnosing replay mismatches.

## References — APA 7th

Kelsey, J., Chang, S., & Perlner, R. (2016). *SHA-3 derived functions: cSHAKE, KMAC, TupleHash, and ParallelHash* (NIST Special Publication 800-185). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-185

National Institute of Standards and Technology. (2025, March 12). *Decision to update FIPS 202 and revise SP 800-185*. https://csrc.nist.gov/news/2025/decision-to-update-fips-202-and-revise-sp-800-185
