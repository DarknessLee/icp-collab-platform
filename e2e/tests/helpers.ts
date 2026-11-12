import { expect, type Page } from '@playwright/test';

/** 后端 API 基地址（与前端 baseURL 无关，E2E 可直接调后端做同源断言） */
export const API_BASE = process.env.E2E_API_BASE || 'http://127.0.0.1:8080/api/v1';
export const PASSWORD = process.env.E2E_PASSWORD || 'Icp@2026';

export interface MenuNode {
  name?: string;
  path?: string;
  children?: MenuNode[];
  [key: string]: unknown;
}

export interface AuthSession {
  token: string;
  userId: number;
  username: string;
  realName: string;
  roles: string[];
  permissions: string[];
  menus: MenuNode[];
  raw: Record<string, unknown>;
}

export interface ApiBody<T = unknown> {
  code: number;
  message?: string;
  data: T;
  traceId?: string;
}

export interface PageIssues {
  console: string[];
  pageErrors: string[];
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** 等待后端可登录（最多 60s），用于脚本/初始数据态刚启动时的抖动 */
export async function waitBackend(timeoutMs = 60_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  let lastError = '';
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: PASSWORD }),
      });
      const body = (await res.json()) as ApiBody;
      if (res.ok && body.code === 0) return;
      lastError = `code=${body.code} message=${body.message ?? ''}`;
    } catch (error) {
      lastError = (error as Error).message;
    }
    await sleep(1000);
  }
  throw new Error(`后端不可用（${API_BASE}）：${lastError}`);
}

/** API 登录，返回 token/权限/菜单，供页面断言与数字同源对比 */
export async function apiLogin(username: string, password = PASSWORD): Promise<AuthSession> {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const body = (await res.json()) as ApiBody<{
    accessToken: string;
    user?: { id?: number; username?: string; realName?: string };
    roles?: string[];
    permissions?: string[];
    menus?: MenuNode[];
  }>;
  if (body.code !== 0 || !body.data?.accessToken) {
    throw new Error(`登录失败 ${username}: code=${body.code} message=${body.message}`);
  }
  const user = body.data.user ?? {};
  return {
    token: body.data.accessToken,
    userId: Number(user.id ?? 0),
    username: String(user.username ?? username),
    realName: String(user.realName ?? username),
    roles: body.data.roles ?? [],
    permissions: body.data.permissions ?? [],
    menus: body.data.menus ?? [],
    raw: body.data as unknown as Record<string, unknown>,
  };
}

async function parseBody<T>(res: Response, path: string): Promise<ApiBody<T>> {
  const text = await res.text();
  let body: ApiBody<T>;
  try {
    body = JSON.parse(text) as ApiBody<T>;
  } catch {
    throw new Error(`${path} 返回非 JSON（HTTP ${res.status}）：${text.slice(0, 200)}`);
  }
  return body;
}

/** GET，返回 data；code != 0 时抛错（含 message 便于定位） */
export async function apiGet<T = unknown>(path: string, token: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await parseBody<T>(res, path);
  if (body.code !== 0) {
    throw new Error(`GET ${path} 失败：HTTP ${res.status} code=${body.code} message=${body.message}`);
  }
  return body.data;
}

/** 直接拿 HTTP 状态 + code，用于断言 403/400 等预期失败 */
export async function apiRaw(
  path: string,
  token: string,
): Promise<{ status: number; code: number; message?: string; data?: unknown }> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await parseBody<T>(res, path);
  return { status: res.status, code: body.code, message: body.message, data: body.data };
}

export async function apiPost<T = unknown>(
  path: string,
  token: string,
  payload?: unknown,
): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  const body = await parseBody<T>(res, path);
  if (body.code !== 0) {
    throw new Error(`POST ${path} 失败：HTTP ${res.status} code=${body.code} message=${body.message}`);
  }
  return body.data;
}

export async function apiPut<T = unknown>(
  path: string,
  token: string,
  payload?: unknown,
): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  const body = await parseBody<T>(res, path);
  if (body.code !== 0) {
    throw new Error(`PUT ${path} 失败：HTTP ${res.status} code=${body.code} message=${body.message}`);
  }
  return body.data;
}

export async function apiDelete<T = unknown>(path: string, token: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await parseBody<T>(res, path);
  if (body.code !== 0) {
    throw new Error(`DELETE ${path} 失败：HTTP ${res.status} code=${body.code} message=${body.message}`);
  }
  return body.data;
}

/** 收集页面 console error / pageerror，过滤已知无害噪声（在断言里仍校验业务接口） */
export function watchPage(page: Page): PageIssues {
  const issues: PageIssues = { console: [], pageErrors: [] };
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    if (isBenignConsole(msg.text())) return;
    issues.console.push(msg.text());
  });
  page.on('pageerror', (error) => {
    issues.pageErrors.push(String(error));
  });
  return issues;
}

function isBenignConsole(text: string): boolean {
  return (
    /favicon\.ico/i.test(text) ||
    /net::ERR_ABORTED/i.test(text) ||
    /Failed to load resource: the server responded with a status of 40[13]/i.test(text) ||
    /messages\/stream/i.test(text)
  );
}

export function expectNoIssues(issues: PageIssues, label = ''): void {
  expect(issues.console, `${label} 非预期 console error：${issues.console.join(' | ')}`).toEqual([]);
  expect(issues.pageErrors, `${label} pageerror：${issues.pageErrors.join(' | ')}`).toEqual([]);
}

/** UI 登录（每个 test 使用独立 context，这里额外清理本地存储避免角色串用） */
export async function uiLogin(page: Page, username: string, password = PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.goto('/login');
  await page.locator('input[placeholder="请输入用户名"]').fill(username);
  await page.locator('input[placeholder="请输入密码"]').fill(password);
  await page.locator('input[placeholder="请输入密码"]').press('Enter');
  await page.waitForURL(/\/dashboard/, { timeout: 25_000 });
  await expect(page.locator('.dash-card')).toHaveCount(8);
}

/** 把菜单树（含 children）展开为菜单 path 列表，例如 ["system/log"] */
export function menuPaths(menus: MenuNode[], out: string[] = []): string[] {
  for (const item of menus) {
    if (typeof item.path === 'string' && item.path) out.push(item.path);
    if (item.children?.length) menuPaths(item.children, out);
  }
  return out;
}

/** 读取 el-pagination 的总数文案「共 6 条」→ 6 */
export async function paginationTotal(page: Page): Promise<number> {
  const totalLocator = page.locator('.el-pagination__total').first();
  await totalLocator.waitFor({ state: 'visible', timeout: 15_000 });
  const text = (await totalLocator.innerText()).replace(/\s/g, '');
  const match = text.match(/共(\d+)条/);
  if (!match) throw new Error(`无法解析分页总数：${text}`);
  return Number(match[1]);
}

/** 归一化 rate：接口可能返回 0-1 小数，也可能返回 0-100 百分数 */
export function normalizeRate(value: number | undefined | null): number {
  const n = Number(value ?? 0);
  if (!Number.isFinite(n)) return 0;
  return n > 1 ? n / 100 : n;
}

/** 页面百分比展示口径：1 位小数，如 100.0% */
export function percentText(value: number | undefined | null): string {
  return `${(normalizeRate(value) * 100).toFixed(1)}%`;
}

/** 唯一后缀，保证数据相关用例互不干扰 */
export function uniqueSuffix(): string {
  return `E2E${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

/** 等待 ElMessage 出现并包含指定文案 */
export async function waitToast(page: Page, text: string, timeout = 15_000): Promise<void> {
  await expect(page.locator('.el-message').filter({ hasText: text }).first()).toBeVisible({
    timeout,
  });
}

/** 带幂等/错误码断言用的 POST（不抛业务错误，返回 HTTP+code+message） */
export async function apiPostRaw(
  path: string,
  token: string,
  payload?: unknown,
): Promise<{ status: number; code: number; message?: string; data?: unknown }> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  const body = await parseBody<T>(res, path);
  return { status: res.status, code: body.code, message: body.message, data: body.data };
}

export interface FileRef {
  id: number;
  originalName: string;
}

/** 上传本地文件到 /files（multipart），返回文件 id 供文档/整改证据等引用 */
export async function apiUpload(filePath: string, token: string): Promise<FileRef> {
  const { readFile } = await import('node:fs/promises');
  const buffer = await readFile(filePath);
  const form = new FormData();
  form.append(
    'file',
    new Blob([new Uint8Array(buffer)], { type: 'application/pdf' }),
    'e2e-evidence.pdf',
  );
  const res = await fetch(`${API_BASE}/files`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  const body = (await res.json()) as ApiBody<Record<string, unknown>>;
  if (body.code !== 0) {
    throw new Error(`上传文件失败：HTTP ${res.status} code=${body.code} message=${body.message}`);
  }
  return {
    id: Number(body.data.id),
    originalName: String(body.data.originalName ?? body.data.fileName ?? ''),
  };
}

export interface WorkflowInstanceRow {
  processInstanceId: string;
  businessNo?: string;
  title?: string;
  status: string | number;
  processKey?: string;
}

/** 拉取实例列表（最多 200 条），按标题/业务单号/流程 key 匹配 */
export async function findInstance(
  token: string,
  match: { title?: string; businessNo?: string; processKey?: string; processInstanceId?: string },
): Promise<WorkflowInstanceRow> {
  const load = async (path: string): Promise<WorkflowInstanceRow[]> => {
    const data = await apiGet<{ list?: WorkflowInstanceRow[]; records?: WorkflowInstanceRow[] }>(path, token);
    return data.list ?? data.records ?? (Array.isArray(data) ? (data as unknown as WorkflowInstanceRow[]) : []);
  };
  // 监控列表需要 workflow:monitor:view；员工只有 initiated，逐级降级
  let list: WorkflowInstanceRow[] = [];
  for (const path of [
    '/workflow/monitor?pageNum=1&pageSize=200',
    '/workflow/initiated?pageNum=1&pageSize=200',
    '/workflow/todo?pageNum=1&pageSize=200',
  ]) {
    try {
      list = await load(path);
      if (list.length) break;
    } catch {
      // 无权限则降级到下一种列表
    }
  }
  const hit = list.find((row) => {
    if (match.processInstanceId) return String(row.processInstanceId) === match.processInstanceId;
    if (match.businessNo && row.businessNo && row.businessNo !== match.businessNo) return false;
    if (match.title && row.title && !row.title.includes(match.title)) return false;
    if (match.processKey && row.processKey && row.processKey !== match.processKey) return false;
    return Boolean(match.title || match.businessNo || match.processKey);
  });
  if (!hit) throw new Error(`未找到流程实例：${JSON.stringify(match)}`);
  return hit;
}

export interface WorkflowInstanceDetail {
  processInstanceId: string;
  businessNo?: string;
  title?: string;
  status: string | number;
  statusLabel?: string;
  processKey?: string;
  formData?: Record<string, unknown>;
  business?: Record<string, unknown>;
}

export async function instanceDetail(token: string, instanceId: string): Promise<WorkflowInstanceDetail> {
  return apiGet<WorkflowInstanceDetail>(`/workflow/instances/${instanceId}`, token);
}

/** 实例状态归一化：RUNNING/COMPLETED/REJECTED（兼容数字） */
export function instanceStatus(value: string | number | undefined): string {
  if (typeof value === 'number' || /^\d+$/.test(String(value ?? ''))) {
    return { 0: 'RUNNING', 1: 'COMPLETED', 2: 'REJECTED' }[Number(value)] ?? String(value);
  }
  return String(value ?? '');
}

/** 轮询实例状态直到期望值（失败返回最后一次状态） */
export async function waitInstanceStatus(
  token: string,
  instanceId: string,
  expected: 'RUNNING' | 'COMPLETED' | 'REJECTED',
  timeoutMs = 20_000,
): Promise<string> {
  const deadline = Date.now() + timeoutMs;
  let last = '';
  while (Date.now() < deadline) {
    const detail = await instanceDetail(token, instanceId);
    last = instanceStatus(detail.status);
    if (last === expected) return last;
    await sleep(800);
  }
  return last;
}

export interface TodoRow {
  taskId: string;
  processInstanceId: string;
  processKey?: string;
  title?: string;
  businessNo?: string;
  nodeName?: string;
  activityId?: string;
}

/** 按流程实例/标题找当前待办 */
export async function findTodo(
  token: string,
  match: { processInstanceId?: string; title?: string },
): Promise<TodoRow> {
  const data = await apiGet<{ list?: TodoRow[] }>('/workflow/todo?pageNum=1&pageSize=200', token);
  const list = data.list ?? [];
  const hit = list.find((row) => {
    if (match.processInstanceId) return String(row.processInstanceId) === match.processInstanceId;
    if (match.title && row.title && !row.title.includes(match.title)) return false;
    return Boolean(match.title);
  });
  if (!hit) throw new Error(`未找到待办：${JSON.stringify(match)}`);
  return hit;
}

export async function approveTask(token: string, taskId: string, comment = 'E2E 审批通过'): Promise<void> {
  await apiPost(`/workflow/tasks/${taskId}/approve`, token, { comment });
}

export async function rejectTask(token: string, taskId: string, comment: string): Promise<void> {
  await apiPost(`/workflow/tasks/${taskId}/reject`, token, { comment });
}

export async function urgeTask(token: string, taskId: string, content = 'E2E 催办'): Promise<void> {
  await apiPost(`/workflow/tasks/${taskId}/urge`, token, { content });
}

/**
 * 依次用候选人审批当前待办，直到实例结束或候选人耗尽。
 * candidates 应覆盖完整审批链（含发起节点）。
 */
export async function approveChain(
  instanceId: string,
  candidates: Array<{ name: string; token: string }>,
  maxRounds = 8,
): Promise<string> {
  let status = 'RUNNING';
  for (let round = 0; round < maxRounds; round += 1) {
    const pending = await Promise.all(
      candidates.map(async (candidate) => {
        try {
          return await findTodo(candidate.token, { processInstanceId: instanceId });
        } catch {
          return null;
        }
      }),
    );
    const todoIndex = pending.findIndex(Boolean);
    if (todoIndex < 0) {
      const any = candidates[0];
      const detail = await instanceDetail(any.token, instanceId);
      return instanceStatus(detail.status);
    }
    await approveTask(candidates[todoIndex].token, pending[todoIndex]!.taskId);
    await sleep(500);
    const detail = await instanceDetail(candidates[0].token, instanceId);
    status = instanceStatus(detail.status);
    if (status !== 'RUNNING') return status;
  }
  return status;
}
