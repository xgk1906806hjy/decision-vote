// 后端地址与订阅模板 ID 集中在 miniprogram/config.js，换环境只改那一个文件。
const config = require('../config');

const TMPL_FOLLOW_UP = config.TMPL_FOLLOW_UP;
const HOST_KEY = 'dv_api_host';
// 手动指定的地址，优先级最高。隧道域名变了不用改代码重新编译，
// 在「我的 → 服务器地址」里粘一次就行。
const MANUAL_KEY = 'dv_api_manual';

// 当前生效的后端根地址（不含 /api），由 resolveBase() 决定：
//   手动地址 > ENV 指定的地址 > auto 探测结果。
let baseUrl = '';
let resolving = null;

function envHost(env) {
    return (config.HOSTS[env] || config.HOSTS.lan).replace(/\/+$/, '');
}

function getManualHost() {
    try {
        return (wx.getStorageSync(MANUAL_KEY) || '').replace(/\/+$/, '');
    } catch (e) {
        return '';
    }
}

/**
 * 设置/清除手动地址。传空字符串即清除，回到自动选路。
 */
function setManualHost(host) {
    const v = (host || '').trim().replace(/\/+$/, '');
    try {
        if (v) {
            wx.setStorageSync(MANUAL_KEY, v);
        } else {
            wx.removeStorageSync(MANUAL_KEY);
        }
    } catch (e) {
        // ignore
    }
    baseUrl = '';
    resolving = null;
}

function currentHost() {
    if (baseUrl) return baseUrl;
    const manual = getManualHost();
    if (manual) return manual;
    if (config.ENV !== 'auto') return envHost(config.ENV);
    // 还没探测出结果时先给个兜底，用于头像等地址拼接的展示
    try {
        return wx.getStorageSync(HOST_KEY) || envHost(config.AUTO_ORDER[0]);
    } catch (e) {
        return envHost(config.AUTO_ORDER[0]);
    }
}

function resetBase() {
    // 当前地址失效（换网、IP 变了、隧道断了）时清空，下次请求重新选路
    if (config.ENV !== 'auto') return;
    baseUrl = '';
    resolving = null;
}

/**
 * 探测单个地址是否可用：能返回 code=0 的 /api/health 就算通。
 */
function probeHost(host, timeout) {
    return new Promise((resolve, reject) => {
        wx.request({
            url: host + '/api/health',
            method: 'GET',
            timeout: timeout || 3000,
            success(res) {
                if (res.statusCode === 200 && res.data && res.data.code === 0) {
                    resolve(host);
                    return;
                }
                reject(new Error('bad health'));
            },
            fail(err) {
                reject(err);
            },
        });
    });
}

/**
 * 并发探测所有候选地址，第一个成功的获胜并记住它。
 */
function probe() {
    const timeouts = config.PROBE_TIMEOUT || {};
    const targets = config.AUTO_ORDER.map((env) => ({
        host: envHost(env),
        timeout: timeouts[env],
    }));
    return new Promise((resolve, reject) => {
        let left = targets.length;
        let done = false;
        targets.forEach((t) => {
            probeHost(t.host, t.timeout).then((ok) => {
                if (done) return;
                done = true;
                resolve(ok);
            }).catch(() => {
                left -= 1;
                if (left === 0 && !done) {
                    done = true;
                    reject(new Error('all hosts unreachable'));
                }
            });
        });
    });
}

/**
 * 拿到可用根地址（不带 /api）。并发调用共享同一次探测。
 */
function resolveBase() {
    if (baseUrl) return Promise.resolve(baseUrl);
    // 手动指定 > 自动选路：用户明确填了地址就不再去猜
    const manual = getManualHost();
    if (manual) {
        baseUrl = manual;
        return Promise.resolve(manual);
    }
    if (config.ENV !== 'auto') {
        baseUrl = envHost(config.ENV);
        return Promise.resolve(baseUrl);
    }
    if (resolving) return resolving;
    resolving = probe()
        .then((host) => {
            baseUrl = host;
            resolving = null;
            try {
                wx.setStorageSync(HOST_KEY, host);
            } catch (e) {
                // ignore
            }
            console.log('[api] 已选中后端地址:', host);
            return host;
        })
        .catch((e) => {
            resolving = null;
            const tried = config.AUTO_ORDER.map((k) => envHost(k)).join(' / ');
            console.error('[api] 所有后端地址都不通:', tried);
            throw { code: -1, msg: '网络异常（都连不上：' + tried + '）' };
        });
    return resolving;
}

const TOKEN_KEY = 'dv_token';

function getToken() {
    try {
        return wx.getStorageSync(TOKEN_KEY) || '';
    } catch (e) {
        return '';
    }
}

function setToken(token) {
    try {
        wx.setStorageSync(TOKEN_KEY, token || '');
    } catch (e) {
        // ignore
    }
}

/**
 * 统一请求封装。resolve 成功 data；失败时 reject({ code, msg })。
 */
function request(path, options) {
    const opts = options || {};
    return resolveBase().then((base) => new Promise((resolve, reject) => {
        const header = {
            'Content-Type': 'application/json',
        };
        const token = getToken();
        if (token) {
            header['Authorization'] = 'Bearer ' + token;
        }
        wx.request({
            url: base + '/api' + path,
            method: opts.method || 'GET',
            data: opts.data || {},
            header: header,
            timeout: 15000,
            success(res) {
                const status = res.statusCode;
                const body = res.data;
                if (status >= 200 && status < 300 && body && body.code === 0) {
                    resolve(body.data);
                    return;
                }
                if (status === 401 || (body && body.code === 401)) {
                    removeToken();
                    reject({ code: 401, msg: (body && body.msg) || '请先登录' });
                    return;
                }
                reject({
                    code: body && body.code ? body.code : status,
                    msg: (body && body.msg) || '请求失败，请稍后重试',
                });
            },
            fail(err) {
                // 真机上最常见的原因是地址不通（换网 / IP 变了 / 隧道断了），
                // 把目标地址带到提示里，避免只看到「网络异常」无从下手。
                console.error('[api] 请求失败:', base + '/api' + path, err && err.errMsg);
                resetBase();
                reject({ code: -1, msg: '网络异常（连不上 ' + base.replace(/^https?:\/\//, '') + '）' });
            },
        });
    }));
}

function removeToken() {
    try {
        wx.removeStorageSync(TOKEN_KEY);
    } catch (e) {
        // ignore
    }
}

/**
 * 把后端返回的相对路径（/uploads/xxx）拼成可直接渲染的完整地址。
 */
function fileUrl(path) {
    if (!path) return '';
    if (/^https?:\/\//.test(path) || path.indexOf('//') === 0) return path;
    return currentHost() + path;
}

/**
 * 上传头像。后端只收 jpg/png/webp，2MB 以内。
 */
function uploadAvatar(filePath) {
    return resolveBase().then((base) => new Promise((resolve, reject) => {
        const header = {};
        const token = getToken();
        if (token) {
            header['Authorization'] = 'Bearer ' + token;
        }
        wx.uploadFile({
            url: base + '/api/files/avatar',
            filePath,
            name: 'file',
            header,
            success(res) {
                let body = null;
                try {
                    body = JSON.parse(res.data);
                } catch (e) {
                    body = null;
                }
                if (res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 0) {
                    resolve(body.data);
                    return;
                }
                if (body && body.code === 401) {
                    removeToken();
                }
                reject({
                    code: body && body.code ? body.code : res.statusCode,
                    msg: (body && body.msg) || '上传失败，请稍后再试',
                });
            },
            fail() {
                resetBase();
                reject({ code: -1, msg: '上传失败，请检查网络' });
            },
        });
    }));
}

/**
 * 登录：一次 wx.login 换后端 token。可选带上用户填的昵称/头像。
 */
function login(profile) {
    const p = profile || {};
    return new Promise((resolve, reject) => {
        wx.login({
            success(res) {
                if (!res.code) {
                    reject({ code: -1, msg: '微信登录失败' });
                    return;
                }
                request('/auth/login', {
                    method: 'POST',
                    data: {
                        code: res.code,
                        nickname: p.nickname || '',
                        avatar: p.avatar || '',
                    },
                })
                    .then((data) => {
                        setToken(data.token);
                        resolve(data);
                    })
                    .catch(reject);
            },
            fail: reject,
        });
    });
}

function ensureLogin() {
    if (getToken()) {
        return Promise.resolve();
    }
    return login().then(() => {});
}

const api = {
    get BASE_URL() {
        return currentHost() + '/api';
    },
    ENV: config.ENV,
    TMPL_FOLLOW_UP,
    getToken,
    setToken,
    removeToken,
    request,
    resolveBase,
    getHost: currentHost,
    getManualHost,
    setManualHost,
    login,
    ensureLogin,
    uploadAvatar,
    fileUrl,

    health: () => request('/health'),

    // 场景标签：预设 + 站内自定义（按热度）
    scenes: () => request('/scenes'),

    // 决策帖
    feed: (params) => request('/posts/feed', { data: params }),
    detail: (id) => request('/posts/' + id),
    createPost: (data) => request('/posts', { method: 'POST', data }),
    myPosts: (params) => request('/posts/mine', { data: params }),
    myVoted: (params) => request('/posts/voted', { data: params }),
    vote: (id, optionId) => request('/posts/' + id + '/vote', {
        method: 'POST', data: { optionId },
    }),
    comments: (id, data) => request('/posts/' + id + '/comments', { method: 'POST', data }),

    // 举报（targetType: post / comment）
    report: (data) => request('/reports', { method: 'POST', data }),

    // 方案
    solutions: (id) => request('/posts/' + id + '/solutions'),
    addSolution: (id, content) => request('/posts/' + id + '/solutions', {
        method: 'POST', data: { content },
    }),
    likeSolution: (solutionId) => request('/solutions/' + solutionId + '/like', { method: 'POST' }),
    adoptSolution: (postId, solutionId) => request(
        '/posts/' + postId + '/adopt?solutionId=' + solutionId, { method: 'POST' }),

    // 回访
    submitFollowUp: (id, data) => request('/posts/' + id + '/follow-up', {
        method: 'POST', data,
    }),
    pendingFollowUps: () => request('/follow-ups/pending'),

    // 回访订阅提醒（额度：授权一次 +1，发送一条 -1）
    subscribeStatus: () => request('/subscribe/follow-up'),
    grantSubscribe: () => request('/subscribe/follow-up', { method: 'POST' }),

    // 案例库
    cases: (params) => request('/cases', { data: params }),
    similarCases: (params) => request('/cases/similar', { data: params }),
    caseStats: (params) => request('/cases/stats', { data: params }),

    // 我的
    profile: () => request('/users/me'),
    updateProfile: (data) => request('/users/me', { method: 'PUT', data }),
    addExperience: (data) => request('/users/me/experiences', { method: 'POST', data }),
    deleteExperience: (id) => request('/users/me/experiences/' + id, { method: 'DELETE' }),
};

module.exports = api;
