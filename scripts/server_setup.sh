#!/usr/bin/env bash
# ============================================================
# 路口后端 · 服务器端一键部署（幂等，可重复执行）
# 适配：113.45.152.236（Ubuntu 24.04，MySQL 8 跑在 docker 容器 mysql 里，
#       8080 被 Jenkins 占用 → 本应用监听 127.0.0.1:8081，Nginx 80 反代）
#
# 前置（由本地 deploy 流程上传）：
#   /opt/decision-vote/app.jar              后端 jar
#   /opt/decision-vote/schema.sql           建库脚本
#   /opt/decision-vote/decision-vote.env    环境变量（含密钥，权限 600）
#
# 用法：bash server_setup.sh
# ============================================================
set -euo pipefail

APP_DIR=/opt/decision-vote
LOG_DIR=/var/log/decision-vote
BACKUP_DIR=/backup/decision_vote
ENV_FILE="$APP_DIR/decision-vote.env"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"   # docker 容器 mysql 的 root 密码

[ -f "$APP_DIR/app.jar" ]   || { echo "缺少 $APP_DIR/app.jar"; exit 1; }
[ -f "$APP_DIR/schema.sql" ] || { echo "缺少 $APP_DIR/schema.sql"; exit 1; }
[ -f "$ENV_FILE" ]          || { echo "缺少 $ENV_FILE"; exit 1; }
chmod 600 "$ENV_FILE"
# shellcheck disable=SC1090
source "$ENV_FILE"

mkdir -p "$APP_DIR/uploads" "$LOG_DIR" "$BACKUP_DIR"

# ---------- 1. Nginx（已装则跳过） ----------
if ! command -v nginx >/dev/null 2>&1; then
    echo ">>> 安装 nginx"
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nginx
fi

# ---------- 2. 数据库：复用 docker 容器 mysql，只新增 decision_vote 库 ----------
echo ">>> 等待 MySQL 容器就绪"
for i in $(seq 1 30); do
    if docker exec mysql mysqladmin -uroot -p"$MYSQL_ROOT_PASSWORD" ping --silent 2>/dev/null; then
        break
    fi
    sleep 2
    [ "$i" = 30 ] && { echo "MySQL 容器不可用"; exit 1; }
done

echo ">>> 建库建专用账号（不动已有库）"
docker exec -i mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS decision_vote DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'dvapp'@'%' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER 'dvapp'@'%' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON decision_vote.* TO 'dvapp'@'%';
FLUSH PRIVILEGES;
SQL

echo ">>> 执行 schema.sql（幂等）"
docker exec -i mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < "$APP_DIR/schema.sql"

# ---------- 3. systemd 服务 ----------
echo ">>> 写 systemd 单元"
cat > /etc/systemd/system/decision-vote.service <<'UNIT'
[Unit]
Description=decision-vote backend (lukou)
After=network.target docker.service

[Service]
Type=simple
WorkingDirectory=/opt/decision-vote
EnvironmentFile=/opt/decision-vote/decision-vote.env
ExecStart=/usr/bin/java -Xms128m -Xmx256m -XX:MaxMetaspaceSize=160m -jar /opt/decision-vote/app.jar
Restart=always
RestartSec=5
StandardOutput=append:/var/log/decision-vote/app.log
StandardError=append:/var/log/decision-vote/app.log

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable decision-vote >/dev/null 2>&1
systemctl restart decision-vote

# ---------- 4. Nginx 站点：80 → 127.0.0.1:8081 ----------
echo ">>> 配置 Nginx 反代"
cat > /etc/nginx/sites-available/decision-vote <<'NGINX'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    client_max_body_size 3m;

    location /api/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /uploads/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
    }

    location /admin/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
    }

    location / {
        return 404;
    }
}
NGINX
ln -sf /etc/nginx/sites-available/decision-vote /etc/nginx/sites-enabled/decision-vote
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable nginx >/dev/null 2>&1
systemctl restart nginx

# ---------- 5. 每日备份（数据库 + 头像），保留 7 天 ----------
echo ">>> 配置备份 cron"
cat > /etc/cron.d/decision-vote-backup <<'CRON'
# 密码不硬编码：运行时 source 环境变量文件（600，仅 root 可读），cron 以 root 执行
30 3 * * * root . /opt/decision-vote/decision-vote.env; docker exec mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --databases decision_vote > /backup/decision_vote/dv_$(date +\%Y\%m\%d).sql 2>/dev/null; find /backup/decision_vote -name 'dv_*.sql' -mtime +7 -delete
40 3 * * * root . /opt/decision-vote/decision-vote.env; tar -czf /backup/decision_vote/uploads_$(date +\%Y\%m\%d).tar.gz -C /opt/decision-vote uploads 2>/dev/null; find /backup/decision_vote -name 'uploads_*.tar.gz' -mtime +7 -delete
CRON
chmod 644 /etc/cron.d/decision-vote-backup

# ---------- 6. 健康检查 ----------
echo ">>> 等待后端启动"
for i in $(seq 1 45); do
    if curl -sf http://127.0.0.1:8081/api/health >/dev/null 2>&1; then
        echo ">>> 后端健康检查通过"
        curl -s http://127.0.0.1/api/health && echo
        echo ">>> 部署完成"
        exit 0
    fi
    sleep 2
done
echo "!!! 后端 90 秒内未通过健康检查，最近日志："
tail -30 "$LOG_DIR/app.log"
exit 1