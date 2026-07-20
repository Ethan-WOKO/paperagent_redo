[CmdletBinding()]
param(
    [string]$SbxExecutable
)

$ErrorActionPreference = 'Stop'

$resolvedSbxExecutable = if (-not [string]::IsNullOrWhiteSpace($SbxExecutable)) {
    $SbxExecutable
} elseif (-not [string]::IsNullOrWhiteSpace($env:YANBAN_SBX_EXECUTABLE)) {
    $env:YANBAN_SBX_EXECUTABLE
} elseif (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    Join-Path $env:LOCALAPPDATA 'DockerSandboxes\bin\sbx.exe'
} else {
    $null
}
if ([string]::IsNullOrWhiteSpace($resolvedSbxExecutable) -or
        -not (Test-Path -LiteralPath $resolvedSbxExecutable -PathType Leaf)) {
    throw 'Official sbx executable was not found. Pass -SbxExecutable, set YANBAN_SBX_EXECUTABLE, or install it under LOCALAPPDATA\DockerSandboxes\bin.'
}
$resolvedSbxExecutable = (Resolve-Path -LiteralPath $resolvedSbxExecutable).Path

$dbPassword = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
$brokerToken = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
$dbContainer = 'yanban-agent-redo-mysql-1'
$root = Join-Path $env:TEMP ('yanban-worker14-broker-e2e-' + [guid]::NewGuid().ToString('N'))
$work = Join-Path $root 'work'
$providerHome = Join-Path $root 'provider-home'
$config = Join-Path $root 'provider-config'
$data = Join-Path $root 'provider-data'
$state = Join-Path $root 'provider-state'
$logs = Join-Path $root 'logs'
@($root, $work, $providerHome, $config, $data, $state, $logs) | ForEach-Object {
    New-Item -ItemType Directory -Path $_ | Out-Null
}
$broker = $null

function New-SignedBody([string]$key, [long]$timeoutMillis, [long]$maxOutputBytes) {
    $projectVersion = 'a' * 64
    $policyDigest = 'b' * 64
    $canonical = '{"idempotencyKey":"' + $key + '","requestDigest":"","userId":1,"projectId":1,"sessionId":1,"planId":1,"stepId":1,"fence":1,"projectVersion":"' + $projectVersion + '","policyDigest":"' + $policyDigest + '","files":{},"argv":["java","-version"],"cpus":1,"memoryBytes":1073741824,"timeoutMillis":' + $timeoutMillis + ',"maxOutputBytes":' + $maxOutputBytes + ',"networkEnabled":false}'
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try { $hash = ([BitConverter]::ToString($sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical)))).Replace('-', '').ToLowerInvariant() }
    finally { $sha256.Dispose() }
    return $canonical.Replace('"requestDigest":""', '"requestDigest":"' + $hash + '"')
}

function Wait-Terminal([string]$url, [hashtable]$headers, [string]$executionId) {
    for ($i = 0; $i -lt 300; $i++) {
        Start-Sleep -Milliseconds 500
        $view = Invoke-RestMethod -Uri ($url + '/' + $executionId) -Headers $headers -TimeoutSec 5
        if ($view.status -in @('SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'CLEANUP_FAILED')) { return $view }
    }
    throw 'real broker execution did not reach terminal state'
}

function Start-TestBroker {
    if (Get-NetTCPConnection -LocalPort 8091 -State Listen -ErrorAction SilentlyContinue) {
        throw 'refusing to start the dedicated Broker while port 8091 is already in use'
    }
    $launcher = Start-Process -FilePath java -ArgumentList @('-jar', $jar) -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    for ($i = 0; $i -lt 120; $i++) {
        $listener = Get-NetTCPConnection -LocalPort 8091 -State Listen -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($listener) {
            $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
            if (-not $process -or $process.CommandLine -notlike '*yanban-sandbox-broker-0.1.0-SNAPSHOT.jar*') {
                throw 'port 8091 was acquired by an unexpected process'
            }
            return Get-Process -Id $listener.OwningProcess
        }
        if ($launcher.HasExited) { throw 'broker launcher exited before a verified listener was created' }
        Start-Sleep -Milliseconds 250
    }
    throw 'broker listener unavailable'
}

try {
    $settings = @{}
    Get-Content -LiteralPath '.env' | ForEach-Object {
        if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $settings[$Matches[1]] = $Matches[2] }
    }
    $rootPassword = $settings['MYSQL_ROOT_PASSWORD']
    if ([string]::IsNullOrWhiteSpace($rootPassword)) { throw 'existing MySQL root credential unavailable' }
    $provision = "CREATE DATABASE IF NOT EXISTS yanban_sandbox_w14_e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'yb_w14_e2e'@'%' IDENTIFIED BY '$dbPassword'; ALTER USER 'yb_w14_e2e'@'%' IDENTIFIED BY '$dbPassword'; GRANT ALL PRIVILEGES ON yanban_sandbox_w14_e2e.* TO 'yb_w14_e2e'@'%'; FLUSH PRIVILEGES;"
    $provision | docker exec -i -e "MYSQL_PWD=$rootPassword" $dbContainer mysql -uroot --batch --skip-column-names
    if ($LASTEXITCODE -ne 0) { throw 'dedicated schema provisioning failed' }
    $dbPort = 3307
    $ready = $false
    for ($i = 0; $i -lt 120; $i++) {
        docker exec -e "MYSQL_PWD=$dbPassword" $dbContainer mysqladmin ping -uroot --silent 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'dedicated mysql not ready' }
    $tcpReady = $false
    for ($i = 0; $i -lt 60; $i++) {
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $client.Connect('127.0.0.1', [int]$dbPort)
            $tcpReady = $client.Connected
        } catch { } finally { $client.Dispose() }
        if ($tcpReady) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $tcpReady) { throw 'dedicated mysql loopback port not ready' }
    Start-Sleep -Seconds 2
    Write-Host 'PASS dedicated MySQL schema ready on loopback'

    $env:YANBAN_SANDBOX_BROKER_ENABLED = 'true'
    $env:YANBAN_SANDBOX_BROKER_MODE = 'LOCAL_ACCEPTANCE'
    $env:YANBAN_SANDBOX_BROKER_ADDRESS = '127.0.0.1'
    $env:YANBAN_SANDBOX_BROKER_REMOTE = 'false'
    $env:YANBAN_SANDBOX_BROKER_PORT = '8091'
    $env:YANBAN_SBX_EXECUTABLE = $resolvedSbxExecutable
    $env:YANBAN_SANDBOX_WORKSPACE_ROOT = $work
    $env:YANBAN_SANDBOX_BROKER_TOKEN = $brokerToken
    $env:YANBAN_SANDBOX_DB_URL = "jdbc:mysql://127.0.0.1:$dbPort/yanban_sandbox_w14_e2e?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    $env:YANBAN_SANDBOX_DB_USER = 'yb_w14_e2e'
    $env:YANBAN_SANDBOX_DB_PASSWORD = $dbPassword
    $env:YANBAN_SBX_HOME = $providerHome
    $env:YANBAN_SBX_CONFIG_HOME = $config
    $env:YANBAN_SBX_DATA_HOME = $data
    $env:YANBAN_SBX_STATE_HOME = $state
    $jar = (Resolve-Path 'yanban-sandbox-broker\target\yanban-sandbox-broker-0.1.0-SNAPSHOT.jar').Path
    $stdout = Join-Path $logs 'broker.out.log'
    $stderr = Join-Path $logs 'broker.err.log'
    $broker = Start-TestBroker
    Write-Host ('BROKER_PID=' + $broker.Id)
    Write-Host ('TEST_ROOT=' + $root)

    $headers = @{ Authorization = 'Bearer ' + $brokerToken }
    $listening = $false
    for ($i = 0; $i -lt 60; $i++) {
        if ($broker.HasExited) { break }
        $probe = [Net.Sockets.TcpClient]::new()
        try { $probe.Connect('127.0.0.1', 8091); $listening = $probe.Connected } catch { } finally { $probe.Dispose() }
        if ($listening) { break }
        Start-Sleep -Milliseconds 250
    }
    if (-not $listening) { throw 'broker listener unavailable' }
    $healthy = $false
    $lastHealthError = 'none'
    for ($i = 0; $i -lt 5; $i++) {
        if ($broker.HasExited) { break }
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8091/internal/v1/health' -Headers $headers -TimeoutSec 20
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch {
            $status = $_.Exception.Response.StatusCode.value__
            $lastHealthError = if ($status) { 'HTTP_' + $status } else { $_.Exception.GetType().Name }
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $healthy) {
        Write-Host ('HEALTH_ERROR=' + $lastHealthError)
        throw ('broker health unavailable: ' + $lastHealthError)
    }
    Write-Output 'PASS Broker health with real sbx provider'

    $idempotencyKey = 'w14-real-' + [guid]::NewGuid().ToString('N')
    $body = New-SignedBody $idempotencyKey 120000 65536
    $url = 'http://127.0.0.1:8091/internal/v1/executions'
    $dispatch = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType application/json -Body $body -TimeoutSec 10
    $duplicate = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType application/json -Body $body -TimeoutSec 10
    if ($duplicate.executionId -ne $dispatch.executionId) { throw 'duplicate dispatch created another execution' }
    Write-Output 'PASS duplicate dispatch idempotent'
    $terminal = Wait-Terminal $url $headers $dispatch.executionId
    Write-Output ('REAL_TERMINAL=' + $terminal.status)
    Write-Output ('REAL_ERROR=' + $terminal.errorCode)
    if ($terminal.status -ne 'SUCCEEDED') {
        Get-Content $stdout -Tail 150
        Get-Content $stderr -Tail 150
        throw 'real broker execution failed'
    }
    if ($terminal.receipt.provider -ne 'docker-sbx' -or $terminal.receipt.exitCode -ne 0) { throw 'invalid real receipt' }
    Write-Output 'PASS real Broker -> sbx -> receipt execution'
    $query = "SELECT COUNT(*),MAX(worker_fence),MAX(status),COUNT(receipt_digest) FROM sandbox_executions WHERE idempotency_key='$idempotencyKey';"
    $dbResult = $query | docker exec -i -e "MYSQL_PWD=$dbPassword" $dbContainer mysql -uyb_w14_e2e yanban_sandbox_w14_e2e --batch --skip-column-names
    if ($dbResult -notmatch '^1\s+1\s+SUCCEEDED\s+1$') { throw ('unexpected durable state: ' + $dbResult) }
    Write-Output 'PASS one durable execution, fence=1, one receipt'

    $recoveryKey = 'w14-recovery-' + [guid]::NewGuid().ToString('N')
    $recoveryDispatch = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType application/json -Body (New-SignedBody $recoveryKey 120000 65536) -TimeoutSec 10
    $claimed = $false
    for ($i = 0; $i -lt 120; $i++) {
        $claimState = ("SELECT status,worker_fence FROM sandbox_executions WHERE execution_id='$($recoveryDispatch.executionId)';" | docker exec -i -e "MYSQL_PWD=$dbPassword" $dbContainer mysql -uyb_w14_e2e yanban_sandbox_w14_e2e --batch --skip-column-names)
        if ($claimState -match '^(CLAIMED|MATERIALIZING|CREATED|POLICY_APPLIED|RUNNING)\s+1$') { $claimed = $true; break }
        Start-Sleep -Milliseconds 100
    }
    if (-not $claimed) { throw 'recovery execution was not claimed with fence=1' }
    Stop-Process -Id $broker.Id -Force
    Wait-Process -Id $broker.Id -Timeout 10 -ErrorAction SilentlyContinue
    "UPDATE sandbox_executions SET lease_expires_at=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 SECOND) WHERE execution_id='$($recoveryDispatch.executionId)';" | docker exec -i -e "MYSQL_PWD=$dbPassword" $dbContainer mysql -uyb_w14_e2e yanban_sandbox_w14_e2e --batch --skip-column-names
    $broker = Start-TestBroker
    $recoveryTerminal = Wait-Terminal $url $headers $recoveryDispatch.executionId
    $recoveryState = ("SELECT worker_fence,status FROM sandbox_executions WHERE execution_id='$($recoveryDispatch.executionId)';" | docker exec -i -e "MYSQL_PWD=$dbPassword" $dbContainer mysql -uyb_w14_e2e yanban_sandbox_w14_e2e --batch --skip-column-names)
    $recoveryParts = $recoveryState -split '\s+'
    if ($recoveryParts.Count -lt 2 -or [int]$recoveryParts[0] -lt 2 -or $recoveryParts[1] -notmatch '^(FAILED|TIMED_OUT|CANCELLED|SUCCEEDED)$') { throw ('lease recovery did not advance fence: ' + $recoveryState) }
    Write-Output ('PASS expired lease reclaimed with fence=2 and terminal=' + $recoveryTerminal.status)

    $concurrentKeyA = 'w14-concurrent-a-' + [guid]::NewGuid().ToString('N')
    $concurrentKeyB = 'w14-concurrent-b-' + [guid]::NewGuid().ToString('N')
    $concurrentA = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType application/json -Body (New-SignedBody $concurrentKeyA 120000 65536) -TimeoutSec 10
    $concurrentB = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType application/json -Body (New-SignedBody $concurrentKeyB 120000 65536) -TimeoutSec 10
    $maxActive = 0
    for ($i = 0; $i -lt 300; $i++) {
        $active = [int]("SELECT COUNT(*) FROM sandbox_executions WHERE execution_id IN ('$($concurrentA.executionId)','$($concurrentB.executionId)') AND status NOT IN ('ACCEPTED','SUCCEEDED','FAILED','CANCELLED','TIMED_OUT','CLEANUP_FAILED') AND lease_expires_at>CURRENT_TIMESTAMP;" | docker exec -i -e "MYSQL_PWD=$dbPassword" $dbContainer mysql -uyb_w14_e2e yanban_sandbox_w14_e2e --batch --skip-column-names)
        if ($active -gt $maxActive) { $maxActive = $active }
        $done = [int]("SELECT COUNT(*) FROM sandbox_executions WHERE execution_id IN ('$($concurrentA.executionId)','$($concurrentB.executionId)') AND status IN ('SUCCEEDED','FAILED','CANCELLED','TIMED_OUT','CLEANUP_FAILED');" | docker exec -i -e "MYSQL_PWD=$dbPassword" $dbContainer mysql -uyb_w14_e2e yanban_sandbox_w14_e2e --batch --skip-column-names)
        if ($done -eq 2) { break }
        Start-Sleep -Milliseconds 250
    }
    $concurrentTerminalA = Wait-Terminal $url $headers $concurrentA.executionId
    $concurrentTerminalB = Wait-Terminal $url $headers $concurrentB.executionId
    if ($maxActive -gt 1 -or $concurrentTerminalA.status -ne 'SUCCEEDED' -or $concurrentTerminalB.status -ne 'SUCCEEDED') { throw "single concurrency failed: maxActive=$maxActive" }
    Write-Output 'PASS two dispatches completed with maxActive=1'

    $outputKey = 'w14-output-' + [guid]::NewGuid().ToString('N')
    $outputDispatch = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType application/json -Body (New-SignedBody $outputKey 120000 1) -TimeoutSec 10
    $outputTerminal = Wait-Terminal $url $headers $outputDispatch.executionId
    $outputRejected = $outputTerminal.status -eq 'FAILED' -and $outputTerminal.errorCode -eq 'PROVIDER_REJECTED'
    $outputCleanupFailed = $outputTerminal.status -eq 'CLEANUP_FAILED' -and $outputTerminal.errorCode -eq 'CLEANUP_FAILED'
    if (-not $outputRejected -and -not $outputCleanupFailed) { throw 'output limit did not fail closed' }
    if ($outputCleanupFailed) {
        $sandboxName = 'yb-' + ([string]$outputDispatch.executionId).Replace('-', '')
        if ($sandboxName -notmatch '^yb-[0-9a-f]{32}$') { throw 'refusing to clean an unexpected sandbox name' }
        & $env:YANBAN_SBX_EXECUTABLE rm $sandboxName --force | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'output-limit sandbox cleanup failed' }
    }
    Write-Output 'PASS real output limit fail-closed'

    $timeoutKey = 'w14-timeout-' + [guid]::NewGuid().ToString('N')
    $timeoutDispatch = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType application/json -Body (New-SignedBody $timeoutKey 1 65536) -TimeoutSec 10
    $timeoutTerminal = Wait-Terminal $url $headers $timeoutDispatch.executionId
    if ($timeoutTerminal.status -ne 'TIMED_OUT' -or $timeoutTerminal.errorCode -ne 'TIMED_OUT') { throw 'timeout boundary failed' }
    Write-Output 'PASS real timeout boundary'

    $cancelKey = 'w14-cancel-' + [guid]::NewGuid().ToString('N')
    $cancelDispatch = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType application/json -Body (New-SignedBody $cancelKey 120000 65536) -TimeoutSec 10
    Invoke-RestMethod -Method Post -Uri ($url + '/' + $cancelDispatch.executionId + '/cancel?fence=1') -Headers $headers -TimeoutSec 10 | Out-Null
    $cancelTerminal = Wait-Terminal $url $headers $cancelDispatch.executionId
    if ($cancelTerminal.status -ne 'CANCELLED' -or $cancelTerminal.errorCode -ne 'CANCELLED') { throw 'cancellation boundary failed' }
    Write-Output 'PASS real cancellation boundary'
    $remaining = & $env:YANBAN_SBX_EXECUTABLE ls --json
    if ($remaining -ne '{"sandboxes":[]}') { throw ('sandbox cleanup incomplete: ' + $remaining) }
    Write-Output 'PASS Broker sandbox cleanup'
} finally {
    if ($broker) {
        Stop-Process -Id $broker.Id -Force -ErrorAction SilentlyContinue
        Wait-Process -Id $broker.Id -Timeout 10 -ErrorAction SilentlyContinue
    }
    $listeners = Get-NetTCPConnection -LocalPort 8091 -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
        if ($process -and $process.CommandLine -like '*yanban-sandbox-broker-0.1.0-SNAPSHOT.jar*') {
            Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
            Wait-Process -Id $listener.OwningProcess -Timeout 10 -ErrorAction SilentlyContinue
        } else { throw 'refusing to stop unverified process on Broker port' }
    }
    Write-Output 'Dedicated Broker process cleaned up; dedicated MySQL schema retained'
}
