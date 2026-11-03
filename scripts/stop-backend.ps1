# Stop the backend started by start-backend.ps1 (kills the process tree on port 8080).
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\stop-backend.ps1
. "$PSScriptRoot\_env.ps1"

$pidFile = Join-Path $RuntimeDir 'backend.pid'

if (-not (Test-LocalPort -Port $BackendPort)) {
    Write-Step "Backend is not running on port $BackendPort (nothing to do)"
    if (Test-Path $pidFile) { Remove-Item $pidFile -Force -ErrorAction SilentlyContinue }
    exit 0
}

$pidValue = $null
if (Test-Path $pidFile) {
    $raw = (Get-Content -Path $pidFile -Raw).Trim()
    if ($raw -match '^\d+$') { $pidValue = [int]$raw }
}

if ($pidValue) {
    Write-Step "Stopping backend process tree (pid=$pidValue) ..."
    & taskkill.exe /PID $pidValue /T /F 2>&1 | Out-Null
} else {
    Write-Step "No pid file found, resolving listener on port $BackendPort ..."
    $lines = netstat -ano | Select-String (":$BackendPort\s+.*LISTENING")
    foreach ($line in $lines) {
        $parts = ($line.ToString() -split '\s+') | Where-Object { $_ -ne '' }
        $listenerPid = $parts[$parts.Count - 1]
        if ($listenerPid -match '^\d+$') {
            & taskkill.exe /PID ([int]$listenerPid) /T /F 2>&1 | Out-Null
        }
    }
}

$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
    if (-not (Test-LocalPort -Port $BackendPort)) { break }
    Start-Sleep -Milliseconds 500
}

if (Test-LocalPort -Port $BackendPort) {
    Write-Host "ERROR: port $BackendPort is still listening."
    exit 1
}
Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
Write-Step "Backend stopped."
exit 0
