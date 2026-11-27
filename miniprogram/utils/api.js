// 后端地址与订阅模板 ID 集中在 miniprogram/config.js，换环境只改那一个文件。
const config = require('../config');

const BASE_URL = config.BASE_URL;
const TMPL_FOLLOW_UP = config.TMPL_FOLLOW_UP;

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
    return new Promise((resolve, reject) => {
        const header = {
            'Content-Type': 'application/json',
        };
        const token = getToken();
        if (token) {
            header['Authorization'] = 'Bearer ' + token;
        }
        wx.request({
            url: BASE_URL + path,
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
                // 真机上最常见的原因是地址不通（ENV 还是 dev / 不同网段 / 防火墙），
                // 把目标地址带到提示里，避免只看到「网络异常」无从下手。
                const host = BASE_URL.replace(/^https?:\/\//, '').replace(/\/api$/, '');
                console.error('[api] 请求失败:', BASE_URL + path, err && err.errMsg);
                reject({ code: -1, msg: '网络异常（连不上 ' + host + '）' });
            },
        });
    });
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
    return BASE_URL.replace(/\/api$/, '') + path;
}

/**
 * 上传头像。后端只收 jpg/png/webp，2MB 以内。
 */
function uploadAvatar(filePath) {
    return new Promise((resolve, reject) => {
        const header = {};
        const token = getToken();
        if (token) {
            header['Authorization'] = 'Bearer ' + token;
        }
        wx.uploadFile({
            url: BASE_URL + '/files/avatar',
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
                reject({ code: -1, msg: '上传失败，请检查网络' });
            },
        });
    });
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
    BASE_URL,
    ENV: config.ENV,
    TMPL_FOLLOW_UP,
    getToken,
    setToken,
    removeToken,
    request,
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
