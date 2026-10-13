#!/usr/bin/env bash
# Startpunkt des Updater-Containers (liegt im Projektordner, nicht im Image).
#  1. Nach einem Neustart des Rechners: einen unterbrochenen Rollback zu Ende
#     bringen (Stromausfall, Windows-Update mitten in der Nacht ...).
#  2. Gibt es noch kein Ergebnis der taeglichen Sicherung, festhalten, dass
#     ab jetzt gesichert wird - laeuft binnen eines Tages keine, meldet das ERP es.
#  3. Den Klick-Container "erp-jetzt-aktualisieren" anlegen (nicht starten).
#  4. Danach wartet crond auf die naechste Sicherung (Standard 02:00) und das
#     naechste Nachtupdate (Standard 03:00 Ortszeit).
set -euo pipefail

bash /erp/nachtupdate.sh --nach-neustart || true
bash /erp/sicherung.sh --status-anlegen || true

# Den Knopf "Jetzt aktualisieren" in Docker Desktop bereitstellen: ein Container,
# der nur bei Klick auf Start laeuft (Profil "manuell", sonst startete ihn
# "docker compose up" mit). Hier anlegen statt per Befehl, damit er nach jedem
# Neustart ohne Zutun in der Liste steht. Laeuft er gerade (ein Klick hat den
# Updater eben erst gestartet), bleibt er unangetastet: "create" wuerde ihn bei
# geaenderter Einstellung unter den Augen des Nutzers neu anlegen.
if [[ "$(docker inspect -f '{{.State.Running}}' erp-jetzt-aktualisieren 2>/dev/null || true)" != "true" ]]; then
    docker compose --project-directory /erp -f /erp/docker-compose.yml --profile manuell \
        create jetzt-aktualisieren >/dev/null \
        || echo "Hinweis: Der Knopf 'Jetzt aktualisieren' konnte nicht angelegt werden." >&2
fi

# Genau 5 cron-Felder aus Ziffern, * / , - (kein Platz fuer weitere Befehle)
zeitplan_pruefen() {  # zeitplan_pruefen <Name> <Wert> <Standard>
    if [[ "$2" =~ ^[0-9*/,-]+( [0-9*/,-]+){4}$ ]]; then
        printf '%s' "$2"
    else
        echo "$1='$2' ist ungueltig - nehme $3" >&2
        printf '%s' "$3"
    fi
}
update_zeit="$(zeitplan_pruefen UPDATE_ZEITPLAN "${UPDATE_ZEITPLAN:-0 3 * * *}" "0 3 * * *")"
sicherung_zeit="$(zeitplan_pruefen SICHERUNG_ZEITPLAN "${SICHERUNG_ZEITPLAN:-0 2 * * *}" "0 2 * * *")"

mkdir -p /etc/crontabs
{
    echo "$update_zeit bash /erp/nachtupdate.sh >/dev/null 2>&1"
    echo "$sicherung_zeit bash /erp/sicherung.sh >/dev/null 2>&1"
} > /etc/crontabs/root
echo "Nachtupdate eingeplant: '$update_zeit', Sicherung: '$sicherung_zeit' (Zeitzone ${TZ:-UTC})"
exec crond -f -l 8
