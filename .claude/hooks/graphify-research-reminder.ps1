# PreToolUse-Hook: Erinnert Claude, vor jeder Suche zuerst graphify zu nutzen
$ErrorActionPreference = 'SilentlyContinue'

$graphFile  = "c:\dev\ERP-System-fuer-Handwerksbetriebe\graphify-out\graph.json"
$wikiIndex  = "c:\dev\ERP-System-fuer-Handwerksbetriebe\graphify-out\wiki\index.md"

if (-not (Test-Path $graphFile)) { exit 0 }

$msg  = "GRAPHIFY ZUERST: Dieser Workspace hat einen Knowledge-Graphen. Nutze graphify, bevor du rohe Dateien durchsuchst:`n"
$msg += "  graphify query `"<Frage>`"        -> fokussierter Subgraph (viel kleiner als grep-Ausgabe)`n"
$msg += "  graphify path `"<A>`" `"<B>`"     -> Beziehung/Abhängigkeit zwischen zwei Komponenten`n"
$msg += "  graphify explain `"<Konzept>`"    -> gezahlte Erklaerung eines Konzepts`n"

if (Test-Path $wikiIndex) {
    $msg += "  graphify-out/wiki/index.md        -> breite Navigation statt rohem Source-Browsing`n"
}

$msg += "Lies graphify-out/GRAPH_REPORT.md nur fuer breite Architektur-Reviews.`n"
$msg += "Greife auf Rohdateien nur zurueck, wenn graphify nicht genuegend Kontext liefert."

$output = [ordered]@{
    hookSpecificOutput = [ordered]@{
        hookEventName    = "PreToolUse"
        additionalContext = $msg
    }
} | ConvertTo-Json -Compress

Write-Output $output
exit 0
