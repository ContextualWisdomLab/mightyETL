#!/usr/bin/env bash
set -euo pipefail
umask 077

: "${BACKUP_DIRECTORY:?Set BACKUP_DIRECTORY to an existing or creatable backup directory}"
: "${APPLICATION_SOURCE_SHA:?Set APPLICATION_SOURCE_SHA to the exact mightyETL source commit}"
: "${PGHOST:?Set PGHOST for the PostgreSQL server to back up}"
: "${PGPORT:?Set PGPORT for the PostgreSQL server to back up}"
: "${PGDATABASE:?Set PGDATABASE for the PostgreSQL database to back up}"
: "${PGUSER:?Set PGUSER for the PostgreSQL role used by backup tooling}"

if [[ ! "$APPLICATION_SOURCE_SHA" =~ ^[0-9a-f]{40}$ ]]; then
    printf 'APPLICATION_SOURCE_SHA must be a 40-character lowercase Git commit SHA\n' >&2
    exit 2
fi

for command_name in pg_dump pg_restore psql mktemp mv; do
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

query_flyway_schema_version() {
    psql \
        --host="$PGHOST" \
        --port="$PGPORT" \
        --username="$PGUSER" \
        --dbname="$PGDATABASE" \
        --no-psqlrc --tuples-only --no-align --set=ON_ERROR_STOP=1 \
        --command="SELECT COALESCE((SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1), 'none')"
}

mkdir -p -- "$BACKUP_DIRECTORY"
backup_directory=$(cd -- "$BACKUP_DIRECTORY" && pwd -P)
created_at_utc=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
backup_identity="mightyetl-postgres-${created_at_utc//[:]/}-${APPLICATION_SOURCE_SHA}"
final_bundle="${backup_directory}/${backup_identity}"
reservation_directory="${backup_directory}/.${backup_identity}.reservation"

if [[ -e "$final_bundle" ]]; then
    printf 'Refusing to replace an existing backup bundle: %s\n' "$final_bundle" >&2
    exit 4
fi

# mkdir is the same-filesystem compare-and-set for a second-resolution backup identity.
# A concurrent invocation with the same source and timestamp must fail before dump work begins.
if ! mkdir -- "$reservation_directory" 2>/dev/null; then
    printf 'Backup identity is already reserved by another invocation: %s\n' "$backup_identity" >&2
    exit 4
fi

temporary_bundle=""
cleanup() {
    if [[ -n "$temporary_bundle" ]]; then
        rm -rf -- "$temporary_bundle"
    fi
    rm -rf -- "$reservation_directory"
}
trap cleanup EXIT

temporary_bundle=$(mktemp -d "${backup_directory}/.mightyetl-postgres-backup.XXXXXX")
archive_path="${temporary_bundle}/database.dump"
manifest_path="${temporary_bundle}/manifest.txt"

# Bind migration provenance on both sides of the dump window. A concurrent Flyway migration may
# otherwise make a post-dump manifest claim a schema level newer than the archive snapshot.
flyway_schema_version_before_dump=$(query_flyway_schema_version)

pg_dump \
    --host="$PGHOST" \
    --port="$PGPORT" \
    --username="$PGUSER" \
    --dbname="$PGDATABASE" \
    --format=custom \
    --file="$archive_path"

# A completed custom archive must be structurally readable before it is published.
pg_restore --list "$archive_path" >/dev/null

flyway_schema_version_after_dump=$(query_flyway_schema_version)
if [[ "$flyway_schema_version_before_dump" != "$flyway_schema_version_after_dump" ]]; then
    printf 'Database migration level changed while backup was being captured\n' >&2
    exit 5
fi

server_version_num=$(psql \
    --host="$PGHOST" \
    --port="$PGPORT" \
    --username="$PGUSER" \
    --dbname="$PGDATABASE" \
    --no-psqlrc --tuples-only --no-align --set=ON_ERROR_STOP=1 \
    --command='SHOW server_version_num')

backup_sha256=$(sha256_file "$archive_path")

printf '%s\n' \
    "manifest_version=1" \
    "application_source_sha=${APPLICATION_SOURCE_SHA}" \
    "created_at_utc=${created_at_utc}" \
    "server_version_num=${server_version_num}" \
    "flyway_schema_version=${flyway_schema_version_before_dump}" \
    "backup_sha256=${backup_sha256}" \
    > "$manifest_path"

# Publish archive and manifest together through one same-filesystem directory rename.
mv -- "$temporary_bundle" "$final_bundle"
temporary_bundle=""
rm -rf -- "$reservation_directory"
trap - EXIT
printf '%s\n' "$final_bundle"
