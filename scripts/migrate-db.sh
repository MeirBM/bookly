#!/usr/bin/env bash
# Copy a Postgres database: scripts/migrate-db.sh SOURCE_URL TARGET_URL
# Dumps SOURCE to backup.dump (gitignored; it holds real data), restores into TARGET.
# Use the Neon *direct* URL, not the -pooler one.
#
# The target may already hold the schema (the app runs Flyway on first start), so the restore
# replaces those objects (--clean --if-exists). To keep that from destroying real data, the script
# refuses to run if any table other than flyway_schema_history on the target has rows.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 SOURCE_URL TARGET_URL" >&2
  exit 2
fi
SOURCE_URL=$1
TARGET_URL=$2

for tool in pg_dump pg_restore psql; do
  command -v "$tool" >/dev/null || {
    echo "$tool not found. Install the client tools: brew install libpq && brew link --force libpq" >&2
    exit 1
  }
done

# pg_dump refuses to dump a server newer than itself.
server_major=$(psql "$SOURCE_URL" -Atc "SHOW server_version_num" | cut -c1-2)
client_major=$(pg_dump --version | sed -E 's/.* ([0-9]+)(\.[0-9]+)?.*/\1/')
if [ "$client_major" -lt "$server_major" ]; then
  echo "pg_dump is v$client_major but the source server is v$server_major; install postgresql@$server_major client tools." >&2
  exit 1
fi

populated=$(psql "$TARGET_URL" -Atc "
  SELECT string_agg(table_name, ', ') FROM (
    SELECT table_name,
           (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name), false, true, '')))[1]::text::int AS n
    FROM information_schema.tables
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
  ) t WHERE n > 0")
if [ -n "$populated" ]; then
  echo "Target already has rows in: $populated. Refusing to overwrite it." >&2
  exit 1
fi

pg_dump "$SOURCE_URL" -Fc -f backup.dump
# Restored as SQL through psql, in one transaction, rather than `pg_restore -d`: a pg_dump newer
# than the target writes `SET transaction_timeout`, which servers before 17 reject, and pg_restore
# then exits non-zero over a harmless line. Filtering that one line and stopping on any other
# error means a failure leaves the target exactly as it was.
pg_restore --clean --if-exists --no-owner --no-acl -f - backup.dump \
  | grep -v '^SET transaction_timeout' \
  | psql "$TARGET_URL" --single-transaction -v ON_ERROR_STOP=1 -q
echo "Done. backup.dump contains production data; delete it when finished."
