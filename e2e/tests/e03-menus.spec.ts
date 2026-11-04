import { test, expect } from '@playwright/test';
import { apiLogin, expectNoIssues, uiLogin, watchPage } from './helpers';

interface RoleCase {
  role: string;
  expectedMenus: number;
  systemChildren?: string[];
  systemExcluded?: string[];
}

const ROLES: RoleCase[] = [
  { role: 'admin', expectedMenus: 6, systemChildren: ['部门管理', '用户管理', '角色管理', '菜单管理', '日志审计', '定时任务'] },
  { role: 'icpadmin', expectedMenus: 5, systemExcluded: ['系统管理'] },
  { role: 'auditor', expectedMenus: 6, systemChildren: ['日志审计'], systemExcluded: ['用户管理', '角色管理', '菜单管理'] },
  { role: 'exec', expectedMenus: 5, systemExcluded: ['系统管理'] },
  { role: 'dept01', expectedMenus: 5, systemExcluded: ['系统管理'] },
  { role: 'staff01', expectedMenus: 4, systemExcluded: ['系统管理', '报表中心'] },
];

for (const { role, expectedMenus, systemChildren, systemExcluded } of ROLES) {
  test(`E03 菜单可见性 ${role}：${expectedMenus} 个顶级目录，与登录返回菜单一致`, async ({ page }) => {
    const issues = watchPage(page);
    const auth = await apiLogin(role);
    expect(auth.menus, `${role} 登录返回顶级目录数`).toHaveLength(expectedMenus);

    await uiLogin(page, role);

    const topItems = page.locator('.sidebar-menu > li');
    await expect(topItems).toHaveCount(expectedMenus);

    const menuText = await page.locator('.sidebar-menu').innerText();
    for (const menu of auth.menus) {
      expect(menuText, `${role} 侧栏应包含顶级目录「${menu.name}」`).toContain(String(menu.name ?? ''));
    }

    // 种子中 visible=0 的遗留菜单不得出现（表单管理 id40 / 催办管理 id47）
    for (const hidden of ['表单管理', '催办管理']) {
      expect(menuText, `${role} 侧栏不应出现隐藏菜单「${hidden}」`).not.toContain(hidden);
    }
    // 侧栏页脚应为与阶段无关的产品标识
    await expect(page.locator('.sidebar-footer')).toContainText('V1.0');

    if (systemExcluded?.length) {
      for (const name of systemExcluded) {
        expect(menuText, `${role} 侧栏不应出现「${name}」`).not.toContain(name);
      }
    }

    if (systemChildren?.length) {
      const system = page.locator('.sidebar-menu .el-sub-menu__title').filter({ hasText: '系统管理' }).first();
      await expect(system).toBeVisible();
      await system.click();
      await page.waitForTimeout(400);
      const expanded = await page.locator('.sidebar-menu').innerText();
      for (const child of systemChildren) {
        expect(expanded, `系统管理应含「${child}」`).toContain(child);
      }
    }

    expectNoIssues(issues, `E03-${role}`);
  });
}
