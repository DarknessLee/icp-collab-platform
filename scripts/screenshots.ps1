# ---------------------------------------------------------------------------
# screenshots.ps1 - capture / validate the acceptance screenshots (ASCII, PS 5.1).
#
# Screenshots live in docs\screenshots (PNG). Acceptance requires >= 25 images.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\screenshots.ps1
#       # validate only: list the PNGs and check the count
#   ... -Take
#       # run the e2e screenshot suite first (e2e\npm run e2e:shots), then validate
#       # NOTE: run scripts\demo-reset.ps1 first so the UI shows the demo data
#       #       (60 flow instances / 40 todo / 20 urges).
# ---------------------------------------------------------------------------
param(
    [switch]$Take,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$shotsDir = Join-Path (Join-Path $RepoRoot 'docs') 'screenshots'
$minCount = 25

function Get-Shots {
    if (-not (Test-Path $shotsDir)) { return @() }
    return @(Get-ChildItem $shotsDir -Filter '*.png' -File | Sort-Object Name)
}

if ($Take) {
    $e2eDir = Join-Path $RepoRoot 'e2e'
    $e2ePackage = Join-Path $e2eDir 'package.json'
    if (-not (Test-Path $e2ePackage)) {
        Write-Host 'ERROR: cannot capture screenshots - e2e\package.json not found (task-14).'
        Write-Host '       Run scripts\demo-reset.ps1 first, then retry once the e2e harness exists.'
        exit 1
    }
    $pkg = [System.IO.File]::ReadAllText($e2ePackage, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
    $scriptNames = @()
    if ($pkg.scripts) { $scriptNames = @($pkg.scripts.PSObject.Properties.Name) }
    if (-not ($scriptNames -contains 'e2e:shots')) {
        Write-Host 'ERROR: e2e\package.json has no "e2e:shots" npm script.'
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
            Write-Step 'screenshots: npm install (e2e)'
            npm install --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { Write-Host 'ERROR: npm install failed.'; exit 1 }
        }
        Write-Step 'screenshots: npm run e2e:shots'
        npm run e2e:shots
        if ($LASTEXITCODE -ne 0) {
            Write-Host ('ERROR: screenshot suite failed (exit=' + $LASTEXITCODE + ')')
            exit 1
        }
    } finally {
        Pop-Location
    }
}

Write-Step ('screenshots: directory ' + $shotsDir)
$shots = Get-Shots
if ($shots.Count -eq 0) {
    Write-Host 'FAIL: no PNG screenshots found.'
    Write-Host '      Run scripts\demo-reset.ps1, then scripts\screenshots.ps1 -Take (needs e2e\ from task-14).'
    exit 1
}
foreach ($s in $shots) {
    $kb = [math]::Round($s.Length / 1KB, 1)
    Write-Host ('  ' + $s.Name + '  (' + $kb + ' KB)')
}
Write-Host ''
if ($shots.Count -lt $minCount) {
    Write-Host ('FAIL: found ' + $shots.Count + ' PNG(s), need at least ' + $minCount + '.')
    exit 1
}
Write-Host ('screenshots: PASS (' + $shots.Count + ' PNG >= ' + $minCount + ')')
exit 0
