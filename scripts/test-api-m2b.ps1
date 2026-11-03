# M2b (icp domain) API contract test against real MySQL 3307 + backend 8080.
# ASCII only (PowerShell 5.1 decodes BOM-less UTF-8 as ANSI).
# Seed-dependent assertions expect a freshly rebuilt DB: run scripts/db-reset.ps1 first.
# Usage: powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-api-m2b.ps1
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080/api/v1'

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$base$Path"; Headers = $headers; UseBasicParsing = $true }
    if ($Body -ne $null) {
        $params['Body'] = ($Body | ConvertTo-Json -Depth 10 -Compress)
        $params['ContentType'] = 'application/json; charset=utf-8'
    }
    try {
        $r = Invoke-WebRequest @params
        return ($r.Content | ConvertFrom-Json)
    } catch {
        $resp = $_.Exception.Response
        if ($resp -ne $null) {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $txt = $sr.ReadToEnd()
            return ($txt | ConvertFrom-Json)
        }
        throw
    }
}

$script:pass = 0
$script:fail = 0
function Step($name, $ok, $detail) {
    if ($ok) { $script:pass = $script:pass + 1; Write-Output ("PASS {0} :: {1}" -f $name, $detail) }
    else { $script:fail = $script:fail + 1; Write-Output ("FAIL {0} :: {1}" -f $name, $detail) }
}

$adminLogin = Invoke-Api 'POST' '/auth/login' @{ username = 'admin'; password = 'Icp@2026' }
$adminToken = $adminLogin.data.accessToken
Write-Output ("admin login code=" + $adminLogin.code + " perms=" + $adminLogin.data.permissions.Count)
$roPerms = @('icp:policy:get','icp:risk:get','icp:control:get','icp:matrix:coverage','icp:evaluation:get','icp:defect:get','icp:rectify:get','icp:policy-category:list')
$hasAll = $true
foreach ($p in $roPerms) { if ($adminLogin.data.permissions -notcontains $p) { $hasAll = $false; Write-Output ("MISSING PERM " + $p) } }
Step 'admin icp read perms' $hasAll ($roPerms.Count.ToString() + ' checked')
# ---- best-effort cleanup of stale rows created by earlier API test runs ----
function Remove-StaleTestRows([string]$path, [string]$codeField, [string]$prefix, [int]$seedMax) {
    $resp = Invoke-Api 'GET' ($path + '?pageNum=1&pageSize=500') $null $adminToken
    if ($resp.code -ne 0) { return }
    foreach ($row in $resp.data.list) {
        $code = [string]$row.$codeField
        if ($code -match ('^' + $prefix + '-2026-(\d+)$')) {
            if ([int]$Matches[1] -gt $seedMax) {
                Invoke-Api 'DELETE' ($path + '/' + $row.id) $null $adminToken | Out-Null
            }
        }
    }
}
Remove-StaleTestRows '/policies' 'policyCode' 'ZD' 40
Remove-StaleTestRows '/risks' 'riskCode' 'FX' 40
Remove-StaleTestRows '/controls' 'controlCode' 'KZ' 60
Remove-StaleTestRows '/matrices' 'matrixCode' 'JC' 60
Remove-StaleTestRows '/evaluations' 'planCode' 'PJ' 4
Remove-StaleTestRows '/defects' 'defectCode' 'QX' 30


# ---- data scope (fresh seed) ----
$adminPol = Invoke-Api 'GET' '/policies?pageNum=1&pageSize=500' $null $adminToken
Step 'scope admin full policies' ($adminPol.code -eq 0 -and $adminPol.data.total -eq 40) ("total=" + $adminPol.data.total)

$d01Login = Invoke-Api 'POST' '/auth/login' @{ username = 'dept01'; password = 'Icp@2026' }
$d01Token = $d01Login.data.accessToken
$d01Pol = Invoke-Api 'GET' '/policies?pageNum=1&pageSize=500' $null $d01Token
Step 'scope dept01 own dept policies' ($d01Pol.code -eq 0 -and $d01Pol.data.total -eq 6) ("total=" + $d01Pol.data.total)
$d01Def = Invoke-Api 'GET' '/defects?pageNum=1&pageSize=500' $null $d01Token
Step 'scope dept01 own dept defects' ($d01Def.code -eq 0 -and $d01Def.data.total -eq 4) ("total=" + $d01Def.data.total)
$d01Ev = Invoke-Api 'GET' '/evaluations?pageNum=1&pageSize=500' $null $d01Token
Step 'scope dept01 no other-dept evaluations' ($d01Ev.code -eq 0 -and $d01Ev.data.total -eq 0) ("total=" + $d01Ev.data.total)

$otherPolicy = $null
foreach ($row in $adminPol.data.list) { if ($row.ownerDeptId -eq 6) { $otherPolicy = $row; break } }
$d01Other = Invoke-Api 'GET' ("/policies/" + $otherPolicy.id) $null $d01Token
Step 'scope dept01 single row out of scope' ($d01Other.code -eq 40300) ("code=" + $d01Other.code)

$s01Login = Invoke-Api 'POST' '/auth/login' @{ username = 'staff01'; password = 'Icp@2026' }
$s01Token = $s01Login.data.accessToken
$s01Pol = Invoke-Api 'GET' '/policies?pageNum=1&pageSize=500' $null $s01Token
Step 'scope staff01 self policies' ($s01Pol.code -eq 0 -and $s01Pol.data.total -eq 2) ("total=" + $s01Pol.data.total)
$s01Def = Invoke-Api 'GET' '/defects?pageNum=1&pageSize=500' $null $s01Token
Step 'scope staff01 self defects' ($s01Def.code -eq 0 -and $s01Def.data.total -eq 2) ("total=" + $s01Def.data.total)
$s01Risk = Invoke-Api 'GET' '/risks?pageNum=1&pageSize=10' $null $s01Token
Step 'staff01 risk denied' ($s01Risk.code -eq 40300) ("code=" + $s01Risk.code)
$s01Other = Invoke-Api 'GET' ("/policies/" + $otherPolicy.id) $null $s01Token
Step 'scope staff01 single row out of scope' ($s01Other.code -eq 40300) ("code=" + $s01Other.code)

# admin is READ-ONLY on internal control (docs/05 2 frozen): writes must be 40300
$adminDraft = Invoke-Api 'POST' '/policies' @{ policyName = 'Admin Test Draft'; categoryId = 1001 } $adminToken
Step 'admin icp write denied' ($adminDraft.code -eq 40300) ("code=" + $adminDraft.code)
$adminWriteCount = 0
$adminWriteSuffix = @(':add', ':edit', ':delete', ':submit', ':publish', ':abolish', ':status', ':start', ':finish', ':close', ':approve', ':reject')
foreach ($perm in $adminLogin.data.permissions) {
    foreach ($sfx in $adminWriteSuffix) {
        if ($perm.EndsWith($sfx) -and $perm.StartsWith('icp:')) { $adminWriteCount = $adminWriteCount + 1; Write-Output ("ADMIN WRITE PERM " + $perm) }
    }
}
Step 'admin icp write perms zero' ($adminWriteCount -eq 0) ("writePerms=" + $adminWriteCount)

$login = Invoke-Api 'POST' '/auth/login' @{ username = 'icpadmin'; password = 'Icp@2026' }
$token = $login.data.accessToken
Write-Output ("icpadmin login code=" + $login.code + " perms=" + $login.data.permissions.Count)
$newPerms = @('icp:policy:get','icp:policy:delete','icp:policy-category:add','icp:risk:get','icp:risk:status','icp:control:add','icp:matrix:coverage','icp:evaluation:start','icp:evaluation-task:submit','icp:workpaper:add','icp:defect:get','icp:rectify:close')
$hasAll = $true
foreach ($p in $newPerms) { if ($login.data.permissions -notcontains $p) { $hasAll = $false; Write-Output ("MISSING PERM " + $p) } }
Step 'icpadmin new perms' $hasAll ($newPerms.Count.ToString() + ' checked')

# ---- policies ----
$p = Invoke-Api 'GET' '/policies?pageNum=1&pageSize=500' $null $token
Step 'policy list pageSize500' ($p.code -eq 0 -and $p.data.total -eq 40) ("total=" + $p.data.total + " rows=" + $p.data.list.Count)
$created = Invoke-Api 'POST' '/policies' @{ policyName = 'API Test Policy'; categoryId = 1001; content = 'test content'; ownerDeptId = 2 } $token
Step 'policy create draft' ($created.code -eq 0 -and $created.data.status -eq 0 -and $created.data.policyCode -match '^ZD-2026-\d{4}$') ("code=" + $created.data.policyCode + " status=" + $created.data.status)
$policyId = $created.data.id
$badPublish = Invoke-Api 'POST' "/policies/$policyId/publish" @{} $token
Step 'publish from draft rejected' ($badPublish.code -eq 40001) ("code=" + $badPublish.code + " msg=" + $badPublish.message)
$sub = Invoke-Api 'POST' "/policies/$policyId/submit" $null $token
Step 'policy submit' ($sub.code -eq 0 -and $sub.data.status -eq 1 -and $sub.data.pendingWorkflow -eq $true) ("status=" + $sub.data.status + " pendingWorkflow=" + $sub.data.pendingWorkflow)
$pub = Invoke-Api 'POST' "/policies/$policyId/publish" @{ effectiveDate = '2026-11-01' } $token
Step 'policy publish' ($pub.code -eq 0 -and $pub.data.status -eq 2 -and $null -ne $pub.data.publishTime) ("status=" + $pub.data.status + " publishTime=" + $pub.data.publishTime)
$abol = Invoke-Api 'POST' "/policies/$policyId/abolish" $null $token
Step 'policy abolish' ($abol.code -eq 0 -and $abol.data.status -eq 3) ("status=" + $abol.data.status)
$delP = Invoke-Api 'DELETE' "/policies/$policyId" $null $token
Step 'delete non-draft rejected' ($delP.code -eq 40001) ("code=" + $delP.code)

# ---- risks ----
$r = Invoke-Api 'POST' '/risks' @{ riskName = 'API Test Risk'; categoryId = 1201; probability = 4; impact = 5; ownerDeptId = 2 } $token
Step 'risk create score20 level1' ($r.code -eq 0 -and $r.data.riskScore -eq 20 -and $r.data.riskLevel -eq 1 -and $r.data.riskCode -match '^FX-2026-\d{3}$') ("code=" + $r.data.riskCode + " score=" + $r.data.riskScore + " level=" + $r.data.riskLevel)
$rid = $r.data.id
$r2 = Invoke-Api 'POST' '/risks' @{ riskName = 'API Test Risk 6'; categoryId = 1201; probability = 2; impact = 3; ownerDeptId = 2 } $token
Step 'risk boundary 6 -> level3' ($r2.code -eq 0 -and $r2.data.riskScore -eq 6 -and $r2.data.riskLevel -eq 3) ("score=" + $r2.data.riskScore + " level=" + $r2.data.riskLevel)
$r3 = Invoke-Api 'POST' '/risks' @{ riskName = 'API Test Risk 8'; categoryId = 1201; probability = 2; impact = 4; ownerDeptId = 2 } $token
Step 'risk boundary 8 -> level2' ($r3.code -eq 0 -and $r3.data.riskScore -eq 8 -and $r3.data.riskLevel -eq 2) ("score=" + $r3.data.riskScore + " level=" + $r3.data.riskLevel)
$r4 = Invoke-Api 'POST' '/risks' @{ riskName = 'API Test Risk 15'; categoryId = 1201; probability = 3; impact = 5; ownerDeptId = 2 } $token
Step 'risk boundary 15 -> level1' ($r4.code -eq 0 -and $r4.data.riskScore -eq 15 -and $r4.data.riskLevel -eq 1) ("score=" + $r4.data.riskScore + " level=" + $r4.data.riskLevel)
$st = Invoke-Api 'PUT' "/risks/$rid/status" @{ status = 0 } $token
Step 'risk disable' ($st.code -eq 0 -and $st.data.status -eq 0) ("status=" + $st.data.status)

# ---- controls / matrices ----
$c = Invoke-Api 'POST' '/controls' @{ controlName = 'API Test Control'; controlType = 1; ownerDeptId = 2 } $token
Step 'control create' ($c.code -eq 0 -and $c.data.controlCode -match '^KZ-2026-\d{3}$') ("code=" + $c.data.controlCode)
$cid = $c.data.id
$cov = Invoke-Api 'GET' '/matrices/coverage' $null $token
$covOk = $false
if ($cov.code -eq 0 -and $cov.data.totalRisks -ge 40 -and $cov.data.coveredRisks -ge 40) {
    $expectRate = [math]::Round([double]$cov.data.coveredRisks * 100.0 / [double]$cov.data.totalRisks, 2)
    $covOk = ([math]::Abs([double]$cov.data.coverageRate - $expectRate) -lt 0.02)
}
Step 'coverage consistent' $covOk ("total=" + $cov.data.totalRisks + " covered=" + $cov.data.coveredRisks + " rate=" + $cov.data.coverageRate)
$mx = Invoke-Api 'POST' '/matrices' @{ riskId = $rid; controlId = $cid; policyId = $policyId; coverage = 1 } $token
Step 'matrix create' ($mx.code -eq 0 -and $mx.data.matrixCode -match '^JC-2026-\d{3}$') ("code=" + $mx.data.matrixCode)
$mxDup = Invoke-Api 'POST' '/matrices' @{ riskId = $rid; controlId = $cid; policyId = $policyId; coverage = 2 } $token
Step 'matrix duplicate rejected' ($mxDup.code -eq 40000) ("code=" + $mxDup.code + " msg=" + $mxDup.message)
$mxid = $mx.data.id
$mxUpd = Invoke-Api 'PUT' "/matrices/$mxid" @{ coverage = 3; evidenceDesc = 'updated' } $token
Step 'matrix update partial' ($mxUpd.code -eq 0 -and $mxUpd.data.coverage -eq 3) ("coverage=" + $mxUpd.data.coverage)

# ---- evaluations ----
$ev = Invoke-Api 'POST' '/evaluations' @{ planName = 'API Test Evaluation'; planYear = 2026; planType = 1; startDate = '2026-11-01'; endDate = '2026-12-31'; ownerDeptId = 2 } $token
Step 'evaluation create' ($ev.code -eq 0 -and $ev.data.planCode -match '^PJ-2026-\d{2}$' -and $ev.data.status -eq 0) ("code=" + $ev.data.planCode)
$evid = $ev.data.id
$evSub = Invoke-Api 'POST' "/evaluations/$evid/submit" $null $token
Step 'evaluation submit pending' ($evSub.code -eq 0 -and $evSub.data.status -eq 1 -and $evSub.data.pendingWorkflow -eq $true) ("status=" + $evSub.data.status)
$evStart = Invoke-Api 'POST' "/evaluations/$evid/start" $null $token
Step 'evaluation start generates tasks' ($evStart.code -eq 0 -and $evStart.data.status -eq 2) ("status=" + $evStart.data.status)
$tasks = Invoke-Api 'GET' ("/evaluation-tasks?planId=" + $evid + "&pageSize=500") $null $token
$matrixTotal = Invoke-Api 'GET' '/matrices?pageNum=1&pageSize=500' $null $token
Step 'tasks generated from matrices' ($tasks.code -eq 0 -and $tasks.data.total -eq $matrixTotal.data.total) ("tasks=" + $tasks.data.total + " matrices=" + $matrixTotal.data.total)
$evStart2 = Invoke-Api 'POST' "/evaluations/$evid/start" $null $token
$tasks2 = Invoke-Api 'GET' ("/evaluation-tasks?planId=" + $evid + "&pageSize=500") $null $token
Step 'evaluation start idempotent' ($evStart2.code -eq 0 -and $tasks2.data.total -eq $tasks.data.total) ("total=" + $tasks2.data.total)
$t0 = $tasks2.data.list[0]
$tStart = Invoke-Api 'POST' "/evaluation-tasks/$($t0.id)/start" $null $token
Step 'task start' ($tStart.code -eq 0 -and $tStart.data.status -eq 1) ("status=" + $tStart.data.status)
$tSubmit = Invoke-Api 'POST' "/evaluation-tasks/$($t0.id)/submit" @{ score = 88.50; conclusion = 1; workpaper = @{ sampleDesc = 'sample'; testDesc = 'test'; testResult = 2; evidence = @(4001,4002); conclusion = 'ok' } } $token
Step 'task submit with workpaper' ($tSubmit.code -eq 0 -and $tSubmit.data.status -eq 2 -and $tSubmit.data.workpaperCount -ge 1) ("status=" + $tSubmit.data.status + " wp=" + $tSubmit.data.workpaperCount)
$tWp = Invoke-Api 'GET' "/evaluation-tasks/$($t0.id)/workpapers" $null $token
Step 'workpaper list' ($tWp.code -eq 0 -and $tWp.data.Count -eq 1) ("count=" + $tWp.data.Count)
$evFinish = Invoke-Api 'POST' "/evaluations/$evid/finish" $null $token
Step 'finish blocked by open tasks' ($evFinish.code -eq 40001) ("code=" + $evFinish.code + " msg=" + $evFinish.message)

# ---- defects / rectifies ----
$d = Invoke-Api 'POST' '/defects' @{ defectName = 'API Test Defect'; severity = 2; defectType = 1; deptId = 2; description = 'd' } $token
Step 'defect create' ($d.code -eq 0 -and $d.data.defectCode -match '^QX-2026-\d{3}$' -and $d.data.status -eq 0) ("code=" + $d.data.defectCode)
$did = $d.data.id
$closeEarly = Invoke-Api 'POST' "/defects/$did/close" $null $token
Step 'close before rectify rejected' ($closeEarly.code -eq 40001) ("code=" + $closeEarly.code)
$rec = Invoke-Api 'POST' "/defects/$did/rectify" @{ rectifyPlan = 'Plan'; deadline = '2026-12-31' } $token
Step 'defect rectify generates' ($rec.code -eq 0 -and $rec.data.defectStatus -eq 1 -and $rec.data.rectifyCode -match '^ZG-2026-\d{3}$') ("rectify=" + $rec.data.rectifyCode)
$zid = $rec.data.rectifyId
$zSub = Invoke-Api 'POST' "/rectifies/$zid/submit" @{ evidence = '4001' } $token
Step 'rectify submit' ($zSub.code -eq 0 -and $zSub.data.status -eq 1) ("status=" + $zSub.data.status)
$zApp = Invoke-Api 'POST' "/rectifies/$zid/approve" @{ reviewOpinion = 'ok' } $token
Step 'rectify approve' ($zApp.code -eq 0 -and $zApp.data.status -eq 2 -and $null -ne $zApp.data.reviewTime) ("status=" + $zApp.data.status)
$dGet = Invoke-Api 'GET' "/defects/$did" $null $token
Step 'defect advanced to reviewing' ($dGet.code -eq 0 -and $dGet.data.status -eq 2) ("status=" + $dGet.data.status + " rectifyId=" + $dGet.data.rectifyId)
$dClose = Invoke-Api 'POST' "/defects/$did/close" $null $token
Step 'defect close' ($dClose.code -eq 0 -and $dClose.data.status -eq 3 -and $null -ne $dClose.data.closeTime) ("status=" + $dClose.data.status)
$zClose = Invoke-Api 'POST' "/rectifies/$zid/close" $null $token
Step 'rectify close' ($zClose.code -eq 0 -and $zClose.data.status -eq 4) ("status=" + $zClose.data.status)

Write-Output ("SMOKE RESULT pass=" + $script:pass + " fail=" + $script:fail)
if ($script:fail -gt 0) { exit 1 }
