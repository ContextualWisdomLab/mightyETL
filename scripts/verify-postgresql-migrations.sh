#!/usr/bin/env bash
set -Eeuo pipefail

: "${PGHOST:=127.0.0.1}"
: "${PGPORT:=5432}"
: "${PGDATABASE:=mightyetl_replay_test}"
: "${PGUSER:=mightyetl_test}"
: "${PGPASSWORD:=mightyetl_test_password}"
export PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD

migration_directory="etl-service/src/main/resources/db/migration"
if [[ ! -d "${migration_directory}" ]]; then
  printf 'Migration directory not found: %s\n' "${migration_directory}" >&2
  exit 1
fi

for attempt_number in $(seq 1 30); do
  if pg_isready --host "${PGHOST}" --port "${PGPORT}" --dbname "${PGDATABASE}" --username "${PGUSER}" >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt_number}" -eq 30 ]]; then
    printf 'PostgreSQL did not become ready after 30 attempts.\n' >&2
    exit 1
  fi
  sleep 2
done

mapfile -d '' migration_files < <(
  find "${migration_directory}" -maxdepth 1 -type f -name 'V*__*.sql' -print0 | sort -zV
)
if [[ "${#migration_files[@]}" -eq 0 ]]; then
  printf 'No versioned SQL migrations found.\n' >&2
  exit 1
fi

for migration_file in "${migration_files[@]}"; do
  printf 'Applying %s\n' "${migration_file}"
  psql --no-psqlrc --set ON_ERROR_STOP=1 --file "${migration_file}" >/dev/null
done

psql --no-psqlrc --set ON_ERROR_STOP=1 <<'SQL'
DO $verification_block$
DECLARE
    missing_column_count integer;
    restrict_foreign_key_count integer;
    replay_lookup_index_count integer;
    replay_check_definition text;
    cancellation_check_definition text;
BEGIN
    IF to_regclass('public.etl_job_records') IS NULL THEN
        RAISE EXCEPTION 'etl_job_records was not created';
    END IF;

    SELECT count(*)
      INTO missing_column_count
      FROM (
            VALUES
                ('replay_source_job_record_id', 'uuid'),
                ('replay_root_job_record_id', 'uuid'),
                ('replay_generation_count', 'integer'),
                ('cancellation_key_hash', 'character'),
                ('cancellation_code', 'character varying'),
                ('job_cancelled_at', 'timestamp with time zone')
      ) AS expected_columns(column_name, data_type)
      WHERE NOT EXISTS (
            SELECT 1
              FROM information_schema.columns AS actual_columns
             WHERE actual_columns.table_schema = 'public'
               AND actual_columns.table_name = 'etl_job_records'
               AND actual_columns.column_name = expected_columns.column_name
               AND actual_columns.data_type = expected_columns.data_type
      );

    IF missing_column_count <> 0 THEN
        RAISE EXCEPTION 'one or more cancellation/replay columns are missing or have the wrong type';
    END IF;

    SELECT count(*)
      INTO restrict_foreign_key_count
      FROM pg_constraint AS constraint_record
      JOIN pg_class AS table_record
        ON table_record.oid = constraint_record.conrelid
      JOIN unnest(constraint_record.conkey) AS constrained_attribute(attribute_number)
        ON true
      JOIN pg_attribute AS attribute_record
        ON attribute_record.attrelid = table_record.oid
       AND attribute_record.attnum = constrained_attribute.attribute_number
     WHERE table_record.relname = 'etl_job_records'
       AND constraint_record.contype = 'f'
       AND constraint_record.confrelid = table_record.oid
       AND constraint_record.confdeltype = 'r'
       AND attribute_record.attname IN (
            'replay_source_job_record_id',
            'replay_root_job_record_id'
       );

    IF restrict_foreign_key_count <> 2 THEN
        RAISE EXCEPTION 'replay source and root must each use a self-reference with ON DELETE RESTRICT';
    END IF;

    SELECT count(*)
      INTO replay_lookup_index_count
      FROM pg_class AS index_class
      JOIN pg_index AS index_record
        ON index_record.indexrelid = index_class.oid
      JOIN pg_class AS table_record
        ON table_record.oid = index_record.indrelid
     WHERE table_record.relname = 'etl_job_records'
       AND index_class.relname IN (
            'etl_job_replay_source_lookup_index',
            'etl_job_replay_root_lookup_index'
       )
       AND index_record.indisready
       AND index_record.indisvalid;

    IF replay_lookup_index_count <> 2 THEN
        RAISE EXCEPTION 'replay lookup indexes are missing or invalid';
    END IF;

    SELECT string_agg(pg_get_constraintdef(constraint_record.oid), ' ')
      INTO replay_check_definition
      FROM pg_constraint AS constraint_record
      JOIN pg_class AS table_record
        ON table_record.oid = constraint_record.conrelid
     WHERE table_record.relname = 'etl_job_records'
       AND constraint_record.contype = 'c'
       AND pg_get_constraintdef(constraint_record.oid) ILIKE '%replay_generation_count%';

    IF replay_check_definition IS NULL
       OR replay_check_definition NOT ILIKE '%replay_source_job_record_id%'
       OR replay_check_definition NOT ILIKE '%replay_root_job_record_id%'
       OR replay_check_definition NOT ILIKE '%100%' THEN
        RAISE EXCEPTION 'replay lineage checks do not bind source, root, and the bounded generation';
    END IF;

    SELECT string_agg(pg_get_constraintdef(constraint_record.oid), ' ')
      INTO cancellation_check_definition
      FROM pg_constraint AS constraint_record
      JOIN pg_class AS table_record
        ON table_record.oid = constraint_record.conrelid
     WHERE table_record.relname = 'etl_job_records'
       AND constraint_record.contype = 'c'
       AND pg_get_constraintdef(constraint_record.oid) ILIKE '%cancellation_key_hash%';

    IF cancellation_check_definition IS NULL
       OR cancellation_check_definition NOT ILIKE '%job_cancelled_at%'
       OR cancellation_check_definition NOT ILIKE '%CANCELLED%' THEN
        RAISE EXCEPTION 'cancellation lifecycle checks are incomplete';
    END IF;
END
$verification_block$;
SQL

rehearsal_file="etl-service/src/test/postgresql/replay_lineage_migration.sql"
if [[ ! -f "${rehearsal_file}" ]]; then
  printf 'Replay-lineage rehearsal not found: %s\n' "${rehearsal_file}" >&2
  exit 1
fi

printf 'Running %s\n' "${rehearsal_file}"
psql --no-psqlrc --set ON_ERROR_STOP=1 --file "${rehearsal_file}" >/dev/null

pg_dump --schema-only --no-owner --no-privileges > /tmp/mightyetl-postgresql-schema.sql
if [[ ! -s /tmp/mightyetl-postgresql-schema.sql ]]; then
  printf 'Schema-only dump was empty.\n' >&2
  exit 1
fi

printf 'PostgreSQL migration and replay-lineage verification succeeded.\n'
