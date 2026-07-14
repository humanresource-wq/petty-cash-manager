#!/usr/bin/env bash
# restore-db.sh — Interactive guided restore of the PostgreSQL database
# from a gzip-compressed backup produced by the db-backup sidecar container.
#
# Usage:
#   ./restore-db.sh                        # interactive file picker
#   ./restore-db.sh <path/to/file.sql.gz>  # non-interactive
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${SCRIPT_DIR}/backups"
ENV_FILE="${SCRIPT_DIR}/.env"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
COMPOSE="docker compose -f ${COMPOSE_FILE}"

# ── Colour helpers ────────────────────────────────────────────────────────────
RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'
err()  { echo -e "${RED}ERROR: $*${NC}" >&2; }
warn() { echo -e "${YELLOW}WARN:  $*${NC}"; }
ok()   { echo -e "${GREEN}OK:    $*${NC}"; }

# ── Preflight checks ──────────────────────────────────────────────────────────
for cmd in docker gunzip; do
  command -v "$cmd" &>/dev/null || { err "'$cmd' is not installed"; exit 1; }
done

if ! docker compose version &>/dev/null; then
  err "'docker compose' plugin is required (Docker >= 20.10)"
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  err ".env file not found at $ENV_FILE"
  exit 1
fi

# ── Load environment ──────────────────────────────────────────────────────────
set -a; source "$ENV_FILE"; set +a

POSTGRES_DB="${POSTGRES_DB:-pettycash}"
POSTGRES_USER="${POSTGRES_USER:-pettycash}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set in .env}"

# ── Verify the stack is up ────────────────────────────────────────────────────
if ! $COMPOSE ps --services --filter "status=running" 2>/dev/null | grep -q '^db$'; then
  err "The 'db' container is not running. Start the stack first:"
  echo "  docker compose up -d db"
  exit 1
fi

# ── Locate backups ────────────────────────────────────────────────────────────
if [[ ! -d "$BACKUP_DIR" ]]; then
  err "Backup directory not found: $BACKUP_DIR"
  exit 1
fi

# Accept an explicit file argument
if [[ $# -ge 1 ]]; then
  if [[ ! -f "$1" ]]; then
    err "Backup file not found: $1"
    exit 1
  fi
  SELECTED_BACKUP="$(realpath "$1")"
else
  mapfile -t BACKUPS < <(find "$BACKUP_DIR" -name "*.sql.gz" | sort -r)
  if [[ ${#BACKUPS[@]} -eq 0 ]]; then
    err "No .sql.gz files found in $BACKUP_DIR"
    exit 1
  fi

  echo ""
  echo "Available backups (newest first):"
  echo "──────────────────────────────────────────────────────────────────"
  for i in "${!BACKUPS[@]}"; do
    size=$(du -sh "${BACKUPS[$i]}" 2>/dev/null | cut -f1)
    mtime=$(date -r "${BACKUPS[$i]}" "+%Y-%m-%d %H:%M" 2>/dev/null || stat -c "%y" "${BACKUPS[$i]}" 2>/dev/null | cut -d' ' -f1,2 | cut -c1-16)
    printf "  [%2d]  %-50s  %6s  %s\n" "$((i+1))" "$(basename "${BACKUPS[$i]}")" "$size" "$mtime"
  done
  echo ""

  read -rp "Select backup number [1]: " CHOICE
  CHOICE="${CHOICE:-1}"

  if ! [[ "$CHOICE" =~ ^[0-9]+$ ]] || (( CHOICE < 1 || CHOICE > ${#BACKUPS[@]} )); then
    err "Invalid selection: $CHOICE"
    exit 1
  fi

  SELECTED_BACKUP="${BACKUPS[$((CHOICE-1))]}"
fi

echo ""
echo "  Backup file : $(basename "$SELECTED_BACKUP")"
echo "  Target DB   : $POSTGRES_DB"
echo "  DB user     : $POSTGRES_USER"
echo ""

# ── Safety confirmation ───────────────────────────────────────────────────────
warn "This will DESTROY and REPLACE ALL DATA in the '$POSTGRES_DB' database."
warn "There is no undo. Make sure you have the right backup selected."
echo ""
read -rp "  Type the database name to confirm restore: " CONFIRM_DB

if [[ "$CONFIRM_DB" != "$POSTGRES_DB" ]]; then
  err "Confirmation did not match. Aborting — no changes made."
  exit 1
fi

# ── Pause backend writes ──────────────────────────────────────────────────────
BACKEND_WAS_RUNNING=false
if $COMPOSE ps --services --filter "status=running" 2>/dev/null | grep -q '^backend$'; then
  BACKEND_WAS_RUNNING=true
fi

restart_backend() {
  if [[ "$BACKEND_WAS_RUNNING" == "true" ]]; then
    echo ""
    echo "Restarting backend container..."
    $COMPOSE start backend && ok "Backend restarted." || warn "Backend restart failed — run: docker compose start backend"
  fi
}
# Always attempt to restart backend on exit (success or failure)
trap restart_backend EXIT

if [[ "$BACKEND_WAS_RUNNING" == "true" ]]; then
  echo "[1/5] Stopping backend to pause application writes..."
  $COMPOSE stop backend
  ok "Backend stopped."
else
  echo "[1/5] Backend not running — skipping stop."
fi

# ── Terminate existing database connections ───────────────────────────────────
echo "[2/5] Terminating active connections to '$POSTGRES_DB'..."
$COMPOSE exec -T db \
  psql -U "$POSTGRES_USER" -d postgres \
  -c "SELECT pg_terminate_backend(pid)
      FROM pg_stat_activity
      WHERE datname = '$POSTGRES_DB'
        AND pid <> pg_backend_pid();" \
  > /dev/null
ok "Connections terminated."

# ── Drop and recreate the database ───────────────────────────────────────────
echo "[3/5] Dropping database '$POSTGRES_DB'..."
$COMPOSE exec -T db \
  psql -U "$POSTGRES_USER" -d postgres \
  -c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\";" \
  > /dev/null

echo "      Recreating database '$POSTGRES_DB'..."
$COMPOSE exec -T db \
  psql -U "$POSTGRES_USER" -d postgres \
  -c "CREATE DATABASE \"$POSTGRES_DB\" OWNER \"$POSTGRES_USER\";" \
  > /dev/null
ok "Database recreated."

# ── Stream and restore ────────────────────────────────────────────────────────
echo "[4/5] Restoring from $(basename "$SELECTED_BACKUP") ..."
if ! gunzip -c "$SELECTED_BACKUP" | \
     $COMPOSE exec -T db \
       psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -q -v ON_ERROR_STOP=1; then
  err "Restore failed. The database may be in an inconsistent state."
  err "Re-run this script to restore again from a known-good backup."
  exit 1
fi
ok "Restore complete."

# ── Restart backend (trap fires here on clean exit too) ───────────────────────
echo "[5/5] Restarting backend..."
# Disable trap so it doesn't double-fire, then call directly
trap - EXIT
restart_backend

echo ""
ok "Database '$POSTGRES_DB' successfully restored from $(basename "$SELECTED_BACKUP")."
