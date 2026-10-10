#!/bin/sh
# =============================================================================
# Laeuft EINMAL, wenn der PostgreSQL-Container eine leere Datenbank anlegt.
# Legt die Rolle an, mit der sich das ERP verbindet - bewusst OHNE
# Superuser-Rechte (der Superuser "postgres" bleibt fuer Sicherungen und
# Rueckspielen im Nachtupdate). Die Rolle besitzt Datenbank und Schema
# "public", darf also Tabellen anlegen (Basis-Schema, Flyway-Migrationen).
# Namen und Passwort kommen als psql-Variablen - nichts davon steht im SQL-Text.
#
# Kein "set -eu": Ohne Ausfuehrungsrecht (Windows-Ordner) wird die Datei vom
# Image-Startskript eingelesen statt ausgefuehrt - das soll sie nicht verstellen.
# Fehler beenden trotzdem: ON_ERROR_STOP + "set -e" des Startskripts.
# =============================================================================
if [ "$ERP_DB_USER" = "$POSTGRES_USER" ]; then
    echo "FEHLER: DB_USER in .env darf nicht \"$POSTGRES_USER\" heissen (das ist der Datenbank-Verwalter)." >&2
    exit 1
fi
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v rolle="$ERP_DB_USER" -v passwort="$ERP_DB_PASSWORD" -v datenbank="$POSTGRES_DB" <<'SQL'
CREATE ROLE :"rolle" LOGIN PASSWORD :'passwort';
ALTER DATABASE :"datenbank" OWNER TO :"rolle";
ALTER SCHEMA public OWNER TO :"rolle";
SQL
