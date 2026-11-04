# ---------------------------------------------------------------------------
# demo-reset.ps1 - rebuild the demo state in one shot (ASCII only, PS 5.1).
#
#   stop backend -> db-reset (Flyway + seed + db-validate) -> start backend
#   -> wait for health UP -> seed-flow.ps1 (60 flow instances / 40 todos / 20 urges)
#
# Idempotent: running it again simply rebuilds the same state.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\demo-reset.ps1
#   ... -SkipFlowSeed   rebuild the clean seed only (no workflow demo data)
# ---------------------------------------------------------------------------
param(
    [switch]$SkipFlowSeed
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

function Invoke-Child([string]$name, [string[]]$extra = @()) {
    $script = Join-Path $PSScriptRoot $name
    if (-not (Test-Path $script)) { throw "missing script: $script" }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script @extra | Out-Host
    return $LASTEXITCODE
}

function Get-HealthStatus {
    try {
        $r = Invoke-RestMethod -Uri ("http://127.0.0.1:" + $BackendPort + "/actuator/health") -TimeoutSec 3
        return [string]$r.status
    } catch {
        return ''
    }
}

function Wait-Health([int]$TimeoutSeconds = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ((Get-HealthStatus) -eq 'UP') { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Sql-One([string]$query) {
    $mysql = Join-Path $MySQLBin 'mysql.exe'
    # Use MYSQL_PWD (no --password) so mysql does not print an insecure-password
    # warning to stderr; with $ErrorActionPreference='Stop' that warning would
    # otherwise surface as a terminating NativeCommandError.
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $hadPwd = Test-Path Env:MYSQL_PWD
    $oldPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = $DbPassword
    try {
        $out = & $mysql '--host=127.0.0.1' ('--port=' + $MySQLPort) ('--user=' + $DbUser) '-N' '-e' ("USE " + $DbName + "; " + $query) 2>$null
    } finally {
        if ($hadPwd) { $env:MYSQL_PWD = $oldPwd } else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        $ErrorActionPreference = $prev
    }
    return (($out | Out-String).Trim())
}

function Get-Token([string]$user) {
    $body = @{ username = $user; password = 'Icp@2026' } | ConvertTo-Json
    $r = Invoke-RestMethod -Method Post -Uri ("http://127.0.0.1:" + $BackendPort + "/api/v1/auth/login") -ContentType 'application/json; charset=utf-8' -Body $body
    return $r.data.accessToken
}

$startAll = Get-Date
Write-Step 'demo-reset: step 1/5 stop backend'
$code = Invoke-Child 'stop-backend.ps1'
if ($code -ne 0) { Write-Step 'stop-backend returned non-zero (backend was probably not running) - continue' }

Write-Step 'demo-reset: step 2/5 db-reset (Flyway + seed)'
$code = Invoke-Child 'db-reset.ps1'
if ($code -ne 0) {
    Write-Host 'ERROR: db-reset failed. Check MySQL 3307 and .runtime/logs/.'
    exit 1
}

Write-Step 'demo-reset: step 3/5 db-validate'
$code = Invoke-Child 'db-validate.ps1'
if ($code -ne 0) {
    Write-Host 'ERROR: db-validate failed after db-reset (seed is inconsistent).'
    exit 1
}

Write-Step 'demo-reset: step 4/5 start backend'
$code = Invoke-Child 'start-backend.ps1' @('-SkipDbStart')
if ($code -ne 0) { Write-Host 'ERROR: backend failed to start. Check .runtime/logs/backend.log.'; exit 1 }
if (-not (Wait-Health 180)) {
    Write-Host 'ERROR: backend health endpoint did not become UP within 180s. Check .runtime/logs/backend.log.'
    exit 1
}
Write-Step ('backend health UP (pid=' + (Get-Content (Join-Path $RuntimeDir 'backend.pid') -ErrorAction SilentlyContinue) + ')')

$flowSummary = ''
if ($SkipFlowSeed) {
    Write-Step 'demo-reset: step 5/5 seed-flow SKIPPED (-SkipFlowSeed)'
} else {
    Write-Step 'demo-reset: step 5/5 seed-flow (60 instances / 40 todo / 20 urges)'
    $flowLog = Join-Path $LogDir 'seed-flow.log'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'seed-flow.ps1') 2>&1 | Tee-Object -FilePath $flowLog
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        Write-Host ('ERROR: seed-flow failed (exit=' + $code + '), see ' + $flowLog)
        exit 1
    }
    $flowSummary = (Select-String -Path $flowLog -Pattern 'instances total=' | Select-Object -Last 1).Line
}

# ------------------------------ baseline report ------------------------------
Write-Step 'demo-reset: baseline'
$menu = Sql-One 'SELECT COUNT(*) FROM sys_menu WHERE deleted=0'
$roleMenu = Sql-One 'SELECT COUNT(*) FROM sys_role_menu'
$config = Sql-One 'SELECT COUNT(*) FROM sys_config WHERE deleted=0'
$policy = Sql-One 'SELECT COUNT(*) FROM icp_policy WHERE deleted=0'
$risk = Sql-One 'SELECT COUNT(*) FROM icp_risk WHERE deleted=0'
$defect = Sql-One 'SELECT COUNT(*) FROM icp_defect WHERE deleted=0'
$task = Sql-One 'SELECT COUNT(*) FROM coll_task WHERE deleted=0'

Write-Host ('demo-reset: seed rows menu=' + $menu + ' role_menu=' + $roleMenu + ' config=' + $config + ' policy=' + $policy + ' risk=' + $risk + ' defect=' + $defect + ' task=' + $task)

try {
    $token = Get-Token 'admin'
    $h = @{ Authorization = 'Bearer ' + $token }
    $mon = Invoke-RestMethod -Uri ("http://127.0.0.1:" + $BackendPort + "/api/v1/workflow/monitor?pageNum=1&pageSize=1") -Headers $h
    $d = $mon.data
    Write-Host ('demo-reset: workflow total=' + $d.total + ' running=' + $d.running + ' completed=' + $d.completed + ' rejected=' + $d.rejected + ' overdue=' + $d.overdue)
    $todoIcp = Invoke-RestMethod -Uri ("http://127.0.0.1:" + $BackendPort + "/api/v1/workflow/todo?pageNum=1&pageSize=1") -Headers @{ Authorization = 'Bearer ' + (Get-Token 'icpadmin') }
    Write-Host ('demo-reset: todo icpadmin=' + $todoIcp.data.total)
} catch {
    Write-Host ('WARN: baseline API probe failed: ' + $_.Exception.Message)
}

$elapsed = [math]::Round(((Get-Date) - $startAll).TotalSeconds, 1)
Write-Host ''
Write-Host ('demo-reset: DONE in ' + $elapsed + 's (backend http://127.0.0.1:' + $BackendPort + ', MySQL ' + $MySQLPort + ')')
if ($flowSummary) { Write-Host ('demo-reset: ' + $flowSummary.Trim()) }
exit 0
