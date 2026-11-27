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

    onLogin() {
        if (this.data.logging) return;
        if (!this.data.agreed) {
            wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
            return;
        }
        this.setData({ logging: true });
        const nickname = (this.data.nickname || '').trim();

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
        wx.showModal({
            title: '用户协议与隐私政策',
            content: '登录仅获取你的微信标识用于区分账号，不读取手机号、通讯录与位置。'
                + '你发布的内容会公开展示，涉及他人的内容请先征得同意。'
                + '可在「我的 → 编辑资料」随时修改或清除昵称、简介与头像。',
            showCancel: false,
            confirmText: '知道了',
        });
    },
});
