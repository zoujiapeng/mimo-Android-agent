#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
APP="$PROJ/app"
PREFIX=/data/data/com.termux/files/usr
T="$PREFIX/tmp/opencode"
SDK="$T/sdk"
BUILD="$T/build"
STDLIB="$PREFIX/opt/kotlin/lib/kotlin-stdlib.jar"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/javac" "$BUILD/classes" "$BUILD/dex"

echo "[1/7] aapt2 compile resources"
aapt2 compile --dir "$APP/src/main/res" -o "$BUILD/res.zip"

echo "[2/7] aapt2 link"
sed 's|<manifest |<manifest package="com.example.mimoagent" |' \
  "$APP/src/main/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"
aapt2 link -o "$BUILD/base.apk" -I "$SDK/android.jar" \
  --manifest "$BUILD/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0 \
  "$BUILD/res.zip"

echo "[3/7] javac R.java"
javac -source 17 -target 17 -cp "$SDK/android.jar" -d "$BUILD/javac" \
  $(find "$BUILD/gen" -name '*.java')

echo "[4/7] kotlinc"
kotlinc $(find "$APP/src/main/java" -name '*.kt') \
  -cp "$SDK/android.jar:$BUILD/javac" \
  -jvm-target 17 -d "$BUILD/classes" 2>&1 | grep -viE 'jansi|UnsatisfiedLink' || true

echo "[5/7] jar + d8"
jar cf "$BUILD/prog.jar" -C "$BUILD/javac" . -C "$BUILD/classes" .
d8 --min-api 24 --lib "$SDK/android.jar" --output "$BUILD/dex" \
  "$BUILD/prog.jar" "$STDLIB"

echo "[6/7] add classes.dex + zipalign"
python - "$BUILD/base.apk" "$BUILD/dex/classes.dex" <<'PY'
import sys, zipfile, shutil
apk, dex = sys.argv[1], sys.argv[2]
tmp = apk + ".tmp"
with zipfile.ZipFile(apk) as zin, zipfile.ZipFile(tmp, "w") as zout:
    for it in zin.infolist():
        data = zin.read(it.filename)
        if it.filename == "resources.arsc":
            zout.writestr(it, data, zipfile.ZIP_STORED)
        else:
            zout.writestr(it, data)
    zout.write(dex, "classes.dex")
shutil.move(tmp, apk)
PY
zipalign -f 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

echo "[7/7] sign"
KS="$BUILD/debug.keystore"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias androiddebugkey \
  -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$PROJ/MiMoAgent-debug.apk" "$BUILD/aligned.apk"
apksigner verify "$PROJ/MiMoAgent-debug.apk" && echo "SIGNED OK"
ls -lh "$PROJ/MiMoAgent-debug.apk"
