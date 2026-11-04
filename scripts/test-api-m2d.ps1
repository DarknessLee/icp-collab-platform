# ---------------------------------------------------------------------------
# M2d API acceptance script (PowerShell 5.1, ASCII only).
# Covers the automatable parts of docs/01 M2d acceptance cases T01-T28 via the
# real REST API against 127.0.0.1:8080 + MySQL 3307.
#
# Canonical run order (fresh seed is required for exact-count assertions):
#   scripts\stop-backend.ps1
#   scripts\db-reset.ps1
#   scripts\start-backend.ps1 -SkipDbStart
#   scripts\test-api-m2a.ps1
#   scripts\test-api-m2b.ps1
#   scripts\test-api-m2d.ps1
#   scripts\test-api-m2c.ps1
# The script leaves test rows (published docs/notices/borrows cannot be deleted
# through the API); run it on a disposable seed database.
# Exit code 0 = all assertions passed.
# ---------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'
$Base = 'http://127.0.0.1:8080/api/v1'
$script:Pass = 0
$script:Fail = 0
$Stamp = Get-Date -Format 'HHmmss'

function Check($name, $cond, $detail) {
    if ($cond) {
        $script:Pass++
        Write-Host ('PASS  ' + $name)
    } else {
        $script:Fail++
        Write-Host ('FAIL  ' + $name + '  ::  ' + $detail)
    }
}

function Api {
    param([string]$Method, [string]$Path, $Body, $Headers)
    $req = @{ Uri = $Base + $Path; Method = $Method; TimeoutSec = 60 }
    if ($Headers) { $req.Headers = $Headers }
    if ($null -ne $Body) {
        $req.ContentType = 'application/json; charset=utf-8'
        $req.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    try {
        $r = Invoke-RestMethod @req
        return [pscustomobject]@{ code = $r.code; message = $r.message; data = $r.data }
    } catch {
        $resp = $_.Exception.Response
        $code = -1
        $msg = $_.Exception.Message
        if ($resp -ne $null) {
            try {
                $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $txt = $sr.ReadToEnd()
                $sr.Dispose()
                $j = $txt | ConvertFrom-Json
                if ($j.code -ne $null) { $code = $j.code }
                if ($j.message -ne $null) { $msg = $j.message }
            } catch { }
        }
        return [pscustomobject]@{ code = $code; message = $msg; data = $null }
    }
}

function Login([string]$user) {
    $r = Api -Method POST -Path '/auth/login' -Body @{ username = $user; password = 'Icp@2026' }
    if ($r.code -ne 0) { throw "login failed: $user" }
    return $r.data
}

function AuthHeader($login) { return @{ Authorization = 'Bearer ' + $login.accessToken } }

function Download([string]$path, $headers) {
    $req = @{ Uri = $Base + $path; Method = 'GET'; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 60 }
    try {
        $r = Invoke-WebRequest @req
        return [pscustomobject]@{ status = $r.StatusCode; length = $r.RawContentLength; cd = [string]$r.Headers['Content-Disposition'] }
    } catch {
        $code = -1
        if ($_.Exception.Response -ne $null) { $code = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ status = $code; length = 0; cd = '' }
    }
}

function FindTodo($headers, [string]$instanceId) {
    $r = Api -Method GET -Path '/workflow/todo?pageNum=1&pageSize=500' -Headers $headers
    if ($r.code -ne 0) { return $null }
    return @($r.data.list) | Where-Object { $_.processInstanceId -eq $instanceId } | Select-Object -First 1
}

try {
    $admin = Login 'admin'
    $icp = Login 'icpadmin'
    $dept = Login 'dept01'
    $staff = Login 'staff01'
    $aud = Login 'auditor'
    $exec = Login 'exec'
    $hAdmin = AuthHeader $admin
    $hIcp = AuthHeader $icp
    $hDept = AuthHeader $dept
    $hStaff = AuthHeader $staff
    $hAud = AuthHeader $aud
    $hExec = AuthHeader $exec

    $createdTasks = New-Object System.Collections.ArrayList
    $createdDocs = New-Object System.Collections.ArrayList
    $createdCats = New-Object System.Collections.ArrayList
    $createdSchedules = New-Object System.Collections.ArrayList

    Write-Host '--- T01 task list + data scope ---'
    $taskAdmin = Api -Method GET -Path '/tasks?pageNum=1&pageSize=100' -Headers $hIcp
    Check 'T01 task seed total >= 80' ($taskAdmin.code -eq 0 -and $taskAdmin.data.total -ge 80) ('total=' + $taskAdmin.data.total)
    $taskStaff = Api -Method GET -Path '/tasks?pageNum=1&pageSize=100' -Headers $hStaff
    Check 'T01 staff task list readable' ($taskStaff.code -eq 0) $taskStaff.message
    $staffOnlySelf = $true
    foreach ($row in @($taskStaff.data.list)) {
        if ($row.assigneeId -ne 11 -and $row.creatorId -ne 11) { $staffOnlySelf = $false }
    }
    Check 'T01 staff sees self tasks only' ($staffOnlySelf -eq $true) 'leak'
    $scopeTask = Api -Method POST -Path '/tasks' -Headers $hIcp -Body @{ taskTitle = ('M2dT scope ' + $Stamp); assigneeId = 5; planStart = '2026-11-04'; planEnd = '2026-12-31' }
    [void]$createdTasks.Add($scopeTask.data.id)
    Check 'T01 out-of-scope task created' ($scopeTask.code -eq 0) $scopeTask.message
    $denyTask = Api -Method GET -Path ('/tasks/' + $scopeTask.data.id) -Headers $hStaff
    Check 'T01 staff out-of-scope task 40300' ($denyTask.code -eq 40300) ('code=' + $denyTask.code)

    Write-Host '--- T02 create validation + code ---'
    $badCreate = Api -Method POST -Path '/tasks' -Headers $hIcp -Body @{ assigneeId = 11; planEnd = '2026-12-31' }
    Check 'T02 missing title 40000' ($badCreate.code -eq 40000) ('code=' + $badCreate.code)
    $badDate = Api -Method POST -Path '/tasks' -Headers $hIcp -Body @{ taskTitle = 'M2dT bad date'; assigneeId = 11; planStart = '2026-12-10'; planEnd = '2026-12-01' }
    Check 'T02 plan start > end 40000' ($badDate.code -eq 40000) ('code=' + $badDate.code)
    $taskA = Api -Method POST -Path '/tasks' -Headers $hIcp -Body @{ taskTitle = ('M2dT task A ' + $Stamp); taskType = 2; priority = 1; assigneeId = 11; planStart = '2026-11-04'; planEnd = '2026-11-30'; description = 'api test' }
    $refPrefix = ([string]$taskA.data.taskNo).StartsWith('RW-')
    Check 'T02 create ok + code RW-' ($taskA.code -eq 0 -and $refPrefix) $taskA.message
    [void]$createdTasks.Add($taskA.data.id)
    Check 'T02 create status 0' ($taskA.data.status -eq 0) $taskA.data.status

    Write-Host '--- T03 task state machine ---'
    $complA = Api -Method POST -Path ('/tasks/' + $taskA.data.id + '/complete') -Headers $hIcp
    Check 'T03 complete 0->2' ($complA.code -eq 0 -and $complA.data.status -eq 2) ('code=' + $complA.code)
    $startA = Api -Method POST -Path ('/tasks/' + $taskA.data.id + '/start') -Headers $hIcp
    Check 'T03 start after complete 40001' ($startA.code -eq 40001) ('code=' + $startA.code)
    $cancelA = Api -Method POST -Path ('/tasks/' + $taskA.data.id + '/cancel') -Headers $hIcp
    Check 'T03 cancel after complete 40001' ($cancelA.code -eq 40001) ('code=' + $cancelA.code)
    $editA = Api -Method PUT -Path ('/tasks/' + $taskA.data.id) -Headers $hIcp -Body @{ taskTitle = 'M2dT edited' }
    Check 'T03 edit completed 40001' ($editA.code -eq 40001) ('code=' + $editA.code)

    Write-Host '--- T04 subtasks independent ---'
    $parentB = Api -Method POST -Path '/tasks' -Headers $hIcp -Body @{ taskTitle = ('M2dT parent ' + $Stamp); taskType = 2; assigneeId = 11; planStart = '2026-11-04'; planEnd = '2026-12-15' }
    [void]$createdTasks.Add($parentB.data.id)
    $childC = Api -Method POST -Path '/tasks' -Headers $hIcp -Body @{ taskTitle = ('M2dT child ' + $Stamp); assigneeId = 11; parentId = $parentB.data.id; planStart = '2026-11-05'; planEnd = '2026-12-10' }
    Check 'T04 child created taskType=3' ($childC.code -eq 0 -and $childC.data.taskType -eq 3) ('code=' + $childC.code + ' type=' + $childC.data.taskType)
    [void]$createdTasks.Add($childC.data.id)
    $complC = Api -Method POST -Path ('/tasks/' + $childC.data.id + '/complete') -Headers $hIcp
    Check 'T04 child complete ->2' ($complC.code -eq 0 -and $complC.data.status -eq 2) ('code=' + $complC.code)
    $parentGet = Api -Method GET -Path ('/tasks/' + $parentB.data.id) -Headers $hIcp
    Check 'T04 parent not auto completed' ($parentGet.data.status -ne 2) $parentGet.data.status
    Check 'T04 parent children listed' (@($parentGet.data.children).Count -eq 1) (@($parentGet.data.children).Count)

    Write-Host '--- T05 my tasks + overdue ---'
    $taskD = Api -Method POST -Path '/tasks' -Headers $hIcp -Body @{ taskTitle = ('M2dT overdue ' + $Stamp); assigneeId = 11; planStart = '2026-10-01'; planEnd = '2026-10-05' }
    [void]$createdTasks.Add($taskD.data.id)
    Check 'T05 overdue task created' ($taskD.code -eq 0) $taskD.message
    $overdue = Api -Method GET -Path '/tasks/overdue?pageSize=100' -Headers $hStaff
    $overdueHit = @($overdue.data.list | Where-Object { $_.id -eq $taskD.data.id }).Count
    Check 'T05 overdue list contains task' ($overdue.code -eq 0 -and $overdueHit -eq 1) ('code=' + $overdue.code)
    $myMine = Api -Method GET -Path '/tasks/my?scope=mine&pageSize=100' -Headers $hStaff
    Check 'T05 /my mine readable' ($myMine.code -eq 0) $myMine.message
    $myCreated = Api -Method GET -Path '/tasks/my?scope=created&pageSize=100' -Headers $hStaff
    Check 'T05 /my created readable' ($myCreated.code -eq 0) $myCreated.message

    Write-Host '--- T06 task comments ---'
    $cmt = Api -Method POST -Path ('/tasks/' + $parentB.data.id + '/comments') -Headers $hStaff -Body @{ content = ('M2dT comment ' + $Stamp) }
    Check 'T06 comment add code=0' ($cmt.code -eq 0 -and $cmt.data.content -like 'M2dT comment*') ('code=' + $cmt.code)
    $cmtList = Api -Method GET -Path ('/tasks/' + $parentB.data.id + '/comments') -Headers $hIcp
    Check 'T06 comment list >=1' ($cmtList.code -eq 0 -and @($cmtList.data).Count -ge 1) ('code=' + $cmtList.code)
    Check 'T06 comment has user name' (@($cmtList.data | Where-Object { -not [string]::IsNullOrEmpty($_.userName) }).Count -ge 1) 'userName'

    Write-Host '--- T07 task remind ---'
    $rem = Api -Method POST -Path ('/tasks/' + $parentB.data.id + '/remind') -Headers $hStaff
    Check 'T07 assignee remind ok' ($rem.code -eq 0 -and $rem.data.messageType -eq 3 -and $rem.data.bizType -eq 'REMIND') ('code=' + $rem.code)
    $remDeny = Api -Method POST -Path ('/tasks/' + $parentB.data.id + '/remind') -Headers $hDept
    Check 'T07 unrelated remind 40300' ($remDeny.code -eq 40300) ('code=' + $remDeny.code)

    Write-Host '--- T08 task permissions ---'
    $delDeny = Api -Method DELETE -Path ('/tasks/' + $scopeTask.data.id) -Headers $hStaff
    Check 'T08 staff delete other task 40300' ($delDeny.code -eq 40300) ('code=' + $delDeny.code)
    $addDeny = Api -Method POST -Path '/tasks' -Headers $hAud -Body @{ taskTitle = 'M2dT auditor'; assigneeId = 11; planEnd = '2026-12-31' }
    Check 'T08 auditor create 40300' ($addDeny.code -eq 40300) ('code=' + $addDeny.code)

    Write-Host '--- T09 document categories ---'
    $tree = Api -Method GET -Path '/doc-categories/tree' -Headers $hIcp
    Check 'T09 category tree roots >= 6' ($tree.code -eq 0 -and @($tree.data).Count -ge 6) ('roots=' + @($tree.data).Count)
    $cat1 = Api -Method POST -Path '/doc-categories' -Headers $hIcp -Body @{ categoryName = ('M2dT cat ' + $Stamp); parentId = 0; orderNum = 99 }
    [void]$createdCats.Add($cat1.data.id)
    Check 'T09 category create' ($cat1.code -eq 0) $cat1.message
    $cat2 = Api -Method POST -Path '/doc-categories' -Headers $hIcp -Body @{ categoryName = ('M2dT subcat ' + $Stamp); parentId = $cat1.data.id; orderNum = 1 }
    [void]$createdCats.Add($cat2.data.id)
    Check 'T09 subcategory create' ($cat2.code -eq 0) $cat2.message
    $delParent = Api -Method DELETE -Path ('/doc-categories/' + $cat1.data.id) -Headers $hIcp
    Check 'T09 delete parent with child 40000' ($delParent.code -eq 40000) ('code=' + $delParent.code)
    $docCat = Api -Method POST -Path '/docs' -Headers $hIcp -Body @{ docName = ('M2dT cat doc ' + $Stamp); categoryId = $cat2.data.id; fileId = 4001 }
    [void]$createdDocs.Add($docCat.data.id)
    $delCat2 = Api -Method DELETE -Path ('/doc-categories/' + $cat2.data.id) -Headers $hIcp
    Check 'T09 delete category with docs 40000' ($delCat2.code -eq 40000) ('code=' + $delCat2.code)
    $delDocCat = Api -Method DELETE -Path ('/docs/' + $docCat.data.id) -Headers $hIcp
    Check 'T09 draft doc delete' ($delDocCat.code -eq 0) $delDocCat.message
    $delCat2b = Api -Method DELETE -Path ('/doc-categories/' + $cat2.data.id) -Headers $hIcp
    $delCat1b = Api -Method DELETE -Path ('/doc-categories/' + $cat1.data.id) -Headers $hIcp
    Check 'T09 delete empty categories' ($delCat2b.code -eq 0 -and $delCat1b.code -eq 0) ('c2=' + $delCat2b.code + ' c1=' + $delCat1b.code)

    Write-Host '--- T10 document visibility ---'
    $docAdmin = Api -Method GET -Path '/docs?pageNum=1&pageSize=100' -Headers $hAdmin
    Check 'T10 doc seed total >= 40' ($docAdmin.code -eq 0 -and $docAdmin.data.total -ge 40) ('total=' + $docAdmin.data.total)
    $docStaff = Api -Method GET -Path '/docs?pageNum=1&pageSize=100' -Headers $hStaff
    Check 'T10 staff doc list readable' ($docStaff.code -eq 0) $docStaff.message
    Check 'T10 staff doc list limited' ($docStaff.data.total -lt $docAdmin.data.total) ('staff=' + $docStaff.data.total + ' all=' + $docAdmin.data.total)

    Write-Host '--- T11 publish / archive ---'
    $docDraft = Api -Method POST -Path '/docs' -Headers $hIcp -Body @{ docName = ('M2dT draft ' + $Stamp); categoryId = 3201; docType = 5; fileId = 4001; description = 'draft' }
    $docDraftId = $docDraft.data.id
    [void]$createdDocs.Add($docDraftId)
    Check 'T11 draft created V1.0' ($docDraft.code -eq 0 -and $docDraft.data.status -eq 0) ('code=' + $docDraft.code)
    $draftStaff = Api -Method GET -Path ('/docs/' + $docDraftId) -Headers $hStaff
    Check 'T11 draft hidden from staff' ($draftStaff.code -eq 40300) ('code=' + $draftStaff.code)
    $pub = Api -Method POST -Path ('/docs/' + $docDraftId + '/publish') -Headers $hIcp
    Check 'T11 publish 0->1' ($pub.code -eq 0 -and $pub.data.status -eq 1) ('code=' + $pub.code)
    $arch = Api -Method POST -Path ('/docs/' + $docDraftId + '/archive') -Headers $hIcp
    Check 'T11 archive 1->2' ($arch.code -eq 0 -and $arch.data.status -eq 2) ('code=' + $arch.code)
    $editArch = Api -Method PUT -Path ('/docs/' + $docDraftId) -Headers $hIcp -Body @{ docName = 'M2dT archived edit' }
    Check 'T11 archived edit 40001' ($editArch.code -eq 40001) ('code=' + $editArch.code)
    $dlOwner = Download ('/docs/' + $docDraftId + '/download') $hIcp
    Check 'T11 owner download 200' ($dlOwner.status -eq 200 -and $dlOwner.length -gt 0) ('status=' + $dlOwner.status)

    Write-Host '--- T12 version bump ---'
    $docVer = Api -Method POST -Path '/docs' -Headers $hIcp -Body @{ docName = ('M2dT version ' + $Stamp); categoryId = 3201; fileId = 4001 }
    $verId = $docVer.data.id
    [void]$createdDocs.Add($verId)
    $up1 = Api -Method PUT -Path ('/docs/' + $verId) -Headers $hIcp -Body @{ fileId = 4002 }
    Check 'T12 version V1.0->V1.1' ($up1.code -eq 0 -and $up1.data.version -eq 'V1.1') ('ver=' + $up1.data.version)
    $up2 = Api -Method PUT -Path ('/docs/' + $verId) -Headers $hIcp -Body @{ fileId = 4003 }
    Check 'T12 version V1.1->V1.2' ($up2.code -eq 0 -and $up2.data.version -eq 'V1.2') ('ver=' + $up2.data.version)
    $up3 = Api -Method PUT -Path ('/docs/' + $verId) -Headers $hIcp -Body @{ docName = 'M2dT renamed' }
    Check 'T12 rename keeps version' ($up3.code -eq 0 -and $up3.data.version -eq 'V1.2') ('ver=' + $up3.data.version)

    Write-Host '--- T13 doc perms full overwrite ---'
    $docPerm = Api -Method POST -Path '/docs' -Headers $hIcp -Body @{ docName = ('M2dT perm ' + $Stamp); categoryId = 3201; fileId = 4001 }
    $permId = $docPerm.data.id
    [void]$createdDocs.Add($permId)
    $null = Api -Method POST -Path ('/docs/' + $permId + '/publish') -Headers $hIcp
    $permPut = Api -Method PUT -Path ('/docs/' + $permId + '/perms') -Headers $hIcp -Body @{ perms = @(
        @{ targetType = 0; targetId = 0; permType = 1 },
        @{ targetType = 1; targetId = 4; permType = 2 },
        @{ targetType = 2; targetId = 5; permType = 2 },
        @{ targetType = 3; targetId = 4; permType = 2 }) }
    Check 'T13 perms 4 rows' ($permPut.code -eq 0 -and @($permPut.data).Count -eq 4) ('code=' + $permPut.code + ' rows=' + @($permPut.data).Count)
    $permGet = Api -Method GET -Path ('/docs/' + $permId + '/perms') -Headers $hIcp
    Check 'T13 perms read back 4' ($permGet.code -eq 0 -and @($permGet.data).Count -eq 4) ('rows=' + @($permGet.data).Count)
    $permPut2 = Api -Method PUT -Path ('/docs/' + $permId + '/perms') -Headers $hIcp -Body @{ perms = @(@{ targetType = 2; targetId = 5; permType = 2 }) }
    $permGet2 = Api -Method GET -Path ('/docs/' + $permId + '/perms') -Headers $hIcp
    Check 'T13 overwrite physical delete' ($permPut2.code -eq 0 -and @($permGet2.data).Count -eq 1) ('rows=' + @($permGet2.data).Count)

    Write-Host '--- T14 doc download permission ---'
    $docDl = Api -Method POST -Path '/docs' -Headers $hIcp -Body @{ docName = ('M2dT download ' + $Stamp); categoryId = 3201; fileId = 4001 }
    $dlId = $docDl.data.id
    [void]$createdDocs.Add($dlId)
    $null = Api -Method POST -Path ('/docs/' + $dlId + '/publish') -Headers $hIcp
    $null = Api -Method PUT -Path ('/docs/' + $dlId + '/perms') -Headers $hIcp -Body @{ perms = @(@{ targetType = 0; targetId = 0; permType = 2 }) }
    $dlStaff = Download ('/docs/' + $dlId + '/download') $hStaff
    Check 'T14 all-users perm download 200' ($dlStaff.status -eq 200 -and $dlStaff.length -gt 0 -and $dlStaff.cd.Contains('attachment')) ('status=' + $dlStaff.status)
    $null = Api -Method PUT -Path ('/docs/' + $dlId + '/perms') -Headers $hIcp -Body @{ perms = @(@{ targetType = 2; targetId = 5; permType = 2 }) }
    $dlStaff2 = Download ('/docs/' + $dlId + '/download') $hStaff
    Check 'T14 staff download denied 403' ($dlStaff2.status -eq 403) ('status=' + $dlStaff2.status)
    $dlDept = Download ('/docs/' + $dlId + '/download') $hDept
    Check 'T14 dept01 user perm download 200' ($dlDept.status -eq 200 -and $dlDept.length -gt 0) ('status=' + $dlDept.status)

    Write-Host '--- T15 borrow workflow ---'
    $borrow = Api -Method POST -Path '/borrows' -Headers $hStaff -Body @{ docId = $dlId; reason = ('M2dT borrow ' + $Stamp); startDate = '2026-11-05'; endDate = '2026-11-20' }
    Check 'T15 borrow create starts flow' ($borrow.code -eq 0 -and $borrow.data.pendingWorkflow -eq $false -and $null -ne $borrow.data.processInstanceId -and $borrow.data.borrow.status -eq 0) ('code=' + $borrow.code)
    $borrowId = $borrow.data.borrow.id
    $t1 = FindTodo $hStaff $borrow.data.processInstanceId
    $ap1 = Api -Method POST -Path ('/workflow/tasks/' + $t1.taskId + '/approve') -Headers $hStaff -Body @{ comment = 'submit' }
    $t2 = FindTodo $hDept $borrow.data.processInstanceId
    $ap2 = Api -Method POST -Path ('/workflow/tasks/' + $t2.taskId + '/approve') -Headers $hDept -Body @{ comment = 'ok' }
    Check 'T15 approvals ok' ($ap1.code -eq 0 -and $ap2.code -eq 0 -and $ap2.data.linkStatus -eq 1) ('ap1=' + $ap1.code + ' ap2=' + $ap2.code)
    $borrowGet = Api -Method GET -Path ('/borrows/' + $borrowId) -Headers $hStaff
    Check 'T15 borrow status=1' ($borrowGet.data.status -eq 1) $borrowGet.data.status
    $ret = Api -Method POST -Path ('/borrows/' + $borrowId + '/return') -Headers $hStaff
    Check 'T15 return 1->3' ($ret.code -eq 0 -and $ret.data.status -eq 3) ('code=' + $ret.code + ' status=' + $ret.data.status)
    $ret2 = Api -Method POST -Path ('/borrows/' + $borrowId + '/return') -Headers $hStaff
    Check 'T15 return twice 40001' ($ret2.code -eq 40001) ('code=' + $ret2.code)
    $borrow2 = Api -Method POST -Path '/borrows' -Headers $hStaff -Body @{ docId = $dlId; reason = ('M2dT reject ' + $Stamp); startDate = '2026-11-05'; endDate = '2026-11-20' }
    $borrow2Id = $borrow2.data.borrow.id
    $r1 = FindTodo $hStaff $borrow2.data.processInstanceId
    $null = Api -Method POST -Path ('/workflow/tasks/' + $r1.taskId + '/approve') -Headers $hStaff -Body @{ comment = 'submit' }
    $r2 = FindTodo $hDept $borrow2.data.processInstanceId
    $null = Api -Method POST -Path ('/workflow/tasks/' + $r2.taskId + '/reject') -Headers $hDept -Body @{ comment = 'rejected' }
    $borrow2Get = Api -Method GET -Path ('/borrows/' + $borrow2Id) -Headers $hStaff
    Check 'T15 rejected borrow status=2' ($borrow2Get.data.status -eq 2) $borrow2Get.data.status

    Write-Host '--- T16 notice scope ---'
    $nDept = Api -Method POST -Path '/notices' -Headers $hIcp -Body @{ noticeTitle = ('M2dT dept notice ' + $Stamp); noticeType = 2; content = 'dept scope'; scope = 2; scopeDeptIds = @(4) }
    Check 'T16 dept-scope notice create' ($nDept.code -eq 0 -and $nDept.data.scope -eq 2) ('code=' + $nDept.code + ' scope=' + $nDept.data.scope)
    $null = Api -Method POST -Path ('/notices/' + $nDept.data.id + '/publish') -Headers $hIcp
    $deptList = Api -Method GET -Path '/notices?pageSize=100' -Headers $hDept
    $deptHit = @($deptList.data.list | Where-Object { $_.id -eq $nDept.data.id }).Count
    Check 'T16 dept01 sees dept notice' ($deptHit -eq 1) ('hit=' + $deptHit)
    $staffList = Api -Method GET -Path '/notices?pageSize=100' -Headers $hStaff
    $staffHit = @($staffList.data.list | Where-Object { $_.id -eq $nDept.data.id }).Count
    Check 'T16 staff does not see dept notice' ($staffHit -eq 0) ('hit=' + $staffHit)
    $staffNotice = Api -Method GET -Path ('/notices/' + $nDept.data.id) -Headers $hStaff
    Check 'T16 staff notice detail 40300' ($staffNotice.code -eq 40300) ('code=' + $staffNotice.code)
    $nRole = Api -Method POST -Path '/notices' -Headers $hIcp -Body @{ noticeTitle = ('M2dT role notice ' + $Stamp); noticeType = 2; content = 'role scope'; scope = 3; scopeRoles = @('EMPLOYEE') }
    $null = Api -Method POST -Path ('/notices/' + $nRole.data.id + '/publish') -Headers $hIcp
    $staffRoleList = Api -Method GET -Path '/notices?pageSize=100' -Headers $hStaff
    $roleHit = @($staffRoleList.data.list | Where-Object { $_.id -eq $nRole.data.id }).Count
    Check 'T16 employee sees role notice' ($roleHit -eq 1) ('hit=' + $roleHit)
    $deptRoleNote = Api -Method GET -Path ('/notices/' + $nRole.data.id) -Headers $hDept
    Check 'T16 dept01 role notice detail 40300' ($deptRoleNote.code -eq 40300) ('code=' + $deptRoleNote.code)
    $nAll = Api -Method POST -Path '/notices' -Headers $hIcp -Body @{ noticeTitle = ('M2dT all notice ' + $Stamp); content = 'all scope' }
    Check 'T16 default scope=1' ($nAll.code -eq 0 -and $nAll.data.scope -eq 1) ('scope=' + $nAll.data.scope)
    $null = Api -Method POST -Path ('/notices/' + $nAll.data.id + '/publish') -Headers $hIcp

    Write-Host '--- T17 notice read + unread ---'
    $unreadBefore = Api -Method GET -Path '/notices/unread-count' -Headers $hStaff
    $read1 = Api -Method POST -Path ('/notices/' + $nAll.data.id + '/read') -Headers $hStaff
    $read2 = Api -Method POST -Path ('/notices/' + $nAll.data.id + '/read') -Headers $hStaff
    Check 'T17 read idempotent' ($read1.code -eq 0 -and $read2.code -eq 0 -and $read2.data.read -eq $true -and $read2.data.readers -eq 1) ('readers=' + $read2.data.readers)
    $unreadAfter = Api -Method GET -Path '/notices/unread-count' -Headers $hStaff
    Check 'T17 unread count decreases' ($unreadAfter.data.unread -eq ($unreadBefore.data.unread - 1)) ('before=' + $unreadBefore.data.unread + ' after=' + $unreadAfter.data.unread)

    Write-Host '--- T18 notice state machine ---'
    $nDraft = Api -Method POST -Path '/notices' -Headers $hIcp -Body @{ noticeTitle = ('M2dT state notice ' + $Stamp); content = 'state' }
    $nDraftId = $nDraft.data.id
    $nEdit = Api -Method PUT -Path ('/notices/' + $nDraftId) -Headers $hIcp -Body @{ noticeTitle = ('M2dT state notice edited ' + $Stamp) }
    Check 'T18 draft edit ok' ($nEdit.code -eq 0) $nEdit.message
    $nPub = Api -Method POST -Path ('/notices/' + $nDraftId + '/publish') -Headers $hIcp
    Check 'T18 publish 0->1' ($nPub.code -eq 0 -and $nPub.data.status -eq 1) ('code=' + $nPub.code)
    $nEdit2 = Api -Method PUT -Path ('/notices/' + $nDraftId) -Headers $hIcp -Body @{ noticeTitle = 'M2dT published edit' }
    Check 'T18 published edit 40001' ($nEdit2.code -eq 40001) ('code=' + $nEdit2.code)
    $nPub2 = Api -Method POST -Path ('/notices/' + $nDraftId + '/publish') -Headers $hIcp
    Check 'T18 publish twice 40001' ($nPub2.code -eq 40001) ('code=' + $nPub2.code)
    $nClose = Api -Method POST -Path ('/notices/' + $nDraftId + '/close') -Headers $hIcp
    Check 'T18 close ->2' ($nClose.code -eq 0 -and $nClose.data.status -eq 2) ('code=' + $nClose.code)

    Write-Host '--- T19 messages ---'
    $msgStaff = Api -Method GET -Path '/messages?pageNum=1&pageSize=50' -Headers $hStaff
    Check 'T19 message list' ($msgStaff.code -eq 0 -and $msgStaff.data.total -ge 1) ('total=' + $msgStaff.data.total)
    $msgIds = @($msgStaff.data.list | Select-Object -First 2 | ForEach-Object { $_.id })
    $msgRead = Api -Method POST -Path '/messages/read' -Headers $hStaff -Body @{ ids = $msgIds }
    Check 'T19 batch read ok' ($msgRead.code -eq 0) $msgRead.message
    $msgUnread = Api -Method GET -Path '/messages/unread-count' -Headers $hStaff
    Check 'T19 unread count readable' ($msgUnread.code -eq 0) $msgUnread.message

    Write-Host '--- T20 SSE stream ---'
    $badToken = $staff.accessToken + 'xxxxx'
    $badSse = -1
    try {
        $badReq = [System.Net.HttpWebRequest]::Create($Base + '/messages/stream?access_token=' + $badToken)
        $badReq.Timeout = 5000
        $null = $badReq.GetResponse()
        $badSse = 200
    } catch {
        $msg = $_.Exception.Message
        if ($_.Exception.Response -ne $null) {
            $badSse = [int]$_.Exception.Response.StatusCode
        } elseif ($msg -match '\((\d{3})\)') {
            $badSse = [int]$Matches[1]
        }
    }
    Check 'T20 bad token 401' ($badSse -eq 401) ('status=' + $badSse)
    $sseOk = $false
    $heartbeat = $false
    try {
        $sseReq = [System.Net.HttpWebRequest]::Create($Base + '/messages/stream?access_token=' + $staff.accessToken)
        $sseReq.Timeout = 5000
        $sseReq.ReadWriteTimeout = 45000
        $sseResp = $sseReq.GetResponse()
        $sseStream = $sseResp.GetResponseStream()
        $sseReader = New-Object System.IO.StreamReader($sseStream)
        $line1 = $sseReader.ReadLine()
        $null = $sseReader.ReadLine()
        if ($line1 -eq ':connected') { $sseOk = $true }
        $line3 = $sseReader.ReadLine()
        if ($line3 -eq ':ping') { $heartbeat = $true }
        $sseReader.Dispose()
        $sseResp.Close()
    } catch { }
    Check 'T20 SSE connected event' ($sseOk) 'line'
    Check 'T20 SSE heartbeat <= 30-45s' ($heartbeat) 'ping'

    Write-Host '--- T21 message isolation ---'
    $staffMsgs = Api -Method GET -Path '/messages?pageSize=100' -Headers $hStaff
    $onlyStaff = $true
    foreach ($m in @($staffMsgs.data.list)) { if ($m.receiverId -ne 11) { $onlyStaff = $false } }
    Check 'T21 staff messages receiver self' ($onlyStaff) 'receiver'
    $deptMsgs = Api -Method GET -Path '/messages?pageSize=100' -Headers $hDept
    $onlyDept = $true
    foreach ($m in @($deptMsgs.data.list)) { if ($m.receiverId -ne 5) { $onlyDept = $false } }
    Check 'T21 dept01 messages receiver self' ($onlyDept) 'receiver'
    $staffUnread = Api -Method GET -Path '/messages/unread-count' -Headers $hStaff
    $deptUnread = Api -Method GET -Path '/messages/unread-count' -Headers $hDept
    Check 'T21 user-specific unread' ($staffUnread.code -eq 0 -and $deptUnread.code -eq 0 -and $staffUnread.data.unread -ne $deptUnread.data.unread) ('staff=' + $staffUnread.data.unread + ' dept=' + $deptUnread.data.unread)

    Write-Host '--- T22 schedules scope ---'
    $icpSchedule = Api -Method POST -Path '/schedules' -Headers $hIcp -Body @{ scheduleTitle = ('M2dT icp schedule ' + $Stamp); scheduleType = 1; startTime = '2026-12-01 09:00:00'; endTime = '2026-12-01 10:00:00' }
    [void]$createdSchedules.Add($icpSchedule.data.id)
    Check 'T22 icp schedule create' ($icpSchedule.code -eq 0) $icpSchedule.message
    $staffSched = Api -Method GET -Path ('/schedules/' + $icpSchedule.data.id) -Headers $hStaff
    Check 'T22 staff other schedule 40300' ($staffSched.code -eq 40300) ('code=' + $staffSched.code)
    $deptSched = Api -Method GET -Path ('/schedules/' + $icpSchedule.data.id) -Headers $hDept
    Check 'T22 dept01 other schedule 40300' ($deptSched.code -eq 40300) ('code=' + $deptSched.code)
    $staffCal = Api -Method GET -Path '/schedules/calendar?month=2026-12' -Headers $hStaff
    Check 'T22 staff calendar readable' ($staffCal.code -eq 0 -and $null -ne $staffCal.data.items) $staffCal.message

    Write-Host '--- T23 schedule validation + cancel ---'
    $badSched = Api -Method POST -Path '/schedules' -Headers $hStaff -Body @{ scheduleTitle = 'M2dT bad schedule'; scheduleType = 3; startTime = '2026-12-05 10:00:00'; endTime = '2026-12-05 09:00:00' }
    Check 'T23 end before start 40000' ($badSched.code -eq 40000) ('code=' + $badSched.code)
    $sched = Api -Method POST -Path '/schedules' -Headers $hStaff -Body @{ scheduleTitle = ('M2dT schedule ' + $Stamp); scheduleType = 3; startTime = '2026-12-03 09:00:00'; endTime = '2026-12-03 10:00:00'; location = 'Room B' }
    $schedId = $sched.data.id
    [void]$createdSchedules.Add($schedId)
    Check 'T23 schedule create' ($sched.code -eq 0 -and $sched.data.status -eq 0) ('code=' + $sched.code)
    $cal = Api -Method GET -Path '/schedules/calendar?month=2026-12' -Headers $hStaff
    $calHit = @($cal.data.items | Where-Object { $_.id -eq $schedId }).Count
    Check 'T23 calendar contains schedule' ($calHit -eq 1) ('hit=' + $calHit)
    $scCancel = Api -Method POST -Path ('/schedules/' + $schedId + '/cancel') -Headers $hStaff
    Check 'T23 cancel ->3' ($scCancel.code -eq 0 -and $scCancel.data.status -eq 3) ('code=' + $scCancel.code + ' status=' + $scCancel.data.status)
    $scCancel2 = Api -Method POST -Path ('/schedules/' + $schedId + '/cancel') -Headers $hStaff
    Check 'T23 cancel twice 40001' ($scCancel2.code -eq 40001) ('code=' + $scCancel2.code)
    $scEdit = Api -Method PUT -Path ('/schedules/' + $schedId) -Headers $hStaff -Body @{ scheduleTitle = 'M2dT cancelled edit' }
    Check 'T23 cancelled edit 40001' ($scEdit.code -eq 40001) ('code=' + $scEdit.code)

    Write-Host '--- T24 cross-module messages ---'
    $staffMsgs2 = Api -Method GET -Path '/messages?pageSize=100' -Headers $hStaff
    $remindHit = @($staffMsgs2.data.list | Where-Object { $_.bizType -eq 'REMIND' -and $_.messageType -eq 3 }).Count
    Check 'T24 task remind message exists' ($remindHit -ge 1) ('hits=' + $remindHit)
    $null = Api -Method POST -Path ('/tasks/' + $parentB.data.id + '/complete') -Headers $hIcp
    $icpMsgs = Api -Method GET -Path '/messages?pageSize=100' -Headers $hIcp
    $taskMsgHit = @($icpMsgs.data.list | Where-Object { $_.bizType -eq 'TASK' -and $_.messageType -eq 3 }).Count
    Check 'T24 task complete message' ($taskMsgHit -ge 1) ('hits=' + $taskMsgHit)

    Write-Host '--- T25 audit log ---'
    $logs = Api -Method GET -Path '/logs/oper?pageNum=1&pageSize=50&module=%E5%8D%8F%E5%90%8C%E8%BF%90%E8%90%A5' -Headers $hAdmin
    Check 'T25 collab audit rows' ($logs.code -eq 0 -and $logs.data.total -ge 1) ('code=' + $logs.code)

    Write-Host '--- T26 scope / permission denies ---'
    $noticeDeny = Api -Method POST -Path '/notices' -Headers $hStaff -Body @{ noticeTitle = 'M2dT staff notice'; content = 'x' }
    Check 'T26 staff notice create 40300' ($noticeDeny.code -eq 40300) ('code=' + $noticeDeny.code)
    $audDocDeny = Api -Method PUT -Path ('/docs/' + $permId + '/perms') -Headers $hAud -Body @{ perms = @(@{ targetType = 0; targetId = 0; permType = 1 }) }
    Check 'T26 auditor doc perm 40300' ($audDocDeny.code -eq 40300) ('code=' + $audDocDeny.code)
    $staffDoc2 = Api -Method GET -Path ('/docs/' + $dlId) -Headers $hStaff
    Check 'T26 staff doc no perm 40300' ($staffDoc2.code -eq 40300) ('code=' + $staffDoc2.code)
    $missing = Api -Method GET -Path '/tasks/999999' -Headers $hIcp
    Check 'T26 missing task 40400' ($missing.code -eq 40400) ('code=' + $missing.code)

    Write-Host '--- T27 regression contracts ---'
    $procs = Api -Method GET -Path '/workflow/processes' -Headers $hIcp
    Check 'T27 workflow definitions 6' ($procs.code -eq 0 -and @($procs.data).Count -eq 6) ('count=' + @($procs.data).Count)
    $policy = Api -Method GET -Path '/policies?pageNum=1&pageSize=5' -Headers $hIcp
    Check 'T27 policy list 200' ($policy.code -eq 0 -and $policy.data.total -ge 40) ('total=' + $policy.data.total)
    $defect = Api -Method GET -Path '/defects?pageNum=1&pageSize=5' -Headers $hIcp
    Check 'T27 defect list 200' ($defect.code -eq 0 -and $defect.data.total -ge 30) ('total=' + $defect.data.total)
    $health = 'FAILED'
    try { $health = (Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 10).status } catch { }
    Check 'T27 health UP' ($health -eq 'UP') $health

    Write-Host '--- T28 seed read counts ---'
    $seedTasks = Api -Method GET -Path '/tasks?pageNum=1&pageSize=1' -Headers $hIcp
    Check 'T28 task seed >= 80' ($seedTasks.data.total -ge 80) ('total=' + $seedTasks.data.total)
    $seedDocs = Api -Method GET -Path '/docs?pageNum=1&pageSize=1' -Headers $hAdmin
    Check 'T28 doc seed >= 40' ($seedDocs.data.total -ge 40) ('total=' + $seedDocs.data.total)
    $seedBorrows = Api -Method GET -Path '/borrows?pageNum=1&pageSize=1' -Headers $hIcp
    Check 'T28 borrow seed >= 10' ($seedBorrows.data.total -ge 10) ('total=' + $seedBorrows.data.total)
    $seedNotices = Api -Method GET -Path '/notices?pageNum=1&pageSize=1' -Headers $hIcp
    Check 'T28 notice seed >= 12' ($seedNotices.data.total -ge 12) ('total=' + $seedNotices.data.total)
    $seedSchedules = Api -Method GET -Path '/schedules?pageNum=1&pageSize=1' -Headers $hIcp
    Check 'T28 schedule seed >= 20' ($seedSchedules.data.total -ge 20) ('total=' + $seedSchedules.data.total)
    $seedMsgs = Api -Method GET -Path '/messages?pageNum=1&pageSize=1' -Headers $hIcp
    Check 'T28 message seed >= 1' ($seedMsgs.data.total -ge 1) ('total=' + $seedMsgs.data.total)

    Write-Host '--- cleanup created rows (best effort) ---'
    foreach ($id in @($createdSchedules)) { $null = Api -Method DELETE -Path ('/schedules/' + $id) -Headers $hIcp }
    foreach ($id in @($createdDocs)) {
        $null = Api -Method DELETE -Path ('/docs/' + $id) -Headers $hAdmin
    }
    foreach ($id in @($createdTasks)) { $null = Api -Method DELETE -Path ('/tasks/' + $id) -Headers $hIcp }
    foreach ($id in @($createdCats)) { $null = Api -Method DELETE -Path ('/doc-categories/' + $id) -Headers $hIcp }

    Write-Host ''
    Write-Host ('test-api-m2d: PASS=' + $script:Pass + ' FAIL=' + $script:Fail)
    if ($script:Fail -gt 0) { exit 1 }
    exit 0
} catch {
    Write-Host ('test-api-m2d: ERROR ' + $_.Exception.Message)
    exit 1
}
