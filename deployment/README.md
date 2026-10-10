# Deployment Scripts - Handwerkerprogramm

Dieses Verzeichnis enthält alle Scripts und Dokumentation für das Deployment des Kalkulationsprogramms.

## Docker beim Kunden (Windows-PC oder Linux-Server) mit automatischen Updates – empfohlen

Ein Doppelklick (Windows) bzw. ein Befehl (Linux) richtet alles ein: Datenbank
(PostgreSQL), ERP und Updater. Das ERP startet nach jedem Neustart von selbst, holt
sich neue Versionen nachts um 3 Uhr, sichert vorher die Datenbank und rollt bei
Problemen automatisch zurück – mit Handy-Nachricht.

→ **[docker-server/README.md](./docker-server/README.md)**

---

## Windows-Skripte (Sicherung, Start als JAR)

Ältere Skripte aus der Zeit vor `docker-server/`. Weiter sinnvoll ist vor allem
`backup-database.ps1`: Es sichert die Datenbank des docker-server (Container `erp-postgres`)
per `pg_dump` direkt im Container.

**Achtung beim Start als JAR:** Die Datenbank des docker-server ist bewusst nicht von außen
erreichbar (kein freigegebener Port, nur das interne Docker-Netz). Ein JAR auf dem Rechner
kommt an sie nicht heran – für diesen Weg braucht es ein eigenes PostgreSQL, z. B. einen
Container mit `-p 127.0.0.1:5432:5432` (siehe `docs/INSTALLATION.md`, Variante 3). Sicherung
und Update dann auf diesen Container zeigen lassen: `update-production.ps1 -DbContainer <name>
-DbUser erp_user` bzw. `backup-database.ps1 -DockerPostgresContainer <name> -DockerPostgresUser
erp_user`. Ohne gelungene Sicherung bricht das Update ab (bewusst ohne: `-SkipBackup`).

### Scripts (`scripts/`)

| Script | Beschreibung |
|--------|--------------|
| `backup-database.ps1` | Erstellt automatische Datenbank-Backups auf externe Festplatte E:\ |
| `start-kalkulationsprogramm.ps1` | Startet den Server mit Health-Check |
| `restart-kalkulationsprogramm.ps1` | Führt sauberen Server-Neustart durch |
| `install-scheduled-tasks.ps1` | Installiert alle Windows Scheduled Tasks |
| `start-server.bat` | Windows Batch-Skript zum schnellen Start des Servers (Kurzform) |

### Dokumentation

- **[DEPLOYMENT_README.md](./DEPLOYMENT_README.md)** - Vollständige Installations- und Konfigurationsanleitung

## Schnellstart

### 1. Repository auf Produktions-Server klonen/kopieren

```powershell
# Falls Git verfügbar
git clone <repository-url> C:\Temp\Handwerkerprogramm

# Oder: Dateien manuell kopieren
```

### 2. Installation ausführen

```powershell
# PowerShell als Administrator öffnen
cd C:\Temp\Handwerkerprogramm

# Scheduled Tasks installieren
.\deployment\scripts\install-scheduled-tasks.ps1

# Optional: Mit wöchentlichem Neustart
.\deployment\scripts\install-scheduled-tasks.ps1 -EnableWeeklyRestart
```

### 3. JAR-Datei deployen

```powershell
# Kompilierte JAR nach C:\Kalkulationsprogramm kopieren
Copy-Item "target\Kalkulationsprogramm-<Version>.jar" "C:\Kalkulationsprogramm\Kalkulationsprogramm.jar"
```

### 4. Server manuell starten (Test)

```powershell
cd C:\Kalkulationsprogramm\scripts
.\start-kalkulationsprogramm.ps1
```

## Voraussetzungen

- Windows Server oder Windows 10/11
- Java 23+
- Docker Desktop mit PostgreSQL: für die Sicherung der Container `erp-postgres` (aus `docker-server/`), für den Start als JAR ein eigenes PostgreSQL mit Port auf 127.0.0.1 (siehe oben)
- Externe Festplatte E:\ für Backups
- Administrator-Rechte für Installation

## Automatische Prozesse nach Installation

✓ **Datenbank-Backup**: Täglich um 02:00 Uhr  
✓ **Server-Autostart**: Bei jedem System-Neustart  
✓ **Backup-Rotation**: Alte Backups > 30 Tage werden automatisch gelöscht

## Weitere Informationen

Siehe [DEPLOYMENT_README.md](./DEPLOYMENT_README.md) für:
- Detaillierte Installationsanleitung
- Konfigurationsoptionen
- Troubleshooting
- Backup-Wiederherstellung
- Monitoring und Wartung
