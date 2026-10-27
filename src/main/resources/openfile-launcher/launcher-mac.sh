#!/bin/bash
# ERP - Dateien direkt oeffnen (Mac). Nimmt openfile://-Links aus dem ERP an, verbindet den
# gemeinsamen Ordner (smb) und oeffnet die Datei mit dem Standardprogramm (z. B. Excel).
# Kanonische Fassung: src/main/resources/openfile-launcher/launcher-mac.sh. Das ERP liefert sie
# mit der Einrichtungs-Datei aus und aktualisiert sie selbst - nur ueber https und nur mit
# gueltiger Unterschrift des ERP (oeffentlicher Schluessel in config.json).
set -u
export PATH=/usr/bin:/bin:/usr/sbin:/sbin

BASIS="$HOME/Library/Application Support/ERP-Dateien-oeffnen"
CONFIG="$BASIS/config.json"
LOG="$HOME/Library/Logs/ERP-Dateien-oeffnen.log"
mkdir -p "$BASIS" "$(dirname "$LOG")"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "$LOG"; }

meldung() {
  osascript -e 'on run argv' \
    -e 'display dialog (item 1 of argv) with title "ERP - Datei oeffnen" buttons {"OK"} default button 1 with icon caution' \
    -e 'end run' "$1" >/dev/null 2>&1
}

# Wert aus config.json (Text, Zahl oder bei Listen die Anzahl)
cfg() { plutil -extract "$1" raw -o - "$CONFIG" 2>/dev/null; }

klein() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

# \\HOST\Freigabe\Ordner\Datei -> /HOST/Freigabe/Ordner/Datei
normalisiere() { printf '%s' "$1" | tr '\\' '/' | tr -s '/'; }

# Prozent-Kodierung aufloesen (Argument, nicht in den Code eingesetzt)
url_dekodieren() {
  osascript -l JavaScript -e 'function run(a){ return decodeURIComponent(a[0]); }' "$1" 2>/dev/null
}

# ---------- Selbst-Update ----------
aktualisieren() {
  local erp version antwort status tmp
  erp="$(cfg erpAdresse)"
  version="$(cfg version)"
  case "$erp" in
    https://*) ;;
    *) log "Update: nur ueber https - '$erp' wird nicht gefragt"; return ;;
  esac
  tmp="$(mktemp -d)" || return
  antwort="$tmp/antwort.json"
  status="$(curl -sS --proto '=https' --max-time 15 -o "$antwort" -w '%{http_code}' -G \
    --data-urlencode "system=mac" --data-urlencode "version=$version" --data-urlencode "adresse=$erp" \
    "${erp%/}/openfile-launcher/aktuell" 2>>"$LOG")"
  if [ "$status" != "200" ]; then
    log "Update: aktuell ($version, Antwort $status)"; rm -rf "$tmp"; return
  fi
  # Antwort zerlegen: unterschriebenes Paket, Skript, Konfiguration, Unterschrift als Dateien
  if ! osascript -l JavaScript - "$antwort" "$tmp" >>"$LOG" 2>&1 <<'JXA'
ObjC.import('Foundation');
function schreibe(text, pfad) {
  if (!$(text).writeToFileAtomicallyEncodingError(pfad, true, $.NSUTF8StringEncoding, null)) throw new Error('schreiben: ' + pfad);
}
function run(argv) {
  var roh = $.NSString.stringWithContentsOfFileEncodingError(argv[0], $.NSUTF8StringEncoding, null).js;
  var neu = JSON.parse(roh);
  if (typeof neu.skript !== 'string' || neu.skript.indexOf('#!/bin/bash') !== 0
      || typeof neu.configJson !== 'string' || typeof neu.signatur !== 'string') throw new Error('Antwort unbrauchbar');
  schreibe(String(neu.version) + '\n' + neu.configJson + '\n' + neu.skript, argv[1] + '/paket');
  schreibe(neu.skript, argv[1] + '/launcher.sh');
  schreibe(neu.configJson, argv[1] + '/config.json');
  var sig = $.NSData.alloc.initWithBase64EncodedStringOptions(neu.signatur, 0);
  if (!sig || !sig.writeToFileAtomically(argv[1] + '/signatur', true)) throw new Error('Unterschrift unlesbar');
}
JXA
  then
    log "Update: Antwort unbrauchbar - nichts geaendert"; rm -rf "$tmp"; return
  fi
  cfg pruefSchluesselPem > "$tmp/schluessel.pem"
  if ! openssl dgst -sha256 -verify "$tmp/schluessel.pem" -signature "$tmp/signatur" "$tmp/paket" >/dev/null 2>&1; then
    log "Update ABGELEHNT: Unterschrift passt nicht - nichts geaendert"; rm -rf "$tmp"; return
  fi
  # Auch eine echte Unterschrift darf den Mac nicht umlenken oder auf alte Fassungen zurueckdrehen
  if [ "$(plutil -extract erpAdresse raw -o - "$tmp/config.json" 2>/dev/null)" != "$erp" ]; then
    log "Update ABGELEHNT: andere ERP-Adresse"; rm -rf "$tmp"; return
  fi
  local bisher neu_stand
  bisher="$(cfg stand)"; neu_stand="$(plutil -extract stand raw -o - "$tmp/config.json" 2>/dev/null)"
  case "$neu_stand" in ''|*[!0-9]*) log "Update ABGELEHNT: ohne Stand"; rm -rf "$tmp"; return ;; esac
  case "$bisher" in ''|*[!0-9]*) bisher=0 ;; esac
  if [ "$neu_stand" -le "$bisher" ]; then
    log "Update ABGELEHNT: Stand $neu_stand ist nicht neuer als $bisher"; rm -rf "$tmp"; return
  fi
  chmod 755 "$tmp/launcher.sh"
  mv -f "$tmp/config.json" "$CONFIG"
  mv -f "$tmp/launcher.sh" "$BASIS/launcher.sh"
  log "Update: neue Fassung $(cfg version) installiert"
  rm -rf "$tmp"
}

if [ "${1:-}" = "--aktualisieren" ]; then aktualisieren; exit 0; fi

# ---------- Datei oeffnen ----------
URL="${1:-}"
log "=== Start: $URL"
if [ ! -f "$CONFIG" ] || [ "$(cfg allowedRoots)" = "" ]; then
  log "FEHLER: config.json fehlt oder ohne erlaubte Ordner"
  meldung "Das Hilfsprogramm ist nicht fertig eingerichtet. Bitte im ERP unter Einstellungen -> Dateien neu herunterladen."
  exit 2
fi

# Update im Hintergrund anstossen - die Datei geht trotzdem sofort auf
( "$BASIS/launcher.sh" --aktualisieren >/dev/null 2>&1 & )

roh="${URL#*\?}"
pfad=""
IFS='&' read -r -a teile <<< "$roh"
for teil in "${teile[@]}"; do
  case "$teil" in path=*) pfad="$(url_dekodieren "${teil#path=}")" ;; esac
done
if [ -z "$pfad" ]; then log "FEHLER: kein Pfad in $URL"; exit 1; fi

ziel="$(normalisiere "$pfad")"
host="$(printf '%s' "$ziel" | cut -d/ -f2)"
freigabe="$(printf '%s' "$ziel" | cut -d/ -f3)"
rest="$(printf '%s' "$ziel" | cut -d/ -f4-)"
log "Ziel: host=$host freigabe=$freigabe datei=$rest"

if [ -z "$host" ] || [ -z "$freigabe" ] || [ -z "$rest" ]; then log "FEHLER: unvollstaendiger Pfad"; exit 2; fi
case "/$rest/" in */../*|*/./*) log "FEHLER: unzulaessiger Pfad"; exit 2 ;; esac

# Nur Tabellen und Zeichnungen - nie Programme oder Skripte aus der Freigabe
case "$(klein "$rest")" in
  *.xlsx|*.xls|*.xlsm|*.xlsb|*.xlt|*.xltx|*.xltm|*.csv|*.ods|*.sza|*.tcd) ;;
  *) log "FEHLER: Dateiart nicht erlaubt: $rest"
     meldung "Diese Dateiart oeffnet das Hilfsprogramm nicht."
     exit 2 ;;
esac

# Nur Dateien aus den freigegebenen Ordnern
im_erlaubten_ordner() {
  local anzahl i wurzel
  anzahl="$(cfg allowedRoots)"
  i=0
  while [ "$i" -lt "${anzahl:-0}" ]; do
    wurzel="$(normalisiere "$(cfg "allowedRoots.$i")")"
    wurzel="${wurzel%/}"
    case "$(klein "$ziel")" in "$(klein "$wurzel")"/*) return 0 ;; esac
    i=$((i + 1))
  done
  return 1
}
erlaubt=0
if im_erlaubten_ordner; then
  erlaubt=1
else
  # Hat der Admin den Ordner im ERP umgestellt, kennt erst die neue Fassung die neue
  # Adresse: jetzt gleich aktualisieren statt erst nach diesem Klick
  log "Ordner unbekannt - frage nach einer neuen Fassung"
  aktualisieren
  if im_erlaubten_ordner; then erlaubt=1; fi
fi
if [ "$erlaubt" != "1" ]; then
  log "FEHLER: ausserhalb der erlaubten Ordner: $ziel"
  meldung "Diese Datei liegt nicht im freigegebenen Ordner und wird nicht geoeffnet."
  exit 2
fi

# Rechner, unter denen die Freigabe erreichbar sein kann: erst der Name aus dem Buero,
# dann die Ausweich-Namen (z. B. Tailscale-Name fuer unterwegs).
kandidaten=("$host")
anzahl="$(cfg aliasHosts)"
i=0
while [ "$i" -lt "${anzahl:-0}" ]; do
  kandidaten+=("$(cfg "aliasHosts.$i")")
  i=$((i + 1))
done

# IP-Adressen ganz vergleichen, Namen nur bis zum ersten Punkt (Bonjour haengt .local o. ae. an)
gleicher_rechner() {
  case "$2" in
    *[!0-9.]*) [ "${1%%.*}" = "${2%%.*}" ] ;;
    *) [ "$1" = "$2" ] ;;
  esac
}

eingehaengt() {
  # Ausgabe von mount: //benutzer@HOST/Freigabe on /Volumes/Freigabe (smbfs, ...)
  local k zeile quelle h f
  while IFS= read -r zeile; do
    quelle="${zeile%% on /*}"
    quelle="${quelle#//}"
    quelle="${quelle#*@}"
    h="$(klein "${quelle%%/*}")"
    f="$(klein "$(url_dekodieren "${quelle#*/}")")"
    [ "$f" = "$(klein "$freigabe")" ] || continue
    for k in "${kandidaten[@]}"; do
      if gleicher_rechner "$h" "$(klein "$k")"; then
        zeile="${zeile#* on }"
        printf '%s' "${zeile% (*}"
        return 0
      fi
    done
  done < <(mount | grep '(smbfs')
  return 1
}

MELDUNG_FREIGABE_FEHLT="Das ERP ist erreichbar, der gemeinsame Ordner aber nicht. Wahrscheinlich ist der Ordner fuer unterwegs (Tailscale) noch nicht freigegeben. Das richtet der Administrator einmal ein (Tailscale-Regeln bzw. Freigabe-Tailscale.ps1 am Rechner mit dem Ordner)."
MELDUNG_NICHT_ERREICHBAR="Der gemeinsame Ordner ist gerade nicht erreichbar. Unterwegs bitte Tailscale einschalten - sonst ist der Rechner mit dem Ordner vielleicht aus."

# Antwortet das ERP? Kurzer Versuch auf dessen Port (https 443, http 80 oder der angegebene)
erp_erreichbar() {
  local erp hp h p
  erp="$(cfg erpAdresse)"
  case "$erp" in
    https://*) p=443 ;;
    http://*) p=80 ;;
    *) return 1 ;;
  esac
  hp="${erp#*://}"
  hp="${hp%%/*}"
  h="${hp%%:*}"
  if [ "$h" != "$hp" ]; then p="${hp##*:}"; fi
  [ -n "$h" ] && nc -z -G 2 "$h" "$p" >/dev/null 2>&1
}

# Hat die Einrichtung den Zugang zum Ordner in den Schluesselbund gelegt (Ordner-Freigabe des
# ERP), verbindet das Hilfsprogramm selbst - ohne Anmeldedialog. Der Finder nimmt den Eintrag
# nicht von sich aus. Das Passwort geht ueber stdin an osascript, nie in eine Befehlszeile.
mit_zugang_verbinden() {  # mit_zugang_verbinden <rechner> <freigabe (URL-kodiert)>
  local konto pw
  case "$1" in ''|*[!A-Za-z0-9._-]*) return 1 ;; esac
  case "$2" in ''|*[!A-Za-z0-9._~%-]*) return 1 ;; esac
  konto="$(security find-internet-password -s "$1" -r 'smb ' 2>/dev/null \
    | sed -n 's/^ *"acct"<blob>="\([a-z0-9-]*\)"$/\1/p' | head -n 1)"
  [ -n "$konto" ] || return 1
  pw="$(security find-internet-password -s "$1" -a "$konto" -r 'smb ' -w 2>/dev/null)" || return 1
  case "$pw" in ''|*[!A-Za-z0-9]*) return 1 ;; esac
  printf 'mount volume "smb://%s/%s" as user name "%s" with password "%s"\n' "$1" "$2" "$konto" "$pw" \
    | osascript >/dev/null 2>>"$LOG"
}

ordner="$(eingehaengt)"
if [ -z "$ordner" ]; then
  verbunden=""
  for k in "${kandidaten[@]}"; do
    if nc -z -G 2 "$k" 445 >/dev/null 2>&1; then
      freigabe_url="$(osascript -l JavaScript -e 'function run(a){ return encodeURIComponent(a[0]); }' "$freigabe" 2>/dev/null)"
      if mit_zugang_verbinden "$k" "$freigabe_url"; then
        log "Verbunden mit dem Zugang aus der Einrichtung: smb://$k/$freigabe_url"
      else
        log "Verbinde smb://$k/$freigabe_url"
        open "smb://$k/$freigabe_url"
      fi
      verbunden="$k"
      break
    fi
    log "Nicht erreichbar: $k"
  done
  if [ -z "$verbunden" ]; then
    # Ist das ERP erreichbar, laeuft die Verbindung (z. B. Tailscale) - dann sperrt der Rechner
    # mit dem Ordner die Freigabe von unterwegs, meist die Windows-Firewall
    if erp_erreichbar; then
      log "ERP erreichbar, Ordner nicht -> Freigabe fuer unterwegs fehlt"
      meldung "$MELDUNG_FREIGABE_FEHLT"
    else
      log "Weder Ordner noch ERP erreichbar"
      meldung "$MELDUNG_NICHT_ERREICHBAR"
    fi
    exit 3
  fi
  # Beim ersten Mal fragt der Mac nach Benutzer und Passwort - bis zu 2 Minuten warten
  for _ in $(seq 1 120); do
    ordner="$(eingehaengt)" && [ -n "$ordner" ] && break
    sleep 1
  done
fi
if [ -z "$ordner" ]; then log "FEHLER: Freigabe nicht verbunden"; exit 3; fi

datei="$ordner/$rest"
# Nur echte Dateien - keine Ordner oder Verknuepfungen (z. B. auf ein Programm)
if [ ! -f "$datei" ] || [ -L "$datei" ]; then
  log "FEHLER: Datei fehlt: $datei"
  meldung "Die Datei wurde im gemeinsamen Ordner nicht gefunden."
  exit 5
fi
log "Oeffne $datei"
open "$datei"
