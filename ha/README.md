# Home Assistant side

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
