# ---------------------------------------------------------------------------
# restore.ps1 - restore a local backup produced by backup.ps1 (ASCII, PS 5.1).
#
# Steps:
#   1. validate the backup folder (db.sql + files/ + manifest.json)
#   2. stop the backend so no connection survives DROP/CREATE
#   3. recreate icp_platform + app account, import db.sql
#   4. restore .runtime/files
#   5. db-validate 15/15, start backend, wait for /actuator/health UP
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\restore.ps1 -Path .runtime\backups\20261104-120000
# ---------------------------------------------------------------------------
param(
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [int]$HealthTimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$mysql = Join-Path $MySQLBin 'mysql.exe'
$backupDir = (Resolve-Path $Path).Path
$dbFile = Join-Path $backupDir 'db.sql'
# NOTE: never call this $filesDir - PowerShell variables are case-insensitive, so
# it would overwrite $FilesDir from _env.ps1 and the runtime attachment folder.
$backupFilesDir = Join-Path $backupDir 'files'
$manifestFile = Join-Path $backupDir 'manifest.json'

Write-Step "restore: source $backupDir"
if (-not (Test-Path $dbFile)) { Write-Host "ERROR: db.sql not found in $backupDir"; exit 1 }
if (-not (Test-Path $manifestFile)) { Write-Host "ERROR: manifest.json not found in $backupDir"; exit 1 }

function Invoke-MySql {
    param([string]$User, [string]$Password, [string]$Sql)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $oldPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = $Password
    $out = & $mysql ("--host=$MySQLHost") ("--port=$MySQLPort") '--protocol=TCP' ("--user=$User") '-N' '-B' '-e' $Sql 2>&1
    $code = $LASTEXITCODE
    if ($null -eq $oldPwd) { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $oldPwd }
    $ErrorActionPreference = $prev
    return [pscustomobject]@{ Exit = $code; Output = $out }
}

if (-not (Test-LocalPort -Port $MySQLPort)) {
    Write-Host "ERROR: MySQL is not listening on $MySQLHost`:$MySQLPort. Run scripts\mysql-start.ps1 first."
    exit 1
}

# 1. stop backend
Write-Step 'restore: stopping backend'
$stopBackend = Join-Path $PSScriptRoot 'stop-backend.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $stopBackend | Out-Host
if ($LASTEXITCODE -ne 0) { Write-Host 'ERROR: could not stop the backend'; exit 1 }

# 2. recreate database + account
Write-Step "restore: recreating database $DbName"
$rootSql = "DROP DATABASE IF EXISTS $DbName; " +
           "CREATE DATABASE $DbName DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci; " +
           "CREATE USER IF NOT EXISTS '$DbUser'@'127.0.0.1' IDENTIFIED BY '$DbPassword'; " +
           "CREATE USER IF NOT EXISTS '$DbUser'@'localhost' IDENTIFIED BY '$DbPassword'; " +
           "ALTER USER '$DbUser'@'127.0.0.1' IDENTIFIED BY '$DbPassword'; " +
           "ALTER USER '$DbUser'@'localhost' IDENTIFIED BY '$DbPassword'; " +
           "GRANT ALL PRIVILEGES ON $DbName.* TO '$DbUser'@'127.0.0.1'; " +
           "GRANT ALL PRIVILEGES ON $DbName.* TO '$DbUser'@'localhost'; FLUSH PRIVILEGES;"
$res = Invoke-MySql -User 'root' -Password '' -Sql $rootSql
if ($res.Exit -ne 0) {
    Write-Host ($res.Output | Out-String)
    Write-Host 'ERROR: cannot recreate the database (root access denied?)'
    exit 1
}

# 3. import dump
Write-Step 'restore: importing db.sql'
$oldPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = ''
$importCmd = '"' + $mysql + '" --host=' + $MySQLHost + ' --port=' + $MySQLPort + ' --protocol=TCP --user=root --default-character-set=utf8mb4 < "' + $dbFile + '"'
& cmd.exe /c $importCmd | Out-Host
$importCode = $LASTEXITCODE
if ($null -eq $oldPwd) { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $oldPwd }
if ($importCode -ne 0) { Write-Host "ERROR: import failed (exit=$importCode)"; exit 1 }

# 4. restore attachments
Write-Step "restore: restoring $FilesDir"
if (Test-Path $backupFilesDir) {
    if (Test-Path $FilesDir) {
        Get-ChildItem -Path $FilesDir -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    } else {
        New-Item -ItemType Directory -Path $FilesDir -Force | Out-Null
    }
    $items = Get-ChildItem -Path $backupFilesDir -Force -ErrorAction SilentlyContinue
    if (@($items).Count -gt 0) {
        Copy-Item -Path (Join-Path $backupFilesDir '*') -Destination $FilesDir -Recurse -Force
    }
    Write-Step ('restore: files restored (' + (@(Get-ChildItem -Path $FilesDir -Recurse -File -ErrorAction SilentlyContinue)).Count + ' files)')
} else {
    Write-Step 'restore: no files/ folder in the backup (attachments untouched)'
}

# 5. validate + start backend
Write-Step 'restore: db-validate'
$validate = Join-Path $PSScriptRoot 'db-validate.ps1'
# Capture the child output so a operational dataset can be recognised:
# V15 pins the exact clean-seed row counts, so an otherwise valid seed backup
# (seed-flow adds policies/purchases/...) fails only V15 by design.
$validateOut = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $validate 2>&1
$validateCode = $LASTEXITCODE
$validateOut | ForEach-Object { Write-Host $_ }
if ($validateCode -ne 0) {
    $failedChecks = @($validateOut | Where-Object { ([string]$_).Trim() -match '^V\d{2}\s+.*FAIL' })
    $v15Only = ($failedChecks.Count -eq 1) -and (([string]$failedChecks[0]) -match '^V15\s')
    if ($v15Only) {
        Write-Step 'restore: NOTE - V15 (exact seed row counts) is expected to fail for a operational backup; integrity checks V01-V14 passed'
    } else {
        Write-Host 'ERROR: db-validate failed after restore'
        exit 1
    }
}

Write-Step 'restore: starting backend'
$startBackend = Join-Path $PSScriptRoot 'start-backend.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $startBackend @('-SkipDbStart') | Out-Host
if ($LASTEXITCODE -ne 0) { Write-Host 'ERROR: start-backend.ps1 failed'; exit 1 }

$deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
$health = $null
while ((Get-Date) -lt $deadline) {
    try { $health = (Invoke-RestMethod -Uri ('http://127.0.0.1:' + $BackendPort + '/actuator/health') -TimeoutSec 5).status } catch { $health = $null }
    if ($health -eq 'UP') { break }
    Start-Sleep -Seconds 2
}
if ($health -ne 'UP') {
    Write-Host 'ERROR: backend /actuator/health is not UP after restore'
    exit 1
}

# 6. summary
$manifest = Get-Content $manifestFile -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Step 'restore: DONE'
Write-Host ('  backup timestamp : ' + $manifest.timestamp)
Write-Host ('  backend health   : ' + $health + '  (pid=' + (Get-Content (Join-Path $RuntimeDir 'backend.pid') -ErrorAction SilentlyContinue) + ')')
Write-Host ('  attachments      : ' + $manifest.fileCount + ' files')
Write-Host ('  row counts       : ' + (($manifest.rowCounts.PSObject.Properties | ForEach-Object { $_.Name + '=' + $_.Value }) -join ', '))
exit 0
