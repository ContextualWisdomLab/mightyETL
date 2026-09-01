# CDC source semantic identifier doctoring

## Decision

The CDC source configuration bounded context owns source identity and connector-type vocabulary. Internal Java identifiers therefore use the explicit terms `sourceId` and `sourceType` rather than the generic one-word `id` and `type` names. The Java casing remains idiomatic camelCase; the decision is about semantic specificity, not forcing snake_case into Java.

`CdcSourceFactory.SourceSpec` is the authoritative internal value object for one declared source. Its record components are `sourceId`, `sourceType`, and `enabled`. Factory parameters and local variables use source-specific terms (`sourceRegistry`, `sourceSpecs`, `sourceDescription`, `sourceConnector`) so ownership remains visible at the call site.

## Compatibility boundary

Existing operators already configure `xtrmetl.cdc.sources[*].id` and `xtrmetl.cdc.sources[*].type`, and the CDC status resource already emits configured-source objects containing `id` and `type`. This change therefore treats those historical keys as an anti-corruption/compatibility boundary rather than silently changing the wire/config contract.

`XtrmetlProperties.Source` stores values internally as `sourceId` and `sourceType`. The historical `getId`/`setId` and `getType`/`setType` JavaBean accessors remain only so Spring can continue binding the established configuration keys. Repository-owned production callers use `getSourceId`/`getSourceType`. Focused binder coverage proves that legacy `id`/`type` configuration still populates the semantic internal fields.

`CdcSourceFactory.describeConfigured` likewise keeps the established output keys `id` and `type`, while all internal record access is through `sourceId()` and `sourceType()`. The compatibility regression explicitly fails if the implementation starts leaking new Java member names into the existing HTTP payload.

## DDD traceability

- **Bounded context:** Change Data Capture source configuration and discovery.
- **Ubiquitous language:** CDC source, source identifier, source connector type, source registry, configured source, registered source.
- **Value object:** `CdcSourceFactory.SourceSpec` represents one immutable source declaration.
- **Domain service:** `CdcSourceFactory` validates source identity uniqueness and resolves source connector types.
- **Invariant:** two configured source declarations cannot share the same `sourceId`.
- **Invariant:** `sourceId` and `sourceType` must be nonblank before a declaration enters the configured-source description pipeline.
- **Compatibility invariant:** established operator configuration and HTTP keys remain `id`/`type` until a separately versioned breaking contract intentionally replaces them.
- **Persistence:** no database row, column, index, migration, Kafka record, or Debezium offset changes in this repair.

## Verification contract

Focused tests require semantic record accessors, duplicate-source rejection, repeated connector types with distinct source identities, unchanged legacy HTTP keys, semantic Java defaults, and Spring binding of the historical `id`/`type` keys into the new internal fields. The full Maven/CI, dependency review, SBOM, SAST, and Security Scan gates remain authoritative on the unchanged resulting head.

The regression-only head was intentionally superseded quickly by ordinary non-force implementation commits, so its hosted jobs were cancelled rather than claimed as RED evidence. The regression itself is retained in history and remains executable on the final branch; no cancelled or predecessor run is counted as merge evidence.

## Research basis

The naming rule is intentionally semantic rather than mechanical. Schankin et al. found that descriptive compound identifiers helped experienced Java developers locate semantic defects faster than shorter, less descriptive names. Feitelson et al. model naming as selecting the concepts a name should communicate, choosing words for those concepts, and constructing the identifier; explicitly applying that model produced names judged superior to unconstrained choices. These findings support encoding the owned concepts (`source` + `id`, `source` + `type`) while retaining the host language's normal casing and allowing concise names where context itself already supplies the meaning.

### References

Feitelson, D. G., Mizrahi, A., Noy, N., Ben Shabat, A., Eliyahu, O., & Sheffer, R. (2022). How developers choose names. *IEEE Transactions on Software Engineering, 48*(1), 37–52. https://doi.org/10.1109/TSE.2020.2976920

Schankin, A., Berger, A., Holt, D. V., Hofmeister, J. C., Riedel, T., & Beigl, M. (2018). Descriptive compound identifier names improve source code comprehension. In *Proceedings of the 26th Conference on Program Comprehension* (pp. 31–40). Association for Computing Machinery. https://doi.org/10.1145/3196321.3196332
