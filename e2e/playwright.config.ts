import { defineConfig } from '@playwright/test';

/**
 * M3 E2E config.
 * - baseURL: 默认前端预览 4173，可用 E2E_BASE_URL 覆盖（如 http://127.0.0.1:5173）
 * - 串行执行（workers=1），不做重试；失败保留 trace + 截图
 */
const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:4173';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  outputDir: './test-results',
  use: {
    baseURL,
    browserName: 'chromium',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
});
