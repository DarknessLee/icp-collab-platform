# Build the backend: mvn clean verify (unit tests + package).
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-backend.ps1
#         -SkipTests to skip unit tests
param(
    [switch]$SkipTests
)
. "$PSScriptRoot\_env.ps1"

if (-not (Test-Path $BackendPom)) {
    Write-Host "ERROR: backend pom not found: $BackendPom"
    exit 1
}

Write-Step "Maven: $MvnCmd"
Write-Step "Building backend (mvn clean verify) ..."

$mvnArgs = @('-B', 'clean', 'verify')
if ($SkipTests) { $mvnArgs = @('-B', 'clean', 'verify', '-DskipTests') }

Push-Location $BackendDir
try {
    & $MvnCmd @mvnArgs
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($code -ne 0) {
    Write-Host "ERROR: backend build failed (exit=$code)."
    exit $code
}

$jar = Join-Path $BackendDir 'target\icp-platform-backend.jar'
if (Test-Path $jar) {
    $size = [math]::Round((Get-Item $jar).Length / 1MB, 1)
    Write-Step "BUILD OK: $jar ($size MB)"
} else {
    Write-Step "BUILD OK (jar not found at expected path, check target\*.jar)"
}
exit 0
