# Config Server repository authority doctoring

**Capability status:** `active_pr` via repair of replacement PR #322; not `implemented_on_develop` until protected integration.  
**Protected baseline assessed:** `develop@e8373b7193019e72b7a860c9f14d109fe7963ee7`  
**Component:** Spring Cloud Config 5.0.4, managed through the repository's Spring Cloud 2025.0.3 release train

## Decision boundary

mightyETL's independently runnable Config Server Git backend must have explicit deployment-owned repository authority. The deployable property `spring.cloud.config.server.git.uri` uses only `${CONFIG_REPO_URI}` and has no example, demo, guessed, or implicit remote fallback. Missing, blank, unresolved, or demo repository authority must fail closed at default-profile startup, before remote Git access.

This is a narrow destination-authority decision, not a claim that Config Server is part of the default supported topology. The protected Compose profile does not establish Config Server as a required production dependency. A later product decision may promote, constrain, or retire the module, but none of those outcomes requires retaining a fake repository fallback.

Operators: export `CONFIG_REPO_URI` to the reviewed Git URI, then start the default profile. Use the `native` profile only for local fixtures that must not depend on a remote. Do not start the default profile with a blank secret.

## Root cause and rejected alternatives

The prior configuration used a syntactically valid default resembling `https://github.com/your-repo/config-repo.git`. That deferred a missing deployment decision into an outbound network attempt while providing no real ownership, provenance, credential, availability, change-control, or recovery contract.

Removing the demo default alone is not fail-closed. On Spring Cloud Config 5.0.4, `JGitEnvironmentRepository.afterPropertiesSet` accepts a non-null empty URI when `cloneOnStart` is false. Local evidence on `50ddcc0` showed both unset and blank `CONFIG_REPO_URI` starting Tomcat and exposing `/actuator` before any Git work. Spring also left `${CONFIG_REPO_URI}` unresolved instead of failing Environment bootstrap, so a YAML token change cannot be the only control.

Rejected alternatives:

- invent a ContextualWisdomLab repository URL, private endpoint, username, password, token, SSH key, certificate, or trust root;
- retain the demo URI because operators can override it later;
- embed credentials in `CONFIG_REPO_URI` or repository source;
- enable `skipSslValidation` to make an untrusted repository reachable;
- discover repository authority through mutable remote bootstrap code;
- treat repository credentials as proof that a destination is authorized;
- treat a YAML `String.contains` test as live fail-closed evidence.

The selected remedy keeps the explicit `${CONFIG_REPO_URI}` token and registers `ConfigServerRepositoryAuthorityEnvironmentPostProcessor` after config-data load. That processor rejects blank, Unicode-padded blank, unresolved `${CONFIG_REPO_URI...}`, request-templated `{application}` / `{profile}` / `{label}` locations, and the retired `github.com/your-repo/config-repo` destination (any case, with or without `.git`, HTTPS or `git@`) before JGit `afterPropertiesSet`. `ConfigServerRepositoryAuthorityValidator` remains as defense in depth on every non-`native` profile. Combining `native` with `prod` or `production` fails unless `xtrmetl.config.allow-native=true`. Config Server HTTP authentication, Git credentials, trust material, retry and timeout policy, readiness, repository support status, and service-to-service identity remain separate controls.

## Spring Cloud Config 5.0.4 contract

Spring Cloud Config documents `spring.cloud.config.server.git.uri` as the Git environment-repository location. Local filesystem repositories can be useful for deliberately configured development or testing, while production repository location and access remain deployment decisions. mightyETL externalizes this authority through `CONFIG_REPO_URI` rather than assigning a product-owned demo default.

`cloneOnStart` can surface an invalid repository during startup instead of first request. This repair still does not enable it, because fail-early cloning changes startup and network-availability semantics beyond destination authority. Blank-authority rejection is a local configuration check and does not need a clone. If Config Server becomes a supported profile, fail-early cloning must be evaluated together with bounded connect/fetch timeouts, readiness, retry, recovery, and SLO policy.

TLS certificate validation remains enabled. The documented `skipSslValidation` escape hatch must not become a convenience default. Private Git services needing custom roots require explicit trust-material provenance and rotation rather than global certificate-verification bypass.

Git credentials are deployment secrets, not repository authority. They must be externalized through supported secret-management controls, be least-privileged to the approved repository, and stay out of source, committed URLs, ordinary logs, metrics, traces, pull-request text, and generated documentation.

## Failure, privacy, and operability semantics

A missing or blank `CONFIG_REPO_URI` on the default profile is a deployment-configuration failure. The process must stop with a finite message that names `CONFIG_REPO_URI`. That failure is safer and more actionable than contacting an invented external repository or serving misleading configuration from an empty URI.

The `native` profile remains independently startable without `CONFIG_REPO_URI` so inbound-security fixtures and local filesystem backends keep working. Do not use `native` to bypass destination authority in a composed production topology. A `native,prod` or `native,production` mix is a configuration failure unless an operator sets `xtrmetl.config.allow-native=true` for an approved fixture.

Repository URLs and provider exceptions can expose internal hostnames, usernames, paths, query parameters, or credentials. Ordinary observability should retain finite configuration and fetch outcome classifications rather than raw credential-bearing URLs or unrestricted exception text. This is purpose-bound diagnostic minimization, not blanket PII masking.

Rollback must never restore the demo remote. When an approved repository is unavailable, the safe choices are to disable or not deploy Config Server, restore the approved repository service, or supply another explicitly authorized repository through a reviewed deployment change. A deliberate local-development `file:` URI belongs in an explicit operator-supplied `CONFIG_REPO_URI` or the `native` profile, not in a production fallback.

## Standalone and modular MSA implications

The Config Server module remains independently buildable. ETL, CDC, gateway, and discovery services must not silently become dependent on it merely because the module exists. A composed MSA profile that adopts Config Server needs explicit dependency and failure-domain documentation, inbound authentication, repository authority, trust and credential provenance, readiness, rollback, and tests proving promised standalone modes remain available.

No central orchestrator, gateway, or sibling service acquires authority to rewrite Config Server destination configuration at runtime without a versioned, authenticated deployment contract.

## Evidence and replacement lineage

`ConfigServerRepositoryAuthorityLiveTest` starts `ConfigServerApplication` on the default servlet profile. Blank and retired-demo URIs with `clone-on-start=true` must fail with the authority message, which is the evidence that the processor ran before JGit clone. Unset `CONFIG_REPO_URI` must fail with that message or Spring's unresolved-placeholder failure. `native,prod` must fail with the native/production message. `ConfigServerRepositoryAuthorityEnvironmentPostProcessorTest` covers blank, demo, trim, native skip, and native+prod opt-in. `ConfigServerRepositoryAuthorityTest` covers null, ASCII blank, NBSP/ZWSP, `${CONFIG_REPO_URI:}` defaults, case and no-`.git` demo variants, `git@` demo, request templates, padded `https`, `ssh`, `file:`, and a non-demo nested path. `ConfigServerRepositoryConfigurationTest` pins the exact YAML token with no default colon. Old PR #189 / #322 / #327 must not merge separately after this unique work is accepted.

Canonical PRD, TRD, Architecture, UML, Security, Threat Model, Operability, and Traceability must represent this capability as `active_pr` until protected integration. The documentation must not infer that Config Server became a shipped default component merely because repository authority was hardened.

## References (APA 7)

Spring Boot. (2026). *Externalized configuration*. https://docs.spring.io/spring-boot/reference/features/external-config.html

Spring Cloud Config. (2026). *Config Server (Spring Cloud Config 5.0.4)*. https://docs.spring.io/spring-cloud-config/reference/server.html

Spring Cloud Config. (2026). *Git backend (Spring Cloud Config 5.0.4)*. https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/git-backend.html

Spring Boot. (2026). *EnvironmentPostProcessor*. https://docs.spring.io/spring-boot/api/java/org/springframework/boot/env/EnvironmentPostProcessor.html

Spring Boot. (2026). *SpringApplication*. https://docs.spring.io/spring-boot/reference/features/spring-application.html

Spring Cloud Config. (2026). *Security (Spring Cloud Config 5.0.4)*. https://docs.spring.io/spring-cloud-config/reference/server/security.html
