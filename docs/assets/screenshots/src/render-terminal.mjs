// Renders the REAL captured terminal output in src/*.txt / src/claude-run.jsonl into clean,
// terminal-styled PNGs. Nothing is invented: commands and output are printed verbatim; long
// commands are only broken at option boundaries with a shell line continuation ("\").
// Usage: node docs/assets/screenshots/src/render-terminal.mjs   (PLAYWRIGHT_CORE=… if needed)
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const { chromium } = await import(process.env.PLAYWRIGHT_CORE ? pathToFileURL(process.env.PLAYWRIGHT_CORE).href : "playwright-core");
const src = dirname(fileURLToPath(import.meta.url));
const out = join(src, "..");
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

function wrapCommand(cmd, max = 104) {
  if (cmd.length <= max) return [cmd];
  // candidate break points: a space OUTSIDE quotes that precedes an option or a pipe
  const parts = [];
  let q = null, last = 0, depth = 0;
  for (let i = 0; i < cmd.length; i++) {
    const c = cmd[i];
    if (q) { if (c === q) q = null; continue; }
    if (c === "'" || c === '"') { q = c; continue; }
    if (c === "(") depth++;
    if (c === ")") depth--;
    if (c === " " && i > last && /^ (?:-H |-d |--[a-z-]+ |\| )/.test(cmd.slice(i))) { parts.push(cmd.slice(last, i)); last = i; }
  }
  parts.push(cmd.slice(last));
  const lines = [];
  let cur = "";
  for (const p of parts) {
    if (cur && (cur + p).length > max) { lines.push(cur + " \\"); cur = "   " + p.trimStart(); }
    else cur += p;
  }
  lines.push(cur);
  return lines;
}

function colorJson(line) {
  // tokenizer: colours tokens without changing a single character of the output
  const re = /"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|true|false|null|\s+|[^"\s]+?|"/g;
  const toks = line.match(re) || [];
  return toks.map((t, i) => {
    if (t.startsWith('"') && t.length > 1 && t.endsWith('"')) {
      let j = i + 1; while (j < toks.length && /^\s+$/.test(toks[j])) j++;
      return `<span class="${toks[j] === ":" ? "k" : "s"}">${esc(t)}</span>`;
    }
    if (/^-?\d/.test(t)) return `<span class="n">${esc(t)}</span>`;
    if (/^(true|false|null)$/.test(t)) return `<span class="b">${esc(t)}</span>`;
    return esc(t);
  }).join("");
}

function renderTranscript(text) {
  const html = [];
  for (const line of text.replace(/\n$/, "").split("\n")) {
    if (line.startsWith("$ ")) {
      const ls = wrapCommand(line.slice(2));
      html.push(`<div class="cmd"><span class="ps">$</span> ${ls.map(esc).join("\n")}</div>`);
    } else if (/^\s*[{\["}\]]/.test(line)) {
      html.push(`<div class="o">${colorJson(line)}</div>`);
    } else {
      html.push(`<div class="o">${esc(line).replace(/\bSUCCESS\b/g, '<span class="ok">SUCCESS</span>').replace(/\bDENIED\b/g, '<span class="deny">DENIED</span>').replace(/\bMCP\b(?= )/g, '<span class="mcp">MCP</span>')}</div>`);
    }
  }
  return html.join("\n");
}

// Claude Code stream-json -> transcript in Claude Code's visual idiom (● for steps, ⎿ for results)
function renderClaude(jsonl, prompt) {
  const ev = jsonl.trim().split("\n").map((l) => JSON.parse(l));
  const init = ev.find((e) => e.type === "system");
  const html = [`<div class="cmd"><span class="ps">$</span> ${wrapCommand(`claude -p ${JSON.stringify(prompt)} --mcp-config mcp.json --strict-mcp-config --allowedTools "mcp__agentrest__searchArticles,mcp__agentrest__getArticle" --max-turns 6 --output-format stream-json --verbose`).map(esc).join("\n")}</div>`];
  html.push(`<div class="o dim">  init · claude ${esc(init.claude_code_version)} · mcp: ${init.mcp_servers.map((s) => `${esc(s.name)} <span class="ok">${esc(s.status)}</span>`).join(", ")}</div>`);
  html.push(`<div class="user">&gt; ${esc(prompt)}</div>`);
  const md = (t) => esc(t).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>").replace(/`([^`]+)`/g, '<span class="code">$1</span>');
  for (const e of ev) {
    if (e.type === "assistant") {
      for (const c of e.message.content) {
        if (c.type === "text") html.push(`<div class="step"><span class="dot">●</span><div>${md(c.text).replace(/\n/g, "<br>")}</div></div>`);
        if (c.type === "tool_use") {
          const [, server, tool] = c.name.split("__");
          const args = Object.entries(c.input).map(([k, v]) => `${k}: ${esc(JSON.stringify(v))}`).join(", ");
          html.push(`<div class="step"><span class="dot m">●</span><div><b>${esc(server)} - ${esc(tool)}</b> <span class="dim">(MCP)</span>(${args})</div></div>`);
        }
      }
    } else if (e.type === "user") {
      for (const c of e.message.content) {
        const raw = Array.isArray(c.content) ? c.content.map((x) => x.text).join("") : String(c.content);
        const max = 190;
        const shown = raw.length > max ? `${raw.slice(0, max)}… <span class="dim">(+${raw.length - max} chars)</span>` : raw;
        html.push(`<div class="res"><span class="dim">⎿</span><div class="j">${raw.length > max ? colorJson(raw.slice(0, max)) + `… <span class="dim">(+${raw.length - max} chars)</span>` : colorJson(shown)}</div></div>`);
      }
    } else if (e.type === "result") {
      html.push(`<div class="o dim">  result · ${e.num_turns} turns · ${(e.duration_ms / 1000).toFixed(1)}s</div>`);
    }
  }
  return html.join("\n");
}

const shots = [
  { out: "terminal-curl-problemdetail.png", title: "curl · REST session (alice) → ProblemDetail → audit", body: renderTranscript(readFileSync(join(src, "curl-session.txt"), "utf8")), width: 1180 },
  { out: "terminal-mcp-inspector.png", title: "MCP Inspector CLI · tools/list + tools/call", body: renderTranscript(readFileSync(join(src, "inspector-session.txt"), "utf8")), width: 1180 },
  { out: "terminal-audit-mcp.png", title: "GET /api/audit-logs?channel=MCP (admin)", body: renderTranscript(readFileSync(join(src, "audit-session.txt"), "utf8")), width: 1340 },
  { out: "claude-code-mcp.png", title: "Claude Code → AgentREST over MCP", body: renderClaude(readFileSync(join(src, "claude-run.jsonl"), "utf8"), "Search AgentREST for articles about MCP, then read the most relevant one and summarize it in two sentences."), width: 1180, wrap: true },
];

const css = `
*{box-sizing:border-box;margin:0;padding:0}
body{background:#060A08;font-family:"SF Mono",ui-monospace,Menlo,monospace;-webkit-font-smoothing:antialiased}
.stage{padding:36px;background:radial-gradient(1200px 500px at 85% -10%,rgba(52,224,161,.14),transparent 60%),#060A08;display:inline-block}
.win{background:#0B110E;border:1px solid #24312B;border-radius:12px;overflow:hidden;box-shadow:0 30px 80px rgba(0,0,0,.5)}
.bar{display:flex;align-items:center;gap:8px;padding:12px 16px;border-bottom:1px solid #1B2621;color:#5E6F67;font-size:12.5px;font-family:-apple-system,"SF Pro Text",sans-serif}
.bar i{width:11px;height:11px;border-radius:50%;background:#2A3631;display:block}
.bar span{margin-left:10px}
.body{padding:18px 22px 22px;font-size:14px;line-height:1.62;color:#DCE5E0}
.cmd{white-space:pre;color:#EEF3F0;margin-top:10px}
.cmd:first-child{margin-top:0}
.ps{color:#34E0A1;font-weight:600}
.o{white-space:pre;color:#B9C6BF}
.k{color:#9CC4FF}.s{color:#7BEFC4}.n{color:#F5C26B}.b{color:#FF9E9E}
.ok{color:#34E0A1}.deny{color:#F5C26B;font-weight:600}.mcp{color:#34E0A1}
.dim{color:#5E6F67}
.user{margin:14px 0 6px;padding:10px 14px;border-left:2px solid #34E0A1;background:#111916;color:#EEF3F0;font-family:-apple-system,"SF Pro Text",sans-serif;font-size:15px;white-space:normal}
.step{display:flex;gap:10px;margin-top:12px;font-family:-apple-system,"SF Pro Text",sans-serif;font-size:15px;line-height:1.55;color:#DCE5E0}
.step b{font-family:"SF Mono",ui-monospace,Menlo,monospace;font-size:14px;color:#EEF3F0;font-weight:600}
.step .dot{color:#8FA199}.step .dot.m{color:#34E0A1}
.code{font-family:"SF Mono",ui-monospace,Menlo,monospace;font-size:13.5px;color:#7BEFC4}
.res{display:flex;gap:10px;margin:4px 0 0 22px;font-size:13px;line-height:1.55}
.res .j{white-space:pre-wrap;word-break:break-all;color:#B9C6BF}
`;
const killer = setTimeout(() => { console.error("timeout"); process.exit(2); }, 90000);
const browser = await chromium.launch({ channel: "chrome", timeout: 30000 });
try {
  for (const s of shots) {
    const page = await browser.newPage({ viewport: { width: s.width + 72, height: 600 }, deviceScaleFactor: 2 });
    try {
      await page.setContent(`<html><head><meta charset="utf-8"><style>${css}</style></head><body><div class="stage"><div class="win" style="width:${s.width}px"><div class="bar"><i></i><i></i><i></i><span>${esc(s.title)}</span></div><div class="body">${s.body}</div></div></div></body></html>`);
      await page.waitForTimeout(200);
      await page.locator(".stage").screenshot({ path: join(out, s.out) });
      console.log("saved", s.out);
    } finally { await page.close(); }
  }
} finally { await browser.close(); clearTimeout(killer); }
