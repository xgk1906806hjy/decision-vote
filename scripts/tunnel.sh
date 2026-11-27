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
echo "[*] 建立成功后这一步会打印 https 地址；按 Ctrl+C 结束隧道"
echo

ssh -o StrictHostKeyChecking=no \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -R 80:localhost:"${PORT}" \
    nokey@localhost.run 2>&1 |
while IFS= read -r line; do
    printf '%s\n' "${line}"
    url=$(printf '%s' "${line}" | grep -oE 'https://[A-Za-z0-9._-]+\.(lhr\.life|loca\.lt)' | head -1)
    if [ -n "${url}" ]; then
        echo
        echo "=================================================================="
        echo "  隧道地址：${url}"
        echo "  下一步：把上面这个地址填进 miniprogram/config.js 的 HOSTS.tunnel"
        echo "         然后开发者工具重新编译，手机上退出小程序再进"
        echo "=================================================================="
        echo
    fi
done
