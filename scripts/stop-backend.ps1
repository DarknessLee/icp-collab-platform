# Stop the backend started by start-backend.ps1 (kills the process tree on port 8080).
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\stop-backend.ps1
. "$PSScriptRoot\_env.ps1"

$pidFile = Join-Path $RuntimeDir 'backend.pid'

# Kill a process tree without letting taskkill write to stderr: in PS 5.1 a
# missing/already-dead pid would surface as a NativeCommandError and abort the
# caller under $ErrorActionPreference='Stop'.
function Kill-Tree([int]$ProcessId) {
    $null = cmd.exe /c ('taskkill /PID ' + $ProcessId + ' /T /F 2>nul')
}

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
    Kill-Tree $pidValue
} else {
    Write-Step "No pid file found, resolving listener on port $BackendPort ..."
}

# The pid file may point at the launcher (cmd/mvn) instead of the JVM, so always
# make sure the real listener on the port and any leftover app JVM are terminated.
function Stop-AgentsOnPort {
    $lines = netstat -ano | Select-String (":$BackendPort\s+.*LISTENING")
    foreach ($line in $lines) {
        $parts = ($line.ToString() -split '\s+') | Where-Object { $_ -ne '' }
        $listenerPid = $parts[$parts.Count - 1]
        if ($listenerPid -match '^\d+$') {
            if (Get-Process -Id ([int]$listenerPid) -ErrorAction SilentlyContinue) {
                Kill-Tree ([int]$listenerPid)
            }
        }
    }
}

for ($i = 0; $i -lt 10; $i++) {
    if (-not (Test-LocalPort -Port $BackendPort)) { break }
    Stop-AgentsOnPort
    Start-Sleep -Seconds 1
}

# Last resort: leftover IcpPlatformApplication JVMs (e.g. port already released but JVM stuck).
$leftovers = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine.Contains('com.icp.IcpPlatformApplication') }
foreach ($proc in $leftovers) {
    Kill-Tree ([int]$proc.ProcessId)
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
