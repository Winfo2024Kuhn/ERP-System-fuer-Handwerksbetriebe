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

# Alle Skills, die als Design-Pflichtlektuere zaehlen
$designSkills = @(
    'ui-ux-pro-max',
    'ui-ux-pro-max:ui-ux-pro-max',
    'ui-ux-pro-max:design',
    'ui-ux-pro-max:design-system',
    'ui-ux-pro-max:ui-styling',
    'frontend-design',
    'frontend-design:frontend-design'
)

if ($designSkills -contains $skillName) {
    $flagDir = Join-Path $env:TEMP 'claude-doc-flags'
    if (-not (Test-Path $flagDir)) {
        New-Item -ItemType Directory -Force -Path $flagDir | Out-Null
    }
    Set-Content -Path (Join-Path $flagDir "$sessionId-design.flag") -Value $skillName -Encoding ASCII
}

exit 0
