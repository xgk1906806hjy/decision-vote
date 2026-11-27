const api = require('../../utils/api.js');

const SCENES = ['职场', '感情', '考研', '留学', '买房', '创业', '家庭', '其他'];

const DEADLINES = [
    { label: '不设截止', value: null },
    { label: '1 天', value: 24 },
    { label: '3 天', value: 72 },
    { label: '7 天', value: 168 },
];

Page({
    data: {
        scenes: SCENES,
        deadlines: DEADLINES,
        deadlineIndex: 2,

        title: '',
        content: '',
        scene: '',
        // 自定义场景：打开输入框后 scene 直接取输入值，与预设 chip 互斥
        customOpen: false,
        customScene: '',
        anonymous: false,
        options: [
            { key: 1, value: '' },
            { key: 2, value: '' },
        ],
        optionSeq: 2,

        similarCases: [],
        loadingSimilar: false,
        submitting: false,
    },

    // ---------- 基础输入 ----------
    onLoad() {
        this.loadScenes();
    },

    /**
     * 场景 chip = 预设 8 个 + 站内已被使用的自定义场景。
     * 拉不到就用本地预设兜底，不阻塞发布。
     */
    loadScenes() {
        api.scenes()
            .then((list) => {
                if (Array.isArray(list) && list.length) {
                    this.setData({ scenes: list });
                }
            })
            .catch(() => {});
    },

    onTitleInput(e) {
        this.setData({ title: e.detail.value });
    },

    onContentInput(e) {
        this.setData({ content: e.detail.value });
    },

    onSceneTap(e) {
        const scene = e.currentTarget.dataset.scene;
        this.setData({
            scene: this.data.scene === scene ? '' : scene,
            customOpen: false,
        });
        if (this.data.scene) {
            this.loadSimilarCases(this.data.scene);
        } else {
            this.setData({ similarCases: [] });
        }
    },

    toggleCustomScene() {
        const open = !this.data.customOpen;
        const scene = open ? (this.data.customScene || '').trim() : '';
        this.setData({ customOpen: open, scene });
        if (scene) {
            this.loadSimilarCases(scene);
        } else {
            this.setData({ similarCases: [] });
        }
    },

    onCustomSceneInput(e) {
        const customScene = e.detail.value;
        this.setData({ customScene, scene: customScene.trim() });
    },

    onAnonymousChange(e) {
        this.setData({ anonymous: e.detail.value });
    },

    onDeadlineChange(e) {
        this.setData({ deadlineIndex: Number(e.detail.value) });
    },

    // ---------- 选项 ----------
    onOptionInput(e) {
        const index = Number(e.currentTarget.dataset.index);
        const options = this.data.options.slice();
        options[index] = Object.assign({}, options[index], { value: e.detail.value });
        this.setData({ options });
    },

    addOption() {
        if (this.data.options.length >= 6) {
            wx.showToast({ title: '最多 6 个选项', icon: 'none' });
            return;
        }
        const seq = this.data.optionSeq + 1;
        this.setData({
            optionSeq: seq,
            options: this.data.options.concat({ key: seq, value: '' }),
        });
    },

    removeOption(e) {
        if (this.data.options.length <= 2) {
            wx.showToast({ title: '至少保留 2 个选项', icon: 'none' });
            return;
        }
        const index = Number(e.currentTarget.dataset.index);
        const options = this.data.options.slice();
        options.splice(index, 1);
        this.setData({ options });
    },

    // ---------- 相似案例 ----------
    loadSimilarCases(scene) {
        this.setData({ loadingSimilar: true });
        api.similarCases({ scene, limit: 3 })
            .then((list) => {
                this.setData({ similarCases: list || [], loadingSimilar: false });
            })
            .catch(() => {
                this.setData({ similarCases: [], loadingSimilar: false });
            });
    },

    goCase(e) {
        const id = e.currentTarget.dataset.id;
        wx.navigateTo({ url: '/pages/detail/detail?id=' + id });
    },

    // ---------- 提交 ----------
    submit() {
        const title = (this.data.title || '').trim();
        const content = (this.data.content || '').trim();
        const scene = (this.data.scene || '').trim();
        const options = this.data.options
            .map((o) => (o.value || '').trim())
            .filter((o) => o.length > 0);

        if (!title) {
            wx.showToast({ title: '给这个决策起个标题', icon: 'none' });
            return;
        }
        if (!content) {
            wx.showToast({ title: '把处境说清楚一点', icon: 'none' });
            return;
        }
        if (!scene) {
            wx.showToast({ title: this.data.customOpen ? '写下你的自定义场景' : '选一个所属场景', icon: 'none' });
            return;
        }
        if (this.data.customOpen && scene.length < 2) {
            wx.showToast({ title: '自定义标签至少 2 个字', icon: 'none' });
            return;
        }
        if (scene.length > 10) {
            wx.showToast({ title: '场景标签最多 10 个字', icon: 'none' });
            return;
        }
        if (options.length < 2) {
            wx.showToast({ title: '至少填 2 个选项', icon: 'none' });
            return;
        }

        const deadline = DEADLINES[this.data.deadlineIndex];
        const payload = {
            title,
            content,
            scene,
            anonymous: this.data.anonymous ? 1 : 0,
            options,
        };
        if (deadline && deadline.value) {
            payload.deadlineHours = deadline.value;
        }

        this.setData({ submitting: true });
        api.createPost(payload)
            .then((result) => {
                this.setData({ submitting: false });
                wx.showToast({ title: '已发布', icon: 'success' });
                setTimeout(() => {
                    wx.redirectTo({ url: '/pages/detail/detail?id=' + result.id });
                }, 600);
            })
            .catch((err) => {
                this.setData({ submitting: false });
                wx.showToast({ title: err.msg || '发布失败', icon: 'none' });
            });
    },
});
