# ====================================================================
# OpenFile Launcher - All-in-One Installations-Skript
# ====================================================================
# Dieses einzelne Skript kann als .exe kompiliert oder direkt
# als PowerShell-Skript ausgeführt werden.
#
# Als Administrator ausführen!
# ====================================================================

param()

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName PresentationFramework

$ErrorActionPreference = 'Stop'

# Prüfe Admin-Rechte
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    [System.Windows.Forms.MessageBox]::Show(
        "Dieses Setup muss als Administrator ausgeführt werden!`n`nBitte:`n1. Rechtsklick auf die Datei`n2. 'Als Administrator ausführen' wählen",
        "Administrator-Rechte erforderlich",
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Warning
    )
    exit 1
}

# Willkommens-Nachricht
$result = [System.Windows.Forms.MessageBox]::Show(
    "Dieser Assistent installiert den OpenFile Launcher auf diesem Computer.`n`nDer OpenFile Launcher ermöglicht das Öffnen von CAD-Zeichnungen und Excel-Dateien über openfile://-Links aus Ihrer Web-Anwendung.`n`nMöchten Sie fortfahren?",
    "OpenFile Launcher Setup",
    [System.Windows.Forms.MessageBoxButtons]::YesNo,
    [System.Windows.Forms.MessageBoxIcon]::Information
)

if ($result -eq [System.Windows.Forms.DialogResult]::No) {
    exit 0
}

try {
    # Embedded launcher.ps1 content (Base64)
    $launcherBase64Content = @'
INSERT_LAUNCHER_BASE64_HERE
'@

    # Zielverzeichnis
    $targetDir = "C:\Program Files (x86)\OpenFileLauncher"
    
    # Schritt 1: Verzeichnis erstellen
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }

    # Schritt 2: launcher.ps1 dekodieren und speichern  
    try {
        $launcherBytes = [Convert]::FromBase64String($launcherBase64Content)
        $launcherContent = [System.Text.Encoding]::UTF8.GetString($launcherBytes)
        $launcherPath = Join-Path $targetDir "launcher.ps1"
        Set-Content -Path $launcherPath -Value $launcherContent -Encoding UTF8 -Force
    } catch {
        throw "Fehler beim Erstellen von launcher.ps1: $($_.Exception.Message)"
    }

    # Schritt 3: Registry - openfile:// Schema registrieren
    if (-not (Test-Path "HKCR:\")) {
        New-PSDrive -Name HKCR -PSProvider Registry -Root HKEY_CLASSES_ROOT | Out-Null
    }

    $schemaPath = "HKCR:\openfile"
    
    if (-not (Test-Path $schemaPath)) {
        New-Item -Path $schemaPath -Force | Out-Null
    }
    Set-ItemProperty -Path $schemaPath -Name "(Default)" -Value "URL:OpenFile Protocol" -Type String
    Set-ItemProperty -Path $schemaPath -Name "URL Protocol" -Value "" -Type String

    $iconPath = "$schemaPath\DefaultIcon"
    if (-not (Test-Path $iconPath)) {
        New-Item -Path $iconPath -Force | Out-Null
    }
    Set-ItemProperty -Path $iconPath -Name "(Default)" -Value "shell32.dll,0" -Type String

    $shellPath = "$schemaPath\shell"
    $openPath = "$shellPath\open"
    if (-not (Test-Path $openPath)) {
        New-Item -Path $openPath -Force | Out-Null
    }

    $commandPath = "$openPath\command"
    if (-not (Test-Path $commandPath)) {
        New-Item -Path $commandPath -Force | Out-Null
    }
    
    $command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$launcherPath`" `"%1`""
    Set-ItemProperty -Path $commandPath -Name "(Default)" -Value $command -Type String

    # Schritt 4: Log-Verzeichnis erstellen
    $logDir = "C:\ProgramData\OpenFileLauncher"
    if (-not (Test-Path $logDir)) {
        New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    }

    # Erfolgsmeldung
    [System.Windows.Forms.MessageBox]::Show(
        "OpenFile Launcher wurde erfolgreich installiert!`n`nInstalliert in:`n$targetDir`n`nSie können jetzt openfile://-Links aus Ihrer Web-Anwendung verwenden.",
        "Installation erfolgreich",
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Information
    )
    
    exit 0

} catch {
    [System.Windows.Forms.MessageBox]::Show(
        "Fehler bei der Installation:`n`n$($_.Exception.Message)`n`nBitte kontaktieren Sie den Administrator.",
        "Installationsfehler",
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Error
    )
    exit 1
}
