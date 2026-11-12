# ---------------------------------------------------------------------------
# start-local.ps1 - bring the whole local stack up in one command (ASCII, PS 5.1).
#
#   MySQL 3307 (init if the datadir is missing) -> database/user ensure ->
#   backend 8080 (spring-boot:run, health UP) -> frontend preview 4173 (build if
#   frontend/dist is missing) -> print URLs and predefined accounts.
#
# Idempotent: already running parts are detected and skipped with a hint.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\start-local.ps1
#   ... -SkipFrontend     # backend + database only
#   ... -SkipDb           # do not touch MySQL (use when it is started elsewhere)
# ---------------------------------------------------------------------------
param(
    [switch]$SkipFrontend,
    [switch]$SkipDb
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$frontendDir = Join-Path $RepoRoot 'frontend'
$frontendPidFile = Join-Path $RuntimeDir 'frontend.preview.pid'
$frontendLog = Join-Path $LogDir 'frontend-preview.log'
$FrontendPort = 4173

function Invoke-Child([string]$scriptName, [string[]]$extra = @()) {
    $path = Join-Path $PSScriptRoot $scriptName
    if (-not (Test-Path $path)) { throw "script not found: $path" }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $path @extra | Out-Host
    return $LASTEXITCODE
}

function Get-Health {
    try {
        $r = Invoke-RestMethod -Uri ("http://127.0.0.1:" + $BackendPort + "/actuator/health") -TimeoutSec 5
        return [string]$r.status
    } catch {
        return $null
    }
}

function Wait-BackendHealth([int]$TimeoutSeconds = 300) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ((Get-Health) -eq 'UP') { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Get-ListenerPid([int]$Port) {
    $lines = netstat -ano | Select-String (":" + $Port + "\s+.*LISTENING")
    foreach ($line in $lines) {
        $parts = ($line.ToString() -split '\s+') | Where-Object { $_ -ne '' }
        $candidate = $parts[$parts.Count - 1]
        if ($candidate -match '^\d+$') { return [int]$candidate }
    }
    return $null
}

function Invoke-MySql([string]$user, [string]$password, [string]$sql, [switch]$NoDatabase) {
    $mysql = Join-Path $MySQLBin 'mysql.exe'
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $oldPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = $password
    $args = @("--host=$MySQLHost", "--port=$MySQLPort", '--protocol=TCP', "--user=$user", '-N', '-B')
    if (-not $NoDatabase) { $args += $DbName }
    $args += @('-e', $sql)
    $out = & $mysql @args 2>&1
    $code = $LASTEXITCODE
    if ($null -eq $oldPwd) { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $oldPwd }
    $ErrorActionPreference = $prev
    return [pscustomobject]@{ Exit = $code; Output = $out }
}

Write-Step 'start-local: 1/4 MySQL 3307'
if (-not $SkipDb) {
    if (Test-LocalPort -Port $MySQLPort) {
        Write-Step "MySQL already listening on ${MySQLHost}:$MySQLPort (skip init/start)"
    } else {
        if (-not (Test-Path (Join-Path $DataDir 'auto.cnf'))) {
            Write-Step 'MySQL datadir missing -> running mysql-init.ps1 (fresh instance)'
            $code = Invoke-Child 'mysql-init.ps1'
            if ($code -ne 0) { Write-Host "ERROR: mysql-init.ps1 failed (exit=$code)"; exit 1 }
        }
        $code = Invoke-Child 'mysql-start.ps1'
        if ($code -ne 0) { Write-Host "ERROR: mysql-start.ps1 failed (exit=$code)"; exit 1 }
    }

    # Ensure the application database and account exist (non-destructive).
    $probe = Invoke-MySql -user $DbUser -password $DbPassword -sql 'SELECT 1'
    if ($probe.Exit -ne 0) {
        Write-Step "database/account $DbName / $DbUser missing -> creating (root, empty password)"
        $rootSql = "CREATE DATABASE IF NOT EXISTS $DbName DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci; " +
                   "CREATE USER IF NOT EXISTS '$DbUser'@'127.0.0.1' IDENTIFIED BY '$DbPassword'; " +
                   "CREATE USER IF NOT EXISTS '$DbUser'@'localhost' IDENTIFIED BY '$DbPassword'; " +
                   "ALTER USER '$DbUser'@'127.0.0.1' IDENTIFIED BY '$DbPassword'; " +
                   "ALTER USER '$DbUser'@'localhost' IDENTIFIED BY '$DbPassword'; " +
                   "GRANT ALL PRIVILEGES ON $DbName.* TO '$DbUser'@'127.0.0.1'; " +
                   "GRANT ALL PRIVILEGES ON $DbName.* TO '$DbUser'@'localhost'; FLUSH PRIVILEGES;"
        $res = Invoke-MySql -user 'root' -password '' -sql $rootSql -NoDatabase
        if ($res.Exit -ne 0) {
            Write-Host ($res.Output | Out-String)
            Write-Host 'ERROR: cannot create database/account (root access denied?).'
            exit 1
        }
        Write-Step "database $DbName ready; the backend Flyway migration will create the schema on first startup"
    } else {
        Write-Step "database $DbName / account $DbUser OK"
    }
} else {
    Write-Step 'MySQL step skipped (-SkipDb)'
}

Write-Step 'start-local: 2/4 backend 8080'
if (Test-LocalPort -Port $BackendPort) {
    if ((Get-Health) -eq 'UP') {
        Write-Step "backend already UP on http://127.0.0.1:$BackendPort"
    } else {
        Write-Step 'backend port is listening but health is not UP yet -> waiting ...'
        if (-not (Wait-BackendHealth 180)) {
            Write-Host 'ERROR: backend port is open but /actuator/health never became UP.'
            Write-Host 'Check .runtime/logs/backend.log ; if it is a broken instance run scripts\stop-local.ps1 first.'
            exit 1
        }
        Write-Step 'backend health: UP'
    }
} else {
    $code = Invoke-Child 'start-backend.ps1' @('-SkipDbStart')
    if ($code -ne 0) { Write-Host "ERROR: start-backend.ps1 failed (exit=$code)"; exit 1 }
    if (-not (Wait-BackendHealth 300)) {
        Write-Host 'ERROR: backend did not become healthy within 300s.'
        if (Test-Path (Join-Path $LogDir 'backend.log')) {
            Get-Content (Join-Path $LogDir 'backend.log') -Tail 60 | ForEach-Object { Write-Host $_ }
        }
        exit 1
    }
    Write-Step 'backend health: UP'
}

Write-Step 'start-local: 3/4 frontend preview 4173'
if ($SkipFrontend) {
    Write-Step 'frontend step skipped (-SkipFrontend)'
} else {
    if (Test-LocalPort -Port $FrontendPort) {
        Write-Step "frontend preview already listening on http://127.0.0.1:$FrontendPort"
    } else {
        if (-not (Test-Path (Join-Path $frontendDir 'dist\index.html'))) {
            Write-Step 'frontend/dist missing -> building frontend (build-frontend.ps1)'
            $code = Invoke-Child 'build-frontend.ps1'
            if ($code -ne 0) { Write-Host "ERROR: build-frontend.ps1 failed (exit=$code)"; exit 1 }
        }
        if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
            Write-Step 'frontend/node_modules missing -> npm install'
            Push-Location $frontendDir
            try {
                & npm install --no-audit --no-fund | Out-Host
                if ($LASTEXITCODE -ne 0) { Write-Host 'ERROR: npm install failed'; exit 1 }
            } finally {
                Pop-Location
            }
        }

        if (Test-Path $frontendLog) { Remove-Item $frontendLog -Force -ErrorAction SilentlyContinue }
        $launcher = Join-Path $RuntimeDir 'start-frontend.cmd'
        $lineCd = 'cd /d "' + $frontendDir + '"'
        $lineRun = 'npm run preview -- --port ' + $FrontendPort + ' --host 127.0.0.1 > "' + $frontendLog + '" 2>&1'
        Set-Content -Path $launcher -Value @('@echo off', $lineCd, $lineRun) -Encoding ASCII
        $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList ('/c "' + $launcher + '"') -WindowStyle Hidden -PassThru
        Set-Content -Path $frontendPidFile -Value $proc.Id -Encoding ASCII

        if (-not (Wait-LocalPort -Port $FrontendPort -TimeoutSeconds 90)) {
            Write-Host "ERROR: frontend preview did not listen on $FrontendPort within 90s."
            if (Test-Path $frontendLog) { Get-Content $frontendLog -Tail 40 | ForEach-Object { Write-Host $_ } }
            exit 1
        }
        $listener = Get-ListenerPid $FrontendPort
        if ($listener) { Set-Content -Path $frontendPidFile -Value $listener -Encoding ASCII }
        Write-Step "frontend preview is UP (listener pid=$listener, log=$frontendLog)"
    }
}

Write-Step 'start-local: 4/4 summary'
$health = Get-Health
Write-Host ''
Write-Host '=================================================================='
Write-Host '  ICP platform is ready'
Write-Host '=================================================================='
Write-Host ('  Frontend : http://127.0.0.1:' + $FrontendPort + '  (preview, production build)')
Write-Host ('  Frontend : http://127.0.0.1:5173       (dev server, run scripts\dev-frontend.ps1)')
Write-Host ('  Backend  : http://127.0.0.1:' + $BackendPort + '  (health=' + $health + ')')
Write-Host ('  MySQL    : ' + $MySQLHost + ':' + $MySQLPort + '  (db=' + $DbName + ', user=' + $DbUser + ')')
Write-Host '  Accounts : admin / icpadmin / auditor / exec / dept01-dept06 / staff01-staff20'
Write-Host '  Password : Icp@2026'
Write-Host '  Swagger  : http://127.0.0.1:8080/swagger-ui/index.html'
Write-Host '  Stop     : powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1'
Write-Host '=================================================================='
if ($health -ne 'UP') { Write-Host 'ERROR: backend health is not UP'; exit 1 }
exit 0
