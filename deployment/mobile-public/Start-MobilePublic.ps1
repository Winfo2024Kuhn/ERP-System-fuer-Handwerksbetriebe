[CmdletBinding()]
param(
    [ValidateSet('quick', 'domain')][string]$Mode = 'quick',
    [string]$EnvFile = (Join-Path $PSScriptRoot '.env'),
    [string]$ErpProjectName = '',
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

# Never echo resolved Compose config or captured Docker output: it can contain secrets.
function Docker-Result([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $text = (& docker @Arguments 2>&1 | Out-String)
        $code = $LASTEXITCODE
        return @{ Code = $code; Text = $text }
    } finally { $ErrorActionPreference = $previous }
}
function Docker-Checked([string[]]$Arguments, [string]$Operation) {
    $result = Docker-Result $Arguments
    if ($result.Code -ne 0) { throw "$Operation fehlgeschlagen. Keine vertraulichen Ausgaben angezeigt." }
    return $result.Text
}
function Assert-Credential([string]$Name, [string]$Value, [int]$MinLength = 1) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value.Length -lt $MinLength -or
        $Value -match '(?i)CHANGE.?ME|REPLACE_|YOUR_|DEIN_|changeme|sicheresPasswort123|^123456$') {
        throw "$Name fehlt oder ist ein Beispielwert. Bitte lokal konfigurieren; Wert wird nicht ausgegeben."
    }
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop mit Linux-Containern wird benoetigt.' }
if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) { throw 'Lokale .env fehlt. .env.example kopieren und bestehende Zugangsdaten eintragen.' }
$version = (Docker-Checked @('compose', 'version', '--short') 'Compose-Version').Trim().TrimStart('v').Split('-')[0]
if ([version]$version -lt [version]'2.24.4') { throw 'Docker Compose ab 2.24.4 ist fuer !override erforderlich.' }
$null = Docker-Checked @('info', '--format', '{{.ServerVersion}}') 'Docker-Verbindung'
$existing = Docker-Result @('inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', 'kalkulationsprogramm-app')
$existingInstallation = $existing.Code -eq 0 -and -not [string]::IsNullOrWhiteSpace($existing.Text)
if ($existingInstallation) {
    $detected = $existing.Text.Trim()
    if ($ErpProjectName -and $ErpProjectName -ne $detected) { throw 'ERP-Projektname passt nicht zum vorhandenen Container. Daten-Volumes duerfen nicht gewechselt werden.' }
    $ErpProjectName = $detected
}
if (-not $ErpProjectName) { throw 'Kein vorhandener ERP-Container erkannt. Bei einer bewusst neuen Installation -ErpProjectName angeben.' }
if ($ErpProjectName -notmatch '^[a-z0-9][a-z0-9_-]*$') { throw 'Ungueltiger Compose-Projektname.' }
$erp = @('compose', '--env-file', $EnvFile, '--project-name', $ErpProjectName,
    '-f', (Join-Path $RepoRoot 'docker-compose.yml'), '-f', (Join-Path $PSScriptRoot 'erp.override.yaml'))
$public = @('compose', '--env-file', $EnvFile, '-f', (Join-Path $PSScriptRoot 'compose.yaml'))
try {
    $erpConfig = (Docker-Checked ($erp + @('config', '--format', 'json')) 'ERP-Konfigurationspruefung') | ConvertFrom-Json
    $publicConfig = (Docker-Checked ($public + @('--profile', $Mode, 'config', '--format', 'json')) 'Proxy-Konfigurationspruefung') | ConvertFrom-Json
} catch { throw 'Compose-Konfiguration konnte nicht ausgewertet werden. Keine Konfigurationswerte ausgegeben.' }
$appEnv = $erpConfig.services.app.environment
$dbEnv = $erpConfig.services.mysql.environment
Assert-Credential 'MYSQL_ROOT_PASSWORD' $dbEnv.MYSQL_ROOT_PASSWORD 16
Assert-Credential 'MYSQL_PASSWORD' $dbEnv.MYSQL_PASSWORD 16
Assert-Credential 'APP_DB_PASS' $appEnv.APP_DB_PASS 16
if (-not $existingInstallation -or $appEnv.APP_ADMIN_USER -or $appEnv.APP_ADMIN_PASS) {
    Assert-Credential 'APP_ADMIN_USER' $appEnv.APP_ADMIN_USER
    Assert-Credential 'APP_ADMIN_PASS' $appEnv.APP_ADMIN_PASS 16
}
if ($appEnv.APP_DB_URL -match 'jdbc:mysql://mysql:' -and
    ($appEnv.APP_DB_PASS -ne $dbEnv.MYSQL_PASSWORD -or $appEnv.APP_DB_USER -ne $dbEnv.MYSQL_USER)) {
    throw 'App- und MySQL-Zugangsdaten stimmen fuer die interne Datenbank nicht ueberein.'
}
$bind = $erpConfig.services.app.ports[0].host_ip
$parsedBind = $null
if (-not [System.Net.IPAddress]::TryParse($bind, [ref]$parsedBind) -or
    $parsedBind.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
    $bind -ne $parsedBind.ToString() -or $bind -notmatch '^(127\.|10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.|100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.)') {
    throw 'ERP_PRIVATE_BIND_IP muss eine konkrete IPv4-Adresse von Loopback, LAN oder Tailscale sein.'
}
if ($publicConfig.services.gateway.environment.ERP_UPSTREAM -ne 'app:8080') { throw 'Dieses Windows-Startskript erwartet das isolierte Docker-Backend app:8080.' }
if ($Mode -eq 'domain') {
    $domain = $publicConfig.services.caddy.environment.MOBILE_DOMAIN
    if ($domain -notmatch '^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$' -or $domain -match '(?i)\.invalid$|\.example$|example\.(com|org|net)$') {
        throw 'MOBILE_DOMAIN muss die eigene feste Domain enthalten.'
    }
    if ($appEnv.ZEITERFASSUNG_URL -ne "https://$domain") {
        throw 'ZEITERFASSUNG_URL muss fuer feste Domains genau https://MOBILE_DOMAIN entsprechen (ohne Pfad oder abschliessenden Schraegstrich).'
    }
}
Write-Host 'Vorpruefung bestanden: private ERP-Ports, enge Proxy-Adresse und eigene Zugangsdaten.'
if ($ValidateOnly) { return }

$network = Docker-Result @('network', 'inspect', 'erp-mobile-backend')
if ($network.Code -eq 0) {
    $details = @($network.Text | ConvertFrom-Json)[0]
    if (-not $details.Internal -or $details.IPAM.Config[0].Subnet -ne '172.30.51.0/29') {
        throw 'Vorhandenes erp-mobile-backend hat abweichende Netzregeln. Keine automatische Aenderung.'
    }
} else {
    $null = Docker-Checked @('network', 'create', '--internal', '--subnet', '172.30.51.0/29', 'erp-mobile-backend') 'Isoliertes Backendnetz'
}
# Switching profiles must not leave the old public entry point running.
$null = Docker-Checked ($public + @('--profile', 'quick', '--profile', 'domain', 'stop', 'caddy', 'tunnel')) 'Bisherigen oeffentlichen Zugang stoppen'
$null = Docker-Checked ($erp + @('up', '-d', '--no-build', 'mysql', 'qdrant', 'app')) 'ERP mit privaten Portbindungen starten'
$null = Docker-Checked ($public + @('up', '-d', '--wait', 'gateway')) 'Mobiles Gateway starten'
$protected = $false
for ($attempt = 0; $attempt -lt 24; $attempt++) {
    $probe = Docker-Result ($public + @('exec', '-T', 'gateway', 'wget', '-S', '--spider',
        '--header=X-ERP-Public-Mobile: 1', 'http://app:8080/api/auth/bootstrap-status'))
    if ($probe.Text -match 'HTTP/1\.[01] 404\b' -and $probe.Text -match '(?im)^\s*X-ERP-Public-Mobile:\s*1\s*$') {
        $tokenProbe = Docker-Result ($public + @('exec', '-T', 'gateway', 'wget', '-S', '--spider',
            '--header=X-ERP-Public-Mobile: 1', 'http://app:8080/api/mitarbeiter/by-token/malformed'))
        # 429 also proves the token filter is active after repeated startup checks.
        if ($tokenProbe.Text -match 'HTTP/1\.[01] (401|429)\b') {
            $bootstrap = Docker-Result ($public + @('exec', '-T', 'gateway', 'wget', '-q', '-O', '-',
                'http://app:8080/api/auth/bootstrap-status'))
            try { $setup = $bootstrap.Text | ConvertFrom-Json } catch { $setup = $null }
            if ($bootstrap.Code -eq 0 -and $setup.hasLoginUsers -eq $true -and $setup.setupRequired -eq $false) {
                $protected = $true
                break
            }
        }
    }
    Start-Sleep -Seconds 5
}
if (-not $protected) { throw 'Backend bestaetigt Public-Mobile-Schutz und abgeschlossene Ersteinrichtung nicht. Oeffentlicher Proxy/Tunnel bleibt gestoppt. Zuerst die neue App-Version bereitstellen.' }
$target = if ($Mode -eq 'domain') { 'caddy' } else { 'tunnel' }
$null = Docker-Checked ($public + @('--profile', $Mode, 'up', '-d', '--build', $target)) 'Oeffentlichen mobilen Zugang starten'
if ($Mode -eq 'quick') {
    Write-Host 'Test-Tunnel gestartet. Die temporaere Adresse erscheint mit:'
    Write-Host "docker compose --env-file `"$EnvFile`" -f `"$(Join-Path $PSScriptRoot 'compose.yaml')`" logs -f tunnel"
} else {
    Write-Host "Proxy gestartet. HTTPS von einem externen Geraet pruefen: https://$domain/zeiterfassung/"
}
