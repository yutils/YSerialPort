#!/bin/sh
# 测试辅助脚本（Git Bash / MSYS 环境）
# 用法：
#   sh docs/tools/t.sh shot <name>        截图到 docs/screenshots/<name>.png
#   sh docs/tools/t.sh dump [关键字]       输出可点击节点与中心坐标
#   sh docs/tools/t.sh tap <x> <y>         点击
#   sh docs/tools/t.sh text <字符串>       输入文本
#   sh docs/tools/t.sh key <keycode>       按键（4=返回,3=HOME）
#   sh docs/tools/t.sh log [关键字]        输出 logcat
set -e
export PATH="$PATH:/c/Users/yujing/AppData/Local/Android/Sdk/platform-tools"
export MSYS_NO_PATHCONV=1
# 本机可能同时连接多台设备，这里固定使用模拟器
DEV="${ADB_DEVICE:-emulator-5554}"
ADB="adb -s $DEV"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ROOTW="$(cygpath -m "$ROOT")"
PY="C:/Users/yujing/.workbuddy/binaries/python/versions/3.13.12/python.exe"

case "$1" in
  shot)
    $ADB exec-out screencap -p > "$ROOT/docs/screenshots/$2.png"
    echo "saved docs/screenshots/$2.png"
    ;;
  dump)
    rm -f "$ROOT/docs/ui.xml"
    $ADB shell rm -f /sdcard/ui.xml
    for i in 1 2 3; do
      $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
      $ADB shell cat /sdcard/ui.xml > "$ROOT/docs/ui.xml" 2>/dev/null
      if [ -s "$ROOT/docs/ui.xml" ]; then break; fi
      sleep 1
    done
    "$PY" "$ROOTW/docs/tools/ui.py" "$ROOTW/docs/ui.xml" "$2"
    ;;
  tap)
    $ADB shell input tap "$2" "$3"
    echo "tap $2 $3"
    ;;
  text)
    $ADB shell input text "$2"
    ;;
  key)
    $ADB shell input keyevent "$2"
    ;;
  log)
    $ADB logcat -d | grep -iE "chuankou|AndroidRuntime|serial_port" | tail -60
    ;;
  *)
    echo "usage: t.sh shot|dump|tap|text|key|log ..."
    ;;
esac
