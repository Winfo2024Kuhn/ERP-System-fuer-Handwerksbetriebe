[CmdletBinding()]
param([string]$EnvFile = (Join-Path $PSScriptRoot '.env'))
$ErrorActionPreference = 'Stop'
& docker compose --env-file $EnvFile -f (Join-Path $PSScriptRoot 'compose.yaml') --profile quick --profile domain stop caddy tunnel
if ($LASTEXITCODE -ne 0) { throw 'Oeffentlicher Zugang konnte nicht vollstaendig gestoppt werden.' }
Write-Host 'Oeffentlicher Zugang gestoppt. ERP, Datenbank und Daten-Volumes bleiben bestehen.'
