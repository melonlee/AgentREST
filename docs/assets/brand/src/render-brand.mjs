// Renders the brand PNGs from the HTML/SVG sources in this folder and syncs the website assets.
// Usage: node docs/assets/brand/src/gen-brand.mjs && node docs/assets/brand/src/render-brand.mjs [target...]
//   targets: banner social share poster arch logos icons site   (default: all)
// Needs playwright-core + a local Chrome (set PLAYWRIGHT_CORE to its index.mjs if not resolvable).
import { readFileSync, writeFileSync, unlinkSync, mkdirSync, copyFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const { chromium } = await import(process.env.PLAYWRIGHT_CORE ? pathToFileURL(process.env.PLAYWRIGHT_CORE).href : "playwright-core");
const src = dirname(fileURLToPath(import.meta.url));
const out = join(src, "..");
const icons = join(out, "icons");
const site = join(src, "../../../../website/assets");
const pages = {
  banner: { file: "banner.html", out: "lazyrest-banner.png", w: 1280, h: 400 },
  social: { file: "social.html", out: "lazyrest-social-preview.png", w: 1280, h: 640, dpr: 1 },
  share: { file: "share-card-en.html", out: "lazyrest-share-card-en.png", w: 1200, h: 630, dpr: 1 },
  poster: { file: "poster-zh.html", out: "lazyrest-poster-zh.png", w: 1080, h: 1440, dpr: 1 },
  arch: { file: "architecture.html", out: "lazyrest-architecture.png", w: 1680, h: 880 },
};
// [svg, out png, width] - transparent background, height from the viewBox aspect ratio
const logos = [
  ["lazyrest-logo.svg", "lazyrest-logo.png", 960], ["lazyrest-logo-dark.svg", "lazyrest-logo-dark.png", 960],
  ["lazyrest-wordmark.svg", "lazyrest-wordmark.png", 720], ["lazyrest-wordmark-dark.svg", "lazyrest-wordmark-dark.png", 720],
  ["lazyrest-mark.svg", "lazyrest-mark.png", 512], ["lazyrest-mark-dark.svg", "lazyrest-mark-dark.png", 512],
  ["lazyrest-icon.svg", "lazyrest-icon.png", 1024], ["lazyrest-beaver.svg", "lazyrest-beaver.png", 840],
];
// App icon sizes: the full beaver down to 64 px, the head-only crop below that (it stays legible).
const iconSizes = [
  ["lazyrest-icon.svg", [512, 256, 128, 64]],
  ["lazyrest-icon-small.svg", [48, 32, 16]],
];
const only = process.argv.slice(2);
const want = (t) => !only.length || only.includes(t);
const include = (html) => html.replace(/<!--include:([\w-]+)-->/g, (_, n) => readFileSync(join(src, `${n}.inc.html`), "utf8"));

// Minimal .ico writer: PNG-compressed entries (supported by every current browser / OS).
function ico(entries) {
  const head = Buffer.alloc(6 + 16 * entries.length);
  head.writeUInt16LE(0, 0); head.writeUInt16LE(1, 2); head.writeUInt16LE(entries.length, 4);
  let offset = head.length;
  entries.forEach(({ size, png }, i) => {
    const o = 6 + 16 * i;
    head.writeUInt8(size % 256, o); head.writeUInt8(size % 256, o + 1);
    head.writeUInt16LE(1, o + 4); head.writeUInt16LE(32, o + 6);
    head.writeUInt32LE(png.length, o + 8); head.writeUInt32LE(offset, o + 12);
    offset += png.length;
  });
  return Buffer.concat([head, ...entries.map((e) => e.png)]);
}

const killer = setTimeout(() => { console.error("timeout"); process.exit(2); }, 180000);
const browser = await chromium.launch({ channel: "chrome", timeout: 30000 });
// Renders an SVG file through <img> at an exact pixel size (works for any viewBox origin).
async function svgToPng(file, w, h, { bg = "transparent", path } = {}) {
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  try {
    await page.goto(pathToFileURL(join(src, "brand.css")).href); // file:// origin so <img> may load local SVGs
    await page.setContent(`<body style="margin:0;background:${bg}"><img src="${pathToFileURL(join(out, file)).href}" style="display:block;width:${w}px;height:${h}px"></body>`);
    await page.waitForFunction(() => document.images[0].complete && document.images[0].naturalWidth > 0, null, { timeout: 10000 });
    return await page.screenshot({ path, omitBackground: bg === "transparent", timeout: 20000 });
  } finally { await page.close(); }
}
const aspect = (file) => { const [, , , vw, vh] = readFileSync(join(out, file), "utf8").match(/viewBox="([\d.-]+) ([\d.-]+) ([\d.]+) ([\d.]+)"/); return vh / vw; };
try {
  for (const [name, p] of Object.entries(pages)) {
    if (!want(name)) continue;
    const tmp = join(src, `.${name}.build.html`);
    writeFileSync(tmp, include(readFileSync(join(src, p.file), "utf8")));
    const page = await browser.newPage({ viewport: { width: p.w, height: p.h }, deviceScaleFactor: process.env.DPR ? +process.env.DPR : (p.dpr ?? 2) });
    try {
      await page.goto(pathToFileURL(tmp).href, { waitUntil: "load", timeout: 15000 });
      await page.evaluate(() => document.fonts.ready);
      await page.waitForTimeout(300);
      await page.screenshot({ path: process.env.OUT_DIR ? join(process.env.OUT_DIR, p.out) : join(out, p.out), timeout: 20000 });
      console.log("saved", p.out);
    } finally { await page.close(); unlinkSync(tmp); }
  }
  if (want("logos")) {
    for (const [file, png, w] of logos) {
      await svgToPng(file, w, Math.round(w * aspect(file)), { path: join(out, png) });
      console.log("saved", png);
    }
  }
  if (want("icons") || want("site")) {
    mkdirSync(icons, { recursive: true });
    const small = {};
    for (const [file, sizes] of iconSizes) {
      for (const s of sizes) {
        small[s] = await svgToPng(file, s, s, { path: join(icons, `lazyrest-icon-${s}.png`) });
      }
    }
    // apple-touch-icon: opaque square, iOS applies its own rounded mask
    await svgToPng("lazyrest-icon.svg", 180, 180, { bg: "#161b1a", path: join(icons, "apple-touch-icon.png") });
    writeFileSync(join(icons, "favicon.ico"), ico([16, 32, 48].map((size) => ({ size, png: small[size] }))));
    copyFileSync(join(out, "lazyrest-mark.svg"), join(icons, "favicon.svg"));
    console.log("saved icons/ (sizes, apple-touch-icon, favicon.ico, favicon.svg)");
  }
  if (want("site")) {
    // website/ ships copies so the Pages artifact is self-contained
    for (const f of ["favicon.svg", "favicon.ico", "apple-touch-icon.png"]) copyFileSync(join(icons, f), join(site, f));
    copyFileSync(join(icons, "lazyrest-icon-32.png"), join(site, "favicon-32.png"));
    copyFileSync(join(icons, "lazyrest-icon-16.png"), join(site, "favicon-16.png"));
    for (const f of ["lazyrest-logo-dark.svg", "lazyrest-beaver.svg", "lazyrest-social-preview.png"]) copyFileSync(join(out, f), join(site, f));
    console.log("synced website/assets");
  }
} finally { await browser.close(); clearTimeout(killer); }
