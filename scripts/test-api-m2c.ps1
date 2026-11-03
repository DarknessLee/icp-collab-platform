# ---------------------------------------------------------------------------
# M2c API acceptance script (PowerShell 5.1, ASCII only).
# Covers the automatable parts of docs/01 M2c acceptance cases C01-C25 via the
# real REST API against 127.0.0.1:8080 + MySQL 3307. Leaves workflow runtime
# rows in the database (no delete API); run it on a disposable seed database.
# Exit code 0 = all assertions passed.
# ---------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'
$Base = 'http://127.0.0.1:8080/api/v1'
$script:Pass = 0
$script:Fail = 0

function Check($name, $cond, $detail) {
    if ($cond) {
        $script:Pass++
        Write-Host ('[PASS] ' + $name)
    } else {
        $script:Fail++
        Write-Host ('[FAIL] ' + $name + ' :: ' + $detail)
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
        return Invoke-RestMethod @req
    } catch {
        $resp = $_.Exception.Response
        $code = -1
        if ($resp -ne $null) { $code = [int]$resp.StatusCode }
        return [pscustomobject]@{ code = $code; message = $_.Exception.Message; httpError = $true }
    }
}

function Login([string]$user) {
    $r = Api -Method POST -Path '/auth/login' -Body @{ username = $user; password = 'Icp@2026' }
    if ($r.code -ne 0) { throw "login failed: $user" }
    return $r.data
}

function AuthHeader($login) { return @{ Authorization = 'Bearer ' + $login.accessToken } }

function StartFlow($headers, [string]$key, [string]$type, $form, $idem) {
    $hd = @{}
    foreach ($k in $headers.Keys) { $hd[$k] = $headers[$k] }
    if ($idem) { $hd['Idempotency-Key'] = $idem }
    return Api -Method POST -Path '/workflow/instances' -Headers $hd -Body @{ processKey = $key; businessType = $type; formData = $form }
}

function FindTask($headers, [string]$instanceId) {
    $r = Api -Method GET -Path '/workflow/todo?pageNum=1&pageSize=500' -Headers $headers
    if ($r.code -ne 0) { return $null }
    return @($r.data.list) | Where-Object { $_.processInstanceId -eq $instanceId } | Select-Object -First 1
}

function Approve($headers, [string]$taskId, [string]$comment) {
    return Api -Method POST -Path ('/workflow/tasks/' + $taskId + '/approve') -Headers $headers -Body @{ comment = $comment }
}

function Reject($headers, [string]$taskId, [string]$comment) {
    return Api -Method POST -Path ('/workflow/tasks/' + $taskId + '/reject') -Headers $headers -Body @{ comment = $comment }
}

try {
    $icp = Login 'icpadmin'
    $dept = Login 'dept01'
    $staff = Login 'staff01'
    $aud = Login 'auditor'
    $admin = Login 'admin'
    $exec = Login 'exec'
    $submitNodeName = ([char]0x53D1).ToString() + ([char]0x8D77).ToString() + ([char]0x7533).ToString() + ([char]0x8BF7).ToString()
    $hIcp = AuthHeader $icp; $hDept = AuthHeader $dept; $hStaff = AuthHeader $staff; $hAud = AuthHeader $aud; $hAdmin = AuthHeader $admin; $hExec = AuthHeader $exec

    Write-Host '--- C01 process definitions ---'
    $procs = Api -Method GET -Path '/workflow/processes' -Headers $hIcp
    Check 'C01 processes code=0' ($procs.code -eq 0) $procs.message
    $keys = @($procs.data | ForEach-Object { $_.processKey })
    Check 'C01 six definitions' ($keys.Count -eq 6) ('count=' + $keys.Count)
    $expected = @('policy_publish', 'defect_rectify', 'evaluation_plan', 'doc_borrow', 'purchase_apply', 'seal_apply')
    $allThere = $true
    foreach ($k in $expected) { if ($keys -notcontains $k) { $allThere = $false } }
    Check 'C01 expected keys' $allThere ($keys -join ',')
    Check 'C01 version>=1' (@($procs.data | Where-Object { $_.version -ge 1 }).Count -eq 6) 'version'
    Check 'C01 meta version synced' (@($procs.data | Where-Object { $_.metaVersion -eq $_.version }).Count -eq 6) 'metaVersion'

    Write-Host '--- C02 BPMN diagram ---'
    $dg = Api -Method GET -Path '/workflow/processes/purchase_apply/diagram' -Headers $hIcp
    Check 'C02 diagram code=0' ($dg.code -eq 0) $dg.message
    $xml = [string]$dg.data.xml
    Check 'C02 has startEvent' ($xml.Contains('startEvent')) 'xml'
    Check 'C02 has userTask' ($xml.Contains('userTask')) 'xml'
    Check 'C02 has endEvent' ($xml.Contains('endEvent')) 'xml'

    Write-Host '--- C03/C04 start + idempotency ---'
    $form = @{ title = 'M2c api purchase'; amount = 888.88; budgetAmount = 5000; supplier = 'Api Supplier'; purchaseType = 1; purpose = 'api test' }
    $idem = 'm2c-' + (Get-Date -Format 'yyyyMMddHHmmss') + '-1'
    $s1 = StartFlow $hStaff 'purchase_apply' 'purchase' $form $idem
    Check 'C03 start code=0' ($s1.code -eq 0) $s1.message
    Check 'C03 processInstanceId' (-not [string]::IsNullOrEmpty($s1.data.processInstanceId)) 'data'
    Check 'C03 taskId' (-not [string]::IsNullOrEmpty($s1.data.taskId)) 'data'
    Check 'C03 businessNo' ([string]$s1.data.businessNo).StartsWith('CG-') $s1.data.businessNo
    Check 'C03 pendingWorkflow=false' ($s1.data.pendingWorkflow -eq $false) 'pendingWorkflow'
    $pi = $s1.data.processInstanceId
    $s2 = StartFlow $hStaff 'purchase_apply' 'purchase' $form $idem
    Check 'C04 idempotent same instance' ($s2.data.processInstanceId -eq $pi) ($s2.data.processInstanceId)
    Check 'C04 idempotent same businessNo' ($s2.data.businessNo -eq $s1.data.businessNo) ($s2.data.businessNo)

    Write-Host '--- C05 todo isolation ---'
    $staffTodo = Api -Method GET -Path '/workflow/todo?pageNum=1&pageSize=500' -Headers $hStaff
    $found = @($staffTodo.data.list | Where-Object { $_.processInstanceId -eq $pi }).Count -ge 1
    Check 'C05 initiator sees todo' $found 'todo'
    $audTodo = Api -Method GET -Path '/workflow/todo?pageNum=1&pageSize=500' -Headers $hAud
    $leak = @($audTodo.data.list | Where-Object { $_.processInstanceId -eq $pi }).Count -gt 0
    Check 'C05 unrelated role cannot see' (-not $leak) 'leak'

    Write-Host '--- C06 task detail ---'
    $task = FindTask $hStaff $pi
    $det = Api -Method GET -Path ('/workflow/tasks/' + $task.taskId) -Headers $hStaff
    Check 'C06 detail code=0' ($det.code -eq 0) $det.message
    Check 'C06 formSchema present' ($det.data.formSchema -ne $null) 'formSchema'
    Check 'C06 formData readback' ($det.data.formData.title -eq 'M2c api purchase') ($det.data.formData.title)
    Check 'C06 nodeName' ($det.data.nodeName -eq $submitNodeName) $det.data.nodeName
    $deny = Api -Method GET -Path ('/workflow/tasks/' + $task.taskId) -Headers $hAud
    Check 'C06 non-handler denied' ($deny.code -eq 40300 -or $deny.code -eq 403) ('code=' + $deny.code)

    Write-Host '--- C07/C08 approve to completion ---'
    $ap1 = Approve $hStaff $task.taskId 'submit'
    Check 'C07 approve submit code=0' ($ap1.code -eq 0) $ap1.message
    Check 'C07 next node created' (-not [string]::IsNullOrEmpty($ap1.data.nextTaskId)) 'nextTaskId'
    $t2 = FindTask $hDept $pi
    Check 'C07 next assignee todo' ($t2 -ne $null) 'dept todo'
    $ap2 = Approve $hDept $t2.taskId 'ok'
    Check 'C08 final approve code=0' ($ap2.code -eq 0) $ap2.message
    Check 'C08 instance completed' ($ap2.data.linkStatus -eq 1) $ap2.data.linkStatus
    $inst = Api -Method GET -Path ('/workflow/instances/' + $pi) -Headers $hIcp
    Check 'C08 instance status completed' ($inst.data.status -eq 1) $inst.data.status
    $pur = Api -Method GET -Path '/workflow/purchase?pageNum=1&pageSize=500' -Headers $hIcp
    $row = @($pur.data.list | Where-Object { $_.applyNo -eq $s1.data.businessNo }) | Select-Object -First 1
    Check 'C08 purchase approved' ($row.status -eq 2) $row.status
    Check 'C08 purchase amount exact' ([decimal]$row.amount -eq [decimal]888.88) $row.amount

    Write-Host '--- C09/C10 reject + resubmit ---'
    $s3 = StartFlow $hStaff 'purchase_apply' 'purchase' @{ title = 'M2c api reject'; amount = 100; budgetAmount = 1000 } $null
    $pi3 = $s3.data.processInstanceId
    $t = FindTask $hStaff $pi3
    $null = Approve $hStaff $t.taskId 'submit'
    $t2 = FindTask $hDept $pi3
    $rj = Reject $hDept $t2.taskId 'budget too low'
    Check 'C09 reject code=0' ($rj.code -eq 0) $rj.message
    Check 'C09 link rejected' ($rj.data.linkStatus -eq 2) $rj.data.linkStatus
    $inst3 = Api -Method GET -Path ('/workflow/instances/' + $pi3) -Headers $hIcp
    Check 'C09 back to submit node' ($inst3.data.currentNode -eq $submitNodeName) $inst3.data.currentNode
    $pur3 = Api -Method GET -Path '/workflow/purchase?pageNum=1&pageSize=500' -Headers $hIcp
    $row3 = @($pur3.data.list | Where-Object { $_.applyNo -eq $s3.data.businessNo }) | Select-Object -First 1
    Check 'C09 business rolled back' ($row3.status -eq 0) $row3.status
    $rs = Api -Method POST -Path ('/workflow/instances/' + $pi3 + '/resubmit?comment=resubmit-again') -Headers $hStaff
    Check 'C10 resubmit code=0' ($rs.code -eq 0) $rs.message
    Check 'C10 resubmit running' ($rs.data.status -eq 0) $rs.data.status
    $hist = Api -Method GET -Path ('/workflow/instances/' + $pi3 + '/history') -Headers $hIcp
    $hasReject = @($hist.data | Where-Object { $_.action -eq 'reject' }).Count -ge 1
    $hasResubmit = @($hist.data | Where-Object { $_.action -eq 'resubmit' }).Count -ge 1
    $hasStart = @($hist.data | Where-Object { $_.action -eq 'start' }).Count -ge 1
    Check 'C13 history start' $hasStart 'start'
    Check 'C13 history reject retained' $hasReject 'reject'
    Check 'C13 history resubmit appended' $hasResubmit 'resubmit'
    Check 'C13 no sequence rows' (@($hist.data | Where-Object { $_.activityType -eq 'sequenceFlow' }).Count -eq 0) 'sequenceFlow'

    Write-Host '--- C11 urge + C12 done/initiated + C14 highlight ---'
    $t3 = FindTask $hIcp $pi3
    if ($null -eq $t3) { $t3 = FindTask $hDept $pi3 }
    $ur = Api -Method POST -Path ('/workflow/tasks/' + $t3.taskId + '/urge') -Headers $hStaff -Body @{ content = 'hurry up' }
    Check 'C11 urge code=0' ($ur.code -eq 0) $ur.message
    $ur2 = Api -Method POST -Path ('/workflow/tasks/' + $t3.taskId + '/urge') -Headers $hStaff -Body @{ content = 'hurry up again' }
    Check 'C11 repeated urge allowed' ($ur2.code -eq 0) $ur2.message
    $done = Api -Method GET -Path '/workflow/done?pageNum=1&pageSize=100' -Headers $hDept
    Check 'C12 done list' ($done.code -eq 0 -and $done.data.total -ge 1) $done.data.total
    $init = Api -Method GET -Path '/workflow/initiated?pageNum=1&pageSize=100' -Headers $hStaff
    $mine = @($init.data.list | Where-Object { $_.processInstanceId -eq $pi3 }).Count -ge 1
    Check 'C12 initiated contains mine' $mine 'initiated'
    $hl = Api -Method GET -Path ('/workflow/instances/' + $pi3 + '/diagram-highlight') -Headers $hIcp
    Check 'C14 highlight activity' (-not [string]::IsNullOrEmpty($hl.data.activityId)) $hl.data.activityId
    Check 'C14 finished activities' (@($hl.data.finishedActivityIds).Count -ge 1) 'finished'

    Write-Host '--- C15 monitor ---'
    $mon = Api -Method GET -Path '/workflow/monitor?pageNum=1&pageSize=5' -Headers $hAdmin
    Check 'C15 admin monitor code=0' ($mon.code -eq 0) $mon.message
    Check 'C15 stats keys' ($mon.data.running -ne $null -and $mon.data.completed -ne $null -and $mon.data.overdue -ne $null) 'stats'
    $monOver = Api -Method GET -Path '/workflow/monitor?pageNum=1&pageSize=5&timeoutHours=0' -Headers $hAdmin
    Check 'C15 overdue override' ($monOver.data.overdue -ge 1) $monOver.data.overdue
    $monStaff = Api -Method GET -Path '/workflow/monitor' -Headers $hStaff
    Check 'C15 staff denied' ($monStaff.code -eq 40300 -or $monStaff.code -eq 403) ('code=' + $monStaff.code)

    Write-Host '--- C17 policy integration ---'
    $pol = Api -Method POST -Path '/policies' -Headers $hIcp -Body @{ policyName = 'M2c api policy'; categoryId = 1001; content = 'policy body'; effectiveDate = '2026-11-01'; expireDate = '2027-10-31' }
    $policyId = $pol.data.id
    $ps = Api -Method POST -Path ('/policies/' + $policyId + '/submit') -Headers $hIcp
    Check 'C17 submit pendingWorkflow=false' ($ps.data.pendingWorkflow -eq $false) $ps.data.pendingWorkflow
    Check 'C17 submit processInstanceId' (-not [string]::IsNullOrEmpty($ps.data.processInstanceId)) 'pi'
    $t = FindTask $hIcp $ps.data.processInstanceId
    $null = Approve $hIcp $t.taskId 'submit'
    $t2 = FindTask $hIcp $ps.data.processInstanceId
    $null = Approve $hIcp $t2.taskId 'approved'
    $polDetail = Api -Method GET -Path ('/policies/' + $policyId) -Headers $hIcp
    Check 'C17 policy effective after approval' ($polDetail.data.status -eq 2) $polDetail.data.status
    Check 'C17 publishTime set' ($polDetail.data.publishTime -ne $null) $polDetail.data.publishTime

    Write-Host '--- C18 evaluation integration ---'
    $ev = Api -Method POST -Path '/evaluations' -Headers $hIcp -Body @{ planName = 'M2c api evaluation'; planYear = 2026; planType = 1; startDate = '2026-11-10'; endDate = '2026-12-20'; deptScope = '2' }
    $eid = $ev.data.id
    $es = Api -Method POST -Path ('/evaluations/' + $eid + '/submit') -Headers $hIcp
    Check 'C18 submit pendingWorkflow=false' ($es.data.pendingWorkflow -eq $false) $es.data.pendingWorkflow
    $t = FindTask $hIcp $es.data.processInstanceId
    $null = Approve $hIcp $t.taskId 'submit'
    $t2 = FindTask $hIcp $es.data.processInstanceId
    $null = Approve $hIcp $t2.taskId 'approved'
    $evDetail = Api -Method GET -Path ('/evaluations/' + $eid) -Headers $hIcp
    Check 'C18 plan submitted after approval' ($evDetail.data.status -eq 1) $evDetail.data.status

    Write-Host '--- C19 rectify integration ---'
    $rectRes = Api -Method GET -Path '/rectifies?pageNum=1&pageSize=10&status=0' -Headers $hIcp
    $rect = @($rectRes.data.list)[0]
    $rs2 = Api -Method POST -Path ('/rectifies/' + $rect.id + '/submit') -Headers $hIcp -Body @{ evidence = '4001,4002' }
    Check 'C19 rectify submit status=1' ($rs2.data.status -eq 1) $rs2.data.status
    Check 'C19 pendingWorkflow=false' ($rs2.data.pendingWorkflow -eq $false) $rs2.data.pendingWorkflow
    $t = FindTask $hIcp $rs2.data.processInstanceId
    $null = Approve $hIcp $t.taskId 'submit'
    $t2 = FindTask $hIcp $rs2.data.processInstanceId
    $null = Approve $hIcp $t2.taskId 'approved'
    $rec = Api -Method GET -Path ('/rectifies/' + $rect.id) -Headers $hIcp
    $df = Api -Method GET -Path ('/defects/' + $rect.defectId) -Headers $hIcp
    Check 'C19 rectify approved' ($rec.data.status -eq 2) $rec.data.status
    Check 'C19 defect reviewing' ($df.data.status -eq 2) $df.data.status

    Write-Host '--- C16 seal + borrow chains ---'
    $seal = StartFlow $hIcp 'seal_apply' 'seal' @{ sealType = 1; reason = 'api seal'; useDate = '2026-11-20'; fileId = 4001 } $null
    Check 'C16 seal start' ($seal.code -eq 0) $seal.message
    $t = FindTask $hIcp $seal.data.processInstanceId
    $null = Approve $hIcp $t.taskId 'submit'
    $t2 = FindTask $hDept $seal.data.processInstanceId
    $null = Approve $hDept $t2.taskId 'ok'
    $seals = Api -Method GET -Path '/workflow/seal?pageNum=1&pageSize=500' -Headers $hIcp
    $srow = @($seals.data.list | Where-Object { $_.applyNo -eq $seal.data.businessNo }) | Select-Object -First 1
    Check 'C16 seal approved' ($srow.status -eq 2) $srow.status
    $bor = StartFlow $hIcp 'doc_borrow' 'borrow' @{ docId = 3310; reason = 'api borrow'; startDate = '2026-11-05'; endDate = '2026-11-20' } $null
    Check 'C16 borrow start' ($bor.code -eq 0) $bor.message
    $t = FindTask $hIcp $bor.data.processInstanceId
    $null = Approve $hIcp $t.taskId 'submit'
    $t2 = FindTask $hDept $bor.data.processInstanceId
    $null = Approve $hDept $t2.taskId 'ok'
    $brs = Api -Method GET -Path '/workflow/borrows?pageNum=1&pageSize=500' -Headers $hIcp
    $brow = @($brs.data.list | Where-Object { $_.docId -eq 3310 -and $_.status -eq 1 }) | Select-Object -First 1
    Check 'C16 borrow approved' ($brow -ne $null) 'borrow row'

    Write-Host '--- C20/C21 validation ---'
    $bad = StartFlow $hStaff 'purchase_apply' 'purchase' @{ title = 'bad amount'; amount = 0; budgetAmount = 100 } $null
    Check 'C20 amount<=0 rejected' ($bad.code -eq 40000) $bad.code
    $over = StartFlow $hStaff 'purchase_apply' 'purchase' @{ title = 'over budget'; amount = 500; budgetAmount = 100 } $null
    Check 'C20 over budget rejected' ($over.code -eq 40000) $over.code
    $badSeal = StartFlow $hIcp 'seal_apply' 'seal' @{ sealType = 1; reason = 'bad file'; useDate = '2026-11-20'; fileId = 999999 } $null
    Check 'C21 invalid attachment rejected' ($badSeal.code -eq 40000) $badSeal.code

    Write-Host '--- C23 audit ---'
    $logs = Api -Method GET -Path '/logs/oper?pageNum=1&pageSize=50&module=%E6%B5%81%E7%A8%8B%E4%B8%AD%E5%BF%83' -Headers $hAdmin
    Check 'C23 oper log code=0' ($logs.code -eq 0) $logs.message
    Check 'C23 workflow audit rows' ($logs.data.total -ge 1) $logs.data.total

    Write-Host ''
    Write-Host ('test-api-m2c: PASS=' + $script:Pass + ' FAIL=' + $script:Fail)
    if ($script:Fail -gt 0) { exit 1 }
    exit 0
} catch {
    Write-Host ('test-api-m2c: ERROR ' + $_.Exception.Message)
    exit 1
}
