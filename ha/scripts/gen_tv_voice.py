#!/usr/bin/env python3
"""Generates ha/automations/kiosk-tv-voice.json: Hungarian voice commands for the Samsung TV and all lights.

A single automation with `conversation` triggers (matched by HA's Assist before its own intents),
so it needs no configuration.yaml change and no restart. Deploy with `deploy.py tv`.
"""
import json
from pathlib import Path

TV = "media_player.samsung_au7022_55_tv_ue55au7022kxxh"
REMOTE = "remote.samsung_au7022_55_tv_ue55au7022kxxh"
WEATHER = "weather.home"  # core Open-Meteo integration (7 day forecast)
TEMP_LIVING = "sensor.sonoff_snzb_02d_temperature_2"
TEMP_BEDROOM = "sensor.sonoff_snzb_02d_temperature"
LIGHTS = {  # key -> (HA entity, spoken words incl. accusative "-t", reply name)
    "living": ("light.haloszoba_lampa", "nappali", "a nappali lámpát"),
    "bedroom": ("light.aqara_lumi_light_agl004", "(hálószoba|hálószobai)", "a hálószoba lámpát"),
}

# Hassil sentence templates: (a|b) alternatives, [x] optional, {name} wildcard slot.
SENTENCES = {
    "tv_on": ["kapcsold be a (tévét|televíziót|tv)", "indítsd be a (tévét|televíziót|tv)", "tévé be"],
    "tv_off": ["kapcsold ki a (tévét|televíziót|tv)", "tévé ki"],
    "vol_up": ["(hangosítsd|növeld|emeld) fel a (tévét|tv|hangerőt)", "hangosítsd fel", "hangosabban", "emeld a hangerőt"],
    "vol_down": ["(halkítsd|csökkentsd|vedd) le a (tévét|tv|hangerőt)", "halkítsd le", "halkabban", "csökkentsd a hangerőt"],
    "vol_set": [
        "[a] (tévé|tv) hangerő[t|je|jét] [állítsd|legyen|tedd] {volume}",
        "(állítsd|tedd|legyen) a (tévé|tv) hangerejét {volume}",
        "(állítsd|tedd) a hangerőt {volume}",
        "hangerő {volume}",
    ],
    "mute": ["némítsd [el] a (tévét|hangot)", "némítsd el", "némítás"],
    "unmute": ["kapcsold vissza a hangot", "szólaljon meg a tévé", "némítás ki"],
    "ch_up": ["következő csatorna", "a következő csatorna", "csatorna fel"],
    "ch_down": ["előző csatorna", "az előző csatorna", "csatorna le"],
    "pause": ["(állítsd meg|szüneteltesd) a (videót|lejátszást)", "szünet"],
    "play": ["folytasd a (videót|lejátszást)", "indítsd el a lejátszást", "lejátszás"],
    "lights_off": [
        "(kapcsold|oltsd) (le|ki) az összes lámpát",
        "(kapcsold|oltsd) (le|ki) a lámpákat",
        "(kapcsold|oltsd) (le|ki) minden lámpát",
        "minden lámpa ki",
        "[a|az összes] lámpák (le|ki)kapcsolása",
        "[az] összes lámpa (le|ki)kapcsolása",
        "lámpák oltása",
    ],
    "lights_on": [
        "(kapcsold|gyújtsd) fel az összes lámpát",
        "(kapcsold|gyújtsd) fel a lámpákat",
        "(kapcsold|gyújtsd) fel minden lámpát",
        "kapcsold be az összes lámpát",
        "minden lámpa be",
        "[a|az összes] lámpák (fel|be)kapcsolása",
        "[az] összes lámpa (fel|be)kapcsolása",
        "lámpák felgyújtása",
    ],
    # Weather / temperature / clothing advice (read-only)
    "weather_now": [
        "milyen idő van [kint|odakint]",
        "milyen az idő [kint|odakint]",
        "hogy van kint az idő",
        "mi az időjárás",
        "időjárás",
        "milyen idő lesz [ma]",
    ],
    "temp_out": [
        "hány fok van [kint|odakint]",
        "hány fok van most",
        "mennyi a hőmérséklet [kint|odakint]",
    ],
    "temp_in": [
        "hány fok van (bent|odabent|a lakásban)",
        "mennyi a hőmérséklet (bent|a lakásban)",
    ],
    "temp_living": ["hány fok van a nappaliban", "mennyi a hőmérséklet a nappaliban"],
    "temp_bedroom": ["hány fok van a hálószobában", "mennyi a hőmérséklet a hálószobában"],
    "clothes": [
        "milyen ruhát húzzak [fel]",
        "milyen ruhát vegyek fel [ma]",
        "mit vegyek fel [ma]",
        "mit húzzak fel [ma]",
        "hogyan öltözzek [fel]",
        "milyen ruhában menjek",
        "kell kabát",
    ],
    # Single lights: "[a] <szoba> lámp(a|át)" accepts the nominative and the accusative form.
    **{
        f"{room}_{state}": sentences
        for room, (_, name, _) in LIGHTS.items()
        for state, sentences in {
            "on": [
                f"(kapcsold|gyújtsd) fel [a] {name} lámp(a|át)",
                f"kapcsold be [a] {name} lámp(a|át)",
                f"(kapcsold|gyújtsd) fel a lámpát a {name.replace('(hálószoba|hálószobai)', 'hálószobában') if 'hálószoba' in name else 'nappaliban'}",
            ],
            "off": [
                f"(kapcsold|oltsd) (le|ki) [a] {name} lámp(a|át)",
                f"oltsd el [a] {name} lámp(a|át)",
                f"(kapcsold|oltsd) (le|ki) a lámpát a {name.replace('(hálószoba|hálószobai)', 'hálószobában') if 'hálószoba' in name else 'nappaliban'}",
            ],
        }.items()
    },
    # App ids are the Tizen ones; untested on this TV (Emby intentionally not included yet).
    "youtube": ["(indítsd|nyisd meg|kapcsold be) a youtube ot", "(indítsd|nyisd meg) a youtubeot"],
    "netflix": ["(indítsd|nyisd meg|kapcsold be) a netflixet", "(indítsd|nyisd meg) a netflixet"],
}

# Spoken or written number -> 0..100 ("20-ra", "húszra", "tizenötre", "huszonöt").
PARSE_VOLUME = """
{% set ns = namespace(n=-1) %}
{% set raw = (trigger.slots.volume | default('') | string | lower) %}
{% set digits = raw | regex_findall('\\\\d+') %}
{% if digits | length > 0 %}
  {% set ns.n = digits[0] | int %}
{% else %}
  {% set units = {'nulla':0,'egy':1,'kettő':2,'két':2,'három':3,'négy':4,'öt':5,'hat':6,'hét':7,'nyolc':8,'kilenc':9} %}
  {% set tens = {'tíz':10,'húsz':20,'harminc':30,'negyven':40,'ötven':50,'hatvan':60,'hetven':70,'nyolcvan':80,'kilencven':90,'száz':100} %}
  {% set pref = {'tizen':10,'huszon':20,'harminc':30,'negyven':40,'ötven':50,'hatvan':60,'hetven':70,'nyolcvan':80,'kilencven':90} %}
  {% for w in raw.replace('-', ' ').split() %}
    {% for c in [w, w[:-2]] %}
      {% if ns.n < 0 %}
        {% if c in tens %}{% set ns.n = tens[c] %}
        {% elif c in units %}{% set ns.n = units[c] %}
        {% else %}
          {% for p, v in pref.items() %}
            {% if ns.n < 0 and c.startswith(p) and c[p | length:] in units %}{% set ns.n = v + units[c[p | length:]] %}{% endif %}
          {% endfor %}
        {% endif %}
      {% endif %}
    {% endfor %}
  {% endfor %}
{% endif %}
{{ ns.n }}
""".strip()


COND_HU = ("{% set cond = {'sunny':'napos','clear-night':'tiszta az ég','partlycloudy':'részben felhős',"
           "'cloudy':'felhős','fog':'ködös','rainy':'esős','pouring':'zuhog az eső','snowy':'havazik',"
           "'snowy-rainy':'havas eső','lightning':'villámlik','lightning-rainy':'zivatar','hail':'jégeső',"
           "'windy':'szeles','windy-variant':'szeles','exceptional':'rendkívüli az idő'} %}")
# one hour list/ daily list from the forecast responses; next 6 h rain sum, today's max/min
FORECAST_VARS = (
    "{% set t = state_attr('__W__', 'temperature') | float(0) %}"
    "{% set d = daily['__W__'].forecast[0] %}"
    "{% set tmax = d.temperature | float(t) %}{% set tmin = d.templow | float(t) %}"
    "{% set rain6 = hourly['__W__'].forecast[:6] | map(attribute='precipitation') | map('float', 0) | sum %}"
    "{% set wind = state_attr('__W__', 'wind_speed') | float(0) %}"
).replace("__W__", WEATHER)

REPLY_WEATHER = (COND_HU + FORECAST_VARS +
    "Kint {{ t | round(0) | int }} fok van, {{ cond.get(states('__W__'), states('__W__')) }}. "
    "A mai csúcs {{ tmax | round(0) | int }}, a minimum {{ tmin | round(0) | int }} fok."
    "{% if rain6 > 0.3 %} A következő órákban eső várható.{% endif %}").replace("__W__", WEATHER)

# Clothing: dress for the day (mean of now and today's max), colder with strong wind (> 8 m/s).
REPLY_CLOTHES = (FORECAST_VARS +
    "{% set eff = (t + tmax) / 2 - (2 if wind > 8 else 0) %}"
    "Kint {{ t | round(0) | int }} fok van, ma legfeljebb {{ tmax | round(0) | int }} lesz. "
    "{% if eff <= 0 %}Fagyos idő van: télikabát, sapka, sál és kesztyű kell."
    "{% elif eff <= 7 %}Hideg van: vegyél fel meleg kabátot, sálat és sapkát."
    "{% elif eff <= 14 %}Hűvös van: kabát vagy vastagabb dzseki és hosszú nadrág kell."
    "{% elif eff <= 19 %}Enyhe idő van: egy pulóver vagy könnyű dzseki elég."
    "{% elif eff <= 24 %}Kellemes idő van: hosszú ujjú vagy póló, könnyű nadrág."
    "{% elif eff <= 29 %}Meleg van: póló és rövidnadrág is jó."
    "{% else %}Nagyon meleg van: könnyű, szellős ruha, napszemüveg és sok víz kell.{% endif %}"
    "{% if rain6 > 0.3 %} Vigyél esernyőt.{% endif %}"
    "{% if wind > 8 %} Szeles is van.{% endif %}")

REPLY_TEMP_OUT = "Kint {{ state_attr('__W__', 'temperature') | float(0) | round(1) | string | replace('.', ',') }} fok van.".replace("__W__", WEATHER)
REPLY_TEMP_IN = ("A nappaliban {{ states('__L__') | float(0) | round(1) | string | replace('.', ',') }} fok, "
                 "a hálószobában {{ states('__B__') | float(0) | round(1) | string | replace('.', ',') }} fok van."
                 ).replace("__L__", TEMP_LIVING).replace("__B__", TEMP_BEDROOM)
REPLY_TEMP_LIVING = "A nappaliban {{ states('__L__') | float(0) | round(1) | string | replace('.', ',') }} fok van.".replace("__L__", TEMP_LIVING)
REPLY_TEMP_BEDROOM = "A hálószobában {{ states('__B__') | float(0) | round(1) | string | replace('.', ',') }} fok van.".replace("__B__", TEMP_BEDROOM)


def forecasts() -> list:
    """Fetch hourly + daily forecasts into the `hourly` / `daily` variables."""
    return [
        {"action": "weather.get_forecasts", "target": {"entity_id": WEATHER}, "data": {"type": kind}, "response_variable": var}
        for kind, var in (("hourly", "hourly"), ("daily", "daily"))
    ]


def reply(text: str) -> dict:
    return {"set_conversation_response": text}


def branch(trigger_id: str, *actions: dict) -> dict:
    return {"conditions": [{"condition": "trigger", "id": trigger_id}], "sequence": list(actions)}


def key(code: str, repeat: int = 1) -> dict:
    data = {"command": code, "num_repeats": repeat} if repeat > 1 else {"command": code}
    return {"action": "remote.send_command", "target": {"entity_id": REMOTE}, "data": data}


def tv(service: str, **data) -> dict:
    action = {"action": f"media_player.{service}", "target": {"entity_id": TV}}
    if data:
        action["data"] = data
    return action


def app(app_id: str) -> dict:
    return tv("play_media", media_content_type="app", media_content_id=app_id)


automation = {
    "alias": "Kiosk: TV hangparancsok",
    "description": "Magyar hangparancsok a Samsung TV-hez és a lámpákhoz (generálva: ha/scripts/gen_tv_voice.py)",
    "mode": "queued",
    "max": 5,
    "triggers": [{"trigger": "conversation", "command": s, "id": i} for i, s in SENTENCES.items()],
    "conditions": [],
    "actions": [
        {"variables": {"level": PARSE_VOLUME}},
        {
            "choose": [
                branch("tv_on", tv("turn_on"), reply("Bekapcsolom a tévét")),
                branch("tv_off", tv("turn_off"), reply("Kikapcsolom a tévét")),
                branch(
                    "vol_up",
                    {"repeat": {"count": 3, "sequence": [tv("volume_up")]}},
                    reply("Hangosítom"),
                ),
                branch(
                    "vol_down",
                    {"repeat": {"count": 3, "sequence": [tv("volume_down")]}},
                    reply("Halkítom"),
                ),
                {
                    "conditions": [{"condition": "trigger", "id": "vol_set"}, {"condition": "template", "value_template": "{{ level | int(-1) >= 0 }}"}],
                    "sequence": [
                        tv("volume_set", volume_level="{{ ([100, level | int] | min) / 100 }}"),
                        reply("A tévé hangereje {{ [100, level | int] | min }}"),
                    ],
                },
                branch("vol_set", reply("Nem értettem a hangerőt")),
                branch("mute", tv("volume_mute", is_volume_muted=True), reply("Némítva")),
                branch("unmute", tv("volume_mute", is_volume_muted=False), reply("Hang visszakapcsolva")),
                branch("ch_up", key("KEY_CHUP"), reply("Következő csatorna")),
                branch("ch_down", key("KEY_CHDOWN"), reply("Előző csatorna")),
                branch("pause", tv("media_pause"), reply("Megállítva")),
                branch("play", tv("media_play"), reply("Folytatom")),
                branch("lights_off", {"action": "light.turn_off", "target": {"entity_id": "all"}}, reply("Lekapcsoltam az összes lámpát")),
                branch("lights_on", {"action": "light.turn_on", "target": {"entity_id": "all"}}, reply("Felkapcsoltam az összes lámpát")),
                *[
                    branch(
                        f"{room}_{state}",
                        {"action": f"light.turn_{state}", "target": {"entity_id": entity}},
                        reply(("Felkapcsoltam " if state == "on" else "Lekapcsoltam ") + label),
                    )
                    for room, (entity, _, label) in LIGHTS.items()
                    for state in ("on", "off")
                ],
                branch("weather_now", *forecasts(), reply(REPLY_WEATHER)),
                branch("clothes", *forecasts(), reply(REPLY_CLOTHES)),
                branch("temp_out", reply(REPLY_TEMP_OUT)),
                branch("temp_in", reply(REPLY_TEMP_IN)),
                branch("temp_living", reply(REPLY_TEMP_LIVING)),
                branch("temp_bedroom", reply(REPLY_TEMP_BEDROOM)),
                branch("youtube", app("111299001912"), reply("Indítom a YouTube-ot")),
                branch("netflix", app("3201907018807"), reply("Indítom a Netflixet")),
            ],
        },
    ],
}

out = Path(__file__).resolve().parents[1] / "automations" / "kiosk-tv-voice.json"
out.parent.mkdir(exist_ok=True)
out.write_text(json.dumps(automation, ensure_ascii=False, indent=2) + "\n")
print("wrote", out, f"({len(SENTENCES)} commands)")
