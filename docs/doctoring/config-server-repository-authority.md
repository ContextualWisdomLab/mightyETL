# Config Server repository authority doctoring

**Capability status:** `active_pr` via replacement PR #327; not `implemented_on_develop` until protected integration.  
**Protected baseline assessed:** `develop@e8373b7193019e72b7a860c9f14d109fe7963ee7`  
**Component:** Spring Cloud Config 5.0.4, managed through the repository's Spring Cloud 2025.0.3 release train

## Decision boundary

mightyETL's independently runnable Config Server Git backend must have explicit deployment-owned repository authority. The deployable property `spring.cloud.config.server.git.uri` uses only `${CONFIG_REPO_URI}` and has no example, demo, guessed, or implicit remote fallback. Missing, blank, unresolved, or retired demo repository authority must fail closed during environment preparation, before Spring Cloud Config creates Git repository beans or `cloneOnStart` can contact a remote.

This is a narrow destination-authority decision, not a claim that Config Server is part of the default supported topology. The protected Compose profile does not establish Config Server as a required production dependency. A later product decision may promote, constrain, or retire the module, but none of those outcomes requires retaining a fake repository fallback.

Operators: export `CONFIG_REPO_URI` to the reviewed Git URI, then start the default profile. Use the `native` profile only as the sole active profile for reviewed local fixtures that must not depend on a remote. Do not start the default profile with an unset or blank `CONFIG_REPO_URI`. Git credentials are separate deployment secrets and must not be embedded in this URI.

## Root cause and rejected alternatives

The prior configuration used a syntactically valid default resembling `https://github.com/your-repo/config-repo.git`. That deferred a missing deployment decision into an outbound network attempt while providing no real ownership, provenance, credential, availability, change-control, or recovery contract.

Removing the demo default alone is not fail-closed. Local evidence showed that Spring Cloud Config can defer Git work when `cloneOnStart` is false, while a bean-level validator is not an ordering guarantee against JGit initialization when `cloneOnStart` is true. A YAML token change and an `InitializingBean` therefore cannot be the only controls.

Rejected alternatives:

- invent a ContextualWisdomLab repository URL, private endpoint, username, password, token, SSH key, certificate, or trust root;
- retain the demo URI because operators can override it later;
- embed credentials in `CONFIG_REPO_URI` or repository source;
- enable `skipSslValidation` to make an untrusted repository reachable;
- discover repository authority through mutable remote bootstrap code;
- treat repository credentials as proof that a destination is authorized;
- treat a YAML `String.contains` test as live fail-closed evidence;
- use `native` together with another active profile to bypass Git destination authority.

The selected remedy keeps the exact `${CONFIG_REPO_URI}` token and registers `ConfigServerRepositoryAuthorityEnvironmentPostProcessor` through `META-INF/spring.factories`. It runs immediately after Spring Boot's `ConfigDataEnvironmentPostProcessor`, when ordinary config data has been loaded but before the application context creates Spring Cloud Config Git beans. It rejects blank or unresolved values, the retired `github.com/your-repo/config-repo[.git]` destination case-insensitively, and mixed `native` profile compositions. `ConfigServerRepositoryAuthorityValidator` remains defense in depth for non-native bean initialization.

## Spring Boot and Spring Cloud Config contract

Spring Boot 3.5 documents `EnvironmentPostProcessor` as the extension point for changing or validating the prepared `Environment` before the application context is refreshed, with registration through `META-INF/spring.factories`. `ConfigDataEnvironmentPostProcessor.ORDER` is public, so the repository-authority guard can run at `ORDER + 1`, after normal config data has been applied and before context refresh.

Spring Cloud Config documents `spring.cloud.config.server.git.uri` as the Git environment-repository location. A deliberately configured `file:` repository can be appropriate for development or testing, while production repository location and access remain deployment decisions. mightyETL externalizes this authority through `CONFIG_REPO_URI` rather than assigning a product-owned demo default.

`cloneOnStart` can cause Git access during startup. The authority guard therefore runs before Config Server Git beans rather than relying on a bean whose relative initialization order is not a security boundary. The repair does not itself enable cloning or redefine retry, timeout, readiness, recovery, or SLO policy.

TLS certificate validation remains enabled. The documented `skipSslValidation` escape hatch must not become a convenience default. Private Git services needing custom roots require explicit trust-material provenance and rotation rather than global certificate-verification bypass.

Git credentials are deployment secrets, not repository authority. They must be externalized through supported secret-management controls, be least-privileged to the approved repository, and stay out of source, committed URLs, ordinary logs, metrics, traces, pull-request text, and generated documentation.

## Failure, privacy, and operability semantics

A missing or blank `CONFIG_REPO_URI` on a non-native profile is a deployment-configuration failure. The process must stop with a finite message that names `CONFIG_REPO_URI`. The retired demo GitHub path must also stop locally even when `cloneOnStart=true`, so a configuration defect cannot become an outbound Git request.

Standalone `native` remains independently startable without `CONFIG_REPO_URI` for reviewed local filesystem fixtures. If `native` is combined with any other active profile, startup fails closed with a finite profile-composition message. Do not use `native` to bypass destination authority in a composed production topology.

Repository URLs and provider exceptions can expose internal hostnames, usernames, paths, query parameters, or credentials. Ordinary observability should retain finite configuration and fetch outcome classifications rather than raw credential-bearing URLs or unrestricted exception text.

Rollback must never restore the demo remote. When an approved repository is unavailable, the safe choices are to disable or not deploy Config Server, restore the approved repository service, or supply another explicitly authorized repository through a reviewed deployment change.

## Evidence and replacement lineage

`ConfigServerRepositoryAuthorityLiveTest` isolates inherited `CONFIG_REPO_URI`, asserts finite authority failure for unset and blank values, exercises `clone-on-start=true` against the retired demo URI, and rejects mixed `native` profile startup. `ConfigServerRepositoryAuthorityTest` covers null, whitespace, unresolved placeholder, exact retired GitHub destinations with case and `.git` variants, non-matching paths, and explicit `https` / `file:` values. `ConfigServerRepositoryConfigurationTest` requires the exact YAML token with no default colon and the registered early environment guard.

Canonical PRD, TRD, Architecture, Security, Operability, and Traceability must represent this capability as `active_pr` until protected integration. The documentation must not infer that Config Server became a shipped default component merely because repository authority was hardened.

## References (APA 7)

Spring Boot. (n.d.). *Externalized configuration*. In *Spring Boot reference documentation*. Retrieved August 28, 2026, from https://docs.spring.io/spring-boot/reference/features/external-config.html

Spring Boot. (n.d.). *EnvironmentPostProcessor*. In *Spring Boot 3.5 API*. Retrieved August 28, 2026, from https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/env/EnvironmentPostProcessor.html

Spring Boot. (n.d.). *ConfigDataEnvironmentPostProcessor*. In *Spring Boot 3.5 API*. Retrieved August 28, 2026, from https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/context/config/ConfigDataEnvironmentPostProcessor.html

Spring Cloud Config. (n.d.). *Git backend*. Retrieved August 28, 2026, from https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/git-backend.html

Spring Cloud Config. (n.d.). *Security*. Retrieved August 28, 2026, from https://docs.spring.io/spring-cloud-config/reference/server/security.html

Spring Cloud Config. (2026, June 11). *Spring Cloud Config 5.0.4* [Software release]. GitHub. https://github.com/spring-cloud/spring-cloud-config/releases/tag/v5.0.4
