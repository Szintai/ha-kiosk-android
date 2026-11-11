#!/usr/bin/env python3
"""Adds a compact phone layout (max-width: 600px) to the Otthon dashboard cards (www/otthon/otthon-cards.js).

Desktop and tablet layouts are untouched: every rule sits inside a media query. Idempotent (marker
comment). A backup is expected next to the file (`otthon-cards.js.bak-before-mobile`).

  python3 ha/scripts/otthon_mobile_patch.py [--revert]

Env: HA_SSH (default kixpi), HA_OTTHON (default /docker/container/home-assistant/config/www/otthon/otthon-cards.js)
"""
import os
import subprocess
import sys

SSH = os.environ.get("HA_SSH", "kixpi")
PATH = os.environ.get("HA_OTTHON", "/docker/container/home-assistant/config/www/otthon/otthon-cards.js")
MARK = "/* mobile-compact */"

# class name in the file -> phone CSS (appended to that card's <style>)
MOBILE = {
    "OHeader": """
      h1{font-size:32px !important}
      .date{font-size:11.5px !important}
      .theme{width:38px !important;height:38px !important;font-size:17px !important}
      .chips{flex-wrap:nowrap !important;overflow-x:auto !important;gap:8px !important;margin:14px -16px 0 !important;padding:0 16px 4px !important;scrollbar-width:none;-webkit-overflow-scrolling:touch}
      .chips::-webkit-scrollbar{display:none}
      .chip{flex:none !important;padding:7px 14px 7px 8px !important;border-radius:16px !important;gap:8px !important}
      .chip .i{width:28px !important;height:28px !important;font-size:14px !important}
      .chip .l{font-size:10.5px !important}.chip .v{font-size:13px !important}""",
    "ORoom": """
      .r{padding:2px 4px 0 !important}
      h2{font-size:21px !important}
      .s{font-size:13.5px !important}""",
    "OClimate": """
      .card{padding:14px 16px 10px !important;border-radius:22px !important}
      .temp{font-size:52px !important}
      .temp small{font-size:18px !important;top:4px !important;margin-left:2px !important}
      .hum{font-size:13.5px !important;padding-bottom:8px !important}
      .ctl{margin-top:8px !important}
      .read{font-size:12px !important}
      .seg button{padding:4px 10px !important;font-size:12px !important}
      .chart{height:64px !important}
      .ax{margin-top:4px !important;font-size:10.5px !important}
      .bat{font-size:12px !important}.lab{font-size:13px !important}""",
    "OLight": """
      .tile{min-height:58px !important;padding:9px 10px !important;gap:12px !important}
      .ico{width:40px !important;height:40px !important;font-size:19px !important}
      .n{font-size:15.5px !important}.s{font-size:12.5px !important}
      .tune{width:36px !important;height:36px !important}""",
    "OTv": """
      .card{padding:10px !important}
      .ico{width:40px !important;height:40px !important;font-size:19px !important}
      .n{font-size:15.5px !important}.s{font-size:12.5px !important}
      .vol{margin-top:9px !important}
      .bar{height:40px !important}.mt{width:40px !important;height:40px !important}""",
    "OCamera": """
      .card{aspect-ratio:16/10 !important}
      .cap b{font-size:16px !important}.cap span{font-size:12px !important}
      .live{top:10px !important;left:10px !important;font-size:12px !important}
      .clock{top:13px !important;right:13px !important;font-size:12.5px !important}""",
}


def sh(cmd: str, **kw) -> str:
    return subprocess.run(["ssh", SSH, cmd], check=True, capture_output=True, text=True, **kw).stdout


def patch(src: str) -> str:
    if MARK in src:
        print("already patched")
        return src
    for cls, css in MOBILE.items():
        start = src.index(f"class {cls} ")
        end = src.index("</style>", start)
        src = src[:end] + f"\n      {MARK}\n      @media (max-width:600px){{{css}\n      }}\n    " + src[end:]
    return src


def main() -> None:
    if "--revert" in sys.argv:
        sh(f"cp {PATH}.bak-before-mobile {PATH}")
        print("reverted to the backup")
        return
    sh(f"cp -n {PATH} {PATH}.bak-before-mobile")
    original = sh(f"cat {PATH}")
    patched = patch(original)
    if patched != original:
        subprocess.run(["ssh", SSH, f"cat > {PATH}"], input=patched, text=True, check=True)
        print("patched", PATH)


if __name__ == "__main__":
    main()
