# ---------------------------------------------------------------------------
# build.ps1 - build both deliverables and report timings/artifacts (ASCII, PS 5.1).
#
#   backend : mvn -B clean verify        (scripts\build-backend.ps1)
#   frontend: npm run build              (scripts\build-frontend.ps1)
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1
#   ... -SkipTests        # backend package without unit tests
#   ... -SkipBackend / -SkipFrontend
# ---------------------------------------------------------------------------
param(
    [switch]$SkipTests,
    [switch]$SkipBackend,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$startAll = Get-Date
$results = @()
$failed = 0

function Invoke-BuildStep([string]$label, [string]$name, [string[]]$extra = @()) {
    $script = Join-Path $PSScriptRoot $name
    if (-not (Test-Path $script)) {
        Write-Host ('ERROR: ' + $name + ' not found')
        $script:results += [pscustomobject]@{ Step = $label; Exit = 1; Seconds = 0 }
        $script:failed++
        return
    }
    Write-Step ('build: ' + $label + ' ...')
    $start = Get-Date
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script @extra
    $code = $LASTEXITCODE
    $seconds = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
    $script:results += [pscustomobject]@{ Step = $label; Exit = $code; Seconds = $seconds }
    if ($code -ne 0) {
        Write-Host ('ERROR: ' + $label + ' failed (exit=' + $code + ')')
        $script:failed++
    } else {
        Write-Step ('build: ' + $label + ' OK (' + $seconds + 's)')
    }
}

# ------------------------------ backend ------------------------------
$backendArgs = @()
if ($SkipTests) { $backendArgs += '-SkipTests' }
if (-not $SkipBackend) {
    Invoke-BuildStep 'backend (mvn -B clean verify)' 'build-backend.ps1' $backendArgs
} else {
    Write-Step 'build: backend SKIPPED (-SkipBackend)'
}

# ------------------------------ frontend ------------------------------
if (-not $SkipFrontend) {
    Invoke-BuildStep 'frontend (npm run build)' 'build-frontend.ps1'
} else {
    Write-Step 'build: frontend SKIPPED (-SkipFrontend)'
}

# ------------------------------ artifacts ------------------------------
Write-Host ''
Write-Step 'build: artifacts'
$jar = Join-Path $BackendDir 'target\icp-platform-backend.jar'
if (Test-Path $jar) {
    $mb = [math]::Round((Get-Item $jar).Length / 1MB, 1)
    Write-Host ('  backend jar : ' + $jar + '  (' + $mb + ' MB)')
} else {
    $jars = Get-ChildItem (Join-Path $BackendDir 'target') -Filter '*.jar' -ErrorAction SilentlyContinue
    if ($jars) {
        foreach ($j in $jars) { Write-Host ('  backend jar : ' + $j.FullName + '  (' + [math]::Round($j.Length / 1MB, 1) + ' MB)') }
    } else {
        Write-Host '  backend jar : NOT FOUND (target\*.jar)'
    }
}
$dist = Join-Path (Join-Path $RepoRoot 'frontend') 'dist'
if (Test-Path $dist) {
    $files = Get-ChildItem $dist -Recurse -File | Measure-Object -Property Length -Sum
    $kb = [math]::Round($files.Sum / 1024, 1)
    Write-Host ('  frontend dist: ' + $dist + '  (' + $files.Count + ' files, ' + $kb + ' KB)')
} else {
    Write-Host '  frontend dist: NOT FOUND (run without -SkipFrontend)'
}

# ------------------------------ summary ------------------------------
$elapsed = [math]::Round(((Get-Date) - $startAll).TotalSeconds, 1)
Write-Host ''
Write-Host 'build: summary'
foreach ($r in $results) {
    $status = if ($r.Exit -eq 0) { 'PASS' } else { 'FAIL' }
    Write-Host ('  ' + $status + '  ' + $r.Step + '  ' + $r.Seconds + 's')
}
Write-Host ('build: total ' + $elapsed + 's')
if ($failed -gt 0) {
    Write-Host 'build: RESULT = FAIL'
    exit 1
}
Write-Host 'build: RESULT = PASS'
exit 0
