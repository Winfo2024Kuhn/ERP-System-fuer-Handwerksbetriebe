$ErrorActionPreference = 'SilentlyContinue'
try {
    $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json
} catch {
    exit 0
}

$sessionId = $payload.session_id
$filePath = $payload.tool_input.file_path
if (-not $sessionId -or -not $filePath) { exit 0 }

# $env:TEMP gibt es nur unter Windows; GetTempPath() liefert dort denselben
# Ordner und unter Linux (Cloud-Sitzung) /tmp.
$flagDir = Join-Path ([System.IO.Path]::GetTempPath()) 'claude-doc-flags'
if (-not (Test-Path $flagDir)) {
    New-Item -ItemType Directory -Force -Path $flagDir | Out-Null
}

$normalized = $filePath -replace '\\', '/'

if ($normalized -match '/BACKEND_ARCH\.md$') {
    Set-Content -Path (Join-Path $flagDir "$sessionId-backend.flag") -Value 'read' -Encoding ASCII
}
if ($normalized -match '/FRONTEND_UI\.md$') {
    Set-Content -Path (Join-Path $flagDir "$sessionId-frontend.flag") -Value 'read' -Encoding ASCII
}
if ($normalized -match '/TESTING_SECURITY\.md$') {
    Set-Content -Path (Join-Path $flagDir "$sessionId-testing.flag") -Value 'read' -Encoding ASCII
}

exit 0
