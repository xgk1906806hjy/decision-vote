const api = require('../../utils/api.js');

const SCENES = ['职场', '感情', '考研', '留学', '买房', '创业', '家庭', '其他'];
const OUTCOMES = [
    { value: 'worth', label: '值得' },
    { value: 'regret', label: '后悔' },
    { value: 'watching', label: '还在观察' },
];
const YEAR_OPTIONS = ['1 年内', '1–2 年', '3–5 年', '5 年以上'];
/**
 * 与 YEAR_OPTIONS 一一对应，取区间代表值。
 * "5 年以上"记为 8 年：既区别于 3–5 年档，也避免正好落在 5 年边界上。
 */
const YEAR_VALUES = [1, 2, 4, 8];

const TAB_MINE = 'mine';
const TAB_VOTED = 'voted';

const OUTCOME_LABELS = { worth: '值得', regret: '后悔', watching: '观察中' };

Page({
    data: {
        scenes: SCENES,
        outcomes: OUTCOMES,
        yearOptions: YEAR_OPTIONS,

        profile: null,
        viewState: 'loading',
        errorMessage: '',

        pendingList: [],

        // 回访提醒订阅
        subscribeRemain: 0,
        subscribeTemplateReady: false,
        subscribing: false,

        activeTab: TAB_MINE,
        posts: [],
        listLoading: false,

        // 个人资料编辑
        showProfileEdit: false,
        editNickname: '',
        editBio: '',
        savingProfile: false,

        // 经历档案表单
        showExpForm: false,
        expScene: '',
        expChoice: '',
        expYearIndex: 1,
        expOutcome: 'worth',
        savingExp: false,
    },

    onLoad() {
        this.loadProfile();
        this.loadPending();
        this.loadSubscribeStatus();
        this.loadList(TAB_MINE);
    },

    onShow() {
        if (this.data.viewState === 'ready') {
            this.loadProfile();
            this.loadPending();
            this.loadSubscribeStatus();
            this.loadList(this.data.activeTab);
        }
    },

    onPullDownRefresh() {
        this.loadProfile()
            .then(() => this.loadPending())
            .then(() => this.loadSubscribeStatus())
            .then(() => this.loadList(this.data.activeTab))
            .then(() => wx.stopPullDownRefresh())
            .catch(() => wx.stopPullDownRefresh());
    },

    // ---------- 数据加载 ----------
    loadProfile() {
        return api.profile()
            .then((profile) => {
                const name = profile.nickname || '路口用户';
                const experiences = (profile.experiences || []).map((exp) => Object.assign({}, exp, {
                    outcomeLabel: OUTCOME_LABELS[exp.outcome] || '未标注',
                }));
                this.setData({
                    profile: Object.assign({}, profile, {
                        nickname: name,
                        initial: name.slice(0, 1),
                        experiences,
                    }),
                    viewState: 'ready',
                });
            })
            .catch((err) => {
                this.setData({
                    viewState: 'error',
                    errorMessage: err.msg || '加载失败',
                });
            });
    },

    loadPending() {
        return api.pendingFollowUps()
            .then((list) => {
                const pendingList = (list || []).map((item) => Object.assign({}, item, {
                    dueText: item.dueAt ? '应于 ' + formatDate(item.dueAt) + ' 回访' : '可以回访了',
                }));
                this.setData({ pendingList });
            })
            .catch(() => this.setData({ pendingList: [] }));
    },

    loadSubscribeStatus() {
        return api.subscribeStatus()
            .then((status) => {
                this.setData({
                    subscribeRemain: status.remain || 0,
                    subscribeTemplateReady: !!status.templateReady,
                });
            })
            .catch(() => {});
    },

    onSubscribeRemind() {
        if (this.data.subscribing) return;
        const tmplId = api.TMPL_FOLLOW_UP;
        if (!tmplId) {
            wx.showModal({
                title: '提醒暂未开放',
                content: '订阅消息模板还没配置，先把模板 ID 填到 utils/api.js 的 TMPL_FOLLOW_UP。',
                showCancel: false,
            });
            return;
        }
        this.setData({ subscribing: true });
        wx.requestSubscribeMessage({
            tmplIds: [tmplId],
            success: (res) => {
                if (res[tmplId] !== 'accept') {
                    this.setData({ subscribing: false });
                    wx.showToast({ title: '未开启，随时可以再来', icon: 'none' });
                    return;
                }
                api.grantSubscribe()
                    .then((status) => {
                        this.setData({
                            subscribing: false,
                            subscribeRemain: status.remain || 0,
                            subscribeTemplateReady: !!status.templateReady,
                        });
                        wx.showToast({ title: '已开启回访提醒', icon: 'success' });
                    })
                    .catch((err) => {
                        this.setData({ subscribing: false });
                        wx.showToast({ title: err.msg || '开启失败', icon: 'none' });
                    });
            },
            fail: () => {
                this.setData({ subscribing: false });
                wx.showToast({ title: '开启失败，请稍后再试', icon: 'none' });
            },
        });
    },

    loadList(tab) {
        this.setData({ listLoading: true });
        const request = tab === TAB_MINE ? api.myPosts({ page: 1, size: 20 }) : api.myVoted({ page: 1, size: 20 });
        return request
            .then((result) => {
                const posts = (result.list || []).map((p) => Object.assign({}, p, {
                    timeText: formatDate(p.createdAt),
                }));
                this.setData({ posts, listLoading: false });
            })
            .catch(() => this.setData({ posts: [], listLoading: false }));
    },

    onTabTap(e) {
        const tab = e.currentTarget.dataset.tab;
        if (tab === this.data.activeTab) return;
        this.setData({ activeTab: tab, posts: [] });
        this.loadList(tab);
    },

    // ---------- 跳转 ----------
    goDetail(e) {
        const id = e.currentTarget.dataset.id;
        wx.navigateTo({ url: '/pages/detail/detail?id=' + id });
    },

    // ---------- 个人资料 ----------
    toggleProfileEdit() {
        if (this.data.showProfileEdit) {
            this.setData({ showProfileEdit: false });
            return;
        }
        const profile = this.data.profile || {};
        this.setData({
            showProfileEdit: true,
            editNickname: profile.nickname || '',
            editBio: profile.bio || '',
        });
    },

    onEditNickname(e) {
        this.setData({ editNickname: e.detail.value });
    },

    onEditBio(e) {
        this.setData({ editBio: e.detail.value });
    },

    saveProfile() {
        const nickname = (this.data.editNickname || '').trim();
        if (!nickname) {
            wx.showToast({ title: '昵称不能为空', icon: 'none' });
            return;
        }
        this.setData({ savingProfile: true });
        api.updateProfile({
            nickname,
            bio: (this.data.editBio || '').trim(),
        })
            .then(() => {
                this.setData({ savingProfile: false, showProfileEdit: false });
                wx.showToast({ title: '资料已更新', icon: 'success' });
                return this.loadProfile();
            })
            .then(() => {
                const app = getApp();
                if (app && this.data.profile) {
                    app.globalData.profile = this.data.profile;
                }
            })
            .catch((err) => {
                this.setData({ savingProfile: false });
                wx.showToast({ title: err.msg || '保存失败', icon: 'none' });
            });
    },

    // ---------- 经历档案 ----------
    toggleExpForm() {
        this.setData({ showExpForm: !this.data.showExpForm });
    },

    onExpSceneTap(e) {
        const scene = e.currentTarget.dataset.scene;
        this.setData({ expScene: this.data.expScene === scene ? '' : scene });
    },

    onExpChoiceInput(e) {
        this.setData({ expChoice: e.detail.value });
    },

    onExpYearChange(e) {
        this.setData({ expYearIndex: Number(e.detail.value) });
    },

    onExpOutcomeTap(e) {
        this.setData({ expOutcome: e.currentTarget.dataset.value });
    },

    saveExperience() {
        const scene = this.data.expScene;
        const choice = (this.data.expChoice || '').trim();
        if (!scene) {
            wx.showToast({ title: '选一个场景', icon: 'none' });
            return;
        }
        if (!choice) {
            wx.showToast({ title: '写下当时怎么选的', icon: 'none' });
            return;
        }
        this.setData({ savingExp: true });
        api.addExperience({
            scene,
            choice,
            yearsAgo: YEAR_VALUES[this.data.expYearIndex] || YEAR_VALUES[0],
            outcome: this.data.expOutcome,
        })
            .then(() => {
                this.setData({
                    savingExp: false,
                    showExpForm: false,
                    expChoice: '',
                    expScene: '',
                });
                wx.showToast({ title: '已添加', icon: 'success' });
                return this.loadProfile();
            })
            .catch((err) => {
                this.setData({ savingExp: false });
                wx.showToast({ title: err.msg || '保存失败', icon: 'none' });
            });
    },

    deleteExperience(e) {
        const id = e.currentTarget.dataset.id;
        wx.showModal({
            title: '删除经历',
            content: '删除后不再用于匹配相似建议，确认吗？',
            success: (res) => {
                if (!res.confirm) return;
                api.deleteExperience(id)
                    .then(() => this.loadProfile())
                    .then(() => wx.showToast({ title: '已删除', icon: 'none' }))
                    .catch((err) => wx.showToast({ title: err.msg || '删除失败', icon: 'none' }));
            },
        });
    },
});

function formatDate(value) {
    if (!value) return '';
    const s = String(value).replace('T', ' ');
    return s.length >= 10 ? s.substring(5, 10) : s;
}
