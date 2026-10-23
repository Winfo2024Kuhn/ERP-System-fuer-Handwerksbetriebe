# =====================================================================
#  ERP-System - Gemeinsamen Ordner auch ueber Tailscale erreichbar machen
#  Auf dem Rechner mit dem freigegebenen Ordner als Administrator ausfuehren.
#
#  Windows laesst die Datei- und Druckerfreigabe (SMB, Port 445) meist nur aus
#  dem lokalen Netz zu. Diese Regel erlaubt zusaetzlich Zugriffe aus dem
#  eigenen Tailscale-Netz (100.64.0.0/10 und fd7a:115c:a1e0::/48), damit
#  "Dateien direkt am PC oeffnen" auch von unterwegs funktioniert.
#  Das Internet bleibt aussen vor: Tailscale-Adressen erreicht nur, wer im
#  eigenen Tailnet angemeldet ist. Die Ordnerrechte (wer lesen/aendern darf)
#  aendert das Skript nicht.
#
#  Rueckgaengig: .\Freigabe-Tailscale.ps1 -Entfernen
# =====================================================================

param(
    [switch]$Entfernen
)

$ErrorActionPreference = 'Stop'
$RegelName = 'ERP - Datei-Ordner ueber Tailscale (SMB)'

$istAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $istAdmin) {
    Write-Host 'Bitte als Administrator ausfuehren (Rechtsklick -> "Als Administrator ausfuehren").' -ForegroundColor Red
    Read-Host 'Enter zum Beenden' | Out-Null
    exit 1
}

try {
    Get-NetFirewallRule -DisplayName $RegelName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    if ($Entfernen) {
        Write-Host 'Regel entfernt. Der Ordner ist nur noch im lokalen Netz erreichbar.' -ForegroundColor Green
    } else {
        # Nur ueber den Tailscale-Adapter: 100.64.0.0/10 nutzen auch manche Hotspots und
        # Mobilfunk-Router (CGNAT) - dort soll der Ordner nicht offen stehen.
        $adapter = Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { $_.InterfaceDescription -like 'Tailscale*' -or $_.Name -like 'Tailscale*' } | Select-Object -First 1
        if (-not $adapter) {
            throw 'Tailscale ist auf diesem Rechner nicht installiert oder nicht verbunden.'
        }
        New-NetFirewallRule -DisplayName $RegelName -Direction Inbound -Action Allow -Protocol TCP `
            -LocalPort 445 -RemoteAddress @('100.64.0.0/10', 'fd7a:115c:a1e0::/48') `
            -InterfaceAlias $adapter.Name | Out-Null
        Write-Host "Regel '$RegelName' eingerichtet." -ForegroundColor Green
        Write-Host 'Unterwegs erreichen die PCs den Ordner jetzt ueber den Tailscale-Namen dieses Rechners.'
        Write-Host 'Beim ersten Mal fragt der PC nach Benutzer und Passwort fuer den Ordner.'
    }
} catch {
    Write-Host "FEHLER: $($_.Exception.Message)" -ForegroundColor Red
    Read-Host 'Enter zum Beenden' | Out-Null
    exit 1
}
Read-Host 'Enter zum Beenden' | Out-Null
