// Generates the LazyREST logo SVGs from code so every variant stays geometrically identical.
// Usage: node docs/assets/brand/src/gen-brand.mjs   (writes SVGs into docs/assets/brand/)
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const out = join(dirname(fileURLToPath(import.meta.url)), "..");
mkdirSync(out, { recursive: true });

export const palette = {
  ink: "#0B110E", // near-black, slightly green
  paper: "#EEF3F0",
  mint: "#34E0A1", // accent on dark
  mintDeep: "#0B9467", // accent on light
};

// 64x64 mark: two callers (a REST client and an AI agent) merge into ONE path that ends in
// one endpoint - same permissions, same audit trail.
const SW = 8;
export function markBody(ink, accent) {
  return `<g fill="none" stroke-width="${SW}" stroke-linecap="butt" stroke-linejoin="round">
  <path d="M3 14 H13 C25 14 24 32 36 32" stroke="${ink}"/>
  <path d="M3 50 H13 C25 50 24 32 36 32 H45" stroke="${accent}"/>
</g>
<circle cx="52" cy="32" r="9" fill="${accent}"/>`;
}

// Monoline geometric wordmark "lazyrest" built from strokes (no font dependency).
// x-height band y 20..60 (stroke centred 23.75..56.25), ascender 0, descender ~84.
const S = 7.5;
const H = S / 2;
const R = 18;
const glyphs = {
  l: { w: 19, d: (x) => `M${x + H} 0 V46 A14 14 0 0 0 ${x + H + 14} 60` },
  a: { w: 43.5, d: (x) => { const c = x + H + R; return `M${c + R} 40 A${R} ${R} 0 1 0 ${c + R} 40.01 M${c + R} 20 V60`; } },
  z: { w: 33, d: (x) => `M${x} ${20 + H} H${x + 33 - H} L${x + H} ${60 - H} H${x + 33}` },
  y: { w: 43.5, d: (x) => `M${x + H} 20 V40 A${R} ${R} 0 0 0 ${x + H + 2 * R} 40 M${x + H + 2 * R} 20 V66 A18 18 0 0 1 ${x + H + R} 84 H${x + 6}` },
  r: { w: 30, d: (x) => `M${x + H} 20 V60 M${x + H} 40 A${R} ${R} 0 0 1 ${x + H + R} ${40 - R} H${x + 30}` },
  e: { w: 43.5, d: (x) => { const c = x + H + R; const a = Math.PI / 4; return `M${c - R} 40 H${c + R} A${R} ${R} 0 1 0 ${(c + R * Math.cos(a)).toFixed(2)} ${(40 + R * Math.sin(a)).toFixed(2)}`; } },
  s: { w: 33, d: (x) => { const r = (40 - (20 + H)) / 2; const l = x + H + r; const rr = x + 33 - H - r; return `M${x + 33} ${20 + H} H${l} A${r} ${r} 0 0 0 ${l} 40 H${rr} A${r} ${r} 0 0 1 ${rr} ${60 - H} H${x}`; } },
  t: { w: 26, d: (x) => `M${x + 10} 6 V46 A14 14 0 0 0 ${x + 24} 60 H${x + 26} M${x} ${20 + H} H${x + 25}` },
};
// per-pair spacing tweaks (optical)
const GAP = 9;
const kern = { la: -1, az: 1, zy: 0, yr: 3, re: 0, es: 1, st: 0 };
function layout(word) {
  let x = 0;
  const parts = [];
  [...word].forEach((ch, i) => {
    if (i > 0) x += GAP + (kern[word[i - 1] + ch] ?? 0);
    parts.push({ ch, x, d: glyphs[ch].d(x) });
    x += glyphs[ch].w;
  });
  return { parts, width: x };
}
const WORD = layout("lazyrest");
export const WORDMARK_W = Math.ceil(WORD.width);
export const WORDMARK_H = 88;
export function wordmarkBody(ink, accent) {
  const lazy = WORD.parts.slice(0, 4).map((p) => p.d).join(" ");
  const rest = WORD.parts.slice(4).map((p) => p.d).join(" ");
  return `<g fill="none" stroke-width="${S}" stroke-linecap="butt" stroke-linejoin="round">
  <path d="${lazy}" stroke="${ink}"/>
  <path d="${rest}" stroke="${accent}"/>
</g>`;
}

function svg(w, h, body, title) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="${title}">
<title>${title}</title>
${body}
</svg>
`;
}

// Horizontal lockup: mark height = x-height band + a bit, centred on the x-height.
function lockup(ink, accent) {
  const s = 1.12; // mark drawn at 64 units, wordmark x-height 40
  const markH = 64 * s;
  const pad = 6;
  const wy = 2 + pad; // wordmark top
  const my = wy + 40 - markH / 2; // centre mark on x-height centre (y=40)
  const wx = Math.round(64 * s + 24);
  const w = wx + WORDMARK_W + pad;
  const h = WORDMARK_H + 2 * pad + 4;
  const body = `<g transform="translate(${pad} ${my}) scale(${s})">${markBody(ink, accent)}</g>
<g transform="translate(${wx + pad} ${wy})">${wordmarkBody(ink, accent)}</g>`;
  return { w: Math.ceil(w + pad), h, body };
}

// App-icon style tile (favicon, avatars)
function tile(bg, ink, accent) {
  return `<rect width="64" height="64" rx="14" fill="${bg}"/>
<g transform="translate(9 9) scale(0.72)">${markBody(ink, accent)}</g>`;
}

const L = lockup(palette.ink, palette.mintDeep);
const D = lockup(palette.paper, palette.mint);
const files = {
  "lazyrest-mark.svg": svg(64, 64, markBody(palette.ink, palette.mintDeep), "LazyREST"),
  "lazyrest-mark-dark.svg": svg(64, 64, markBody(palette.paper, palette.mint), "LazyREST"),
  "lazyrest-icon.svg": svg(64, 64, tile(palette.ink, palette.paper, palette.mint), "LazyREST"),
  "lazyrest-wordmark.svg": svg(WORDMARK_W, WORDMARK_H, wordmarkBody(palette.ink, palette.mintDeep), "LazyREST"),
  "lazyrest-wordmark-dark.svg": svg(WORDMARK_W, WORDMARK_H, wordmarkBody(palette.paper, palette.mint), "LazyREST"),
  "lazyrest-logo.svg": svg(L.w, L.h, L.body, "LazyREST"),
  "lazyrest-logo-dark.svg": svg(D.w, D.h, D.body, "LazyREST"),
};
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  for (const [name, content] of Object.entries(files)) {
    writeFileSync(join(out, name), content);
    console.log("wrote", name);
  }
}
