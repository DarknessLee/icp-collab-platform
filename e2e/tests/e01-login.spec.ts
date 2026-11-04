import { test, expect } from '@playwright/test';
import { uiLogin, watchPage, expectNoIssues } from './helpers';

test.describe('E01 登录', () => {
  test('E01 错误密码提示「用户名或密码错误」；admin 登录成功跳工作台', async ({ page }) => {
    const issues = watchPage(page);
    await page.goto('/login');
    await expect(page.locator('h2')).toContainText('账号登录');

    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('Wrong@2026');
    await page.locator('input[placeholder="请输入密码"]').press('Enter');

    const errorToast = page.locator('.el-message').filter({ hasText: '用户名或密码错误' }).first();
    await expect(errorToast).toBeVisible({ timeout: 15_000 });
    await expect(page).toHaveURL(/\/login/);

    await uiLogin(page, 'admin');
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('.dash-hero__title')).toContainText('系统管理员');
    await expect(page.locator('.dash-card')).toHaveCount(8);

    expectNoIssues(issues, 'E01');
  });
});
