# ---------------------------------------------------------------------------
# test-e2e.ps1 - run the Playwright end-to-end suite (ASCII, PS 5.1).
#
# Checks:
#   - backend  http://127.0.0.1:8080/actuator/health  must be UP
#   - frontend http://127.0.0.1:4173 (preview) or http://127.0.0.1:5173 (dev)
#     must be reachable (start it first: scripts\dev-frontend.ps1 or
#     cd frontend; npm run preview -- --port 4173)
#   - e2e\package.json must exist (delivered by task-14 / frontend-eng)
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-e2e.ps1
#   ... -Shots            # additionally run the screenshot suite (npm run e2e:shots)
#   ... -SkipInstall      # do not run npm install when node_modules is missing
# ---------------------------------------------------------------------------
param(
    [switch]$Shots,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$frontendPorts = @(4173, 5173)
$e2eDir = Join-Path $RepoRoot 'e2e'
$e2ePackage = Join-Path $e2eDir 'package.json'

function Test-Health {
    try {
        $r = Invoke-RestMethod -Uri ("http://127.0.0.1:" + $BackendPort + "/actuator/health") -TimeoutSec 3
        return ([string]$r.status -eq 'UP')
    } catch {
        return $false
    }
}

Write-Step 'test-e2e: preconditions'
if (-not (Test-Health)) {
    Write-Host ('ERROR: backend is not UP on port ' + $BackendPort + '.')
    Write-Host '       Start it with: powershell -NoProfile -ExecutionPolicy Bypass -File scripts\start-backend.ps1'
    exit 1
}
Write-Step ('backend health UP on ' + $BackendPort)

$frontendPort = 0
foreach ($p in $frontendPorts) {
    if (Test-LocalPort -Port $p) { $frontendPort = $p; break }
}
if ($frontendPort -eq 0) {
    Write-Host 'ERROR: frontend is not reachable on 4173 (preview) or 5173 (dev).'
    Write-Host '       Start it first:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\dev-frontend.ps1'
    Write-Host '       (dev server 5173) or: cd frontend; npm run preview -- --port 4173'
    exit 1
}
Write-Step ('frontend reachable on ' + $frontendPort)

if (-not (Test-Path $e2ePackage)) {
    Write-Host 'NO E2E SUITE YET: e2e\package.json not found.'
    Write-Host '  The E2E harness is delivered by task-14 (frontend-eng). Once e2e\ exists:'
    Write-Host '  1) cd e2e  2) npm install  3) npm run e2e'
    Write-Host '  This script will then run the same commands automatically.'
    exit 1
}

$pkg = [System.IO.File]::ReadAllText($e2ePackage, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$scriptNames = @()
if ($pkg.scripts) { $scriptNames = @($pkg.scripts.PSObject.Properties.Name) }
if (-not ($scriptNames -contains 'e2e')) {
    Write-Host 'ERROR: e2e\package.json has no "e2e" npm script.'
    Write-Host ('       Found scripts: ' + ($scriptNames -join ', '))
    exit 1
}
if ($Shots -and -not ($scriptNames -contains 'e2e:shots')) {
    Write-Host 'ERROR: -Shots requested but e2e\package.json has no "e2e:shots" npm script.'
    Write-Host ('       Found scripts: ' + ($scriptNames -join ', '))
    exit 1
}

Push-Location $e2eDir
try {
    if (-not (Test-Path (Join-Path $e2eDir 'node_modules'))) {
        if ($SkipInstall) {
            Write-Host 'ERROR: e2e\node_modules is missing and -SkipInstall was given.'
            exit 1
        }
        Write-Step 'test-e2e: npm install (e2e)'
        npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) {
            Write-Host ('ERROR: npm install failed (exit=' + $LASTEXITCODE + ')')
            exit 1
        }
    }

    $start = Get-Date
    Write-Step 'test-e2e: npm run e2e'
    npm run e2e
    $code = $LASTEXITCODE
    $elapsed = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
    if ($code -ne 0) {
        Write-Host ('test-e2e: FAIL (exit=' + $code + ', ' + $elapsed + 's)')
        exit $code
    }
    Write-Step ('test-e2e: PASS (' + $elapsed + 's)')

    if ($Shots) {
        $startShots = Get-Date
        Write-Step 'test-e2e: npm run e2e:shots'
        npm run e2e:shots
        $shotsCode = $LASTEXITCODE
        $shotsElapsed = [math]::Round(((Get-Date) - $startShots).TotalSeconds, 1)
        if ($shotsCode -ne 0) {
            Write-Host ('test-e2e: SHOTS FAIL (exit=' + $shotsCode + ', ' + $shotsElapsed + 's)')
            exit $shotsCode
        }
        Write-Step ('test-e2e: SHOTS PASS (' + $shotsElapsed + 's)')
    }
} finally {
    Pop-Location
}

exit 0
