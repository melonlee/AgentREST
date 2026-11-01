// Renders the brand PNGs from the HTML/SVG sources in this folder.
// Usage: node docs/assets/brand/src/render-brand.mjs [name...]
// Needs playwright-core + a local Chrome (set PLAYWRIGHT_CORE to its index.mjs if not resolvable).
import { readFileSync, writeFileSync, unlinkSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const { chromium } = await import(process.env.PLAYWRIGHT_CORE ? pathToFileURL(process.env.PLAYWRIGHT_CORE).href : "playwright-core");
const src = dirname(fileURLToPath(import.meta.url));
const out = join(src, "..");
const pages = {
  banner: { file: "banner.html", out: "lazyrest-banner.png", w: 1280, h: 400 },
  social: { file: "social.html", out: "lazyrest-social-preview.png", w: 1280, h: 640, dpr: 1 },
  share: { file: "share-card-en.html", out: "lazyrest-share-card-en.png", w: 1200, h: 630, dpr: 1 },
  poster: { file: "poster-zh.html", out: "lazyrest-poster-zh.png", w: 1080, h: 1440, dpr: 1 },
  arch: { file: "architecture.html", out: "lazyrest-architecture.png", w: 1680, h: 880 },
};
const logos = [
  ["lazyrest-logo.svg", 900], ["lazyrest-logo-dark.svg", 900], ["lazyrest-wordmark.svg", 720], ["lazyrest-wordmark-dark.svg", 720],
  ["lazyrest-mark.svg", 512], ["lazyrest-mark-dark.svg", 512], ["lazyrest-icon.svg", 512],
];
const only = process.argv.slice(2);
const include = (html) => html.replace(/<!--include:([\w-]+)-->/g, (_, n) => readFileSync(join(src, `${n}.inc.html`), "utf8"));
const killer = setTimeout(() => { console.error("timeout"); process.exit(2); }, 120000);
const browser = await chromium.launch({ channel: "chrome", timeout: 30000 });
try {
  for (const [name, p] of Object.entries(pages)) {
    if (only.length && !only.includes(name)) continue;
    const tmp = join(src, `.${name}.build.html`);
    writeFileSync(tmp, include(readFileSync(join(src, p.file), "utf8")));
    const page = await browser.newPage({ viewport: { width: p.w, height: p.h }, deviceScaleFactor: process.env.DPR ? +process.env.DPR : (p.dpr ?? 2) });
    try {
      await page.goto(pathToFileURL(tmp).href, { waitUntil: "load", timeout: 15000 });
      await page.waitForTimeout(300);
      await page.screenshot({ path: process.env.OUT_DIR ? join(process.env.OUT_DIR, p.out) : join(out, p.out) });
      console.log("saved", p.out);
    } finally { await page.close(); unlinkSync(tmp); }
  }
  if (!only.length || only.includes("logos")) {
    for (const [file, w] of logos) {
      const svg = readFileSync(join(out, file), "utf8");
      const [, vw, vh] = svg.match(/viewBox="0 0 ([\d.]+) ([\d.]+)"/);
      const h = Math.round((w * vh) / vw);
      const page = await browser.newPage({ viewport: { width: w, height: h } });
      try {
        await page.setContent(`<body style="margin:0;background:transparent">${svg.replace(/width="[\d.]+" height="[\d.]+"/, `width="${w}" height="${h}"`)}</body>`);
        await page.screenshot({ path: join(out, file.replace(".svg", ".png")), omitBackground: true, clip: { x: 0, y: 0, width: w, height: h } });
        console.log("saved", file.replace(".svg", ".png"));
      } finally { await page.close(); }
    }
  }
} finally { await browser.close(); clearTimeout(killer); }
