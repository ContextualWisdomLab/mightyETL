# ETL service JWT authentication

**Capability status:** `active_pr` on PR #287; not `implemented_on_develop` until protected integration.  
**Protected baseline assessed:** `develop@d6c6665163eabe1b5eca80556c6963bafd6b2625`  
**Security boundary:** direct and gateway-routed access to the independently runnable `etl-service`

## Decision

The historical HTTP Basic mechanism is removed from the ETL service security chain. The repository default is a credential-free, fail-closed `deny` posture: actuator health and information probes remain available, but `/api/**` returns `401 Unauthorized` until a deployment explicitly selects JWT mode and supplies issuer and audience authority.

JWT mode uses Spring Security's maintained OAuth 2.0 Resource Server support. The service validates bearer tokens independently rather than trusting a gateway header as an authenticated downstream principal. Direct access on the published ETL port and access routed through the gateway therefore cross the same service-owned authentication boundary.

## Configuration contract

The mode is selected through the following precedence:

```text
mightyetl.security.mode
→ xtrmetl.security.mode
→ ETL_SECURITY_MODE
→ deny
```

Supported values are:

| Value | Behavior |
|---|---|
| `deny` | Secure default. `/api/**` is unavailable and historical Basic credentials are rejected. |
| `jwt` | OAuth 2.0 Resource Server bearer-token validation is enabled. |

Unknown values fail during security-chain construction. JWT mode additionally requires nonblank deployment-owned values for:

```text
spring.security.oauth2.resourceserver.jwt.issuer-uri
spring.security.oauth2.resourceserver.jwt.audiences
```

Spring Boot environment-variable forms can be used by deployment systems:

```text
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES
```

No issuer, JWK endpoint, audience, client secret, certificate authority, token, or compatibility password is invented or committed by mightyETL. Deployment configuration and secret-management systems own those values.

## Validation and principal contract

Spring Security obtains issuer metadata and signing-key authority from the configured issuer according to its Resource Server JWT support. It validates the token signature and standard time and issuer claims; Spring Boot's configured audience authority prevents a token issued for another resource from being accepted by the ETL service.

The authenticated principal name is the validated JWT subject (`sub`) under Spring Security's default JWT authentication mapping. Existing idempotency and durable-job owner scoping therefore consume a validated service principal rather than an untrusted forwarded username or a Basic-auth account. Changing subject semantics requires a separately versioned identity-migration decision because it can change access to previously scoped records.

The gateway may forward the original bearer token to the ETL service, but gateway validation never substitutes for ETL validation. A future distinct gateway-to-service credential, mTLS profile, or service mesh identity must be introduced through a separate reviewed contract rather than by trusting arbitrary caller headers.

## Failure and degraded behavior

- Missing or blank mode selects `deny`.
- Unknown mode fails closed during startup.
- JWT mode with missing issuer or audience authority fails during startup.
- Missing, malformed, expired, wrong-issuer, wrong-audience, or unverifiable bearer tokens cannot reach ETL controllers.
- HTTP Basic, form login, logout, server-side sessions, and request caching are disabled.
- Health and information probes remain available for orchestration.
- Issuer or JWK unavailability follows Spring Security's maintained decoder and key-cache behavior; deployments must monitor readiness and authentication failures rather than silently falling back to Basic.

Authentication failures must retain bounded classifications without logging bearer tokens, Authorization headers, JWK material, full provider exceptions, credentials, or raw request payloads. This is purpose-bound diagnostic minimization, not blanket masking of business data needed by authorized ETL work.

## Rollout and rollback

1. Provision an approved issuer and ETL-specific audience.
2. Configure and validate JWT mode in a non-production environment.
3. Verify direct-port and gateway-routed calls with valid, invalid, expired, wrong-issuer, and wrong-audience tokens.
4. Verify that the resulting `sub` preserves intended idempotency and durable-job ownership.
5. Remove Basic credentials from clients and secret stores.
6. Enable production JWT mode and monitor bounded authentication outcomes.

Rollback to HTTP Basic is prohibited because it restores the vulnerability that this change removes. If issuer authority is unavailable, the safe rollback is `deny` mode while identity infrastructure is restored or a separately reviewed authentication mechanism is deployed. The service must not silently downgrade authentication to maintain availability.

## Machine-readable API handoff

PR #278 currently describes the protected runtime truth with an OpenAPI HTTP Basic scheme, and Semgrep correctly rejects that weak mechanism. Do not suppress or falsify the contract on the authentication branch. After this runtime change integrates, refresh the machine-readable contract from protected `develop`, replace the Basic scheme with bearer JWT semantics, regenerate SAST evidence, and preserve exact source/review lineage. The SAST finding is resolved only when runtime and contract move together.

## Testing and evidence

The current branch preserves hosted RED evidence showing that valid historical Basic credentials reached a protected endpoint. The GREEN contract exercises:

- default-deny behavior through the real Spring Security filter chain;
- rejection of valid historical Basic credentials;
- rejection of missing and invalid bearer tokens;
- successful JWT subject authentication through the real bearer-token filter;
- fail-closed mode parsing;
- required issuer and audience authority;
- unchanged health/information probe intent;
- complete current-head CI, dependency, SBOM, SAST, security, and review evidence before integration.

## References (APA 7th)

Internet Engineering Task Force. (2012). *The OAuth 2.0 authorization framework: Bearer token usage* (RFC 6750). https://www.rfc-editor.org/rfc/rfc6750

Internet Engineering Task Force. (2015). *JSON Web Token (JWT)* (RFC 7519). https://www.rfc-editor.org/rfc/rfc7519

Internet Engineering Task Force. (2018). *OAuth 2.0 authorization server metadata* (RFC 8414). https://www.rfc-editor.org/rfc/rfc8414

Internet Engineering Task Force. (2021). *JSON Web Token (JWT) profile for OAuth 2.0 access tokens* (RFC 9068). https://www.rfc-editor.org/rfc/rfc9068

Internet Engineering Task Force. (2025). *Best current practice for OAuth 2.0 security* (RFC 9700, BCP 240). https://www.rfc-editor.org/rfc/rfc9700

Spring Security. (2026). *OAuth 2.0 resource server JWT*. https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html

Spring Boot. (2026). *OAuth2 resource server*. https://docs.spring.io/spring-boot/reference/web/spring-security.html#web.security.oauth2.server
