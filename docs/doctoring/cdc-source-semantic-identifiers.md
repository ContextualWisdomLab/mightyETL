# CDC source semantic identifier doctoring

## Decision

The CDC configuration and connector-discovery bounded context owns source identity, target identity, and connector-type vocabulary. Internal Java identifiers therefore use explicit terms such as `sourceId`, `targetId`, and `sourceType` rather than generic one-word `id` and `type` names. Java casing remains idiomatic camelCase; the decision is about semantic specificity, not forcing snake_case into Java.

`CdcSourceFactory.SourceSpec` is the authoritative internal value object for one declared source. Its record components are `sourceId`, `sourceType`, and `enabled`. Factory parameters and local variables use source-specific terms (`sourceRegistry`, `sourceSpecs`, `sourceDescription`, `sourceConnector`) so ownership remains visible at the call site.

The connector SPIs now expose semantic organization-owned accessors as well. `CdcSourceConnector.sourceId()` is the internal source-identity vocabulary and `CdcTargetConnector.targetId()` is the internal target-identity vocabulary. Built-in connectors publish semantic constants (`SOURCE_ID` or `TARGET_ID`), registries index by semantic identifiers, controller code reads the semantic accessors, and internal tests use the same language.

## Compatibility boundary

Existing operators already configure `xtrmetl.cdc.sources[*].id` and `xtrmetl.cdc.sources[*].type`, and the CDC status resources already emit source/target objects containing `id`. This repair treats those historical keys as anti-corruption/compatibility boundaries rather than silently changing wire/config contracts.

`XtrmetlProperties.Source` stores values internally as `sourceId` and `sourceType`. The historical `getId`/`setId` and `getType`/`setType` JavaBean accessors remain only so Spring can continue binding the established configuration keys. Repository-owned production callers use `getSourceId`/`getSourceType`. Focused binder coverage proves that legacy `id`/`type` configuration still populates the semantic internal fields.

`CdcSourceFactory.describeConfigured` likewise keeps the established output keys `id` and `type`, while all internal record access is through `sourceId()` and `sourceType()`. `CdcController` keeps the established response key `id`, but obtains its value from `sourceId()` or `targetId()` internally. The compatibility regression explicitly fails if implementation member names leak into the existing HTTP payload.

The CDC connector SPIs can also be consumed outside the repository. Their historical generic `id()` accessor therefore remains deprecated as a compatibility seam while organization-owned callers use `sourceId()`/`targetId()`. Built-in connectors retain deprecated `ID` constants as aliases to `SOURCE_ID`/`TARGET_ID`; repository-owned callers use the semantic constants. This keeps old caller source behavior available without making the generic vocabulary authoritative inside the bounded context.

## DDD traceability

- **Bounded context:** Change Data Capture source configuration, source discovery, and target discovery.
- **Ubiquitous language:** CDC source, source identifier, source connector type, source registry, CDC target, target identifier, target registry, configured source, registered source, registered target.
- **Value object:** `CdcSourceFactory.SourceSpec` represents one immutable source declaration.
- **Domain services:** `CdcSourceFactory`, `CdcSourceRegistry`, and `CdcTargetRegistry` validate and resolve semantic connector identities.
- **Invariant:** two configured source declarations cannot share the same `sourceId`.
- **Invariant:** registered source connectors cannot share a `sourceId`; registered target connectors cannot share a `targetId`.
- **Invariant:** `sourceId` and `sourceType` must be nonblank before a declaration enters the configured-source description pipeline.
- **Compatibility invariant:** established operator configuration and HTTP keys remain `id`/`type`, and the deprecated SPI aliases return the same identity as the semantic accessors, until a separately versioned breaking contract intentionally replaces them.
- **Persistence:** no database row, column, index, migration, Kafka record, or Debezium offset changes in this repair.

## Verification contract

Focused tests require semantic record and connector accessors, duplicate source/target rejection, repeated connector types with distinct source identities, unchanged legacy HTTP keys, semantic Java defaults/constants, compatibility alias equality, and Spring binding of the historical `id`/`type` keys into the new internal fields. Existing validation exception text is preserved so a naming-only refactor does not create an unrelated behavioral contract change. The full Maven/CI, dependency review, SBOM, SAST, and Security Scan gates remain authoritative on the unchanged resulting head.

The regression-only heads were intentionally superseded quickly by ordinary non-force implementation commits. The connector-identity RED commit introduced calls to `sourceId()`/`targetId()` before production support landed; successor commits implement the contract while retaining explicit compatibility adapters. No cancelled, predecessor, base-only, or model-only run is counted as merge evidence.

## Research basis

The naming rule is intentionally semantic rather than mechanical. Schankin et al. found that descriptive compound identifiers helped experienced Java developers locate semantic defects faster than shorter, less descriptive names. Feitelson et al. model naming as selecting the concepts a name should communicate, choosing words for those concepts, and constructing the identifier; explicitly applying that model produced names judged superior to unconstrained choices. These findings support encoding the owned concepts (`source` + `id`, `target` + `id`, `source` + `type`) while retaining the host language's normal casing and allowing concise names where context itself already supplies the meaning.

### References

Feitelson, D. G., Mizrahi, A., Noy, N., Ben Shabat, A., Eliyahu, O., & Sheffer, R. (2022). How developers choose names. *IEEE Transactions on Software Engineering, 48*(1), 37–52. https://doi.org/10.1109/TSE.2020.2976920

Schankin, A., Berger, A., Holt, D. V., Hofmeister, J. C., Riedel, T., & Beigl, M. (2018). Descriptive compound identifier names improve source code comprehension. In *Proceedings of the 26th Conference on Program Comprehension* (pp. 31–40). Association for Computing Machinery. https://doi.org/10.1145/3196321.3196332
