# build.ps1 - thin alias for scripts\build-backend.ps1 (kept for task/README naming).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1
param(
    [switch]$SkipTests
)
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $here 'build-backend.ps1'
$buildArgs = @()
if ($SkipTests) { $buildArgs += '-SkipTests' }
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $target @buildArgs
exit $LASTEXITCODE
