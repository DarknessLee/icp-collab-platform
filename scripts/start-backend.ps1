# Start the backend in background (spring-boot:run) and wait for port 8080.
# Log: .runtime/logs/backend.log ; pid file: .runtime/backend.pid
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\start-backend.ps1
#         -NoBuild to reuse an already running instance check only
param(
    [switch]$SkipDbStart
)
. "$PSScriptRoot\_env.ps1"

$backendLog = Join-Path $LogDir 'backend.log'
$backendErr = Join-Path $LogDir 'backend.err.log'
$pidFile = Join-Path $RuntimeDir 'backend.pid'

if (Test-LocalPort -Port $BackendPort) {
    Write-Step "Backend already listening on port $BackendPort (nothing to do)"
    exit 0
}

if (-not $SkipDbStart) {
    $dbScript = Join-Path $PSScriptRoot 'mysql-start.ps1'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $dbScript
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: MySQL 3307 is not available, cannot start backend."
        exit 1
    }
}

if (Test-Path $backendLog) { Remove-Item $backendLog -Force -ErrorAction SilentlyContinue }
if (Test-Path $backendErr) { Remove-Item $backendErr -Force -ErrorAction SilentlyContinue }

Write-Step "Starting backend (mvn spring-boot:run), log -> $backendLog"
# cmd.exe quoting of a nested "path" + redirection is unreliable, so generate a
# small launcher .cmd (ASCII) and run that instead.
$launcher = Join-Path $RuntimeDir 'start-backend.cmd'
$lineCd = 'cd /d "' + $BackendDir + '"'
$lineRun = '"' + $MvnCmd + '" -B -f "' + $BackendPom + '" spring-boot:run > "' + $backendLog + '" 2>&1'
$launcherLines = @('@echo off', $lineCd, $lineRun)
Set-Content -Path $launcher -Value $launcherLines -Encoding ASCII
$proc = Start-Process -FilePath 'cmd.exe' -ArgumentList ('/c "' + $launcher + '"') -WindowStyle Hidden -PassThru
Set-Content -Path $pidFile -Value $proc.Id -Encoding ASCII

$timeout = 300
if (-not (Wait-LocalPort -Port $BackendPort -TimeoutSeconds $timeout)) {
    Write-Host "ERROR: backend did not listen on port $BackendPort within $timeout seconds."
    if (Test-Path $backendLog) {
        Write-Host "Tail of $backendLog :"
        Get-Content -Path $backendLog -Tail 60 | ForEach-Object { Write-Host $_ }
    }
    exit 1
}

Write-Step "Backend is UP: http://127.0.0.1:$BackendPort (pid=$($proc.Id))"
Write-Step "Health: http://127.0.0.1:$BackendPort/actuator/health"
Write-Step "Swagger: http://127.0.0.1:$BackendPort/swagger-ui/index.html"
exit 0
