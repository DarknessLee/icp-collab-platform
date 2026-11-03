# db-reset.ps1 - rebuild the project-local MySQL database from Flyway migrations.
# Steps: stop instance -> start instance -> DROP/CREATE database + app account
#        -> flyway migrate -> db-validate. Any failure exits non-zero.
# Idempotent: safe to run repeatedly; every run produces a clean, seeded database.
param(
    [switch]$SkipValidate
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $here '_env.ps1')

$mysql = Join-Path $MySQLBin 'mysql.exe'
$mysqladmin = Join-Path $MySQLBin 'mysqladmin.exe'
$flywayLog = Join-Path $LogDir 'flyway.log'
$startedAt = Get-Date

function Invoke-Native([string]$exe, [string[]]$arguments, [string]$name) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $out = & $exe @arguments 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prev
    if ($code -ne 0) {
        Write-Host ($out | Select-Object -Last 20 | Out-String)
        throw "$name failed with exit code $code"
    }
    return $out
}

Write-Step "db-reset: target $DbName on $MySQLHost`:$MySQLPort (user $DbUser)"

# 1. stop the instance if it is running
if (Test-LocalPort -Port $MySQLPort) {
    Write-Step 'db-reset: stopping MySQL instance'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'mysql-stop.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'db-reset: mysql-stop.ps1 failed' }
} else {
    Write-Step 'db-reset: MySQL instance already stopped'
}

# 2. start the instance (mysql-start validates that it was initialized)
Write-Step 'db-reset: starting MySQL instance'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'mysql-start.ps1')
if ($LASTEXITCODE -ne 0) { throw 'db-reset: mysql-start.ps1 failed' }
if (-not (Wait-LocalPort -Port $MySQLPort -TimeoutSeconds 30)) { throw 'db-reset: MySQL 3307 did not become ready' }

# 3. recreate database and application account (root has empty password on the local instance)
Write-Step "db-reset: recreating database $DbName and account $DbUser"
$sql = "DROP DATABASE IF EXISTS $DbName; " +
       "CREATE DATABASE $DbName DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci; " +
       "CREATE USER IF NOT EXISTS '$DbUser'@'127.0.0.1' IDENTIFIED BY '$DbPassword'; " +
       "CREATE USER IF NOT EXISTS '$DbUser'@'localhost' IDENTIFIED BY '$DbPassword'; " +
       "ALTER USER '$DbUser'@'127.0.0.1' IDENTIFIED BY '$DbPassword'; " +
       "ALTER USER '$DbUser'@'localhost' IDENTIFIED BY '$DbPassword'; " +
       "GRANT ALL PRIVILEGES ON $DbName.* TO '$DbUser'@'127.0.0.1'; " +
       "GRANT ALL PRIVILEGES ON $DbName.* TO '$DbUser'@'localhost'; " +
       "FLUSH PRIVILEGES;"
Invoke-Native $mysql @("--host=$MySQLHost", "--port=$MySQLPort", '--protocol=TCP', '--user=root', '-e', $sql) 'create database/account' | Out-Null

# 4. run Flyway migrations through the Maven plugin
Write-Step 'db-reset: running flyway migrate'
if (-not (Test-Path $flywayLog)) { New-Item -ItemType File -Path $flywayLog -Force | Out-Null }
Push-Location $BackendDir
try {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $flyOut = & $MvnCmd -B flyway:migrate 2>&1
    $flyCode = $LASTEXITCODE
    $ErrorActionPreference = $prev
} finally {
    Pop-Location
}
$flyOut | Out-File -FilePath $flywayLog -Encoding UTF8
if ($flyCode -ne 0) {
    Write-Host ($flyOut | Select-Object -Last 20 | Out-String)
    throw "db-reset: flyway migrate failed with exit code $flyCode (see $flywayLog)"
}
$applied = ($flyOut | Select-String -Pattern 'Successfully applied' | Select-Object -First 1)
if ($applied) { Write-Step ("db-reset: " + $applied.Line.Trim()) } else { Write-Step 'db-reset: flyway migrate finished' }

# 5. validate seed data
if (-not $SkipValidate) {
    Write-Step 'db-reset: running db-validate'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'db-validate.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'db-reset: db-validate failed - database reset rejected' }
} else {
    Write-Step 'db-reset: validation skipped (-SkipValidate)'
}

$elapsed = [int]((Get-Date) - $startedAt).TotalSeconds
Write-Step "db-reset: DONE in ${elapsed}s - database $DbName is ready"
exit 0
