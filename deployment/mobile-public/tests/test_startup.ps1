$ErrorActionPreference = 'Stop'
$Package = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
foreach ($name in @('Start-MobilePublic.ps1', 'Stop-MobilePublic.ps1')) {
    $tokens = $null
    $errors = $null
    $null = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $Package $name), [ref]$tokens, [ref]$errors)
    if ($errors.Count) { throw "PowerShell syntax errors in $name" }
}
# The actual script runs. Only external Docker and waiting are replaced; no real containers are changed.
$global:MobileProxyTest = @{}
function Reset-TestState {
    $global:MobileProxyTest = @{badCredentials=$false; unsafeBinding=$false; version='2.24.4'; existing=$true;
        emptyAdmin=$false; gateHeader=$true; gateStatus=404; setupRequired=$false; exposed=$false}
}
function global:Start-Sleep { }
function global:docker {
    $global:LASTEXITCODE = 0
    $arguments = $args -join ' '
    $state = $global:MobileProxyTest
    if ($arguments -match '^compose version') { return $state.version }
    if ($arguments -match '^info') { return '29.8.2' }
    if ($arguments -match '^inspect') {
        if ($state.existing) { return 'existing-erp' }
        $global:LASTEXITCODE = 1
        return ''
    }
    if ($arguments -match 'config --format json') {
        if ($arguments -match 'erp.override.yaml') {
            $password = if ($state.badCredentials) { 'CHANGE_ME_DB_PW' } else { 'Dummy-Password-2026!' }
            $admin = if ($state.emptyAdmin) { '' } else { 'test.admin' }
            $adminPassword = if ($state.emptyAdmin) { '' } else { $password }
            $bind = if ($state.unsafeBinding) { '0.0.0.0' } else { '127.0.0.1' }
            return @{services=@{
                app=@{environment=@{APP_DB_URL='jdbc:mysql://mysql:3306/test';APP_DB_USER='erp_user';APP_DB_PASS=$password;APP_ADMIN_USER=$admin;APP_ADMIN_PASS=$adminPassword};ports=@(@{host_ip=$bind})}
                mysql=@{environment=@{MYSQL_ROOT_PASSWORD=$password;MYSQL_PASSWORD=$password;MYSQL_USER='erp_user'}}
            }} | ConvertTo-Json -Depth 8 -Compress
        }
        return @{services=@{gateway=@{environment=@{ERP_UPSTREAM='app:8080'}};caddy=@{environment=@{MOBILE_DOMAIN='mobile.invalid'}}}} | ConvertTo-Json -Depth 8 -Compress
    }
    if ($arguments -match '^network inspect') { return '[{"Internal":true,"IPAM":{"Config":[{"Subnet":"172.30.51.0/29"}]}}]' }
    if ($arguments -match 'stop caddy tunnel') { return '' }
    if ($arguments -match 'up .*--build (caddy|tunnel)') { $state.exposed = $true; return '' }
    if ($arguments -match 'up ') { return '' }
    if ($arguments -match 'wget .*--spider.*bootstrap-status') {
        $header = if ($state.gateHeader) { "`n  X-ERP-Public-Mobile: 1" } else { '' }
        $global:LASTEXITCODE = 1
        return "  HTTP/1.1 $($state.gateStatus) Denied$header"
    }
    if ($arguments -match 'wget .*malformed') { $global:LASTEXITCODE = 1; return '  HTTP/1.1 401 Unauthorized' }
    if ($arguments -match 'wget .*bootstrap-status') { return @{hasLoginUsers=$true;setupRequired=$state.setupRequired} | ConvertTo-Json -Compress }
    throw 'Unexpected Docker operation in test.'
}
$file = [System.IO.Path]::GetTempFileName()
try {
    Reset-TestState
    & (Join-Path $Package 'Start-MobilePublic.ps1') -EnvFile $file -ValidateOnly
    if ($global:MobileProxyTest.exposed) { throw 'ValidateOnly exposed a service.' }
    Reset-TestState
    $global:MobileProxyTest.emptyAdmin = $true
    & (Join-Path $Package 'Start-MobilePublic.ps1') -EnvFile $file
    if (-not $global:MobileProxyTest.exposed) { throw 'A protected existing ERP with completed setup should be allowed.' }
    $cases = @('credentials', 'binding', 'project', 'version', 'domain', 'missing-gate-header', 'old-403', 'unfinished-setup', 'new-without-admin')
    foreach ($case in $cases) {
        Reset-TestState
        $state = $global:MobileProxyTest
        $state.badCredentials = $case -eq 'credentials'
        $state.unsafeBinding = $case -eq 'binding'
        if ($case -eq 'version') { $state.version = '2.24.3' }
        if ($case -eq 'missing-gate-header') { $state.gateHeader = $false }
        if ($case -eq 'old-403') { $state.gateStatus = 403; $state.gateHeader = $false }
        if ($case -eq 'unfinished-setup') { $state.setupRequired = $true }
        $parameters = @{EnvFile=$file}
        if ($case -eq 'project') { $parameters.ErpProjectName = 'different-erp' }
        if ($case -eq 'domain') { $parameters.Mode = 'domain' }
        if ($case -eq 'new-without-admin') { $state.existing = $false; $state.emptyAdmin = $true; $parameters.ErpProjectName = 'new-erp' }
        $rejected = $false
        try { & (Join-Path $Package 'Start-MobilePublic.ps1') @parameters } catch {
            $rejected = $true
            if ($_.Exception.Message -match 'Dummy-Password|CHANGE_ME_DB_PW') { throw 'Credential leaked into a diagnostic.' }
        }
        if (-not $rejected -or $state.exposed) { throw "Startup should reject $case before exposure." }
    }
    Write-Host 'PowerShell syntax and 11 startup scenarios passed; no real Docker mutations.'
} finally {
    Remove-Item -LiteralPath $file
    Remove-Item Function:global:docker
    Remove-Item Function:global:Start-Sleep
    Remove-Variable MobileProxyTest -Scope Global
}
