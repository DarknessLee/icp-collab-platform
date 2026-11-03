# Initialize the project-local MySQL instance (port 3307, data dir <repo>\.runtime\mysql).
# Idempotent: if the data directory is already initialized it exits 0 without touching it.
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\mysql-init.ps1
. "$PSScriptRoot\_env.ps1"

$autoCnf = Join-Path $DataDir 'auto.cnf'
if (Test-Path $autoCnf) {
    Write-Step "MySQL data dir already initialized: $DataDir"
    exit 0
}

# Remove a partial/failed data dir (safe: path is always inside <repo>\.runtime).
if (Test-Path $DataDir) {
    Write-Step "Removing partial data dir: $DataDir"
    Remove-Item -Path $DataDir -Recurse -Force
}
New-Item -ItemType Directory -Path $DataDir -Force | Out-Null
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

$basedir = $MySQLHome.Replace('\', '/')
$datadir = $DataDir.Replace('\', '/')
$logdir = $LogDir.Replace('\', '/')

$ini = @"
[mysqld]
basedir="$basedir"
datadir="$datadir"
port=$MySQLPort
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_general_ci
lower_case_table_names=1
default-time-zone='+08:00'
log-error="$logdir/mysql-error.log"
pid-file="$datadir/mysqld.pid"

[client]
host=127.0.0.1
port=$MySQLPort
default-character-set=utf8mb4
"@
$ini | Set-Content -Path $MyIni -Encoding ASCII
Write-Step "Wrote my.ini: $MyIni"

$initLog = Join-Path $LogDir 'mysql-init.log'
$initErr = Join-Path $LogDir 'mysql-init.err.log'
Write-Step "Initializing MySQL data dir (root has empty password afterwards)..."
$proc = Start-Process -FilePath (Join-Path $MySQLBin 'mysqld.exe') `
    -ArgumentList "--defaults-file=`"$MyIni`" --initialize-insecure --console" `
    -RedirectStandardOutput $initLog -RedirectStandardError $initErr -WindowStyle Hidden -Wait -PassThru
$code = $proc.ExitCode

if (-not (Test-Path $autoCnf)) {
    Write-Host "ERROR: MySQL initialization failed (exit=$code). Tail of $initErr :"
    Get-Content -Path $initErr -Tail 30 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host $_ }
    if (Test-Path $initLog) { Get-Content -Path $initLog -Tail 20 | ForEach-Object { Write-Host $_ } }
    exit 1
}
Write-Step "MySQL initialized OK (exit=$code), data dir: $DataDir"
exit 0
