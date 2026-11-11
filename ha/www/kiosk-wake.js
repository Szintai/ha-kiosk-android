/* kiosk-wake — status, wake-up statistics and on-demand camera of the kiosk tablet, in the Otthon
 * design language (--o-* tokens).
 *   kiosk-wake-tile  : small card: today's wake-ups, screen state, battery; tap -> opens the pop-up hash
 *   kiosk-wake-chart : modal content: state + battery, 24 h / 7 day wake bars, wake/dim log, camera
 *
 * Config (both cards): entity (wake counter), presence, battery, charging, temp, camera,
 * camera_rotate (0/90/180/270, CSS rotation of the camera image in the modal; use it only while the app's own
 * 'Kép elforgatása' setting is 0, otherwise the image is rotated twice).
 * Wake-ups = every increase of the total_increasing counter in the recorder history. The screen log is
 * every on/off change of the presence binary sensor (on = awake, off = dimmed).
 * The camera is only streamed while the "Kamera" button has been pressed (and the modal is visible). */
const FONT = `"Inter","SF Pro Display",-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,system-ui,sans-serif`;
const ORANGE = "#ff9f0a", GREEN = "#30d158", RED = "#ff453a";
const DAY = 86400e3, HOUR = 3600e3;
const pad = (n) => String(n).padStart(2, "0");
const hhmm = (t) => { const d = new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
const dayLabel = (t) => {
  const today = startOfDay(Date.now());
  if (t >= today) return "ma";
  if (t >= today - DAY) return "tegnap";
  return new Date(t).toLocaleDateString("hu-HU", { month: "numeric", day: "numeric" });
};
const ICON = `<svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="2.2"/><path d="M7.5 7.5a6.4 6.4 0 0 0 0 9M16.5 7.5a6.4 6.4 0 0 1 0 9M4.6 4.6a10.5 10.5 0 0 0 0 14.8M19.4 4.6a10.5 10.5 0 0 1 0 14.8"/></svg>`;
const CAM_ICON = `<svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M23 7l-7 5 7 5V7z"/><rect x="1" y="5" width="15" height="14" rx="2"/></svg>`;
const BOLT = `<svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor"><path d="M13 2 4 14h6l-1 8 9-12h-6z"/></svg>`;
const BATT = (pct, charging) => `<svg viewBox="0 0 28 14" width="1.8em" height="0.9em"><rect x="1" y="1" width="23" height="12" rx="3.5" fill="none" stroke="currentColor" stroke-width="1.6" opacity=".7"/><rect x="25" y="4.5" width="2" height="5" rx="1" fill="currentColor" opacity=".7"/><rect x="3" y="3" width="${Math.max(1, Math.round(19 * pct / 100))}" height="8" rx="2" fill="${charging ? GREEN : pct <= 15 ? RED : "currentColor"}"/></svg>`;

const BASE_CSS = `
:host{display:block;font-family:${FONT};color:var(--o-ink,#111113);-webkit-font-smoothing:antialiased;font-feature-settings:"tnum" 1,"cv11" 1}
*{box-sizing:border-box}
button{font:inherit;color:inherit;background:none;border:0;padding:0;cursor:pointer;-webkit-tap-highlight-color:transparent;text-align:left}
.card{background:var(--o-card,#fff);border-radius:26px;box-shadow:var(--o-shadow,0 1px 2px rgba(0,0,0,.04));position:relative;overflow:hidden}
.mute{color:var(--o-mute,#8a8a90)}
svg{display:block}`;

/* ---- data ------------------------------------------------------------- */
const startOfDay = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };
const tsOf = (r) => { const ts = r.lu ?? r.last_updated ?? r.lc; return typeof ts === "number" ? ts * 1000 : new Date(ts).getTime(); };

async function fetchHistory(hass, entity, days = 7) {
  const end = Date.now(), start = end - days * DAY;
  const res = await hass.callWS({ type: "history/history_during_period", start_time: new Date(start).toISOString(), end_time: new Date(end).toISOString(),
    entity_ids: [entity], minimal_response: true, no_attributes: true, significant_changes_only: false });
  return res[entity] || [];
}
/** [{t, n}] wake-ups from the counter history. */
async function loadWakes(hass, entity) {
  const out = []; let prev = null;
  for (const r of await fetchHistory(hass, entity)) {
    const v = parseFloat(r.s ?? r.state); if (!Number.isFinite(v)) continue;
    if (prev != null && v > prev) out.push({ t: tsOf(r), n: v - prev });
    prev = v;
  }
  return out;
}
/** [{t, on}] display awake/dimmed changes from the presence sensor history (first state is only the baseline). */
async function loadScreenLog(hass, entity) {
  const out = []; let prev = null;
  for (const r of await fetchHistory(hass, entity)) {
    const s = r.s ?? r.state; if (s !== "on" && s !== "off") continue;
    if (prev != null && s !== prev) out.push({ t: tsOf(r), on: s === "on" });
    prev = s;
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
const num = (s) => { const v = parseFloat(s?.state); return Number.isFinite(v) ? v : null; };

class KBase extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: "open" }); this._cfg = null; this._hass = null; this._ev = []; this._screen = []; this._sig = null; }
  setConfig(c) { if (!c.entity) throw new Error("entity is required"); this._cfg = { ...c }; this._build(); this._draw(); }
  set hass(h) {
    this._hass = h;
    if (!this._cfg) return;
    const c = this._cfg, st = (id) => h.states?.[id]?.state;
    const sig = [c.entity, c.presence, c.battery, c.charging, c.temp, c.camera].map(st).join("|");
    if (sig !== this._sig) { this._sig = sig; this._load(); this._draw(); }
  }
  getGridOptions() { return { columns: 12, min_columns: 6 }; }
  connectedCallback() { this._timer = setInterval(() => this._load(), 5 * 60e3); if (this._hass) this._load(); }
  disconnectedCallback() { clearInterval(this._timer); }
  st(id) { return id ? this._hass?.states?.[id] : undefined; }
  async _load() {
    if (!this._hass || !this._cfg) return;
    try {
      this._ev = await loadWakes(this._hass, this._cfg.entity);
      if (this._cfg.presence) this._screen = await loadScreenLog(this._hass, this._cfg.presence);
      this._draw();
    } catch (e) { /* recorder not ready */ }
  }
  /** {awake, since} of the display, null if unknown. */
  screen() {
    const s = this.st(this._cfg.presence);
    if (!s || (s.state !== "on" && s.state !== "off")) return null;
    return { awake: s.state === "on", since: new Date(s.last_changed).getTime() };
  }
  battery() {
    const lvl = num(this.st(this._cfg.battery));
    if (lvl == null) return null;
    return { level: Math.round(lvl), charging: this.st(this._cfg.charging)?.state === "on", temp: num(this.st(this._cfg.temp)) };
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
      .pills{display:flex;gap:8px;margin-top:7px;flex-wrap:wrap}
      .pill{display:flex;align-items:center;gap:6px;padding:3px 10px 3px 8px;border-radius:12px;background:var(--o-soft,#f0f0f3);font-size:11.5px;font-weight:600}
      .dot{width:8px;height:8px;border-radius:50%;background:var(--o-mute,#8a8a90)}
      .dot.on{background:${GREEN};box-shadow:0 0 0 3px rgba(48,209,88,.18)}
      .pill .b{font-size:13px;display:flex;align-items:center;gap:3px}
      .pill .c{color:${GREEN};font-size:11px}
      .mini{margin-left:auto;display:flex;align-items:flex-end;gap:2px;height:38px}
      .camb{width:42px;height:42px;border-radius:50%;display:grid;place-items:center;font-size:19px;background:rgba(10,132,255,.14);color:#0a84ff;flex:none;margin-left:6px}
      .camb:active{transform:scale(.92)}
      .mini i{display:block;width:4px;border-radius:2px;background:${ORANGE};min-height:2px;opacity:.9}
      .mini i.z{background:var(--o-line,rgba(0,0,0,.1));opacity:1}
      .card:active{transform:scale(.985)}
      .txt{min-width:0}
      .camb{margin-left:auto}
      .mini + .camb{margin-left:6px}
      @media (max-width:600px){
        .card{padding:12px 14px;gap:12px}
        .ic{width:40px;height:40px;font-size:19px}
        .n{font-size:28px}
        .mini{display:none}      /* the bars do not fit next to the camera button on a phone */
        .camb{margin-left:auto;width:40px;height:40px}
        .pills{margin-top:5px}
      }
    </style>
    <button class="card"><div class="ic">${ICON}</div><div class="txt"><div class="l"></div><div class="n">–</div><div class="s"></div><div class="pills"></div></div><div class="mini"></div><span class="camb" role="button" aria-label="Kamerakép" title="Kamerakép">${CAM_ICON}</span></button>`;
    const open = (hash) => { window.history.pushState(null, "", hash); window.dispatchEvent(new CustomEvent("location-changed")); };
    this.shadowRoot.querySelector(".card").addEventListener("click", () => open(this._cfg.hash || "#wakes"));
    // The camera button opens the camera pop-up directly, without the statistics pop-up.
    this.shadowRoot.querySelector(".camb").addEventListener("click", (e) => { e.stopPropagation(); open(this._cfg.camera_hash || "#tabletcam"); });
  }
  _draw() {
    const r = this.shadowRoot; if (!r.querySelector(".n")) return;
    const today = startOfDay(Date.now());
    const todayN = sum(this._ev.filter((e) => e.t >= today));
    const total = parseFloat(this.st(this._cfg.entity)?.state);
    r.querySelector(".l").textContent = this._cfg.name || "Tablet";
    r.querySelector(".n").textContent = String(todayN);
    r.querySelector(".s").textContent = `ébredés ma${Number.isFinite(total) ? ` · ${total} összesen` : ""}`;
    const scr = this.screen(), bat = this.battery();
    r.querySelector(".pills").innerHTML =
      (scr ? `<div class="pill"><span class="dot${scr.awake ? " on" : ""}"></span>${scr.awake ? "Ébren" : "Alszik"} <span class="mute" style="font-weight:500">${hhmm(scr.since)}</span></div>` : "") +
      (bat ? `<div class="pill"><span class="b">${BATT(bat.level, bat.charging)}</span>${bat.level} %${bat.charging ? ` <span class="c">${BOLT}</span>` : ""}</div>` : "");
    const b = hourBuckets(this._ev), max = Math.max(1, ...b.map((x) => x.n));
    r.querySelector(".mini").innerHTML = b.map((x) => `<i class="${x.n ? "" : "z"}" style="height:${x.n ? 8 + (x.n / max) * 30 : 2}px"></i>`).join("");
    // show the camera button only when the camera entity exists
    r.querySelector(".camb").style.display = this._cfg.camera && this.st(this._cfg.camera)?.attributes?.access_token ? "" : "none";
  }
}
if (!customElements.get("kiosk-wake-tile")) customElements.define("kiosk-wake-tile", KWakeTile);

/* ================================================================== chart / modal */
class KWakeChart extends KBase {
  _build() {
    this._mode = "h"; this._sel = null; this._camOpen = false;
    this.shadowRoot.innerHTML = `<style>${BASE_CSS}
      .card{padding:18px 20px 16px;box-shadow:none;background:transparent}
      .stat{display:grid;grid-template-columns:1fr 1fr;gap:10px}
      .box{background:var(--o-soft,#f0f0f3);border-radius:18px;padding:12px 14px}
      .box .k{font-size:11.5px;font-weight:600;letter-spacing:.06em;text-transform:uppercase;color:var(--o-mute,#8a8a90)}
      .box .v{font-size:22px;font-weight:600;margin-top:4px;display:flex;align-items:center;gap:8px}
      .box .d{font-size:12.5px;color:var(--o-mute,#8a8a90);margin-top:2px}
      .dot{width:10px;height:10px;border-radius:50%;background:var(--o-mute,#8a8a90);flex:none}
      .dot.on{background:${GREEN};box-shadow:0 0 0 4px rgba(48,209,88,.18)}
      .row{display:flex;align-items:center;justify-content:space-between;margin-top:18px}
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
      .sec{font-size:12.5px;font-weight:600;letter-spacing:.07em;text-transform:uppercase;color:var(--o-mute,#8a8a90);margin:20px 0 8px}
      .list{display:flex;flex-direction:column}
      .li{display:flex;align-items:center;gap:10px;padding:9px 0;border-top:1px solid var(--o-line,rgba(0,0,0,.08));font-size:14px}
      .li:first-child{border-top:0}
      .li .d{width:8px;height:8px;border-radius:50%;background:${ORANGE}}
      .li .d.off{background:var(--o-mute,#8a8a90)}
      .li .t{font-weight:600}.li .w{margin-left:auto;color:var(--o-mute,#8a8a90);font-size:12.5px}
      .none{color:var(--o-mute,#8a8a90);font-size:13px;padding:6px 0}
      .cam{margin-top:6px}
      .camBtn{width:100%;padding:13px 16px;border-radius:16px;background:var(--o-soft,#f0f0f3);font-size:14px;font-weight:600;text-align:center}
      .camBtn.stop{background:rgba(255,69,58,.14);color:${RED}}
      .camBox{margin-top:10px;border-radius:18px;overflow:hidden;background:#000;aspect-ratio:4/3;display:grid;place-items:center}
      .camBox img{width:100%;height:100%;object-fit:contain;display:block}
      .camNote{font-size:12px;color:var(--o-mute,#8a8a90);margin-top:8px}
    </style>
    <div class="card">
      <div class="stat"></div>
      <div class="row"><div class="big"><span class="tot">0</span><small class="cap"></small></div>
        <div class="seg"><button data-m="h" class="on">24 óra</button><button data-m="d">7 nap</button></div></div>
      <div class="read"></div>
      <div class="bars"></div><div class="ax"></div>
      <div class="sec">Ébredések és elsötétülések</div><div class="log list"></div>
      <div class="sec cam-sec">Kamera</div><div class="cam"></div>
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
    r.querySelector(".cam").addEventListener("click", (e) => {
      if (e.target.closest(".camBtn")) { this._camOpen ? this._stopCam() : this._startCam(); }
    });
  }
  disconnectedCallback() { super.disconnectedCallback(); this._stopCam(); }

  _camUrl() {
    const id = this._cfg.camera, s = this.st(id);
    return s && s.attributes.access_token ? `/api/camera_proxy_stream/${id}?token=${s.attributes.access_token}` : null;
  }
  _startCam() {
    if (!this._camUrl()) return;
    this._camOpen = true; this._drawCam();
    // Stop streaming when the modal is closed (not visible) or after 3 minutes.
    this._camGuard = setInterval(() => { if (!this.isConnected || this.offsetParent === null) this._stopCam(); }, 1000);
    this._camTimeout = setTimeout(() => this._stopCam(), 3 * 60e3);
  }
  _stopCam() {
    clearInterval(this._camGuard); clearTimeout(this._camTimeout);
    if (!this._camOpen) return;
    this._camOpen = false; this._drawCam();
  }
  _drawCam() {
    const el = this.shadowRoot.querySelector(".cam"); if (!el) return;
    const sec = this.shadowRoot.querySelector(".cam-sec");
    if (!this._cfg.camera) { sec.style.display = "none"; el.innerHTML = ""; return; }
    const url = this._camUrl();
    if (!url) { el.innerHTML = `<div class="none">A kamera nincs bekapcsolva a tableten (Beállítások → Kamerakép megosztása), vagy a Home Assistant entitás még nem készült el.</div>`; return; }
    if (!this._camOpen) {
      el.innerHTML = `<button class="camBtn">Kamerakép megnyitása</button><div class="camNote">Csak a megnyitás idejére streamel, bezáráskor leáll.</div>`;
    } else {
      const deg = ((parseInt(this._cfg.camera_rotate) || 0) % 360 + 360) % 360;
      const sideways = deg === 90 || deg === 270;
      // sideways images are rotated inside a 4:3 box, so shrink them to fit instead of being cropped
      const tf = deg ? `transform:rotate(${deg}deg)${sideways ? " scale(.75)" : ""};` : "";
      el.innerHTML = `<button class="camBtn stop">Bezárás</button><div class="camBox"><img alt="Kamera" style="${tf}" src="${url}"></div>`;
    }
  }

  _draw() {
    const r = this.shadowRoot; if (!r.querySelector(".bars")) return;
    // --- status boxes
    const scr = this.screen(), bat = this.battery();
    const boxes = [];
    if (scr) boxes.push(`<div class="box"><div class="k">Képernyő</div><div class="v"><span class="dot${scr.awake ? " on" : ""}"></span>${scr.awake ? "Ébren" : "Alszik"}</div><div class="d">${dayLabel(scr.since)} ${hhmm(scr.since)} óta</div></div>`);
    if (bat) boxes.push(`<div class="box"><div class="k">Akkumulátor</div><div class="v">${BATT(bat.level, bat.charging)} ${bat.level} %${bat.charging ? ` <span style="color:${GREEN};font-size:15px">${BOLT}</span>` : ""}</div><div class="d">${bat.charging ? "Töltőn van" : "Nincs töltőn"}${bat.temp != null ? ` · ${bat.temp.toFixed(1).replace(".", ",")} °C` : ""}</div></div>`);
    r.querySelector(".stat").innerHTML = boxes.join("");
    r.querySelector(".stat").style.display = boxes.length ? "grid" : "none";
    // --- wake bars
    const hourly = this._mode === "h";
    const b = hourly ? hourBuckets(this._ev) : dayBuckets(this._ev);
    const max = Math.max(1, ...b.map((x) => x.n));
    r.querySelector(".tot").textContent = String(sum(b.map((x) => ({ n: x.n }))));
    r.querySelector(".cap").textContent = hourly ? "ébredés az elmúlt 24 órában" : "ébredés az elmúlt 7 napban";
    r.querySelector(".bars").style.setProperty("--gap", hourly ? "4px" : "12px");
    r.querySelector(".ax").style.setProperty("--gap", hourly ? "4px" : "12px");
    r.querySelector(".bars").innerHTML = b.map((x, i) =>
      `<div class="b${x.n ? "" : " z"}${this._sel === i ? " sel" : ""}" data-i="${i}">${x.n ? `<em>${x.n}</em>` : ""}<i style="height:${x.n ? 8 + (x.n / max) * 118 : 3}px"></i></div>`).join("");
    r.querySelector(".ax").innerHTML = b.map((x) => {
      const d = new Date(x.t);
      return `<span>${hourly ? (d.getHours() % 3 === 0 ? pad(d.getHours()) : "") : d.toLocaleDateString("hu-HU", { weekday: "short" })}</span>`;
    }).join("");
    const read = r.querySelector(".read");
    if (this._sel != null && b[this._sel]) {
      const x = b[this._sel], d = new Date(x.t);
      read.textContent = hourly ? `${pad(d.getHours())}:00–${pad((d.getHours() + 1) % 24)}:00 · ${x.n} ébredés`
        : `${d.toLocaleDateString("hu-HU", { month: "long", day: "numeric", weekday: "long" })} · ${x.n} ébredés`;
    } else read.textContent = "Koppints egy oszlopra a részletekért";
    // --- wake / dim log (screen state changes; falls back to the counter when there is no presence sensor)
    const log = this._cfg.presence
      ? [...this._screen].sort((a, c) => c.t - a.t).slice(0, 10).map((e) => ({ t: e.t, on: e.on, label: e.on ? "Felébredt" : "Elsötétült" }))
      : [...this._ev].sort((a, c) => c.t - a.t).slice(0, 10).map((e) => ({ t: e.t, on: true, label: e.n > 1 ? `Felébredt (${e.n}×)` : "Felébredt" }));
    r.querySelector(".log").innerHTML = log.length ? log.map((e) =>
      `<div class="li"><span class="d${e.on ? "" : " off"}"></span><span class="t">${e.label}</span><span class="w">${dayLabel(e.t)} ${hhmm(e.t)}</span></div>`).join("")
      : `<div class="none">Még nincs rögzített esemény.</div>`;
    if (!this._camOpen) this._drawCam();
  }
}
if (!customElements.get("kiosk-wake-chart")) customElements.define("kiosk-wake-chart", KWakeChart);

/* ================================================================== camera tile + viewer */

/** Small tile that opens the camera pop-up (config: camera, name, hash). The camera is NOT touched until the pop-up opens. */
class KCameraTile extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: "open" }); this._cfg = null; this._hass = null; }
  setConfig(c) { if (!c.camera) throw new Error("camera is required"); this._cfg = { ...c }; this._build(); this._draw(); }
  set hass(h) { this._hass = h; this._draw(); }
  getGridOptions() { return { columns: 12, min_columns: 6 }; }
  _build() {
    this.shadowRoot.innerHTML = `<style>${BASE_CSS}
      .card{padding:16px 18px;display:flex;align-items:center;gap:14px;width:100%}
      .ic{width:44px;height:44px;border-radius:50%;display:grid;place-items:center;font-size:21px;background:rgba(10,132,255,.14);color:#0a84ff;flex:none}
      .t{font-size:16px;font-weight:650}
      .s{font-size:12.5px;color:var(--o-mute,#8a8a90);margin-top:2px}
      .go{margin-left:auto;font-size:13px;font-weight:600;color:var(--o-mute,#8a8a90)}
      .card:active{transform:scale(.985)}
    </style>
    <button class="card"><div class="ic">${CAM_ICON}</div><div><div class="t"></div><div class="s"></div></div><div class="go">Megnyitás ›</div></button>`;
    this.shadowRoot.querySelector(".card").addEventListener("click", () => {
      window.history.pushState(null, "", this._cfg.hash || "#tabletcam");
      window.dispatchEvent(new CustomEvent("location-changed"));
    });
  }
  _draw() {
    const r = this.shadowRoot; if (!r.querySelector(".t")) return;
    const ok = !!this._hass?.states?.[this._cfg.camera]?.attributes?.access_token;
    r.querySelector(".t").textContent = this._cfg.name || "Tablet kamera";
    r.querySelector(".s").textContent = ok ? "Élő kép, csak a megnyitás idejére" : "Nincs bekapcsolva a tableten";
    r.querySelector(".go").style.display = ok ? "" : "none";
  }
}
if (!customElements.get("kiosk-camera-tile")) customElements.define("kiosk-camera-tile", KCameraTile);

/** Camera view for the pop-up: streams only while it is actually visible (config: camera, camera_rotate). */
class KCameraView extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: "open" }); this._cfg = null; this._hass = null; this._visible = false; this._active = false; }
  setConfig(c) { if (!c.camera) throw new Error("camera is required"); this._cfg = { ...c }; this._build(); this._sync(); }
  set hass(h) { this._hass = h; this._sync(); }
  getGridOptions() { return { columns: 12, min_columns: 6 }; }
  connectedCallback() {
    this._io = new IntersectionObserver((entries) => { this._visible = entries.some((e) => e.isIntersecting); this._sync(); }, { threshold: 0.1 });
    this._io.observe(this);
  }
  disconnectedCallback() { this._io?.disconnect(); this._visible = false; this._stop(true); }
  _build() {
    this.shadowRoot.innerHTML = `<style>${BASE_CSS}
      .wrap{padding:6px 4px 4px}
      .box{position:relative;border-radius:20px;overflow:hidden;background:#000;aspect-ratio:4/3;display:grid;place-items:center}
      .box img{width:100%;height:100%;object-fit:contain;display:block}
      .live{position:absolute;top:12px;left:12px;display:flex;align-items:center;gap:7px;padding:5px 11px 5px 9px;border-radius:14px;background:rgba(0,0,0,.55);color:#fff;font-size:12.5px;font-weight:700}
      .live i{width:8px;height:8px;border-radius:50%;background:#ff453a;animation:p 1.4s ease-in-out infinite}
      @keyframes p{50%{opacity:.35}}
      .msg{color:#fff;font-size:14px;text-align:center;padding:20px}
      .resume{margin-top:12px;display:inline-block;padding:10px 22px;border-radius:18px;background:rgba(255,255,255,.16);font-weight:600;cursor:pointer}
      .note{font-size:12px;color:var(--o-mute,#8a8a90);margin:10px 4px 2px}
    </style><div class="wrap"><div class="box"></div><div class="note"></div></div>`;
    this.shadowRoot.querySelector(".box").addEventListener("click", (e) => { if (e.target.closest(".resume")) { this._start(); } });
  }
  _url() {
    const id = this._cfg?.camera, a = this._hass?.states?.[id]?.attributes;
    return a?.access_token ? `/api/camera_proxy_stream/${id}?token=${a.access_token}` : null;
  }
  _sync() {
    if (!this._cfg || !this.shadowRoot.querySelector(".box")) return;
    if (this._visible && !this._active && !this._expired) this._start();
    else if (!this._visible && this._active) this._stop();
    else if (!this._visible) { this._expired = false; this._paint(); }
    else if (this._visible && !this._active && !this._expired) this._paint();
  }
  _start() {
    if (!this._url()) { this._paint(); return; }
    this._active = true; this._expired = false; this._paint();
    clearTimeout(this._timeout);
    this._timeout = setTimeout(() => { this._expired = true; this._stop(); }, 3 * 60e3);
  }
  _stop(silent) {
    clearTimeout(this._timeout);
    this._active = false;
    if (!silent) this._paint();
  }
  _paint() {
    const box = this.shadowRoot.querySelector(".box"), note = this.shadowRoot.querySelector(".note");
    if (!box) return;
    if (this._active) {
      const deg = ((parseInt(this._cfg.camera_rotate) || 0) % 360 + 360) % 360, sideways = deg === 90 || deg === 270;
      const tf = deg ? `transform:rotate(${deg}deg)${sideways ? " scale(.75)" : ""};` : "";
      const cur = box.querySelector("img");
      if (!cur) box.innerHTML = `<img alt="Tablet kamera" style="${tf}" src="${this._url()}"><div class="live"><i></i>Élő</div>`;
      note.textContent = "A kép csak addig megy, amíg ez az ablak nyitva van (legfeljebb 3 percig).";
    } else {
      box.innerHTML = !this._url()
        ? `<div class="msg">A kamera nincs bekapcsolva a tableten (Beállítások → Kamerakép megosztása).</div>`
        : this._expired ? `<div class="msg">A kép 3 perc után leállt.<br><span class="resume">Folytatás</span></div>` : `<div class="msg">…</div>`;
      note.textContent = "";
    }
  }
}
if (!customElements.get("kiosk-camera-view")) customElements.define("kiosk-camera-view", KCameraView);

window.customCards = window.customCards || [];
window.customCards.push({ type: "kiosk-wake-tile", name: "Kiosk wake tile", description: "Wake-ups, screen state, battery" }, { type: "kiosk-wake-chart", name: "Kiosk wake chart", description: "Wake chart, screen log, camera" }, { type: "kiosk-camera-tile", name: "Kiosk camera tile", description: "Opens the tablet camera pop-up" }, { type: "kiosk-camera-view", name: "Kiosk camera view", description: "Streams only while visible" });
