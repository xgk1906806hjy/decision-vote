// 后端 API 地址。本地联调：开发者工具「详情 → 本地设置」勾选「不校验合法域名」。
// 上线前把 BASE_URL 改成已备案的 HTTPS 域名。
const BASE_URL = 'http://localhost:8080/api';

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
 * 登录：一次 wx.login 换后端 token。
 */
function login() {
    return new Promise((resolve, reject) => {
        wx.login({
            success(res) {
                if (!res.code) {
                    reject({ code: -1, msg: '微信登录失败' });
                    return;
                }
                request('/auth/login', { method: 'POST', data: { code: res.code } })
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
    getToken,
    setToken,
    removeToken,
    request,
    login,
    ensureLogin,

    health: () => request('/health'),

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
