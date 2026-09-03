# Config Server repository authority doctoring

**Capability status:** `active_pr` via replacement PR #322; not `implemented_on_develop` until protected integration.  
**Protected baseline assessed:** `develop@d6c6665163eabe1b5eca80556c6963bafd6b2625`  
**Component:** Spring Cloud Config 5.0.4, managed through the repository's Spring Cloud 2025.0.3 release train

## Decision boundary

mightyETL's independently runnable Config Server Git backend must have explicit deployment-owned repository authority. The deployable property `spring.cloud.config.server.git.uri` therefore uses only `${CONFIG_REPO_URI}` and has no example, demo, guessed, or implicit remote fallback. Missing repository authority must fail closed before remote Git access instead of silently selecting a destination that the operator did not approve.

This is a narrow destination-authority decision, not a claim that Config Server is part of the default supported topology. The protected Compose profile does not establish Config Server as a required production dependency. A later product decision may promote, constrain, or retire the module, but none of those outcomes requires retaining a fake repository fallback.

## Root cause and rejected alternatives

The prior configuration used a syntactically valid default resembling `https://github.com/your-repo/config-repo.git`. That deferred a missing deployment decision into an outbound network attempt while providing no real ownership, provenance, credential, availability, change-control, or recovery contract.

Rejected alternatives:

- invent a ContextualWisdomLab repository URL, private endpoint, username, password, token, SSH key, certificate, or trust root;
- retain the demo URI because operators can override it later;
- embed credentials in `CONFIG_REPO_URI` or repository source;
- enable `skipSslValidation` to make an untrusted repository reachable;
- discover repository authority through mutable remote bootstrap code;
- treat repository credentials as proof that a destination is authorized.

The selected remedy changes only repository selection. Config Server HTTP authentication, Git credentials, trust material, retry and timeout policy, readiness, repository support status, and service-to-service identity remain separate controls.

## Spring Cloud Config 5.0.4 contract

Spring Cloud Config documents `spring.cloud.config.server.git.uri` as the Git environment-repository location. Local filesystem repositories can be useful for deliberately configured development or testing, while production repository location and access remain deployment decisions. mightyETL externalizes this authority through `CONFIG_REPO_URI` rather than assigning a product-owned demo default.

`cloneOnStart` can surface an invalid repository during startup instead of first request. Replacement PR #322 deliberately does not enable it because that would change startup and network-availability semantics beyond the demonstrated fake-fallback defect. If Config Server becomes a supported profile, fail-early cloning must be evaluated together with bounded connect/fetch timeouts, readiness, retry, recovery, and SLO policy.

TLS certificate validation remains enabled. The documented `skipSslValidation` escape hatch must not become a convenience default. Private Git services needing custom roots require explicit trust-material provenance and rotation rather than global certificate-verification bypass.

Git credentials are deployment secrets, not repository authority. They must be externalized through supported secret-management controls, be least-privileged to the approved repository, and stay out of source, committed URLs, ordinary logs, metrics, traces, pull-request text, and generated documentation.

## Failure, privacy, and operability semantics

With no fallback, a missing `CONFIG_REPO_URI` is a deployment-configuration failure. That failure is safer and more actionable than contacting an invented external repository and possibly serving misleading configuration.

Repository URLs and provider exceptions can expose internal hostnames, usernames, paths, query parameters, or credentials. Ordinary observability should retain finite configuration and fetch outcome classifications rather than raw credential-bearing URLs or unrestricted exception text. This is purpose-bound diagnostic minimization, not blanket PII masking.

Rollback must never restore the demo remote. When an approved repository is unavailable, the safe choices are to disable or not deploy Config Server, restore the approved repository service, or supply another explicitly authorized repository through a reviewed deployment change. A deliberate local-development `file:` URI belongs in an explicit profile, not in a production fallback.

## Standalone and modular MSA implications

The Config Server module remains independently buildable. ETL, CDC, gateway, and discovery services must not silently become dependent on it merely because the module exists. A composed MSA profile that adopts Config Server needs explicit dependency and failure-domain documentation, inbound authentication, repository authority, trust and credential provenance, readiness, rollback, and tests proving promised standalone modes remain available.

No central orchestrator, gateway, or sibling service acquires authority to rewrite Config Server destination configuration at runtime without a versioned, authenticated deployment contract.

## Evidence and replacement lineage

`ConfigServerRepositoryAuthorityLiveTest` on replacement PR #322 establishes current-base RED against the real deployable YAML. `ConfigServerRepositoryConfigurationTest` preserves the earlier behavior and doctoring contracts from PR #189. The replacement branch carries both tests plus the one-line production correction and this source-backed evidence; old PR #189 must not merge separately after exact unique-work preservation and current-head acceptance are proven.

Canonical PRD, TRD, Architecture, UML, Security, Threat Model, Operability, and Traceability must represent this capability as `active_pr` until protected integration. The documentation must not infer that Config Server became a shipped default component merely because repository authority was hardened.

## References (APA 7)

Spring Cloud Config. (2026). *Git backend (Spring Cloud Config 5.0.4)*. https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/git-backend.html

Spring Cloud Config. (2026). *Security (Spring Cloud Config 5.0.4)*. https://docs.spring.io/spring-cloud-config/reference/server/security.html

Spring Cloud Config. (2026). *Config Server (Spring Cloud Config 5.0.4)*. https://docs.spring.io/spring-cloud-config/reference/server.html
