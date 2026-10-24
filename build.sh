#!/usr/bin/env bash
#
# 内存守护 —— 无需 Gradle 的极简构建脚本
#
# 依赖：JDK 17+、Android SDK（platform-34 + build-tools 34）
#
# 用法：
#   ./build.sh
#   JAVA_HOME=/path/to/jdk17 ANDROID_SDK=/path/to/sdk ./build.sh
#
# 产物：build/memclean-<version>.apk（另有 build/memclean.apk 供脚本引用）
#
set -euo pipefail

cd "$(dirname "$0")"

# ---- 工具链定位 ----------------------------------------------------------
# 优先用显式环境变量，其次项目内 .tools/，最后回落到 SDK 的标准环境变量，
# 这样在 CI（android-actions/setup-android）里不用额外配置也能跑。
JAVA_HOME="${JAVA_HOME:-$(ls -d ../.tools/jdk-17*/Contents/Home 2>/dev/null | head -1 || true)}"
ANDROID_SDK="${ANDROID_SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
if [ -z "$ANDROID_SDK" ]; then
  ANDROID_SDK="$(ls -d ../.tools/android-sdk 2>/dev/null | head -1 || true)"
fi

if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "找不到 JDK 17+，请设置 JAVA_HOME" >&2
  exit 1
fi
if [ -z "$ANDROID_SDK" ] || [ ! -f "$ANDROID_SDK/platforms/android-34/android.jar" ]; then
  echo "找不到 android.jar，请设置 ANDROID_SDK 指向包含 platforms/android-34 的 SDK" >&2
  exit 1
fi

BT="$(ls -d "$ANDROID_SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
ANDROID_JAR="$ANDROID_SDK/platforms/android-34/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"

# ---- 版本号：唯一来源是 AndroidManifest.xml ------------------------------
VERSION="$(grep -oE 'android:versionName="[^"]+"' AndroidManifest.xml | head -1 | cut -d'"' -f2)"
VERSION_CODE="$(grep -oE 'android:versionCode="[^"]+"' AndroidManifest.xml | head -1 | cut -d'"' -f2)"
[ -n "$VERSION" ] || { echo "无法从 AndroidManifest.xml 读取 versionName" >&2; exit 1; }

OUT="build/memclean-${VERSION}.apk"

echo "version    : ${VERSION} (${VERSION_CODE})"
echo "JDK        : ${JAVA_HOME}"
echo "build-tools: ${BT}"

rm -rf build
mkdir -p build/classes build/dex build/gen

echo "[1/5] 编译资源"
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link \
  -o build/base.apk \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  --java build/gen \
  build/res.zip

echo "[2/5] 编译 Java"
{ find src -name '*.java'; find build/gen -name '*.java'; } > build/sources.txt
javac -source 8 -target 8 -nowarn \
  -bootclasspath "$ANDROID_JAR" \
  -d build/classes \
  @build/sources.txt 2>&1 | grep -v 'bootstrap class path' || true

echo "[3/5] 生成 dex"
find build/classes -name '*.class' > build/classes.txt
"$BT/d8" --release --min-api 21 --lib "$ANDROID_JAR" \
  --output build/dex @build/classes.txt

echo "[4/5] 打包对齐"
( cd build/dex && zip -q ../base.apk classes.dex )
"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk

echo "[5/5] 签名"
# 发布时用自己的 keystore 覆盖：
#   KEYSTORE=path/to/release.jks KS_PASS=... KEY_ALIAS=... KEY_PASS=... ./build.sh
KS="${KEYSTORE:-keystore/debug.keystore}"
KS_PASS="${KS_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"
KEY_PASS="${KEY_PASS:-android}"

if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  echo "      未找到 keystore，生成调试用证书：$KS"
  keytool -genkeypair -keystore "$KS" -alias "$KEY_ALIAS" \
    -storepass "$KS_PASS" -keypass "$KEY_PASS" -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" \
  --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" \
  --out "$OUT" build/aligned.apk

# 供 tools/deploy.sh 等脚本引用的稳定路径
cp "$OUT" build/memclean.apk

"$BT/apksigner" verify --print-certs "$OUT" | head -4
echo
echo "完成：$OUT"
ls -lh "$OUT" | awk '{print "体积：" $5}'
