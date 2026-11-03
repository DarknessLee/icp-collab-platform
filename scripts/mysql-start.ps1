# Start the project-local MySQL instance on 127.0.0.1:3307.
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\mysql-start.ps1
. "$PSScriptRoot\_env.ps1"

$autoCnf = Join-Path $DataDir 'auto.cnf'
if (-not (Test-Path $autoCnf)) {
    Write-Host "ERROR: MySQL data dir is not initialized ($DataDir). Run scripts\mysql-init.ps1 first."
    exit 1
}

if (Test-LocalPort -Port $MySQLPort) {
    Write-Step "MySQL already listening on ${MySQLHost}:$MySQLPort"
    exit 0
}

# A previous mysqld (same datadir) may still be shutting down with the port
# already closed; starting a second one would fail with "ibdata1 must be writable".
$waitDeadline = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $waitDeadline) {
    $own = @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" -ErrorAction SilentlyContinue |
             Where-Object { $_.CommandLine -and ($_.CommandLine -like "*$MyIni*" -or $_.CommandLine -like "*$DataDir*") })
    if ($own.Count -eq 0) { break }
    Write-Step "Waiting for previous mysqld process to exit ..."
    Start-Sleep -Milliseconds 500
}

Write-Step "Starting mysqld with $MyIni ..."
Start-Process -FilePath (Join-Path $MySQLBin 'mysqld.exe') -ArgumentList "--defaults-file=`"$MyIni`"" -WindowStyle Hidden | Out-Null

if (-not (Wait-LocalPort -Port $MySQLPort -TimeoutSeconds 90)) {
    Write-Host "ERROR: mysqld did not listen on port $MySQLPort within 90s."
    $errLog = Join-Path $LogDir 'mysql-error.log'
    if (Test-Path $errLog) {
        Write-Host "Tail of $errLog :"
        Get-Content -Path $errLog -Tail 30 | ForEach-Object { Write-Host $_ }
    }
    exit 1
}

$version = & (Join-Path $MySQLBin 'mysql.exe') --version
Write-Step "MySQL 3307 is UP. $version"
exit 0
