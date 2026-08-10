#!/usr/bin/env bash
set -euo pipefail
umask 077

: "${BACKUP_BUNDLE:?Set BACKUP_BUNDLE to one verified mightyETL PostgreSQL backup bundle}"
: "${EXPECTED_APPLICATION_SOURCE_SHA:?Set EXPECTED_APPLICATION_SOURCE_SHA to the exact source revision expected in the backup}"
: "${EXPECTED_BACKUP_SHA256:?Set EXPECTED_BACKUP_SHA256 to the independently recorded backup archive digest}"
: "${EXPECTED_MANIFEST_SHA256:?Set EXPECTED_MANIFEST_SHA256 to the independently recorded backup manifest digest}"
: "${RECOVERY_PGHOST:?Set RECOVERY_PGHOST to the disposable PostgreSQL restore target}"
: "${RECOVERY_PGPORT:?Set RECOVERY_PGPORT to the disposable PostgreSQL restore target port}"
: "${RECOVERY_PGDATABASE:?Set RECOVERY_PGDATABASE to the explicitly provisioned empty restore database}"
: "${RECOVERY_PGUSER:?Set RECOVERY_PGUSER to the restore role}"

if [[ ! "$EXPECTED_APPLICATION_SOURCE_SHA" =~ ^[0-9a-f]{40}$ ]]; then
    printf 'EXPECTED_APPLICATION_SOURCE_SHA must be a 40-character lowercase Git commit SHA\n' >&2
    exit 2
fi
if [[ ! "$EXPECTED_BACKUP_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
    printf 'EXPECTED_BACKUP_SHA256 must be a 64-character lowercase SHA-256 digest\n' >&2
    exit 2
fi
if [[ ! "$EXPECTED_MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
    printf 'EXPECTED_MANIFEST_SHA256 must be a 64-character lowercase SHA-256 digest\n' >&2
    exit 2
fi

for command_name in pg_restore psql; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        printf 'Required command is unavailable: %s\n' "$command_name" >&2
        exit 3
    fi
done

sha256_file() {
    local file_path=$1
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file_path" | awk '{print $1}'
        return
    fi
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file_path" | awk '{print $1}'
        return
    fi
    printf 'Neither sha256sum nor shasum is available\n' >&2
    return 1
}

backup_bundle=$(cd -- "$BACKUP_BUNDLE" 2>/dev/null && pwd -P) || {
    printf 'BACKUP_BUNDLE must be an existing directory\n' >&2
    exit 4
}
archive_path="${backup_bundle}/database.dump"
manifest_path="${backup_bundle}/manifest.txt"

if [[ -L "$BACKUP_BUNDLE" || ! -f "$archive_path" || -L "$archive_path" || ! -f "$manifest_path" || -L "$manifest_path" ]]; then
    printf 'Backup bundle must contain regular, non-symlink database.dump and manifest.txt files\n' >&2
    exit 4
fi

actual_manifest_sha256=$(sha256_file "$manifest_path")
if [[ "$actual_manifest_sha256" != "$EXPECTED_MANIFEST_SHA256" ]]; then
    printf 'Backup manifest SHA-256 verification failed\n' >&2
    exit 4
fi

manifest_value() {
    local wanted_key=$1
    local matched_value=""
    local match_count=0
    local line key value

    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" != *=* ]]; then
            printf 'Invalid backup manifest line\n' >&2
            return 1
        fi
        key=${line%%=*}
        value=${line#*=}
        if [[ "$key" == "$wanted_key" ]]; then
            match_count=$((match_count + 1))
            matched_value=$value
        fi
    done < "$manifest_path"

    if [[ "$match_count" -ne 1 ]]; then
        printf 'Backup manifest must contain exactly one %s entry\n' "$wanted_key" >&2
        return 1
    fi
    printf '%s' "$matched_value"
}

manifest_version=$(manifest_value manifest_version)
application_source_sha=$(manifest_value application_source_sha)
expected_backup_sha256=$(manifest_value backup_sha256)
expected_flyway_schema_version=$(manifest_value flyway_schema_version)
backup_server_version_num=$(manifest_value server_version_num)

if [[ "$manifest_version" != "1" ]]; then
    printf 'Unsupported backup manifest version\n' >&2
    exit 4
fi
if [[ ! "$application_source_sha" =~ ^[0-9a-f]{40}$ || "$application_source_sha" != "$EXPECTED_APPLICATION_SOURCE_SHA" ]]; then
    printf 'Backup application source does not match the expected source revision\n' >&2
    exit 4
fi
if [[ ! "$expected_backup_sha256" =~ ^[0-9a-f]{64}$ ]]; then
    printf 'Backup manifest SHA-256 is invalid\n' >&2
    exit 4
fi
if [[ "$expected_backup_sha256" != "$EXPECTED_BACKUP_SHA256" ]]; then
    printf 'Backup manifest digest does not match independently recorded archive evidence\n' >&2
    exit 4
fi
if [[ ! "$backup_server_version_num" =~ ^[0-9]{6,9}$ ]]; then
    printf 'Backup PostgreSQL server version provenance is invalid\n' >&2
    exit 4
fi

actual_backup_sha256=$(sha256_file "$archive_path")
if [[ "$actual_backup_sha256" != "$expected_backup_sha256" ]]; then
    printf 'Backup archive SHA-256 verification failed\n' >&2
    exit 4
fi

# Verify that the archive can be parsed before any write reaches the recovery target.
pg_restore --list "$archive_path" >/dev/null

psql_recovery=(
    psql
    --host="$RECOVERY_PGHOST"
    --port="$RECOVERY_PGPORT"
    --username="$RECOVERY_PGUSER"
    --dbname="$RECOVERY_PGDATABASE"
    --no-psqlrc
    --tuples-only
    --no-align
    --set=ON_ERROR_STOP=1
)

target_server_version_num=$("${psql_recovery[@]}" --command='SHOW server_version_num')
target_server_version_num=${target_server_version_num//[[:space:]]/}
if [[ ! "$target_server_version_num" =~ ^[0-9]{6,9}$ ]]; then
    printf 'Recovery target PostgreSQL server version is invalid\n' >&2
    exit 5
fi

backup_server_major=$((backup_server_version_num / 10000))
target_server_major=$((target_server_version_num / 10000))
if [[ "$backup_server_major" != "$target_server_major" ]]; then
    printf 'Recovery target PostgreSQL major version does not match backup provenance\n' >&2
    exit 5
fi

user_table_count=$("${psql_recovery[@]}" --command="SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') AND n.nspname NOT LIKE 'pg_toast%' AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')")
user_table_count=${user_table_count//[[:space:]]/}
if [[ ! "$user_table_count" =~ ^[0-9]+$ || "$user_table_count" != "0" ]]; then
    printf 'Recovery target must be explicitly provisioned and empty before rehearsal\n' >&2
    exit 5
fi

pg_restore \
    --host="$RECOVERY_PGHOST" \
    --port="$RECOVERY_PGPORT" \
    --username="$RECOVERY_PGUSER" \
    --dbname="$RECOVERY_PGDATABASE" \
    --exit-on-error \
    --no-owner \
    --no-privileges \
    "$archive_path"

restored_flyway_schema_version=$("${psql_recovery[@]}" --command="SELECT COALESCE((SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1), 'none')")
restored_flyway_schema_version=${restored_flyway_schema_version//$'\r'/}
restored_flyway_schema_version=${restored_flyway_schema_version//$'\n'/}

if [[ "$restored_flyway_schema_version" != "$expected_flyway_schema_version" ]]; then
    printf 'Restored Flyway schema version does not match backup provenance\n' >&2
    exit 6
fi

required_application_relation_count=$("${psql_recovery[@]}" --command="SELECT count(*) FROM (VALUES (to_regclass('public.processed_data')), (to_regclass('public.etl_idempotency_records')), (to_regclass('public.etl_job_records'))) AS required(relation_oid) WHERE relation_oid IS NOT NULL")
required_application_relation_count=${required_application_relation_count//[[:space:]]/}
if [[ ! "$required_application_relation_count" =~ ^[0-9]+$ || "$required_application_relation_count" != "3" ]]; then
    printf 'Restored database is missing one or more required mightyETL application relations\n' >&2
    exit 6
fi

printf '%s\n' 'PostgreSQL restore rehearsal completed on the explicit disposable target'
