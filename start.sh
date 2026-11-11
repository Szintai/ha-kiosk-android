#!/usr/bin/env bash
# HA Kiosk – build / install / run helper.
#
#   ./start.sh            interactive menu
#   ./start.sh <number>   run one menu entry directly (e.g. ./start.sh 1)
#
# Env: ANDROID_SERIAL  adb device serial when several devices are attached
#      HA_TOKEN         long-lived HA token for the "deploy HA" entry (asked for if unset)
set -euo pipefail
cd "$(dirname "$0")"

PKG="hu.szintai.hakiosk"
ACTIVITY="$PKG/.MainActivity"
APK_DEBUG="app/build/outputs/apk/debug/app-debug.apk"
APK_RELEASE="app/build/outputs/apk/release/app-release.apk"

if [[ -t 1 ]]; then B=$'\e[1m'; G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; D=$'\e[2m'; N=$'\e[0m'; else B= G= Y= R= D= N=; fi
say()  { printf '%s==>%s %s\n' "$B" "$N" "$*"; }
ok()   { printf '%s✔%s %s\n' "$G" "$N" "$*"; }
warn() { printf '%s!%s %s\n' "$Y" "$N" "$*"; }
die()  { printf '%s✘%s %s\n' "$R" "$N" "$*" >&2; exit 1; }

# --- adb ----------------------------------------------------------------------------------------
adb_ready() {
  command -v adb >/dev/null || die "adb not found (add \$ANDROID_HOME/platform-tools to PATH)"
  local n; n=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)
  (( n > 0 )) || die "no authorised device on adb. Plug in the tablet, enable USB debugging and accept the prompt."
  if (( n > 1 )) && [[ -z "${ANDROID_SERIAL:-}" ]]; then
    adb devices -l | tail -n +2
    die "several devices attached: set ANDROID_SERIAL=<serial>"
  fi
}

grant_permissions() {
  for p in CAMERA RECORD_AUDIO; do adb shell pm grant "$PKG" "android.permission.$p" 2>/dev/null || true; done
  # "Do Not Disturb" access lets the app mute the speech recogniser's beeps in continuous mode.
  adb shell cmd notification allow_dnd "$PKG" 2>/dev/null || true
}

install_apk() {  # install_apk <apk>
  local apk=$1
  [[ -f $apk ]] || die "$apk not found - build it first"
  adb_ready
  say "Installing $(basename "$apk") ($(du -h "$apk" | cut -f1)); a slow tablet can take a minute ..."
  # -r: replace keeping data, -d: allow version downgrade
  if ! out=$(adb install -r -d "$apk" 2>&1); then
    echo "$out" >&2
    if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" <<<"$out"; then
      warn "The installed app is signed with a different key."
      read -r -p "Uninstall it first? This DELETES the app's settings (token etc.) [y/N] " a
      [[ $a == [yY] ]] || die "aborted"
      adb uninstall "$PKG" >/dev/null && adb install -d "$apk"
    else
      die "install failed"
    fi
  fi
  grant_permissions
  ok "installed, camera, microphone and Do-Not-Disturb access granted"
}

start_app() {
  adb_ready
  adb shell am force-stop "$PKG" || true
  adb shell am start -n "$ACTIVITY" >/dev/null
  ok "app started (the first start of a slow tablet takes ~20 s to draw)"
}

# --- builds -------------------------------------------------------------------------------------
build_debug()   { say "Debug build";                ./gradlew :app:assembleDebug   --console=plain -q; ok "$APK_DEBUG"; }
build_release() { say "Prod (release, R8-minified) build"; ./gradlew :app:assembleRelease --console=plain -q; ok "$APK_RELEASE"; release_notes; }
run_tests()     { say "Unit tests";                 ./gradlew :app:testDebugUnitTest --console=plain; ok "tests passed"; }

release_notes() {
  if [[ -f keystore.properties ]]; then
    printf '%s  signed with keystore.properties%s\n' "$D" "$N"
  else
    printf '%s  signed with the debug key -> installs over a debug build and keeps its settings.\n  For a dedicated key create keystore.properties (storeFile, storePassword, keyAlias, keyPassword).%s\n' "$D" "$N"
  fi
  printf '%s  The prod build ignores the adb provisioning extras (token, path); set them in the app Settings.%s\n' "$D" "$N"
}

# --- menu entries -------------------------------------------------------------------------------
pick_apk() {
  local choice
  read -r -p "Which build? [1] debug  [2] prod (release)  > " choice
  case $choice in 1) echo "$APK_DEBUG" ;; 2) echo "$APK_RELEASE" ;; *) die "invalid choice" ;; esac
}

deploy_ha() {
  [[ -n ${HA_TOKEN:-} ]] || { read -r -s -p "HA long-lived token (not stored): " HA_TOKEN; echo; export HA_TOKEN; }
  local which; read -r -p "Deploy: [1] v1 iOS dark  [2] v2 ambient  [3] tablet tile (Otthon)  [4] voice commands  [5] tablet camera  > " which
  case $which in 1) v=v1 ;; 2) v=v2 ;; 3) v=wake ;; 4) python3 ha/scripts/gen_tv_voice.py; v=tv ;; 5) v=cam ;; *) die "invalid choice" ;; esac
  say "Deploying HA $v"
  python3 ha/scripts/deploy.py "$v"
  rm -rf ha/scripts/__pycache__
}

show_menu() {
  cat <<EOF

${B}HA Kiosk${N}  ${D}($(git rev-parse --short HEAD 2>/dev/null || echo '-'), $(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | wc -l) device on adb)${N}

  ${B}1${N}) Debug build  → install → start on the tablet
  ${B}2${N}) Debug build only
  ${B}3${N}) Prod build only
  ${B}4${N}) Prod build  → install → start on the tablet
  ${B}5${N}) Install an already built APK (debug / prod)
  ${B}6${N}) Start / restart the app on the tablet
  ${B}7${N}) Logcat (this app only, Ctrl-C to stop)
  ${B}8${N}) Unit tests
  ${B}9${N}) Deploy to HA (dashboards · tablet tile · voice commands · camera)
  ${B}0${N}) Exit

EOF
}

run_choice() {
  case $1 in
    1) build_debug;   install_apk "$APK_DEBUG";   start_app ;;
    2) build_debug ;;
    3) build_release ;;
    4) build_release; install_apk "$APK_RELEASE"; start_app ;;
    5) install_apk "$(pick_apk)" ;;
    6) start_app ;;
    7) adb_ready; adb logcat --pid="$(adb shell pidof "$PKG" | tr -d '\r')" -v time ;;  # Log.i and above on this tablet
    8) run_tests ;;
    9) deploy_ha ;;
    0|q|Q) exit 0 ;;
    *) die "unknown choice: $1" ;;
  esac
}

if [[ $# -gt 0 ]]; then
  run_choice "$1"
else
  show_menu
  read -r -p "Choose [0-9]: " choice
  run_choice "$choice"
fi
