# Stop the project-local MySQL instance (port 3307) via mysqladmin shutdown.
# Never touches the system MySQL80 service on 3306.
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\mysql-stop.ps1
. "$PSScriptRoot\_env.ps1"

if (-not (Test-LocalPort -Port $MySQLPort)) {
    Write-Step "MySQL is not running on port $MySQLPort (nothing to do)"
    exit 0
}

Write-Step "Shutting down MySQL on port $MySQLPort ..."
$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$out = & (Join-Path $MySQLBin 'mysqladmin.exe') --user=root --host=$MySQLHost --port=$MySQLPort --protocol=TCP shutdown 2>&1
$code = $LASTEXITCODE
$ErrorActionPreference = $oldEap

# Wait until the port is closed AND our mysqld process really exited. The port can
# be released while mysqld is still flushing InnoDB, and starting a second mysqld
# on the same datadir then fails with "ibdata1 must be writable".
$stopped = $false
$deadline = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $deadline) {
    if (-not (Test-LocalPort -Port $MySQLPort)) {
        $own = @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" -ErrorAction SilentlyContinue |
                 Where-Object { $_.CommandLine -and ($_.CommandLine -like "*$MyIni*" -or $_.CommandLine -like "*$DataDir*") })
        if ($own.Count -eq 0) { $stopped = $true; break }
    }
    Start-Sleep -Milliseconds 500
}

if (-not $stopped) {
    Write-Host "ERROR: MySQL on port $MySQLPort did not fully stop after shutdown (exit=$code)."
    $out | ForEach-Object { Write-Host $_ }
    exit 1
}
Write-Step "MySQL stopped (exit=$code)."
exit 0
