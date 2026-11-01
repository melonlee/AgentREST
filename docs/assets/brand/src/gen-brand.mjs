// Generates the LazyREST logo SVGs (beaver mascot + "lazyrest" wordmark) from code so every
// variant stays geometrically identical.
// Usage: node docs/assets/brand/src/gen-brand.mjs   (writes SVGs into docs/assets/brand/)
//
// Mascot sources live in ./mascot/ (FantasyAI Lab mascot family, same canvas as the 喵想 cat):
//   beaver.svg        glossy app icon, 1024 (viewBox 100..924 = the squircle tile)
//   beaver-flat.svg   flat version of the same icon
//   beaver-small.svg  head-only crop for 16-48 px (favicons)
// Everything else (mark, figure, lockups) is derived here.
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const src = dirname(fileURLToPath(import.meta.url));
const out = join(src, "..");
mkdirSync(out, { recursive: true });
const mascot = (name) => readFileSync(join(src, "mascot", name), "utf8");

export const palette = {
  ink: "#0B110E", // near-black, slightly green
  paper: "#EEF3F0",
  mint: "#34E0A1", // accent on dark
  mintDeep: "#0B9467", // accent on light
  tile: "#111615", // mascot tile (flat)
};

const ICON = mascot("beaver.svg");
const SQUIRCLE = ICON.match(/<path id="squircle" d="([^"]+)"/)[1];

// ---------------------------------------------------------------- flat head mark
// Head-only crop of beaver-small.svg, flattened to solid fills so it stays crisp at 16-40 px
// (header logo, favicon.svg, next to text). `id` keeps clip ids unique when inlined.
const HEAD_T = "translate(512 1060) scale(1.16) translate(-512 -1000) rotate(-4 512 760)";
const HEAD = "M 104 1110 C 104 900 136 780 196 716 C 172 540 318 392 512 392 C 706 392 852 540 828 716 C 888 780 920 900 920 1110 Z";
export function markBody({ tile, edge, edgeW = 36, id }) {
  const f = tile; // features are punched in the tile colour
  return `<defs><clipPath id="${id}"><path d="${SQUIRCLE}"/></clipPath></defs>
<path d="${SQUIRCLE}" fill="${tile}"/>
<g clip-path="url(#${id})">
<circle cx="790" cy="236" r="70" fill="${palette.mint}"/>
<g transform="${HEAD_T}">
<g fill="#fff"><circle cx="246" cy="520" r="62"/><circle cx="778" cy="520" r="62"/><path d="${HEAD}"/></g>
<circle cx="254" cy="526" r="32" fill="#c9c4bc"/><circle cx="770" cy="526" r="32" fill="#c9c4bc"/>
<rect x="352" y="542" width="80" height="112" rx="40" fill="${f}"/><circle cx="406" cy="574" r="17" fill="#fff"/>
<rect x="592" y="542" width="80" height="112" rx="40" fill="${f}"/><circle cx="646" cy="574" r="17" fill="#fff"/>
<path d="M 404 750 C 404 712 450 700 512 700 C 574 700 620 712 620 750 C 620 790 572 800 512 800 C 452 800 404 790 404 750 Z" fill="#e4e1db"/>
<path d="M 456 690 C 456 664 482 654 512 654 C 542 654 568 664 568 690 C 568 716 534 728 512 728 C 490 728 456 716 456 690 Z" fill="${f}"/>
<path d="M 468 790 L 556 790 L 556 862 Q 556 874 544 874 L 480 874 Q 468 874 468 862 Z" fill="#fffaf0" stroke="${f}" stroke-width="12" stroke-linejoin="round"/>
<path d="M 512 792 L 512 872" stroke="${f}" stroke-width="11"/>
</g>
${edge ? `<path d="${SQUIRCLE}" fill="none" stroke="${edge}" stroke-width="${edgeW}"/>` : ""}
</g>`;
}
const MARK_LIGHT = { tile: palette.tile, edge: palette.tile, edgeW: 56, id: "lr-m" }; // dark tile + rim so the white body never melts into a white page
const MARK_DARK = { tile: "#1A2521", edge: "#34443D", id: "lr-md" }; // lifted tile + edge on near-black

// ---------------------------------------------------------------- figure (no tile)
// The glossy beaver from the app icon without its tile: clipped flat at the bottom edge so it can
// "rise" from the edge of a card or the canvas. Used on the website and the marketing images.
function figure() {
  return ICON
    .replace(/width="1024" height="1024" viewBox="100 100 824 824"/, 'width="840" height="824" viewBox="100 100 840 824" role="img" aria-label="LazyREST beaver"')
    .replace('<clipPath id="clip"><use href="#squircle"/></clipPath>', '<clipPath id="clip"><rect x="60" y="60" width="920" height="864"/></clipPath>')
    .replace('<use href="#squircle" fill="url(#bg)"/>', "")
    .replace('<use href="#squircle" fill="none" stroke="url(#rim)" stroke-width="3"/>', "");
}

// ---------------------------------------------------------------- wordmark
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

function svg(w, h, body, title, viewBox = `0 0 ${w} ${h}`) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="${viewBox}" role="img" aria-label="${title}">
<title>${title}</title>
${body}
</svg>
`;
}

// Horizontal lockup: the beaver tile is centred on the wordmark's x-height (y=40) and spans
// roughly ascender..descender, so it reads as a peer of the lettering, not a badge.
function lockup(ink, accent, mark) {
  const T = 84; // tile size in wordmark units (x-height = 40)
  const pad = 4;
  const gap = 22;
  const wy = pad + T / 2 - 40; // wordmark top so that its x-height centre meets the tile centre
  const wx = pad + T + gap;
  const w = Math.ceil(wx + WORDMARK_W + pad);
  const h = Math.max(T + 2 * pad, Math.ceil(wy + WORDMARK_H));
  const body = `<svg x="${pad}" y="${pad}" width="${T}" height="${T}" viewBox="100 100 824 824">${markBody(mark)}</svg>
<g transform="translate(${wx} ${wy})">${wordmarkBody(ink, accent)}</g>`;
  return { w, h, body };
}

const L = lockup(palette.ink, palette.mintDeep, MARK_LIGHT);
const D = lockup(palette.paper, palette.mint, MARK_DARK);
export const files = {
  "lazyrest-icon.svg": ICON.replace("<defs>", "<title>LazyREST</title>\n<defs>"),
  "lazyrest-icon-small.svg": mascot("beaver-small.svg").replace("<defs>", "<title>LazyREST</title>\n<defs>"),
  "lazyrest-icon-flat.svg": mascot("beaver-flat.svg").replace(/^<svg /, '<svg width="1024" height="1024" '),
  "lazyrest-beaver.svg": figure(),
  "lazyrest-mark.svg": svg(64, 64, markBody(MARK_LIGHT), "LazyREST", "100 100 824 824"),
  "lazyrest-mark-dark.svg": svg(64, 64, markBody(MARK_DARK), "LazyREST", "100 100 824 824"),
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
