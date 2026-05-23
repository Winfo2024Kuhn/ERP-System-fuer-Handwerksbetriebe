# PostToolUse-Hook: Aktualisiert den Graphify-Graphen nach jeder Code-Aenderung automatisch
$ErrorActionPreference = 'SilentlyContinue'

$graphFile = "c:\dev\ERP-System-fuer-Handwerksbetriebe\graphify-out\graph.json"

# Nur ausfuehren wenn der Graph bereits existiert
if (-not (Test-Path $graphFile)) { exit 0 }

# Pruefe ob graphify im PATH verfuegbar ist
$graphifyCmd = Get-Command graphify -ErrorAction SilentlyContinue
if (-not $graphifyCmd) { exit 0 }

Push-Location "c:\dev\ERP-System-fuer-Handwerksbetriebe"
try {
    # AST-only, kein API-Aufruf – laeuft schnell im Hintergrund
    & graphify update . 2>$null | Out-Null
} finally {
    Pop-Location
}

exit 0
