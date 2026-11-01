// AgentREST website: progressive enhancement only. Every section works without this file.
(() => {
  "use strict";

  /* Header border once the page scrolls. */
  const header = document.querySelector(".site-header");
  if (header) {
    const onScroll = () => header.classList.toggle("scrolled", window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
  }

  /* Copy buttons: copy the text of the <pre> without prompt characters. */
  const label = document.documentElement.lang.startsWith("zh") ? ["复制", "已复制"] : ["Copy", "Copied"];
  document.querySelectorAll(".code").forEach((box) => {
    const pre = box.querySelector("pre");
    if (!pre || !navigator.clipboard) return;
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "copy";
    btn.textContent = label[0];
    btn.addEventListener("click", async () => {
      const clone = pre.cloneNode(true);
      clone.querySelectorAll(".p, .c").forEach((n) => n.remove());
      const text = clone.textContent.split("\n").map((l) => l.replace(/^ /, "")).filter((l) => l.trim()).join("\n");
      try {
        await navigator.clipboard.writeText(text);
        btn.textContent = label[1];
        btn.classList.add("done");
        setTimeout(() => { btn.textContent = label[0]; btn.classList.remove("done"); }, 1600);
      } catch { /* clipboard blocked: leave the text selectable */ }
    });
    box.appendChild(btn);
  });
})();
