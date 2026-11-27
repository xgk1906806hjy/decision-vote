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
        // 场景标签多选，最多 3 个；sceneSelected 供 WXML O(1) 判断选中态
        selectedScenes: [],
        sceneSelected: {},
        // 自定义场景：输入后点「添加」进入已选列表
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
        const selected = this.data.selectedScenes.slice();
        const idx = selected.indexOf(scene);
        if (idx >= 0) {
            selected.splice(idx, 1);
        } else {
            if (selected.length >= 3) {
                wx.showToast({ title: '最多选 3 个标签', icon: 'none' });
                return;
            }
            selected.push(scene);
        }
        this.applyScenes(selected);
    },

    applyScenes(selected) {
        const map = {};
        selected.forEach((s) => { map[s] = true; });
        this.setData({ selectedScenes: selected, sceneSelected: map });
        if (selected.length) {
            this.loadSimilarCases(selected[0]);
        } else {
            this.setData({ similarCases: [] });
        }
    },

    toggleCustomScene() {
        this.setData({ customOpen: !this.data.customOpen });
    },

    onCustomSceneInput(e) {
        this.setData({ customScene: e.detail.value });
    },

    addCustomScene() {
        const tag = (this.data.customScene || '').trim();
        if (tag.length < 2) {
            wx.showToast({ title: '自定义标签至少 2 个字', icon: 'none' });
            return;
        }
        if (tag.length > 10) {
            wx.showToast({ title: '自定义标签最多 10 个字', icon: 'none' });
            return;
        }
        if (tag.indexOf(',') >= 0 || tag.indexOf('，') >= 0) {
            wx.showToast({ title: '标签里不能有逗号', icon: 'none' });
            return;
        }
        const selected = this.data.selectedScenes.slice();
        if (selected.indexOf(tag) >= 0) {
            wx.showToast({ title: '已选过这个标签', icon: 'none' });
            return;
        }
        if (selected.length >= 3) {
            wx.showToast({ title: '最多选 3 个标签', icon: 'none' });
            return;
        }
        selected.push(tag);
        const scenes = this.data.scenes.indexOf(tag) >= 0
            ? this.data.scenes : this.data.scenes.concat(tag);
        this.setData({ scenes, customScene: '', customOpen: false });
        this.applyScenes(selected);
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
        if (!api.getToken()) {
            api.goLogin();
            return;
        }
        const title = (this.data.title || '').trim();
        const content = (this.data.content || '').trim();
        const scenes = this.data.selectedScenes;
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
        if (!scenes.length) {
            wx.showToast({ title: '选 1–3 个场景标签', icon: 'none' });
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
            scenes,
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
