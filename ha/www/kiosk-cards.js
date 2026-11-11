/* Kiosk cards — flat, calm, Apple-like. Same design tokens as otthon-cards.js (--o-*).
 * Cards: kiosk-weather (current + 24 h strip + 7 day), kiosk-dock (climate sparklines, lights, TV).
 * No dependencies. */
const FONT = `"Inter","SF Pro Display",-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,system-ui,sans-serif`;
const ORANGE = "#ff9f0a", BLUE = "#0a84ff", GREEN = "#30d158";

/* ------------------------------------------------------------------ theme */
const THEMES = {
  light: { "--o-bg": "#f2f2f5", "--o-card": "#ffffff", "--o-ink": "#111113", "--o-mute": "#8a8a90", "--o-soft": "#f0f0f3", "--o-line": "rgba(0,0,0,.09)", "--o-shadow": "0 1px 2px rgba(0,0,0,.04)", "--o-shade": "rgba(0,0,0,.06)" },
  dark: { "--o-bg": "#000000", "--o-card": "#1c1c1e", "--o-ink": "#ffffff", "--o-mute": "#8e8e93", "--o-soft": "#2c2c2e", "--o-line": "rgba(255,255,255,.10)", "--o-shadow": "none", "--o-shade": "rgba(255,255,255,.07)" },
};
const deepFind = (root, tag) => {
  for (const el of root.querySelectorAll("*")) {
    if (el.localName === tag) return el;
    if (el.shadowRoot) { const r = deepFind(el.shadowRoot, tag); if (r) return r; }
  }
  return null;
};
const Theme = {
  panel: null,
  apply(mode) {
    const p = deepFind(document.querySelector("home-assistant")?.shadowRoot || document, "ha-panel-lovelace");
    if (!p) return false;
    Theme.panel = p;
    const t = THEMES[mode] || THEMES.dark;
    for (const [k, v] of Object.entries(t)) p.style.setProperty(k, v);
    const map = { "--primary-background-color": t["--o-bg"], "--lovelace-background": t["--o-bg"], "--card-background-color": t["--o-card"],
      "--primary-text-color": t["--o-ink"], "--secondary-text-color": t["--o-mute"], "--divider-color": t["--o-line"],
      "--app-header-background-color": t["--o-bg"], "--app-header-text-color": t["--o-ink"], "--secondary-background-color": t["--o-soft"] };
    for (const [k, v] of Object.entries(map)) p.style.setProperty(k, v);
    p.style.background = t["--o-bg"];
    return true;
  },
  ensure(mode) {
    let n = 0;
    const tick = () => { if (!Theme.apply(mode) && n++ < 40) setTimeout(tick, 150); };
    tick();
  },
  clear() { if (Theme.panel) [...Theme.panel.style].forEach((k) => Theme.panel.style.removeProperty(k)); Theme.panel = null; },
};

/* ------------------------------------------------------------------ helpers */
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const num = (s) => { const v = parseFloat(s); return Number.isFinite(v) ? v : null; };
const fmt = (v, d = 0) => (v == null ? "–" : v.toFixed(d).replace(".", ","));
const deg = (v) => (v == null ? "–" : `${Math.round(v)}°`);
const pad = (n) => String(n).padStart(2, "0");
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

const MDI = { "clear-night": "weather-night", cloudy: "weather-cloudy", exceptional: "alert-circle-outline", fog: "weather-fog", hail: "weather-hail", lightning: "weather-lightning",
  "lightning-rainy": "weather-lightning-rainy", partlycloudy: "weather-partly-cloudy", pouring: "weather-pouring", rainy: "weather-rainy", snowy: "weather-snowy",
  "snowy-rainy": "weather-snowy-rainy", sunny: "weather-sunny", windy: "weather-windy", "windy-variant": "weather-windy-variant" };
const TINT = { sunny: "#ff9f0a", "clear-night": "#b8b8ff", partlycloudy: "#ffb340", cloudy: "#aeaeb2", fog: "#8e8e93", rainy: "#0a84ff", pouring: "#0a84ff",
  "lightning-rainy": "#ffd60a", lightning: "#ffd60a", snowy: "#64d2ff", "snowy-rainy": "#64d2ff", hail: "#64d2ff", windy: "#8e8e93", "windy-variant": "#8e8e93", exceptional: "#ff453a" };
const HU = { "clear-night": "Tiszta éjszaka", cloudy: "Felhős", exceptional: "Rendkívüli", fog: "Köd", hail: "Jégeső", lightning: "Villámlás", "lightning-rainy": "Zivatar",
  partlycloudy: "Részben felhős", pouring: "Zápor", rainy: "Eső", snowy: "Havazás", "snowy-rainy": "Havas eső", sunny: "Napos", windy: "Szeles", "windy-variant": "Szeles" };
const wIcon = (c, size) => `<ha-icon icon="mdi:${MDI[c] || "weather-cloudy"}" style="--mdc-icon-size:${size}px;color:${TINT[c] || "#aeaeb2"}"></ha-icon>`;

const ic = (d, fill = "none") => `<svg viewBox="0 0 24 24" width="1em" height="1em" fill="${fill}" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`;
const ICON = {
  thermo: ic('<path d="M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z"/>'),
  drop: ic('<path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"/>'),
  wind: ic('<path d="M17.7 7.7a2.5 2.5 0 1 1 1.8 4.3H2"/><path d="M9.6 4.6A2 2 0 1 1 11 8H2"/><path d="M12.6 19.4A2 2 0 1 0 14 16H2"/>'),
  bulb: ic('<path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5"/><path d="M9 18h6"/><path d="M10 22h4"/>'),
  bulbFill: ic('<path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5Z"/><path d="M9 18h6"/><path d="M10 22h4"/>', "currentColor"),
  remote: ic('<rect x="7" y="2" width="10" height="20" rx="3"/><circle cx="12" cy="7" r="1.2"/><path d="M10 12h4M10 15.5h4M10 19h.01M14 19h.01"/>'),
};

const BASE_CSS = `
:host{display:block;font-family:${FONT};color:var(--o-ink,#fff);-webkit-font-smoothing:antialiased;font-feature-settings:"tnum" 1,"cv11" 1}
*{box-sizing:border-box}
button{font:inherit;color:inherit;background:none;border:0;padding:0;cursor:pointer;-webkit-tap-highlight-color:transparent}
.card{background:var(--o-card,#1c1c1e);border-radius:26px;box-shadow:var(--o-shadow,none);position:relative;overflow:hidden}
.mute{color:var(--o-mute,#8e8e93)}
svg{display:block}
ha-icon{display:block}
`;

/* smooth cubic path through points P=[[x,y],...] (both 0..100) */
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
const define = (name, cls) => { if (!customElements.get(name)) customElements.define(name, cls); };

class KBase extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: "open" }); this._cfg = null; this._hass = null; this._sig = null; }
  setConfig(c) { this._cfg = { theme: "dark", ...c }; this._build(); if (this._hass) this._update(); }
  set hass(h) { this._hass = h; if (this._cfg) this._update(); }
  st(id) { return id ? this._hass?.states?.[id] : undefined; }
  _changed(sig) { const s = JSON.stringify(sig); if (s === this._sig) return false; this._sig = s; return true; }
  getGridOptions() { return { columns: "full" }; }
}

/* ================================================================== weather */
class KWeather extends KBase {
  _build() {
    this.shadowRoot.innerHTML = `<style>${BASE_CSS}
      .card{padding:16px 24px 14px}
      .top{display:flex;align-items:center;gap:20px}
      .now ha-icon{flex:none}
      .lbl{font-size:12.5px;font-weight:600;letter-spacing:.07em;text-transform:uppercase;color:var(--o-mute)}
      .temp{font-size:72px;font-weight:300;letter-spacing:-.05em;line-height:.95;margin-top:4px}
      .temp small{font-size:26px;font-weight:400;letter-spacing:0;color:var(--o-mute);vertical-align:top;margin-left:4px;position:relative;top:6px}
      .cond{font-size:20px;font-weight:600;margin-top:4px}
      .cond span{color:var(--o-mute);font-weight:500}
      .chips{display:flex;gap:10px;margin-left:auto;flex-wrap:wrap;justify-content:flex-end}
      .chip{display:flex;align-items:center;gap:10px;padding:9px 16px 9px 10px;border-radius:18px;background:var(--o-soft)}
      .chip .i{width:32px;height:32px;border-radius:50%;display:grid;place-items:center;font-size:16px;background:var(--o-card)}
      .chip .l{font-size:11.5px;color:var(--o-mute);font-weight:500;line-height:1.2}
      .chip .v{font-size:15px;font-weight:700;line-height:1.25;white-space:nowrap}
      .sec{font-size:12.5px;font-weight:600;letter-spacing:.07em;text-transform:uppercase;color:var(--o-mute);margin:14px 0 6px}
      .hourly{position:relative}
      .hgrid{display:grid;grid-template-columns:repeat(var(--n),1fr)}
      .hc{display:flex;flex-direction:column;align-items:center;gap:3px;position:relative}
      .hc.nd::before{content:"";position:absolute;left:0;top:0;bottom:0;border-left:1.5px dotted var(--o-line)}
      .hd{height:15px;font-size:11px;font-weight:700;color:${ORANGE};text-transform:uppercase;letter-spacing:.05em}
      .hh{font-size:13px;font-weight:600;color:var(--o-mute)}
      .hr{height:16px;font-size:11.5px;font-weight:600;color:${BLUE}}
      .chart{position:relative;height:72px}
      .chart svg{position:absolute;inset:0;width:100%;height:100%;overflow:visible}
      .pt{position:absolute;transform:translate(-50%,-100%);font-size:15px;font-weight:700;padding-bottom:7px;white-space:nowrap}
      .pt::after{content:"";position:absolute;left:50%;bottom:-1px;width:7px;height:7px;margin-left:-3.5px;border-radius:50%;background:var(--o-card);border:2.5px solid ${ORANGE}}
      .days{display:grid;grid-template-columns:repeat(var(--n),1fr);gap:10px}
      .d{display:flex;flex-direction:column;align-items:center;gap:1px;padding:8px 4px 8px;border-radius:20px;background:var(--o-soft)}
      .d.today{background:var(--o-shade);box-shadow:inset 0 0 0 1.5px ${ORANGE}}
      .dn{font-size:16px;font-weight:700;text-transform:capitalize}
      .dd{font-size:11.5px;color:var(--o-mute);font-weight:500;margin-bottom:2px}
      .hi{font-size:24px;font-weight:700;margin-top:2px;letter-spacing:-.02em}
      .lo{font-size:17px;font-weight:500;color:var(--o-mute)}
      .dr{height:14px;font-size:12px;font-weight:600;color:${BLUE}}
    </style>
    <div class="card">
      <div class="top"><div class="now"></div><div class="chips"></div></div>
      <div class="sec">Következő 24 óra</div>
      <div class="hourly"></div>
      <div class="sec">Heti előrejelzés</div>
      <div class="days"></div>
    </div>`;
    this._r = this.shadowRoot;
  }
  connectedCallback() { Theme.ensure(this._cfg?.theme || "dark"); if (this._hass) this._startLoad(); }
  disconnectedCallback() { clearInterval(this._timer); this._timer = null; Theme.clear(); }
  _startLoad() { if (this._timer) return; this._load(); this._timer = setInterval(() => this._load(), 15 * 60e3); }
  async _fc(type) {
    const id = this._cfg.entity;
    const r = await this._hass.callWS({ type: "call_service", domain: "weather", service: "get_forecasts", service_data: { type }, target: { entity_id: id }, return_response: true });
    return (r.response?.[id] || {}).forecast || [];
  }
  async _load() {
    try { const [h, d] = await Promise.all([this._fc("hourly"), this._fc("daily")]); this._hourly = h; this._daily = d; this._drawHourly(); this._drawDaily(); this._sig = null; this._update(); }
    catch (e) { console.error("kiosk-weather: forecast failed", e); }
  }
  _update() {
    this._startLoad();
    const s = this.st(this._cfg.entity), a = s?.attributes || {};
    const day0 = (this._daily || [])[0];
    if (!this._changed([s?.state, a.temperature, a.humidity, a.wind_speed, a.pressure, this._cfg.title, day0?.temperature, day0?.templow, day0?.precipitation])) return;
    const cond = s?.state || "";
    const title = this._cfg.title || a.friendly_name || "";
    this._r.querySelector(".now").innerHTML = `<div style="display:flex;align-items:center;gap:22px">${wIcon(cond, 84)}<div>
      <div class="lbl">${esc(title)} · most</div>
      <div class="temp">${a.temperature != null ? Math.round(a.temperature) : "–"}<small>°C</small></div>
      <div class="cond">${esc(HU[cond] || cond)}${day0 ? ` <span>· ${deg(day0.temperature)} / ${deg(day0.templow)}</span>` : ""}</div></div></div>`;
    const chip = (i, color, l, v) => `<div class="chip"><div class="i" style="color:${color}">${i}</div><div><div class="l">${l}</div><div class="v">${v}</div></div></div>`;
    this._r.querySelector(".chips").innerHTML =
      (day0 ? chip(ICON.thermo, ORANGE, "Ma max / min", `${deg(day0.temperature)} / ${deg(day0.templow)}`) : "") +
      (day0 ? chip(ICON.drop, BLUE, "Csapadék ma", day0.precipitation > 0 ? `${day0.precipitation.toFixed(1).replace(".", ",")} mm` : "nincs") : "") +
      (a.humidity != null ? chip(ICON.drop, BLUE, "Páratartalom", `${Math.round(a.humidity)} %`) : "") +
      (a.wind_speed != null ? chip(ICON.wind, "#64d2ff", "Szél", `${Math.round(a.wind_speed * 3.6)} km/h`) : "") +
      (a.pressure != null ? chip(ICON.thermo, "#bf5af2", "Légnyomás", `${Math.round(a.pressure)} hPa`) : "");
  }
  _drawHourly() {
    const el = this._r.querySelector(".hourly"), n0 = this._cfg.hours || 24;
    const from = Date.now() - 30 * 60e3;
    const it = (this._hourly || []).filter((f) => new Date(f.datetime).getTime() >= from).slice(0, n0);
    if (!it.length) { el.innerHTML = ""; return; }
    const n = it.length, T = it.map((f) => f.temperature), lo = Math.min(...T), hi = Math.max(...T), span = Math.max(hi - lo, 3);
    const Y = (v) => 40 + (1 - (v - lo) / span) * 46; // % of chart height, leaves room for labels above
    const P = it.map((f, i) => [((i + 0.5) / n) * 100, Y(f.temperature)]);
    const d = smoothPath(P);
    const cols = it.map((f) => {
      const dt = new Date(f.datetime), h = dt.getHours();
      const wd = h === 0 ? dt.toLocaleDateString("hu-HU", { weekday: "short" }) : "";
      const rain = f.precipitation > 0 ? (f.precipitation < 1 ? f.precipitation.toFixed(1) : Math.round(f.precipitation)) : "";
      return `<div class="hc${h === 0 ? " nd" : ""}"><div class="hd">${wd}</div><div class="hh">${pad(h)}</div>${wIcon(f.condition, 30)}<div class="hr">${rain}</div></div>`;
    }).join("");
    const pts = it.map((f, i) => `<div class="pt" style="left:${P[i][0]}%;top:${P[i][1]}%">${deg(f.temperature)}</div>`).join("");
    el.innerHTML = `<div class="hgrid" style="--n:${n}">${cols}</div>
      <div class="chart"><svg viewBox="0 0 100 100" preserveAspectRatio="none"><defs><linearGradient id="hg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${ORANGE}" stop-opacity=".28"/><stop offset="1" stop-color="${ORANGE}" stop-opacity="0"/></linearGradient></defs>
        <path d="${d} L${P[n - 1][0]},100 L${P[0][0]},100 Z" fill="url(#hg)"/>
        <path d="${d}" fill="none" stroke="${ORANGE}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke"/></svg>${pts}</div>`;
  }
  _drawDaily() {
    const el = this._r.querySelector(".days"), it = (this._daily || []).slice(0, this._cfg.days || 7);
    el.style.setProperty("--n", it.length || 1);
    el.innerHTML = it.map((f, i) => {
      const dt = new Date(f.datetime);
      const name = i === 0 ? "Ma" : dt.toLocaleDateString("hu-HU", { weekday: "short" });
      const date = dt.toLocaleDateString("hu-HU", { month: "numeric", day: "numeric" });
      const rain = f.precipitation > 0 ? `${f.precipitation < 10 ? f.precipitation.toFixed(1) : Math.round(f.precipitation)} mm` : "";
      return `<div class="d${i === 0 ? " today" : ""}"><div class="dn">${name}</div><div class="dd">${date}</div>${wIcon(f.condition, 36)}<div class="hi">${deg(f.temperature)}</div><div class="lo">${deg(f.templow)}</div><div class="dr">${rain}</div></div>`;
    }).join("");
  }
  getCardSize() { return 9; }
}
define("kiosk-weather", KWeather);

/* ================================================================== dock */
class KDock extends KBase {
  _build() {
    const c = this._cfg, climate = c.climate || [], lights = c.lights || [];
    this._data = {};
    this.shadowRoot.innerHTML = `<style>${BASE_CSS}
      .dock{display:grid;grid-template-columns:${climate.map(() => "1.35fr").join(" ")} auto;gap:14px;align-items:stretch}
      .card{padding:10px 16px 8px}
      .cl .row{display:flex;align-items:center;justify-content:space-between}
      .cl .nm{font-size:12.5px;font-weight:600;letter-spacing:.07em;text-transform:uppercase;color:var(--o-mute)}
      .cl .vals{display:flex;align-items:baseline;gap:16px;margin-top:2px}
      .cl .t{font-size:30px;font-weight:300;letter-spacing:-.03em;line-height:1.05}
      .cl .t small{font-size:15px;color:var(--o-mute);margin-left:2px}
      .cl .h{display:flex;align-items:center;gap:5px;font-size:15px;font-weight:600;color:${BLUE}}
      .cl .h svg{font-size:13px}
      .spark{position:relative;height:36px;margin-top:4px}
      .spark svg{position:absolute;inset:0;width:100%;height:100%;overflow:visible}
      .ax{display:flex;justify-content:space-between;font-size:10.5px;color:var(--o-mute);margin-top:3px}
      .ctl{display:flex;gap:12px;align-items:stretch}
      .tile{width:96px;border-radius:26px;background:var(--o-card);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;padding:12px 8px;text-align:center}
      .tile .i{width:40px;height:40px;border-radius:50%;display:grid;place-items:center;font-size:22px;background:var(--o-soft);color:var(--o-mute);transition:background .2s,color .2s}
      .tile.on .i{background:${ORANGE};color:#fff}
      .tile .n{font-size:12px;font-weight:600;line-height:1.2;max-width:92px}
      .tile .s{font-size:11px;color:var(--o-mute);margin-top:-5px}
      .tile.tv.on .i{background:${GREEN};color:#fff}
      .tile:active{transform:scale(.97)}
    </style>
    <div class="dock">
      ${climate.map((cl, i) => `<div class="card cl" data-i="${i}">
        <div class="row"><div class="nm">${esc(cl.name)}</div></div>
        <div class="vals"><div class="t"><span class="tv">–</span><small>°C</small></div><div class="h">${ICON.drop}<span class="hv">–</span></div></div>
        <div class="spark"><svg viewBox="0 0 100 100" preserveAspectRatio="none">
          <path class="lh" fill="none" stroke="${BLUE}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" opacity=".85" d=""/>
          <path class="lt" fill="none" stroke="${ORANGE}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" d=""/></svg></div>
        <div class="ax"><span>24 órája</span><span>Most</span></div></div>`).join("")}
      <div class="ctl">
        ${lights.map((id) => `<button class="tile light" data-id="${esc(id)}"><div class="i">${ICON.bulb}</div><div class="n"></div></button>`).join("")}
        ${c.tv ? `<button class="tile tv"><div class="i">${ICON.remote}</div><div class="n">TV</div></button>` : ""}
      </div>
    </div>`;
    const r = this.shadowRoot;
    r.querySelectorAll(".tile.light").forEach((b) => b.addEventListener("click", () => this._hass.callService("light", "toggle", { entity_id: b.dataset.id })));
    const tv = r.querySelector(".tile.tv");
    if (tv) tv.addEventListener("click", () => {
      history.pushState(null, "", this._cfg.tv.hash || "#tv");
      window.dispatchEvent(new CustomEvent("location-changed"));
    });
  }
  connectedCallback() { this._timer = setInterval(() => this._load(), 5 * 60e3); if (this._hass) this._load(); }
  disconnectedCallback() { clearInterval(this._timer); }
  async _load() {
    const cl = this._cfg?.climate || [];
    if (!this._hass || !cl.length) return;
    const now = Date.now();
    try {
      const ids = cl.flatMap((x) => [x.t, x.h]);
      const res = await this._hass.callWS({ type: "history/history_during_period", start_time: new Date(now - 24 * 3600e3).toISOString(), end_time: new Date(now).toISOString(), entity_ids: ids, minimal_response: true, no_attributes: true, significant_changes_only: false });
      const conv = (id) => (res[id] || []).map((e) => { const ts = e.lu ?? e.last_updated ?? e.lc; return [typeof ts === "number" ? ts * 1000 : new Date(ts).getTime(), num(e.s ?? e.state)]; }).filter((p) => p[1] != null && Number.isFinite(p[0]));
      for (const x of cl) this._data[x.t] = conv(x.t), this._data[x.h] = conv(x.h);
      this._drawSparks();
    } catch (e) { /* recorder not ready */ }
  }
  _line(points, W, now) {
    const start = now - W, B = W / 60, m = new Map();
    for (const [t, v] of points) { if (t < start) continue; const k = Math.min(59, Math.floor((t - start) / B)); const o = m.get(k) || { s: 0, n: 0 }; o.s += v; o.n++; m.set(k, o); }
    const arr = [...m.entries()].sort((a, b) => a[0] - b[0]).map(([k, o]) => [((k + 0.5) / 60) * 100, o.s / o.n]);
    if (arr.length < 2) return null;
    const vs = arr.map((p) => p[1]); let lo = Math.min(...vs), hi = Math.max(...vs);
    if (hi - lo < 1) { const mid = (hi + lo) / 2; lo = mid - 0.5; hi = mid + 0.5; }
    return smoothPath(arr.map(([x, v]) => [x, 90 - ((v - lo) / (hi - lo)) * 80]));
  }
  _drawSparks() {
    const cl = this._cfg.climate || [], now = Date.now(), W = 24 * 3600e3;
    cl.forEach((x, i) => {
      const card = this.shadowRoot.querySelector(`.cl[data-i="${i}"]`); if (!card) return;
      card.querySelector(".lt").setAttribute("d", this._line(this._data[x.t] || [], W, now) || "");
      card.querySelector(".lh").setAttribute("d", this._line(this._data[x.h] || [], W, now) || "");
    });
  }
  _update() {
    const c = this._cfg, cl = c.climate || [], lights = c.lights || [];
    const sig = [...cl.flatMap((x) => [this.st(x.t)?.state, this.st(x.h)?.state]), ...lights.map((id) => [this.st(id)?.state, this.st(id)?.attributes?.friendly_name]), this.st(c.tv?.entity)?.state];
    if (!this._changed(sig)) return;
    const r = this.shadowRoot;
    cl.forEach((x, i) => {
      const card = r.querySelector(`.cl[data-i="${i}"]`); if (!card) return;
      card.querySelector(".tv").textContent = fmt(num(this.st(x.t)?.state), 1);
      const h = num(this.st(x.h)?.state); card.querySelector(".hv").textContent = h == null ? "–" : `${Math.round(h)} %`;
    });
    r.querySelectorAll(".tile.light").forEach((b) => {
      const s = this.st(b.dataset.id), on = s?.state === "on";
      b.classList.toggle("on", on);
      b.querySelector(".n").textContent = s?.attributes?.friendly_name || b.dataset.id;
      b.querySelector(".i").innerHTML = on ? ICON.bulbFill : ICON.bulb;
    });
    const tv = r.querySelector(".tile.tv");
    if (tv) tv.classList.toggle("on", ["on", "playing", "paused", "idle"].includes(this.st(c.tv.entity)?.state));
    if (!this._loaded) { this._loaded = true; this._load(); } else this._drawSparks();
  }
  getCardSize() { return 3; }
}
define("kiosk-dock", KDock);

window.customCards = window.customCards || [];
window.customCards.push({ type: "kiosk-weather", name: "Kiosk weather", description: "Current + 24h + weekly" }, { type: "kiosk-dock", name: "Kiosk dock", description: "Climate sparklines, lights, TV" });
