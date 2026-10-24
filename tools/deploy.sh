#!/bin/bash
# 一键部署到电视盒子
# 用法：./tools/deploy.sh [ip:port] [apk路径]
set -euo pipefail

cd "$(dirname "$0")/.."

DEV="${1:-192.168.1.172:5555}"
APK="${2:-build/memclean.apk}"
PKG=io.github.wavebird.memclean

[ -f "$APK" ] || { echo "找不到 $APK，先跑 ./build.sh"; exit 1; }

echo "==> 连接 $DEV"
adb disconnect "$DEV" >/dev/null 2>&1 || true
adb connect "$DEV" >/dev/null 2>&1 || true
sleep 2

STATE=$(adb devices | grep "$DEV" | awk '{print $2}')
if [ "$STATE" != "device" ]; then
  echo "!! 连接失败，当前状态：${STATE:-无}"
  echo "   常见原因：盒子处于待机、或重启后「网络调试(adb)」被关掉。"
  echo "   处理：用遥控器唤醒盒子，到 设置 → 开发者选项 里重新打开网络调试。"
  exit 1
fi

echo "==> 推送 APK"
# 不走 adb install 的流式传输：老盒子上 34s 的 push 会触发超时被 SIGTERM
adb -s "$DEV" push "$APK" /data/local/tmp/memclean.apk >/dev/null

echo "==> 覆盖安装"
adb -s "$DEV" shell pm install -r /data/local/tmp/memclean.apk

echo "==> 授予「使用情况访问」"
adb -s "$DEV" shell appops set "$PKG" GET_USAGE_STATS allow

echo "==> 拉起服务"
adb -s "$DEV" shell am startservice -n "$PKG/.MemCleanService" >/dev/null 2>&1 || true

echo "==> 清理临时文件"
adb -s "$DEV" shell rm -f /data/local/tmp/memclean.apk

echo
echo "已安装："
adb -s "$DEV" shell dumpsys package "$PKG" | grep -E "versionName|versionCode" | head -2
echo "打开界面：adb -s $DEV shell am start -n $PKG/.MainActivity"
