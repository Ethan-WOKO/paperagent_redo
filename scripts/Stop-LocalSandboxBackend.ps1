[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if (-not $listener) {
    Write-Output 'Local backend is already stopped.'
    exit 0
}
if (@($listener).Count -ne 1) { throw 'Multiple listeners were found on port 8080; refusing to stop them.' }

$process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
$expected = $process -and $process.Name -eq 'java.exe' -and
    $process.CommandLine -like '*com.yanban.api.YanbanApiApplication*'
if (-not $expected) {
    throw "Port 8080 belongs to an unverified process (PID $($listener.OwningProcess)); refusing to stop it."
}

Stop-Process -Id $listener.OwningProcess
Wait-Process -Id $listener.OwningProcess -Timeout 30 -ErrorAction SilentlyContinue
for ($attempt = 0; $attempt -lt 40; $attempt++) {
    if (-not (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
        Write-Output 'Local backend stopped.'
        exit 0
    }
    Start-Sleep -Milliseconds 250
}
throw 'Backend did not stop cleanly.'
