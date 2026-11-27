#!/usr/bin/env bash
# USB 直连：把手机上的 localhost:8080 转发到本机 8080。
#
# 为什么用这个：手机走流量时，内网 IP 和公网隧道都不理想——
#   内网 IP 根本不可达；免费隧道十几分钟到一天就轮换域名。
#   adb reverse 走 USB 数据线，不经过任何网络，手机上访问 localhost:8080
#   就等于访问电脑的 8080，永不掉线、零延迟、不用注册任何账号。
#
# 用法：
#   1. 手机开「开发者选项 → USB 调试」（小米：设置 → 关于手机 → 连点 MIUI 版本 7 次）
#   2. 数据线连电脑，手机上弹「允许 USB 调试」点允许
#   3. bash scripts/usb.sh
#   4. 小程序「我的 → 服务器地址 → 修改地址」填 http://localhost:8080
#
# 拔线后转发会失效，重新插上再跑一次本脚本即可。

set -u

PORT="${PORT:-8080}"

# adb 不一定在 PATH 里（常见于 Android SDK platform-tools）
find_adb() {
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return 0
    fi
    for p in \
        "/d/Program Files/platform-tools/platform-tools/adb.exe" \
        "/c/Android/Sdk/platform-tools/adb.exe" \
        "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" \
        "/c/Program Files (x86)/Tencent/微信web开发者工具/adb.exe"
    do
        [ -x "$p" ] && { echo "$p"; return 0; }
    done
    return 1
}

ADB=$(find_adb) || {
    echo "[x] 找不到 adb。装一个 Android platform-tools，或把 adb 所在目录加进 PATH。"
    exit 1
}

echo "[*] 使用 adb: ${ADB}"

# 没有设备时给出明确指引，而不是让后面的命令报一堆英文
devices=$("$ADB" devices | sed '1d' | grep -w "device" | wc -l | tr -d ' ')
if [ "${devices}" = "0" ]; then
    echo "[x] 没有检测到已授权的设备。检查："
    echo "    1. 数据线是否插好（有的线只能充电，换一根试）"
    echo "    2. 手机「开发者选项 → USB 调试」是否打开"
    echo "    3. 手机上是否弹过「允许 USB 调试」，点了允许"
    echo "    当前设备列表："
    "$ADB" devices -l | sed '1d'
    exit 1
fi

if ! "$ADB" reverse "tcp:${PORT}" "tcp:${PORT}"; then
    echo "[x] 端口转发失败"
    exit 1
fi

echo
echo "=================================================================="
echo "  USB 直连已建立：手机 localhost:${PORT} → 电脑 localhost:${PORT}"
echo
echo "  下一步：小程序 → 我的 → 服务器地址 → 修改地址"
echo "  填：http://localhost:${PORT}"
echo
echo "  之后电脑上的后端随便重启都不影响，只要线还插着。"
echo "  拔线重插后，再跑一次本脚本。"
echo "=================================================================="
echo
echo "  当前转发列表："
"$ADB" reverse --list
