# Test and Verification Strategy

**Protected baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`

## 1. Quality objective

A green aggregate status is not sufficient by itself. Tests must prove the actual production boundary, on the source revision under review, with deterministic failure/success evidence and realistic domain semantics.

## 2. TDD contract

Every source behavior change follows RED → GREEN → REFACTOR:

1. add the smallest deterministic test that reaches the intended production boundary;
2. execute it before production modification and observe the intended failure;
3. reject import/setup/fixture/runner failures as invalid RED evidence;
4. implement the narrowest root-cause change;
5. rerun the exact failing test and observe GREEN;
6. run the relevant module/full reactor and exact coverage gates;
7. inspect the final diff for unrelated behavior drift.

Historical RED commits are development evidence. They are not merge evidence for a later head.

## 3. Test layers

| Layer | Required evidence |
| --- | --- |
| unit | pure validation, transformations, value objects, error classification |
| component/controller | HTTP status, headers, media types, owner/security boundary, non-leakage |
| integration | Spring transaction/JDBC behavior, idempotency, durable jobs, connectors |
| concurrency | same-key races, lease fences, cancellation/terminal races, CDC lifecycle/delivery |
| migration | clean install, upgrade, exact constraint/index definitions, nontransactional migration behavior where used |
| rollback/recovery | failed target transaction, stale lease, migration recovery, external-effect limitations |
| compatibility | HTTP/event/connector schema, old clients, renamed/legacy config aliases |
| security | hostile input, authorization, injection, secret/non-leakage, workflow permissions/source identity |
| packaging | complete Maven artifacts/images, SBOM, install/run smoke where applicable |
| performance | bounded batch, large schema/connector/CDC paths with explicit budgets and no unbounded fan-out |
| operational | health/status/control semantics, restart, stop, retry, observable state truthfulness |
| documentation | canonical docs compare against live source/API/migrations/status taxonomy |

## 4. Coverage contract

Owned production code maintains exact 100% configured statement/line/method/branch coverage where tooling exposes the dimension. Coverage is not satisfied by excluding difficult production paths without an ADR.

- Public production types/methods require beginner-readable documentation.
- New private branches are tested through observable behavior where possible.
- A skipped test/job is not positive evidence.
- Generated code or truly unreachable platform glue can be excluded only with a documented rationale and review.

## 5. Current domain-validity tests

### Bounded ETL

- exact UTF-8 byte limits;
- record-count limits;
- duplicate JSON field rejection;
- unsafe Unicode/control identifiers;
- numeric precision/scale handling;
- all-or-nothing transaction rollback;
- deterministic transformation and input-order response.

### Idempotency

- same principal/key/payload replay;
- changed payload conflict;
- different principal isolation;
- nonblocking concurrent same-key conflict;
- target failure leaves no false successful ledger;
- target + ledger commit atomically.

### Durable jobs

Protected develop:

- feature flag fail-closed;
- bounded submission;
- same-intent replay and conflict;
- owner-safe status lookup;
- migration lifecycle/payload constraints.

Active stack must independently prove worker lease, pagination, polling, ETag, cancellation, replay lineage and migrations on each repaired exact ancestry before merge.

### CDC

- source configuration validation;
- event mapping/publication;
- replica SQL boundary;
- start/stop concurrency and restart behavior;
- `active_pr` #139: acknowledgement-before-progress and hung-future timeout;
- `planned` #141: deterministic graceful-stop Future completion without wall-clock sleeps.

### Gateway

Protected placeholder tests are not evidence of production JWT security. PR #142 must test the registered WebFlux SecurityWebFilterChain, deny mode, JWT mode, malformed/unsigned/expired/wrong-audience/algorithm policies according to deployment configuration, and public actuator boundary.

## 6. Exact-source evidence

For `pull_request`, GitHub's default ref is the generated merge ref. Protected develop's current CI uses default checkout, so its source-executing results can be synthetic-merge previews.

Where mightyETL governance requires literal-head proof:

- checkout `github.event.pull_request.head.sha` explicitly;
- assert `git rev-parse HEAD` equals that SHA before running repository code;
- set `persist-credentials: false` unless a narrowly documented write is required;
- bind SBOM/scanners to the same identity;
- invalidate evidence after any head/base movement.

PR #121 carries these repository-local controls but remains `active_pr`.

## 7. Required PR gate inventory

At every merge decision refetch and classify:

- exact current head and exact live base tip;
- stack predecessor ancestry;
- CI and coverage;
- Dependency Review;
- SBOM;
- SAST/Semgrep/CodeQL or configured equivalent;
- hard security scanner source identity;
- commit statuses;
- formal reviews and requested reviewers/teams;
- unresolved human/CodeRabbit/GHAS/Dependabot/OpenCode/Noema/Strix feedback;
- migration/rollback/compatibility evidence;
- branch protection/rulesets;
- independent non-author approval where explicit governance requires it.

`queued`, `pending`, `neutral-required`, `skipped-required`, `absent`, `cancelled`, failed, stale-head, predecessor-head, old-base, status-only, and synthetic-merge-only evidence are non-passing for a gate that requires literal exact-head success.

## 8. Documentation contract tests

Documentation tests must compare canonical claims to source reality, not preserve historical claims merely because they were once written. They verify:

- canonical family entry points;
- status taxonomy (`implemented_on_develop`, `active_pr`, `planned`, `superseded`, `out_of_scope`);
- current API paths and durable database objects;
- legacy auth strings are explicitly described as superseded rather than shipped;
- Mermaid blocks and internal links remain parseable;
- DB object naming violations are identified and tracked;
- active PRs are not mislabeled as shipped;
- security authority claims match workflow/source code.

## 9. Performance/reliability acceptance

No success claim is based on a microbenchmark detached from production semantics. Relevant tests include realistic bounded JSON batches, PostgreSQL transaction contention, Kafka acknowledgement latency/failure, connector concurrency, large pagination datasets, and migration lock/index behavior.

Performance tests record environment, input shape, warmup, repetitions, distribution statistics, and resource limits. A regression threshold must have measurement error headroom rather than equal one noisy point estimate.

## 10. Release verification

Before a release:

1. refetch integrated protected head;
2. execute full supported platform test/coverage matrix;
3. verify all current security/dependency/SBOM/provenance gates;
4. rehearse applicable clean-install and upgrade migrations plus documented recovery;
5. run representative standalone ETL, standalone CDC, and composed MSA smoke paths;
6. verify public docs/API/ERD/UML/ADRs match the release head;
7. verify release artifacts after publication.

## 11. References

GitHub. (2026). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows

Meszaros, G. (2007). *xUnit test patterns: Refactoring test code*. Addison-Wesley.

Souppaya, M., Scarfone, K., & Dodson, D. (2022). *Secure Software Development Framework (SSDF) Version 1.1* (NIST SP 800-218). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-218
