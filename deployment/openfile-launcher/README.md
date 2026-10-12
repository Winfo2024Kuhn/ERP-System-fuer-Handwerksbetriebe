# OpenFile Launcher - Installations-Paket

Dieses Paket ermöglicht die einfache Installation des OpenFile Launchers auf Client-Rechnern
(Windows). Der Launcher registriert das Schema `openfile://`. Klickt man im ERP auf eine
CAD-Zeichnung oder Excel-Datei, öffnet er sie direkt aus der Netzwerkfreigabe im passenden
Programm (z. B. TENADO, HiCAD, Excel) statt eine Kopie herunterzuladen.

## 📦 Inhalt

| Datei | Zweck |
| --- | --- |
| `launcher.ps1` | Das Hauptskript zum Öffnen von Dateien über `openfile://`-Links |
| `INSTALL.bat` | Startet `install-openfile-launcher.ps1` per Doppelklick (als Administrator) |
| `install-openfile-launcher.ps1` | Installations-Skript: kopiert `launcher.ps1` nach `C:\Program Files\OpenFileLauncher` und registriert `openfile://` |
| `uninstall-openfile-launcher.ps1` | Deinstallations-Skript |
| `Setup-Freigabe.ps1` | Für den ERP-Rechner: Netzwerkfreigabe und erlaubte Pfade einrichten (siehe unten) |
| `OpenFileLauncher-Setup.ps1`, `OpenFileLauncher-Install.bat` | Setup-Paket, das das ERP zum Herunterladen anbietet (siehe unten) |
| `SETUP-ERSTELLEN.md` und die `BUILD-*`-/`*.iss`-/`*.sed`-Dateien | Eine einzelne Setup-Datei bauen, siehe [SETUP-ERSTELLEN.md](SETUP-ERSTELLEN.md) |
| `README.md` | Diese Datei |

## 🚀 Installation auf einem Client

### ⭐ Methode 1: Batch-Datei (am einfachsten!)

1. **Kopieren Sie diesen gesamten Ordner** auf den Ziel-PC (z.B. auf den Desktop oder in einen temporären Ordner)
2. **Rechtsklick** auf `INSTALL.bat`
3. Wählen Sie **"Als Administrator ausführen"**
4. Bestätigen Sie die UAC-Sicherheitsabfrage mit "Ja"
5. Warten Sie auf die Erfolgsmeldung
6. Fertig! ✅

### Methode 2: PowerShell-Skript

1. **Kopieren Sie diesen gesamten Ordner** auf den Ziel-PC
2. Öffnen Sie PowerShell **als Administrator** (das Skript fordert die Rechte nicht selbst an,
   sondern bricht ohne sie ab) und führen Sie aus:
   ```powershell
   cd "C:\Pfad\zum\Ordner\openfile-launcher"
   .\install-openfile-launcher.ps1
   ```
3. Fertig! ✅

## 🖥️ Auf dem ERP-Rechner: Freigabe und erlaubte Pfade

Der Launcher öffnet nur Dateien unterhalb der **erlaubten Pfade**. Er liest sie aus
`config.json` neben `launcher.ps1` (Eintrag `allowedRoots`). Fehlt die Datei, gilt ein fest
eingetragener Pfad aus `launcher.ps1`, der in der Regel nicht passt.

`Setup-Freigabe.ps1` (als Administrator, aus dem Repository-Ordner
`deployment\openfile-launcher` heraus) erledigt das auf dem ERP-Rechner:

1. liest `hicad.network-url` (UNC-Pfad, z. B. `\\SERVER\CADdrawings`) aus
   `src\main\resources\application-local.properties`,
2. gibt den Ordner `uploads` im Repository-Stamm unter dem Freigabenamen aus diesem Pfad frei –
   **mit Ändern-Recht für „Jeder“**,
3. schreibt `C:\Program Files\OpenFileLauncher\config.json` mit diesem Pfad als erlaubtem Pfad,
4. kopiert die aktuelle `launcher.ps1` dorthin.

Auf den Clients muss `config.json` ebenfalls neben `launcher.ps1` liegen. Das
Installations-Skript kopiert sie nicht mit.

## 📥 Setup-Download aus dem ERP

Das ERP bietet unter `/api/system/openfile-launcher-setup` ein ZIP mit
`OpenFileLauncher-Install.bat` und `OpenFileLauncher-Setup.ps1` an. Es nimmt beide aus dem
Ordner `downloads` im Arbeitsverzeichnis des ERP (Einstellung `app.downloads.path`). Dorthin
kopiert sie beim JAR-Betrieb `deployment/scripts/update-production.ps1`. Im Docker-Image gibt
es diesen Ordner nicht. Dieses Setup installiert nach `C:\Program Files (x86)\OpenFileLauncher`
und bringt eine eingebettete, ältere Fassung von `launcher.ps1` mit (ohne `config.json`).
Wie man es neu erzeugt, steht in [SETUP-ERSTELLEN.md](SETUP-ERSTELLEN.md).

## 🗑️ Deinstallation

PowerShell **als Administrator** öffnen und `uninstall-openfile-launcher.ps1` ausführen. Es
entfernt das Schema `openfile://` und den Ordner `C:\Program Files (x86)\OpenFileLauncher`.
Wurde mit `install-openfile-launcher.ps1` installiert, liegen die Dateien in
`C:\Program Files\OpenFileLauncher` – diesen Ordner dann von Hand löschen.

## 🔧 Massenverteilung (für IT-Admins)

### Option A: Netzwerkfreigabe

1. Legen Sie diesen Ordner auf einer Netzwerkfreigabe ab
2. Führen Sie die Installation auf jedem Client als Administrator von der Freigabe aus:

```powershell
# Als Administrator ausführen
\\SERVER\share\openfile-launcher\install-openfile-launcher.ps1
```

### Option B: Group Policy / SCCM

Verwenden Sie `install-openfile-launcher.ps1` als Teil eines Software-Deployments über:
- **Group Policy** (Startup-Skript)
- **Microsoft Endpoint Manager** (SCCM/Intune)
- **PDQ Deploy**

Beispiel für GPO:
1. Öffnen Sie die **Gruppenrichtlinienverwaltung**
2. Erstellen Sie eine neue GPO
3. Computerkonfiguration → Richtlinien → Windows-Einstellungen → Skripts → Autostart
4. Fügen Sie `install-openfile-launcher.ps1` hinzu

Hinweis: Das Skript wartet am Ende auf einen Tastendruck (`pause`). Für eine Verteilung ohne
Benutzer muss das berücksichtigt werden.

### Option C: Remote-Installation mit PowerShell

```powershell
# Beispiel: Installation auf mehreren Rechnern
$computers = @("PC01", "PC02", "PC03")

foreach ($pc in $computers) {
    Write-Host "Installiere auf $pc..."

    # Dateien kopieren
    $dest = "\\$pc\C$\Temp\openfile-launcher"
    Copy-Item -Path ".\*" -Destination $dest -Recurse -Force

    # Remote-Installation ausführen
    Invoke-Command -ComputerName $pc -ScriptBlock {
        & "C:\Temp\openfile-launcher\install-openfile-launcher.ps1"
    }
}
```

## ✅ Verifikation

Nach der Installation können Sie testen, ob alles funktioniert:

1. Öffnen Sie einen **Browser**
2. Geben Sie diese URL in die Adresszeile ein (Pfad an die eigene Freigabe anpassen):
   ```
   openfile://open?path=%5C%5CSERVER_PC%5CCADdrawings%5Ctest.txt
   ```
3. Wenn ein Fenster kurz aufploppt und sich wieder schließt, prüfen Sie das Log:
   ```
   C:\ProgramData\OpenFileLauncher\launcher.log
   ```
   Steht dort `ERR out of allowed roots`, passt `config.json` nicht zur Freigabe.

## 🔍 Fehlersuche

### "Skript kann nicht ausgeführt werden" (Execution Policy)

`INSTALL.bat` umgeht die Ausführungsrichtlinie selbst. Beim direkten Aufruf (als Administrator):
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope LocalMachine
```

### "Zugriff verweigert"

Stellen Sie sicher, dass PowerShell **als Administrator** ausgeführt wird.

### Datei wird nicht geöffnet

1. Prüfen Sie das Log: `C:\ProgramData\OpenFileLauncher\launcher.log`
2. Prüfen Sie, ob der Pfad unter einem erlaubten Pfad aus `config.json` liegt
3. Überprüfen Sie die Netzwerkfreigabe (z. B. `\\SERVER_PC\CADdrawings`)
4. Stellen Sie sicher, dass der Benutzer Zugriff auf die Freigabe hat

## 📝 Lizenz

Teil des Projekts „ERP-System für Handwerksbetriebe“, es gilt die Lizenz des Repositorys
(AGPL-3.0, siehe `LICENSE`).
