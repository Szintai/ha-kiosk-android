#!/usr/bin/env python3
"""Deploy the kiosk dashboard to Home Assistant.

Env:  HA_TOKEN  long-lived access token (never commit it)
      HA_HOST   default 192.168.0.11
      HA_SSH    ssh host that owns the HA config dir (default: kixpi)
      HA_WWW    default /docker/container/home-assistant/config/www/kiosk
Usage: deploy.py [v1|v2|wake|tv|cam]   (default v2)
  cam  -> creates the HA `mjpeg` camera (camera.kiosk_tablet_camera) from the stream URL the tablet publishes in
          sensor.kiosk_tablet_ip_address (attribute camera_stream_url); needs 'Kamerakép megosztása' on in the app
  tv   -> creates/updates the automation ha/automations/kiosk-tv-voice.json (Hungarian TV voice commands,
          conversation triggers; generate it first with ha/scripts/gen_tv_voice.py)
  wake -> ha/www/kiosk-wake.js + adds the wake-up tile and its pop-up to the Otthon dashboard
          (dashboard-otthon, first section that has the otthon-tv card). Idempotent.
  v1 -> ha/www/kiosk-cards.js,    ha/dashboard/kiosk.json  -> dashboard-kiosk   (iOS dark, flat)
  v2 -> ha/www/kiosk-cards-v2.js, ha/dashboard/kiosk2.json -> dashboard-kiosk2  (ambient)
v2 reuses the kiosk-dock card from v1, so v1's file is deployed as well.
Each step copies the card file(s), registers them as Lovelace resources (bumping ?v=)
and creates/saves the dashboard.
"""
import asyncio, json, os, subprocess, sys, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hawspy
from hawspy import HA

ROOT = Path(__file__).resolve().parents[1]
HOST = os.environ.get("HA_HOST", "192.168.0.11")
SSH = os.environ.get("HA_SSH", "kixpi")
WWW = os.environ.get("HA_WWW", "/docker/container/home-assistant/config/www/kiosk")
hawspy.URL = f"ws://{HOST}:8123/api/websocket"

VERSIONS = {
    "v1": {"files": ["kiosk-cards.js"], "dash": ("dashboard-kiosk", "Kiosk", "dashboard/kiosk.json")},
    "v2": {"files": ["kiosk-cards.js", "kiosk-cards-v2.js"], "dash": ("dashboard-kiosk2", "Kiosk 2", "dashboard/kiosk2.json")},
}

WAKE_ENTITY = "sensor.kiosk_tablet_wake_count"
WAKE_CFG = {
    "entity": WAKE_ENTITY,
    "presence": "binary_sensor.kiosk_tablet_presence",
    "battery": "sensor.kiosk_tablet_battery_level",
    "charging": "binary_sensor.kiosk_tablet_battery_charging",
    "temp": "sensor.kiosk_tablet_battery_temperature",
    "camera": "camera.kiosk_tablet_camera",
    # The tablet stands upside down (charger on top). Set to 0 once the app's own "Kép elforgatása" is 180°.
    "camera_rotate": 180,
}
CAMERA_NAME = "Kiosk Tablet Camera"

async def register_resource(ha, name):
    subprocess.run(["ssh", SSH, f"mkdir -p {WWW}"], check=True)
    subprocess.run(["scp", "-q", str(ROOT / "www" / name), f"{SSH}:{WWW}/{name}"], check=True)
    res = (await ha.call(type="lovelace/resources"))["result"]
    url = f"/local/kiosk/{name}?v={int(time.time())}"
    cur = next((r for r in res if name in r["url"]), None)
    if cur:
        await ha.call(type="lovelace/resources/update", resource_id=cur["id"], res_type="module", url=url)
    else:
        await ha.call(type="lovelace/resources/create", res_type="module", url=url)

async def deploy_wake(ha):
    await register_resource(ha, "kiosk-wake.js")
    cfg = (await ha.call(type="lovelace/config", url_path="dashboard-otthon"))["result"]
    section = next(s for s in cfg["views"][0]["sections"] if any(c["type"] == "custom:otthon-tv" for c in s["cards"]))
    cards = [c for c in section["cards"] if c.get("type") not in ("custom:kiosk-wake-tile", "custom:kiosk-camera-tile")
             and not (c.get("type") == "custom:bubble-card" and c.get("hash") in ("#wakes", "#tabletcam"))]
    tile = {"type": "custom:kiosk-wake-tile", **WAKE_CFG, "name": "Tablet", "hash": "#wakes", "camera_hash": "#tabletcam",
            "grid_options": {"columns": "full"}}
    popup = {"type": "custom:bubble-card", "card_type": "pop-up", "hash": "#wakes", "name": "Tablet",
             "icon": "mdi:tablet", "entity": WAKE_ENTITY,
             "cards": [{"type": "custom:kiosk-wake-chart", **WAKE_CFG}]}
    cam_popup = {"type": "custom:bubble-card", "card_type": "pop-up", "hash": "#tabletcam", "name": "Tablet kamera",
                 "icon": "mdi:camera",
                 "cards": [{"type": "custom:kiosk-camera-view", "camera": WAKE_CFG["camera"],
                            "camera_rotate": WAKE_CFG["camera_rotate"]}]}
    idx = max(i for i, c in enumerate(cards) if c["type"] == "custom:otthon-tv")
    cards.insert(idx + 1, tile)
    cards.append(popup)
    cards.append(cam_popup)
    section["cards"] = cards
    r = await ha.call(type="lovelace/config/save", url_path="dashboard-otthon", config=cfg)
    print("dashboard-otthon saved:", r.get("success"), r.get("error") or "")

def deploy_cam():
    """Create the mjpeg camera from the URL the tablet reports (re-created each time: the token may change)."""
    import urllib.request
    host_hdr = {"Authorization": f"Bearer {os.environ['HA_TOKEN']}", "Content-Type": "application/json"}
    base = f"http://{HOST}:8123"

    def call(method, path, body=None):
        req = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None,
                                     method=method, headers=host_hdr)
        return json.loads(urllib.request.urlopen(req, timeout=20).read() or b"null")

    ip = call("GET", "/api/states/sensor.kiosk_tablet_ip_address")
    attrs = ip.get("attributes", {})
    stream, still = attrs.get("camera_stream_url"), attrs.get("camera_snapshot_url")
    if not stream:
        sys.exit("The tablet does not publish a camera URL yet: install the latest app build, switch on "
                 "'Kamerakép megosztása' in its Settings and wait a minute.")
    for e in call("GET", "/api/config/config_entries/entry"):
        if e["domain"] == "mjpeg" and e["title"] == CAMERA_NAME:
            call("DELETE", f"/api/config/config_entries/entry/{e['entry_id']}")
    flow = call("POST", "/api/config/config_entries/flow", {"handler": "mjpeg"})
    out = call("POST", f"/api/config/config_entries/flow/{flow['flow_id']}", {
        "name": CAMERA_NAME, "mjpeg_url": stream, "still_image_url": still, "verify_ssl": False})
    print("mjpeg camera:", out.get("type"), out.get("errors") or out.get("title") or "")

def deploy_tv():
    import urllib.request
    body = (ROOT / "automations" / "kiosk-tv-voice.json").read_bytes()
    req = urllib.request.Request(
        f"http://{HOST}:8123/api/config/automation/config/kiosk_tv_voice", data=body, method="POST",
        headers={"Authorization": f"Bearer {os.environ['HA_TOKEN']}", "Content-Type": "application/json"})
    print("automation kiosk_tv_voice:", urllib.request.urlopen(req, timeout=20).read().decode())

async def main():
    ver = sys.argv[1] if len(sys.argv) > 1 else "v2"
    if ver == "tv":
        deploy_tv()
        return
    if ver == "cam":
        deploy_cam()
        return
    if ver == "wake":
        async with HA() as ha:
            await deploy_wake(ha)
        return
    spec = VERSIONS[ver]
    subprocess.run(["ssh", SSH, f"mkdir -p {WWW}"], check=True)
    async with HA() as ha:
        res = (await ha.call(type="lovelace/resources"))["result"]
        for name in spec["files"]:
            subprocess.run(["scp", "-q", str(ROOT / "www" / name), f"{SSH}:{WWW}/{name}"], check=True)
            url = f"/local/kiosk/{name}?v={int(time.time())}"
            cur = next((r for r in res if name in r["url"]), None)
            if cur:
                await ha.call(type="lovelace/resources/update", resource_id=cur["id"], res_type="module", url=url)
            else:
                await ha.call(type="lovelace/resources/create", res_type="module", url=url)
        url_path, title, cfg_file = spec["dash"]
        dashes = (await ha.call(type="lovelace/dashboards/list"))["result"]
        if not any(d["url_path"] == url_path for d in dashes):
            await ha.call(type="lovelace/dashboards/create", url_path=url_path, title=title,
                          icon="mdi:tablet-dashboard", show_in_sidebar=True, require_admin=False, mode="storage")
        cfg = json.loads((ROOT / cfg_file).read_text())
        r = await ha.call(type="lovelace/config/save", url_path=url_path, config=cfg)
        print(f"{url_path} saved:", r.get("success"), r.get("error") or "")

asyncio.run(main())
