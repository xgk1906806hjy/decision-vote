const api = require('../../utils/api.js');

/**
 * 微信登录。
 * 登录本身只需要 wx.login（换 code），头像昵称是"可选补充"：
 * 头像走 chooseAvatar 拿到临时路径 → 登录后上传 → 写回资料；
 * 昵称用 type="nickname" 的输入框，微信会给出默认昵称候选。
 */
Page({
    data: {
        agreed: false,
        logging: false,
        avatarPath: '',
        nickname: '',
        nickFocus: false,
    },

    onLoad() {
        // 已登录不允许重复登录；token 失效（401）才放行重新登录
        if (api.getToken()) {
            api.profile()
                .then(() => {
                    wx.showToast({ title: '已登录', icon: 'none' });
                    setTimeout(() => this.back(), 400);
                })
                .catch((err) => {
                    if (err && err.code === 401) {
                        api.removeToken();
                    }
                });
        }
    },

    toggleAgree() {
        this.setData({ agreed: !this.data.agreed });
    },

    onChooseAvatar(e) {
        const path = e.detail && e.detail.avatarUrl;
        if (!path) return;
        this.setData({ avatarPath: path });
    },

    onNicknameInput(e) {
        this.setData({ nickname: e.detail.value });
    },

    // type=nickname 的「使用微信昵称」快捷填入在部分基础库上只触发 blur，这里兜底
    onNicknameBlur(e) {
        this.setData({ nickname: e.detail.value, nickFocus: false });
    },

    onLogin() {
        if (this.data.logging) return;
        if (!this.data.agreed) {
            wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
            return;
        }
        const nickname = (this.data.nickname || '').trim();
        if (!nickname) {
            // 微信不允许静默读取昵称，最快合规路径：点一下昵称框选「使用微信昵称」
            this.setData({ nickFocus: true });
            wx.showToast({ title: '点一下昵称框，使用微信昵称', icon: 'none' });
            return;
        }
        this.setData({ logging: true });

        api.login({ nickname })
            .then(() => {
                // 头像上传需要 token，所以放在登录之后；失败不阻塞登录
                if (!this.data.avatarPath) return null;
                return api.uploadAvatar(this.data.avatarPath)
                    .then((res) => api.updateProfile({ avatar: res.path || res.url }))
                    .catch(() => null);
            })
            .then(() => {
                const app = getApp();
                if (app) {
                    app.globalData.profile = null;
                }
                this.setData({ logging: false });
                wx.showToast({ title: '登录成功', icon: 'success' });
                setTimeout(() => this.back(), 500);
            })
            .catch((err) => {
                this.setData({ logging: false });
                wx.showToast({ title: (err && err.msg) || '登录失败', icon: 'none' });
            });
    },

    back() {
        const pages = getCurrentPages();
        if (pages.length > 1) {
            wx.navigateBack();
            return;
        }
        wx.switchTab({ url: '/pages/profile/profile' });
    },

    showAgreement() {
        wx.navigateTo({ url: '/pages/agreement/agreement' });
    },

    showPrivacy() {
        wx.navigateTo({ url: '/pages/privacy/privacy' });
    },
});
