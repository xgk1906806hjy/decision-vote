#!/usr/bin/env bash
# 真机调试用的公网隧道：把本机 8080 暴露成一个手机能访问的 https 地址。
#
# 用途：手机和电脑不在同一个 WiFi、或所在 WiFi 开了客户端隔离时，
#      内网 IP 怎么配都连不上，只能走公网隧道。
#
# 用法：
#   1. 先保证后端在本机跑着（curl http://localhost:8080/api/health 返回 ok）
#   2. bash scripts/tunnel.sh
#   3. 等它打印出 https://xxxx.lhr.life，把地址填到 miniprogram/config.js 的 HOSTS.tunnel
#   4. 这个窗口别关，关了隧道就断；重启会换新地址，换了就再填一次
#
# 注意：免费隧道域名会周期性轮换、带宽有限，只适合联调，不要用于正式环境。

set -u

PORT="${PORT:-8080}"
HEALTH="http://localhost:${PORT}/api/health"

if ! command -v ssh >/dev/null 2>&1; then
    echo "[x] 找不到 ssh 命令（Windows 请用 Git Bash 运行本脚本）"
    exit 1
fi

if ! curl -s -m 5 -o /dev/null "${HEALTH}"; then
    echo "[x] 后端没起来：${HEALTH} 不可访问"
    echo "    先在另一个窗口启动后端再试"
    exit 1
fi

echo "[*] 后端正常，正在建立隧道（首次连接约 5-15 秒）..."
echo "[*] 域名轮换时会自动写回 miniprogram/config.js，按 Ctrl+C 结束隧道"
echo

cd "$(dirname "$0")/.." || exit 1
CONFIG="miniprogram/config.js"

ssh -o StrictHostKeyChecking=no \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -R 80:localhost:"${PORT}" \
    nokey@localhost.run 2>&1 |
while IFS= read -r line; do
    printf '%s\n' "${line}"
    url=$(printf '%s' "${line}" | grep -oE 'https://[A-Za-z0-9._-]+\.(lhr\.life|loca\.lt)' | head -1)
    if [ -n "${url}" ]; then
        # 自动写回 config.js：免费隧道十几分钟就轮换一次，手改跟不上
        if [ -f "${CONFIG}" ]; then
            sed -i "s#tunnel: 'https://[^']*'#tunnel: '${url}'#" "${CONFIG}"
            wrote="已自动写入 ${CONFIG}"
        else
            wrote="未找到 ${CONFIG}，需手动填写"
        fi
        echo
        echo "=================================================================="
        echo "  隧道地址：${url}"
        echo "  ${wrote}"
        echo
        echo "  手机上改（最快，不用重新编译）："
        echo "    我的 → 服务器地址 → 修改地址 → 粘贴上面的地址"
        echo "  或者：开发者工具重新编译后，退出小程序再进"
        echo "=================================================================="
        echo
    fi
done

