# AgentREST website

Standalone static landing page: plain HTML, CSS and a small vanilla JS file (copy buttons, header border). No build step, no CDNs, web fonts or trackers. All in-page URLs are relative, so the folder works at a domain root or under `https://melonlee.github.io/AgentREST/`.

```text
website/
├── index.html        # 简体中文 (primary)
├── en/index.html     # English
└── assets/           # site.css · site.js · favicons · logo + beaver SVGs · social preview · WebP screenshots
```

Preview locally (the language links point at folders, which need a web server):

```bash
cd website && python3 -m http.server 8080   # http://localhost:8080 and /en/
```

Deployment: `.github/workflows/pages.yml` publishes this folder to GitHub Pages on every push to `master` that touches `website/**` (Settings → Pages → Source: *GitHub Actions*). `og:image`, `og:url`, `canonical` and `hreflang` are absolute URLs for `https://melonlee.github.io/AgentREST/`; update them in both pages if the site moves to a custom domain.

Editing rules: every claim must match the READMEs and the code; update both languages together. Images come from `docs/assets/brand` (synced by `node docs/assets/brand/src/render-brand.mjs site`) and `docs/assets/screenshots` (re-encoded to WebP); colours follow `docs/assets/brand/src/brand.css`.
