/**
 * 后端环境配置。改地址只改这个文件。
 *
 * ENV 取值：
 *   'auto'   自动选路（默认）。启动时并发探测下面 AUTO_ORDER 里的地址，谁先通就用谁。
 *            手机连同一个 WiFi 会自动走局域网（最快），连不上再退回隧道（公网可达）。
 *   'dev'    只在开发者工具模拟器用（localhost 在真机上指手机自己，一定失败）
 *   'lan'    强制走局域网 IP
 *   'tunnel' 强制走公网隧道
 *   'prod'   线上，必须是已备案的 HTTPS 域名
 *
 * 真机仍然报「网络异常」时的自查顺序：
 *   1. 电脑上后端有没有起来（curl http://localhost:8080/api/health）
 *   2. HOSTS.lan 的 IP 是不是这台电脑当前的地址（cmd 里 ipconfig 看「以太网」那条）
 *   3. 隧道是否还活着（电脑上那个 ssh / cloudflared 窗口别关）
 *   4. 开发者工具「详情 - 本地设置」是否勾选「不校验合法域名」
 */
const ENV = 'auto';

const HOSTS = {
    // 开发者工具模拟器
    dev: 'http://localhost:8080',
    // 真机 + 同一个 WiFi。IP 变了就改这里（ipconfig 里看「以太网」的 IPv4）
    lan: 'http://192.168.100.89:8080',
    // 公网隧道。地址由 scripts/tunnel.sh 打印，重启隧道后会变，变了就改这里
    tunnel: 'https://55effc1c1028fc.app.lhr.life',
    // 线上正式环境
    prod: 'https://api.your-domain.com',
};

// auto 模式的探测顺序：先局域网（快），再隧道（慢但一定通）
const AUTO_ORDER = ['lan', 'tunnel'];

// 各档地址的探测超时（毫秒）。
// 局域网同网段 RTT 只有几毫秒，1.2 秒还不通基本就是不通，没必要等满 3 秒；
// 隧道要走 TLS 握手 + 出海，冷启动实测 1.5-4.9 秒，给足 8 秒避免误判。
const PROBE_TIMEOUT = {
    lan: 1200,
    tunnel: 8000,
};

// 订阅消息模板 ID：微信公众平台「订阅消息」里选中「回访提醒」模板后复制到此处。
// 留空则前端隐藏「开启提醒」按钮，后端也会跳过发送（降级，不影响其他功能）。
const TMPL_FOLLOW_UP = '';

module.exports = {
    ENV,
    HOSTS,
    AUTO_ORDER,
    PROBE_TIMEOUT,
    TMPL_FOLLOW_UP,
    // 兼容旧引用：非 auto 时就是确定的地址，auto 时是首选地址（仅作兜底展示）
    HOST: HOSTS[ENV] || HOSTS[AUTO_ORDER[0]],
    BASE_URL: (HOSTS[ENV] || HOSTS[AUTO_ORDER[0]]) + '/api',
};
