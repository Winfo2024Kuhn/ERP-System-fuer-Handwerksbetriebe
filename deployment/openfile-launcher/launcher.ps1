param([string]$Url, [switch]$Aktualisieren)

# Kanonische Fassung: src/main/resources/openfile-launcher/launcher.ps1. Das ERP liefert sie
# mit der Einrichtungs-Datei aus (Einstellungen -> Dateien) und aktualisiert sie selbst.
# deployment/openfile-launcher/launcher.ps1 ist eine Kopie fuer die alte Admin-Installation.
# Nur ASCII-Zeichen: Windows PowerShell 5 liest UTF-8 ohne BOM als ANSI.

# ===================== Setup =====================
Add-Type -AssemblyName System.Web
$ErrorActionPreference = 'Stop'

# Erlaubte Pfade aus config.json neben diesem Skript (kommt aus dem ERP bzw. von
# Setup-Freigabe.ps1). Ohne config.json oeffnet der Launcher nichts:
# Ein fest eingetragener Rechnername wuerde auf fremden Installationen nie passen.
$AllowedRoots = @()
$RootAliases  = @{}
$ErpAdresse   = $null
$Version      = ''
$configFile   = Join-Path $PSScriptRoot 'config.json'
$configError  = $null
if (Test-Path $configFile) {
  try {
    # Als UTF-8 lesen (mit oder ohne BOM): Get-Content nimmt in PowerShell 5 sonst ANSI
    $cfg = [IO.File]::ReadAllText($configFile) | ConvertFrom-Json
    if ($cfg.allowedRoots) { $AllowedRoots = @($cfg.allowedRoots) }
    if ($cfg.rootAliases) {
      foreach ($key in $cfg.rootAliases.PSObject.Properties.Name) {
        $RootAliases[$key] = $cfg.rootAliases.$key
      }
    }
    if ($cfg.erpAdresse) { $ErpAdresse = [string]$cfg.erpAdresse }
    if ($cfg.version) { $Version = [string]$cfg.version }
  } catch {
    $configError = $_.Exception.Message
  }
}

# Bevorzugter Laufwerksbuchstabe
$PreferredDrive = 'Z:'

# Optional: bekannter TENADO-Pfad (Fallback)
$TenadoExeFallback = 'C:\Program Files (x86)\TENADO\TENADO METALL 2D V22\TENADOMETALL22.exe'

# Spreadsheet-Endungen (Excel, CSV, ODS, ...)
$SpreadsheetExts = @('.xlsx','.xls','.xlsm','.xlsb','.xlt','.xltx','.xltm','.csv','.ods')

# ---- Logging: Konsole + Datei (im Benutzerordner, dort darf jeder schreiben) ----
$logDir = if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'OpenFileLauncher' } else { 'C:\ProgramData\OpenFileLauncher' }
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$log = Join-Path $logDir 'launcher.log'
function L([string]$m){
  $ts = "[{0:yyyy-MM-dd HH:mm:ss}] {1}" -f (Get-Date), $m
  try { Add-Content -Path $log -Value $ts } catch {}
  Write-Host $ts
}

# ===================== Selbst-Update =====================
# Fragt das ERP, ob es eine neuere Fassung (Skript oder erlaubte Ordner) gibt. Laeuft in einem
# eigenen, unsichtbaren Prozess, damit das Oeffnen der Datei nie darauf wartet.
# Sicherheit: nur ueber https, und nur was das ERP mit seinem eigenen Schluessel unterschrieben
# hat (oeffentlicher Teil kam mit der Einrichtung in config.json). Eine abgefangene oder
# umgeleitete Verbindung kann so keinen fremden Code auf den PC bringen.
function Test-Unterschrift([string]$paket, [string]$signaturB64) {
  if (-not $cfg -or -not $cfg.pruefSchluesselXml) { return $false }
  $rsa = New-Object System.Security.Cryptography.RSACryptoServiceProvider
  try {
    $rsa.FromXmlString([string]$cfg.pruefSchluesselXml)
    return $rsa.VerifyData([Text.Encoding]::UTF8.GetBytes($paket), 'SHA256', [Convert]::FromBase64String($signaturB64))
  } catch { return $false } finally { $rsa.Dispose() }
}

function Update-Launcher {
  if (-not $ErpAdresse) { L "Update: keine ERP-Adresse in config.json"; return }
  if (-not $ErpAdresse.StartsWith('https://')) { L "Update: nur ueber https - $ErpAdresse wird nicht gefragt"; return }
  try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $abfrage = $ErpAdresse.TrimEnd('/') + '/openfile-launcher/aktuell?system=windows' +
      '&version=' + [Uri]::EscapeDataString($Version) + '&adresse=' + [Uri]::EscapeDataString($ErpAdresse)
    $antwort = Invoke-WebRequest -Uri $abfrage -UseBasicParsing -TimeoutSec 15 -MaximumRedirection 0
    if ($antwort.StatusCode -ne 200) { L "Update: aktuell ($Version)"; return }
    # Bytes selbst als UTF-8 lesen: PowerShell 5 raet den Zeichensatz sonst falsch
    $neu = [Text.Encoding]::UTF8.GetString($antwort.RawContentStream.ToArray()) | ConvertFrom-Json
    $skript = [string]$neu.skript
    $configJson = [string]$neu.configJson
    if (-not $skript.StartsWith('param(') -or -not $configJson -or -not $neu.signatur) {
      L "Update: Antwort unbrauchbar - nichts geaendert"; return
    }
    # Unterschrieben ist genau: version + LF + configJson + LF + skript
    $paket = [string]$neu.version + "`n" + $configJson + "`n" + $skript
    if (-not (Test-Unterschrift $paket ([string]$neu.signatur))) {
      L "Update ABGELEHNT: Unterschrift passt nicht - nichts geaendert"; return
    }
    # Auch eine echte Unterschrift darf den PC nicht umlenken oder auf alte Fassungen zurueckdrehen
    $neuCfg = $configJson | ConvertFrom-Json
    if ([string]$neuCfg.erpAdresse -ne $ErpAdresse) {
      L "Update ABGELEHNT: andere ERP-Adresse ($($neuCfg.erpAdresse))"; return
    }
    $bisherStand = [int64]0
    if ($cfg.stand) { $bisherStand = [int64]$cfg.stand }
    if (-not $neuCfg.stand -or [int64]$neuCfg.stand -le $bisherStand) {
      L "Update ABGELEHNT: Stand $($neuCfg.stand) ist nicht neuer als $bisherStand"; return
    }
    $skriptPfad = $PSCommandPath
    $tmpSkript = $skriptPfad + '.neu'
    $tmpConfig = $configFile + '.neu'
    [IO.File]::WriteAllText($tmpSkript, $skript, (New-Object System.Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText($tmpConfig, $configJson, (New-Object System.Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $tmpConfig -Destination $configFile -Force
    Move-Item -LiteralPath $tmpSkript -Destination $skriptPfad -Force
    L "Update: neue Fassung $($neu.version) installiert"
  } catch {
    L "Update fehlgeschlagen: $($_.Exception.Message)"
  }
}

if ($Aktualisieren) { Update-Launcher; exit 0 }

# ===================== Helfer =====================
function Normalize-UNC([string]$p){
  if (-not $p){ return $p }
  $p = $p -replace '/', '\'
  # UNC-Praefix (\\) bewahren, nur innere Mehrfach-Backslashes reduzieren
  $prefix = ''
  if ($p.StartsWith('\\')){
    $prefix = '\\'
    $p = $p.Substring(2)
  }
  $p = [regex]::Replace($p, '\\{2,}', '\')
  return ($prefix + $p).TrimEnd('\')
}

# Antwortet der Rechner hinter \\host\... auf dem Freigabe-Port (445)? Kurzer Versuch statt
# der langen Windows-Wartezeit bei unbekannten Rechnern (z. B. Buero-Name unterwegs).
function Test-SmbErreichbar([string]$uncPfad){
  $h = (Normalize-UNC $uncPfad).TrimStart('\').Split('\')[0]
  if (-not $h){ return $false }
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $versuch = $client.BeginConnect($h, 445, $null, $null)
    if (-not $versuch.AsyncWaitHandle.WaitOne(1500)) { return $false }
    $client.EndConnect($versuch)
    return $true
  } catch { return $false } finally { $client.Close() }
}

# Antwortet das ERP? Dann laeuft die Verbindung (z. B. Tailscale), und ein nicht erreichbarer
# Ordner liegt an der Freigabe des Rechners mit dem Ordner (meist die Windows-Firewall).
function Test-ErpErreichbar {
  if (-not $ErpAdresse) { return $false }
  $erp = $null
  if (-not [Uri]::TryCreate($ErpAdresse, [UriKind]::Absolute, [ref]$erp) -or -not $erp.DnsSafeHost) { return $false }
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $versuch = $client.BeginConnect($erp.DnsSafeHost, $erp.Port, $null, $null)
    if (-not $versuch.AsyncWaitHandle.WaitOne(1500)) { return $false }
    $client.EndConnect($versuch)
    return $true
  } catch { return $false } finally { $client.Close() }
}

# Der Launcher laeuft unsichtbar - ohne Meldung saehe der Nutzer nach dem Klick gar nichts
function Zeige-Meldung([string]$text){
  try {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show($text, "ERP - Datei oeffnen",
      [System.Windows.Forms.MessageBoxButtons]::OK,
      [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
  } catch {}
}

$MeldungFreigabeFehlt = "Das ERP ist erreichbar, der gemeinsame Ordner aber nicht. Wahrscheinlich ist der Ordner fuer unterwegs (Tailscale) noch nicht freigegeben. Das richtet der Administrator einmal ein (Tailscale-Regeln bzw. Freigabe-Tailscale.ps1 am Rechner mit dem Ordner)."
$MeldungNichtErreichbar = "Der gemeinsame Ordner ist gerade nicht erreichbar. Unterwegs bitte Tailscale einschalten - sonst ist der Rechner mit dem Ordner vielleicht aus."

function Get-MatchingRoot([string]$fullPath, [string[]]$roots){
  $p = Normalize-UNC $fullPath
  $candidates = @()
  foreach($r in $roots){
    $rn = Normalize-UNC $r
    # Pfadgrenze: \\server\Zeichnungen erlaubt nicht \\server\Zeichnungen-Lohn
    if ($p.ToLower().StartsWith(($rn + '\').ToLower())){
      $candidates += [pscustomobject]@{
        Root = $r
        RootNorm = $rn
        Relative = $p.Substring($rn.Length).TrimStart('\')
      }
    }
  }
  if ($candidates.Count -gt 0){
    return ($candidates | Sort-Object { $_.RootNorm.Length } -Descending | Select-Object -First 1)
  }
  return $null
}

function TryOpen-Explorer([string]$path){
  if (-not (Test-Path -LiteralPath $path)){ L "MISS File not found: $path"; return $false }
  try{
    L "Open via explorer.exe `"$path`""
    Start-Process -FilePath "$env:WINDIR\explorer.exe" -ArgumentList "`"$path`""
    L "Explorer dispatched"; return $true
  }catch{ L "FAIL explorer.exe: $($_.Exception.Message)"; return $false }
}

function Try-NetUse([string]$drive, [string]$uncRoot){
  try {
    $out = cmd /c ("net use " + $drive + " `"" + $uncRoot + "`" /persistent:no 2>&1")
    L "net use output: $out"
    Start-Sleep -Milliseconds 500
    if (Test-Path -LiteralPath ($drive + '\')){ return $true }
    L "WARN drive $drive mapped but not accessible"
    return $false
  } catch {
    L "WARN net use $drive failed: $($_.Exception.Message)"
    return $false
  }
}

function Get-NetUseMappedRoot([string]$driveLetter){
  try{
    $out = (cmd /c ("net use " + $driveLetter + ": 2>&1")) -join "`n"
    if ($out -match '(?i)Remote[^\s]*\s+(\\\\[^\s\r\n]+)'){ return $matches[1].Trim() }
  }catch{}
  return $null
}

function Ensure-MappedDrive([string]$uncRoot, [string]$preferred = $PreferredDrive){
  $existing = Get-PSDrive -PSProvider FileSystem | Where-Object { $_.DisplayRoot -and ($_.DisplayRoot -ieq $uncRoot) }
  if ($existing){ L "Reusing map: $($existing.Name): -> $uncRoot"; return ($existing.Name + ':') }

  if ($preferred){
    $pref = $preferred.TrimEnd(':')
    $used = (Get-PSDrive -PSProvider FileSystem).Name
    if ($used -notcontains $pref){
      L "Map preferred: $preferred -> $uncRoot"
      if (Try-NetUse $preferred $uncRoot){ return $preferred }
    } else {
      $existingRoot = Get-NetUseMappedRoot $pref
      if ($existingRoot -and ($existingRoot.TrimEnd('\') -ieq $uncRoot.TrimEnd('\'))){
        L "Preferred $preferred already mapped to $uncRoot (reusing)"
        return $preferred
      }
      L "Preferred $preferred already in use (root: $existingRoot)"
    }
  }

  $used2 = (Get-PSDrive -PSProvider FileSystem).Name
  foreach($c in @('Z','P','T','Y','X','W','V','U','S','R','Q')){
    if ($used2 -notcontains $c){
      $drive = ($c + ':')
      L "Map fallback: $drive -> $uncRoot"
      if (Try-NetUse $drive $uncRoot){ return $drive }
    }
  }

  # Last resort: any in-use drive already pointing to the UNC root
  foreach($c in @('Z','P','T','Y','X','W','V','U','S','R','Q')){
    $chkRoot = Get-NetUseMappedRoot $c
    if ($chkRoot -and ($chkRoot.TrimEnd('\') -ieq $uncRoot.TrimEnd('\'))){
      L "Found existing map via net use: ${c}: -> $uncRoot (reusing)"
      return ($c + ':')
    }
  }

  L "ERR No free drive letter or all mappings failed"; return $null
}

function Get-OpenCommand([string]$extension){
  try{
    $progId = (Get-Item ("Registry::HKEY_CLASSES_ROOT\$extension")).GetValue('')
    if (-not $progId){ return $null }
    $cmd = (Get-Item ("Registry::HKEY_CLASSES_ROOT\$progId\shell\open\command")).GetValue('')
    if ([string]::IsNullOrWhiteSpace($cmd)){ return $null }
    return $cmd
  }catch{ return $null }
}

function Parse-ExeFromOpenCommand([string]$cmd){
  if ($cmd -match '^\s*"?([^"]+?\.exe)"?'){ return $matches[1] }
  return $null
}

function Find-TenadoExe {
  try{
    $hits = Get-ChildItem 'C:\Program Files','C:\Program Files (x86)' -Recurse -File -Include *tenado*metall*.exe,*tenado*cad*.exe -ErrorAction SilentlyContinue
    $exe = $hits | Select-Object -First 1 -ExpandProperty FullName
    if ($exe){ return $exe }
  }catch{}
  if (Test-Path -LiteralPath $TenadoExeFallback){ return $TenadoExeFallback }
  return $null
}

function Get-ExcelExe {
  foreach($k in @(
    'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\excel.exe',
    'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\App Paths\excel.exe'
  )){
    try{
      $p = (Get-ItemProperty $k).'(default)'; if ($p -and (Test-Path -LiteralPath $p)) { return $p }
      $p2 = (Get-ItemProperty $k).Path; if ($p2) { $p3 = Join-Path $p2 'EXCEL.EXE'; if (Test-Path -LiteralPath $p3) { return $p3 } }
    }catch{}
  }
  return $null
}

# ===================== Main =====================
try {
  if (-not $Url) { if ($args.Count -gt 0) { $Url = $args[0] } }
  if (-not $Url) { L "ERR no URL"; exit 1 }

  L "=== OpenFileLauncher start ==="
  L "URL: $Url"
  L "User: $env:USERNAME  Identity: $([Security.Principal.WindowsIdentity]::GetCurrent().Name)"
  L "PSVersion: $($PSVersionTable.PSVersion)  PID: $PID"
  L "WD: $(Get-Location)"

  # Update (nur https + Unterschrift, siehe Update-Launcher) parallel anstossen; greift beim naechsten Klick
  if ($ErpAdresse -and $PSCommandPath) {
    try {
      Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Hidden',
        '-File', ('"' + $PSCommandPath + '"'), '-Aktualisieren') | Out-Null
    } catch { L "Update nicht gestartet: $($_.Exception.Message)" }
  }

  if ($configError){ L "ERR config.json nicht lesbar ($configFile): $configError" }
  if ($AllowedRoots.Count -eq 0){
    L "ERR keine erlaubten Pfade: $configFile fehlt oder enthaelt keine allowedRoots. Auf dem ERP-Rechner Setup-Freigabe.ps1 ausfuehren und den Launcher mit der erzeugten config.json neu installieren."
    # Der Launcher laeuft unsichtbar - ohne Meldung saehe der Nutzer nach dem Klick gar nichts
    try {
      Add-Type -AssemblyName System.Windows.Forms
      [System.Windows.Forms.MessageBox]::Show(
        "Die Datei kann nicht direkt geoeffnet werden: Der OpenFile Launcher ist auf diesem PC noch nicht fertig eingerichtet (config.json fehlt).`n`nBitte wenden Sie sich an den Administrator.",
        "OpenFile Launcher",
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
    } catch {}
    exit 2
  }

  # URL -> UNC (unterstuetzt openfile://?path=... und openfile://open/?path=...)
  $unc = $null
  if ($Url -like '\\*'){ $unc = $Url } else {
    $u = [Uri]$Url
    $qs = [System.Web.HttpUtility]::ParseQueryString($u.Query)
    $rawPath = $qs.Get('path')
    if (-not $rawPath) { L "ERR no path in query"; exit 1 }
    $unc = [System.Web.HttpUtility]::UrlDecode($rawPath)
  }
  $unc = Normalize-UNC $unc

  $ext = [IO.Path]::GetExtension($unc); if ($null -eq $ext){ $ext="" } else { $ext = $ext.ToLowerInvariant() }
  L "UNC: $unc"
  L "EXT: $ext"

  # Nur Tabellen und Zeichnungen - nie Programme, Verknuepfungen oder Skripte aus der Freigabe
  $ErlaubteEndungen = $SpreadsheetExts + @('.sza', '.tcd')
  if ($ErlaubteEndungen -notcontains $ext) {
    L "ERR Dateiart nicht erlaubt: $ext"; exit 2
  }

  # Root + rel ermitteln (inkl. Alias-Rewrite)
  $match = Get-MatchingRoot $unc $AllowedRoots
  if (-not $match -and $ErpAdresse) {
    # Hat der Admin den Ordner im ERP umgestellt, kennt erst die neue Fassung die neue
    # Adresse: jetzt gleich aktualisieren statt erst nach diesem Klick
    L "Ordner unbekannt - frage nach einer neuen Fassung"
    Update-Launcher
    try {
      $cfg = [IO.File]::ReadAllText($configFile) | ConvertFrom-Json
      if ($cfg.allowedRoots) { $AllowedRoots = @($cfg.allowedRoots) }
      $RootAliases = @{}
      if ($cfg.rootAliases) {
        foreach ($key in $cfg.rootAliases.PSObject.Properties.Name) { $RootAliases[$key] = $cfg.rootAliases.$key }
      }
    } catch { L "config.json nach dem Update nicht lesbar: $($_.Exception.Message)" }
    $match = Get-MatchingRoot $unc $AllowedRoots
  }
  if (-not $match){
    L ("ERR out of allowed roots: " + $unc + "  (allowed: " + ($AllowedRoots -join ', ') + ")")
    exit 2
  }
  $root = $match.Root
  $rel  = $match.Relative
  if (-not $rel -or ($rel -split '\\' | Where-Object { $_ -eq '..' -or $_ -eq '.' })) {
    L "ERR unzulaessiger Pfad: $unc"; exit 2
  }

  # Share erreichbar machen. Unterwegs (Tailscale) ist der Rechnername aus dem Buero oft
  # unbekannt - dann ohne langes Warten auf den Ausweich-Namen wechseln.
  $aliasRoot = $null
  foreach ($k in $RootAliases.Keys) { if ((Normalize-UNC $k) -ieq (Normalize-UNC $root)) { $aliasRoot = $RootAliases[$k] } }
  if ($aliasRoot -and -not (Test-SmbErreichbar $root) -and (Test-SmbErreichbar $aliasRoot)) {
    L "Root nicht erreichbar -> Ausweich-Adresse: $root  ==>  $aliasRoot"
    $root = Normalize-UNC $aliasRoot
    $unc  = $root + '\' + $rel
  }
  $rootExists = Test-Path -LiteralPath $root
  if (-not $rootExists){
    L "Share not mounted, trying: net use $root"
    try { cmd /c ("net use " + $root) | Out-Null } catch {}
    $rootExists = Test-Path -LiteralPath $root
  }
  if (-not $rootExists -and $aliasRoot -and ($root -ine (Normalize-UNC $aliasRoot))){
    $root = Normalize-UNC $aliasRoot
    L "Root unavailable -> rewrite: $root"
    $unc  = $root + '\' + $rel
    $rootExists = Test-Path -LiteralPath $root
  }
  if (-not $rootExists -and (Test-SmbErreichbar $root)) {
    # Rechner erreichbar, aber keine Anmeldung: Explorer fragt nach Benutzer und Passwort
    # (mit "Anmeldedaten speichern"). Danach klappt der naechste Klick ohne Rueckfrage.
    L "Share erreichbar, aber kein Zugriff -> Explorer fragt nach Anmeldung: $root"
    Start-Process -FilePath "$env:WINDIR\explorer.exe" -ArgumentList ('"' + $root + '"')
    exit 4
  }
  if (-not $rootExists) {
    # Ohne erreichbaren Ordner scheitert jedes Programm - lieber sagen, woran es liegt
    if (Test-ErpErreichbar) {
      L "ERP erreichbar, Share nicht -> Freigabe fuer unterwegs fehlt: $root"
      Zeige-Meldung $MeldungFreigabeFehlt
    } else {
      L "Weder Share noch ERP erreichbar: $root"
      Zeige-Meldung $MeldungNichtErreichbar
    }
    exit 3
  }
  L ("RootExists: " + $rootExists)

  # ========= HiCAD (.sza) =========
  if ($ext -eq '.sza'){
    L ".sza detected -> mapped first"
    $drive = Ensure-MappedDrive $root
    $target = $unc
    if ($drive){
      $mapped = Join-Path ($drive + '\') $rel
      L "rel: $rel"
      L "mapped: $mapped"
      L ("Mapped exists: " + (Test-Path -LiteralPath $mapped))
      if (Test-Path -LiteralPath $mapped){ $target = $mapped }
    }

    $cmd = Get-OpenCommand '.sza'
    if ($cmd){
      L "Open command (.sza): $cmd"
      $exe = Parse-ExeFromOpenCommand $cmd
      if ($exe -and (Test-Path -LiteralPath $exe)){
        try{
          L "Start HiCAD EXE: $exe `"$target`""
          Start-Process -FilePath $exe -ArgumentList "`"$target`"" -ErrorAction Stop | Out-Null
          L "HiCAD EXE OK"; exit 0
        }catch{ L "HiCAD EXE failed: $($_.Exception.Message)" }
      } else {
        $expanded = $cmd -replace '%1|%L', ('"'+$target+'"')
        try{
          L "Run ftype via cmd /c: $expanded"
          Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $expanded -ErrorAction Stop | Out-Null
          L "HiCAD ftype OK"; exit 0
        }catch{ L "HiCAD ftype failed: $($_.Exception.Message)" }
      }
    } else { L "No registry open command for .sza" }

    if (TryOpen-Explorer $target) { exit 0 }
    L ("All attempts failed for " + $unc); exit 5
  }

  # ========= TENADO (.tcd) =========
  if ($ext -eq '.tcd'){
    if (TryOpen-Explorer $unc) { L "OK .tcd via UNC"; exit 0 }

    $drive = Ensure-MappedDrive $root
    if ($drive){
      $mapped = Join-Path ($drive + '\') $rel
      L "rel: $rel"
      L "mapped: $mapped"
      L ("Mapped exists: " + (Test-Path -LiteralPath $mapped))
      if (TryOpen-Explorer $mapped) { L "OK .tcd via mapped path"; exit 0 }
    }

    $tenado = Find-TenadoExe
    if ($tenado){
      try{
        L "Fallback TENADO EXE: $tenado `"$unc`""
        Start-Process -FilePath $tenado -ArgumentList "`"$unc`"" -ErrorAction Stop | Out-Null
        L "TENADO EXE OK"; exit 0
      }catch{ L "TENADO EXE failed: $($_.Exception.Message)" }
    } else { L "TENADO EXE not found" }

    L ("All attempts failed for " + $unc); exit 5
  }

  # ========= Spreadsheet (.xlsx/.xls/.csv/.ods/...) =========
  if ($SpreadsheetExts -contains $ext){
    L "Spreadsheet detected: $ext"

    # Immer zuerst MAPPEN -> Excel mag UNC teils nicht / ersetzt \ durch /
    $drive = Ensure-MappedDrive $root
    $target = $unc
    if ($drive){
      $mapped = Join-Path ($drive + '\') $rel
      L "rel: $rel"
      L "mapped: $mapped"
      L ("Mapped exists: " + (Test-Path -LiteralPath $mapped))
      if (Test-Path -LiteralPath $mapped){ $target = $mapped }
    }
    $target = Normalize-UNC $target

    # 1) Direkt EXCEL.EXE (ohne DDE, ohne Zuordnung)
    $excel = Get-ExcelExe
    if ($excel){
      try{
        L "Start EXCEL.EXE: $excel `"$target`""
        Start-Process -FilePath $excel -ArgumentList "`"$target`"" -ErrorAction Stop | Out-Null
        L "Excel direct OK"; exit 0
      }catch{ L "Excel direct failed: $($_.Exception.Message)" }
    } else {
      L "Excel EXE not found via App Paths"
    }

    # 2) Fallback: Registry-Zuordnung
    $cmd = Get-OpenCommand $ext
    if ($cmd){
      L "Open command ($ext): $cmd"
      $exe = Parse-ExeFromOpenCommand $cmd
      if ($exe -and (Test-Path -LiteralPath $exe)){
        try{
          L "Start assoc EXE: $exe `"$target`""
          Start-Process -FilePath $exe -ArgumentList "`"$target`"" -ErrorAction Stop | Out-Null
          L "Association EXE OK"; exit 0
        }catch{ L "Association EXE failed: $($_.Exception.Message)" }
      } else {
        $expanded = $cmd -replace '%1|%L', ('"'+$target+'"')
        try{
          L "Run ftype via cmd /c: $expanded"
          Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $expanded -ErrorAction Stop | Out-Null
          L "Association ftype OK"; exit 0
        }catch{ L "Association ftype failed: $($_.Exception.Message)" }
      }
    } else {
      L "No registry open command for $ext"
    }

    # 3) Allerletzter Versuch: Explorer
    if (TryOpen-Explorer $target) { L "OK spreadsheet via Explorer"; exit 0 }

    L ("All attempts failed for spreadsheet: " + $unc); exit 5
  }

  # ========= andere Endungen =========
  if (TryOpen-Explorer $unc) { L "OK other via UNC"; exit 0 }

  $drive2 = Ensure-MappedDrive $root
  if ($drive2){
    $mapped2 = Join-Path ($drive2 + '\') $rel
    L "rel: $rel"
    L "mapped: $mapped2"
    L ("Mapped exists: " + (Test-Path -LiteralPath $mapped2))
    if (TryOpen-Explorer $mapped2) { L "OK other via mapped path"; exit 0 }
  }

  L "FAIL open (other ext) via UNC + mapped"; exit 3
}
catch {
  L ("UNHANDLED: " + $_.Exception.GetType().FullName + " -> " + $_.Exception.Message)
  exit 9
}
