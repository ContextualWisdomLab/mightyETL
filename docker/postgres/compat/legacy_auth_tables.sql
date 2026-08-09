-- DEPRECATED COMPATIBILITY: historical local-auth bootstrap only.
--
-- This file is intentionally outside docker/postgres/init and is never executed by the default
-- mightyETL clean-install path. Use it only when an existing integration has independently proven
-- that it still depends on the abandoned local users/roles schema. It does not re-enable a shipped
-- /auth/signup or /auth/signin API, and it must not be used as evidence that mightyETL provides a
-- production authentication service.
--
-- Existing PostgreSQL volumes that already contain these tables require no action from this file.
-- Before removing the compatibility artifact entirely, inventory private/external consumers and
-- preserve an explicit rollback/export path.

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(20) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (role_id) REFERENCES roles (id)
);

INSERT INTO roles (name)
VALUES ('ROLE_USER'), ('ROLE_ADMIN')
ON CONFLICT (name) DO NOTHING;
