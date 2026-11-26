const api = require('../../utils/api.js');

const SCENES = ['职场', '感情', '考研', '留学', '买房', '创业', '家庭', '其他'];
const OUTCOMES = [
    { value: 'worth', label: '值得' },
    { value: 'regret', label: '后悔' },
    { value: 'watching', label: '还在观察' },
];
const YEAR_OPTIONS = ['1 年内', '1–2 年', '3–5 年', '5 年以上'];

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

        activeTab: TAB_MINE,
        posts: [],
        listLoading: false,

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
        this.loadList(TAB_MINE);
    },

    onShow() {
        if (this.data.viewState === 'ready') {
            this.loadProfile();
            this.loadPending();
            this.loadList(this.data.activeTab);
        }
    },

    onPullDownRefresh() {
        this.loadProfile()
            .then(() => this.loadPending())
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
            yearsAgo: this.data.expYearIndex + 1,
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
