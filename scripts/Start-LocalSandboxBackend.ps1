[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = Join-Path $env:LOCALAPPDATA 'Yanban\SandboxBroker'
$stdoutLog = Join-Path $runtimeRoot 'backend.out.log'
$stderrLog = Join-Path $runtimeRoot 'backend.err.log'

if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 8080 is already listening. Stop the existing backend before starting another one.'
}

# The Broker token is deliberately process-local and never persisted. Restarting the
# idle Broker here lets this PowerShell process pass the fresh token to the backend.
& (Join-Path $PSScriptRoot 'Stop-LocalSandboxBroker.ps1')
& (Join-Path $PSScriptRoot 'Start-LocalSandboxBroker.ps1')

$maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
# spring-boot:run for a single module resolves sibling modules from the local Maven
# repository. Refresh them first so runtime command policy cannot lag behind source.
& $maven -o -pl yanban-api -am install -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw 'Offline installation of current backend reactor dependencies failed.'
}
$backend = Start-Process -FilePath $maven `
    -ArgumentList @('-pl', 'yanban-api', 'spring-boot:run', '-Dspring-boot.run.profiles=dev') `
    -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog

for ($attempt = 0; $attempt -lt 180; $attempt++) {
    if ($backend.HasExited) { break }
    try {
        $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2
        if ($health.status -eq 'UP') {
            Write-Output 'Sandbox Broker and backend are healthy.'
            Write-Output "BACKEND_PID=$($backend.Id)"
            Write-Output 'BACKEND_URL=http://127.0.0.1:8080'
            Write-Output 'BROKER_URL=http://127.0.0.1:8091'
            exit 0
        }
    } catch { }
    Start-Sleep -Milliseconds 500
}

$details = @()
if (Test-Path -LiteralPath $stdoutLog) { $details += Get-Content -LiteralPath $stdoutLog -Tail 30 }
if (Test-Path -LiteralPath $stderrLog) { $details += Get-Content -LiteralPath $stderrLog -Tail 20 }
if (-not $backend.HasExited) { Stop-Process -Id $backend.Id -ErrorAction SilentlyContinue }
throw "Backend did not become healthy.$([Environment]::NewLine)$($details -join [Environment]::NewLine)"
