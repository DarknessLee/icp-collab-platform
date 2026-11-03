# Build frontend production bundle into frontend/dist.
# Usage: powershell -ExecutionPolicy Bypass -File scripts\build-frontend.ps1
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot 'frontend'
$packageJson = Join-Path $frontendDir 'package.json'

if (-not (Test-Path $packageJson)) {
    throw "frontend/package.json not found: $packageJson"
}

Push-Location $frontendDir
try {
    if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
        Write-Host '[frontend] node_modules not found, running npm install ...'
        npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) {
            throw "npm install failed with exit code $LASTEXITCODE"
        }
    }

    $start = Get-Date
    Write-Host '[frontend] running npm run build ...'
    npm run build
    $exitCode = $LASTEXITCODE
    $elapsed = ((Get-Date) - $start).TotalSeconds

    if ($exitCode -ne 0) {
        throw "npm run build failed with exit code $exitCode"
    }

    $dist = Join-Path $frontendDir 'dist'
    if (-not (Test-Path $dist)) {
        throw "dist was not created: $dist"
    }

    $files = Get-ChildItem -Path $dist -Recurse -File
    $totalBytes = ($files | Measure-Object -Property Length -Sum).Sum
    $totalKb = [math]::Round($totalBytes / 1024, 1)

    Write-Host ("[frontend] build OK in {0:N1}s, {1} files, {2} KB, output: {3}" -f $elapsed, $files.Count, $totalKb, $dist)
} finally {
    Pop-Location
}
