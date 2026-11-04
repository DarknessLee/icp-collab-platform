# ---------------------------------------------------------------------------
# stop-local.ps1 - stop the local stack started by start-local.ps1 (ASCII, PS 5.1).
#
#   stops frontend preview (4173) -> backend (8080) -> optionally MySQL (3307)
#
# Idempotent: missing components are reported and skipped.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\stop-local.ps1
#   ... -WithDb        # also stop the project-local MySQL instance on 3307
# ---------------------------------------------------------------------------
param(
    [switch]$WithDb
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$frontendPidFile = Join-Path $RuntimeDir 'frontend.preview.pid'
$FrontendPort = 4173
$failed = $false

function Kill-Tree([int]$ProcessId) {
    # cmd + 2>nul: taskkill must never write to stderr (PS 5.1 would turn a
    # missing/already-dead pid into a NativeCommandError and abort the script).
    $null = cmd.exe /c ('taskkill /PID ' + $ProcessId + ' /T /F 2>nul')
}

function Stop-Listener([int]$Port, [string]$pidFile, [string]$label) {
    if (-not (Test-LocalPort -Port $Port)) {
        Write-Step ($label + " is not listening on " + $Port + " (nothing to do)")
        if (Test-Path $pidFile) { Remove-Item $pidFile -Force -ErrorAction SilentlyContinue }
        return
    }
    $pidValue = $null
    if (Test-Path $pidFile) {
        $raw = (Get-Content $pidFile -Raw).Trim()
        if ($raw -match '^\d+$') { $pidValue = [int]$raw }
    }
    if ($pidValue) {
        Write-Step ('stopping ' + $label + ' process tree (pid=' + $pidValue + ') ...')
        Kill-Tree $pidValue
    }
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-LocalPort -Port $Port)) { break }
        Start-Sleep -Milliseconds 500
    }
    if (Test-LocalPort -Port $Port) {
        $lines = netstat -ano | Select-String (":" + $Port + "\s+.*LISTENING")
        foreach ($line in $lines) {
            $parts = ($line.ToString() -split '\s+') | Where-Object { $_ -ne '' }
            $listener = $parts[$parts.Count - 1]
            if ($listener -match '^\d+$') {
                if (Get-Process -Id ([int]$listener) -ErrorAction SilentlyContinue) {
                    Kill-Tree ([int]$listener)
                }
            }
        }
        Start-Sleep -Seconds 1
    }
    if (Test-LocalPort -Port $Port) {
        Write-Host ('ERROR: ' + $label + ' port ' + $Port + ' is still listening.')
        $script:failed = $true
    } else {
        Write-Step ($label + ' stopped.')
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
}

Write-Step 'stop-local: 1/3 frontend preview'
Stop-Listener -Port $FrontendPort -pidFile $frontendPidFile -label 'frontend preview'

Write-Step 'stop-local: 2/3 backend'
$stopBackend = Join-Path $PSScriptRoot 'stop-backend.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $stopBackend | Out-Host
if ($LASTEXITCODE -ne 0) { $failed = $true }

Write-Step 'stop-local: 3/3 MySQL'
if ($WithDb) {
    $stopDb = Join-Path $PSScriptRoot 'mysql-stop.ps1'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $stopDb | Out-Host
    if ($LASTEXITCODE -ne 0) { $failed = $true }
} else {
    if (Test-LocalPort -Port $MySQLPort) {
        Write-Step 'MySQL left running (use -WithDb to stop it)'
    } else {
        Write-Step 'MySQL is not running'
    }
}

if ($failed) {
    Write-Host 'stop-local: RESULT = FAIL'
    exit 1
}
Write-Step 'stop-local: DONE (frontend/backend stopped)'
exit 0
