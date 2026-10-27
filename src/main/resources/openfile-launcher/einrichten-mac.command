#!/bin/bash
# ERP - Dateien direkt oeffnen einrichten (Mac). Laeuft ueber den Terminal-Befehl aus dem ERP
# (Einmal-Link per curl, siehe OpenFileLauncherService), keine Administrator-Rechte.
# Legt das Hilfsprogramm unter ~/Library/Application Support/ERP-Dateien-oeffnen ab und meldet
# ein kleines Programm in ~/Applications fuer openfile://-Links an.
set -euo pipefail
export PATH=/usr/bin:/bin:/usr/sbin:/sbin

BASIS="$HOME/Library/Application Support/ERP-Dateien-oeffnen"
APP="$HOME/Applications/ERP-Dateien-oeffnen.app"
LSREGISTER=/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister

echo "ERP - Dateien direkt oeffnen wird eingerichtet ..."
mkdir -p "$BASIS" "$HOME/Applications"
base64 -D -o "$BASIS/launcher.sh" <<'LAUNCHER'
@@LAUNCHER_BASE64@@
LAUNCHER
base64 -D -o "$BASIS/config.json" <<'CONFIG'
@@CONFIG_BASE64@@
CONFIG
chmod 755 "$BASIS/launcher.sh"

# Kleines Programm, das openfile://-Links annimmt und an launcher.sh weitergibt
rm -rf "$APP"
osacompile -o "$APP" \
  -e 'on open location dieAdresse' \
  -e 'set skript to (POSIX path of (path to home folder)) & "Library/Application Support/ERP-Dateien-oeffnen/launcher.sh"' \
  -e 'do shell script quoted form of skript & " " & quoted form of dieAdresse & " > /dev/null 2>&1 &"' \
  -e 'end open location' \
  -e 'on run' \
  -e 'display dialog "Dieses Programm oeffnet Dateien aus dem ERP. Es laeuft von selbst, wenn Sie im ERP auf eine Datei klicken." buttons {"OK"} default button 1' \
  -e 'end run'

PLIST="$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Delete :CFBundleIdentifier" "$PLIST" >/dev/null 2>&1 || true
/usr/libexec/PlistBuddy -c "Delete :CFBundleURLTypes" "$PLIST" >/dev/null 2>&1 || true
/usr/libexec/PlistBuddy \
  -c "Add :CFBundleIdentifier string de.handwerkerprogramm.erp-dateien-oeffnen" \
  -c "Add :CFBundleURLTypes array" \
  -c "Add :CFBundleURLTypes:0 dict" \
  -c "Add :CFBundleURLTypes:0:CFBundleURLName string ERP Datei oeffnen" \
  -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes array" \
  -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes:0 string openfile" \
  "$PLIST"
/usr/libexec/PlistBuddy -c "Add :LSUIElement bool true" "$PLIST" >/dev/null 2>&1 || true

# Nach der Aenderung an Info.plist neu (lokal) signieren und bei macOS anmelden
codesign --force --deep --sign - "$APP" >/dev/null 2>&1 || true
xattr -dr com.apple.quarantine "$APP" >/dev/null 2>&1 || true
"$LSREGISTER" -f "$APP"
@@ORDNER_ZUGANG@@
echo ""
echo "Fertig! Excel-Dateien aus dem ERP oeffnen sich jetzt direkt."
echo "Beim ersten Klick fragt der Browser, ob er den Link oeffnen darf - bitte erlauben."
echo "Das Hilfsprogramm haelt sich selbst aktuell. Dieses Fenster kann geschlossen werden."
