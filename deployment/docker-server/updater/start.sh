#!/usr/bin/env bash
# Startpunkt des Updater-Containers.
#  1. Nach einem Neustart des Rechners: einen unterbrochenen Rollback zu Ende
#     bringen (Stromausfall, Windows-Update mitten in der Nacht ...).
#  2. Danach wartet crond auf die naechste Update-Zeit (Standard 03:00 Ortszeit).
set -euo pipefail

bash /erp/nachtupdate.sh --nach-neustart || true

zeitplan="${UPDATE_ZEITPLAN:-0 3 * * *}"
# Genau 5 cron-Felder aus Ziffern, * / , - (kein Platz fuer weitere Befehle)
if [[ ! "$zeitplan" =~ ^[0-9*/,-]+( [0-9*/,-]+){4}$ ]]; then
    echo "UPDATE_ZEITPLAN='$zeitplan' ist ungueltig - nehme 0 3 * * *" >&2
    zeitplan="0 3 * * *"
fi
mkdir -p /etc/crontabs
echo "$zeitplan bash /erp/nachtupdate.sh >/dev/null 2>&1" > /etc/crontabs/root
echo "Nachtupdate eingeplant: '$zeitplan' (Zeitzone ${TZ:-UTC})"
exec crond -f -l 8
