# ---------------------------------------------------------------------------
# test-api.ps1 - run the whole backend API regression chain (ASCII, PS 5.1).
#
# Order: db-validate -> test-api-m2a -> test-api-m2b -> test-api-m2d
#        -> test-api-m2c  (all on a CLEAN seed database)
#        -> clean reset again -> test-api-m2e (reports need a clean seed)
#
# The first four suites require the clean seed (policies 40 / risks 40 /
# defects 30 / tasks 80). If the current DB is not clean:
#   - use -Reset to let this script stop -> db-reset -> start first;
#   - or run scripts\reset-data.ps1 manually and then this script without -Reset.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-api.ps1
#   ... -Reset           # reset to clean seed before running (recommended)
#   ... -SkipM2e         # skip the final clean-reset + M2e suite
# ---------------------------------------------------------------------------
param(
    [switch]$Reset,
    [switch]$SkipM2e
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$script:results = @()
$startAll = Get-Date

function Sql-One([string]$query) {
    $mysql = Join-Path $MySQLBin 'mysql.exe'
    # MYSQL_PWD avoids mysql's insecure-password warning on stderr (which would
    # become a terminating NativeCommandError under $ErrorActionPreference='Stop').
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

function Test-CleanSeed {
    try {
        $policy = [int](Sql-One 'SELECT COUNT(*) FROM icp_policy WHERE deleted=0')
        $risk = [int](Sql-One 'SELECT COUNT(*) FROM icp_risk WHERE deleted=0')
        $defect = [int](Sql-One 'SELECT COUNT(*) FROM icp_defect WHERE deleted=0')
        $task = [int](Sql-One 'SELECT COUNT(*) FROM coll_task WHERE deleted=0')
    } catch {
        return $false
    }
    return (($policy -eq 40) -and ($risk -eq 40) -and ($defect -eq 30) -and ($task -eq 80))
}

function Invoke-Child([string]$name, [string[]]$extra = @()) {
    $script = Join-Path $PSScriptRoot $name
    if (-not (Test-Path $script)) { throw "missing script: $script" }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script @extra | Out-Host
    return $LASTEXITCODE
}

function Wait-Health([int]$TimeoutSeconds = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-RestMethod -Uri ('http://127.0.0.1:' + $BackendPort + '/actuator/health') -TimeoutSec 3
            if ([string]$r.status -eq 'UP') { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Reset-ToCleanSeed([string]$label) {
    Write-Step ($label + ': stop backend -> db-reset -> db-validate -> start backend')
    $null = Invoke-Child 'stop-backend.ps1'
    $code = Invoke-Child 'db-reset.ps1'
    if ($code -ne 0) { Write-Host 'ERROR: db-reset failed.'; return 1 }
    $code = Invoke-Child 'db-validate.ps1'
    if ($code -ne 0) { Write-Host 'ERROR: db-validate failed.'; return 1 }
    $code = Invoke-Child 'start-backend.ps1' @('-SkipDbStart')
    if ($code -ne 0) { Write-Host 'ERROR: start-backend failed (see .runtime\logs\backend.log).'; return 1 }
    if (-not (Wait-Health 180)) {
        Write-Host 'ERROR: backend process started but /actuator/health never became UP within 180s.'
        Write-Host '       Inspect .runtime\logs\backend.log; the start step is NOT treated as success.'
        return 1
    }
    Write-Step 'backend health UP'
    return 0
}

function Invoke-Suite([string]$name) {
    $script = Join-Path $PSScriptRoot $name
    if (-not (Test-Path $script)) {
        Write-Host ('FAIL ' + $name + ': script missing')
        $script:results += [pscustomobject]@{ Suite = $name; Exit = 1; Seconds = 0; Summary = 'missing script' }
        return 1
    }
    $log = Join-Path $LogDir ($name -replace '\.ps1$', '.log')
    $start = Get-Date
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script 2>&1 | Tee-Object -FilePath $log | Out-Host
    $code = $LASTEXITCODE
    $seconds = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
    $summary = ''
    $hit = Select-String -Path $log -Pattern 'PASS=|RESULT: passed=|SMOKE RESULT|M2A API REGRESSION PASS' -ErrorAction SilentlyContinue | Select-Object -Last 1
    if ($hit) { $summary = $hit.Line.Trim() }
    $script:results += [pscustomobject]@{ Suite = $name; Exit = $code; Seconds = $seconds; Summary = $summary }
    if ($code -eq 0) { Write-Step ('PASS ' + $name + ' (' + $seconds + 's) ' + $summary) }
    else { Write-Host ('FAIL ' + $name + ' (exit=' + $code + ', ' + $seconds + 's) ' + $summary) }
    return $code
}

# ------------------------------ preconditions ------------------------------
Write-Step 'test-api: checking clean seed baseline'
if ($Reset) {
    $code = Reset-ToCleanSeed 'test-api(-Reset)'
    if ($code -ne 0) { exit $code }
}
$clean = Test-CleanSeed
if (-not $clean) {
    Write-Host 'ERROR: database is not on the clean seed (expected policies 40 / risks 40 / defects 30 / tasks 80).'
    Write-Host '       Run: powershell -NoProfile -ExecutionPolicy Bypass -File scripts\reset-data.ps1 -SkipFlowSeed'
    Write-Host '       or re-run this script with -Reset.'
    exit 1
}
Write-Step 'test-api: clean seed OK'

# ------------------------------ phase 1: M2a-M2c ------------------------------
$script:failures = 0
$code = Invoke-Child 'db-validate.ps1'
if ($code -ne 0) {
    Write-Host 'ERROR: db-validate failed on the clean seed; fix the seed before running API suites.'
    exit 1
}

foreach ($suite in @('test-api-m2a.ps1', 'test-api-m2b.ps1', 'test-api-m2d.ps1', 'test-api-m2c.ps1')) {
    if ((Invoke-Suite $suite) -ne 0) { $script:failures++ }
}

# ------------------------------ phase 2: M2e (needs clean seed) ------------------------------
if (-not $SkipM2e) {
    $code = Reset-ToCleanSeed 'test-api M2e phase'
    if ($code -ne 0) { $script:failures++; }
    else {
        if ((Invoke-Suite 'test-api-m2e.ps1') -ne 0) { $script:failures++ }
    }
} else {
    Write-Step 'test-api: M2e phase SKIPPED (-SkipM2e); run test-api-m2e.ps1 on a clean seed separately'
}

# ------------------------------ summary ------------------------------
Write-Host ''
Write-Host 'test-api: summary'
foreach ($r in $script:results) {
    $status = if ($r.Exit -eq 0) { 'PASS' } else { 'FAIL' }
    Write-Host ('  ' + $status + '  ' + $r.Suite + '  ' + $r.Seconds + 's  ' + $r.Summary)
}
if ($script:failures -gt 0) {
    Write-Host ''
    Write-Host ('test-api: total ' + [math]::Round(((Get-Date) - $startAll).TotalSeconds, 1) + 's')
    Write-Host ('test-api: RESULT = FAIL (' + $script:failures + ' suite(s) failed)')
    exit 1
}
Write-Host ''
Write-Host ('test-api: total ' + [math]::Round(((Get-Date) - $startAll).TotalSeconds, 1) + 's')
Write-Host 'test-api: RESULT = PASS'
exit 0
