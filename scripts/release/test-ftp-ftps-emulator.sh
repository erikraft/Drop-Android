#!/usr/bin/env bash
set -euo pipefail

cleanup() {
  adb logcat -d -s FtpServerService:I FtpServerService:E '*:S' > ftp-ftps-logcat.txt 2>&1 || true
}
trap cleanup EXIT

adb install -r ErikrafT-Drop-release.apk
adb shell pm grant com.erikraft.drop android.permission.WRITE_EXTERNAL_STORAGE

adb shell am start -n com.erikraft.drop/.FtpSettingsActivity
sleep 2

found=0
for attempt in $(seq 1 10); do
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/window.xml > /tmp/drop-window.xml 2>/dev/null || true

  bounds="$(python3 - <<'PY'
import re
from pathlib import Path

text = Path("/tmp/drop-window.xml").read_text(errors="ignore")
match = re.search(r'text="Iniciar servidor".*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text)
if not match:
    match = re.search(r'text="Start server".*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text)
if match:
    x1, y1, x2, y2 = map(int, match.groups())
    print(f"{(x1+x2)//2} {(y1+y2)//2}")
PY
  )"

  if [ -n "$bounds" ]; then
    read -r tap_x tap_y <<< "$bounds"
    adb shell input tap "$tap_x" "$tap_y"
    found=1
    break
  fi

  adb shell input swipe 540 1600 540 900 500 >/dev/null 2>&1 || true
  sleep 1
done

if [ "$found" -ne 1 ]; then
  echo "Unable to locate the existing FTP start control in FtpSettingsActivity." >&2
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/window.xml || true
  exit 1
fi

for port in 2221 50000 50001 50002 50003 50004 50005 50006 50007 50008 50009 50010; do
  adb forward "tcp:$port" "tcp:$port"
done

for attempt in $(seq 1 30); do
  if curl --silent --show-error --fail --connect-timeout 10 --max-time 30 --ftp-skip-pasv-ip --user admin:admin ftp://127.0.0.1:2221/ >/dev/null; then
    break
  fi
  sleep 1
  if [ "$attempt" -eq 30 ]; then
    echo "FTP server did not become reachable." >&2
    exit 1
  fi
done

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
