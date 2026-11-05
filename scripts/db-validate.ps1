# db-validate.ps1 - M1 data quality gate for the ICP platform (docs/04 section 7)
# Runs V01..V15 checks against the local MySQL 3307 instance.
# Each SQL returns a single violation count; 0 means PASS.
# Exit code 0 = all checks pass, 1 = at least one check failed or cannot run.
$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '_env.ps1')

$mysql = Join-Path $MySQLBin 'mysql.exe'
if (-not (Test-Path $mysql)) { throw "mysql client not found: $mysql" }

function Invoke-Count([string]$sql) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $out = & $mysql "--host=$MySQLHost" "--port=$MySQLPort" '--protocol=TCP' "--user=$DbUser" "--password=$DbPassword" '-N' '-B' $DbName '-e' $sql 2>$null
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prev
    if ($code -ne 0) { return $null }
    $first = ($out | Where-Object { $_ -ne '' } | Select-Object -First 1)
    if ($null -eq $first) { return $null }
    return [long]$first.Trim()
}

# ---- violation queries (docs/04 section 7) ----
$checks = @(
  @{ Id = 'V01'; Desc = 'foreign key orphans'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id=d.id AND d.deleted=0 WHERE u.deleted=0 AND d.id IS NULL)
+(SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_user u ON ur.user_id=u.id AND u.deleted=0 LEFT JOIN sys_role r ON ur.role_id=r.id AND r.deleted=0 WHERE u.id IS NULL OR r.id IS NULL)
+(SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_role r ON rm.role_id=r.id AND r.deleted=0 LEFT JOIN sys_menu m ON rm.menu_id=m.id AND m.deleted=0 WHERE r.id IS NULL OR m.id IS NULL)
+(SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_menu p ON m.parent_id=p.id AND p.deleted=0 WHERE m.deleted=0 AND m.parent_id<>0 AND p.id IS NULL)
+(SELECT COUNT(*) FROM icp_policy p LEFT JOIN icp_policy_category c ON p.category_id=c.id AND c.deleted=0 WHERE p.deleted=0 AND c.id IS NULL)
+(SELECT COUNT(*) FROM icp_risk r LEFT JOIN icp_risk_category c ON r.category_id=c.id AND c.deleted=0 WHERE r.deleted=0 AND c.id IS NULL)
+(SELECT COUNT(*) FROM icp_matrix mx LEFT JOIN icp_risk r ON mx.risk_id=r.id AND r.deleted=0 LEFT JOIN icp_control c ON mx.control_id=c.id AND c.deleted=0 LEFT JOIN icp_policy p ON mx.policy_id=p.id AND p.deleted=0 WHERE mx.deleted=0 AND (r.id IS NULL OR c.id IS NULL OR p.id IS NULL))
+(SELECT COUNT(*) FROM icp_evaluation_task t LEFT JOIN icp_evaluation p ON t.plan_id=p.id AND p.deleted=0 WHERE t.deleted=0 AND p.id IS NULL)
+(SELECT COUNT(*) FROM icp_evaluation_workpaper w LEFT JOIN icp_evaluation_task t ON w.task_id=t.id AND t.deleted=0 WHERE w.deleted=0 AND t.id IS NULL)
+(SELECT COUNT(*) FROM icp_rectify r LEFT JOIN icp_defect d ON r.defect_id=d.id AND d.deleted=0 WHERE r.deleted=0 AND d.id IS NULL)
+(SELECT COUNT(*) FROM coll_task t LEFT JOIN coll_task p ON t.parent_id=p.id AND p.deleted=0 WHERE t.deleted=0 AND t.parent_id<>0 AND p.id IS NULL)
+(SELECT COUNT(*) FROM coll_task_comment c LEFT JOIN coll_task t ON c.task_id=t.id AND t.deleted=0 WHERE c.deleted=0 AND t.id IS NULL)
+(SELECT COUNT(*) FROM coll_doc d LEFT JOIN coll_doc_category c ON d.category_id=c.id AND c.deleted=0 WHERE d.deleted=0 AND c.id IS NULL)
+(SELECT COUNT(*) FROM coll_doc_perm p LEFT JOIN coll_doc d ON p.doc_id=d.id AND d.deleted=0 WHERE p.deleted=0 AND d.id IS NULL)
+(SELECT COUNT(*) FROM coll_notice_read nr LEFT JOIN coll_notice n ON nr.notice_id=n.id AND n.deleted=0 LEFT JOIN sys_user u ON nr.user_id=u.id AND u.deleted=0 WHERE n.id IS NULL OR u.id IS NULL)
+(SELECT COUNT(*) FROM coll_schedule s LEFT JOIN sys_user u ON s.creator_id=u.id AND u.deleted=0 WHERE s.deleted=0 AND u.id IS NULL)
+(SELECT COUNT(*) FROM coll_message m LEFT JOIN sys_user u ON m.receiver_id=u.id AND u.deleted=0 WHERE m.deleted=0 AND u.id IS NULL)
AS violations;
'@ }
  @{ Id = 'V02'; Desc = 'department tree'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM sys_dept d LEFT JOIN sys_dept p ON d.parent_id=p.id AND p.deleted=0 WHERE d.deleted=0 AND d.parent_id<>0 AND p.id IS NULL)
+(SELECT ABS(COUNT(*)-1) FROM sys_dept WHERE deleted=0 AND parent_id=0)
+(SELECT COUNT(*) FROM sys_dept d WHERE d.deleted=0 AND (d.leader_user_id IS NULL OR NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id=d.leader_user_id AND u.deleted=0)))
AS violations;
'@ }
  @{ Id = 'V03'; Desc = 'users / dept / unique / status / role coverage'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id=d.id AND d.deleted=0 WHERE u.deleted=0 AND d.id IS NULL)
+(SELECT COUNT(*) FROM (SELECT username FROM sys_user WHERE deleted=0 GROUP BY username HAVING COUNT(*)>1) x)
+(SELECT COUNT(*) FROM sys_user WHERE deleted=0 AND status NOT IN (0,1,2))
+(SELECT COUNT(*) FROM sys_role r WHERE r.deleted=0 AND NOT EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_user u ON ur.user_id=u.id AND u.deleted=0 WHERE ur.role_id=r.id))
AS violations;
'@ }
  @{ Id = 'V04'; Desc = 'menus and permissions'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM sys_role r WHERE r.deleted=0 AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id))
+(SELECT COUNT(*) FROM (SELECT perms FROM sys_menu WHERE deleted=0 AND perms IS NOT NULL AND perms<>'' GROUP BY perms HAVING COUNT(*)>1) x)
+(SELECT COUNT(*) FROM sys_menu WHERE deleted=0 AND perms='')
+(SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_menu p ON m.parent_id=p.id AND p.deleted=0 WHERE m.deleted=0 AND m.menu_type='F' AND (p.id IS NULL OR p.menu_type<>'C'))
AS violations;
'@ }
  @{ Id = 'V05'; Desc = 'policy dates / publish / code'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM icp_policy WHERE deleted=0 AND effective_date IS NOT NULL AND expire_date IS NOT NULL AND effective_date>expire_date)
+(SELECT COUNT(*) FROM icp_policy WHERE deleted=0 AND status=2 AND (publish_time IS NULL OR effective_date IS NULL))
+(SELECT COUNT(*) FROM icp_policy WHERE deleted=0 AND policy_code NOT REGEXP '^ZD-[0-9]{4}-[0-9]{4}$')
+(SELECT COUNT(*) FROM (SELECT policy_code FROM icp_policy WHERE deleted=0 GROUP BY policy_code HAVING COUNT(*)>1) x)
AS violations;
'@ }
  @{ Id = 'V06'; Desc = 'risk score = probability x impact / level'; Sql = @'
SELECT COUNT(*) AS violations FROM icp_risk
WHERE deleted=0 AND (probability<1 OR probability>5 OR impact<1 OR impact>5
 OR risk_score<>probability*impact OR risk_score<1 OR risk_score>25
 OR risk_level<>CASE WHEN risk_score>=15 THEN 1 WHEN risk_score>=8 THEN 2 ELSE 3 END);
'@ }
  @{ Id = 'V07'; Desc = 'matrix unique pairs / risk coverage'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM (SELECT risk_id,control_id FROM icp_matrix WHERE deleted=0 GROUP BY risk_id,control_id HAVING COUNT(*)>1) x)
+(SELECT COUNT(*) FROM icp_risk r WHERE r.deleted=0 AND NOT EXISTS (SELECT 1 FROM icp_matrix m WHERE m.risk_id=r.id AND m.deleted=0))
AS violations;
'@ }
  @{ Id = 'V08'; Desc = 'evaluation plan/task consistency'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM icp_evaluation WHERE deleted=0 AND start_date>end_date)
+(SELECT COUNT(*) FROM icp_evaluation_task t JOIN icp_evaluation p ON t.plan_id=p.id WHERE t.deleted=0 AND p.deleted=0 AND (t.start_date<p.start_date OR t.due_date>p.end_date))
+(SELECT COUNT(*) FROM icp_evaluation_task t WHERE t.deleted=0 AND t.status=2 AND (t.score IS NULL OR t.conclusion IS NULL OR NOT EXISTS (SELECT 1 FROM icp_evaluation_workpaper w WHERE w.task_id=t.id AND w.deleted=0)))
AS violations;
'@ }
  @{ Id = 'V09'; Desc = 'defect closure / rectify approval'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM icp_defect d WHERE d.deleted=0 AND d.status=3 AND (d.close_time IS NULL OR NOT EXISTS (SELECT 1 FROM icp_rectify r WHERE r.defect_id=d.id AND r.status=2 AND r.deleted=0)))
+(SELECT COUNT(*) FROM icp_rectify WHERE deleted=0 AND status=2 AND (review_user_id IS NULL OR review_time IS NULL))
AS violations;
'@ }
  @{ Id = 'V10'; Desc = 'collaboration task consistency'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM coll_task WHERE deleted=0 AND status=2 AND (actual_end IS NULL OR progress<>100))
+(SELECT COUNT(*) FROM coll_task WHERE deleted=0 AND plan_start>plan_end)
+(SELECT COUNT(*) FROM coll_task t WHERE t.deleted=0 AND t.parent_id<>0 AND NOT EXISTS (SELECT 1 FROM coll_task p WHERE p.id=t.parent_id AND p.deleted=0 AND p.task_type=2))
AS violations;
'@ }
  @{ Id = 'V11'; Desc = 'purchase amount within budget'; Sql = @'
SELECT COUNT(*) AS violations FROM wf_purchase WHERE deleted=0 AND (amount<=0 OR budget_amount<=0 OR amount>budget_amount);
'@ }
  @{ Id = 'V12'; Desc = 'file / evidence references'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM coll_doc d WHERE d.deleted=0 AND (d.file_id IS NULL OR NOT EXISTS (SELECT 1 FROM sys_file f WHERE f.id=d.file_id AND f.deleted=0)))
+(SELECT COUNT(*) FROM wf_seal s WHERE s.deleted=0 AND (s.file_id IS NULL OR NOT EXISTS (SELECT 1 FROM sys_file f WHERE f.id=s.file_id AND f.deleted=0)))
+(SELECT COUNT(*) FROM icp_rectify r WHERE r.deleted=0 AND r.evidence IS NOT NULL
   AND (SELECT COUNT(*) FROM sys_file f WHERE f.deleted=0 AND FIND_IN_SET(f.id, r.evidence))
     <> (LENGTH(r.evidence)-LENGTH(REPLACE(r.evidence,',',''))+1))
+(SELECT COUNT(*) FROM icp_evaluation_workpaper w WHERE w.deleted=0 AND w.evidence IS NOT NULL
   AND (SELECT COUNT(*) FROM sys_file f WHERE f.deleted=0 AND FIND_IN_SET(f.id, w.evidence))
     <> (LENGTH(w.evidence)-LENGTH(REPLACE(w.evidence,',',''))+1))
AS violations;
'@ }
  @{ Id = 'V13'; Desc = 'notice publish / read users'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM coll_notice WHERE deleted=0 AND status=1 AND publish_time IS NULL)
+(SELECT COUNT(*) FROM coll_notice_read nr LEFT JOIN sys_user u ON nr.user_id=u.id AND u.deleted=0 WHERE u.id IS NULL)
+(SELECT COUNT(*) FROM coll_notice_read nr LEFT JOIN coll_notice n ON nr.notice_id=n.id AND n.deleted=0 WHERE n.id IS NULL)
AS violations;
'@ }
  @{ Id = 'V14'; Desc = 'business code uniqueness'; Sql = @'
SELECT
 (SELECT COUNT(*) FROM (SELECT policy_code c FROM icp_policy WHERE deleted=0 GROUP BY policy_code HAVING COUNT(*)>1) a)
+(SELECT COUNT(*) FROM (SELECT risk_code c FROM icp_risk WHERE deleted=0 GROUP BY risk_code HAVING COUNT(*)>1) b)
+(SELECT COUNT(*) FROM (SELECT control_code c FROM icp_control WHERE deleted=0 GROUP BY control_code HAVING COUNT(*)>1) c1)
+(SELECT COUNT(*) FROM (SELECT matrix_code c FROM icp_matrix WHERE deleted=0 GROUP BY matrix_code HAVING COUNT(*)>1) d)
+(SELECT COUNT(*) FROM (SELECT plan_code c FROM icp_evaluation WHERE deleted=0 GROUP BY plan_code HAVING COUNT(*)>1) e)
+(SELECT COUNT(*) FROM (SELECT task_code c FROM icp_evaluation_task WHERE deleted=0 GROUP BY task_code HAVING COUNT(*)>1) f)
+(SELECT COUNT(*) FROM (SELECT defect_code c FROM icp_defect WHERE deleted=0 GROUP BY defect_code HAVING COUNT(*)>1) g)
+(SELECT COUNT(*) FROM (SELECT rectify_code c FROM icp_rectify WHERE deleted=0 GROUP BY rectify_code HAVING COUNT(*)>1) h)
+(SELECT COUNT(*) FROM (SELECT apply_no c FROM wf_purchase WHERE deleted=0 GROUP BY apply_no HAVING COUNT(*)>1) i1)
+(SELECT COUNT(*) FROM (SELECT apply_no c FROM wf_seal WHERE deleted=0 GROUP BY apply_no HAVING COUNT(*)>1) j)
+(SELECT COUNT(*) FROM (SELECT task_no c FROM coll_task WHERE deleted=0 GROUP BY task_no HAVING COUNT(*)>1) k)
+(SELECT COUNT(*) FROM (SELECT doc_no c FROM coll_doc WHERE deleted=0 GROUP BY doc_no HAVING COUNT(*)>1) l)
AS violations;
'@ }
  @{ Id = 'V15'; Desc = 'seed row counts (docs/04 6.3; after V4 meeting: menu 184 / role_menu 558 / dict_type 21 / dict_data 88 / user_role 30)'; Sql = @'
SELECT
 ABS((SELECT COUNT(*) FROM sys_dept)-8)
+ABS((SELECT COUNT(*) FROM sys_post)-6)
+ABS((SELECT COUNT(*) FROM sys_user)-30)
+ABS((SELECT COUNT(*) FROM sys_role)-6)
+ABS((SELECT COUNT(*) FROM sys_menu)-184)
+ABS((SELECT COUNT(*) FROM sys_dict_type)-21)
+ABS((SELECT COUNT(*) FROM sys_config)-16)
+ABS((SELECT COUNT(*) FROM sys_job)-5)
+GREATEST(0, 30-(SELECT COUNT(*) FROM sys_file))
+GREATEST(0, 200-(SELECT COUNT(*) FROM sys_oper_log))
+GREATEST(0, 100-(SELECT COUNT(*) FROM sys_login_log))
+ABS((SELECT COUNT(*) FROM icp_policy_category)-12)
+ABS((SELECT COUNT(*) FROM icp_policy)-40)
+ABS((SELECT COUNT(*) FROM icp_risk_category)-8)
+ABS((SELECT COUNT(*) FROM icp_risk)-40)
+ABS((SELECT COUNT(*) FROM icp_control)-60)
+ABS((SELECT COUNT(*) FROM icp_matrix)-60)
+ABS((SELECT COUNT(*) FROM icp_evaluation)-4)
+ABS((SELECT COUNT(*) FROM icp_evaluation_task)-20)
+ABS((SELECT COUNT(*) FROM icp_evaluation_workpaper)-20)
+ABS((SELECT COUNT(*) FROM icp_defect)-30)
+ABS((SELECT COUNT(*) FROM icp_rectify)-25)
+ABS((SELECT COUNT(*) FROM wf_form)-6)
+ABS((SELECT COUNT(*) FROM wf_process_meta)-6)
+ABS((SELECT COUNT(*) FROM wf_purchase)-20)
+ABS((SELECT COUNT(*) FROM wf_seal)-12)
+ABS((SELECT COUNT(*) FROM coll_task)-80)
+ABS((SELECT COUNT(*) FROM coll_task_comment)-30)
+ABS((SELECT COUNT(*) FROM coll_doc_category)-6)
+ABS((SELECT COUNT(*) FROM coll_doc)-40)
+ABS((SELECT COUNT(*) FROM coll_doc_perm)-30)
+ABS((SELECT COUNT(*) FROM coll_doc_borrow)-10)
+ABS((SELECT COUNT(*) FROM coll_notice)-12)
+ABS((SELECT COUNT(*) FROM coll_notice_read)-120)
+ABS((SELECT COUNT(*) FROM coll_schedule)-20)
+ABS((SELECT COUNT(*) FROM coll_message)-60)
+ABS((SELECT COUNT(*) FROM coll_meeting)-20)
+ABS((SELECT COUNT(*) FROM sys_user_role)-30)
+ABS((SELECT COUNT(*) FROM sys_dict_data)-88)
+ABS((SELECT COUNT(*) FROM sys_role_menu)-558)
AS violations;
'@ }
)

Write-Step "db-validate: running $($checks.Count) checks against $DbName on $MySQLHost`:$MySQLPort"
$failed = 0
$total = 0
foreach ($c in $checks) {
    $total++
    $v = Invoke-Count $c.Sql
    if ($null -eq $v) {
        Write-Host ("{0} {1,-52} FAIL (query error)" -f $c.Id, $c.Desc)
        $failed++
    } elseif ($v -ne 0) {
        Write-Host ("{0} {1,-52} FAIL (violations={2})" -f $c.Id, $c.Desc, $v)
        $failed++
    } else {
        Write-Host ("{0} {1,-52} PASS" -f $c.Id, $c.Desc)
    }
}

Write-Host ("db-validate: {0} passed, {1} failed of {2}" -f ($total - $failed), $failed, $total)
if ($failed -gt 0) {
    Write-Host 'db-validate: RESULT = FAIL'
    exit 1
}
Write-Host 'db-validate: RESULT = PASS'
exit 0

