# Durable-job replay-key domain separation

## Decision

Replay-created jobs store one versioned principal-scoped replay identity in
`submission_key_hash`:

```text
SHA-256(
  "mightyetl:durable-job-replay:v1:"
  || principal_scope_hash
  || ":"
  || normalized_replay_key
)
```

The same identity feeds a separately versioned transaction-lock input. Replay identity is isolated
from ordinary submission-key hashing and from another authenticated principal. It proves only
idempotent admission; every source lookup and created-job query independently binds the owner hash.

## Threat addressed

Using the ordinary raw-key digest would allow one client key to collide across the submit and replay
APIs. It would also make a replay key's stored equality directly comparable between principal
namespaces. The versioned operation domain and owner hash prevent those cross-protocol and
cross-tenant equality channels.

Within one principal namespace, the same replay key intentionally identifies only one replay intent.
An existing row with another immediate source or request digest returns
`etl_job_replay_key_reused` rather than creating a second row.

## Compatibility boundary

These exact strings are persisted behavior:

```text
mightyetl:durable-job-replay:v1:
mightyetl:durable-job-replay-lock:v1:
```

Changing the replay domain would make existing requests stop replaying their created jobs. Changing
the lock domain could let old and new binaries concurrently use different locks for the same
identity. Either change requires an explicit migration and mixed-version deployment analysis.

The implementation does not claim cSHAKE, KMAC, or TupleHash conformance. It uses the existing
SHA-256 utility with fixed-width principal hash, explicit separators, and one final bounded key.
NIST SP 800-185 is methodological evidence for customization and domain separation, not an
implementation-conformance claim.

## Test evidence

Service integration requires:

- quoted and legacy-raw representations of one key replay the same created job;
- one replay key with another source or payload fails closed;
- an ordinary submission key cannot accidentally identify a replay-created row because the replay
  domain changes the digest input;
- an unavailable transaction lock returns `etl_job_replay_in_progress` before table access.

The table's `etl_job_submission_scope_unique` constraint remains a second integrity boundary after
the transaction lock.

## Privacy

Raw replay keys and replay hashes are excluded from HTTP bodies, headers, RFC 9457 problems, ordinary
logs, metrics, status and list resources, lineage exports, and worker leases. The stored hash remains
pseudonymous internal security data and must not be published merely because it is one-way.

## Rollback

Keep both versioned derivations available while replay-created rows can receive retries or while old
and new binaries overlap. Do not derive candidate hashes from logged user input during diagnosis.
Never rewrite a replay-created `submission_key_hash` without preserving unique-constraint and exact
idempotency evidence.

## References — APA 7th

Kelsey, J., Chang, S., & Perlner, R. (2016). *SHA-3 derived functions: cSHAKE, KMAC, TupleHash, and
ParallelHash* (NIST Special Publication 800-185). National Institute of Standards and Technology.
https://doi.org/10.6028/NIST.SP.800-185

National Institute of Standards and Technology. (2025, March 12). *Decision to update FIPS 202 and
revise SP 800-185*. https://csrc.nist.gov/news/2025/decision-to-update-fips-202-and-revise-sp-800-185
