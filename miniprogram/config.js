/**
 * 后端环境配置。改地址只改这个文件。
 *
 * ENV 三种取值：
 *   'dev'  仅开发者工具模拟器用（localhost 在真机上指手机自己，一定会失败）
 *   'lan'  真机调试 / 手机预览用（电脑与手机同一 WiFi）
 *   'prod' 线上，必须是已备案的 HTTPS 域名
 *
 * 真机报「网络异常」时的自查顺序：
 *   1. ENV 是不是还停在 'dev'
 *   2. LAN_HOST 的 IP 是不是这台电脑当前的地址（cmd 里 ipconfig 看「以太网」或「WLAN」那条）
 *   3. 手机和电脑是不是同一个 WiFi
 *   4. 电脑防火墙有没有放行 8080
 */
const ENV = 'lan';

const HOSTS = {
    dev: 'http://localhost:8080',
    lan: 'http://192.168.100.89:8080',
    prod: 'https://api.your-domain.com',
};

// 订阅消息模板 ID：微信公众平台「订阅消息」里选中「回访提醒」模板后复制到此处。
// 留空则前端隐藏「开启提醒」按钮，后端也会跳过发送（降级，不影响其他功能）。
const TMPL_FOLLOW_UP = '';

module.exports = {
    ENV,
    HOST: HOSTS[ENV] || HOSTS.lan,
    BASE_URL: (HOSTS[ENV] || HOSTS.lan) + '/api',
    TMPL_FOLLOW_UP,
};
