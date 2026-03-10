# Deployment Scripts

Dieses Verzeichnis enthält Skripte für das Deployment und Update des Kalkulationsprogramms.

## 📦 update-production.ps1

Automatisches Update-Skript für das Produktionssystem in `C:\Kalkulationsprogramm`.

### Was macht das Skript?

1. ✅ Wechselt zum main branch (falls nicht bereits aktiv)
2. ✅ Pullt die neuesten Änderungen vom Repository
3. ✅ Führt Maven Build aus (`mvn clean package -DskipTests`)
4. ✅ Erstellt automatisch ein Backup der alten JAR
5. ✅ Kopiert die neue JAR nach `C:\Kalkulationsprogramm`
6. ✅ Optional: Neustart des Services

### Verwendung

#### Standard-Update (empfohlen)
```powershell
.\update-production.ps1
```

#### Mit Optionen
```powershell
# Build überspringen (nur wenn bereits gebaut)
.\update-production.ps1 -SkipBuild

# Kein Backup erstellen
.\update-production.ps1 -SkipBackup

# Alle Warnungen ignorieren
.\update-production.ps1 -Force

# Kombiniert
.\update-production.ps1 -SkipBackup -Force
```

### Voraussetzungen

- ✅ Git muss installiert sein
- ✅ Maven muss installiert und im PATH sein
- ✅ Java JDK muss installiert sein
- ✅ Schreibrechte auf `C:\Kalkulationsprogramm`

### Backups

Das Skript erstellt automatisch Backups der alten JAR-Dateien in:
```
C:\Kalkulationsprogramm\backups\
```

Es werden die letzten 5 Backups aufbewahrt, ältere werden automatisch gelöscht.

Format: `handwerkerprogramm-0.0.1-SNAPSHOT.jar.YYYY-MM-DD_HH-mm-ss.bak`

### Sicherheitshinweise

⚠️ **WICHTIG:** Nach dem Update muss die Anwendung/der Service neu gestartet werden!

Das Skript prüft:
- Ob uncommitted changes vorhanden sind
- Ob du auf dem main branch bist
- Ob die JAR-Datei erfolgreich erstellt wurde

### Fehlerbehandlung

Wenn etwas schief geht:
1. Prüfe die Fehlermeldung im Terminal
2. Falls der Build fehlschlägt: Prüfe Maven-Logs
3. Falls das Kopieren fehlschlägt: Prüfe Schreibrechte
4. Im Notfall: Restore ein Backup aus `C:\Kalkulationsprogramm\backups\`

### Restore von Backup

Falls du ein Backup wiederherstellen musst:
```powershell
# Finde das gewünschte Backup
ls C:\Kalkulationsprogramm\backups\

# Restore (Beispiel)
Copy-Item "C:\Kalkulationsprogramm\backups\handwerkerprogramm-0.0.1-SNAPSHOT.jar.2024-12-04_15-30-00.bak" `
          "C:\Kalkulationsprogramm\handwerkerprogramm-0.0.1-SNAPSHOT.jar" -Force

# Service neu starten
Restart-Service HandwerkerProgramm
```

## 🔄 Typischer Update-Workflow

1. **Entwicklung abgeschlossen** im Repository
2. **Commit und Push** zu main branch
3. **Update-Skript ausführen:**
   ```powershell
   cd C:\Users\bausc\OneDrive\Dokumente\GitHub\Handwerkerprogramm\deployment\scripts
   .\update-production.ps1
   ```
4. **Service neu starten** (wird vom Skript gefragt)
5. **Testen** der neuen Version

## 📝 Anpassungen

Falls du den Service-Namen ändern musst, bearbeite die Zeile im Skript:
```powershell
$serviceName = "HandwerkerProgramm"  # Hier deinen Service-Namen eintragen
```

Falls sich der JAR-Name ändert, bearbeite:
```powershell
$JAR_NAME = "handwerkerprogramm-0.0.1-SNAPSHOT.jar"
```
