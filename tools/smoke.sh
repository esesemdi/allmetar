#!/bin/bash
# CI only: installs the built APK on an emulator, drives every screen against the live feeds
# and saves screenshots plus diagnostics into ./shots.
set -u
PKG=io.github.esesemdi.allmetar
OUT=shots
mkdir -p "$OUT"
N=0

shot() { N=$((N + 1)); adb exec-out screencap -p > "$OUT/$(printf '%02d' $N)-$1.png"; echo "shot $N $1"; }
dump() {
  local try
  for try in 1 2 3 4; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    adb exec-out cat /sdcard/ui.xml > ui.xml
    # a slow emulator sometimes shows "<some system app> isn't responding" on top of everything
    if grep -q "isn&apos;t responding\|isn't responding" ui.xml; then
      local xy
      if xy=$(python3 tools/ui_find.py ui.xml text Wait); then
        echo "dismissing a system not-responding dialog"
        adb shell input tap $xy
        sleep 3
        continue
      fi
    fi
    return 0
  done
}
# tap ATTR VALUE [CLASS] [INDEX]
tap() {
  dump
  local xy
  if xy=$(python3 tools/ui_find.py ui.xml "$@"); then
    adb shell input tap $xy
    echo "tap $* -> $xy"
    return 0
  fi
  echo "NOT FOUND: $*" | tee -a "$OUT/problems.txt"
  cp ui.xml "$OUT/notfound-$N.xml"
  return 1
}
# press_in ATTR VALUE DX DY: tap at an offset from the top-left corner of a node (to hit one word of a report)
press_in() {
  dump
  local b
  if b=$(python3 tools/ui_find.py ui.xml "$1" "$2" TextView 0 bounds); then
    set -- $b "$3" "$4"
    adb shell input tap $(( $1 + $5 )) $(( $2 + $6 ))
    echo "press at $(( $1 + $5 )) $(( $2 + $6 ))"
    return 0
  fi
  echo "NOT FOUND for press: $1 $2" | tee -a "$OUT/problems.txt"
  return 1
}
type_into() { # type_into INDEX TEXT: focus the INDEX-th text box on screen and type
  tap class android.widget.EditText - "$1" || return 1
  sleep 1
  adb shell input text "$2"
  sleep 1
}
add_station() {
  tap content-desc "Add location" || return 1
  sleep 3
  adb shell input text "$1"
  sleep 3
  shot "search-$1"
  tap text "$1" TextView || return 1
  sleep 2
}

adb install -r allmetar.apk || exit 1
adb logcat -c
sleep 20   # let the freshly booted system settle
adb shell am start -W -n $PKG/.MainActivity
sleep 4
dump
shot empty

add_station CYWG
sleep 25
shot list-one
add_station KGFK
add_station CKK7
sleep 30
shot list-three

# --- Canadian aerodrome: view, edit, own fields, decode
tap text CYWG TextView
sleep 4
tap text AERODROME
sleep 2
shot cywg-aerodrome
tap text "EDIT AERODROME DATA"
sleep 2
shot edit-mode
tap text ATIS TextView
sleep 2
shot edit-frequency-dialog
tap text "~cancel"
sleep 1
tap text "+ ADD FREQUENCY"
sleep 2
type_into 0 "FBO"
type_into 1 "122.95"
shot add-frequency-dialog
tap text "~save"
sleep 2
adb shell input swipe 540 1900 540 700 400; sleep 1
shot edit-mode-lower
tap text "+ ADD FIELD"
sleep 2
type_into 0 "Circuit"
type_into 1 "Left%shand,%s1000%sft%sAGL"
shot add-field-dialog
tap text "~save"
sleep 2
shot edit-mode-with-field
adb shell input keyevent 4
sleep 2
tap text METAR; sleep 1; tap text AERODROME; sleep 2
shot aerodrome-after-editing
adb shell input swipe 540 1900 540 700 400; sleep 1
shot aerodrome-after-editing-2
adb shell input swipe 540 1900 540 900 400; sleep 1
shot cywg-notams
press_in text "~Q) CZ" 230 92
sleep 2
shot decode-notam-qualifier
tap text AERODROME; sleep 1
press_in text "~Q) CZ" 60 182
sleep 2
shot decode-notam-word
tap text METAR
sleep 2
shot cywg-metar
press_in text "~KT " 330 46
sleep 2
shot decode-metar-wind
tap text METAR; sleep 1
press_in text "~KT " 60 46
sleep 2
shot decode-metar-station
tap text METAR; sleep 1
press_in text "~KT " 480 46
sleep 2
shot decode-metar-third
tap text METAR; sleep 1
press_in text "~KT " 200 92
sleep 2
shot decode-metar-second-line
adb shell input swipe 540 1900 540 600 400; sleep 1
shot cywg-metar-history
adb shell input swipe 540 1900 540 300 200; adb shell input swipe 540 1900 540 300 200; adb shell input swipe 540 1900 540 300 200; sleep 1
shot cywg-metar-history-end
tap text TAF
sleep 2
shot cywg-taf
press_in text "~KT " 40 46
sleep 2
shot decode-taf-first
tap text TAF; sleep 1
press_in text "~KT " 120 137
sleep 2
shot decode-taf-change-group
tap text TAF
sleep 1
adb shell input keyevent 4
sleep 2

# --- US aerodrome (no in-app NOTAMs) and one without weather reports
tap text KGFK TextView
sleep 3
tap text AERODROME
sleep 2
adb shell input swipe 540 1900 540 900 400; sleep 1
shot kgfk-aerodrome
tap text TAF
sleep 2
shot kgfk-taf
adb shell input keyevent 4
sleep 2
tap text CKK7 TextView
sleep 3
tap text METAR
sleep 2
shot ckk7-metar
tap text AERODROME
sleep 2
shot ckk7-aerodrome
adb shell input keyevent 4
sleep 2

# --- background job: state, forced run
adb shell dumpsys jobscheduler > "$OUT/jobs-before.txt" 2>&1
adb shell cmd jobscheduler run -f $PKG 1 > "$OUT/job-run.txt" 2>&1
sleep 20
adb shell dumpsys jobscheduler > "$OUT/jobs-after.txt" 2>&1
grep -n -A25 "$PKG/.UpdateJobService" "$OUT/jobs-before.txt" | head -80 > "$OUT/job-before-summary.txt"
grep -n -A25 "$PKG/.UpdateJobService" "$OUT/jobs-after.txt" | head -80 > "$OUT/job-after-summary.txt"
rm -f "$OUT/jobs-before.txt" "$OUT/jobs-after.txt"
shot after-job

# --- offline: refresh must fail visibly and keep the data
adb shell svc wifi disable; adb shell svc data disable
sleep 5
tap text REFRESH
sleep 12
shot offline
adb shell dumpsys jobscheduler | grep "JobStatus{.*$PKG" > "$OUT/jobs-while-offline.txt" 2>&1
adb shell svc wifi enable; adb shell svc data enable
# no tap here: the app should update by itself once the connection is back
sleep 100
shot online-again-automatic
adb shell dumpsys jobscheduler | grep "JobStatus{.*$PKG" > "$OUT/jobs-back-online.txt" 2>&1
tap text REFRESH
sleep 15
shot online-again

# --- long-press menu
dump
if xy=$(python3 tools/ui_find.py ui.xml text KGFK TextView); then
  adb shell input swipe $xy $xy 900
  sleep 2
  shot menu
  adb shell input keyevent 4
  sleep 1
fi

# --- widget: location picker, then try to place it on the home screen
adb shell am start -W -n $PKG/.WidgetConfigActivity --ei appWidgetId 4242
sleep 3
shot widget-picker
adb shell input keyevent 4
adb shell input keyevent 3
sleep 3
shot home
adb shell input swipe 540 1300 540 1300 1200
sleep 2
shot home-longpress
if tap text "Widgets"; then
  sleep 4
  shot widget-sheet
  dump
  if ! python3 tools/ui_find.py ui.xml text "~AllMetar" > /dev/null; then
    tap class android.widget.EditText || tap text "~Search"
    sleep 2
    adb shell input text "AllMetar"
    sleep 4
    shot widget-search
  fi
  tap text "~AllMetar"
  sleep 3
  shot widget-app-open
  dump
  cp ui.xml "$OUT/widget-sheet.xml"
  if xy=$(python3 tools/ui_find.py ui.xml text "~Current METAR") || xy=$(python3 tools/ui_find.py ui.xml content-desc "~Current METAR"); then
    adb shell input tap $xy
    sleep 2
    shot widget-tapped
    if tap text "Add"; then
      sleep 4
    else
      adb shell input draganddrop $xy 540 900 1500
      sleep 4
    fi
    shot widget-after-add
    tap text CYWG TextView
    sleep 5
    adb shell input keyevent 3
    sleep 3
    shot home-with-widget
  fi
fi

# --- diagnostics
adb logcat -d > logcat-all.txt 2>&1
grep -E "FATAL EXCEPTION|AndroidRuntime|$PKG" logcat-all.txt | tail -400 > "$OUT/logcat.txt"
if grep -A12 "FATAL EXCEPTION" logcat-all.txt | grep -q "$PKG"; then
  echo "CRASH" | tee -a "$OUT/problems.txt"
  grep -B2 -A40 "FATAL EXCEPTION" logcat-all.txt | head -200 > "$OUT/crash.txt"
fi
adb shell dumpsys appwidget > "$OUT/appwidget.txt" 2>&1
ls -la "$OUT"
exit 0
