#!/usr/bin/env bash
set -euo pipefail

cleanup() {
  timeout 20s adb logcat -d -s FtpServerService:I FtpServerService:E '*:S' > ftp-ftps-logcat.txt 2>&1 || true
}
trap cleanup EXIT

ADB_TIMEOUT="${ADB_TIMEOUT:-15s}"
FTP_READY_TIMEOUT="${FTP_READY_TIMEOUT:-90}"
FTP_READY_ATTEMPT_TIMEOUT="${FTP_READY_ATTEMPT_TIMEOUT:-5s}"

adb_timeout() {
  timeout "$ADB_TIMEOUT" adb "$@"
}

echo "Installing signed APK."
adb_timeout 60s install -r ErikrafT-Drop-release.apk
adb_timeout 15s shell pm grant com.erikraft.drop android.permission.WRITE_EXTERNAL_STORAGE

# FtpSettingsActivity is intentionally non-exported. Enter it through the
# existing user-facing MainActivity -> Settings -> FTP/FTPS navigation flow
# instead of bypassing the application's exported-component boundary.
echo "Starting MainActivity."
adb_timeout 20s shell am start -n com.erikraft.drop/.MainActivity >/dev/null

tap_text() {
  description="$1"
  swipe="$2"
  shift 2

  for attempt in $(seq 1 30); do
    timeout "$ADB_TIMEOUT" adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    timeout "$ADB_TIMEOUT" adb exec-out cat /sdcard/window.xml > /tmp/drop-window.xml 2>/dev/null || true

    bounds="$(CANDIDATES="$*" python3 - <<'PY'
import html
import os
import re
from pathlib import Path

text = html.unescape(Path("/tmp/drop-window.xml").read_text(errors="ignore"))
for candidate in os.environ["CANDIDATES"].split("|"):
    pattern = rf'text="{re.escape(candidate)}".*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    match = re.search(pattern, text)
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
        break
PY
)"

    if [ -n "$bounds" ]; then
      read -r tap_x tap_y <<< "$bounds"
      adb_timeout 15s shell input tap "$tap_x" "$tap_y"
      return 0
    fi

    if [ "$swipe" = "true" ]; then
      timeout "$ADB_TIMEOUT" adb shell input swipe 540 1600 540 900 500 >/dev/null 2>&1 || true
    fi
    sleep 1
  done

  echo "Unable to locate $description through the existing UI." >&2
  timeout "$ADB_TIMEOUT" adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  timeout "$ADB_TIMEOUT" adb exec-out cat /sdcard/window.xml || true
  return 1
}

# A clean emulator starts the existing first-run onboarding. Complete it using
# its existing Continue controls so the test reaches the normal main screen.
echo "Completing existing onboarding."
tap_text "onboarding Continue button (1/3)" false "continue|Continue"
tap_text "onboarding Continue button (2/3)" false "continue|Continue"
tap_text "onboarding Finish button (3/3)" false "finish|Finish|continue|Continue"
sleep 2

# Open Settings through MainActivity's existing action-bar menu, then select
# the existing FTP/FTPS preference.
echo "Opening FTP/FTPS settings through the existing app navigation."
adb_timeout 15s shell input keyevent 82
sleep 1
tap_text "Settings menu item" false "Settings|Configurações"
tap_text "FTP/FTPS settings preference" true "Transferência via FTP / FTPS|FTP / FTPS"

echo "Starting the existing FTP/FTPS server."
tap_text "FTP start control in FtpSettingsActivity" true "Iniciar servidor|Start server"

for port in 2221 50000 50001 50002 50003 50004 50005 50006 50007 50008 50009 50010; do
  adb_timeout 15s forward "tcp:$port" "tcp:$port"
done

printf 'ErikrafT Drop FTP/FTPS CI payload\n' > /tmp/drop-ftp-upload.txt
rm -f /tmp/drop-ftp-download.txt /tmp/drop-ftps-download.txt

echo "Waiting for the FTP/FTPS listener (maximum ${FTP_READY_TIMEOUT}s)."
ready_at="$(date +%s)"
ready=false
while true; do
  now="$(date +%s)"
  elapsed=$((now - ready_at))
  if [ "$elapsed" -ge "$FTP_READY_TIMEOUT" ]; then
    break
  fi

  if curl --silent --show-error --fail --connect-timeout 2 --max-time "$FTP_READY_ATTEMPT_TIMEOUT" \
      --ftp-skip-pasv-ip --user admin:admin ftp://127.0.0.1:2221/ >/dev/null; then
    ready=true
    break
  fi

  sleep 2
done

if [ "$ready" != "true" ]; then
  echo "FTP server did not become reachable within ${FTP_READY_TIMEOUT}s." >&2
  echo "Forwarded ports:" >&2
  timeout "$ADB_TIMEOUT" adb forward --list >&2 || true
  echo "FTP/FTPS service state:" >&2
  timeout "$ADB_TIMEOUT" adb shell dumpsys activity services com.erikraft.drop/.FtpServerService >&2 || true
  echo "FTP/FTPS service log:" >&2
  timeout 20s adb logcat -d -s FtpServerService:I FtpServerService:E '*:S' >&2 || true
  exit 1
fi

echo "Testing plain FTP upload/download/delete."
printf 'ErikrafT Drop FTP/FTPS CI payload\n' > /tmp/drop-ftp-upload.txt
rm -f /tmp/drop-ftp-download.txt /tmp/drop-ftps-download.txt

echo "Testing plain FTP upload/download/delete"
curl --silent --show-error --fail --connect-timeout 10 --max-time 30 --ftp-skip-pasv-ip --user admin:admin \
  --upload-file /tmp/drop-ftp-upload.txt \
  ftp://127.0.0.1:2221/erikraft-drop-ci-ftp.txt
curl --silent --show-error --fail --connect-timeout 10 --max-time 30 --ftp-skip-pasv-ip --user admin:admin \
  --output /tmp/drop-ftp-download.txt \
  ftp://127.0.0.1:2221/erikraft-drop-ci-ftp.txt
cmp /tmp/drop-ftp-upload.txt /tmp/drop-ftp-download.txt
curl --silent --show-error --fail --connect-timeout 10 --max-time 30 --ftp-skip-pasv-ip --user admin:admin \
  -Q 'DELE erikraft-drop-ci-ftp.txt' \
  ftp://127.0.0.1:2221/ >/dev/null

echo "Testing explicit FTPS/TLS upload/download/delete"
curl --silent --show-error --fail --insecure --ssl-reqd --connect-timeout 10 --max-time 30 --ftp-skip-pasv-ip --user admin:admin \
  --upload-file /tmp/drop-ftp-upload.txt \
  ftp://127.0.0.1:2221/erikraft-drop-ci-ftps.txt
curl --silent --show-error --fail --insecure --ssl-reqd --connect-timeout 10 --max-time 30 --ftp-skip-pasv-ip --user admin:admin \
  --output /tmp/drop-ftps-download.txt \
  ftp://127.0.0.1:2221/erikraft-drop-ci-ftps.txt
cmp /tmp/drop-ftp-upload.txt /tmp/drop-ftps-download.txt
curl --silent --show-error --fail --insecure --ssl-reqd --connect-timeout 10 --max-time 30 --ftp-skip-pasv-ip --user admin:admin \
  -Q 'DELE erikraft-drop-ci-ftps.txt' \
  ftp://127.0.0.1:2221/ >/dev/null

echo "Real FTP and explicit FTPS transfer checks passed against the signed APK."
