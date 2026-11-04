# Stop the project-local MySQL instance (port 3307) via mysqladmin shutdown.
# Never touches the system MySQL80 service on 3306.
#
# Reliability: mysqladmin shutdown can report success while mysqld is still
# flushing InnoDB (or fail transiently right after a fresh --initialize). We
# retry the shutdown, wait up to 2 x 60s, and as a last resort kill only the
# mysqld processes that use OUR my.ini / datadir (never the system service).
#
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\mysql-stop.ps1
. "$PSScriptRoot\_env.ps1"

function Get-OwnMysqld {
    @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" -ErrorAction SilentlyContinue |
      Where-Object { $_.CommandLine -and ($_.CommandLine -like "*$MyIni*" -or $_.CommandLine -like "*$DataDir*") })
}

function Wait-MySqlStopped([int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-LocalPort -Port $MySQLPort)) {
            if (@(Get-OwnMysqld).Count -eq 0) { return $true }
        }
        Start-Sleep -Milliseconds 500
    }
    return ((-not (Test-LocalPort -Port $MySQLPort)) -and (@(Get-OwnMysqld).Count -eq 0))
}

function Invoke-MySqlShutdown {
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $out = & (Join-Path $MySQLBin 'mysqladmin.exe') --user=root --host=$MySQLHost --port=$MySQLPort --protocol=TCP shutdown 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $oldEap
    return @{ Code = $code; Output = $out }
}

if (-not (Test-LocalPort -Port $MySQLPort)) {
    $ours = @(Get-OwnMysqld)
    if ($ours.Count -eq 0) {
        Write-Step "MySQL is not running on port $MySQLPort (nothing to do)"
        exit 0
    }
    Write-Step "MySQL port $MySQLPort is closed but $($ours.Count) project mysqld process(es) still exist; cleaning up ..."
}

$stopped = $false
$lastCode = $null
for ($attempt = 1; $attempt -le 2 -and -not $stopped; $attempt++) {
    if ($attempt -gt 1) { Write-Step "Retrying MySQL shutdown (attempt $attempt) ..." }
    else { Write-Step "Shutting down MySQL on port $MySQLPort ..." }
    $r = Invoke-MySqlShutdown
    $lastCode = $r.Code
    if ($r.Code -ne 0) { $r.Output | ForEach-Object { Write-Host ('  mysqladmin: ' + $_) } }
    $stopped = Wait-MySqlStopped -Seconds 60
}

if (-not $stopped) {
    $ours = @(Get-OwnMysqld)
    if ($ours.Count -gt 0) {
        Write-Step "Graceful shutdown timed out; terminating $($ours.Count) project mysqld process(es) ..."
        foreach ($p in $ours) {
            $null = cmd.exe /c ('taskkill /PID ' + $p.ProcessId + ' /T /F 2>nul')
        }
        Start-Sleep -Seconds 3
        $deadline = (Get-Date).AddSeconds(30)
        while ((Get-Date) -lt $deadline) {
            if (-not (Test-LocalPort -Port $MySQLPort) -and (@(Get-OwnMysqld).Count -eq 0)) { $stopped = $true; break }
            Start-Sleep -Milliseconds 500
        }
    }
}

if (-not $stopped) {
    Write-Host "ERROR: MySQL on port $MySQLPort did not fully stop after shutdown (last exit=$lastCode)."
    exit 1
}
Write-Step "MySQL stopped (exit=$lastCode)."
exit 0
