# ---------------------------------------------------------------------------
# check-docs.ps1 - documentation consistency gate for the delivery (ASCII, PS 5.1)
#
# Checks:
#   1. docs numbering 00..14 and 16..17 present (00-..md), no duplicates
#   2. every numbered doc is non-trivial and starts with a markdown H1
#   3. relative markdown links in README/STATUS/docs/*.md resolve to real files
#      (docs/16-* is allowed to be absent until the M4 report lands)
#   4. README.md / STATUS.md / docs/12-M3-* carry the platform name + V1.0
#   5. STATUS.md records a real commit hash (7-40 hex) and the M4 stage
#   6. docs/screenshots holds >= 25 PNG and INDEX.md lists every PNG
#   7. docs/13 (deploy), docs/14 (user manual) exist
#
# Exit code: 0 = all checks pass, 1 = at least one failed.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-docs.ps1
# ---------------------------------------------------------------------------
param()

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_env.ps1"

$script:pass = 0
$script:fail = 0
$script:warn = 0
$script:problems = @()

function Check([string]$name, [bool]$cond, [string]$detail = '') {
    if ($cond) {
        $script:pass++
        Write-Host ('  PASS  ' + $name)
    } else {
        $script:fail++
        $msg = if ($detail) { $name + ' :: ' + $detail } else { $name }
        $script:problems += $msg
        Write-Host ('  FAIL  ' + $msg)
    }
}

function Warn([string]$name, [string]$detail) {
    $script:warn++
    Write-Host ('  WARN  ' + $name + ' :: ' + $detail)
}

# hex -> string helper so this ASCII script can test Chinese literals
function Cn([string]$hex) {
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i + 4 -le $hex.Length; $i += 4) {
        [void]$sb.Append([char][Convert]::ToInt32($hex.Substring($i, 4), 16))
    }
    return $sb.ToString()
}

$PlatformName = Cn '4F014E1A518563A74E0E534F540C8FD084257BA174065E7353F0'
$docsDir = Join-Path $RepoRoot 'docs'
$shotsDir = Join-Path $docsDir 'screenshots'

Write-Host '=================================================================='
Write-Host '  check-docs'
Write-Host '=================================================================='

# ------------------------------ 1. numbering ------------------------------
Write-Host '[1] docs numbering 00..14,16,17'
$numbered = @{}
$allDocs = @(Get-ChildItem -Path $docsDir -Filter *.md -File -ErrorAction SilentlyContinue)
foreach ($f in $allDocs) {
    if ($f.Name -match '^(\d{2})-') {
        $num = [int]$Matches[1]
        if ($numbered.ContainsKey($num)) {
            $numbered[$num] += 1
        } else {
            $numbered[$num] = 1
        }
    }
}
$missing = @()
foreach ($n in @(0..14) + 16 + 17) {
    if (-not $numbered.ContainsKey($n)) { $missing += ('{0:D2}' -f $n) }
}
Check 'numbered docs 00-14 + 16-17 all present' ($missing.Count -eq 0) ('missing: ' + ($missing -join ', '))
$dupes = @($numbered.Keys | Where-Object { $numbered[$_] -gt 1 } | Sort-Object)
Check 'no duplicate doc numbers' ($dupes.Count -eq 0) ('duplicated: ' + (($dupes | ForEach-Object { '{0:D2}' -f $_ }) -join ', '))
Check 'docs 13/14 delivered' ((Test-Path (Join-Path $docsDir '13-*.md')) -and (Test-Path (Join-Path $docsDir '14-*.md')))

# ------------------------------ 2. doc content ------------------------------
Write-Host '[2] doc headings'
$badDocs = @()
foreach ($n in ($numbered.Keys | Sort-Object)) {
    $file = @($allDocs | Where-Object { $_.Name -match ('^' + ('{0:D2}' -f $n) + '-') })[0]
    $text = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $firstLine = (@($text -split "`r?`n") | Where-Object { $_.Trim().Length -gt 0 })[0]
    if ($text.Length -lt 400 -or -not ($firstLine -like '# *')) { $badDocs += $file.Name }
}
Check 'every numbered doc has an H1 and real content' ($badDocs.Count -eq 0) ('suspect: ' + ($badDocs -join ', '))
$emptyShots = @(Get-ChildItem -Path $shotsDir -Filter *.png -File -ErrorAction SilentlyContinue | Where-Object { $_.Length -lt 1024 })
Check 'no empty screenshot files' ($emptyShots.Count -eq 0) ('zero-byte: ' + (($emptyShots | ForEach-Object { $_.Name }) -join ', '))

# ------------------------------ 3. relative links ------------------------------
Write-Host '[3] relative markdown links'
$mdFiles = @()
foreach ($name in @('README.md', 'STATUS.md')) {
    $p = Join-Path $RepoRoot $name
    if (Test-Path $p) { $mdFiles += (Get-Item $p) }
}
$mdFiles += @($allDocs)
$idx = Join-Path $shotsDir 'INDEX.md'
if (Test-Path $idx) { $mdFiles += (Get-Item $idx) }

$broken = @()
$checkedLinks = 0
foreach ($f in $mdFiles) {
    $text = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    foreach ($m in [regex]::Matches($text, '!?\[[^\]]*\]\(([^)]+)\)')) {
        $target = $m.Groups[1].Value.Trim()
        if ($target -match '^(https?:|mailto:|tel:|#|//)') { continue }
        $target = ($target -split '\s+')[0].Trim('<', '>')
        $target = [regex]::Replace($target, '#.*$', '')
        if (-not $target) { continue }
        $checkedLinks++
        $decoded = [System.Uri]::UnescapeDataString($target)
        $base = Split-Path -Parent $f.FullName
        $candidates = @(
            (Join-Path $base $decoded),
            (Join-Path $RepoRoot $decoded)
        )
        $found = $false
        foreach ($c in $candidates) {
            if (Test-Path -LiteralPath $c) { $found = $true; break }
        }
        if (-not $found) {
            if ($decoded -match 'docs[/\\]16-') {
                Warn 'pending M4 report link' ($f.Name + ' -> ' + $decoded)
            } else {
                $broken += ($f.Name + ' -> ' + $decoded)
            }
        }
    }
}
Check 'all relative links resolve (README/STATUS/docs)' ($broken.Count -eq 0) (($broken | Select-Object -First 5) -join ' | ')
Write-Host ('        links checked: ' + $checkedLinks)

# ------------------------------ 4. name / version ------------------------------
Write-Host '[4] platform name + version'
$nameTargets = @()
foreach ($rel in @('README.md', 'STATUS.md')) {
    $p = Join-Path $RepoRoot $rel
    if (Test-Path $p) { $nameTargets += $p }
}
$m3Report = @(Get-ChildItem -Path $docsDir -Filter '12-*.md' -File -ErrorAction SilentlyContinue)
if ($m3Report.Count -gt 0) { $nameTargets += $m3Report[0].FullName }
foreach ($p in $nameTargets) {
    $text = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
    $hasName = $text.Contains($PlatformName)
    $hasVer = $text.Contains('V1.0')
    Check ('name + V1.0 in ' + (Split-Path -Leaf $p)) ($hasName -and $hasVer) ('name=' + $hasName + ', V1.0=' + $hasVer)
}
Check 'docs/12 M3 acceptance report exists' ($m3Report.Count -gt 0)

# ------------------------------ 5. STATUS commit / stage ------------------------------
Write-Host '[5] STATUS commit + stage'
$statusPath = Join-Path $RepoRoot 'STATUS.md'
if (Test-Path $statusPath) {
    $statusText = [System.IO.File]::ReadAllText($statusPath, [System.Text.Encoding]::UTF8)
    $head = (@($statusText -split "`r?`n") | Select-Object -First 15) -join "`n"
    $sha = [regex]::Match($head, '\b[0-9a-f]{7,40}\b')
    Check 'STATUS.md records a real commit hash' $sha.Success
    Check 'STATUS.md mentions current stage M4' ($statusText.Contains('M4'))
} else {
    Check 'STATUS.md exists' $false 'missing at repository root'
}

# ------------------------------ 6. screenshots ------------------------------
Write-Host '[6] screenshots + INDEX'
$pngs = @(Get-ChildItem -Path $shotsDir -Filter *.png -File -ErrorAction SilentlyContinue)
Check ('screenshots >= 25 PNG (found ' + $pngs.Count + ')') ($pngs.Count -ge 25)
Check 'screenshots INDEX.md exists' (Test-Path $idx)
if ((Test-Path $idx) -and $pngs.Count -gt 0) {
    $indexText = [System.IO.File]::ReadAllText($idx, [System.Text.Encoding]::UTF8)
    $unlisted = @($pngs | Where-Object { -not $indexText.Contains($_.Name) } | ForEach-Object { $_.Name })
    Check 'INDEX.md lists every PNG' ($unlisted.Count -eq 0) ('unlisted: ' + (($unlisted | Select-Object -First 5) -join ', '))
    $shotEntries = [regex]::Matches($indexText, '\bS\d{2}\b').Count
    Check ('INDEX has >= 25 screenshot entries (found ' + $shotEntries + ')') ($shotEntries -ge 25)
}

# ------------------------------ summary ------------------------------
Write-Host ''
Write-Host ('check-docs: PASS=' + $script:pass + ' FAIL=' + $script:fail + ' WARN=' + $script:warn)
if ($script:fail -gt 0) {
    Write-Host 'check-docs: RESULT = FAIL'
    foreach ($p in $script:problems) { Write-Host ('  - ' + $p) }
    exit 1
}
Write-Host 'check-docs: RESULT = PASS'
exit 0
