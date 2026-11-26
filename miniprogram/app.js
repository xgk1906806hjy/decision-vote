const api = require('./utils/api.js');

App({
    globalData: {
        appName: '路口',
        profile: null,
    },

    onLaunch() {
        // 静默登录：失败不阻塞进入，具体页面按需再触发
        api.ensureLogin()
            .then(() => api.profile())
            .then((profile) => {
                this.globalData.profile = profile;
            })
            .catch(() => {
                // 首次打开且后端未启动时保持未登录态，不影响静态展示
            });
    },

    refreshProfile() {
        return api.profile().then((profile) => {
            this.globalData.profile = profile;
            return profile;
        });
    },
});
