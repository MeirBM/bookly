#!/usr/bin/env bash
# Copy a Postgres database: scripts/migrate-db.sh SOURCE_URL TARGET_URL
# Dumps SOURCE to backup.dump (gitignored; it holds real data), restores into TARGET.
# The target should be empty. Use the Neon *direct* URL, not the -pooler one.
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

pg_dump "$SOURCE_URL" -Fc -f backup.dump
pg_restore --no-owner --no-acl -d "$TARGET_URL" backup.dump
echo "Done. backup.dump contains production data; delete it when finished."
