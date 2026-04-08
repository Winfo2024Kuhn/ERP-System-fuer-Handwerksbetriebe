# =====================================================================
#  ERP-System – Netzwerkfreigabe + OpenFileLauncher Update
#  Als Administrator ausfuehren!
# =====================================================================

$ErrorActionPreference = 'Stop'
$UploadsPath  = 'C:\dev\ERP-System-fuer-Handwerksbetriebe\uploads'
$ShareName    = 'ERP-Uploads'
$LauncherSrc  = 'C:\dev\ERP-System-fuer-Handwerksbetriebe\deployment\openfile-launcher\launcher.ps1'
$LauncherDest = 'C:\Program Files\OpenFileLauncher\launcher.ps1'

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "  ERP Netzwerkfreigabe + Launcher Setup" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

# 1) Netzwerkfreigabe erstellen
Write-Host "`n[1/3] Netzwerkfreigabe '$ShareName' einrichten..."
$existing = Get-SmbShare -Name $ShareName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "  Freigabe '$ShareName' existiert bereits -> wird entfernt und neu erstellt"
    Remove-SmbShare -Name $ShareName -Force
}
New-SmbShare -Name $ShareName -Path $UploadsPath -FullAccess $env:USERNAME | Out-Null
Write-Host "  OK: \\$env:COMPUTERNAME\$ShareName -> $UploadsPath" -ForegroundColor Green

# 2) Freigabe testen
Write-Host "`n[2/3] Freigabe testen..."
$testPath = "\\$env:COMPUTERNAME\$ShareName\CADdrawings"
if (Test-Path $testPath) {
    Write-Host "  OK: $testPath erreichbar" -ForegroundColor Green
} else {
    Write-Host "  WARNUNG: $testPath nicht erreichbar – Freigabe existiert aber Unterordner fehlt?" -ForegroundColor Yellow
}

# 3) Launcher.ps1 aktualisieren
Write-Host "`n[3/3] OpenFileLauncher aktualisieren..."
if (Test-Path $LauncherSrc) {
    Copy-Item -Path $LauncherSrc -Destination $LauncherDest -Force
    Write-Host "  OK: launcher.ps1 aktualisiert" -ForegroundColor Green
} else {
    Write-Host "  FEHLER: Quelldatei nicht gefunden: $LauncherSrc" -ForegroundColor Red
    exit 1
}

Write-Host "`n=====================================================" -ForegroundColor Cyan
Write-Host "  Setup abgeschlossen!" -ForegroundColor Green
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Netzwerkpfad: \\$env:COMPUTERNAME\ERP-Uploads\CADdrawings"
Write-Host "Jetzt Backend neu starten, dann koennen .xlsm und .sza"
Write-Host "Dateien direkt geoeffnet werden (keine Kopien mehr)."
Write-Host ""
Read-Host "Druecken Sie Enter zum Beenden"
