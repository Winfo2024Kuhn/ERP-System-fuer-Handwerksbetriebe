# Setzt ein Session-Flag, sobald einer der Pflicht-Design-Skills aufgerufen wurde.
# Gegenstueck: check-doc-read.ps1 blockt Frontend-Edits ohne dieses Flag.
$ErrorActionPreference = 'SilentlyContinue'
try {
    $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json
} catch {
    exit 0
}

$sessionId = $payload.session_id
if (-not $sessionId) { exit 0 }

# Skill-Name steht je nach Tool-Variante in .skill oder .name
$skillName = $payload.tool_input.skill
if (-not $skillName) { $skillName = $payload.tool_input.name }
if (-not $skillName) { exit 0 }

# Der Frontend-Skill, der als Design-Pflichtlektuere zaehlt
$designSkills = @(
    'handwerkerprogramm-design',
    'handwerkerprogramm-design:handwerkerprogramm-design'
)

if ($designSkills -contains $skillName) {
    # $env:TEMP gibt es nur unter Windows; GetTempPath() liefert dort denselben
    # Ordner und unter Linux (Cloud-Sitzung) /tmp.
    $flagDir = Join-Path ([System.IO.Path]::GetTempPath()) 'claude-doc-flags'
    if (-not (Test-Path $flagDir)) {
        New-Item -ItemType Directory -Force -Path $flagDir | Out-Null
    }
    Set-Content -Path (Join-Path $flagDir "$sessionId-design.flag") -Value $skillName -Encoding ASCII
}

exit 0
