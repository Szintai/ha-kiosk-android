# Home Assistant side

## Design versions (switch / roll back)

| | HA dashboard | card file | git tag |
|---|---|---|---|
| **v1** iOS dark, flat | `dashboard-kiosk/home` | `www/kiosk-cards.js` | `design-v1-ios-dark` |
| **v2** ambient (live backdrop, glass) | `dashboard-kiosk2/home` | `www/kiosk-cards-v2.js` | `design-v2-ambient` |

Both dashboards stay deployed side by side. The tablet shows whichever path is set in the app config
(Settings screen, or `adb shell am start -n hu.szintai.hakiosk/.MainActivity --es dashboard_path dashboard-kiosk/home`).
Rolling back = set the path back to `dashboard-kiosk/home` (v1). `python3 ha/scripts/deploy.py v1|v2` re-deploys either one.

The kiosk app only renders a normal Lovelace dashboard (`dashboard-kiosk/home`). Everything
HA-specific lives here:

- `www/kiosk-cards.js` – custom cards `kiosk-weather` (current + 24 h + 7 days) and `kiosk-dock`
  (climate sparklines, lights, TV). Same `--o-*` design tokens as the `otthon-cards.js` dashboard.
- `dashboard/kiosk.json` – the dashboard config (weather entity, sensors, lights, TV pop-up).
- `scripts/deploy.py` – uploads the card, registers the resource and saves the dashboard.

```
HA_TOKEN=<long-lived token> python3 ha/scripts/deploy.py
```

Requires: Bubble Card (TV pop-up) and the `weather.home` entity (core *Open-Meteo* integration on the
home zone – gives a 7 day forecast; Met.no only returned 6).
