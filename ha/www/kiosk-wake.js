/* kiosk-wake — wake-up statistics of the kiosk tablet, in the Otthon design language (--o-* tokens).
 *   kiosk-wake-tile  : small card, today's count + 24 h mini bars; tap -> navigates to a pop-up hash
 *   kiosk-wake-chart : modal content, 24 h / 7 day bars + latest wake-ups
 * Source: a total_increasing counter (sensor.kiosk_tablet_wake_count). Every increase of the
 * counter in the recorder history is one (or more) wake-up(s) at that timestamp. */
const FONT = `"Inter","SF Pro Display",-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,system-ui,sans-serif`;
const ORANGE = "#ff9f0a";
const DAY = 86400e3, HOUR = 3600e3;
const pad = (n) => String(n).padStart(2, "0");
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const ICON = `<svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="2.2"/><path d="M7.5 7.5a6.4 6.4 0 0 0 0 9M16.5 7.5a6.4 6.4 0 0 1 0 9M4.6 4.6a10.5 10.5 0 0 0 0 14.8M19.4 4.6a10.5 10.5 0 0 1 0 14.8"/></svg>`;

const BASE_CSS = `
:host{display:block;font-family:${FONT};color:var(--o-ink,#111113);-webkit-font-smoothing:antialiased;font-feature-settings:"tnum" 1,"cv11" 1}
*{box-sizing:border-box}
button{font:inherit;color:inherit;background:none;border:0;padding:0;cursor:pointer;-webkit-tap-highlight-color:transparent;text-align:left}
.card{background:var(--o-card,#fff);border-radius:26px;box-shadow:var(--o-shadow,0 1px 2px rgba(0,0,0,.04));position:relative;overflow:hidden}
.mute{color:var(--o-mute,#8a8a90)}
svg{display:block}`;

/* ---- data ------------------------------------------------------------- */
const startOfDay = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };

/** [{t, n}] wake events within the last 7 days, derived from the counter history. */
async function loadEvents(hass, entity) {
  const end = Date.now(), start = end - 7 * DAY;
  const res = await hass.callWS({ type: "history/history_during_period", start_time: new Date(start).toISOString(), end_time: new Date(end).toISOString(),
    entity_ids: [entity], minimal_response: true, no_attributes: true, significant_changes_only: false });
  const rows = res[entity] || [];
  const out = [];
  let prev = null;
  for (const r of rows) {
    const v = parseFloat(r.s ?? r.state);
    if (!Number.isFinite(v)) continue;
    const ts = r.lu ?? r.last_updated ?? r.lc;
    const t = typeof ts === "number" ? ts * 1000 : new Date(ts).getTime();
    if (prev != null && v > prev) out.push({ t, n: v - prev });
    prev = v;
  }
  return out;
}
const sum = (ev) => ev.reduce((a, e) => a + e.n, 0);
function hourBuckets(ev) {
  const base = Math.floor(Date.now() / HOUR) * HOUR - 23 * HOUR;
  const b = Array.from({ length: 24 }, (_, i) => ({ t: base + i * HOUR, n: 0 }));
  for (const e of ev) { const i = Math.floor((e.t - base) / HOUR); if (i >= 0 && i < 24) b[i].n += e.n; }
  return b;
}
function dayBuckets(ev) {
  const base = startOfDay(Date.now() - 6 * DAY);
  const b = Array.from({ length: 7 }, (_, i) => ({ t: base + i * DAY, n: 0 }));
  for (const e of ev) { const i = Math.floor((startOfDay(e.t) - base) / DAY); if (i >= 0 && i < 7) b[i].n += e.n; }
  return b;
}

class KBase extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: "open" }); this._cfg = null; this._hass = null; this._ev = []; this._sig = null; }
  setConfig(c) { if (!c.entity) throw new Error("entity is required"); this._cfg = { ...c }; this._build(); this._draw(); }
  set hass(h) {
    this._hass = h;
    const s = h.states?.[this._cfg?.entity]?.state;
    if (this._cfg && s !== this._sig) { this._sig = s; this._load(); }
  }
  getGridOptions() { return { columns: 12, min_columns: 6 }; }
  connectedCallback() { this._timer = setInterval(() => this._load(), 5 * 60e3); if (this._hass) this._load(); }
  disconnectedCallback() { clearInterval(this._timer); }
  async _load() {
    if (!this._hass || !this._cfg) return;
    try { this._ev = await loadEvents(this._hass, this._cfg.entity); this._draw(); } catch (e) { /* recorder not ready */ }
  }
}

/* ================================================================== tile */
class KWakeTile extends KBase {
  _build() {
    this.shadowRoot.innerHTML = `<style>${BASE_CSS}
      .card{padding:16px 18px;display:flex;align-items:center;gap:14px;width:100%}
      .ic{width:44px;height:44px;border-radius:50%;display:grid;place-items:center;font-size:21px;background:rgba(255,159,10,.14);color:${ORANGE};flex:none}
      .l{font-size:12.5px;font-weight:600;color:var(--o-mute,#8a8a90)}
      .n{font-size:34px;font-weight:300;letter-spacing:-.03em;line-height:1.05;margin-top:1px}
      .s{font-size:12px;color:var(--o-mute,#8a8a90);margin-top:1px}
      .mini{margin-left:auto;display:flex;align-items:flex-end;gap:2px;height:38px}
      .mini i{display:block;width:4px;border-radius:2px;background:${ORANGE};min-height:2px;opacity:.9}
      .mini i.z{background:var(--o-line,rgba(0,0,0,.1));opacity:1}
      .card:active{transform:scale(.985)}
    </style>
    <button class="card"><div class="ic">${ICON}</div><div><div class="l"></div><div class="n">–</div><div class="s"></div></div><div class="mini"></div></button>`;
    this.shadowRoot.querySelector(".card").addEventListener("click", () => {
      history.pushState(null, "", this._cfg.hash || "#wakes");
      window.dispatchEvent(new CustomEvent("location-changed"));
    });
  }
  _draw() {
    const r = this.shadowRoot; if (!r.querySelector(".n")) return;
    const today = startOfDay(Date.now());
    const todayN = sum(this._ev.filter((e) => e.t >= today));
    const total = parseFloat(this._hass?.states?.[this._cfg.entity]?.state);
    r.querySelector(".l").textContent = this._cfg.name || "Tablet ébredések";
    r.querySelector(".n").textContent = String(todayN);
    r.querySelector(".s").textContent = `ma${Number.isFinite(total) ? ` · ${total} összesen` : ""}`;
    const b = hourBuckets(this._ev), max = Math.max(1, ...b.map((x) => x.n));
    r.querySelector(".mini").innerHTML = b.map((x) => `<i class="${x.n ? "" : "z"}" style="height:${x.n ? 8 + (x.n / max) * 30 : 2}px"></i>`).join("");
  }
}
if (!customElements.get("kiosk-wake-tile")) customElements.define("kiosk-wake-tile", KWakeTile);

/* ================================================================== chart */
class KWakeChart extends KBase {
  _build() {
    this._mode = "h"; this._sel = null;
    this.shadowRoot.innerHTML = `<style>${BASE_CSS}
      .card{padding:18px 20px 16px;box-shadow:none;background:transparent}
      .row{display:flex;align-items:center;justify-content:space-between}
      .big{font-size:56px;font-weight:300;letter-spacing:-.04em;line-height:1}
      .big small{font-size:15px;font-weight:500;color:var(--o-mute,#8a8a90);letter-spacing:0;margin-left:6px}
      .seg{display:flex;background:var(--o-soft,#f0f0f3);border-radius:11px;padding:3px}
      .seg button{padding:6px 14px;border-radius:9px;font-size:12.5px;font-weight:600;color:var(--o-mute,#8a8a90)}
      .seg button.on{background:var(--o-card,#fff);color:var(--o-ink,#111);box-shadow:0 1px 3px rgba(0,0,0,.14)}
      .read{font-size:13px;font-weight:600;margin:6px 0 10px;min-height:18px}
      .bars{display:flex;align-items:flex-end;gap:var(--gap,4px);height:150px;border-bottom:1.5px dotted var(--o-line,rgba(0,0,0,.1))}
      .b{flex:1;display:flex;flex-direction:column;justify-content:flex-end;align-items:center;height:100%;cursor:pointer}
      .b i{display:block;width:100%;border-radius:6px 6px 2px 2px;background:${ORANGE};min-height:3px;opacity:.9}
      .b.z i{background:var(--o-line,rgba(0,0,0,.1));opacity:1}
      .b.sel i{opacity:1;box-shadow:0 0 0 2px var(--o-card,#fff),0 0 0 4px ${ORANGE}}
      .b em{font-style:normal;font-size:11px;font-weight:700;margin-bottom:3px;color:${ORANGE}}
      .ax{display:flex;gap:var(--gap,4px);margin-top:6px;font-size:11px;color:var(--o-mute,#8a8a90)}
      .ax span{flex:1;text-align:center;white-space:nowrap;overflow:visible}
      .sec{font-size:12.5px;font-weight:600;letter-spacing:.07em;text-transform:uppercase;color:var(--o-mute,#8a8a90);margin:18px 0 8px}
      .list{display:flex;flex-direction:column}
      .li{display:flex;align-items:center;gap:10px;padding:9px 0;border-top:1px solid var(--o-line,rgba(0,0,0,.08));font-size:14px}
      .li:first-child{border-top:0}
      .li .d{width:8px;height:8px;border-radius:50%;background:${ORANGE}}
      .li .t{font-weight:600}.li .w{margin-left:auto;color:var(--o-mute,#8a8a90);font-size:12.5px}
      .none{color:var(--o-mute,#8a8a90);font-size:13px;padding:6px 0}
    </style>
    <div class="card">
      <div class="row"><div class="big"><span class="tot">0</span><small class="cap"></small></div>
        <div class="seg"><button data-m="h" class="on">24 óra</button><button data-m="d">7 nap</button></div></div>
      <div class="read"></div>
      <div class="bars"></div><div class="ax"></div>
      <div class="sec">Utolsó ébredések</div><div class="list"></div>
    </div>`;
    const r = this.shadowRoot;
    r.querySelectorAll(".seg button").forEach((bt) => bt.addEventListener("click", () => {
      this._mode = bt.dataset.m; this._sel = null;
      r.querySelectorAll(".seg button").forEach((x) => x.classList.toggle("on", x === bt));
      this._draw();
    }));
    r.querySelector(".bars").addEventListener("click", (e) => {
      const b = e.target.closest(".b"); if (!b) return;
      this._sel = this._sel === +b.dataset.i ? null : +b.dataset.i; this._draw();
    });
  }
  _draw() {
    const r = this.shadowRoot; if (!r.querySelector(".bars")) return;
    const hourly = this._mode === "h";
    const b = hourly ? hourBuckets(this._ev) : dayBuckets(this._ev);
    const max = Math.max(1, ...b.map((x) => x.n));
    r.querySelector(".tot").textContent = String(sum(b.map((x) => ({ n: x.n }))));
    r.querySelector(".cap").textContent = hourly ? "ébredés az elmúlt 24 órában" : "ébredés az elmúlt 7 napban";
    r.querySelector(".bars").style.setProperty("--gap", hourly ? "4px" : "12px");
    r.querySelector(".ax").style.setProperty("--gap", hourly ? "4px" : "12px");
    r.querySelector(".bars").innerHTML = b.map((x, i) =>
      `<div class="b${x.n ? "" : " z"}${this._sel === i ? " sel" : ""}" data-i="${i}">${x.n ? `<em>${x.n}</em>` : ""}<i style="height:${x.n ? 8 + (x.n / max) * 118 : 3}px"></i></div>`).join("");
    r.querySelector(".ax").innerHTML = b.map((x, i) => {
      const d = new Date(x.t);
      const label = hourly ? (d.getHours() % 3 === 0 ? pad(d.getHours()) : "") : d.toLocaleDateString("hu-HU", { weekday: "short" });
      return `<span>${label}</span>`;
    }).join("");
    const read = r.querySelector(".read");
    if (this._sel != null && b[this._sel]) {
      const x = b[this._sel], d = new Date(x.t);
      read.textContent = hourly ? `${pad(d.getHours())}:00–${pad((d.getHours() + 1) % 24)}:00 · ${x.n} ébredés`
        : `${d.toLocaleDateString("hu-HU", { month: "long", day: "numeric", weekday: "long" })} · ${x.n} ébredés`;
    } else read.textContent = "Koppints egy oszlopra a részletekért";
    const last = [...this._ev].sort((a, c) => c.t - a.t).slice(0, 8);
    const today = startOfDay(Date.now());
    r.querySelector(".list").innerHTML = last.length ? last.map((e) => {
      const d = new Date(e.t);
      const day = e.t >= today ? "ma" : e.t >= today - DAY ? "tegnap" : d.toLocaleDateString("hu-HU", { month: "numeric", day: "numeric" });
      return `<div class="li"><span class="d"></span><span class="t">${pad(d.getHours())}:${pad(d.getMinutes())}</span><span class="w">${day}${e.n > 1 ? ` · ${e.n}×` : ""}</span></div>`;
    }).join("") : `<div class="none">Még nincs rögzített ébredés.</div>`;
  }
}
if (!customElements.get("kiosk-wake-chart")) customElements.define("kiosk-wake-chart", KWakeChart);

window.customCards = window.customCards || [];
window.customCards.push({ type: "kiosk-wake-tile", name: "Kiosk wake tile", description: "Today's wake-ups" }, { type: "kiosk-wake-chart", name: "Kiosk wake chart", description: "Wake-up chart" });
