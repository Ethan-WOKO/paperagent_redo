[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$brokerJar = (Join-Path $repoRoot 'yanban-sandbox-broker\target\yanban-sandbox-broker-0.1.0-SNAPSHOT.jar')
$sbxExecutable = Join-Path $env:LOCALAPPDATA 'DockerSandboxes\bin\sbx.exe'
$listeners = Get-NetTCPConnection -LocalPort 8091 -State Listen -ErrorAction SilentlyContinue

if (-not $listeners) {
    Write-Output 'Local Sandbox Broker is already stopped.'
    exit 0
}

if (Test-Path -LiteralPath $sbxExecutable -PathType Leaf) {
    $sandboxes = & $sbxExecutable ls --json | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Unable to verify sandbox state; refusing to stop Broker.' }
    if ($sandboxes.sandboxes.Count -gt 0) {
        throw 'One or more sandboxes are still active. Wait for execution cleanup or cancel it through the application before stopping Broker.'
    }
}

foreach ($listener in $listeners) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    $expected = $process -and $process.Name -eq 'java.exe' -and
        $process.CommandLine -like '*yanban-sandbox-broker-0.1.0-SNAPSHOT.jar*' -and
        $process.CommandLine -like "*$brokerJar*"
    if (-not $expected) {
        throw "Port 8091 belongs to an unverified process (PID $($listener.OwningProcess)); refusing to stop it."
    }
    Stop-Process -Id $listener.OwningProcess
    Wait-Process -Id $listener.OwningProcess -Timeout 20 -ErrorAction SilentlyContinue
}

if (Get-NetTCPConnection -LocalPort 8091 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Broker did not stop cleanly.'
}
Write-Output 'Local Sandbox Broker stopped. Docker infrastructure was not changed.'
