// Real Swagger UI screenshots of a running LazyREST (localhost:8080).
// Usage: node capture-swagger.mjs <outDir>   (app running on :8080; PLAYWRIGHT_CORE=… if needed)
import { pathToFileURL } from 'node:url';
const { chromium } = await import(process.env.PLAYWRIGHT_CORE ? pathToFileURL(process.env.PLAYWRIGHT_CORE).href : 'playwright-core');
const OUT = process.argv[2];
const killer = setTimeout(() => { console.error('timeout'); process.exit(2); }, 90000);
const browser = await chromium.launch({ channel: 'chrome', timeout: 30000 });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  page.setDefaultTimeout(15000);
  const res = await fetch('http://localhost:8080/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'alice', password: 'alice123' }) });
  const { accessToken } = await res.json();
  await page.goto('http://localhost:8080/swagger-ui.html', { waitUntil: 'networkidle' });
  await page.waitForSelector('.opblock');
  await page.click('button.authorize');
  await page.waitForSelector('.modal-ux');
  await page.fill('.modal-ux input[type=text]', accessToken);
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${OUT}/swagger-authorize.png` });
  await page.click('.modal-ux button.authorize');
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${OUT}/swagger-authorized.png` });
  await page.click('.modal-ux button.close-modal');
  // expand GET /api/articles and execute it for real
  const op = page.locator('.opblock-get', { has: page.locator('[data-path="/api/articles"]') }).first();
  await op.locator('.opblock-summary').click();
  await op.locator('button.try-out__btn').click();
  await op.locator('input[placeholder="q"]').fill('mcp').catch(() => {});
  await op.locator('button.execute').click();
  await op.locator('.responses-wrapper .live-responses-table').waitFor();
  await page.waitForTimeout(600);
  await op.scrollIntoViewIfNeeded();
  await op.locator('.responses-wrapper').evaluate(el => window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 120));
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${OUT}/swagger-try-articles.png` });
  console.log('ok');
} finally { await browser.close(); clearTimeout(killer); }
