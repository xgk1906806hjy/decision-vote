const api = require('../../utils/api.js');

const SCENES = ['职场', '感情', '考研', '留学', '买房', '创业', '家庭', '其他'];
const PAGE_SIZE = 10;

Page({
    data: {
        scenes: SCENES,
        activeScene: '',
        posts: [],
        page: 1,
        hasMore: true,
        loadingMore: false,
        viewState: 'loading',
        errorMessage: '',
    },

    onLoad() {
        this.loadScenes();
        this.loadFeed(true);
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
        // 从发布页返回时刷新；场景也重拉，新写的自定义标签要能立刻出现在 chip 里
        if (this.data.viewState === 'ready' || this.data.viewState === 'empty') {
            this.loadScenes();
            this.loadFeed(true);
        }
    },

    onPullDownRefresh() {
        this.loadFeed(true).then(() => wx.stopPullDownRefresh());
    },

    onReachBottom() {
        if (this.data.hasMore && !this.data.loadingMore && this.data.viewState === 'ready') {
            this.loadFeed(false);
        }
    },

    onSceneTap(e) {
        const scene = e.currentTarget.dataset.scene;
        if (scene === this.data.activeScene) {
            return;
        }
        this.setData({ activeScene: scene, posts: [], viewState: 'loading' });
        this.loadFeed(true);
    },

    loadFeed(reset) {
        const page = reset ? 1 : this.data.page + 1;
        const params = { page, size: PAGE_SIZE };
        if (this.data.activeScene) {
            params.scene = this.data.activeScene;
        }

        if (!reset) {
            this.setData({ loadingMore: true });
        }

        return api.feed(params)
            .then((result) => {
                const incoming = (result.list || []).map((p) => Object.assign({}, p, {
                    timeText: formatTime(p.createdAt),
                    sceneTags: (p.scene || '').split(',').filter(Boolean),
                }));
                const posts = reset ? incoming : this.data.posts.concat(incoming);
                this.setData({
                    posts,
                    page,
                    hasMore: !!result.hasMore,
                    loadingMore: false,
                    viewState: posts.length === 0 ? 'empty' : 'ready',
                });
            })
            .catch((err) => {
                this.setData({
                    loadingMore: false,
                    viewState: this.data.posts.length ? 'ready' : 'error',
                    errorMessage: err.msg || '加载失败',
                });
            });
    },

    goDetail(e) {
        const id = e.currentTarget.dataset.id;
        wx.navigateTo({ url: '/pages/detail/detail?id=' + id });
    },

    goCreate() {
        wx.navigateTo({ url: '/pages/create/create' });
    },

    goCases() {
        wx.switchTab({ url: '/pages/cases/cases' });
    },
});

function formatTime(value) {
    if (!value) return '';
    const s = String(value).replace('T', ' ');
    return s.length > 16 ? s.substring(5, 16) : s;
}
