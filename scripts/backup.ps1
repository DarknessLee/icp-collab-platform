# ---------------------------------------------------------------------------
# backup.ps1 - local backup of the ICP platform database + attachment files
# (ASCII, PowerShell 5.1).
#
#   mysqldump icp_platform (--single-transaction, utf8mb4) -> db.sql
#   copy .runtime/files                                     -> files/
#   manifest.json (timestamp, sizes, sha256, key row counts)
#
# Output: .runtime/backups/<yyyyMMdd-HHmmss>/
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backup.ps1
#   ... -Keep 5         # keep the latest 5 backups (default 10, 0 = keep all)
#   ... -OutDir D:\bak  # custom root directory
# ---------------------------------------------------------------------------
param(
    [int]$Keep = 10,
    [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$mysqldump = Join-Path $MySQLBin 'mysqldump.exe'
$mysql = Join-Path $MySQLBin 'mysql.exe'
if (-not $OutDir) { $OutDir = Join-Path $RuntimeDir 'backups' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dest = Join-Path $OutDir $stamp

Write-Step "backup: target $dest"
if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
New-Item -ItemType Directory -Path $dest -Force | Out-Null

function Invoke-MySql {
    param([string]$User, [string]$Password, [string]$Sql)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $oldPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = $Password
    $out = & $mysql ("--host=$MySQLHost") ("--port=$MySQLPort") '--protocol=TCP' ("--user=$User") '-N' '-B' $DbName '-e' $Sql 2>&1
    $code = $LASTEXITCODE
    if ($null -eq $oldPwd) { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $oldPwd }
    $ErrorActionPreference = $prev
    return [pscustomobject]@{ Exit = $code; Output = $out }
}

if (-not (Test-LocalPort -Port $MySQLPort)) {
    Write-Host "ERROR: MySQL is not listening on $MySQLHost`:$MySQLPort. Run scripts\start-local.ps1 first."
    exit 1
}

# 1. database dump
$dbFile = Join-Path $dest 'db.sql'
Write-Step 'backup: mysqldump icp_platform'
$oldPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $DbPassword
$dumpArgs = '--host=' + $MySQLHost + ' --port=' + $MySQLPort + ' --protocol=TCP --user=' + $DbUser +
            ' --single-transaction --no-tablespaces --routines --triggers --default-character-set=utf8mb4 --databases ' + $DbName +
            ' > "' + $dbFile + '"'
& cmd.exe /c ('"' + $mysqldump + '" ' + $dumpArgs) | Out-Host
$dumpCode = $LASTEXITCODE
if ($null -eq $oldPwd) { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $oldPwd }
if ($dumpCode -ne 0) { Write-Host "ERROR: mysqldump failed (exit=$dumpCode)"; exit 1 }
if (-not (Test-Path $dbFile) -or ((Get-Item $dbFile).Length -lt 1024)) {
    Write-Host 'ERROR: db.sql is missing or too small'
    exit 1
}

# 2. attachment files
$filesDest = Join-Path $dest 'files'
Write-Step "backup: copying $FilesDir"
New-Item -ItemType Directory -Path $filesDest -Force | Out-Null
$fileCount = 0
if (Test-Path $FilesDir) {
    $items = Get-ChildItem -Path $FilesDir -Recurse -File -ErrorAction SilentlyContinue
    $fileCount = @($items).Count
    if ($fileCount -gt 0) {
        Copy-Item -Path (Join-Path $FilesDir '*') -Destination $filesDest -Recurse -Force
    }
}

# 3. manifest
Write-Step 'backup: writing manifest.json'
$sql = "SELECT 'sys_user', COUNT(*) FROM sys_user WHERE deleted=0 " +
       "UNION ALL SELECT 'icp_policy', COUNT(*) FROM icp_policy WHERE deleted=0 " +
       "UNION ALL SELECT 'icp_risk', COUNT(*) FROM icp_risk WHERE deleted=0 " +
       "UNION ALL SELECT 'icp_defect', COUNT(*) FROM icp_defect WHERE deleted=0 " +
       "UNION ALL SELECT 'coll_task', COUNT(*) FROM coll_task WHERE deleted=0 " +
       "UNION ALL SELECT 'wf_business_link', COUNT(*) FROM wf_business_link;"
$counts = @{}
$res = Invoke-MySql -User $DbUser -Password $DbPassword -Sql $sql
if ($res.Exit -eq 0) {
    foreach ($line in $res.Output) {
        $text = ([string]$line).Trim()
        if ($text -match '^([a-z_]+)\s+(\d+)$') {
            $counts[$Matches[1]] = [int]$Matches[2]
        }
    }
}
$manifest = [ordered]@{
    timestamp = $stamp
    database = $DbName
    dbSqlBytes = (Get-Item $dbFile).Length
    dbSqlSha256 = (Get-FileHash -Path $dbFile -Algorithm SHA256).Hash
    fileCount = $fileCount
    rowCounts = $counts
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $dest 'manifest.json') -Encoding UTF8
$manifest | ConvertTo-Json -Depth 4 | Write-Host

# 4. retention
if ($Keep -gt 0) {
    $all = @(Get-ChildItem -Path $OutDir -Directory | Sort-Object Name -Descending)
    if ($all.Count -gt $Keep) {
        foreach ($d in ($all | Select-Object -Skip $Keep)) {
            Write-Step ('backup: removing old backup ' + $d.FullName)
            Remove-Item -Path $d.FullName -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

$mb = [math]::Round((Get-Item $dbFile).Length / 1MB, 2)
Write-Step "backup: DONE $dest (db.sql ${mb} MB, files=$fileCount)"
exit 0
