/* kiosk-cards-v2 — "ambient" weather card. Clean glass-on-gradient look.
 *   kiosk2-weather : live backdrop (condition + day/night), hero, 24 h chart, 7 day range bars
 * The dock card (kiosk-dock) comes from kiosk-cards.js (v1) and is reused as-is.
 * Perf notes: no blur/backdrop-filter; animations only use transform/opacity. */
const FONT = `"Inter","SF Pro Display",-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,system-ui,sans-serif`;
const BLUE = "#0a84ff", ORANGE = "#ff9f0a";

/* ------------------------------------------------------------------ backdrops */
const BACKDROPS = {
  night:  ["#04070f", "#0d1738", "#1c2a5c"],
  clear:  ["#0d2b5c", "#1a5aa8", "#3b8fe0"],
  partly: ["#102a4f", "#235a8e", "#4f86b8"],
  cloudy: ["#101a2b", "#22344f", "#3a5273"],
  fog:    ["#151a21", "#2c3744", "#52606f"],
  rain:   ["#0a1726", "#163450", "#26587d"],
  snow:   ["#172636", "#33495f", "#5f7a94"],
  storm:  ["#100c20", "#241c44", "#443775"],
};
const kindOf = (cond, night) => {
  if (["lightning", "lightning-rainy"].includes(cond)) return "storm";
  if (["rainy", "pouring"].includes(cond)) return "rain";
  if (["snowy", "snowy-rainy", "hail"].includes(cond)) return "snow";
  if (cond === "fog") return "fog";
  if (night) return cond === "cloudy" ? "cloudy" : "night";
  if (cond === "sunny" || cond === "clear-night") return "clear";
  if (cond === "partlycloudy") return "partly";
  return "cloudy";
};
const THEME = {
  "--o-card": "rgba(255,255,255,.07)", "--o-ink": "#ffffff", "--o-mute": "rgba(235,235,245,.62)", "--o-soft": "rgba(255,255,255,.08)",
  "--o-line": "rgba(255,255,255,.12)", "--o-shadow": "none", "--o-shade": "rgba(255,255,255,.12)", "--o-bg": "#05070d", "--k-hum": "#8fcbff",
};
const deepFind = (root, tag) => {
  for (const el of root.querySelectorAll("*")) {
    if (el.localName === tag) return el;
    if (el.shadowRoot) { const r = deepFind(el.shadowRoot, tag); if (r) return r; }
  }
  return null;
};
const Panel = {
  el: null,
  root() { const p = Panel.el; return p && p.shadowRoot ? p.shadowRoot.querySelector("hui-root") : null; },
  get() { return Panel.el && Panel.el.isConnected ? Panel.el : (Panel.el = deepFind(document.querySelector("home-assistant")?.shadowRoot || document, "ha-panel-lovelace")); },
  apply(kind) {
    const p = Panel.get(); if (!p) return false;
    for (const [k, v] of Object.entries(THEME)) p.style.setProperty(k, v);
    const solid = "#1c1e24"; // popups / dialogs must stay opaque
    const map = { "--primary-background-color": "transparent", "--lovelace-background": "transparent", "--card-background-color": solid,
      "--primary-text-color": "#fff", "--secondary-text-color": THEME["--o-mute"], "--divider-color": THEME["--o-line"],
      "--app-header-background-color": "transparent", "--app-header-text-color": "#fff", "--secondary-background-color": "#2a2d34" };
    for (const [k, v] of Object.entries(map)) p.style.setProperty(k, v);
    const [a, b, c] = BACKDROPS[kind] || BACKDROPS.cloudy;
    // ha-panel-lovelace is display:inline, so the backdrop goes on hui-root (made a block)
    const root = Panel.root();
    if (root) {
      root.style.display = "block"; root.style.minHeight = "100vh";
      root.style.background = `linear-gradient(165deg, ${a} 0%, ${b} 52%, ${c} 100%)`;
    }
    return true;
  },
  ensure(kind) { let n = 0; const tick = () => { if (!Panel.apply(kind) && n++ < 40) setTimeout(tick, 150); }; tick(); },
  clear() {
    const p = Panel.el, root = Panel.root();
    if (root) ["display", "minHeight", "background"].forEach((k) => (root.style[k] = ""));
    if (p) [...p.style].forEach((k) => p.style.removeProperty(k));
    Panel.el = null;
  },
};

/* ------------------------------------------------------------------ helpers */
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const pad = (n) => String(n).padStart(2, "0");
const deg = (v) => (v == null ? "–" : `${Math.round(v)}°`);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const MDI = { "clear-night": "weather-night", cloudy: "weather-cloudy", exceptional: "alert-circle-outline", fog: "weather-fog", hail: "weather-hail", lightning: "weather-lightning",
  "lightning-rainy": "weather-lightning-rainy", partlycloudy: "weather-partly-cloudy", pouring: "weather-pouring", rainy: "weather-rainy", snowy: "weather-snowy",
  "snowy-rainy": "weather-snowy-rainy", sunny: "weather-sunny", windy: "weather-windy", "windy-variant": "weather-windy-variant" };
const TINT = { sunny: "#ffb020", "clear-night": "#cfd2ff", partlycloudy: "#ffc15a", cloudy: "#c9ced8", fog: "#aab0ba", rainy: "#5ab0ff", pouring: "#3d9bff",
  "lightning-rainy": "#ffd60a", lightning: "#ffd60a", snowy: "#9fe3ff", "snowy-rainy": "#9fe3ff", hail: "#9fe3ff", windy: "#c9ced8", "windy-variant": "#c9ced8", exceptional: "#ff453a" };
const HU = { "clear-night": "Tiszta éjszaka", cloudy: "Felhős", exceptional: "Rendkívüli", fog: "Köd", hail: "Jégeső", lightning: "Villámlás", "lightning-rainy": "Zivatar",
  partlycloudy: "Részben felhős", pouring: "Zápor", rainy: "Eső", snowy: "Havazás", "snowy-rainy": "Havas eső", sunny: "Napos", windy: "Szeles", "windy-variant": "Szeles" };
const wIcon = (c, size, glow) => `<ha-icon icon="mdi:${MDI[c] || "weather-cloudy"}" style="--mdc-icon-size:${size}px;color:${TINT[c] || "#c9ced8"};${glow ? `filter:drop-shadow(0 0 18px ${TINT[c] || "#fff"}66)` : ""}"></ha-icon>`;
/* cold -> warm */
const STOPS = [[-5, [90, 169, 255]], [5, [100, 210, 255]], [13, [150, 226, 214]], [19, [255, 209, 102]], [26, [255, 159, 10]], [34, [255, 90, 60]]];
const tempColor = (t) => {
  if (t <= STOPS[0][0]) return `rgb(${STOPS[0][1]})`;
  for (let i = 1; i < STOPS.length; i++) {
    if (t <= STOPS[i][0]) { const [t0, c0] = STOPS[i - 1], [t1, c1] = STOPS[i], k = (t - t0) / (t1 - t0); return `rgb(${c0.map((v, j) => Math.round(v + (c1[j] - v) * k))})`; }
  }
  return `rgb(${STOPS[STOPS.length - 1][1]})`;
};
const ic = (d) => `<svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`;
const ICON = {
  thermo: ic('<path d="M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z"/>'),
  drop: ic('<path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"/>'),
  wind: ic('<path d="M17.7 7.7a2.5 2.5 0 1 1 1.8 4.3H2"/><path d="M9.6 4.6A2 2 0 1 1 11 8H2"/><path d="M12.6 19.4A2 2 0 1 0 14 16H2"/>'),
  sunrise: ic('<path d="M12 2v6M4.93 10.93l1.41 1.41M2 18h2M20 18h2M19.07 10.93l-1.41 1.41M22 22H2M8 6l4-4 4 4M16 18a4 4 0 0 0-8 0"/>'),
  sunset: ic('<path d="M12 10V2M4.93 10.93l1.41 1.41M2 18h2M20 18h2M19.07 10.93l-1.41 1.41M22 22H2M16 6l-4 4-4-4M16 18a4 4 0 0 0-8 0"/>'),
};
const smoothPath = (P) => {
  let d = `M${P[0][0].toFixed(2)},${P[0][1].toFixed(2)}`;
  for (let i = 0; i < P.length - 1; i++) {
    const p0 = P[i - 1] || P[i], p1 = P[i], p2 = P[i + 1], p3 = P[i + 2] || p2;
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, clamp(p1[1] + (p2[1] - p0[1]) / 6, 0, 100)];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, clamp(p2[1] - (p3[1] - p1[1]) / 6, 0, 100)];
    d += ` C${c1[0].toFixed(2)},${c1[1].toFixed(2)} ${c2[0].toFixed(2)},${c2[1].toFixed(2)} ${p2[0].toFixed(2)},${p2[1].toFixed(2)}`;
  }
  return d;
};

/* ambient animated layer (cheap: a handful of transform/opacity animations) */
const rnd = (() => { let s = 7; return () => ((s = (s * 16807) % 2147483647) / 2147483647); })();
const fxHtml = (kind) => {
  let h = "";
  if (kind === "rain" || kind === "storm") {
    for (let i = 0; i < 26; i++) h += `<i class="rd" style="left:${(rnd() * 100).toFixed(1)}%;animation-delay:-${(rnd() * 1.6).toFixed(2)}s;animation-duration:${(0.9 + rnd() * 0.7).toFixed(2)}s;opacity:${(0.25 + rnd() * 0.45).toFixed(2)}"></i>`;
    if (kind === "storm") h += `<i class="flash"></i>`;
  } else if (kind === "snow") {
    for (let i = 0; i < 22; i++) h += `<i class="sn" style="left:${(rnd() * 100).toFixed(1)}%;animation-delay:-${(rnd() * 9).toFixed(2)}s;animation-duration:${(6 + rnd() * 6).toFixed(1)}s;width:${(3 + rnd() * 4).toFixed(1)}px;height:${(3 + rnd() * 4).toFixed(1)}px"></i>`;
  } else if (kind === "night") {
    for (let i = 0; i < 34; i++) h += `<i class="st" style="left:${(rnd() * 100).toFixed(1)}%;top:${(rnd() * 88).toFixed(1)}%;animation-delay:-${(rnd() * 5).toFixed(2)}s;animation-duration:${(2.5 + rnd() * 3).toFixed(1)}s"></i>`;
  } else if (kind === "clear" || kind === "partly") {
    h += `<i class="sun"></i>`;
  }
  if (["cloudy", "partly", "fog", "rain", "storm", "snow"].includes(kind)) {
    h += `<i class="cl c1"></i><i class="cl c2"></i>`;
  }
  return h;
};

class KWeather2 extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: "open" }); this._cfg = null; this._hass = null; this._sig = null; this._kind = null; }
  setConfig(c) {
    this._cfg = { hours: 24, days: 7, sun: "sun.sun", ...c };
    this._sig = null; this._kind = null;       // skeleton is rebuilt: force a full redraw
    this._build(); this._clock();
    if (this._hourly) this._drawHourly();
    if (this._daily) this._drawDaily();
    if (this._hass) this._update();
  }
  set hass(h) { this._hass = h; if (this._cfg) this._update(); }
  getGridOptions() { return { columns: "full" }; }
  getCardSize() { return 9; }
  st(id) { return id ? this._hass?.states?.[id] : undefined; }

  _build() {
    this.shadowRoot.innerHTML = `<style>
      :host{display:block;font-family:${FONT};color:#fff;-webkit-font-smoothing:antialiased;font-feature-settings:"tnum" 1,"cv11" 1}
      *{box-sizing:border-box} ha-icon{display:block}
      .card{position:relative;overflow:hidden;border-radius:30px;background:var(--o-card);border:1px solid rgba(255,255,255,.09);padding:12px 22px 12px}
      /* ambient layer */
      .fx{position:absolute;inset:0;pointer-events:none;overflow:hidden;border-radius:30px}
      .fx i{position:absolute;display:block;will-change:transform,opacity}
      .rd{top:-12%;width:1.5px;height:46px;background:linear-gradient(180deg,rgba(160,210,255,0),rgba(160,210,255,.85));transform:rotate(12deg);animation:fall linear infinite}
      @keyframes fall{from{transform:translate3d(0,-60px,0) rotate(12deg)}to{transform:translate3d(-60px,330px,0) rotate(12deg)}}
      .sn{top:-4%;border-radius:50%;background:rgba(255,255,255,.85);animation:snow linear infinite}
      @keyframes snow{from{transform:translate3d(0,-10px,0)}50%{transform:translate3d(18px,170px,0)}to{transform:translate3d(-8px,340px,0)}}
      .st{width:2px;height:2px;border-radius:50%;background:#fff;animation:tw ease-in-out infinite}
      @keyframes tw{0%,100%{opacity:.15}50%{opacity:.95}}
      .sun{left:-4%;top:-26%;width:560px;height:560px;border-radius:50%;background:radial-gradient(circle,rgba(255,196,90,.42) 0%,rgba(255,170,60,.16) 38%,rgba(255,160,40,0) 68%);animation:breathe 9s ease-in-out infinite}
      @keyframes breathe{0%,100%{transform:scale(1);opacity:.9}50%{transform:scale(1.07);opacity:1}}
      .cl{width:640px;height:240px;border-radius:50%;background:radial-gradient(ellipse,rgba(255,255,255,.12) 0%,rgba(255,255,255,0) 66%)}
      .c1{top:-34%;left:-8%;animation:drift 46s linear infinite alternate}
      .c2{top:-10%;right:-16%;animation:drift 62s linear infinite alternate-reverse;opacity:.8}
      @keyframes drift{from{transform:translate3d(-60px,0,0)}to{transform:translate3d(120px,0,0)}}
      .flash{inset:0;background:rgba(190,170,255,.18);opacity:0;animation:fl 9s infinite}
      @keyframes fl{0%,92%,100%{opacity:0}93%{opacity:1}95%{opacity:.1}96%{opacity:.8}98%{opacity:0}}
      .z{position:relative}
      /* hero */
      .hero{display:flex;align-items:center;gap:22px}
      .now{display:flex;align-items:center;gap:20px}
      .lbl{font-size:12px;font-weight:600;letter-spacing:.09em;text-transform:uppercase;color:var(--o-mute)}
      .temp{font-size:88px;font-weight:200;letter-spacing:-.055em;line-height:.9;margin-top:4px;background:linear-gradient(180deg,#fff 20%,rgba(255,255,255,.55));-webkit-background-clip:text;background-clip:text;color:transparent}
      .temp small{font-size:26px;font-weight:300;letter-spacing:0;vertical-align:top;margin-left:6px;position:relative;top:8px;-webkit-text-fill-color:var(--o-mute)}
      .cond{font-size:20px;font-weight:600;margin-top:2px}
      .chips{display:flex;gap:9px;margin-left:auto;flex-wrap:wrap;justify-content:flex-end;max-width:520px}
      .chip{display:flex;align-items:center;gap:9px;padding:7px 14px 7px 8px;border-radius:18px;background:var(--o-soft);border:1px solid rgba(255,255,255,.07)}
      .chip .i{width:28px;height:28px;border-radius:50%;display:grid;place-items:center;font-size:15px;background:rgba(255,255,255,.1)}
      .chip .l{font-size:10.5px;color:var(--o-mute);font-weight:500;line-height:1.2}
      .chip .v{font-size:14px;font-weight:700;line-height:1.2;white-space:nowrap}
      .clock{text-align:right;margin-left:6px}
      .clock .t{font-size:52px;font-weight:200;letter-spacing:-.04em;line-height:1}
      .clock .cd{font-size:12px;font-weight:600;letter-spacing:.08em;text-transform:uppercase;color:var(--o-mute);margin-top:4px}
      .sec{font-size:11.5px;font-weight:600;letter-spacing:.09em;text-transform:uppercase;color:var(--o-mute);margin:9px 0 3px}
      /* hourly */
      .hourly{position:relative}
      .hgrid{display:grid;grid-template-columns:repeat(var(--n),1fr)}
      .hc{display:flex;flex-direction:column;align-items:center;gap:1px;padding:4px 0 3px;border-radius:16px}
      .hc.now{background:rgba(255,255,255,.11)}
      .hh{font-size:12.5px;font-weight:600;color:var(--o-mute)} .hc.now .hh{color:#fff}
      .hd{height:13px;font-size:10.5px;font-weight:700;color:${ORANGE};text-transform:uppercase;letter-spacing:.06em}
      .hrn{height:14px;font-size:11px;font-weight:700;color:#6cb8ff}
      .chart{position:relative;height:84px;margin-top:2px}
      .chart svg{position:absolute;inset:0;width:100%;height:100%;overflow:visible}
      .pt{position:absolute;transform:translate(-50%,-100%);font-size:14.5px;font-weight:700;padding-bottom:8px;white-space:nowrap;text-shadow:0 1px 6px rgba(0,0,0,.35)}
      .mid{position:absolute;top:0;bottom:0;border-left:1.5px dotted rgba(255,255,255,.18)}
      /* days */
      .days{display:grid;grid-template-columns:repeat(var(--n),1fr);gap:9px}
      .d{display:flex;flex-direction:column;align-items:center;gap:1px;padding:6px 4px 6px;border-radius:22px;background:var(--o-soft);border:1px solid rgba(255,255,255,.06)}
      .d.today{background:rgba(255,255,255,.14);border-color:rgba(255,255,255,.28)}
      .dn{font-size:15.5px;font-weight:700;text-transform:capitalize}
      .dd{font-size:11px;color:var(--o-mute);font-weight:500;margin-bottom:2px}
      .hi{font-size:22px;font-weight:700;letter-spacing:-.02em;margin-top:1px}
      .lo{font-size:16px;font-weight:500;color:var(--o-mute)}
      .bar{position:relative;width:9px;height:42px;margin:4px 0 3px;border-radius:5px;background:rgba(255,255,255,.1)}
      .pill{position:absolute;left:0;right:0;border-radius:5px;min-height:8px}
      .dr{height:14px;font-size:11.5px;font-weight:600;color:#6cb8ff;margin-top:1px}
      @media (prefers-reduced-motion:reduce){.fx i{animation:none!important}}
    </style>
    <div class="card">
      <div class="fx"></div>
      <div class="z">
        <div class="hero"><div class="now"></div><div class="chips"></div><div class="clock"></div></div>
        <div class="sec">Következő 24 óra</div>
        <div class="hourly"></div>
        <div class="sec">Heti előrejelzés</div>
        <div class="days"></div>
      </div>
    </div>`;
    this._r = this.shadowRoot;
  }

  connectedCallback() {
    this._clockTimer = setInterval(() => this._clock(), 15e3);
    this._clock();
    if (this._hass) this._startLoad();
    if (this._kind) Panel.ensure(this._kind);
  }
  disconnectedCallback() { clearInterval(this._timer); clearInterval(this._clockTimer); this._timer = null; Panel.clear(); }
  _clock() {
    const el = this._r?.querySelector(".clock"); if (!el) return;
    const n = new Date();
    el.innerHTML = `<div class="t">${pad(n.getHours())}:${pad(n.getMinutes())}</div><div class="cd">${new Intl.DateTimeFormat("hu-HU", { weekday: "long", month: "short", day: "numeric" }).format(n)}</div>`;
  }
  _startLoad() { if (this._timer) return; this._load(); this._timer = setInterval(() => this._load(), 15 * 60e3); }
  async _fc(type) {
    const id = this._cfg.entity;
    const r = await this._hass.callWS({ type: "call_service", domain: "weather", service: "get_forecasts", service_data: { type }, target: { entity_id: id }, return_response: true });
    return (r.response?.[id] || {}).forecast || [];
  }
  async _load() {
    try { const [h, d] = await Promise.all([this._fc("hourly"), this._fc("daily")]); this._hourly = h; this._daily = d; this._drawHourly(); this._drawDaily(); this._sig = null; this._update(); }
    catch (e) { console.error("kiosk2-weather: forecast failed", e); }
  }
  /* sunrise/sunset as local minutes-of-day, from sun.sun attrs */
  _sunMins() {
    const a = this.st(this._cfg.sun)?.attributes; if (!a?.next_rising || !a?.next_setting) return null;
    const m = (iso) => { const d = new Date(iso); return d.getHours() * 60 + d.getMinutes(); };
    return { rise: m(a.next_rising), set: m(a.next_setting) };
  }
  _isNight(t) {
    const s = this._sunMins(); if (!s) return false;
    const d = new Date(t), m = d.getHours() * 60 + d.getMinutes();
    return m < s.rise || m >= s.set;
  }
  _update() {
    this._startLoad();
    const s = this.st(this._cfg.entity), a = s?.attributes || {}, cond = s?.state || "";
    const night = this.st(this._cfg.sun)?.state === "below_horizon";
    const day0 = (this._daily || [])[0];
    const kind = this._cfg.force_kind || kindOf(cond, night); // force_kind: preview/debug
    if (kind !== this._kind) { this._kind = kind; this._r.querySelector(".fx").innerHTML = fxHtml(kind); Panel.ensure(kind); }
    const sig = JSON.stringify([cond, a.temperature, a.wind_speed, a.humidity, a.pressure, this._cfg.title, day0?.temperature, day0?.templow, day0?.precipitation, night, this._sunMins()]);
    if (sig === this._sig) return; this._sig = sig;
    const title = this._cfg.title || a.friendly_name || "";
    this._r.querySelector(".now").innerHTML = `${wIcon(cond, 76, true)}<div><div class="lbl">${esc(title)} · most</div>
      <div class="temp">${a.temperature != null ? Math.round(a.temperature) : "–"}<small>°C</small></div>
      <div class="cond">${esc(HU[cond] || cond)}</div></div>`;
    const chip = (i, color, l, v) => `<div class="chip"><div class="i" style="color:${color}">${i}</div><div><div class="l">${l}</div><div class="v">${v}</div></div></div>`;
    const sm = this._sunMins(), hm = (m) => `${pad(Math.floor(m / 60))}:${pad(m % 60)}`;
    this._r.querySelector(".chips").innerHTML =
      (day0 ? chip(ICON.thermo, "#ffb020", "Ma max / min", `${deg(day0.temperature)} / ${deg(day0.templow)}`) : "") +
      (day0 ? chip(ICON.drop, "#6cb8ff", "Csapadék ma", day0.precipitation > 0 ? `${day0.precipitation.toFixed(1).replace(".", ",")} mm` : "nincs") : "") +
      (a.wind_speed != null ? chip(ICON.wind, "#9fe3ff", "Szél", `${Math.round(a.wind_speed * 3.6)} km/h`) : "") +
      (sm ? chip(night ? ICON.sunrise : ICON.sunset, night ? "#ffc15a" : "#ff9f6a", night ? "Napkelte" : "Naplemente", hm(night ? sm.rise : sm.set)) : "");
  }

  _drawHourly() {
    const el = this._r.querySelector(".hourly"), want = this._cfg.hours || 24;
    const from = Date.now() - 30 * 60e3;
    const it = (this._hourly || []).filter((f) => new Date(f.datetime).getTime() >= from).slice(0, want);
    if (!it.length) { el.innerHTML = ""; return; }
    const n = it.length, T = it.map((f) => f.temperature), lo = Math.min(...T), hi = Math.max(...T), span = Math.max(hi - lo, 3);
    const X = (i) => ((i + 0.5) / n) * 100, Y = (v) => 40 + (1 - (v - lo) / span) * 42;
    const P = it.map((f, i) => [X(i), Y(f.temperature)]);
    const d = smoothPath(P);
    // night shading: merge consecutive night hours into rects
    let rects = "", start = null;
    it.forEach((f, i) => {
      const night = this._isNight(f.datetime);
      if (night && start == null) start = i;
      if ((!night || i === n - 1) && start != null) { const end = night ? n : i; rects += `<rect x="${(start / n) * 100}" y="0" width="${((end - start) / n) * 100}" height="100" fill="rgba(6,10,36,.20)"/>`; start = null; }
    });
    const bars = it.map((f, i) => { const h = clamp((f.precipitation || 0) / 3, 0, 1) * 20; return h > 0.6 ? `<rect x="${(i / n) * 100 + 0.35}" y="${100 - h}" width="${100 / n - 0.7}" height="${h}" fill="rgba(70,160,255,.38)" rx="0.6"/>` : ""; }).join("");
    const cols = it.map((f, i) => {
      const dt = new Date(f.datetime), h = dt.getHours();
      const wd = h === 0 ? dt.toLocaleDateString("hu-HU", { weekday: "short" }) : "";
      const rain = f.precipitation > 0 ? (f.precipitation < 1 ? f.precipitation.toFixed(1) : Math.round(f.precipitation)) : "";
      return `<div class="hc${i === 0 ? " now" : ""}"><div class="hd">${wd}</div><div class="hh">${i === 0 ? "Most" : pad(h)}</div>${wIcon(f.condition, 26)}<div class="hrn">${rain}</div></div>`;
    }).join("");
    const mids = it.map((f, i) => (new Date(f.datetime).getHours() === 0 && i > 0 ? `<div class="mid" style="left:${(i / n) * 100}%"></div>` : "")).join("");
    const pts = it.map((f, i) => `<div class="pt" style="left:${P[i][0]}%;top:${P[i][1]}%;color:${tempColor(f.temperature)}">${deg(f.temperature)}</div>`).join("");
    const yHi = Y(hi), yLo = Y(lo);
    el.innerHTML = `<div class="hgrid" style="--n:${n}">${cols}</div>
      <div class="chart">${mids}<svg viewBox="0 0 100 100" preserveAspectRatio="none"><defs>
        <linearGradient id="ls" gradientUnits="userSpaceOnUse" x1="0" y1="${yHi}" x2="0" y2="${yLo}"><stop offset="0" stop-color="${tempColor(hi)}"/><stop offset="1" stop-color="${tempColor(lo)}"/></linearGradient>
        <linearGradient id="la" gradientUnits="userSpaceOnUse" x1="0" y1="${yHi}" x2="0" y2="100"><stop offset="0" stop-color="${tempColor(hi)}" stop-opacity=".30"/><stop offset="1" stop-color="${tempColor(lo)}" stop-opacity="0"/></linearGradient></defs>
        ${rects}${bars}
        <path d="${d} L${P[n - 1][0]},100 L${P[0][0]},100 Z" fill="url(#la)"/>
        <path d="${d}" fill="none" stroke="url(#ls)" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke"/></svg>${pts}</div>`;
  }

  _drawDaily() {
    const el = this._r.querySelector(".days"), it = (this._daily || []).slice(0, this._cfg.days || 7);
    el.style.setProperty("--n", it.length || 1);
    if (!it.length) { el.innerHTML = ""; return; }
    const lo = Math.min(...it.map((f) => f.templow ?? f.temperature)), hi = Math.max(...it.map((f) => f.temperature)), rng = Math.max(hi - lo, 1);
    el.innerHTML = it.map((f, i) => {
      const dt = new Date(f.datetime), l = f.templow ?? f.temperature;
      const name = i === 0 ? "Ma" : dt.toLocaleDateString("hu-HU", { weekday: "short" });
      const date = dt.toLocaleDateString("hu-HU", { month: "numeric", day: "numeric" });
      const rain = f.precipitation > 0 ? `${f.precipitation < 10 ? f.precipitation.toFixed(1) : Math.round(f.precipitation)} mm` : "";
      const top = ((hi - f.temperature) / rng) * 100, h = Math.max(((f.temperature - l) / rng) * 100, 12);
      return `<div class="d${i === 0 ? " today" : ""}"><div class="dn">${name}</div><div class="dd">${date}</div>${wIcon(f.condition, 30)}
        <div class="hi" style="color:${tempColor(f.temperature)}">${deg(f.temperature)}</div>
        <div class="bar"><div class="pill" style="top:${top}%;height:${h}%;background:linear-gradient(180deg,${tempColor(f.temperature)},${tempColor(l)})"></div></div>
        <div class="lo">${deg(l)}</div><div class="dr">${rain}</div></div>`;
    }).join("");
  }
}
if (!customElements.get("kiosk2-weather")) customElements.define("kiosk2-weather", KWeather2);
window.customCards = window.customCards || [];
window.customCards.push({ type: "kiosk2-weather", name: "Kiosk weather v2", description: "Ambient backdrop, 24h chart, range bars" });
