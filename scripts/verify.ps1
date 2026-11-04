# ---------------------------------------------------------------------------
# verify.ps1 - one-command full verification chain (ASCII, PS 5.1).
#
#   build.ps1            backend mvn clean verify + frontend npm run build
#   demo-reset.ps1       clean DB + backend + seed-flow (demo state)
#   test-api.ps1 -Reset  db-validate + M2a/M2b/M2d/M2c, then clean reset + M2e
#   demo-reset.ps1       (again) demo state for the E2E run
#   test-e2e.ps1         Playwright suite (skipped when e2e\ is missing)
#   screenshots.ps1      validate docs\screenshots >= 25 PNG (with -WithShots)
#   check-docs.ps1       docs/docs numbering/link/name/version/screenshot gate
#
# Fails fast: the first failing step stops the chain with a fix hint.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify.ps1
#   ... -SkipBuild        # reuse current artifacts
#   ... -SkipE2E          # backend-only verification (alias: -SkipE2e)
#   ... -SkipE2e          #   same switch, legacy spelling kept for scripts
#   ... -SkipShots        # do not validate docs\screenshots even with -WithShots
#   ... -WithShots        # also capture + validate screenshots
#   ... -SkipM2e          # skip the M2e phase inside test-api.ps1
#   ... -SkipDocs         # skip the check-docs gate
# ---------------------------------------------------------------------------
param(
    [switch]$SkipBuild,
    [switch]$SkipE2E,
    [switch]$SkipShots,
    [switch]$WithShots,
    [switch]$SkipM2e,
    [switch]$SkipDocs
)

# Parameters are case-insensitive in PowerShell, so the legacy spelling
# "-SkipE2e" keeps working against the canonical -SkipE2E switch.
$SkipE2e = $SkipE2E

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$script:steps = @()
$startAll = Get-Date
$failed = $false

function Write-Section([string]$title) {
    Write-Host ''
    Write-Host ('==================================================================')
    Write-Host ('  ' + $title)
    Write-Host ('==================================================================')
}

function Invoke-Step([string]$label, [string]$scriptName, [string[]]$extra = @(), [string]$hint = '') {
    $path = Join-Path $PSScriptRoot $scriptName
    if (-not (Test-Path $path)) {
        Write-Host ('SKIP ' + $label + ': ' + $scriptName + ' not found')
        $script:steps += [pscustomobject]@{ Step = $label; Exit = 0; Seconds = 0; Skipped = $true }
        return $true
    }
    Write-Section ($label + '  [' + $scriptName + ']')
    $start = Get-Date
    # Out-Host keeps the child script output off this function's return pipeline;
    # without it $ok becomes an array and the fail-fast flag is never set.
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $path @extra | Out-Host
    $code = $LASTEXITCODE
    $seconds = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
    $script:steps += [pscustomobject]@{ Step = $label; Exit = $code; Seconds = $seconds; Skipped = $false }
    if ($code -ne 0) {
        Write-Host ''
        Write-Host ('VERIFY FAILED at step: ' + $label + ' (exit=' + $code + ', ' + $seconds + 's)')
        if ($hint) { Write-Host ('FIX HINT: ' + $hint) }
        return $false
    }
    Write-Host ('STEP PASS: ' + $label + ' (' + $seconds + 's)')
    return $true
}

# ------------------------------ 1. build ------------------------------
if ($SkipBuild) {
    Write-Step 'verify: build SKIPPED (-SkipBuild)'
    $script:steps += [pscustomobject]@{ Step = 'build'; Exit = 0; Seconds = 0; Skipped = $true }
} else {
    $ok = Invoke-Step 'build (backend + frontend)' 'build.ps1' @() 'Fix compile/test failures in backend/ or frontend/ and re-run build.ps1.'
    if (-not $ok) { $failed = $true }
}

# ------------------------------ 2. demo state ------------------------------
if (-not $failed) {
    $ok = Invoke-Step 'demo-reset (clean seed + seed-flow demo state)' 'demo-reset.ps1' @() 'Check MySQL 3307 and .runtime/logs/backend.log; retry demo-reset.ps1.'
    if (-not $ok) { $failed = $true }
}

# ------------------------------ 3. API regression ------------------------------
if (-not $failed) {
    $apiArgs = @('-Reset')
    if ($SkipM2e) { $apiArgs += '-SkipM2e' }
    $ok = Invoke-Step 'test-api (M2a/M2b/M2d/M2c + M2e)' 'test-api.ps1' $apiArgs 'Inspect .runtime/logs/test-api-*.log; re-run the failing suite alone on a clean seed.'
    if (-not $ok) { $failed = $true }
}

# ------------------------------ 4. E2E + screenshots ------------------------------
$e2eReady = Test-Path (Join-Path (Join-Path $RepoRoot 'e2e') 'package.json')
if (-not $failed) {
    if ($SkipE2e) {
        Write-Step 'verify: E2E SKIPPED (-SkipE2e)'
        $script:steps += [pscustomobject]@{ Step = 'test-e2e'; Exit = 0; Seconds = 0; Skipped = $true }
    } elseif (-not $e2eReady) {
        Write-Step 'verify: E2E SKIPPED (e2e\package.json not delivered yet - task-14)'
        $script:steps += [pscustomobject]@{ Step = 'test-e2e'; Exit = 0; Seconds = 0; Skipped = $true }
    } else {
        $ok = Invoke-Step 'demo-reset (again, for E2E demo data)' 'demo-reset.ps1' @() 'Retry demo-reset.ps1, then inspect the E2E console output.'
        if (-not $ok) { $failed = $true }
    }
}

if (-not $failed -and $e2eReady -and -not $SkipE2e) {
    $e2eArgs = @()
    if ($WithShots) { $e2eArgs += '-Shots' }
    $ok = Invoke-Step 'test-e2e (Playwright)' 'test-e2e.ps1' $e2eArgs 'Start the frontend (scripts\dev-frontend.ps1 or npm run preview -- --port 4173) and check e2e/README.'
    if (-not $ok) { $failed = $true }
}

if (-not $failed -and $WithShots -and $e2eReady -and -not $SkipShots) {
    $ok = Invoke-Step 'screenshots (docs\screenshots >= 25 PNG)' 'screenshots.ps1' @() 'Re-run with -Take in demo state; inspect docs\screenshots.'
    if (-not $ok) { $failed = $true }
} elseif ($SkipShots) {
    Write-Step 'verify: screenshots validation SKIPPED (-SkipShots)'
    $script:steps += [pscustomobject]@{ Step = 'screenshots'; Exit = 0; Seconds = 0; Skipped = $true }
}

# ------------------------------ 5. docs gate ------------------------------
if (-not $failed) {
    if ($SkipDocs) {
        Write-Step 'verify: check-docs SKIPPED (-SkipDocs)'
        $script:steps += [pscustomobject]@{ Step = 'check-docs'; Exit = 0; Seconds = 0; Skipped = $true }
    } else {
        $docsScript = Join-Path $PSScriptRoot 'check-docs.ps1'
        if (-not (Test-Path $docsScript)) {
            Write-Host 'VERIFY FAILED at step: check-docs (scripts\check-docs.ps1 not delivered)'
            Write-Host 'FIX HINT: deliver scripts\check-docs.ps1 (M4 docs gate).'
            $failed = $true
        } else {
            $ok = Invoke-Step 'check-docs' 'check-docs.ps1' @() 'Fix docs/ per check-docs output.'
            if (-not $ok) { $failed = $true }
        }
    }
}

# ------------------------------ summary ------------------------------
$elapsed = [math]::Round(((Get-Date) - $startAll).TotalSeconds, 1)
Write-Host ''
Write-Host '=================================================================='
Write-Host '  verify.ps1 summary'
Write-Host '=================================================================='
foreach ($s in $script:steps) {
    $status = if ($s.Skipped) { 'SKIP' } elseif ($s.Exit -eq 0) { 'PASS' } else { 'FAIL' }
    Write-Host ('  ' + $status + '  ' + $s.Step + '  ' + $s.Seconds + 's')
}
Write-Host ('  total ' + $elapsed + 's')
if ($failed) {
    Write-Host 'verify: RESULT = FAIL'
    exit 1
}
Write-Host 'verify: RESULT = PASS'
exit 0
