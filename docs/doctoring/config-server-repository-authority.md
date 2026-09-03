# Config Server repository authority doctoring

**Capability status:** `active_pr` via #189; not `implemented_on_develop` until protected integration.  
**Protected baseline assessed:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Component:** Spring Cloud Config Server 5.0.4

## Decision boundary

mightyETL's Config Server Git backend must have an explicit deployment-owned repository authority. The production property `spring.cloud.config.server.git.uri` therefore uses only `${CONFIG_REPO_URI}` and has no example, demo, or implicit remote fallback. Missing repository authority must fail closed rather than silently selecting a network destination that the operator did not approve.

This is a narrow configuration-authority decision, not a claim that Config Server is part of the default supported deployment topology. Protected `docker-compose.yml` does not currently start Config Server, and the canonical architecture/documentation lane must separately decide whether Config Server is production-supported, reference-only, or retired. Either terminal product decision is compatible with removing the fake repository fallback.

## Root cause and rejected alternatives

The protected baseline used a default resembling `https://github.com/your-repo/config-repo.git`. It was syntactically valid enough to defer configuration failure into remote Git access, while conveying no real ownership, provenance, credential, availability, or change-control contract.

Rejected alternatives:

- invent a ContextualWisdomLab repository URL, private endpoint, token, username, password, SSH key, or certificate without a deployment-owned decision;
- keep a demo URI because operators can override it later;
- embed credentials in `CONFIG_REPO_URI` or repository source;
- disable transport verification through `skipSslValidation` merely to make an unknown repository reachable;
- use a mutable network bootstrap to discover repository authority at runtime.

The selected remedy changes only repository selection. Authentication and authorization for the Config Server HTTP API, Git-backend credentials, trust material, repository availability policy, and deployment support status remain separate controls.

## Spring Cloud Config 5.0.4 contract

Spring Cloud Config 5.0.4 documents `spring.cloud.config.server.git.uri` as the Git environment repository location used by Config Server. Local filesystem repositories are useful for testing; production repository hosting and access are deployment decisions. mightyETL therefore keeps the repository location externalized through `CONFIG_REPO_URI` instead of assigning a project-owned demo default.

`cloneOnStart` is a supported option that can surface an invalid repository during Config Server startup rather than first use. #189 deliberately does not enable it because doing so changes startup/network availability semantics beyond the demonstrated fake-fallback defect. If Config Server becomes a supported production profile, that fail-early behavior should be evaluated together with repository availability, retry/timeout, readiness, recovery, and SLO policy.

TLS certificate validation remains enabled. The documented `skipSslValidation` escape hatch must not be enabled as a convenience default. A private Git service requiring custom trust roots needs an explicit trust-material/provenance design instead of globally accepting an untrusted certificate.

Git credentials are deployment secrets, not repository authority. They must be supplied through supported external configuration or secret-management controls, be least-privileged to the intended repository, and stay out of source, URLs committed to Git, ordinary logs, metrics, traces, PR text, and generated documentation.

## Failure, privacy, and operability semantics

With no fallback, a missing `CONFIG_REPO_URI` is a deployment-configuration failure. That behavior is preferable to contacting an invented external repository because it preserves operator intent and makes the missing authority observable before any misleading configuration content can be served.

Repository URLs and credentials can expose internal hostnames, usernames, access paths, or other infrastructure context. Normal observability should record finite configuration/fetch outcome classes rather than raw credential-bearing URLs or exception messages. This is purpose-bound diagnostic minimization, not blanket PII masking.

Rollback must not restore the demo network fallback. If a deployment cannot supply an approved repository, the safe rollback is to disable/not deploy Config Server or supply an explicitly approved repository under the prior compatible application version. A future supported local-development profile may deliberately use a `file:` URI, but that must be an explicit profile rather than the production fallback.

## Standalone and modular MSA implications

The Config Server module remains independently buildable. Other mightyETL services must not silently become dependent on it merely because the module exists. If a composed MSA profile adopts Config Server later, that integration requires explicit dependency/failure-domain documentation, HTTP authentication, Git repository authority, readiness behavior, secret provenance, recovery/rollback, and tests proving that standalone ETL/CDC operation remains available where promised.

## Evidence and follow-through

`ConfigServerRepositoryConfigurationTest` binds this decision to the deployable YAML and to this doctoring artifact. The test rejects the historical demo Git location and any HTTPS default embedded in `CONFIG_REPO_URI`.

Canonical PRD/TRD/Architecture/UML/Security/Threat Model/Operability/Traceability material is separately owned by #149/#159 while that writer lane is active. The next safe reconciliation must record #179/#189 as `active_pr` until protected integration and must not infer that Config Server became a shipped/default component merely because its repository authority was hardened.

## References (APA 7)

Spring Cloud Config. (2026). *Git backend (Spring Cloud Config 5.0.4)*. https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/git-backend.html

Spring Cloud Config. (2026). *Security (Spring Cloud Config 5.0.4)*. https://docs.spring.io/spring-cloud-config/reference/server/security.html

Spring Cloud Config. (2026). *Config Server (Spring Cloud Config 5.0.4)*. https://docs.spring.io/spring-cloud-config/reference/server.html
