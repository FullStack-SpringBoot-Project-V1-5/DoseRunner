#!/bin/bash
# Database-per-service on a single Postgres instance (dev only).
# Runs once, on first start of an empty volume (docker-entrypoint-initdb.d).
set -euo pipefail

create_db() {
  local db=$1 user=$2 pass=$3
  echo "creating database ${db} owned by ${user}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
	CREATE USER ${user} WITH PASSWORD '${pass}';
	CREATE DATABASE ${db} OWNER ${user};
	REVOKE ALL ON DATABASE ${db} FROM PUBLIC;
SQL
}

create_db identity identity "${IDENTITY_DB_PASSWORD:-identity}"
create_db orders   orders   "${ORDERS_DB_PASSWORD:-orders}"
create_db fleet    fleet    "${FLEET_DB_PASSWORD:-fleet}"
create_db custody  custody  "${CUSTODY_DB_PASSWORD:-custody}"
