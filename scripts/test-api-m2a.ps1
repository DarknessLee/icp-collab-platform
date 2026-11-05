# =============================================================================
# icp-collab-platform - M2a backend API regression (PowerShell 5.1, ASCII only)
# Runs against the real local runtime: MySQL 127.0.0.1:3307 + backend 127.0.0.1:8080
# Covers: login 6 roles, org CRUD, data scope, permission deny, audit logs,
#         file upload/download, change log. Test rows are cleaned up afterwards.
# Exit code: 0 = all passed, 1 = failures. ASCII output only (GBK console safe).
# =============================================================================
$ErrorActionPreference = 'Stop'
$Base = 'http://127.0.0.1:8080/api/v1'
$Pass = 0
$Fail = 0
$FailNames = @()
$Stamp = (Get-Date).ToString('yyMMddHHmmss')

function Write-Case([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        $script:Pass++
        Write-Output ("PASS  " + $name + $(if ($detail) { "  (" + $detail + ")" } else { "" }))
    } else {
        $script:Fail++
        $script:FailNames += $name
        Write-Output ("FAIL  " + $name + $(if ($detail) { "  (" + $detail + ")" } else { "" }))
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body,
        [string]$Token,
        [switch]$Raw
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $uri = $Base + $Path
    $params = @{ Method = $Method; Uri = $uri; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 60 }
    if ($null -ne $Body) {
        $params['ContentType'] = 'application/json'
        $params['Body'] = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    $status = 0
    $content = ''
    try {
        $resp = Invoke-WebRequest @params
        $status = [int]$resp.StatusCode
        $content = $resp.Content
    } catch {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $content = $reader.ReadToEnd()
            $reader.Dispose()
        } else {
            throw
        }
    }
    if ($Raw) {
        return [pscustomobject]@{ status = $status; content = $content }
    }
    $json = $null
    if ($content) { try { $json = $content | ConvertFrom-Json } catch { $json = $null } }
    return [pscustomobject]@{ status = $status; code = $(if ($json) { $json.code } else { $null }); data = $(if ($json) { $json.data } else { $null }); message = $(if ($json) { $json.message } else { $content }) }
}

function Get-Code([string]$Path, [string]$Token) {
    $r = Invoke-Api -Method Get -Path $Path -Token $Token
    return $r
}

function Count-MenuNodes {
    param($Nodes)
    $n = 0
    foreach ($m in $Nodes) {
        $n = $n + 1
        if ($m.children) { $n = $n + (Count-MenuNodes $m.children) }
    }
    return $n
}

function Find-MenuByPath {
    param($Nodes, [string]$Path)
    foreach ($m in $Nodes) {
        if ($m.path -eq $Path) { return $m }
        if ($m.children) {
            $found = Find-MenuByPath $m.children $Path
            if ($null -ne $found) { return $found }
        }
    }
    return $null
}

# ---------------------------------------------------------------- 0. preflight
Write-Output '=== M2a API regression ==='
$health = $null
try { $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 10 } catch { $health = $null }
Write-Case 'preflight: actuator/health UP' ($null -ne $health -and $health.status -eq 'UP') ''

# ---------------------------------------------------------------- 1. login 6 roles
$accounts = @('admin', 'icpadmin', 'auditor', 'exec', 'dept01', 'staff01')
$tokens = @{}
$logins = @{}
foreach ($acc in $accounts) {
    $r = Invoke-Api -Method Post -Path '/auth/login' -Body @{ username = $acc; password = 'Icp@2026' }
    $ok = ($r.status -eq 200 -and $r.code -eq 0 -and $r.data.accessToken)
    if ($ok) {
        $tokens[$acc] = $r.data.accessToken
        $logins[$acc] = $r.data
    }
    Write-Case ("login: " + $acc) $ok ("roles=" + $(if ($ok) { ($r.data.roles -join ',') } else { 'n/a' }))
}
if ($tokens.Count -ne 6) {
    Write-Output 'FATAL: not all accounts can log in; aborting.'
    Write-Output ("RESULT: passed=" + $Pass + " failed=" + $Fail)
    exit 1
}

# wrong password / lock counting (do not lock seed accounts: use 1 wrong attempt)
$r = Invoke-Api -Method Post -Path '/auth/login' -Body @{ username = 'staff01'; password = 'Wrong@123' }
Write-Case 'login: wrong password -> 40101' ($r.status -eq 200 -and $r.code -eq 40101) ("http=" + $r.status + " code=" + $r.code)

# ---------------------------------------------------------------- 2. menu/permission shape
$adminLogin = $logins['admin']
$adminPerms = @($adminLogin.permissions)
$adminNodes = Count-MenuNodes $adminLogin.menus
Write-Case 'menu: admin perms = 100' ($adminPerms.Count -eq 100) ("perms=" + $adminPerms.Count)
Write-Case 'menu: admin tree nodes = 46' ($adminNodes -eq 46) ("nodes=" + $adminNodes)
$newPerms = @('system:job:list', 'system:job:edit', 'system:job:run', 'system:post:add',
    'system:post:edit', 'system:post:delete', 'system:role:add', 'system:role:edit',
    'system:role:delete', 'system:menu:add', 'system:menu:edit', 'system:menu:delete',
    'system:dict:add', 'system:dict:edit', 'system:dict:delete', 'system:user:delete',
    'system:user:status')
$missing = @($newPerms | Where-Object { $adminPerms -notcontains $_ })
Write-Case 'menu: admin has all 17 new system perms' ($missing.Count -eq 0) ("missing=" + ($missing -join ','))

$jobNode = Find-MenuByPath $adminLogin.menus 'system/job'
Write-Case 'menu: admin has system/job C node (system:job:list)' ($null -ne $jobNode -and $jobNode.perms -eq 'system:job:list') ("path=" + $(if ($jobNode) { $jobNode.path } else { 'missing' }))

# ---------------------------------------------------------------- 3. admin read endpoints
$admin = $tokens['admin']
$reads = @(
    @('/depts/tree', 'dept tree'),
    @('/posts', 'posts'),
    @('/users', 'users'),
    @('/roles', 'roles'),
    @('/menus/tree', 'menus tree'),
    @('/dicts/types', 'dict types'),
    @('/configs', 'configs'),
    @('/jobs', 'jobs'),
    @('/logs/oper', 'oper log'),
    @('/logs/login', 'login log'),
    @('/logs/changes', 'change log'),
    @('/auth/online', 'online sessions')
)
foreach ($item in $reads) {
    $r = Get-Code $item[0] $admin
    Write-Case ("read admin: " + $item[1]) ($r.status -eq 200 -and $r.code -eq 0) ("code=" + $r.code)
}

# ---------------------------------------------------------------- 4. data scope (perm + row counts)
# NOTE: /users requires system:user:list which only admin holds, so per-role visible row counts are
# asserted at service layer (DataScopeServiceTest / DataScopeDbIT); here we verify filters + deny.
$r = Get-Code '/users?pageNum=1&pageSize=100' $admin
Write-Case 'datascope: admin sees 30 users (scope=4)' ($r.code -eq 0 -and [int]$r.data.total -eq 30) ("total=" + $r.data.total)
$r = Get-Code '/users?pageNum=1&pageSize=100&deptId=4' $admin
Write-Case 'datascope: admin deptId=4 filter -> 4 users' ($r.code -eq 0 -and [int]$r.data.total -eq 4) ("total=" + $r.data.total)
$r = Get-Code '/users?pageNum=1&pageSize=100&keyword=staff01' $admin
Write-Case 'datascope: admin keyword=staff01 -> 1 user' ($r.code -eq 0 -and [int]$r.data.total -eq 1) ("total=" + $r.data.total)
foreach ($acc in @('icpadmin', 'auditor', 'exec', 'dept01', 'staff01')) {
    $r = Get-Code '/users?pageNum=1&pageSize=10' $tokens[$acc]
    Write-Case ("datascope: " + $acc + " GET /users -> 403 (no system:user:list)") ($r.status -eq 403) ("http=" + $r.status)
}

# ---------------------------------------------------------------- 5. permission deny
$r = Get-Code '/users' $tokens['staff01']
Write-Case 'deny: staff01 GET /users -> 403' ($r.status -eq 403) ("http=" + $r.status)
$r = Invoke-Api -Method Post -Path '/depts' -Token $tokens['staff01'] -Body @{ parentId = 1; deptName = 'x'; deptCode = 'X'; orderNum = 99; status = 1 }
Write-Case 'deny: staff01 POST /depts -> 403' ($r.status -eq 403) ("http=" + $r.status)
$r = Invoke-Api -Method Delete -Path '/roles/1' -Token $admin
Write-Case 'deny: built-in role delete -> 40001' ($r.code -eq 40001) ("http=" + $r.status + " code=" + $r.code)
$r = Invoke-Api -Method Post -Path '/depts' -Token $admin -Body @{ parentId = 9999; deptName = 'orphan'; deptCode = 'ORPHAN'; orderNum = 99; status = 1 }
Write-Case 'deny: dept with missing parent -> 40000' ($r.code -eq 40000) ("code=" + $r.code)

# ---------------------------------------------------------------- 5b. NOT NULL column safety (must be 40000, never 500)
$r = Invoke-Api -Method Post -Path '/depts' -Token $admin -Body @{ parentId = 1; deptName = ('test_api_min_dept_' + $Stamp); orderNum = 99; status = 1 }
$minDeptId = $r.data.id
$autoCode = $r.data.deptCode
Write-Case 'notnull: create dept without deptCode -> 0 + auto code' ($r.code -eq 0 -and $minDeptId -and $autoCode -match '^D[0-9]{4,}$') ("code=" + $r.code + " deptCode=" + $autoCode)
$r = Invoke-Api -Method Put -Path ('/depts/' + $minDeptId) -Token $admin -Body @{ orderNum = 97 }
Write-Case 'notnull: partial PUT dept (no name/code) -> 0' ($r.code -eq 0) ("code=" + $r.code)
$r = Invoke-Api -Method Delete -Path ('/depts/' + $minDeptId) -Token $admin
Write-Case 'notnull: delete temp dept -> 0' ($r.code -eq 0) ("code=" + $r.code)
$r = Invoke-Api -Method Post -Path '/posts' -Token $admin -Body @{ postName = ('test_api_nocode_post_' + $Stamp) }
Write-Case 'notnull: create post without code -> 0 + auto code' ($r.code -eq 0 -and $r.data.postCode -match '^P[0-9]{3,}$') ("code=" + $r.code + " postCode=" + $r.data.postCode)
$autoPostId = $r.data.id
if ($autoPostId) { $null = Invoke-Api -Method Delete -Path ('/posts/' + $autoPostId) -Token $admin }
$r = Invoke-Api -Method Post -Path '/roles' -Token $admin -Body @{ roleName = 'x' }
Write-Case 'notnull: create role without code -> 40000' ($r.code -eq 40000) ("code=" + $r.code)
$r = Invoke-Api -Method Post -Path '/menus' -Token $admin -Body @{ menuName = 'x'; menuType = 'C' }
Write-Case 'notnull: create menu without path -> 40000' ($r.code -eq 40000) ("code=" + $r.code)
$r = Invoke-Api -Method Post -Path '/dicts/types' -Token $admin -Body @{ dictName = 'x' }
Write-Case 'notnull: create dict type without type -> 40000' ($r.code -eq 40000) ("code=" + $r.code)
$r = Invoke-Api -Method Post -Path '/users' -Token $admin -Body @{ username = ('test_api_noname_' + $Stamp) }
Write-Case 'notnull: create user without realName -> 40000' ($r.code -eq 40000) ("code=" + $r.code)

# ---------------------------------------------------------------- 6. org CRUD (admin)
$deptId = $null
$postId = $null
$userId = $null
$menuId = $null

$r = Invoke-Api -Method Post -Path '/depts' -Token $admin -Body @{ parentId = 1; deptName = ('test_api_dept_' + $Stamp); deptCode = ('TAD' + $Stamp); orderNum = 99; status = 1 }
$deptId = $r.data.id
Write-Case 'crud: create dept' ($r.code -eq 0 -and $deptId) ("id=" + $deptId)

$r = Invoke-Api -Method Put -Path ('/depts/' + $deptId) -Token $admin -Body @{ deptName = ('test_api_dept2_' + $Stamp); orderNum = 98; status = 1 }
Write-Case 'crud: update dept' ($r.code -eq 0) ("code=" + $r.code)

$r = Invoke-Api -Method Post -Path '/posts' -Token $admin -Body @{ postCode = ('TAP' + $Stamp); postName = ('test_api_post_' + $Stamp); orderNum = 99; status = 1 }
$postId = $r.data.id
Write-Case 'crud: create post' ($r.code -eq 0 -and $postId) ("id=" + $postId)

$r = Invoke-Api -Method Put -Path ('/posts/' + $postId) -Token $admin -Body @{ postName = ('test_api_post2_' + $Stamp); orderNum = 98; status = 1 }
Write-Case 'crud: update post' ($r.code -eq 0) ("code=" + $r.code)

$newUser = @{ username = ('test_api_u' + $Stamp); password = 'Test@12345'; realName = 'API Test User'; deptId = $deptId; postId = $postId; status = 1; roleIds = @(4) }
$r = Invoke-Api -Method Post -Path '/users' -Token $admin -Body $newUser
$userId = $r.data.id
Write-Case 'crud: create user (with dept/post/role)' ($r.code -eq 0 -and $userId) ("id=" + $userId)

$r = Invoke-Api -Method Post -Path '/users' -Token $admin -Body $newUser
Write-Case 'crud: duplicate username -> 40000' ($r.code -eq 40000) ("code=" + $r.code)

$r = Invoke-Api -Method Put -Path ('/users/' + $userId) -Token $admin -Body @{ realName = 'API Test User 2'; deptId = $deptId; postId = $postId; status = 1 }
Write-Case 'crud: update user' ($r.code -eq 0) ("code=" + $r.code)

$r = Invoke-Api -Method Put -Path ('/users/' + $userId + '/status') -Token $admin -Body @{ status = 0 }
Write-Case 'crud: disable user' ($r.code -eq 0) ("code=" + $r.code)
$r = Invoke-Api -Method Put -Path ('/users/' + $userId + '/status') -Token $admin -Body @{ status = 1 }
Write-Case 'crud: enable user' ($r.code -eq 0) ("code=" + $r.code)

$r = Invoke-Api -Method Put -Path ('/users/' + $userId + '/reset-password') -Token $admin -Body @{ password = 'Reset@12345' }
Write-Case 'crud: reset user password' ($r.code -eq 0) ("code=" + $r.code)

$r = Invoke-Api -Method Put -Path ('/users/' + $userId + '/roles') -Token $admin -Body @{ roleIds = @(4, 6) }
Write-Case 'crud: assign user roles' ($r.code -eq 0) ("code=" + $r.code)

$r = Get-Code ('/users/' + $userId) $admin
Write-Case 'crud: user detail hides password' ($r.code -eq 0 -and -not ($r.data.PSObject.Properties.Name -contains 'password') -and $r.data.deptName) ("deptName=" + $r.data.deptName)

$r = Invoke-Api -Method Delete -Path ('/depts/' + $deptId) -Token $admin
Write-Case 'crud: delete dept with user -> 40001' ($r.code -eq 40001) ("code=" + $r.code)

$r = Invoke-Api -Method Post -Path '/menus' -Token $admin -Body @{ parentId = 15; menuName = ('test_api_menu_' + $Stamp); menuType = 'F'; perms = ('test:api:perm' + $Stamp); orderNum = 99; visible = 1; status = 1 }
$menuId = $r.data.id
Write-Case 'crud: create menu (button under C)' ($r.code -eq 0 -and $menuId) ("id=" + $menuId)
$r = Invoke-Api -Method Delete -Path ('/menus/' + $menuId) -Token $admin
Write-Case 'crud: delete menu' ($r.code -eq 0) ("code=" + $r.code)

$r = Get-Code '/dicts/icp_risk_level/items' $admin
Write-Case 'crud: dict items by type' ($r.code -eq 0 -and $r.data.Count -ge 1) ("items=" + @($r.data).Count)

$dictType = 'test_api_dict_' + $Stamp
$r = Invoke-Api -Method Post -Path '/dicts/types' -Token $admin -Body @{ dictName = ('Test ' + $Stamp); dictType = $dictType; status = 1 }
$dictTypeId = $r.data.id
Write-Case 'crud: create dict type' ($r.code -eq 0 -and $dictTypeId) ("id=" + $dictTypeId)
$r = Invoke-Api -Method Post -Path '/dicts/items' -Token $admin -Body @{ dictType = $dictType; dictLabel = 'A'; dictValue = '1'; dictSort = 1; status = 1 }
Write-Case 'crud: create dict item' ($r.code -eq 0) ("code=" + $r.code)
$r = Invoke-Api -Method Delete -Path ('/dicts/types/' + $dictTypeId) -Token $admin
Write-Case 'crud: delete dict type with items -> 40000' ($r.code -eq 40000) ("code=" + $r.code)
$r = Get-Code ('/dicts/' + $dictType + '/items') $admin
$dictItemId = @($r.data)[0].id
$r = Invoke-Api -Method Delete -Path ('/dicts/items/' + $dictItemId) -Token $admin
Write-Case 'crud: delete dict item' ($r.code -eq 0) ("code=" + $r.code)
$r = Invoke-Api -Method Delete -Path ('/dicts/types/' + $dictTypeId) -Token $admin
Write-Case 'crud: delete dict type' ($r.code -eq 0) ("code=" + $r.code)
$r = Invoke-Api -Method Delete -Path ('/dicts/types/' + $dictTypeId) -Token $admin
Write-Case 'crud: delete dict type again -> 40400' ($r.code -eq 40400) ("code=" + $r.code)
$r = Invoke-Api -Method Delete -Path '/dicts/types/2' -Token $admin
Write-Case 'crud: delete builtin dict type -> 40001' ($r.code -eq 40001) ("code=" + $r.code)

$r = Invoke-Api -Method Put -Path ('/jobs/1/status') -Token $admin -Body @{ status = 1 }
Write-Case 'crud: job status update' ($r.code -eq 0) ("code=" + $r.code)
$r = Invoke-Api -Method Post -Path ('/jobs/1/run') -Token $admin -Body @{}
Write-Case 'crud: job run executes' ($r.code -eq 0 -and $null -ne $r.data.executed -and $null -ne $r.data.jobName) ("executed=" + $r.data.executed)

$r = Get-Code '/configs?pageNum=1&pageSize=100' $admin
$cfg = $null
foreach ($c in $r.data.list) { if ($c.configKey -eq 'sys.upload.maxSize') { $cfg = $c } }
if ($cfg) {
    $r2 = Invoke-Api -Method Put -Path ('/configs/' + $cfg.id) -Token $admin -Body @{ configValue = '30' }
    Write-Case 'crud: config value update' ($r2.code -eq 0) ("code=" + $r2.code)
    $r3 = Invoke-Api -Method Put -Path ('/configs/' + $cfg.id) -Token $admin -Body @{ configValue = '20' }
    Write-Case 'crud: config value restore 20' ($r3.code -eq 0) ("code=" + $r3.code)
} else {
    Write-Case 'crud: config value update' $false 'sys.upload.maxSize missing'
    Write-Case 'crud: config value restore 20' $false 'sys.upload.maxSize missing'
}

# ---------------------------------------------------------------- 7. cleanup org rows
$r = Invoke-Api -Method Delete -Path ('/users/' + $userId) -Token $admin
Write-Case 'cleanup: delete user' ($r.code -eq 0) ("code=" + $r.code)
$r = Invoke-Api -Method Delete -Path ('/posts/' + $postId) -Token $admin
Write-Case 'cleanup: delete post' ($r.code -eq 0) ("code=" + $r.code)
$r = Invoke-Api -Method Delete -Path ('/depts/' + $deptId) -Token $admin
Write-Case 'cleanup: delete dept' ($r.code -eq 0) ("code=" + $r.code)

# ---------------------------------------------------------------- 8. audit evidence
$r = Get-Code '/logs/oper?pageNum=1&pageSize=5&operName=admin' $admin
Write-Case 'audit: oper log has admin writes' ($r.code -eq 0 -and $r.data.total -ge 1) ("total=" + $r.data.total)
$r = Get-Code '/logs/changes?pageNum=1&pageSize=50' $admin
Write-Case 'audit: change log has before/after rows' ($r.code -eq 0 -and $r.data.total -ge 1) ("total=" + $r.data.total)
$r = Get-Code '/logs/login?pageNum=1&pageSize=5&username=admin' $admin
Write-Case 'audit: login log recorded' ($r.code -eq 0 -and $r.data.total -ge 1) ("total=" + $r.data.total)
$r = Get-Code '/logs/oper?pageNum=1&pageSize=1' $tokens['auditor']
Write-Case 'audit: auditor can read oper log' ($r.code -eq 0) ("code=" + $r.code)
$r = Get-Code '/logs/changes' $tokens['auditor']
Write-Case 'audit: auditor can read change log (seed grants system:log:change)' ($r.code -eq 0) ("code=" + $r.code)

# ---------------------------------------------------------------- 9. file upload/download
$tmp = Join-Path $env:TEMP ('icp_api_test_' + $Stamp + '.txt')
Set-Content -Path $tmp -Value 'hello-from-m2a-api-test' -Encoding ASCII
$curl = (Get-Command curl.exe -ErrorAction SilentlyContinue)
if ($curl) {
    $out = & $curl.Source -s -X POST -H ("Authorization: Bearer " + $admin) -F ("file=@" + $tmp + ";filename=test_api_" + $Stamp + ".txt") ($Base + '/files') 2>&1
    $json = $null
    try { $json = ($out -join '') | ConvertFrom-Json } catch { $json = $null }
    Write-Case 'file: txt upload rejected -> 41300' ($json -and $json.code -eq 41300) ("code=" + $(if ($json) { $json.code } else { 'n/a' }))

    $pdf = Join-Path $env:TEMP ('icp_api_test_' + $Stamp + '.pdf')
    $pdfBytes = [System.Text.Encoding]::ASCII.GetBytes("%PDF-1.4`n1 0 obj<</Type/Catalog>>endobj`ntrailer<</Root 1 0 R>>`n%%EOF")
    [System.IO.File]::WriteAllBytes($pdf, $pdfBytes)
    $out = & $curl.Source -s -X POST -H ("Authorization: Bearer " + $admin) -F ("file=@" + $pdf + ";filename=test_api_" + $Stamp + ".pdf") ($Base + '/files') 2>&1
    $json = $null
    try { $json = ($out -join '') | ConvertFrom-Json } catch { $json = $null }
    $fileId = $null
    if ($json -and $json.code -eq 0) { $fileId = $json.data.id }
    Write-Case 'file: pdf upload accepted' ($null -ne $fileId) ("id=" + $fileId)
    if ($fileId) {
        $r = Get-Code ('/files/' + $fileId) $admin
        Write-Case 'file: metadata readable' ($r.code -eq 0 -and $r.data.originalName) ("name=" + $r.data.originalName)
        $dl = Invoke-Api -Method Get -Path ('/files/' + $fileId + '/download') -Token $admin -Raw
        Write-Case 'file: download 200 + attachment' ($dl.status -eq 200) ("http=" + $dl.status)
        $pv = Invoke-Api -Method Get -Path ('/files/' + $fileId + '/preview') -Token $admin -Raw
        Write-Case 'file: preview pdf 200' ($pv.status -eq 200) ("http=" + $pv.status)
    }
    Remove-Item $pdf -ErrorAction SilentlyContinue
} else {
    Write-Case 'file: curl.exe available' $false 'curl.exe not found'
}
Remove-Item $tmp -ErrorAction SilentlyContinue

# ---------------------------------------------------------------- 10. session revoke
$r = Get-Code '/auth/online?pageNum=1&pageSize=100' $admin
$onlineTotal = 0
if ($r.data) { $onlineTotal = [int]$r.data.total }
Write-Case 'session: online list non-empty' ($r.code -eq 0 -and $onlineTotal -ge 1) ("total=" + $onlineTotal)
$r = Invoke-Api -Method Post -Path '/auth/logout' -Token $tokens['staff01']
Write-Case 'session: logout ok' ($r.code -eq 0) ("code=" + $r.code)
$r = Get-Code '/auth/me' $tokens['staff01']
Write-Case 'session: revoked token rejected -> 401' ($r.status -eq 401) ("http=" + $r.status)

# ---------------------------------------------------------------- summary
Write-Output ''
Write-Output ("RESULT: passed=" + $Pass + " failed=" + $Fail)
if ($Fail -gt 0) {
    Write-Output ("FAILED CASES: " + ($FailNames -join ' | '))
    exit 1
}
Write-Output 'M2A API REGRESSION PASS'
exit 0
