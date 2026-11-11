import { defineConfig } from '@playwright/test';

// Failure accessibility snapshots can include input values, including passwords/OTP.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: { timeout: 15_000 },
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: process.env.WEB_AUTH_BASE_URL || 'http://127.0.0.1:5173',
    browserName: 'chromium',
    launchOptions: process.env.WEB_AUTH_CHROME ? { executablePath: process.env.WEB_AUTH_CHROME } : { channel: 'chrome' },
    headless: true,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
});
