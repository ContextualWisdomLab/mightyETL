# Legacy Local-Auth Bootstrap Retirement

**Implementation status:** `active_pr` #155  
**Protected baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`

This runbook defines the bounded compatibility change that stops new default Docker PostgreSQL installations from recreating the abandoned local-auth persistence objects `users`, `roles`, and `user_roles`. It does not claim that those historical objects have been removed from existing PostgreSQL volumes and it does not introduce a replacement authentication API.

## Clean-install boundary

The default Docker initialization path mounts only `docker/postgres/init/` into `/docker-entrypoint-initdb.d`. On this PR, `docker/postgres/init/01_schema.sql` retains the supported `processed_data` target table but no longer creates or seeds `users`, `roles`, or `user_roles`.

A brand-new PostgreSQL data directory therefore starts without the abandoned local-auth tables. This is a clean-install compatibility change, not an in-place migration.

The historical schema is preserved only as the explicit opt-in artifact `docker/postgres/compat/legacy_auth_tables.sql`. That file is deliberately outside the default init directory and must never be mounted into `/docker-entrypoint-initdb.d` by the shipped Compose configuration.

## Existing-volume boundary

Existing PostgreSQL volumes are intentionally untouched by this slice. Docker's PostgreSQL initialization scripts run only for an empty data directory, so a previously initialized volume can continue to contain `users`, `roles`, and `user_roles` after an application upgrade.

That asymmetry is deliberate. Repository and public-code searches found no shipped runtime SQL consumer or local sign-up/sign-in controller, but that evidence does not prove absence of private or external consumers. A destructive DROP or rename would therefore exceed the evidence available to this writer.

Operators must inventory downstream SQL, BI, migration, backup, export, and private integration consumers before deciding that the historical objects can be removed from an existing database.

## Rollback and forward recovery

### Roll back a new clean installation

If a deployment proves that it still requires the historical local-auth objects, apply `docker/postgres/compat/legacy_auth_tables.sql` explicitly to that database under an authorized database identity. Do not move or symlink the compatibility script into the default init directory merely to make all future installations recreate legacy state.

The compatibility script is idempotent for its table creation and seed role names. Its use must be change-controlled because it recreates nonconforming historical database names and should be treated as temporary compatibility debt rather than the target architecture.

### Recover an existing volume

This PR does not drop or rename existing rows, keys, or foreign keys. Rolling back application/container code therefore requires no database reverse migration for an already initialized volume.

If a future migration removes or renames the historical objects, that later change must provide its own data-preserving upgrade and rollback or forward-recovery rehearsal. Do not use this clean-install PR as evidence for that future destructive migration.

### Failure handling

Failure to apply the optional compatibility script is an operator-visible database error and must not be interpreted as successful legacy support. The application must not silently create the historical schema at runtime as a fallback.

## External-consumer uncertainty

The current repository contains no supported local `/auth/signup` or `/auth/signin` implementation that requires these tables. Public organization search did not identify another repository consumer. Those observations narrow the likely blast radius, but public-code search does not prove absence of private or external consumers.

Consequently:

- the default product stops advertising the schema through new clean installations;
- the compatibility artifact remains explicit and opt-in;
- existing volumes are not modified;
- complete retirement of the compatibility artifact requires stronger consumer evidence and a separately reviewed migration decision.

## Security and data handling

The compatibility tables may contain authentication identifiers and historical password material. Their continued presence in an old volume does not make them a supported identity store. Operators should restrict database access, encrypt storage and transport where applicable, avoid copying their contents into telemetry, and apply retention/deletion policy based on the actual deployment purpose and legal obligations.

No raw usernames, password values, table contents, SQL result data, or private-consumer identifiers are required in ordinary mightyETL telemetry for this retirement decision.

## Verification

The PR-level contract verifies that:

- `docker/postgres/init/01_schema.sql` no longer creates or seeds `users`, `roles`, or `user_roles`;
- the default Compose path still mounts only `docker/postgres/init/`;
- `docker/postgres/compat/legacy_auth_tables.sql` remains outside that default path;
- this runbook records the clean-install, existing-volume, rollback/recovery, and external-consumer boundaries;
- `CHANGELOG.md` records the compatibility-impacting clean-install change.

A future complete migration of existing volumes requires a real PostgreSQL upgrade/rollback rehearsal with representative rows and foreign keys before protected merge.
