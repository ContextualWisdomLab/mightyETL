# Gateway JWT authentication boundary

Reviewed on: **2026-08-09**

## Purpose

mightyETL's reactive gateway now has an explicit authentication boundary instead of treating an example bearer string as proof of identity. `/etl/**` and `/cdc/**` are protected application routes. `/actuator/health`, its health subpaths, and `/actuator/info` are the only intentionally public gateway paths in this slice. Every other path is denied.

This control authenticates requests at the gateway. It does not create an authorization server, issue tokens, store refresh tokens, or replace service-level authorization. Downstream services should continue to apply their own authentication and authorization where the deployment threat model requires defense in depth.

## Modes and secure default

`mightyetl.gateway.security.mode` has two accepted values:

- `deny` — the default. Application routes are denied even when a caller supplies credentials. This lets the gateway start independently without inventing an issuer, JWK URL, public key, client secret, or other identity-provider configuration.
- `jwt` — enables Spring Security's reactive OAuth 2.0 Resource Server JWT processing. Set `MIGHTYETL_GATEWAY_SECURITY_MODE=jwt` only together with a trusted decoder configuration.

Any other mode is a startup/configuration error rather than a permissive fallback.

The checked-in configuration sets only the fail-closed mode default. It deliberately does **not** contain a sample production issuer or key URL. Identity-provider coordinates are deployment configuration and must come from the environment or another approved configuration source.

## Supported Spring Security JWT properties

Use Spring Boot's supported resource-server properties rather than a new mightyETL secret contract:

- `spring.security.oauth2.resourceserver.jwt.issuer-uri` identifies the trusted issuer and enables issuer validation.
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` can be supplied with the issuer when deployments need an explicit JWK endpoint rather than metadata-derived key discovery.
- `spring.security.oauth2.resourceserver.jwt.audiences` enables resource-audience validation for deployments whose access-token contract carries `aud`.
- `spring.security.oauth2.resourceserver.jwt.jws-algorithms` constrains acceptable signing algorithms. Spring Security's Nimbus reactive decoder trusts RS256 by default; configure a different or additional set only as an explicit identity-provider contract.
- Spring Boot's public-key configuration can be used when a deployment deliberately chooses static trusted key material instead of JWK rotation.

Do not add a GitHub Actions secret merely because one of these properties is absent. The deployment identity-provider contract must first prove which trust material is required and where it is owned.

## Validation and failure behavior

JWT mode delegates JOSE parsing and cryptographic verification to Spring Security's maintained resource-server/Jose stack. The supported decoder validates the signature and standard time window and, when configured from an issuer, validates `iss`. Audience validation is enabled with the Boot `audiences` property. The accepted algorithm set is application policy; it must never be inferred from an attacker-controlled token header.

Protected-route requests with missing, malformed, unverifiable, expired, materially premature, wrong-issuer, wrong-audience, disallowed-algorithm, unknown-key, or otherwise invalid bearer credentials must fail closed before routing. Unsecured `alg=none` tokens are not an accepted deployment mode. Do not configure a decoder from a token-supplied `jku` or other attacker-selected key URL.

Spring Security maps the authenticated principal from the validated JWT. A production token contract must provide a non-blank `sub` as a stable non-sensitive subject identifier and minimize claims to what downstream authorization actually needs. PII masking is not used as an authentication workaround: minimize token claims, restrict access, protect transport and stored configuration, and keep token contents out of logs and telemetry.

## Authorization header and downstream trust

The gateway security chain reads the incoming `Authorization` bearer credential. The route configuration in this slice does not add a `RemoveRequestHeader=Authorization`, `SetRequestHeader=Authorization`, or token-relay transformation. Spring Cloud Gateway's WebFlux hop-by-hop filter removes protocol hop-by-hop headers; `Authorization` is not in that default removal list. Consequently the bearer header remains available to downstream services under the current routing configuration.

That behavior is intentional for defense-in-depth migration: downstream ETL/CDC services can independently validate the same credential until a separately reviewed trust-boundary change replaces it with another authenticated service identity. Do not strip or replace the header without tests and an ADR defining the new downstream principal-propagation contract.

## Availability, metadata discovery, and key rotation

With issuer-based reactive resource-server configuration, current Spring Security can defer metadata/JWK discovery until a bearer token is first processed, avoiding an unconditional identity-provider dependency during gateway startup. When a trusted JWK Set is used, Spring Security supports key rotation as the authorization server publishes new keys.

An identity-provider or JWK outage is not permission to accept an unverifiable token. Existing decoder/cache behavior may continue to validate with already trusted material according to framework semantics, but a key that cannot be established as trusted must fail authentication. Alert on repeated decoder/JWK refresh failures and elevated authentication-failure rates without attaching token, subject, issuer-controlled free text, or other unbounded/PII dimensions to metric labels.

## Logging and observability

Never log the complete `Authorization` header, bearer token, JOSE signature, raw JWT claims, or stable subject as ordinary request telemetry. Security metrics should use finite-cardinality dimensions such as route family and bounded failure class (`missing`, `malformed`, `signature`, `issuer`, `audience`, `time`, `key`, `other`). Token-level diagnostics belong only in access-controlled incident tooling with explicit retention rules.

Production deployments should avoid HTTP-framework trace logging that can expose request metadata. Changes to request/response logging must include a regression proving credential material is excluded.

## Rollout

1. Deploy the code with the default `deny` mode and verify public health endpoints only.
2. Configure a real trusted issuer and, where required, explicit JWK Set URI, audiences, and accepted algorithms through the deployment configuration plane.
3. Set `MIGHTYETL_GATEWAY_SECURITY_MODE=jwt`.
4. Verify a cryptographically valid token for the configured issuer/resource reaches the intended route and invalid variants fail before upstream routing.
5. Verify downstream services receive only the intended credential/context and continue their own authorization controls.
6. Observe JWK refresh and authentication-failure telemetry during rollout; do not use a permissive fallback during an identity-provider incident.

## rollback

If JWT configuration is incorrect or the identity-provider contract must be withdrawn, set the mode back to `deny`. This is a security-preserving rollback: ETL/CDC traffic stops instead of falling through unauthenticated. Reverting to the historical literal example token or enabling an undocumented downstream-only bypass is not an acceptable rollback.

Code rollback requires reverting the resource-server dependency, security configuration, tests, documentation, and changelog together only after a reviewed replacement provides an equal or stronger fail-closed boundary.

## Acquisition, SOC 2, and CSAP evidence

This implementation provides reviewable evidence of a default-deny ingress policy, explicit authentication-mode change control, maintained cryptographic middleware, configuration provenance, least-privilege public paths, and deterministic failure behavior. These are useful implementation artifacts for security, availability, confidentiality, and processing-integrity control evidence. Source code alone does not establish SOC 2 or CSAP certification; production IAM ownership, TLS termination, configuration approvals, key-management evidence, audit retention, incident response, and operating effectiveness remain deployment controls.

## Threat model

The boundary specifically considers token forgery, algorithm confusion, issuer/audience confusion, cross-JWT substitution, stale or rotated signing keys, metadata/JWK endpoint outage, clock skew, malformed JOSE input, unknown key identifiers, bearer-token leakage, and accidentally broad public-route matchers. Sender-constrained tokens such as DPoP or mTLS are outside this bounded slice and require a separate deployment threat-model decision.

## References

Jones, M., Bradley, J., & Sakimura, N. (2015). *JSON Web Token (JWT)* (RFC 7519). RFC Editor. https://doi.org/10.17487/RFC7519

Sheffer, Y., Hardt, D., & Jones, M. (2020). *JSON Web Token best current practices* (BCP 225, RFC 8725). RFC Editor. https://doi.org/10.17487/RFC8725

Jones, M., Sakimura, N., & Bradley, J. (2018). *OAuth 2.0 authorization server metadata* (RFC 8414). RFC Editor. https://doi.org/10.17487/RFC8414

Spring Security. (2026). *OAuth 2.0 Resource Server JWT*. https://docs.spring.io/spring-security/reference/6.5/reactive/oauth2/resource-server/jwt.html

Spring Cloud. (2026). *Spring Cloud Gateway: HTTP headers filters*. https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/httpheadersfilters.html
