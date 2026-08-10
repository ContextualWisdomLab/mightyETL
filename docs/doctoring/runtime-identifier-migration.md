# Runtime identifier migration and compatibility doctoring

**Capability status:** `active_pr` via #191; the breaking rename remains `planned`, not `implemented_on_develop`.  
**Protected baseline assessed:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Public product name:** `mightyETL`

## Decision

The product name is mightyETL, but several runtime and integration identifiers still carry the historical xtrmETL identity. Those identifiers are compatibility and state surfaces, not cosmetic strings. The protected Maven reactor still identifies the root artifact as `com.xtrmetl:xtrmETL`; Java binary names remain under `com.xtrmetl`; configuration supports the modern `mightyetl.` namespace while retaining legacy `xtrmetl.` aliases; and Kafka/Debezium defaults still include the stateful `xtrmetl-cdc` prefix.

No breaking rename is implemented by #191. The repository first establishes a machine-checkable consumer-facing inventory and prevents the legacy identity from spreading into additional runtime authority surfaces. Actual renames must be staged as a major version change or another explicitly reviewed compatibility boundary with consumer inventory, mixed-version tests, state migration, rollback, and forward recovery.

## Why blind replacement is rejected

A global `xtrmetl -> mightyetl` replacement would conflate several incompatible domains:

- Maven `groupId` and `artifactId` participate in dependency resolution and published artifact identity;
- Java package names are part of class binary names, imports, reflection strings, serialized type references, framework scanning, service provider metadata, and external compiled-client compatibility;
- Spring property prefixes and environment aliases are deployment configuration contracts;
- Kafka topic names, Debezium server/topic prefixes, consumer groups, offset storage, and schema history can carry durable stream-processing state;
- Compose service/database defaults can be embedded in operator DSNs, volumes, scripts, dashboards, runbooks, and external monitoring;
- historical package paths and archived compatibility evidence must remain readable even after the product name changes.

Changing all of these in one commit would make rollback ambiguous because code, dependency coordinates, deployment configuration, and stateful messaging might move at different rates.

## Maven coordinate migration

Apache Maven documents relocation metadata for a project that moves to new coordinates. Relocation can direct Maven consumers from old coordinates to new ones, but it is a repository/dependency-resolution mechanism, not a Java package migration and not a substitute for publishing compatible artifacts.

If mightyETL later changes `com.xtrmetl:xtrmETL` or child-module coordinates, the implementation must:

1. inventory every public/private Maven consumer, CI job, container build, SBOM/provenance rule, dependency lock, plugin, and deployment script using the old coordinates;
2. select new coordinates under an explicit owner-controlled namespace;
3. decide whether an old-coordinate relocation POM, compatibility release, or dual-published transition is operationally required;
4. test dependency resolution from clean repositories rather than relying on a warm local cache;
5. keep version/CHANGELOG/release artifacts and provenance bound to the same exact source head;
6. publish rollback/forward-recovery rules for consumers that cannot upgrade atomically.

A relocation artifact must not imply that historical Java binary names were also migrated.

## Java binary-name migration

Java Language Specification Chapter 13 defines binary compatibility in terms that make type/package identity materially significant. Moving `com.xtrmetl.*` classes to a new package changes their binary names. Existing compiled consumers cannot be assumed to continue linking merely because source code is otherwise identical.

A future Java package migration therefore requires a major version staged plan. Before mutation, perform a consumer inventory covering direct Java clients, Spring component scans, reflection/config strings, serialization, generated code, tests, documentation, examples, and any external modules that import the existing packages. Where compatibility classes or forwarding types are technically appropriate, they must be deliberate, bounded, documented, and removed on an explicit lifecycle rather than duplicated indefinitely.

No source-level package migration may be described as backward compatible without compiled-client evidence. Mixed-version tests must include at least one old compiled consumer against the candidate compatibility artifact when binary compatibility is claimed.

## Configuration namespace migration

The preferred configuration namespace is `mightyetl.`. The legacy `xtrmetl.` namespace remains a compatibility alias on protected develop. This dual-read boundary must be explicit:

- modern keys are the documentation/default choice;
- legacy keys are compatibility inputs, not the brand to propagate into new features;
- when both modern and legacy values are supplied, precedence must be deterministic and tested;
- deprecation telemetry must remain finite-cardinality and must not log secret/property values;
- removal of legacy aliases is a breaking configuration change and belongs to the staged migration, not an opportunistic cleanup.

A major version transition should prove old-only, modern-only, and conflicting mixed-version configuration behavior before legacy aliases are removed.

## Kafka and Debezium stateful identifiers

`xtrmetl-cdc` is not merely a display name. Topic/server prefixes can affect Kafka topic identities and Debezium state such as offsets, schema-history records, consumer subscriptions, ACLs, dashboards, alerts, retention rules, and disaster-recovery procedures. Renaming a stateful identifier without a migration can create a second stream, lose continuity, replay data, or make operators believe an old stream is idle while consumers have moved elsewhere.

Before any Kafka/Debezium rename:

1. inventory topics, consumer groups, ACLs, offset/schema-history stores, connectors, downstream consumers, metrics, alerts, retention policies, and replication/replay tools;
2. specify whether the migration is in-place, dual-publish/dual-read, bridge/copy, or stop-the-world;
3. test mixed-version producer/consumer combinations and exact transition ordering;
4. prove offset continuity and replay behavior with realistic Kafka/Debezium evidence;
5. define a rollback cutoff after which returning to the old prefix would duplicate or lose effects;
6. define forward recovery when rollback is unsafe.

No automatic dual-publish behavior should be invented merely to preserve a name transition; duplicate delivery semantics must be explicitly designed and tested.

## Compose and database defaults

Historical `xtrmetl` database/service defaults are stateful deployment contracts when persisted volumes, DSNs, credentials, backup paths, dashboards, or operator scripts depend on them. A new default name affects new installations differently from existing installations.

A migration must distinguish:

- clean installation defaults;
- existing named volumes/databases and credentials;
- environment-variable overrides already used by operators;
- backup/restore and disaster-recovery artifacts;
- external health checks, dashboards, and service discovery;
- rollback when data was already created under the new name.

The migration must never silently create an empty replacement database and call startup success while the old persistent data remains elsewhere.

## Major version staged migration

The planned strategy is `major_version_staged`, not a single rename commit. A production-ready sequence is:

1. **Inventory:** complete repository plus public/private consumer inventory and classify every legacy identifier by compatibility/state semantics.
2. **Containment:** keep the machine-readable #191 allowlist fail-closed so new runtime code cannot introduce additional xtrmETL identity without an explicit review.
3. **Compatibility release:** make modern identifiers canonical in documentation/config where backward-compatible aliases or relocation mechanisms are proven safe; do not change stateful identifiers yet without a state plan.
4. **Mixed-version proof:** run old/new client, service, config, and messaging combinations that reflect actual supported upgrade ordering.
5. **State migration:** migrate Kafka/Debezium, database, deployment, and observability identifiers using an exact rehearsed plan with bounded downtime/replay/duplication semantics.
6. **Major release:** change binary/dependency identifiers only with versioning, CHANGELOG, SBOM/provenance, packaging, migration, and independent review evidence bound to the exact protected release head.
7. **Deprecation removal:** remove compatibility aliases/shims only after consumer evidence and the promised support window permit it.

Release/licensing/provenance prerequisites remain independent. In particular, repository licensing issue #151 must not be bypassed merely to publish a renamed artifact.

## Consumer inventory requirements

A consumer inventory must include more than repository grep. At minimum identify:

- organization repositories importing Maven/Java packages;
- private/external consumers known to the product owner;
- published artifacts and generated SDKs;
- CI/CD workflows, container images, Helm/Compose/manifests and secret/config stores;
- Kafka/Debezium topics, groups, ACLs and monitoring;
- database/volume/backup/restore names;
- service discovery, dashboards, alerts and runbooks;
- automation/review/release tooling that recognizes repository or artifact identifiers.

Unknown private consumers are an explicit uncertainty, not evidence of absence. Destructive migration waits for an owner-approved compatibility decision when external ownership cannot be established automatically.

## Rollback and forward recovery

Rollback is safe only while old and new identifiers still refer to the same authoritative state or while a tested compatibility bridge exists. Once a stateful stream/database migration accepts writes under the new identity, naive rollback can split history or duplicate processing.

Each migration slice must identify its rollback cutoff. Before that cutoff, rollback may restore the previous application/configuration while preserving one authoritative state. After that cutoff, prefer a documented forward recovery that repairs the new state and consumers. Never use `ours/theirs`, history rewriting, silent alias removal, or hidden topic/database recreation to make the migration appear reversible.

## Security and privacy

Identifier migration does not justify blanket PII masking or indiscriminate telemetry. Configuration aliases, topic names, database names, consumer identities, and artifact coordinates can reveal internal topology even when they are not direct PII. Ordinary migration telemetry should use finite classification/status dimensions and avoid credentials, payloads, principals, connection URLs, exception text, and customer-specific unbounded identifiers.

Privileged migration evidence may retain deeper state only under purpose-bound authorization, least privilege, encryption, bounded retention, and audited access.

## Machine-checkable containment

`docs/compatibility/runtime-identifier-inventory.properties` is the executable inventory for #191. `RuntimeIdentifierMigrationPolicyTest` verifies the known product/legacy identifiers and scans runtime authority surfaces so a new `xtrmetl` marker outside reviewed path families fails CI. The inventory intentionally records `migration.status=planned`; it must not be changed to `implemented_on_develop` before an actual protected migration integrates.

Canonical PRD/TRD/Architecture/ADR/UML/ERD/Test Strategy/Operability/Traceability reconciliation remains owned by #149/#159 while that writer lane is active. That canonical graph should index this doctoring rather than duplicate it and keep the breaking rename as `planned` until protected integration.

## References (APA 7)

Apache Maven Project. (2026). *Guide to relocation*. https://maven.apache.org/guides/mini/guide-relocation.html

Oracle. (2026). *The Java Language Specification, Java SE 26 edition: Chapter 13. Binary compatibility*. https://docs.oracle.com/en/java/javase/26/docs/specs/jls/jls-13.html

These references are maintained in APA 7 style and must be revalidated against the supported Maven/Java baseline before a breaking migration is implemented.