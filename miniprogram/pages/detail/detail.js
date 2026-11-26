const api = require('../../utils/api.js');

const OUTCOME_LABELS = { worth: '值得', regret: '后悔', watching: '观察中' };

/** 后端时间格式为 yyyy-MM-dd HH:mm:ss，截到分钟展示 */
function formatTime(value) {
    if (!value) return '';
    const s = String(value).replace('T', ' ');
    return s.length >= 16 ? s.slice(0, 16) : s;
}

/** 把后端原始字段加工成视图需要的展示字段 */
function decorate(post) {
    if (!post) return post;
    const options = (post.options || []).map((opt) => Object.assign({}, opt, {
        chosen: post.chosenOptionId != null && post.chosenOptionId === opt.id,
    }));
    const comments = (post.comments || []).map((c) => Object.assign({}, c, {
        outcomeLabel: OUTCOME_LABELS[c.outcomeTag] || '',
        timeText: formatTime(c.createdAt),
    }));
    return Object.assign({}, post, {
        options,
        comments,
        outcomeLabel: OUTCOME_LABELS[post.outcome] || '',
        timeText: formatTime(post.createdAt),
        deadlineText: formatTime(post.deadline),
    });
}

Page({
    data: {
        postId: null,
        post: null,
        solutions: [],
        viewState: 'loading',
        errorMessage: '',

        // 投票
        selectedOptionId: null,
        voting: false,
        voted: false,
        revealAnim: false,

        // 方案输入
        solutionInput: '',
        addingSolution: false,

        // 评论输入
        commentInput: '',
        addingComment: false,
        outcomeTag: '',

        // 回访输入（仅发起者可见）
        showFollowUp: false,
        followOutcome: 'worth',
        followNote: '',
        followChosenId: null,
        submittingFollowUp: false,
    },

    onLoad(options) {
        const id = options.id;
        if (!id) {
            this.setData({ viewState: 'error', errorMessage: '缺少话题编号' });
            return;
        }
        this.setData({ postId: id });
        this.loadAll();
    },

    loadAll() {
        this.setData({ viewState: 'loading' });
        Promise.all([
            api.detail(this.data.postId),
            api.solutions(this.data.postId),
        ])
            .then((result) => {
                const post = decorate(result[0]);
                this.setData({
                    post,
                    solutions: result[1] || [],
                    voted: post.myOptionId != null,
                    selectedOptionId: post.myOptionId || null,
                    followChosenId: post.chosenOptionId || null,
                    followOutcome: post.outcome || 'worth',
                    followNote: post.outcomeNote || '',
                    viewState: 'ready',
                });
            })
            .catch((err) => {
                this.setData({ viewState: 'error', errorMessage: err.msg || '加载失败' });
            });
    },

    // ---------- 投票 ----------
    onSelectOption(e) {
        if (this.data.voted || this.data.post.status === 'closed') {
            return;
        }
        this.setData({ selectedOptionId: e.currentTarget.dataset.id });
    },

    submitVote() {
        if (!this.data.selectedOptionId) {
            wx.showToast({ title: '请先选择一个选项', icon: 'none' });
            return;
        }
        if (this.data.voted) {
            return;
        }
        this.setData({ voting: true });
        api.vote(this.data.postId, this.data.selectedOptionId)
            .then((post) => {
                this.setData({
                    post: decorate(post),
                    voted: true,
                    voting: false,
                    revealAnim: true,
                });
                wx.vibrateShort({ type: 'light' });
                wx.showToast({ title: '已投票，结果揭晓', icon: 'none' });
                return api.solutions(this.data.postId);
            })
            .then((solutions) => {
                this.setData({ solutions: solutions || [] });
            })
            .catch((err) => {
                this.setData({ voting: false });
                wx.showToast({ title: err.msg || '投票失败', icon: 'none' });
            });
    },

    // ---------- 方案 ----------
    onSolutionInput(e) {
        this.setData({ solutionInput: e.detail.value });
    },

    addSolution() {
        const content = (this.data.solutionInput || '').trim();
        if (!content) {
            wx.showToast({ title: '请写下你的方案', icon: 'none' });
            return;
        }
        this.setData({ addingSolution: true });
        api.addSolution(this.data.postId, content)
            .then(() => {
                this.setData({ solutionInput: '' });
                wx.showToast({ title: '方案已提交', icon: 'success' });
                return api.solutions(this.data.postId);
            })
            .then((solutions) => {
                this.setData({ solutions: solutions || [], addingSolution: false });
            })
            .catch((err) => {
                this.setData({ addingSolution: false });
                wx.showToast({ title: err.msg || '提交失败', icon: 'none' });
            });
    },

    likeSolution(e) {
        const id = e.currentTarget.dataset.id;
        api.likeSolution(id)
            .then(() => api.solutions(this.data.postId))
            .then((solutions) => this.setData({ solutions: solutions || [] }))
            .catch((err) => wx.showToast({ title: err.msg || '操作失败', icon: 'none' }));
    },

    adoptSolution(e) {
        const id = e.currentTarget.dataset.id;
        wx.showModal({
            title: '采纳方案',
            content: '采纳后方案作者将获得一颗星星及对应领域标签，确认吗？',
            success: (res) => {
                if (!res.confirm) return;
                api.adoptSolution(this.data.postId, id)
                    .then(() => api.solutions(this.data.postId))
                    .then((solutions) => {
                        this.setData({ solutions: solutions || [] });
                        wx.showToast({ title: '已采纳', icon: 'success' });
                    })
                    .catch((err) => wx.showToast({ title: err.msg || '操作失败', icon: 'none' }));
            },
        });
    },

    // ---------- 评论 ----------
    onCommentInput(e) {
        this.setData({ commentInput: e.detail.value });
    },

    onOutcomeTag(e) {
        const tag = e.currentTarget.dataset.tag;
        this.setData({ outcomeTag: this.data.outcomeTag === tag ? '' : tag });
    },

    addComment() {
        const content = (this.data.commentInput || '').trim();
        if (!content) {
            wx.showToast({ title: '请写下你的建议', icon: 'none' });
            return;
        }
        this.setData({ addingComment: true });
        const payload = { content };
        if (this.data.outcomeTag) {
            payload.outcomeTag = this.data.outcomeTag;
        }
        api.comments(this.data.postId, payload)
            .then(() => {
                this.setData({ commentInput: '', outcomeTag: '', addingComment: false });
                wx.showToast({ title: '建议已发布', icon: 'success' });
                return api.detail(this.data.postId);
            })
            .then((post) => this.setData({ post: decorate(post) }))
            .catch((err) => {
                this.setData({ addingComment: false });
                wx.showToast({ title: err.msg || '发布失败', icon: 'none' });
            });
    },

    // ---------- 回访 ----------
    toggleFollowUp() {
        this.setData({ showFollowUp: !this.data.showFollowUp });
    },

    onOutcomeChange(e) {
        this.setData({ followOutcome: e.currentTarget.dataset.value });
    },

    onFollowChosen(e) {
        const id = e.currentTarget.dataset.id;
        this.setData({ followChosenId: this.data.followChosenId === id ? null : id });
    },

    onFollowNote(e) {
        this.setData({ followNote: e.detail.value });
    },

    submitFollowUp() {
        if (!this.data.followChosenId) {
            wx.showToast({ title: '请选择最终结果', icon: 'none' });
            return;
        }
        this.setData({ submittingFollowUp: true });
        const payload = {
            choseOptionId: this.data.followChosenId,
            outcome: this.data.followOutcome,
            note: this.data.followNote,
        };
        api.submitFollowUp(this.data.postId, payload)
            .then(() => {
                this.setData({ submittingFollowUp: false, showFollowUp: false });
                wx.showToast({ title: '已记录，进入案例库', icon: 'none' });
                return api.detail(this.data.postId);
            })
            .then((post) => this.setData({ post: decorate(post) }))
            .catch((err) => {
                this.setData({ submittingFollowUp: false });
                wx.showToast({ title: err.msg || '提交失败', icon: 'none' });
            });
    },

    handleRetry() {
        this.loadAll();
    },
});
