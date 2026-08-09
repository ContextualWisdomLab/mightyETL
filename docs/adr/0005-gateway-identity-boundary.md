# ADR-0005: Fail-Closed Gateway Identity Boundary

**Status:** Accepted direction / implementation pending  
**Date:** 2026-08-09

## Context

Protected develop has a class named `JwtAuthenticationFilter`, but it accepts only the literal example `valid_token`. Historical docs also describe local registration/password/JWT behavior for which no current controller implementation exists. This is a dangerous name/claim mismatch.

## Decision

Production gateway identity must use maintained Spring Security reactive OAuth 2.0 Resource Server JWT verification with deployment-owned issuer/JWK/audience/algorithm policy, while a standalone deny mode can start without inventing trust material. Unknown modes/configuration fail closed. Public health/info endpoints may remain deliberately unauthenticated; workload routes require the selected production identity policy.

Until PR #142 integrates, protected develop is `known_gap` and must not be marketed as production JWT authentication. Historical `/auth/signup` and `/auth/signin` designs are `superseded`.

## Consequences

- cryptographic verification is framework-owned rather than hand-written;
- deployment trust material is external configuration, not a repository secret;
- deny mode has an explicit non-authentication failure response;
- current placeholder must be removed, not cosmetically renamed as secure.

## Alternatives rejected

- **accept example token in production:** no cryptographic identity.
- **invent local issuer/key in source:** unsafe and environment-specific.
- **keep Basic/local password design as undocumented fallback:** expands attack surface and contradicts actual product direction.
