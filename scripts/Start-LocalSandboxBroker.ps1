[CmdletBinding()]
param(
    [switch]$LaunchIdea,
    [string]$IdeaPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$brokerPort = 8091
$dbContainer = 'yanban-agent-redo-mysql-1'
$dbName = 'yanban_sandbox_local'
$dbUser = 'yb_sandbox_local'
$runtimeRoot = Join-Path $env:LOCALAPPDATA 'Yanban\SandboxBroker'
$workspaceRoot = Join-Path $runtimeRoot 'work'
$providerHome = Join-Path $runtimeRoot 'provider-home'
$providerConfig = Join-Path $runtimeRoot 'provider-config'
$providerData = Join-Path $runtimeRoot 'provider-data'
$providerState = Join-Path $runtimeRoot 'provider-state'
$sbxExecutable = Join-Path $env:LOCALAPPDATA 'DockerSandboxes\bin\sbx.exe'
$brokerJar = Join-Path $repoRoot 'yanban-sandbox-broker\target\yanban-sandbox-broker-0.1.0-SNAPSHOT.jar'
$stdoutLog = Join-Path $runtimeRoot 'broker.out.log'
$stderrLog = Join-Path $runtimeRoot 'broker.err.log'

function Resolve-IdeaPath {
    if ($IdeaPath) {
        if (-not (Test-Path -LiteralPath $IdeaPath -PathType Leaf)) { throw "IDEA executable not found: $IdeaPath" }
        return (Resolve-Path -LiteralPath $IdeaPath).Path
    }
    $patterns = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\IntelliJ IDEA*\bin\idea64.exe'),
        (Join-Path $env:LOCALAPPDATA 'JetBrains\Toolbox\apps\IDEA-*\*\bin\idea64.exe'),
        'C:\software\ideainstall\IntelliJ IDEA*\bin\idea64.exe',
        'C:\Program Files\JetBrains\IntelliJ IDEA*\bin\idea64.exe'
    )
    $found = foreach ($pattern in $patterns) {
        Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue
    }
    $selected = $found | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $selected) { throw 'IDEA was not found automatically; pass -IdeaPath with the full idea64.exe path.' }
    return $selected.FullName
}

if ($LaunchIdea -and (Get-Process idea64 -ErrorAction SilentlyContinue)) {
    throw 'IDEA is already running. Close it first so the new IDEA process can inherit the temporary sandbox token.'
}
$resolvedIdea = if ($LaunchIdea) { Resolve-IdeaPath } else { $null }

$listener = Get-NetTCPConnection -LocalPort $brokerPort -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    throw "Port $brokerPort is already used by PID $($listener.OwningProcess) ($($owner.Name)). Run Stop-LocalSandboxBroker.ps1 or inspect it before continuing."
}

if (-not (Test-Path -LiteralPath $sbxExecutable -PathType Leaf)) {
    throw "Official sbx executable is missing: $sbxExecutable"
}
$diagnose = & $sbxExecutable diagnose --output json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $diagnose.summary.fail -ne 0) { throw 'sbx diagnose did not pass.' }

$mysql = docker inspect $dbContainer --format '{{.State.Health.Status}}' 2>$null
if ($LASTEXITCODE -ne 0 -or $mysql -ne 'healthy') { throw "$dbContainer is not healthy. Start the Docker Desktop infrastructure first." }

$settings = @{}
Get-Content -LiteralPath (Join-Path $repoRoot '.env') | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $settings[$Matches[1]] = $Matches[2] }
}
$rootPassword = $settings['MYSQL_ROOT_PASSWORD']
if ([string]::IsNullOrWhiteSpace($rootPassword)) { throw 'MYSQL_ROOT_PASSWORD is unavailable in the existing local .env.' }

$brokerToken = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
$dbPassword = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
$provision = "CREATE DATABASE IF NOT EXISTS $dbName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS '$dbUser'@'%' IDENTIFIED BY '$dbPassword'; ALTER USER '$dbUser'@'%' IDENTIFIED BY '$dbPassword'; GRANT ALL PRIVILEGES ON $dbName.* TO '$dbUser'@'%'; FLUSH PRIVILEGES;"
$provision | docker exec -i -e "MYSQL_PWD=$rootPassword" $dbContainer mysql -uroot --batch --skip-column-names
if ($LASTEXITCODE -ne 0) { throw 'Dedicated Broker database provisioning failed.' }

@($runtimeRoot, $workspaceRoot, $providerHome, $providerConfig, $providerData, $providerState) | ForEach-Object {
    New-Item -ItemType Directory -Path $_ -Force | Out-Null
}

$brokerSources = Get-ChildItem -Path @(
    (Join-Path $repoRoot 'yanban-sandbox-broker\src'),
    (Join-Path $repoRoot 'yanban-sandbox-broker\pom.xml'),
    (Join-Path $repoRoot 'yanban-sandbox-contract\src'),
    (Join-Path $repoRoot 'yanban-sandbox-contract\pom.xml')
) -Recurse -File
$jarIsStale = -not (Test-Path -LiteralPath $brokerJar -PathType Leaf) -or
    ($brokerSources | Where-Object { $_.LastWriteTime -gt (Get-Item -LiteralPath $brokerJar).LastWriteTime } | Select-Object -First 1)
if ($jarIsStale) {
    & mvn -o -pl yanban-sandbox-broker -am package -DskipTests
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $brokerJar -PathType Leaf)) {
        throw 'Broker jar build failed.'
    }
}

$env:YANBAN_SANDBOX_BROKER_ENABLED = 'true'
$env:YANBAN_SANDBOX_BROKER_MODE = 'LOCAL_ACCEPTANCE'
$env:YANBAN_SANDBOX_BROKER_ADDRESS = '127.0.0.1'
$env:YANBAN_SANDBOX_BROKER_REMOTE = 'false'
$env:YANBAN_SANDBOX_BROKER_PORT = "$brokerPort"
$env:YANBAN_SBX_EXECUTABLE = $sbxExecutable
$env:YANBAN_SANDBOX_WORKSPACE_ROOT = $workspaceRoot
$env:YANBAN_SANDBOX_BROKER_TOKEN = $brokerToken
$env:YANBAN_SANDBOX_DB_URL = "jdbc:mysql://127.0.0.1:3307/${dbName}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:YANBAN_SANDBOX_DB_USER = $dbUser
$env:YANBAN_SANDBOX_DB_PASSWORD = $dbPassword
$env:YANBAN_SBX_HOME = $providerHome
$env:YANBAN_SBX_CONFIG_HOME = $providerConfig
$env:YANBAN_SBX_DATA_HOME = $providerData
$env:YANBAN_SBX_STATE_HOME = $providerState

$broker = Start-Process -FilePath java -ArgumentList @('-jar', $brokerJar) -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
try {
    $headers = @{ Authorization = "Bearer $brokerToken" }
    $healthy = $false
    for ($i = 0; $i -lt 120; $i++) {
        if ($broker.HasExited) { break }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$brokerPort/internal/v1/health" -Headers $headers -TimeoutSec 2
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 250
    }
    if (-not $healthy) {
        $detail = if (Test-Path -LiteralPath $stdoutLog) {
            (Get-Content -LiteralPath $stdoutLog -Tail 12) -join [Environment]::NewLine
        } else { 'No Broker stderr was captured.' }
        throw "Broker did not become healthy.$([Environment]::NewLine)$detail"
    }

    # These values are inherited by IDEA and the backend it launches. They are never written to disk.
    if (-not $env:YANBAN_SANDBOX_ENABLED) { $env:YANBAN_SANDBOX_ENABLED = 'true' }
    if (-not $env:YANBAN_SANDBOX_REQUIRED_AT_STARTUP) { $env:YANBAN_SANDBOX_REQUIRED_AT_STARTUP = 'true' }
    $env:YANBAN_SANDBOX_PROVIDER = 'docker-sbx'
    $env:YANBAN_SANDBOX_BROKER_URL = "http://127.0.0.1:$brokerPort"
    $env:YANBAN_SANDBOX_MAX_CONCURRENT_RUNS = '1'
    $env:YANBAN_SANDBOX_CPUS = '2'
    $env:YANBAN_SANDBOX_MEMORY_LIMIT = '4GB'
    if (-not $env:YANBAN_SANDBOX_EXECUTION_TIMEOUT) {
        $env:YANBAN_SANDBOX_EXECUTION_TIMEOUT = '15m'
    }
    $env:YANBAN_SANDBOX_MAX_OUTPUT_SIZE = '20MB'
    $env:YANBAN_SANDBOX_NETWORK_ENABLED = 'false'

    if ($LaunchIdea) {
        Start-Process -FilePath $resolvedIdea -ArgumentList @($repoRoot) | Out-Null
        Write-Output 'Broker is healthy. IDEA was launched with sandbox-enabled backend environment variables.'
        Write-Output 'Start YanbanApiApplication in IDEA as usual, then start the frontend as usual.'
    } else {
        Write-Output 'Broker is healthy, but an already-running IDEA cannot inherit its temporary token.'
        Write-Output 'Recommended: stop this Broker, close IDEA, then run this script again with -LaunchIdea.'
    }
    Write-Output "BROKER_PID=$($broker.Id)"
    Write-Output "BROKER_URL=http://127.0.0.1:$brokerPort"
} catch {
    if ($broker -and -not $broker.HasExited) { Stop-Process -Id $broker.Id -Force -ErrorAction SilentlyContinue }
    throw
}
