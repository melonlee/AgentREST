// AgentREST brand master: a beaver coiled into a capital A whose crossbar is a mint USB-C plug.
// Parametric geometry — never hand-edit the generated SVGs in docs/assets/brand/.
// Usage: node docs/assets/brand/src/gen-brand.mjs   (writes SVGs into docs/assets/brand/)
//
// Drawn in the 「字母动物」 style shared with Capydown: fat round-capped letter strokes, flat fills,
// one crisp side-shade band instead of gradients. The A is the first letter of "agentrest", so the
// lockup reads [beaver A] gentrest — the mascot replaces the letter rather than sitting beside it.
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const src = dirname(fileURLToPath(import.meta.url));
const out = join(src, "..");
mkdirSync(out, { recursive: true });
const SQUIRCLE = readFileSync(join(src, "squircle.txt"), "utf8").trim();

export const palette = {
  ink: "#0B110E", // near-black, slightly green
  paper: "#EEF3F0",
  mint: "#34E0A1", // accent on dark
  mintDeep: "#0B9467", // accent on light
  tile: "#121917", // icon tile, near-black so the mint plug carries the mark
  tileDark: "#1B2522", // lifted tile for the dark-background variant
};
const FUR = "#C08457";
const FUR_SHADE = "#9A6540";
const MUZZLE = "#DCA87C";
const EAR_IN = "#7A4C2E";
const TAIL = "#6B4A33";
const TAIL_SHADE = "#543926";
const TOOTH = "#FFFBEF";
const EYE = "#2A1C14";
const BLUSH = "#E88E7E";

let SMALL = false; // drops the fur ticks, the plug slot and the tail hatching (they mush at 16-48 px)

// ---------------------------------------------------------------- SVG primitives
const f = (v) => { const s = v.toFixed(1); return s.endsWith(".0") ? s.slice(0, -2) : s; };
const stroke = (d, w, c, extra = "") =>
  `<path d="${d}" fill="none" stroke="${c}" stroke-width="${f(w)}" stroke-linecap="round" stroke-linejoin="round"${extra}/>`;
const fill = (d, c, extra = "") => `<path d="${d}" fill="${c}"${extra}/>`;
const circ = (cx, cy, r, c, extra = "") => `<circle cx="${f(cx)}" cy="${f(cy)}" r="${f(r)}" fill="${c}"${extra}/>`;
const ell = (cx, cy, rx, ry, c, rot = 0, extra = "") =>
  `<ellipse cx="${f(cx)}" cy="${f(cy)}" rx="${f(rx)}" ry="${f(ry)}" fill="${c}"${rot ? ` transform="rotate(${f(rot)} ${f(cx)} ${f(cy)})"` : ""}${extra}/>`;
/** Rounded rect with per-corner radii. */
function rr(x0, y0, x1, y1, tl = 0, tr = 0, br = 0, bl = 0) {
  return `M${f(x0 + tl)} ${f(y0)} H${f(x1 - tr)}` + (tr ? ` A${f(tr)} ${f(tr)} 0 0 1 ${f(x1)} ${f(y0 + tr)}` : "") +
    ` V${f(y1 - br)}` + (br ? ` A${f(br)} ${f(br)} 0 0 1 ${f(x1 - br)} ${f(y1)}` : "") +
    ` H${f(x0 + bl)}` + (bl ? ` A${f(bl)} ${f(bl)} 0 0 1 ${f(x0)} ${f(y1 - bl)}` : "") +
    ` V${f(y0 + tl)}` + (tl ? ` A${f(tl)} ${f(tl)} 0 0 1 ${f(x0 + tl)} ${f(y0)}` : "") + " Z";
}
const maskOf = (id, inner) =>
  `<mask id="${id}" maskUnits="userSpaceOnUse" x="-300" y="-300" width="1624" height="1624"><g fill="#fff" stroke="#fff">${inner}</g></mask>`;
/** Flat side shade: paint the shape dark, then the light copy nudged up-left, both clipped to the
 *  shape — leaving one crisp dark band along the bottom-right edge. No gradients. */
const shaded = (id, shape, base, shade, dx = -20, dy = -14) =>
  maskOf(id, shape("#fff")) +
  `<g mask="url(#${id})">${shape(shade)}<g transform="translate(${f(dx)} ${f(dy)})">${shape(base)}</g></g>`;
function ticks(pts, c, w = 9, L = 22) {
  if (SMALL) return "";
  return pts.map(([x, y, a]) => {
    const dx = Math.cos((a * Math.PI) / 180) * L / 2, dy = Math.sin((a * Math.PI) / 180) * L / 2;
    return stroke(`M${f(x - dx)} ${f(y - dy)} L${f(x + dx)} ${f(y + dy)}`, w, c);
  }).join("");
}

// ---------------------------------------------------------------- the letter A
const AW = 190; // stroke weight
const HW = AW / 2;
const APEX_X = 512, APEX_Y = 262, FOOT_Y = 836, LX = 266, RX = 758;
const A_PATH = `M${LX} ${FOOT_Y} L${APEX_X} ${APEX_Y} L${RX} ${FOOT_Y}`;
// head: a big rounded square hung on the right foot, blunt muzzle facing right
const HT = 666, HB = 936, HX0 = 700, HX1 = 960;
const headPath = () => rr(HX0, HT, HX1, HB, 110, 118, 128, 92);
// tail: the flat paddle trailing off the left foot, its long axis following the leg
const TAIL_C = [250, 866], TAIL_R = [150, 90], TAIL_ROT = 150;

function head() {
  // Alert, not sleepy: AgentREST is a tool you watch run. One big round eye with a highlight,
  // a blunt muzzle, and the incisors that make a beaver a beaver.
  let g = SMALL ? "" : ell(886, 848, 78, 62, MUZZLE, 0, ' opacity=".9"') + ell(798, 876, 38, 22, BLUSH, 0, ' opacity=".8"');
  const er = SMALL ? 44 : 36;
  g += circ(838, 742, er, EYE) + circ(838 + er * 0.3, 742 - er * 0.32, er * 0.3, "#fff");
  g += ell(916, 802, SMALL ? 36 : 30, SMALL ? 26 : 22, EYE, -8);
  // one incisor block split by a groove, hanging just below the chin — the beaver signifier
  const t0 = SMALL ? 878 : 886, t1 = 950, y0 = 872, y1 = SMALL ? 962 : 954;
  g += fill(rr(t0, y0, t1, y1, 0, 0, 18, 18), TOOTH, ` stroke="${EYE}" stroke-width="10" stroke-linejoin="round"`);
  g += stroke(`M${f((t0 + t1) / 2)} ${f(y0 + 6)} V${f(y1 - 8)}`, 9, EYE);
  return g;
}

function tail(flat) {
  const [cx, cy] = TAIL_C, [rx, ry] = TAIL_R;
  const shape = (c) => ell(cx, cy, rx, ry, c, TAIL_ROT);
  let g = flat ? shape(TAIL) : shaded("ar-t", shape, TAIL, TAIL_SHADE, -14, -10);
  if (!SMALL) { // the scaly cross-hatch of a beaver's paddle
    const a = (TAIL_ROT * Math.PI) / 180;
    const lines = [-74, -30, 14, 58].map((d) => {
      const px = cx + Math.cos(a) * d, py = cy + Math.sin(a) * d;
      const qx = Math.cos(a + Math.PI / 2) * ry, qy = Math.sin(a + Math.PI / 2) * ry;
      return `M${f(px - qx)} ${f(py - qy)} L${f(px + qx)} ${f(py + qy)}`;
    }).join(" ");
    g += maskOf("ar-tc", shape("#fff")) + `<g mask="url(#ar-tc)">${stroke(lines, 11, "#3D2819", ' opacity=".5"')}</g>`;
  }
  return g;
}

/** The crossbar is a mint USB-C plug — "MCP is the USB-C port for AI". */
function plug(flat) {
  const x0 = 340, x1 = 684, y0 = SMALL ? 582 : 592, y1 = SMALL ? 714 : 704, r = (y1 - y0) / 2;
  const shape = (c) => fill(rr(x0, y0, x1, y1, r, r, r, r), c);
  let g = flat ? shape(palette.mint) : shaded("ar-p", shape, palette.mint, palette.mintDeep, -12, -10);
  if (!SMALL) g += fill(rr(x0 + 62, y0 + 38, x1 - 62, y1 - 38, 18, 18, 18, 18), "#06372A", ' opacity=".9"');
  return g;
}

// fur tufts along the two outer edges of the A (t = how far down the leg, 0 = apex)
const TICKS = [0.3, 0.5, 0.7].map((t) => [APEX_X - 246 * t - HW * 0.919, APEX_Y + 574 * t - HW * 0.394, 203.2])
  .concat([0.25, 0.45].map((t) => [APEX_X + 246 * t + HW * 0.919, APEX_Y + 574 * t - HW * 0.394, -23.2]));

/** The whole mascot on the 1024 grid; ink box ART_BB. */
export function art({ small = false, flat = false } = {}) {
  SMALL = small;
  const shape = (c) => stroke(A_PATH, AW, c) + fill(headPath(), c);
  let g = tail(flat);
  g += circ(820, 664, 46, FUR) + circ(822, 668, 22, EAR_IN); // ear, poking above the skull
  g += flat ? shape(FUR) : shaded(small ? "ar-as" : "ar-a", shape, FUR, FUR_SHADE, -18, -14);
  g += ticks(TICKS, FUR_SHADE, 10, 26);
  g += head();
  g += plug(flat);
  SMALL = false;
  return g;
}
export const ART_BB = [113, 167, 962, 972]; // x0 y0 x1 y1 — follows from the constants above
const LETTER_TOP = APEX_Y - HW, LETTER_BOT = FOOT_Y + HW; // the A proper, ignoring tail and teeth

/** Scale the ink box into a `box`-wide square centred on a `size` canvas. */
function fit([x0, y0, x1, y1], box, size = 1024) {
  const s = box / Math.max(x1 - x0, y1 - y0);
  return `translate(${f(size / 2 - (s * (x0 + x1)) / 2)} ${f(size / 2 - (s * (y0 + y1)) / 2)}) scale(${s.toFixed(4)})`;
}

// ---------------------------------------------------------------- wordmark
// Monoline geometric wordmark built from strokes (no font dependency), fattened and round-capped to
// sit next to the letter-animal A. x-height band y 20..60, ascender 0, descender ~84.
const S = 11;
const H = S / 2;
const R = 18;
const glyphs = {
  a: { w: 43.5, d: (x) => { const c = x + H + R; return `M${c + R} 40 A${R} ${R} 0 1 0 ${c + R} 40.01 M${c + R} 20 V60`; } },
  g: { w: 43.5, d: (x) => { const c = x + H + R; return `M${c + R} 40 A${R} ${R} 0 1 0 ${c + R} 40.01 M${c + R} 20 V66 A18 18 0 0 1 ${c} 84 H${x + 8}`; } },
  n: { w: 43.5, d: (x) => `M${x + H} 20 V60 M${x + H} 40 A${R} ${R} 0 0 1 ${x + H + 2 * R} 40 V60` },
  r: { w: 30, d: (x) => `M${x + H} 20 V60 M${x + H} 40 A${R} ${R} 0 0 1 ${x + H + R} ${40 - R} H${x + 30 - H}` },
  e: { w: 43.5, d: (x) => { const c = x + H + R; const a = Math.PI / 4; return `M${c - R} 40 H${c + R} A${R} ${R} 0 1 0 ${(c + R * Math.cos(a)).toFixed(2)} ${(40 + R * Math.sin(a)).toFixed(2)}`; } },
  s: { w: 33, d: (x) => { const r = (40 - (20 + H)) / 2; const l = x + H + r; const rr2 = x + 33 - H - r; return `M${x + 33 - H} ${20 + H} H${l} A${r} ${r} 0 0 0 ${l} 40 H${rr2} A${r} ${r} 0 0 1 ${rr2} ${60 - H} H${x + H}`; } },
  t: { w: 26, d: (x) => `M${x + 10} 6 V46 A14 14 0 0 0 ${x + 24} 60 H${x + 26 - H} M${x} ${20 + H} H${x + 25}` },
};
const GAP = 9;
const kern = { ag: 0, ge: 0, en: 0, nt: 0, tr: -1, re: 0, es: 1, st: 0 }; // optical per-pair tweaks
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
// ink band: ascender/cap top 0 .. baseline 60 .. descender 89.5 (round caps included)
const WM_TOP = -6, WORDMARK_H = 102;
/** `word` is "agentrest" standalone, or "gentrest" when the beaver A supplies the first letter. */
export function wordmarkBody(word, ink, accent) {
  const W = layout(word);
  const split = word.length - 4; // ...rest always takes the accent
  const g = (parts) => parts.map((p) => p.d).join(" ");
  const body = `<g fill="none" stroke-width="${S}" stroke-linecap="round" stroke-linejoin="round">
  <path d="${g(W.parts.slice(0, split))}" stroke="${ink}"/>
  <path d="${g(W.parts.slice(split))}" stroke="${accent}"/>
</g>`;
  return { body, width: W.width };
}
export const WORDMARK_W = Math.ceil(layout("agentrest").width);

// ---------------------------------------------------------------- tiled mark
export function markBody({ tile, edge, edgeW = 36, id, box = 620 }) {
  return `<defs><clipPath id="${id}"><path d="${SQUIRCLE}"/></clipPath></defs>
<path d="${SQUIRCLE}" fill="${tile}"/>
<g clip-path="url(#${id})"><g transform="${fit(ART_BB, box)}">${art()}</g>${edge ? `
<path d="${SQUIRCLE}" fill="none" stroke="${edge}" stroke-width="${f(edgeW)}"/>` : ""}</g>`;
}
const MARK_LIGHT = { tile: palette.tile, edge: palette.tile, edgeW: 56, id: "ar-m" }; // rim so the tile never melts into a white page
const MARK_DARK = { tile: palette.tileDark, edge: "#34443D", id: "ar-md" };

// ---------------------------------------------------------------- assembly
const GEN = "generated by docs/assets/brand/src/gen-brand.mjs";
function svg(w, h, body, title, viewBox = `0 0 ${f(w)} ${f(h)}`) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${f(w)}" height="${f(h)}" viewBox="${viewBox}" role="img" aria-label="${title}">
<title>${title}</title>
<!-- ${GEN} -->
${body}
</svg>
`;
}
const TILE_SHADOW = '<filter id="tileDrop" x="-10%" y="-10%" width="120%" height="125%"><feGaussianBlur stdDeviation="14"/></filter>';
/** 1024 app-icon grid: the squircle body spans 100..924, the outer 100 is the macOS drop shadow. */
function icon({ small = false, flat = false, box = 620, tile = palette.tile } = {}) {
  const defs = `<defs>\n  ${TILE_SHADOW}\n  <path id="squircle" d="${SQUIRCLE}"/>\n  <clipPath id="clip"><use href="#squircle"/></clipPath>\n</defs>`;
  const shadow = flat ? "" : '<use class="tile-shadow" href="#squircle" fill="#000" opacity="0.3" transform="translate(0 12)" filter="url(#tileDrop)"/>\n';
  const body = `${defs}\n${shadow}<use href="#squircle" fill="${tile}"/>\n<g clip-path="url(#clip)"><g transform="${fit(ART_BB, box)}">${art({ small, flat })}</g></g>`;
  return svg(1024, 1024, body, "AgentREST");
}

/** Horizontal lockup: the frameless beaver A *is* the "a", so the wordmark drops its first letter.
 *  The A proper is scaled onto the cap band (0..60); the tail and teeth overshoot it the way a
 *  round letter overshoots a flat one. */
function lockup(ink, accent) {
  const pad = 6, gap = 13;
  const [x0, y0, x1] = ART_BB;
  const s = 60 / (LETTER_BOT - LETTER_TOP);
  const { body: word, width: ww } = wordmarkBody("gentrest", ink, accent);
  const wx = pad + (x1 - x0) * s + gap;
  const body = `<g transform="translate(${f(pad - x0 * s)} ${f(-LETTER_TOP * s)}) scale(${s.toFixed(4)})">${art()}</g>
<g transform="translate(${f(wx)} 0)">${word}</g>`;
  return { w: Math.ceil(wx + ww + pad), h: WORDMARK_H, body };
}

const L = lockup(palette.ink, palette.mintDeep);
const D = lockup(palette.paper, palette.mint);
const WL = wordmarkBody("agentrest", palette.ink, palette.mintDeep);
const WD = wordmarkBody("agentrest", palette.paper, palette.mint);
const wordmarkSvg = ({ body, width }) => {
  const w = Math.ceil(width) + 12;
  return svg(w, WORDMARK_H, `<g transform="translate(6 0)">${body}</g>`, "AgentREST", `0 ${WM_TOP} ${w} ${WORDMARK_H}`);
};
export const files = {
  "agentrest-icon.svg": icon(),
  "agentrest-icon-small.svg": icon({ small: true, box: 680 }),
  "agentrest-icon-flat.svg": icon({ flat: true }),
  // frameless mascot for the website and the marketing images: cropped tight to the ink so the
  // tail and the teeth sit flush on whatever edge it is stood on
  "agentrest-beaver.svg": svg(ART_BB[2] - ART_BB[0], ART_BB[3] - ART_BB[1], art(), "AgentREST beaver",
    `${ART_BB[0]} ${ART_BB[1]} ${ART_BB[2] - ART_BB[0]} ${ART_BB[3] - ART_BB[1]}`),
  "agentrest-mark.svg": svg(64, 64, markBody(MARK_LIGHT), "AgentREST", "100 100 824 824"),
  "agentrest-mark-dark.svg": svg(64, 64, markBody(MARK_DARK), "AgentREST", "100 100 824 824"),
  "agentrest-wordmark.svg": wordmarkSvg(WL),
  "agentrest-wordmark-dark.svg": wordmarkSvg(WD),
  "agentrest-logo.svg": svg(L.w, L.h, L.body, "AgentREST", `0 ${WM_TOP} ${L.w} ${WORDMARK_H}`),
  "agentrest-logo-dark.svg": svg(D.w, D.h, D.body, "AgentREST", `0 ${WM_TOP} ${D.w} ${WORDMARK_H}`),
};
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  for (const [name, content] of Object.entries(files)) {
    writeFileSync(join(out, name), content);
    console.log("wrote", name);
  }
}
