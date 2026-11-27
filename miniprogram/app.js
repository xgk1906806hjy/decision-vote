const api = require('./utils/api.js');

App({
    globalData: {
        appName: '路口',
        profile: null,
    },

    onLaunch() {
        // 有 token 才恢复登录态；游客不静默建号。
        // 账号在用户首次主动登录时创建，昵称由用户在登录页确认（微信合规）。
        if (api.getToken()) {
            api.profile()
                .then((profile) => {
                    this.globalData.profile = profile;
                })
                .catch(() => {
                    // token 过期等：保持游客态，不影响浏览
                });
        }
    },

    refreshProfile() {
        return api.profile().then((profile) => {
            this.globalData.profile = profile;
            return profile;
        });
    },
});
