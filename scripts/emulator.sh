#!/usr/bin/env bash
# Start/stop the headless BetCoin_Test emulator.
# Usage: scripts/emulator.sh [start|stop|status]
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
AVD=BetCoin_Test
PORT=5580
SERIAL="emulator-$PORT"

booted() { [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; }

case "${1:-start}" in
  start)
    if booted; then echo "$SERIAL already running"; exit 0; fi
    nohup "$SDK/emulator/emulator" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim \
      -gpu swiftshader_indirect >"${TMPDIR:-/tmp}/betcoin-emulator.log" 2>&1 &
    "$ADB" -s "$SERIAL" wait-for-device
    for _ in $(seq 1 120); do booted && break; sleep 2; done
    booted || { echo "emulator did not finish booting; see ${TMPDIR:-/tmp}/betcoin-emulator.log" >&2; exit 1; }
    # Animations make UI checks flaky
    for s in window_animation_scale transition_animation_scale animator_duration_scale; do
      "$ADB" -s "$SERIAL" shell settings put global "$s" 0
    done
    echo "$SERIAL ready"
    ;;
  stop)
    "$ADB" -s "$SERIAL" emu kill
    ;;
  status)
    booted && echo "$SERIAL running" || echo "$SERIAL not running"
    ;;
  *)
    echo "usage: $0 [start|stop|status]" >&2; exit 2
    ;;
esac
