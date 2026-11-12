# ---------------------------------------------------------------------------
# M2c seed: generate workflow runtime data through the real REST APIs only.
# Never writes ACT_* tables directly (docs/03 5.1 D35).
#
# Target (clean seed database): 60 process instances, 40 active todo tasks,
# 20 urge records, opinions and attachments on part of the approvals.
# Idempotent: when the instance count is already >= 60 the script exits 0.
#
# RUN ORDER (important): this script must run AFTER the M2b regression.
#   1) stop-backend  2) db-reset (db-validate 15/15)  3) start-backend
#   4) test-api-m2a.ps1  5) test-api-m2b.ps1 (needs clean exact counts 40/30)
#   6) seed-flow.ps1 (THIS FILE)  7) test-api-m2c.ps1  8) verify-m2c.ps1
# Reason: seeding drives real workflows which CREATE business docs (about 13
# policies + 10 evaluation plans + ...). So after this script the exact seed
# counts used by test-api-m2b.ps1 (policies 40 / defects 30) are no longer valid.
#
# NOTE: after scripts/db-reset.ps1 the backend MUST be restarted before this
# script runs, because Flyway reset drops the Flowable ACT_* tables and they are
# only re-created at application startup (otherwise: Table 'act_re_procdef' does
# not exist).
#
# Usage: powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\seed-flow.ps1
# ---------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'
$Base = 'http://127.0.0.1:8080/api/v1'
$script:Pass = 0
$script:Fail = 0

function Step($text) { Write-Host ("[seed-flow] " + $text) }

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, $Headers)
    $req = @{
        Uri = $Base + $Path
        Method = $Method
        TimeoutSec = 60
    }
    if ($Headers) { $req.Headers = $Headers }
    if ($null -ne $Body) {
        $req.ContentType = 'application/json; charset=utf-8'
        $req.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    return Invoke-RestMethod @req
}

function Login([string]$user) {
    $r = Invoke-Api -Method POST -Path '/auth/login' -Body @{ username = $user; password = 'Icp@2026' }
    if ($r.code -ne 0) { throw "login failed for $user : $($r.message)" }
    return $r.data
}

function Headers($login) { return @{ Authorization = 'Bearer ' + $login.accessToken } }

# Decode UTF-16BE hex into a Unicode string so this script stays pure ASCII while
# still producing professional Chinese business titles in the seed data.
function Cn([string]$hex) {
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $hex.Length; $i += 4) {
        [void]$sb.Append([char][Convert]::ToInt32($hex.Substring($i, 4), 16))
    }
    return $sb.ToString()
}

$script:PolicyNames = @(
    (Cn '00320030003200365E745EA68D2252A162A595007BA1740652365EA6FF084FEE8BA2FF09'),
    (Cn '91C78D2D4E0E4ED86B3E7BA1740652365EA6'),
    (Cn '56FA5B9A8D444EA77BA1740652365EA6'),
    (Cn '5408540C7BA1740652365EA6'),
    (Cn '8D4491D15BA162797BA1740652365EA6'),
    (Cn '98847B977BA1740652365EA6'),
    (Cn '517380544EA466137BA1740652365EA6'),
    (Cn '4FE1606F62AB97327BA1740652365EA6'),
    (Cn '518590E85BA18BA152365EA6'),
    (Cn '53707AE07BA1740652365EA6'),
    (Cn '686368487BA1740652365EA6'),
    (Cn '54585DE5884C4E3A89C48303')
)
$script:EvalNames = @(
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D8FD084254E2D5FC3'),
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D8D2252A14E2D5FC3'),
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D91C78D2D4E2D5FC3'),
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D4EBA529B8D446E9090E8'),
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D4FE1606F4E2D5FC3'),
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D751F4EA74E2D5FC3'),
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D54C18D284E2D5FC3'),
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D6CD552A190E8'),
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D5BA18BA190E8'),
    (Cn '00320030003200365E747B2C4E095B635EA6518590E863A752368BC44EF7002D884C653F90E8')
)
$script:PurchaseTitle = Cn '529E516C8BBE590791C78D2D75338BF7'
$script:PurchasePurpose = Cn '91C78D2D7B148BB0672C753581114E0E663E793A5668'
$script:PurchaseSupplier = Cn '66685149529E516C752854C167099650516C53F8'
$script:SealReason = Cn '5408540C7528537075338BF7'
$script:BorrowReason = Cn '52365EA665876863501F960575338BF7FF088D2252A162A595007BA1740652365EA6FF09'
$script:PolicyContent = Cn '4E3A89C48303516C53F88D2252A162A595006D417A0BFF0C660E786E5BA16279674396504E0E62A59500680751C6FF0C727952365B9A672C52365EA63002'
$script:SubmitComment = Cn '63D04EA45BA16279'
$script:ApproveComment = Cn '540C610FFF0C8BF7630952365EA66267884C'
$script:UrgeContent = Cn '8BF75C3D5FEB590474068BE54EFB52A1'

function Approve($headers, [string]$taskId, [string]$comment, $attachmentIds) {
    $body = @{ comment = $comment }
    if ($attachmentIds) { $body.attachmentIds = $attachmentIds }
    $r = Invoke-Api -Method POST -Path ('/workflow/tasks/' + $taskId + '/approve') -Headers $headers -Body $body
    if ($r.code -ne 0) { throw "approve failed task=$taskId code=$($r.code) msg=$($r.message)" }
    return $r.data
}

function Urge($headers, [string]$taskId, [string]$content) {
    $r = Invoke-Api -Method POST -Path ('/workflow/tasks/' + $taskId + '/urge') -Headers $headers -Body @{ content = $content }
    if ($r.code -ne 0) { throw "urge failed task=$taskId code=$($r.code) msg=$($r.message)" }
    return $r.data
}

function TodoTasks($headers) {
    $r = Invoke-Api -Method GET -Path '/workflow/todo?pageNum=1&pageSize=500' -Headers $headers
    if ($r.code -ne 0) { throw "todo list failed code=$($r.code)" }
    return @($r.data.list)
}

function TaskForInstance($headers, [string]$instanceId) {
    $list = TodoTasks $headers
    return $list | Where-Object { $_.processInstanceId -eq $instanceId } | Select-Object -First 1
}

function StartInstance($headers, [string]$processKey, [string]$businessType, $formData) {
    $r = Invoke-Api -Method POST -Path '/workflow/instances' -Headers $headers -Body @{
        processKey = $processKey
        businessType = $businessType
        formData = $formData
    }
    if ($r.code -ne 0) { throw "start failed key=$processKey code=$($r.code) msg=$($r.message)" }
    return $r.data
}

function StartPurchase([bool]$complete) {
    $form = @{
        title = $script:PurchaseTitle
        amount = 1200.50
        budgetAmount = 5000
        supplier = $script:PurchaseSupplier
        purchaseType = 1
        purpose = $script:PurchasePurpose
    }
    return StartInstance $script:H_ICP 'purchase_apply' 'purchase' $form
}

function CompleteInstance($instanceId, [string]$handler) {
    # approve at the submit node as the initiator, then approve the approval node
    $t = TaskForInstance $script:H_ICP $instanceId
    if ($null -eq $t) { throw "submit task not found for $instanceId" }
    Approve $script:H_ICP $t.taskId $script:SubmitComment @(4001) | Out-Null
    $h = if ($handler -eq 'dept01') { $script:H_DEPT } else { $script:H_ICP }
    $t2 = TaskForInstance $h $instanceId
    if ($null -eq $t2) { throw "approval task not found for $instanceId" }
    Approve $h $t2.taskId $script:ApproveComment @(4001, 4002) | Out-Null
}

function LeaveAtSubmit($instanceId) {
    # keep the submit task open (initiator todo)
    $t = TaskForInstance $script:H_ICP $instanceId
    if ($null -eq $t) { throw "submit task not found for $instanceId" }
    return $t
}

function LeaveAtApproval($instanceId) {
    $t = TaskForInstance $script:H_ICP $instanceId
    if ($null -eq $t) { throw "submit task not found for $instanceId" }
    Approve $script:H_ICP $t.taskId $script:SubmitComment @() | Out-Null
    $t2 = TaskForInstance $script:H_ICP $instanceId
    if ($null -ne $t2) { return $t2 }
    $t3 = TaskForInstance $script:H_DEPT $instanceId
    if ($null -eq $t3) { throw "approval task not found for $instanceId" }
    return $t3
}

function NewPolicyDraft([string]$name) {
    $r = Invoke-Api -Method POST -Path '/policies' -Headers $script:H_ICP -Body @{
        policyName = $name
        categoryId = 1001
        content = $script:PolicyContent
        effectiveDate = '2026-11-01'
        expireDate = '2027-10-31'
    }
    if ($r.code -ne 0) { throw "create policy failed code=$($r.code) msg=$($r.message)" }
    return $r.data
}

function NewEvaluationDraft([string]$name) {
    $r = Invoke-Api -Method POST -Path '/evaluations' -Headers $script:H_ICP -Body @{
        planName = $name
        planYear = 2026
        planType = 1
        startDate = '2026-11-10'
        endDate = '2026-12-20'
        deptScope = '2'
    }
    if ($r.code -ne 0) { throw "create evaluation failed code=$($r.code) msg=$($r.message)" }
    return $r.data
}

function StartPolicyInstance([string]$name) {
    $p = NewPolicyDraft $name
    $r = Invoke-Api -Method POST -Path ('/policies/' + $p.id + '/submit') -Headers $script:H_ICP
    if ($r.code -ne 0) { throw "submit policy failed code=$($r.code) msg=$($r.message)" }
    return $r.data
}

function StartEvaluationInstance([string]$name) {
    $e = NewEvaluationDraft $name
    $r = Invoke-Api -Method POST -Path ('/evaluations/' + $e.id + '/submit') -Headers $script:H_ICP
    if ($r.code -ne 0) { throw "submit evaluation failed code=$($r.code) msg=$($r.message)" }
    return $r.data
}

function StartRectifyInstance($rectify) {
    $r = Invoke-Api -Method POST -Path ('/rectifies/' + $rectify.id + '/submit') -Headers $script:H_ICP -Body @{ evidence = '4001,4002' }
    if ($r.code -ne 0) { throw "submit rectify failed code=$($r.code) msg=$($r.message)" }
    return $r.data
}

function StartSealInstance() {
    $form = @{ sealType = 1; reason = $script:SealReason; useDate = '2026-11-15'; fileId = 4001 }
    return StartInstance $script:H_ICP 'seal_apply' 'seal' $form
}

function StartBorrowInstance([int]$docId) {
    $form = @{ docId = $docId; reason = $script:BorrowReason; startDate = '2026-11-05'; endDate = '2026-11-20' }
    return StartInstance $script:H_ICP 'doc_borrow' 'borrow' $form
}

try {
    Step 'login users'
    $script:L_ICP = Login 'icpadmin'
    $script:L_DEPT = Login 'dept01'
    $script:L_STAFF = Login 'staff01'
    $script:L_EXEC = Login 'exec'
    $script:H_ICP = Headers $script:L_ICP
    $script:H_DEPT = Headers $script:L_DEPT
    $script:H_STAFF = Headers $script:L_STAFF
    $script:H_EXEC = Headers $script:L_EXEC
    Step ('icpadmin permissions=' + $script:L_ICP.permissions.Count)

    Step 'check current runtime volume (idempotency guard)'
    $mon = Invoke-Api -Method GET -Path '/workflow/monitor?pageNum=1&pageSize=1' -Headers $script:H_ICP
    if ($mon.code -ne 0) { throw "monitor failed code=$($mon.code)" }
    $current = [int]$mon.data.total
    Step ('current instances=' + $current)
    if ($current -ge 60) {
        Step 'runtime data already seeded (instances >= 60), skip'
        Write-Host 'seed-flow: SKIP (already seeded)'
        exit 0
    }

    $runningTasks = New-Object System.Collections.ArrayList

    Step 'purchase_apply: 6 completed, 8 left at submit node'
    for ($i = 1; $i -le 6; $i++) {
        $inst = StartPurchase $true
        CompleteInstance $inst.processInstanceId 'dept01'
        $script:Pass++
    }
    for ($i = 7; $i -le 14; $i++) {
        $inst = StartPurchase $false
        $t = LeaveAtSubmit $inst.processInstanceId
        [void]$runningTasks.Add($t)
        $script:Pass++
    }

    Step 'seal_apply: 4 completed, 6 left at submit node'
    for ($i = 1; $i -le 4; $i++) {
        $inst = StartSealInstance
        CompleteInstance $inst.processInstanceId 'dept01'
        $script:Pass++
    }
    for ($i = 5; $i -le 10; $i++) {
        $inst = StartSealInstance
        $t = LeaveAtSubmit $inst.processInstanceId
        [void]$runningTasks.Add($t)
        $script:Pass++
    }

    Step 'doc_borrow: 3 completed, 5 left at submit node'
    $docIds = @(3301, 3302, 3303, 3304, 3305, 3306, 3307, 3308)
    for ($i = 0; $i -lt 3; $i++) {
        $inst = StartBorrowInstance $docIds[$i]
        CompleteInstance $inst.processInstanceId 'dept01'
        $script:Pass++
    }
    for ($i = 3; $i -lt 8; $i++) {
        $inst = StartBorrowInstance $docIds[$i]
        $t = LeaveAtSubmit $inst.processInstanceId
        [void]$runningTasks.Add($t)
        $script:Pass++
    }

    Step 'policy_publish: 3 completed, 9 left at approval node'
    for ($i = 1; $i -le 3; $i++) {
        $inst = StartPolicyInstance ($script:PolicyNames[$i - 1])
        CompleteInstance $inst.processInstanceId 'icpadmin'
        $script:Pass++
    }
    for ($i = 4; $i -le 12; $i++) {
        $inst = StartPolicyInstance ($script:PolicyNames[$i - 1])
        $t = LeaveAtApproval $inst.processInstanceId
        [void]$runningTasks.Add($t)
        $script:Pass++
    }

    Step 'evaluation_plan: 2 completed, 8 left at approval node'
    for ($i = 1; $i -le 2; $i++) {
        $inst = StartEvaluationInstance ($script:EvalNames[$i - 1])
        CompleteInstance $inst.processInstanceId 'icpadmin'
        $script:Pass++
    }
    for ($i = 3; $i -le 10; $i++) {
        $inst = StartEvaluationInstance ($script:EvalNames[$i - 1])
        $t = LeaveAtApproval $inst.processInstanceId
        [void]$runningTasks.Add($t)
        $script:Pass++
    }

    Step 'defect_rectify: 2 completed, 4 left at approval node'
    $rectRes = Invoke-Api -Method GET -Path '/rectifies?pageNum=1&pageSize=10&status=0' -Headers $script:H_ICP
    if ($rectRes.code -ne 0) { throw "list rectify failed code=$($rectRes.code)" }
    $rects = @($rectRes.data.list)
    if ($rects.Count -lt 6) { throw "not enough pending rectify rows: $($rects.Count)" }
    for ($i = 0; $i -lt 2; $i++) {
        $inst = StartRectifyInstance $rects[$i]
        CompleteInstance $inst.processInstanceId 'icpadmin'
        $script:Pass++
    }
    for ($i = 2; $i -lt 6; $i++) {
        $inst = StartRectifyInstance $rects[$i]
        $t = LeaveAtApproval $inst.processInstanceId
        [void]$runningTasks.Add($t)
        $script:Pass++
    }

    Step ('running tasks available for urge=' + $runningTasks.Count)
    Step 'urge 20 running tasks (repeat allowed, state unchanged)'
    $urgeCount = 0
    for ($i = 0; $i -lt $runningTasks.Count -and $urgeCount -lt 20; $i++) {
        $task = $runningTasks[$i]
        $h = $script:H_ICP
        if ($task.assigneeName -eq 'dept01') { $h = $script:H_DEPT }
        Urge $h $task.taskId $script:UrgeContent | Out-Null
        $urgeCount++
        $script:Pass++
    }
    Step ('urges created=' + $urgeCount)

    Step 'summary'
    $mon2 = Invoke-Api -Method GET -Path '/workflow/monitor?pageNum=1&pageSize=1' -Headers $script:H_ICP
    $data = $mon2.data
    Step ('instances total=' + $data.total + ' running=' + $data.running + ' completed=' + $data.completed + ' rejected=' + $data.rejected + ' overdue=' + $data.overdue + ' timeoutHours=' + $data.timeoutHours)

    $roles = @('admin', 'icpadmin', 'auditor', 'exec', 'dept01', 'staff01')
    foreach ($r in $roles) {
        $lg = Login $r
        $hh = Headers $lg
        $td = Invoke-Api -Method GET -Path '/workflow/todo?pageNum=1&pageSize=1' -Headers $hh
        if ($td.code -eq 0) {
            Step ('todo ' + $r + '=' + $td.data.total)
        } else {
            Step ('todo ' + $r + ' unavailable code=' + $td.code)
        }
    }

    Write-Host ('seed-flow: PASS=' + $script:Pass + ' FAIL=' + $script:Fail)
    if ($script:Fail -gt 0) { exit 1 }
    exit 0
} catch {
    Write-Host ('seed-flow: ERROR ' + $_.Exception.Message)
    exit 1
}
