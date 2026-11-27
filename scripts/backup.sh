#!/usr/bin/env bash
# Respaldo nocturno: base de datos + archivos, cifrado con age (o gpg) y rotación de 14 días.
# Cron sugerido (usuario jobhunter):  15 2 * * * /opt/jobhunter/scripts/backup.sh
# Requiere: ~/.my.cnf con credenciales de un usuario de solo lectura para mysqldump, y AGE_RECIPIENT definido.
set -euo pipefail
DEST="${BACKUP_DIR:-/var/backups/jobhunter}"
FILES_DIR="${JOBHUNTER_FILES_DIR:-/var/lib/jobhunter/files}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
: "${AGE_RECIPIENT:?Define AGE_RECIPIENT (clave pública age) para cifrar el respaldo}"
mkdir -p "$DEST"
umask 077
mysqldump --single-transaction --routines --no-tablespaces jobhunter | gzip > "$DEST/db-$STAMP.sql.gz"
tar -C "$(dirname "$FILES_DIR")" -czf "$DEST/files-$STAMP.tar.gz" "$(basename "$FILES_DIR")"
for f in "$DEST/db-$STAMP.sql.gz" "$DEST/files-$STAMP.tar.gz"; do
  age -r "$AGE_RECIPIENT" -o "$f.age" "$f" && rm -f "$f"
done
find "$DEST" -name '*.age' -mtime +14 -delete
echo "Respaldo listo: $DEST/*-$STAMP.*.age"
