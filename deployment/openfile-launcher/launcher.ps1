param([string]$Url)

# ===================== Setup =====================
Add-Type -AssemblyName System.Web
$ErrorActionPreference = 'Stop'

# Erlaubte UNC-Roots (neuer zuerst)
$AllowedRoots = @(
  '\\THOMAS_PC\CADdrawings'
)

# Alias: alten Root automatisch auf neuen umschreiben
$RootAliases = @{
  '\\MARVIN-PC\Zeichnungen' = '\\THOMAS_PC\CADdrawings'
}

# Bevorzugter Laufwerksbuchstabe
$PreferredDrive = 'Z:'

# Optional: bekannter TENADO-Pfad (Fallback)
$TenadoExeFallback = 'C:\Program Files (x86)\TENADO\TENADO METALL 2D V22\TENADOMETALL22.exe'

# Spreadsheet-Endungen (Excel, CSV, ODS, …)
$SpreadsheetExts = @('.xlsx','.xls','.xlsm','.xlsb','.xlt','.xltx','.xltm','.csv','.ods')

# ---- Logging: Konsole + Datei ----
$logDir = 'C:\ProgramData\OpenFileLauncher'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$log = Join-Path $logDir 'launcher.log'
function L([string]$m){
  $ts = "[{0:yyyy-MM-dd HH:mm:ss}] {1}" -f (Get-Date), $m
  Add-Content -Path $log -Value $ts
  Write-Host $ts
}

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

function Get-MatchingRoot([string]$fullPath, [string[]]$roots){
  $p = Normalize-UNC $fullPath
  $candidates = @()
  foreach($r in $roots){
    $rn = Normalize-UNC $r
    if ($p.ToLower().StartsWith($rn.ToLower())){
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

  # URL -> UNC (unterstützt openfile://?path=... und openfile://open/?path=...)
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

  # Root + rel ermitteln (inkl. Alias-Rewrite)
  $match = Get-MatchingRoot $unc $AllowedRoots
  if (-not $match){
    L ("ERR out of allowed roots: " + $unc + "  (allowed: " + ($AllowedRoots -join ', ') + ")")
    exit 2
  }
  $root = $match.Root
  $rel  = $match.Relative

  # Share erreichbar machen
  $rootExists = Test-Path -LiteralPath $root
  if (-not $rootExists){
    L "Share not mounted, trying: net use $root"
    try { cmd /c ("net use " + $root) | Out-Null } catch {}
    $rootExists = Test-Path -LiteralPath $root
  }
  if (-not $rootExists -and $RootAliases.ContainsKey($root)){
    $newRoot = $RootAliases[$root]
    L "Root unavailable -> rewrite: $root  ==>  $newRoot"
    $root = $newRoot
    $unc  = (Join-Path ($root + '\') $rel)
  }
  L ("RootExists: " + (Test-Path -LiteralPath $root))

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
