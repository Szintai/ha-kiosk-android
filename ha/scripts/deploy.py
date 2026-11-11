#!/usr/bin/env python3
"""Deploy the kiosk dashboard to Home Assistant.

Env:  HA_TOKEN  long-lived access token (never commit it)
      HA_HOST   default 192.168.0.11
      HA_SSH    ssh host that owns the HA config dir (default: kixpi)
      HA_WWW    default /docker/container/home-assistant/config/www/kiosk
Steps: copy ha/www/kiosk-cards.js -> HA /local/kiosk, register it as a Lovelace
resource (bumping ?v=), create the `dashboard-kiosk` dashboard, save ha/dashboard/kiosk.json.
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

async def main():
    subprocess.run(["ssh", SSH, f"mkdir -p {WWW}"], check=True)
    subprocess.run(["scp", "-q", str(ROOT / "www/kiosk-cards.js"), f"{SSH}:{WWW}/kiosk-cards.js"], check=True)
    url = f"/local/kiosk/kiosk-cards.js?v={int(time.time())}"
    async with HA() as ha:
        res = (await ha.call(type="lovelace/resources"))["result"]
        cur = next((r for r in res if "kiosk-cards.js" in r["url"]), None)
        if cur:
            await ha.call(type="lovelace/resources/update", resource_id=cur["id"], res_type="module", url=url)
        else:
            await ha.call(type="lovelace/resources/create", res_type="module", url=url)
        dashes = (await ha.call(type="lovelace/dashboards/list"))["result"]
        if not any(d["url_path"] == "dashboard-kiosk" for d in dashes):
            await ha.call(type="lovelace/dashboards/create", url_path="dashboard-kiosk", title="Kiosk",
                          icon="mdi:tablet-dashboard", show_in_sidebar=True, require_admin=False, mode="storage")
        cfg = json.loads((ROOT / "dashboard/kiosk.json").read_text())
        r = await ha.call(type="lovelace/config/save", url_path="dashboard-kiosk", config=cfg)
        print("dashboard saved:", r.get("success"), r.get("error") or "")

asyncio.run(main())
