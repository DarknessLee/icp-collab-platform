# Start frontend dev server (Vite) on port 5173, proxy /api to backend 8080.
# Usage: powershell -ExecutionPolicy Bypass -File scripts\dev-frontend.ps1
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot 'frontend'
$packageJson = Join-Path $frontendDir 'package.json'

if (-not (Test-Path $packageJson)) {
    throw "frontend/package.json not found: $packageJson"
}

if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    Write-Host '[frontend] node_modules not found, running npm install ...'
    Push-Location $frontendDir
    try {
        npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) {
            throw "npm install failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

Write-Host '[frontend] dev server: http://127.0.0.1:5173/'
Write-Host '[frontend] proxy: /api -> http://127.0.0.1:8080'
Write-Host '[frontend] login page: http://127.0.0.1:5173/login'

Push-Location $frontendDir
try {
    npm run dev
    if ($LASTEXITCODE -ne 0) {
        throw "npm run dev exited with code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
