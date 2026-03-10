# OpenFile Launcher - Installations-Paket

Dieses Paket ermöglicht die einfache Installation des OpenFile Launchers auf Client-Rechnern.

## 📦 Inhalt

- `launcher.ps1` - Das Hauptskript zum Öffnen von Dateien über openfile://-Links
- `install-openfile-launcher.ps1` - Automatisches Installations-Skript
- `uninstall-openfile-launcher.ps1` - Deinstallations-Skript
- `README.md` - Diese Datei

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
2. **Rechtsklick** auf `install-openfile-launcher.ps1`
3. Wählen Sie **"Mit PowerShell ausführen"** (PowerShell öffnet sich automatisch als Administrator)
4. **ODER:** Öffnen Sie PowerShell als Administrator und führen Sie aus:
   ```powershell
   cd "C:\Pfad\zum\Ordner\openfile-launcher"
   .\install-openfile-launcher.ps1
   ```
5. Fertig! ✅

### Methode 3: Manuelle Installation (nicht empfohlen)

1. Kopieren Sie `launcher.ps1` nach `C:\Program Files (x86)\OpenFileLauncher\`
2. Führen Sie `install-openfile-launcher.ps1` als Administrator aus

## 🗑️ Deinstallation

**Rechtsklick** auf `uninstall-openfile-launcher.ps1` → **"Als Administrator ausführen"**

## 🔧 Massenverteilung (für IT-Admins)

### Option A: Netzwerkfreigabe

1. Legen Sie diesen Ordner auf einer Netzwerkfreigabe ab
2. Erstellen Sie auf jedem Client ein Skript, das die Installation von der Freigabe ausführt:

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
2. Geben Sie diese URL in die Adresszeile ein:
   ```
   openfile://open?path=%5C%5CSERVER_PC%5CCADdrawings%5Ctest.txt
   ```
3. Wenn ein Fenster kurz aufploppt und sich wieder schließt, prüfen Sie das Log:
   ```
   C:\ProgramData\OpenFileLauncher\launcher.log
   ```

## 🔍 Fehlersuche

### "Skript kann nicht ausgeführt werden" (Execution Policy)

Führen Sie aus (als Administrator):
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope LocalMachine
```

### "Zugriff verweigert"

Stellen Sie sicher, dass PowerShell **als Administrator** ausgeführt wird.

### Datei wird nicht geöffnet

1. Prüfen Sie das Log: `C:\ProgramData\OpenFileLauncher\launcher.log`
2. Überprüfen Sie die Netzwerkfreigabe `\\SERVER_PC\CADdrawings`
3. Stellen Sie sicher, dass der Benutzer Zugriff auf die Freigabe hat

## 📝 Lizenz

Internes Tool für Example Company
