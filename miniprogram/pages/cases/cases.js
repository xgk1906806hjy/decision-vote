const api = require('../../utils/api.js');

const SCENES = ['职场', '感情', '考研', '留学', '买房', '创业', '家庭', '其他'];
const PAGE_SIZE = 10;

Page({
    data: {
        scenes: SCENES,
        activeScene: '',
        keyword: '',

        cases: [],
        stats: null,
        page: 1,
        hasMore: true,
        loading: false,
        viewState: 'loading',
        errorMessage: '',
    },

    onLoad() {
        this.loadScenes();
        this.loadStats();
        this.loadCases(true);
    },

    /** 场景 chip 跟随后端：预设 + 站内自定义；失败保持本地预设 */
    loadScenes() {
        api.scenes()
            .then((list) => {
                if (Array.isArray(list) && list.length) {
                    this.setData({ scenes: list });
                }
            })
            .catch(() => {});
    },

    onShow() {
        if (this.data.viewState === 'ready' || this.data.viewState === 'empty') {
            this.loadScenes();
            this.loadStats();
            this.loadCases(true);
        }
    },

    onPullDownRefresh() {
        Promise.all([this.loadCases(true), this.loadStats()])
            .then(() => wx.stopPullDownRefresh());
    },

    onReachBottom() {
        if (this.data.hasMore && !this.data.loading) {
            this.loadCases(false);
        }
    },

    // ---------- 交互 ----------
    onSceneTap(e) {
        const scene = e.currentTarget.dataset.scene;
        this.setData({ activeScene: scene });
        this.loadStats();
        this.loadCases(true);
    },

    onKeywordInput(e) {
        this.setData({ keyword: e.detail.value });
    },

    onSearch() {
        this.loadCases(true);
    },

    clearKeyword() {
        this.setData({ keyword: '' });
        this.loadCases(true);
    },

    goCase(e) {
        const id = e.currentTarget.dataset.id;
        wx.navigateTo({ url: '/pages/detail/detail?id=' + id });
    },

    handleRetry() {
        this.loadStats();
        this.loadCases(true);
    },

    // ---------- 数据 ----------
    loadStats() {
        const params = {};
        if (this.data.activeScene) {
            params.scene = this.data.activeScene;
        }
        return api.caseStats(params)
            .then((stats) => this.setData({ stats }))
            .catch(() => this.setData({ stats: null }));
    },

    loadCases(reset) {
        const page = reset ? 1 : this.data.page + 1;
        const params = { page, size: PAGE_SIZE };
        if (this.data.activeScene) {
            params.scene = this.data.activeScene;
        }
        const keyword = (this.data.keyword || '').trim();
        if (keyword) {
            params.keyword = keyword;
        }

        if (reset) {
            this.setData({ loading: true, viewState: this.data.cases.length ? this.data.viewState : 'loading' });
        } else {
            this.setData({ loading: true });
        }

        return api.cases(params)
            .then((result) => {
                const incoming = (result.list || []).map((item) => Object.assign({}, item, {
                    answeredText: formatDate(item.answeredAt),
                }));
                const list = reset ? incoming : this.data.cases.concat(incoming);
                let viewState = 'ready';
                if (list.length === 0) {
                    viewState = (keyword || this.data.activeScene) ? 'empty-filter' : 'empty';
                }
                this.setData({
                    cases: list,
                    page,
                    hasMore: !!result.hasMore,
                    loading: false,
                    viewState,
                });
            })
            .catch((err) => {
                this.setData({
                    loading: false,
                    viewState: this.data.cases.length ? 'ready' : 'error',
                    errorMessage: err.msg || '加载失败',
                });
            });
    },
});

function formatDate(value) {
    if (!value) return '';
    const s = String(value).replace('T', ' ');
    return s.length >= 10 ? s.substring(0, 10) : s;
}
