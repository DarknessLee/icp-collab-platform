import { spawnSync } from 'node:child_process';

// 在演示态下生成 S01–S28 截图与 docs/screenshots/INDEX.md
const result = spawnSync(
  process.platform === 'win32' ? 'npx.cmd' : 'npx',
  ['playwright', 'test', 'tests/screenshots.spec.ts'],
  {
    stdio: 'inherit',
    env: { ...process.env, E2E_DEMO: '1' },
  },
);
process.exit(result.status ?? 1);
