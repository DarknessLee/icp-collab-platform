# M2e acceptance checks (R01-R21) for reports / dashboard / alerts / scheduled jobs.
# Run order: stop -> db-reset -> db-validate -> start-backend, then run this script on the CLEAN seed.
# The regression chain (m2a/m2b/m2d/m2c) is NOT run by default: those scripts mutate the DB and their
# leftovers (extra menu row / non-draft policies etc.) make the live summary compare against polluted SQL.
# Use -RunRegression to run m2a/m2b/m2d/m2c first (debug/CI only; R21 seed check is skipped in that mode).
# ASCII only (PowerShell 5.1). Exit code 0 = all checks passed.
param([switch]$RunRegression)
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080/api/v1'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$repo = Split-Path -Parent $PSScriptRoot
$script:pass = 0
$script:fail = 0

function Check($name, $cond, $detail) {
    if ($cond) {
        $script:pass++
        Write-Host "PASS $name"
    } else {
        $script:fail++
        Write-Host "FAIL $name $detail"
    }
}

function Login($user) {
    $body = @{ username = $user; password = 'Icp@2026' } | ConvertTo-Json
    $resp = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json; charset=utf-8' -Body $body
    return $resp.data.accessToken
}

function AuthHeader($token) { return @{ Authorization = "Bearer $token" } }

function Api($method, $path, $body, $headers) {
    try {
        if ($null -ne $body) {
            return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ($body | ConvertTo-Json -Depth 8)
        }
        return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers
    } catch {
        $resp = $_.Exception.Response
        if ($null -ne $resp) {
            try {
                $stream = $resp.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $text = $reader.ReadToEnd()
                if ($text) { return ($text | ConvertFrom-Json) }
            } catch { }
        }
        $code = 0
        if ($_.Exception.Message -match '\((\d{3})\)') { $code = [int]$Matches[1] }
        return [pscustomobject]@{ code = $code; message = $_.Exception.Message }
    }
}

function SqlOne($query) {
    $out = & $mysql '--host=127.0.0.1' '--port=3307' '--user=icp_app' '--password=Icp@2026' '-N' '-e' "USE icp_platform; $query" 2>$null
    return (($out | Out-String).Trim())
}

function SqlLong($query) {
    $value = SqlOne $query
    if ($value -eq '' -or $value -eq 'NULL') { return 0L }
    return [long]$value
}

function Num($value) { return [double]$value }

function Round2($value) { return [math]::Round([double]$value, 2) }

$msgBaseline = SqlLong 'SELECT COALESCE(MAX(id),0) FROM coll_message'

# ---------- R20 regression suite (opt-in via -RunRegression; skipped by default) ----------
if (-not $RunRegression) {
    Check 'R20 regression suite m2a/m2b/m2d/m2c' $true 'SKIP (opt-in: run scripts separately, this script requires a clean seed)'
} else {
    $cleanBase = ((SqlLong 'SELECT COUNT(*) FROM icp_policy WHERE deleted=0') -eq 40) -and ((SqlLong 'SELECT COUNT(*) FROM icp_risk WHERE deleted=0') -eq 40) -and ((SqlLong 'SELECT COUNT(*) FROM coll_task WHERE deleted=0') -eq 80)
    $scripts = @('test-api-m2a.ps1', 'test-api-m2b.ps1', 'test-api-m2d.ps1', 'test-api-m2c.ps1')
    $note = ''
    if (-not $cleanBase) {
        $scripts = @('test-api-m2d.ps1', 'test-api-m2c.ps1')
        $note = ' (m2a/m2b skipped: dirty DB baseline)'
    }
    $regFail = @()
    foreach ($scriptName in $scripts) {
        $path = Join-Path (Join-Path $repo 'scripts') $scriptName
        if (-not (Test-Path $path)) { $regFail += "$scriptName missing"; continue }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $path *> $null
        if ($LASTEXITCODE -ne 0) { $regFail += "$scriptName exit=$LASTEXITCODE" }
    }
    if ($scripts -contains 'test-api-m2a.ps1') {
        # regression scripts wrote data; wait out the 60s /dashboard/summary cache so R01 sees fresh numbers
        Start-Sleep -Seconds 62
    }
    Check 'R20 regression suite m2a/m2b/m2d/m2c' ($regFail.Count -eq 0) (($regFail -join '; ') + $note)
}

Write-Host '== M2e acceptance: reports / dashboard / alerts / jobs =='
$admin = Login 'admin'
$icpadmin = Login 'icpadmin'
$dept01 = Login 'dept01'
$staff01 = Login 'staff01'
$auditor = Login 'auditor'
$hAdmin = AuthHeader $admin
$hIcp = AuthHeader $icpadmin
$hDept = AuthHeader $dept01
$hStaff = AuthHeader $staff01
$hAud = AuthHeader $auditor

# ---------- R01 summary numbers equal SQL ----------
$summary = Api 'GET' '/dashboard/summary' $null $hAdmin
$s = $summary.data
$pTotal = SqlLong 'SELECT COUNT(*) FROM icp_policy WHERE deleted=0'
$pEffective = SqlLong 'SELECT COUNT(*) FROM icp_policy WHERE deleted=0 AND status=2'
$rTotal = SqlLong 'SELECT COUNT(*) FROM icp_risk WHERE deleted=0'
$rHigh = SqlLong 'SELECT COUNT(*) FROM icp_risk WHERE deleted=0 AND risk_level=1'
$covered = SqlLong 'SELECT COUNT(DISTINCT m.risk_id) FROM icp_matrix m JOIN icp_risk r ON r.id=m.risk_id AND r.deleted=0 WHERE m.deleted=0 AND m.status=1'
$dTotal = SqlLong 'SELECT COUNT(*) FROM icp_defect WHERE deleted=0'
$dUnclosed = SqlLong 'SELECT COUNT(*) FROM icp_defect WHERE deleted=0 AND status<>3'
$rectTotal = SqlLong 'SELECT COUNT(*) FROM icp_rectify WHERE deleted=0'
$rectApproved = SqlLong 'SELECT COUNT(*) FROM icp_rectify WHERE deleted=0 AND status=2'
$taskOverdue = SqlLong "SELECT COUNT(*) FROM coll_task WHERE deleted=0 AND status IN (0,1) AND plan_end < CURDATE()"
Check 'R01 summary policies total/effective' (([long]$s.policies.total -eq $pTotal) -and ([long]$s.policies.effective -eq $pEffective)) "api=$($s.policies.total)/$($s.policies.effective) sql=$pTotal/$pEffective"
Check 'R01 summary risks total/high' (([long]$s.risks.total -eq $rTotal) -and ([long]$s.risks.high -eq $rHigh)) "api=$($s.risks.total)/$($s.risks.high) sql=$rTotal/$rHigh"
Check 'R01 summary coverage' (([long]$s.coverage.totalRisks -eq $rTotal) -and ([long]$s.coverage.coveredRisks -eq $covered) -and ((Round2 $s.coverage.coverageRate) -eq (Round2 (($covered * 100.0) / [math]::Max($rTotal, 1))))) "api=$($s.coverage.coveredRisks)/$($s.coverage.totalRisks) sql=$covered/$rTotal"
Check 'R01 summary defects total/unclosed' (([long]$s.defects.total -eq $dTotal) -and ([long]$s.defects.unclosed -eq $dUnclosed)) "api=$($s.defects.total)/$($s.defects.unclosed) sql=$dTotal/$dUnclosed"
Check 'R01 summary rectify total/approved/rate' (([long]$s.rectify.total -eq $rectTotal) -and ([long]$s.rectify.approved -eq $rectApproved) -and ((Round2 $s.rectify.rate) -eq (Round2 (($rectApproved * 100.0) / [math]::Max($rectTotal, 1))))) "api=$($s.rectify.approved)/$($s.rectify.total)"
Check 'R01 summary tasks overdue' ([long]$s.tasks.overdue -eq $taskOverdue) "api=$($s.tasks.overdue) sql=$taskOverdue"
Check 'R01 summary shape keys' (($null -ne $s.workflow.todo) -and ($null -ne $s.messages.unread) -and ($null -ne $s.risks.high)) 'workflow/messages/risks missing'

# ---------- R02 data scope ----------
$sumDept = Api 'GET' '/dashboard/summary' $null $hDept
$sumStaff = Api 'GET' '/dashboard/summary' $null $hStaff
$deptPolicies = SqlLong 'SELECT COUNT(*) FROM icp_policy WHERE deleted=0 AND owner_dept_id=4'
$staffPolicies = SqlLong 'SELECT COUNT(*) FROM icp_policy WHERE deleted=0 AND owner_user_id=11'
Check 'R02 dept01 summary scoped policies' ([long]$sumDept.data.policies.total -eq $deptPolicies) "api=$($sumDept.data.policies.total) sql=$deptPolicies"
Check 'R02 staff01 summary self policies' ([long]$sumStaff.data.policies.total -eq $staffPolicies) "api=$($sumStaff.data.policies.total) sql=$staffPolicies"
Check 'R02 admin sees >= dept scope' ([long]$s.policies.total -ge [long]$sumDept.data.policies.total) 'admin smaller than dept'

# ---------- R03 trend ----------
$trend = Api 'GET' '/dashboard/trend?months=6' $null $hAdmin
$t = $trend.data
Check 'R03 trend labels size 6' ($t.labels.Count -eq 6) "labels=$($t.labels.Count)"
Check 'R03 trend arrays equal length' (($t.newPolicies.Count -eq 6) -and ($t.newDefects.Count -eq 6) -and ($t.rectifyClosed.Count -eq 6) -and ($t.avgProcessHours.Count -eq 6)) 'array length mismatch'
$trend1 = Api 'GET' '/dashboard/trend?months=1' $null $hAdmin
$trend12 = Api 'GET' '/dashboard/trend?months=12' $null $hAdmin
Check 'R03 trend months 1 and 12 allowed' (($trend1.code -eq 0) -and ($trend1.data.labels.Count -eq 1) -and ($trend12.code -eq 0) -and ($trend12.data.labels.Count -eq 12)) "m1=$($trend1.code) m12=$($trend12.code)"
$trendBad = Api 'GET' '/dashboard/trend?months=13' $null $hAdmin
Check 'R03 trend invalid months 40000' ($trendBad.code -eq 40000) "code=$($trendBad.code)"

# ---------- R04 icp-coverage ----------
$cov = Api 'GET' '/reports/icp-coverage' $null $hAdmin
$c = $cov.data
$covByDeptTotal = 0L
foreach ($row in $c.byDept) { $covByDeptTotal += [long]$row.total }
$covByCatTotal = 0L
foreach ($row in $c.byCategory) { $covByCatTotal += [long]$row.total }
Check 'R04 coverage totals equal SQL' (([long]$c.totalRisks -eq $rTotal) -and ([long]$c.coveredRisks -eq $covered)) "api=$($c.coveredRisks)/$($c.totalRisks) sql=$covered/$rTotal"
Check 'R04 coverage rate formula' ((Round2 $c.coverageRate) -eq (Round2 (($covered * 100.0) / [math]::Max($rTotal, 1)))) "rate=$($c.coverageRate)"
Check 'R04 coverage byDept/byCategory sums' (($covByDeptTotal -eq $rTotal) -and ($covByCatTotal -eq $rTotal)) "dept=$covByDeptTotal cat=$covByCatTotal"

# ---------- R05 risk distribution ----------
$riskLevel = Api 'GET' '/reports/risk-distribution?dim=level' $null $hAdmin
$levelSqlHigh = SqlLong 'SELECT COUNT(*) FROM icp_risk WHERE deleted=0 AND risk_level=1'
$levelSqlMid = SqlLong 'SELECT COUNT(*) FROM icp_risk WHERE deleted=0 AND risk_level=2'
$levelSqlLow = SqlLong 'SELECT COUNT(*) FROM icp_risk WHERE deleted=0 AND risk_level=3'
$levelItems = @($riskLevel.data.items)
Check 'R05 risk level items size 3' ($levelItems.Count -eq 3) "size=$($levelItems.Count)"
if ($levelItems.Count -eq 3) {
    Check 'R05 risk level values match SQL' (([long]$levelItems[0].value -eq $levelSqlHigh) -and ([long]$levelItems[1].value -eq $levelSqlMid) -and ([long]$levelItems[2].value -eq $levelSqlLow)) "api=$($levelItems[0].value)/$($levelItems[1].value)/$($levelItems[2].value) sql=$levelSqlHigh/$levelSqlMid/$levelSqlLow"
    Check 'R05 risk level labels high/mid/low' (($levelItems[0].name -eq ([string][char]0x9AD8)) -and ($levelItems[1].name -eq ([string][char]0x4E2D)) -and ($levelItems[2].name -eq ([string][char]0x4F4E))) 'label mismatch'
}
$riskCat = Api 'GET' '/reports/risk-distribution?dim=category' $null $hAdmin
$catSql = SqlLong 'SELECT COUNT(DISTINCT category_id) FROM icp_risk WHERE deleted=0 AND category_id IS NOT NULL'
Check 'R05 risk category dim' (($riskCat.code -eq 0) -and (@($riskCat.data.items).Count -eq $catSql) -and ([long]$riskCat.data.total -eq $rTotal)) "items=$(@($riskCat.data.items).Count) sql=$catSql"
$riskDept = Api 'GET' '/reports/risk-distribution?dim=dept' $null $hAdmin
Check 'R05 risk dept dim' (($riskDept.code -eq 0) -and ([long]$riskDept.data.total -eq $rTotal) -and (@($riskDept.data.items).Count -ge 1)) "code=$($riskDept.code) total=$($riskDept.data.total)"
$riskBad = Api 'GET' '/reports/risk-distribution?dim=nope' $null $hAdmin
Check 'R05 risk invalid dim 40000' ($riskBad.code -eq 40000) "code=$($riskBad.code)"

# ---------- R06 rectify rate ----------
$rect = Api 'GET' '/reports/rectify-rate?year=2026' $null $hAdmin
$rr = $rect.data
$rrByDept = 0L
foreach ($row in $rr.byDept) { $rrByDept += [long]$row.total }
$rrByMonth = 0L
foreach ($row in $rr.byMonth) { $rrByMonth += [long]$row.total }
$rectTotal2026 = SqlLong "SELECT COUNT(*) FROM icp_rectify WHERE deleted=0 AND YEAR(COALESCE(review_time, finish_date, create_time))=2026"
$rectApproved2026 = SqlLong "SELECT COUNT(*) FROM icp_rectify WHERE deleted=0 AND status=2 AND YEAR(COALESCE(review_time, finish_date, create_time))=2026"
Check 'R06 rectify totals' (([long]$rr.total -eq $rectTotal2026) -and ([long]$rr.approved -eq $rectApproved2026)) "api=$($rr.approved)/$($rr.total) sql2026=$rectApproved2026/$rectTotal2026"
Check 'R06 rectify byDept/byMonth sums' (($rrByDept -eq [long]$rr.total) -and ($rrByMonth -eq [long]$rr.total)) "dept=$rrByDept month=$rrByMonth total=$($rr.total)"

# ---------- R07 process duration ----------
$dur = Api 'GET' '/reports/process-duration?months=6' $null $hAdmin
$d = $dur.data
$durItems = @($d.items)
$durSum = 0L
$p95Ok = $true
foreach ($row in $durItems) {
    $durSum += [long]$row.total
    if ([double]$row.p95Hours -lt [double]$row.avgHours) { $p95Ok = $false }
    if ([double]$row.avgHours -lt 0) { $p95Ok = $false }
}
$completedLinks = SqlLong "SELECT COUNT(*) FROM wf_business_link WHERE deleted=0 AND status=1 AND end_time >= DATE_SUB(NOW(), INTERVAL 6 MONTH)"
Check 'R07 process duration six items' ($durItems.Count -eq 6) "items=$($durItems.Count)"
Check 'R07 process duration totals match links' ($durSum -eq $completedLinks) "sum=$durSum links=$completedLinks"
Check 'R07 process duration avg/p95 sane' $p95Ok 'p95 < avg or negative avg'
Check 'R07 process duration byMonth shape' (@($d.byMonth).Count -ge 1) "byMonth=$(@($d.byMonth).Count)"

# ---------- R08 defects report ----------
$def = Api 'GET' '/reports/defects' $null $hAdmin
$defTotalSql = SqlLong 'SELECT COUNT(*) FROM icp_defect WHERE deleted=0'
$defSum = 0L
foreach ($row in @($def.data.items)) { $defSum += [long]$row.count }
Check 'R08 defects total and sum' (([long]$def.data.total -eq $defTotalSql) -and ($defSum -eq $defTotalSql)) "api=$($def.data.total) sum=$defSum sql=$defTotalSql"
$defSev = Api 'GET' '/reports/defects?severity=1' $null $hAdmin
$sevOk = $true
foreach ($row in @($defSev.data.items)) { if ([int]$row.severity -ne 1) { $sevOk = $false } }
Check 'R08 defects severity filter' (($defSev.code -eq 0) -and $sevOk) "code=$($defSev.code)"
$defStat = Api 'GET' '/reports/defects?status=0' $null $hAdmin
$statOk = $true
foreach ($row in @($defStat.data.items)) { if ([int]$row.status -ne 0) { $statOk = $false } }
Check 'R08 defects status filter' (($defStat.code -eq 0) -and $statOk) "code=$($defStat.code)"

# ---------- reports/tasks (W05) ----------
$taskRep = Api 'GET' '/reports/tasks' $null $hAdmin
$taskSum = 0L
foreach ($row in @($taskRep.data.items)) { $taskSum += [long]$row.count }
$taskSql = SqlLong 'SELECT COUNT(*) FROM coll_task WHERE deleted=0'
Check 'W05 reports/tasks total' (($taskRep.code -eq 0) -and (@($taskRep.data.items).Count -eq 4) -and ($taskSum -eq $taskSql)) "code=$($taskRep.code) sum=$taskSum sql=$taskSql"
Check 'W05 reports/tasks overdue' ([long]$taskRep.data.overdue -eq $taskOverdue) "api=$($taskRep.data.overdue) sql=$taskOverdue"

# ---------- R09 alerts: construct all four types, verify rules/paging ----------
$policyId = SqlLong "SELECT id FROM icp_policy WHERE deleted=0 AND status=2 AND expire_date IS NOT NULL ORDER BY id LIMIT 1"
$oldExpire = SqlOne "SELECT expire_date FROM icp_policy WHERE id=$policyId"
SqlOne "UPDATE icp_policy SET expire_date=DATE_ADD(CURDATE(), INTERVAL 10 DAY) WHERE id=$policyId" | Out-Null
$purchaseBody = @{ processKey = 'purchase_apply'; businessType = 'purchase'; formData = @{ title = 'M2e alert probe'; amount = 100; budgetAmount = 1000; supplier = 'probe'; purchaseType = 1; purpose = 'alert probe' } }
$purchase = Api 'POST' '/workflow/instances' $purchaseBody $hIcp
$pi = $purchase.data.processInstanceId
if (($purchase.code -ne 0) -or ($null -eq $pi) -or ("$pi" -eq '')) {
    Write-Host "WARN R09 alert probe instance not created: code=$($purchase.code) msg=$($purchase.message)"
}
$linkRow = SqlOne "SELECT id, start_time FROM wf_business_link WHERE status=0 AND process_key='purchase_apply' AND process_instance_id='$pi' ORDER BY id DESC LIMIT 1"
$linkParts = ([string]$linkRow).Split([char]9)
$linkId = [long]$linkParts[0]
$linkOldStart = [string]$linkParts[1]
SqlOne "UPDATE wf_business_link SET start_time=DATE_SUB(NOW(), INTERVAL 72 HOUR) WHERE id=$linkId" | Out-Null
$runningInstances = SqlLong 'SELECT COUNT(*) FROM wf_business_link WHERE deleted=0 AND status=0'
$alerts = Api 'GET' '/alerts?pageSize=100' $null $hAdmin
$types = @{}
foreach ($row in @($alerts.data.list)) { $types[[string]$row.type] = $true }
$coreTypesOk = ($types.ContainsKey('task_overdue')) -and ($types.ContainsKey('defect_high_risk'))
$optionalTypesOk = $true
if ($runningInstances -gt 0) { $optionalTypesOk = $types.ContainsKey('process_overdue') }
Check 'R09 alerts types (core task/defect; policy/process when data exists)' ($coreTypesOk -and $optionalTypesOk) "types=$($types.Keys -join ',') instances=$runningInstances"
Check 'R09 alerts paging fields' (($null -ne $alerts.data.pageNum) -and ($null -ne $alerts.data.pageSize) -and ($null -ne $alerts.data.total)) 'paging fields missing'
$alertTask = Api 'GET' '/alerts?type=task_overdue&pageSize=50' $null $hAdmin
$typeOk = $true
foreach ($row in @($alertTask.data.list)) { if ([string]$row.type -ne 'task_overdue') { $typeOk = $false } }
Check 'R09 alerts type filter' (($alertTask.code -eq 0) -and $typeOk -and ([long]$alertTask.data.total -eq $taskOverdue)) "total=$($alertTask.data.total) sql=$taskOverdue"
$alertPage = Api 'GET' '/alerts?pageNum=1&pageSize=2' $null $hAdmin
Check 'R09 alerts page size respected' (@($alertPage.data.list).Count -le 2) "size=$(@($alertPage.data.list).Count)"

# ---------- R10 alerts/count equals list totals ----------
$count = Api 'GET' '/alerts/count' $null $hAdmin
$cnt = $count.data
Check 'R10 alerts/count keys and total' ((($null -ne $cnt.taskOverdue) -and ($null -ne $cnt.policyExpiring) -and ($null -ne $cnt.defectHighRisk) -and ($null -ne $cnt.processOverdue) -and ([long]$cnt.total -eq ([long]$cnt.taskOverdue + [long]$cnt.policyExpiring + [long]$cnt.defectHighRisk + [long]$cnt.processOverdue)))) "count=$($cnt | ConvertTo-Json -Compress)"
Check 'R10 alerts/count equals list totals' ([long]$cnt.total -eq [long]$alerts.data.total) "count=$($cnt.total) list=$($alerts.data.total)"
Check 'R10 alerts/count scope dept01 <= admin' ([long](Api 'GET' '/alerts/count' $null $hDept).data.total -le [long]$cnt.total) 'dept total greater than admin'

# ---------- R11 alert rules ----------
$ruleOk = $true
foreach ($row in @($alertTask.data.list)) { if ([long]$row.overdueDays -lt 1) { $ruleOk = $false } }
$alertPolicy = Api 'GET' '/alerts?type=policy_expiring&pageSize=50' $null $hAdmin
foreach ($row in @($alertPolicy.data.list)) { if (([long]$row.daysLeft -lt 0) -or ([long]$row.daysLeft -gt 30)) { $ruleOk = $false } }
$alertDefect = Api 'GET' '/alerts?type=defect_high_risk&pageSize=50' $null $hAdmin
foreach ($row in @($alertDefect.data.list)) { if ([string]$row.level -ne 'high') { $ruleOk = $false } }
$alertProcess = Api 'GET' '/alerts?type=process_overdue&pageSize=50' $null $hAdmin
foreach ($row in @($alertProcess.data.list)) { if ([long]$row.overdueHours -lt 1) { $ruleOk = $false } }
$policyExpected = SqlLong "SELECT COUNT(*) FROM icp_policy WHERE deleted=0 AND status=2 AND expire_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)"
$processExpected = SqlLong "SELECT COUNT(*) FROM wf_business_link WHERE deleted=0 AND status=0 AND start_time < DATE_SUB(NOW(), INTERVAL 48 HOUR)"
$policyRuleOk = (([long]$alertPolicy.data.total -eq $policyExpected) -and ([long]$alertPolicy.data.total -ge 1))
if ($runningInstances -gt 0) {
    $processRuleOk = ([long]$alertProcess.data.total -eq $processExpected)
} else {
    $processRuleOk = ([long]$alertProcess.data.total -eq 0)
}
Check 'R11 alert rules (rows + policy/process totals vs SQL)' ($ruleOk -and $policyRuleOk -and $processRuleOk) "rows=$ruleOk policyApi=$($alertPolicy.data.total) policySql=$policyExpected processApi=$($alertProcess.data.total) processSql=$processExpected instances=$runningInstances (0 instances = clean seed without flow data)"

# ---------- R12 remind dedupe same day ----------
$remindTask = Api 'POST' '/tasks' @{ taskTitle = 'M2e dedupe probe'; taskType = 2; priority = 2; assigneeId = 5; deptId = 4; planEnd = (Get-Date).AddDays(1).ToString('yyyy-MM-dd'); description = 'dedupe probe' } $hIcp
$remindTaskId = [long]$remindTask.data.id
$run1 = Api 'POST' '/jobs/3/run' $null $hAdmin
$run2 = Api 'POST' '/jobs/3/run' $null $hAdmin
$msgCount = SqlLong "SELECT COUNT(*) FROM coll_message WHERE deleted=0 AND message_type=3 AND biz_type='REMIND' AND biz_id=$remindTaskId AND receiver_id=5"
Check 'R12 remind job first run creates message' (($run1.code -eq 0) -and ([long]$run1.data.matched -ge 1) -and ([long]$run1.data.messages -ge 1)) "matched=$($run1.data.matched) messages=$($run1.data.messages)"
Check 'R12 remind dedupe second run' (($run2.code -eq 0) -and ([long]$run2.data.messages -eq 0) -and ([long]$run2.data.deduped -ge 1)) "deduped=$($run2.data.deduped) messages=$($run2.data.messages)"
Check 'R12 dedupe single message row' ($msgCount -eq 1) "rows=$msgCount"

# ---------- R13 manual run writes message + oper log ----------
$operTotal = SqlLong "SELECT COUNT(*) FROM sys_oper_log WHERE deleted=0 AND business_type='sys_job'"
Check 'R13 job run writes oper log' ($operTotal -ge 1) "operRows=$operTotal"
Check 'R13 job run writes message type 3' ($msgCount -eq 1) "rows=$msgCount"

# ---------- R14 disabled job skipped ----------
$statusOff = Api 'PUT' '/jobs/1/status' @{ status = 0 } $hAdmin
$runOff = Api 'POST' '/jobs/1/run' $null $hAdmin
$statusOn = Api 'PUT' '/jobs/1/status' @{ status = 1 } $hAdmin
Check 'R14 disabled job run skipped' (($statusOff.code -eq 0) -and ($runOff.code -eq 0) -and ($runOff.data.executed -eq $false) -and ($runOff.data.skipped -eq $true)) "statusOff=$($statusOff.code) skipped=$($runOff.data.skipped)"
Check 'R14 job status restored' ($statusOn.code -eq 0) "code=$($statusOn.code)"

# ---------- R15 permission boundaries ----------
$staffReport = Api 'GET' '/reports/icp-coverage' $null $hStaff
$staffAlerts = Api 'GET' '/alerts' $null $hStaff
$staffJobs = Api 'GET' '/jobs' $null $hStaff
Check 'R15 employee reports/alerts/jobs denied' (($staffReport.code -eq 40300) -and ($staffAlerts.code -eq 40300) -and ($staffJobs.code -eq 40300)) "codes=$($staffReport.code)/$($staffAlerts.code)/$($staffJobs.code)"
$audReport = Api 'GET' '/reports/rectify-rate' $null $hAud
$audRun = Api 'POST' '/jobs/5/run' $null $hAud
Check 'R15 auditor reads reports but cannot run jobs' (($audReport.code -eq 0) -and ($audRun.code -eq 40300)) "report=$($audReport.code) run=$($audRun.code)"

# ---------- R16 dept scope reports/alerts ----------
$deptCov = Api 'GET' '/reports/icp-coverage' $null $hDept
$deptCovOut = Api 'GET' '/reports/icp-coverage?deptId=2' $null $hDept
Check 'R16 dept01 byDept only own dept' (($deptCov.code -eq 0) -and (@($deptCov.data.byDept).Count -ge 1)) "code=$($deptCov.code)"
Check 'R16 out-of-scope deptId ignored' (([long]$deptCovOut.data.totalRisks -eq [long]$deptCov.data.totalRisks)) "with=$($deptCovOut.data.totalRisks) without=$($deptCov.data.totalRisks)"
$staffScope = Api 'GET' '/reports/tasks' $null $hStaff
Check 'R16 employee reports/tasks denied' ($staffScope.code -eq 40300) "code=$($staffScope.code)"

# ---------- R17 empty data no 500 ----------
$emptyRect = Api 'GET' '/reports/rectify-rate?year=2000' $null $hAdmin
Check 'R17 empty rectify-rate year' (($emptyRect.code -eq 0) -and ([long]$emptyRect.data.total -eq 0) -and ((Round2 $emptyRect.data.rate) -eq 0)) "code=$($emptyRect.code) total=$($emptyRect.data.total)"
$emptyDur = Api 'GET' '/reports/process-duration?processKey=seal_apply&months=1' $null $hAdmin
Check 'R17 empty process-duration month' (($emptyDur.code -eq 0) -and (@($emptyDur.data.items).Count -eq 1)) "code=$($emptyDur.code) items=$(@($emptyDur.data.items).Count)"
$emptyDef = Api 'GET' '/reports/defects?severity=3&status=3' $null $hAdmin
Check 'R17 empty defects filter' ($emptyDef.code -eq 0) "code=$($emptyDef.code)"

# ---------- R18 summary 60s cache ----------
$cachePrimed = Api 'GET' '/dashboard/summary' $null $hAdmin
$overdueProbe = Api 'POST' '/tasks' @{ taskTitle = 'M2e cache probe'; taskType = 2; priority = 1; assigneeId = 5; deptId = 4; planEnd = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd'); description = 'cache probe' } $hIcp
$overdueProbeId = [long]$overdueProbe.data.id
$cacheSecond = Api 'GET' '/dashboard/summary' $null $hAdmin
Check 'R18 summary 60s cache hit' (([long]$cacheSecond.data.tasks.overdue -eq [long]$cachePrimed.data.tasks.overdue)) "primed=$($cachePrimed.data.tasks.overdue) second=$($cacheSecond.data.tasks.overdue)"

# ---------- R19 oper log traceId ----------
$operLogs = Api 'GET' '/logs/oper?pageSize=20' $null $hAdmin
$traceOk = $false
if ($operLogs.code -eq 0 -and @($operLogs.data.list).Count -ge 1) {
    $traceOk = ([string]($operLogs.data.list[0]).traceId).Length -ge 8
}
Check 'R19 oper log query and traceId' (($operLogs.code -eq 0) -and ([long]$operLogs.data.total -ge 1) -and $traceOk) "code=$($operLogs.code) traceOk=$traceOk"

# ---------- R21 seed unchanged ----------
$menuCount = SqlLong 'SELECT COUNT(*) FROM sys_menu WHERE deleted=0'
$roleMenuCount = SqlLong 'SELECT COUNT(*) FROM sys_role_menu'
$configCount = SqlLong 'SELECT COUNT(*) FROM sys_config WHERE deleted=0'
if ($RunRegression) {
    Check 'R21 seed counts unchanged (skipped in -RunRegression mode)' $true "menu=$menuCount roleMenu=$roleMenuCount config=$configCount"
} else {
    Check 'R21 seed counts unchanged' (($menuCount -eq 184) -and ($roleMenuCount -eq 558) -and ($configCount -eq 16)) "menu=$menuCount roleMenu=$roleMenuCount config=$configCount"
}
$summaryAfter = Api 'GET' '/dashboard/summary' $null $hAdmin
Check 'R21 reports still reachable' (($summaryAfter.code -eq 0) -and ([long]$summaryAfter.data.policies.total -ge 1)) "code=$($summaryAfter.code)"

# ---------- cleanup probes + restore seed-like data ----------
SqlOne "UPDATE icp_policy SET expire_date='$oldExpire' WHERE id=$policyId" | Out-Null
SqlOne "UPDATE wf_business_link SET start_time='$linkOldStart' WHERE id=$linkId" | Out-Null
$todo = Api 'GET' '/workflow/todo?pageSize=200' $null $hIcp
if ($todo.code -eq 0) {
    foreach ($row in @($todo.data.list)) {
        if ($row.processInstanceId -eq $pi) { Api 'POST' "/workflow/tasks/$($row.taskId)/approve" @{ comment = 'm2e probe approve' } $hIcp | Out-Null }
    }
}
$todo2 = Api 'GET' '/workflow/todo?pageSize=200' $null $hDept
if ($todo2.code -eq 0) {
    foreach ($row in @($todo2.data.list)) {
        if ($row.processInstanceId -eq $pi) { Api 'POST' "/workflow/tasks/$($row.taskId)/approve" @{ comment = 'm2e probe approve' } $hDept | Out-Null }
    }
}
if ($remindTaskId -gt 0) { Api 'DELETE' "/tasks/$remindTaskId" $null $hIcp | Out-Null }
if ($overdueProbeId -gt 0) { Api 'DELETE' "/tasks/$overdueProbeId" $null $hIcp | Out-Null }
# remove the alert-probe purchase business rows and the reminder/alert messages created by our job runs
$purchaseBizId = [long]$purchase.data.businessId
if ($purchaseBizId -gt 0) {
    SqlOne "DELETE FROM wf_purchase WHERE id=$purchaseBizId" | Out-Null
}
if ($null -ne $pi -and "$pi" -ne '') {
    SqlOne "DELETE FROM wf_task_urge WHERE task_id IN (SELECT id FROM act_ru_task WHERE proc_inst_id_='$pi')" | Out-Null
    SqlOne "DELETE FROM wf_business_link WHERE process_instance_id='$pi'" | Out-Null
}
# remove all runtime messages produced today (job reminders/alerts + probes); seed messages carry old create_time
SqlOne "DELETE FROM coll_message WHERE create_time >= CURDATE()" | Out-Null
$menuAfter = SqlLong 'SELECT COUNT(*) FROM sys_menu WHERE deleted=0'
$policyAfter = SqlLong 'SELECT COUNT(*) FROM icp_policy WHERE deleted=0'
$riskAfter = SqlLong 'SELECT COUNT(*) FROM icp_risk WHERE deleted=0'
$defectAfter = SqlLong 'SELECT COUNT(*) FROM icp_defect WHERE deleted=0'
$taskAfter = SqlLong 'SELECT COUNT(*) FROM coll_task WHERE deleted=0'
# the probe tasks above were cached in /dashboard/summary for 60s; wait it out and re-prime with the cleaned data
Start-Sleep -Seconds 61
Api 'GET' '/dashboard/summary' $null $hAdmin | Out-Null
Write-Host "CLEAN menu=$menuAfter policy=$policyAfter risk=$riskAfter defect=$defectAfter task=$taskAfter"

Write-Host ''
Write-Host "PASS=$($script:pass) FAIL=$($script:fail)"
if ($script:fail -gt 0) { exit 1 }
exit 0
