import { defineConfig } from '@playwright/test';
import { verifyTarget } from './scripts/disposable.cjs';

// Protect direct Playwright invocation too, before any test file is imported.
const baseURL = verifyTarget(process.env.FISHBOOK_E2E_DISPOSABLE_PROJECT);

export default defineConfig({
  retries: 0,
  workers: 1,
  testDir: './tests',
  use: {
    baseURL,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
});
