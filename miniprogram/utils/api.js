// 后端 API 地址。本地联调：开发者工具「详情 → 本地设置」勾选「不校验合法域名」。
// 上线前把 BASE_URL 改成已备案的 HTTPS 域名。
const BASE_URL = 'http://localhost:8080/api';

// 订阅消息模板 ID：微信公众平台「订阅消息」里选中「回访提醒」模板后复制到此处。
// 留空则前端隐藏「开启提醒」按钮，后端也会跳过发送（降级，不影响其他功能）。
const TMPL_FOLLOW_UP = '';

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
                reject({ code: -1, msg: '网络异常，请检查后端是否已启动' });
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
