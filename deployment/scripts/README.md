# Deployment Scripts (Altweg: JAR unter Windows)

Dieses Verzeichnis enthält Skripte für Start, Sicherung und Update des Kalkulationsprogramms,
wenn es als JAR auf einem Windows-Rechner läuft (`C:\Kalkulationsprogramm`).

> **Altweg.** Die Skripte stammen aus der Zeit vor `deployment/docker-server` und wurden
> zuletzt am 11.10.2026 auf PostgreSQL umgestellt. Für Neuinstallationen ist
> [`deployment/docker-server`](../docker-server/README.md) der Weg – dort erledigt der
> Updater-Container Sicherung, Update und Rollback. Installation und geplante Aufgaben:
> [DEPLOYMENT_README.md](../DEPLOYMENT_README.md).

## Übersicht

| Skript | Zweck |
| --- | --- |
| `UPDATE.bat` | Startet `update-production.ps1` per Doppelklick |
| `update-production.ps1` | Neuen Stand von `main` holen, bauen und nach `C:\Kalkulationsprogramm` kopieren (siehe unten) |
| `backup-database.ps1` | Datenbank sichern (lokal, externe Platte, OneDrive) und Uploads spiegeln |
| `install-scheduled-tasks.ps1` | Kopiert Backup-, Start- und Neustart-Skript nach `C:\Kalkulationsprogramm\scripts` und richtet die Windows-Aufgaben ein |
| `SETUP-AUTOSTART.bat`, `setup-autostart.ps1` | Richtet nur die Aufgabe „Kalkulationsprogramm - Auto Start“ ein (Skripte müssen schon in `C:\Kalkulationsprogramm\scripts` liegen) |
| `start-kalkulationsprogramm.ps1` | Startet den Server im Hintergrund und wartet, bis der Port antwortet |
| `restart-kalkulationsprogramm.ps1` | Beendet den Server auf dem Port und ruft das Startskript auf |
| `start-server.bat` | Startet die neueste JAR aus `C:\Kalkulationsprogramm` (sonst aus `target\`) in einem Fenster und öffnet den Browser |

## 📦 update-production.ps1

Update-Skript für das Produktionssystem in `C:\Kalkulationsprogramm`. Es startet sich bei
Bedarf selbst mit Administrator-Rechten neu.

### Was macht das Skript?

1. ✅ Prüft auf nicht committete Änderungen und ob `main` aktiv ist (fragt nach, ob es
   wechseln soll)
2. ✅ Setzt die eingecheckte Frontend-Build-Ausgabe (`src/main/resources/static`) zurück und
   pullt `main`
3. ✅ Sichert die Datenbank per `pg_dump` im Container nach
   `C:\Kalkulationsprogramm\backups\db\` (die letzten 10 bleiben). **Ohne gelungene
   Sicherung bricht das Update ab**, bevor etwas verändert wurde.
4. ✅ Baut beide Frontends (`npm ci` nur wenn nötig, dann `npm run build`)
5. ✅ Baut die JAR (`mvnw.cmd clean package -DskipTests`, sonst `mvn`)
6. ✅ Beendet den Prozess, der auf Port 8080 lauscht
7. ✅ Sichert die alten JAR-Dateien nach `C:\Kalkulationsprogramm\backups\` (die letzten 5
   bleiben) und kopiert die neue JAR nach `C:\Kalkulationsprogramm`
8. ✅ Kopiert `application.properties` und `application-local.properties` aus
   `src\main\resources` sowie `config\logback-spring.xml` nach `C:\Kalkulationsprogramm\config`
9. ✅ Kopiert das OpenFile-Launcher-Setup (`OpenFileLauncher-Setup.ps1`,
   `OpenFileLauncher-Install.bat`) nach `C:\Kalkulationsprogramm\downloads` – von dort bietet
   das ERP es zum Herunterladen an
10. ✅ Startet die Anwendung wieder, wenn sie vorher lief (sonst Rückfrage)

### Verwendung

#### Standard-Update (empfohlen)
```powershell
.\UPDATE.bat
# oder
.\update-production.ps1
```

#### Mit Optionen
```powershell
# Gar nicht bauen (nur wenn bereits gebaut)
.\update-production.ps1 -SkipBuild

# Nur die Frontends nicht bauen (eingecheckte Dateien aus src\main\resources\static nehmen)
.\update-production.ps1 -SkipFrontendBuild

# Keine Sicherung (weder Datenbank noch alte JAR) - nur bewusst!
.\update-production.ps1 -SkipBackup

# Keine Rückfragen zu nicht committeten Änderungen und Branch
.\update-production.ps1 -Force

# Anderer Datenbank-Container (z. B. eigener Container für das JAR)
.\update-production.ps1 -DbContainer <name> -DbUser erp_user
```

`-Force` wechselt **nicht** auf `main`: Steht das Repository auf einem anderen Branch, wird
dieser gebaut und eingespielt.

Standard für die Datenbank ist `-DbContainer erp-postgres -DbUser postgres` (der Container
von `deployment/docker-server`). Ein JAR erreicht diese Datenbank aber nicht – siehe
[DEPLOYMENT_README.md](../DEPLOYMENT_README.md), Abschnitt Voraussetzungen.

### Voraussetzungen

- ✅ Git
- ✅ Node.js mit npm (für den Frontend-Build; sonst `-SkipFrontendBuild`)
- ✅ Java JDK 25 (Maven kommt über `mvnw.cmd` aus dem Repository)
- ✅ Docker Desktop mit dem Datenbank-Container (für die Sicherung)
- ✅ Das Repository unter `C:\Github\ERP-System-fuer-Handwerksbetriebe` (fest im Skript)
- ✅ Administrator-Rechte (fragt das Skript selbst an)

### Backups

| Was | Wohin | Aufbewahrt |
| --- | --- | --- |
| Datenbank | `C:\Kalkulationsprogramm\backups\db\kalkulationsprogramm_db_<yyyy-MM-dd_HH-mm-ss>.sql` | die letzten 10 |
| Alte JAR | `C:\Kalkulationsprogramm\backups\<jar-name>.<yyyy-MM-dd_HH-mm-ss>.bak` | die letzten 5 |

### Fehlerbehandlung

Wenn etwas schief geht:
1. Prüfe die Fehlermeldung im Terminal
2. Datenbank-Sicherung fehlgeschlagen: Läuft Docker Desktop? Stimmen `-DbContainer` und
   `-DbUser`? Es wurde noch nichts verändert.
3. Falls der Build fehlschlägt: Prüfe npm- bzw. Maven-Ausgabe. Die laufende Anwendung ist
   bis dahin unverändert.
4. Falls das Kopieren fehlschlägt: Prüfe Schreibrechte
5. Im Notfall: Restore ein Backup aus `C:\Kalkulationsprogramm\backups\`

### Restore von Backup

Falls du die alte JAR wiederherstellen musst:
```powershell
# Finde das gewünschte Backup
ls C:\Kalkulationsprogramm\backups\

# Neue JAR entfernen und alte zurückkopieren (Beispiel)
Remove-Item "C:\Kalkulationsprogramm\*.jar"
Copy-Item "C:\Kalkulationsprogramm\backups\Kalkulationsprogramm-<Version>.jar.<Zeitstempel>.bak" `
          "C:\Kalkulationsprogramm\Kalkulationsprogramm-<Version>.jar"

# Server neu starten
C:\Kalkulationsprogramm\scripts\restart-kalkulationsprogramm.ps1 -ServerPort 8080
```

Hat die neue Version schon Datenbank-Migrationen ausgeführt, gehört auch die
Datenbank-Sicherung von vor dem Update zurück – sonst passt die alte JAR womöglich nicht zur
Datenbank.

## 🔄 Typischer Update-Workflow

1. **Änderung über einen Pull Request** nach `main` bringen
2. **Update-Skript ausführen** (im Repository unter `deployment\scripts`):
   ```powershell
   .\UPDATE.bat
   ```
3. **Testen** der neuen Version

## 📝 Anpassungen

Repository- und Produktionspfad stehen fest am Anfang von `update-production.ps1`:
```powershell
$REPO_PATH = "C:\Github\ERP-System-fuer-Handwerksbetriebe"
$PRODUCTION_PATH = "C:\Kalkulationsprogramm"
```

Der JAR-Name wird nicht mehr eingetragen: Das Skript nimmt die neueste lauffähige JAR aus
`target\`. Einen Windows-Dienst gibt es nicht – der Server läuft als `java -jar`-Prozess, das
Skript erkennt ihn am Port 8080.
