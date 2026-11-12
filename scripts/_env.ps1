# Shared environment for all local scripts (ASCII only, PowerShell 5.1 compatible).
# Dot-source it:  . "$PSScriptRoot\_env.ps1"

$ErrorActionPreference = 'Stop'

$script:RepoRoot = Split-Path -Parent $PSScriptRoot
$script:BackendDir = Join-Path $RepoRoot 'backend'
$script:BackendPom = Join-Path $BackendDir 'pom.xml'
$script:RuntimeDir = Join-Path $RepoRoot '.runtime'
$script:LogDir = Join-Path $RuntimeDir 'logs'
$script:DataDir = Join-Path $RuntimeDir 'mysql'
$script:MyIni = Join-Path $RuntimeDir 'my.ini'
$script:FilesDir = Join-Path $RuntimeDir 'files'

$script:JavaHome = 'D:\DevTools\jdk-21.0.13'
# Locate a local MySQL server (8.x preferred, 5.7 also supported).
$script:MySQLHome = $null
$mysqlCandidates = @(
    'C:\Program Files\MySQL\MySQL Server 8.0',
    'C:\Program Files\MySQL\MySQL Server 8.4',
    'D:\DevTools\mysql-5.7.44'
)
foreach ($dir in $mysqlCandidates) {
    if (Test-Path (Join-Path $dir 'bin\mysqld.exe')) { $script:MySQLHome = $dir; break }
}
if (-not $MysqlHome -and (Test-Path 'D:\DevTools')) {
    $found = Get-ChildItem -Path 'D:\DevTools' -Directory -Filter 'mysql-*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($found) { $script:MySQLHome = $found.FullName }
}
if (-not $MySQLHome) {
    throw "MySQL server not found (looked for C:\Program Files\MySQL\MySQL Server 8.0, 8.4 and D:\DevTools\mysql-*)."
}
$script:MySQLBin = Join-Path $MySQLHome 'bin'
$script:MySQLPort = 3307
$script:MySQLHost = '127.0.0.1'
$script:DbName = 'icp_platform'
$script:DbUser = 'icp_app'
$script:DbPassword = 'Icp@2026'
$script:BackendPort = 8080

# Locate the portable Maven installed under D:\DevTools (lock to 3.9.x).
$script:MavenHome = $null
$mvnCandidates = Get-ChildItem -Path 'D:\DevTools' -Directory -Filter 'apache-maven-3.9.*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending
if ($mvnCandidates) { $script:MavenHome = $mvnCandidates[0].FullName }
if (-not $MavenHome) {
    throw "Portable Maven not found under D:\DevTools (expected apache-maven-3.9.x). Run the M1 setup first."
}
$script:MvnCmd = Join-Path $MavenHome 'bin\mvn.cmd'

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$MySQLBin;" + $env:Path
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'

foreach ($d in @($RuntimeDir, $LogDir, $FilesDir)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}

function Test-LocalPort {
    param([int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne(500)) { return $false }
        $client.EndConnect($iar)
        return $true
    } catch {
        return $false
    } finally {
        try { $client.Close() } catch { }
    }
}

function Wait-LocalPort {
    param([int]$Port, [int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-LocalPort -Port $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Write-Step {
    param([string]$Message)
    Write-Host "[icp] $Message"
}
