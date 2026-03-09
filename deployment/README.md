# Deployment Scripts - Handwerkerprogramm

Dieses Verzeichnis enthält alle Scripts und Dokumentation für das Deployment des Kalkulationsprogramms auf dem Produktions-Server.

## Übersicht

### Scripts (`scripts/`)

| Script | Beschreibung |
|--------|--------------|
| `backup-database.ps1` | Erstellt automatische Datenbank-Backups auf externe Festplatte E:\ |
| `start-kalkulationsprogramm.ps1` | Startet den Server mit Health-Check |
| `restart-kalkulationsprogramm.ps1` | Führt sauberen Server-Neustart durch |
| `install-scheduled-tasks.ps1` | Installiert alle Windows Scheduled Tasks |

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
Copy-Item "target\Kalkulationsprogramm-1.0.0.jar" "C:\Kalkulationsprogramm\Kalkulationsprogramm.jar"
```

### 4. Server manuell starten (Test)

```powershell
cd C:\Kalkulationsprogramm\scripts
.\start-kalkulationsprogramm.ps1
```

## Voraussetzungen

- Windows Server oder Windows 10/11
- Java 23+
- MySQL Client Tools (mysqldump)
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
