import { defineConfig } from '@playwright/test';

export default defineConfig({
  retries: 1,
  testDir: './tests',
  use: {
    baseURL: 'http://localhost:8080',
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
});
