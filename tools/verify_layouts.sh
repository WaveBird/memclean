#!/bin/bash
# 用不同的屏幕密度模拟不同电视，截图验证三档尺寸资源是否生效
#
#   wm density 240 → 1920×1080 @240dpi = 1280×720dp  → sw600dp（基准档）
#   wm density 320 → 1920×1080 @320dpi =  960×540dp  → 基础档
#   wm density 160 → 1920×1080 @160dpi = 1920×1080dp → sw900dp（大档）
#
# 用法：./tools/verify_layouts.sh [ip:port]
set -uo pipefail

cd "$(dirname "$0")/.."

DEV="${1:-192.168.1.172:5555}"
PKG=io.github.wavebird.memclean
OUT=build/shots
mkdir -p "$OUT"

STATE=$(adb devices | grep "$DEV" | awk '{print $2}')
[ "$STATE" = "device" ] || { echo "设备未连接（${STATE:-无}）"; exit 1; }

# 无论中途怎么失败，退出时一定要把密度还原，否则盒子界面会一直是错的大小
restore() {
  echo "==> 还原密度"
  adb -s "$DEV" shell wm density reset >/dev/null 2>&1 || true
  adb -s "$DEV" shell wm size reset >/dev/null 2>&1 || true
}
trap restore EXIT INT TERM

ORIG=$(adb -s "$DEV" shell wm density | grep -oE '[0-9]+' | tail -1)
echo "原始密度：${ORIG}dpi"

for D in 240 320 160; do
  case $D in
    240) TAG=sw600dp_1280x720dp ;;
    320) TAG=base_960x540dp ;;
    160) TAG=sw900dp_1920x1080dp ;;
  esac
  echo "==> 密度 ${D} dpi (${TAG})"
  adb -s "$DEV" shell wm density "$D" >/dev/null
  sleep 3
  adb -s "$DEV" shell am force-stop "$PKG"
  adb -s "$DEV" shell am start -n "$PKG/.MainActivity" >/dev/null
  sleep 4
  adb -s "$DEV" exec-out screencap -p > "$OUT/main_$TAG.png"
  adb -s "$DEV" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
  echo "    → $OUT/main_$TAG.png"
done

restore
trap - EXIT INT TERM
echo "完成，截图在 $OUT/"
ls -lh "$OUT"
