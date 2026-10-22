#!/usr/bin/env bash
# Lokaler Testserver mit einer Kopie der Produktions-Datenbank – abgeschottet nach außen.
#
#   scripts/prodkopie/prodkopie-starten.sh            Backend gegen die vorhandene Kopie starten
#   scripts/prodkopie/prodkopie-starten.sh --neu      neueste Sicherung frisch einspielen, dann starten
#   scripts/prodkopie/prodkopie-starten.sh --nur-db   nur einspielen, kein Backend
#   scripts/prodkopie/prodkopie-starten.sh --loeschen Kopie samt Daten-Volume restlos entfernen
#
# Code und Daten bleiben getrennt aktuell: Das Backend läuft aus dem Checkout bzw.
# Worktree, aus dem das Skript aufgerufen wird (also mit dessen Git-Stand), die
# Daten stammen aus der neuesten nächtlichen Sicherung (pg_dump, *.sql.gz).
#
# Ordner der Sicherungen: Umgebungsvariable PRODKOPIE_SICHERUNGEN oder eine Zeile
# in ~/.config/erp-prodkopie/sicherungen-pfad. Das Datenbank-Passwort der Kopie
# wird beim ersten Lauf zufällig erzeugt und liegt nur in ~/.config/erp-prodkopie/.
#
# Abschottung:
#  - abschotten.sql leert alle Zugänge und schaltet Mahnlauf, Telefon, Postfächer,
#    Freigaben und Push ab – nach dem Import, vor dem Start und nach dem Start
#    (dann sind auch neue Flyway-Migrationen gelaufen).
#  - Startparameter (Vorrang vor .env, Umgebung und application-local.properties)
#    leeren alle Zugänge und Schlüssel und schalten Außen-Funktionen ab.
#  - Toter HTTP(S)-Proxy als zweiter Riegel, nur localhost bleibt direkt. Mail- und
#    Telefon-Verbindungen (eigene Sockets) sperren allein die geleerten Zugänge.
#  - Das Backend lauscht nur auf 127.0.0.1: Die Kopie ist nicht im WLAN oder Tailnet
#    erreichbar. Der iOS-Simulator erreicht sie trotzdem über localhost.
set -euo pipefail

cd "$(dirname "$0")/../.."
skript_ordner=scripts/prodkopie
konfig_ordner="$HOME/.config/erp-prodkopie"
container=erp-prodkopie-db
port=5434

neu=false
nur_db=false
loeschen=false
for arg in "$@"; do
    case "$arg" in
        --neu) neu=true ;;
        --nur-db) neu=true; nur_db=true ;;
        --loeschen) loeschen=true ;;
        *) echo "Unbekannte Option: $arg" >&2; exit 2 ;;
    esac
done

if [[ "$loeschen" == true ]]; then
    docker rm -f -v "$container" >/dev/null 2>&1 || true
    echo "Kopie samt Daten-Volume entfernt."
    exit 0
fi

mkdir -p "$konfig_ordner"
chmod 700 "$konfig_ordner"
passwort_datei="$konfig_ordner/db-passwort"
if [[ ! -s "$passwort_datei" ]]; then
    (umask 077; openssl rand -hex 24 > "$passwort_datei")
fi
chmod 600 "$passwort_datei"

psql_kopie() {
    docker exec -i "$container" psql -q -v ON_ERROR_STOP=1 -U erp_user -d kalkulationsprogramm_db "$@"
}

warte_auf_db() {
    # Über TCP prüfen: Der Init-Server des Images lauscht nur auf dem Socket und
    # meldet sich sonst "bereit", bevor die Datenbank fertig angelegt ist.
    for _ in $(seq 1 60); do
        docker exec "$container" pg_isready -h 127.0.0.1 -U erp_user -d kalkulationsprogramm_db >/dev/null 2>&1 && return 0
        sleep 1
    done
    echo "Die Datenbank der Kopie startet nicht. Logs: docker logs $container" >&2
    exit 1
}

if ! docker container inspect "$container" >/dev/null 2>&1; then
    neu=true
fi

if [[ "$neu" == true ]]; then
    sicherungen="${PRODKOPIE_SICHERUNGEN:-}"
    if [[ -z "$sicherungen" && -s "$konfig_ordner/sicherungen-pfad" ]]; then
        sicherungen="$(head -n1 "$konfig_ordner/sicherungen-pfad")"
    fi
    sicherungen="${sicherungen/#\~/$HOME}"
    if [[ -z "$sicherungen" || ! -d "$sicherungen" ]]; then
        echo "Ordner mit den Sicherungen fehlt. PRODKOPIE_SICHERUNGEN setzen oder den Pfad" >&2
        echo "in $konfig_ordner/sicherungen-pfad eintragen." >&2
        exit 1
    fi
    sicherung=""
    while IFS= read -r -d '' datei; do
        if [[ -z "$sicherung" || "$datei" -nt "$sicherung" ]]; then sicherung="$datei"; fi
    done < <(find "$sicherungen" -name '*.sql.gz' -type f -print0)
    if [[ -z "$sicherung" ]]; then
        echo "Keine Sicherung (*.sql.gz) in $sicherungen gefunden." >&2
        exit 1
    fi
    echo "Sicherung: $sicherung"
    if ! gzip -t "$sicherung" 2>/dev/null; then
        echo "Die Sicherung ist unvollständig (z. B. noch nicht fertig synchronisiert)." >&2
        exit 1
    fi

    # Neuere pg_dump-Versionen schreiben Befehle, die ein altes Image nicht kennt.
    docker pull -q postgres:16 >/dev/null 2>&1 || true
    # -v: Das Daten-Volume der alten Kopie mit entfernen, sonst bliebe sie verwaist liegen.
    docker rm -f -v "$container" >/dev/null 2>&1 || true
    docker run -d --name "$container" -p "127.0.0.1:$port:5432" \
        -e POSTGRES_DB=kalkulationsprogramm_db -e POSTGRES_USER=erp_user \
        -e POSTGRES_PASSWORD="$(cat "$passwort_datei")" -e TZ=Europe/Berlin \
        postgres:16 >/dev/null
    warte_auf_db

    echo "Einspielen ..."
    gzip -cd "$sicherung" | psql_kopie
    echo "Abschotten ..."
    psql_kopie < "$skript_ordner/abschotten.sql"
else
    docker start "$container" >/dev/null
    warte_auf_db
    # Schadet nie: auch eine ältere Kopie ist danach sicher abgeschottet.
    psql_kopie < "$skript_ordner/abschotten.sql"
fi

if [[ "$nur_db" == true ]]; then
    echo "Kopie bereit auf 127.0.0.1:$port."
    exit 0
fi

if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port 8080 ist belegt – erst den anderen Server beenden." >&2
    exit 1
fi

# Werte aus einer .env (z. B. Website-Schlüssel) dürfen hier nicht greifen.
unset WEBSITE_INTERNAL_API_KEY WEBSITE_BEITRAEGE_BASE_URL WEBSITE_BEITRAEGE_API_TOKEN \
      PUSH_VAPID_PUBLIC_KEY PUSH_VAPID_PRIVATE_KEY CF_ACCESS_ENABLED
# Verbindung zur Kopie über SPRING_DATASOURCE_*: Umgebungsvariablen schlagen eine
# application-local.properties im Checkout, sonst liefe das Backend still gegen die
# Entwickler-DB. Das Passwort bewusst nicht als Startparameter (Prozessliste).
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$port/kalkulationsprogramm_db"
export SPRING_DATASOURCE_USERNAME=erp_user
SPRING_DATASOURCE_PASSWORD="$(cat "$passwort_datei")"
export SPRING_DATASOURCE_PASSWORD

# Startparameter haben Vorrang vor Umgebung und Profil-Dateien. Leere Werte schalten
# auch Zugänge ab, die in der DB fehlen und sonst aus Env/Properties kämen.
argumente=(
    --server.address=127.0.0.1
    --email.features.enabled=false
    --ai.email.enabled=false
    --ai.rag.enabled=false
    --ai.ollama.enabled=false
    --ai.ki-hilfe.web-search-enabled=false
    --ai.gemini.api-key=
    --ai.spamfilter.external.url=
    --ai.spamfilter.external.api-key=
    --smtp.host= --smtp.username= --smtp.password=
    --imap.host= --imap.username= --imap.password=
    --mail.credentials.encryption-key=
    --vendor.microsoft.enabled=false
    --vendor.microsoft.client-secret=
    --vendor.amazon.enabled=false
    --vendor.amazon.client-secret=
    --vendor.amazon.refresh-token=
    --cloudflare.access.enabled=false
    --website.internal-api.key=
    --website.beitraege.base-url=
    --website.beitraege.api-token=
    --push.vapid.public-key=
    --push.vapid.private-key=
)
# Toter Proxy als zweiter Riegel: HTTP(S)-Aufrufe laufen ins Leere, nur localhost
# bleibt direkt erreichbar (Ollama). Ein SOCKS-Proxy ginge auch auf die JDBC-
# Verbindung zu 127.0.0.1 und ist deshalb bewusst nicht gesetzt.
jvm="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9"
jvm+=" -Dhttp.nonProxyHosts=localhost|127.0.0.1|[::1]"

echo "Backend startet auf http://localhost:8080 (Mobile-App: /zeiterfassung/) ..."
./mvnw -q spring-boot:run \
    -Dspring-boot.run.jvmArguments="$jvm" \
    -Dspring-boot.run.arguments="${argumente[*]}" &
backend=$!
trap 'kill "$backend" 2>/dev/null; wait "$backend" 2>/dev/null' INT TERM

# Flyway läuft erst beim Start. Setzt eine neue Migration Zugänge oder Schalter,
# schottet dieser zweite Lauf sie wieder ab, sobald das Backend bereit ist.
for _ in $(seq 1 180); do
    kill -0 "$backend" 2>/dev/null || break
    if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
        psql_kopie < "$skript_ordner/abschotten.sql" && echo "Nach dem Start erneut abgeschottet."
        break
    fi
    sleep 2
done
wait "$backend"
