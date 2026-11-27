# 功能文档索引

本目录按**功能**组织，一个功能一个目录，每个目录固定两份文件：

| 文件 | 用途 | 维护方式 |
|---|---|---|
| `功能逻辑说明.md` | 这个功能是什么、怎么走、规则是什么、代码在哪 | 功能变更时同步更新，保持与代码一致 |
| `改动记录.md` | 这个功能改过什么 | **追加式**，最新一条在最上方，只增不改 |

## 功能清单

| 编号 | 功能 | 后端 | 前端 | 状态 |
|---|---|---|---|---|
| [F01](F01-微信登录与身份/功能逻辑说明.md) | 微信登录与身份 | `AuthService` / `WxApiService` / `FileController` / `JwtUtil` | `pages/login` / `api.login` / `app.onLaunch` | ✅ 已实现（含登录页与头像上传；生产需关 mock） |
| [F02](F02-个人资料与经历档案/功能逻辑说明.md) | 个人资料与经历档案 | `UserService` / `FileController` | `pages/profile` | ✅ 已实现（含头像更换） |
| [F03](F03-发起决策/功能逻辑说明.md) | 发起决策 | `PostService.create` | `pages/create` | ✅ 已实现 |
| [F04](F04-决策信息流/功能逻辑说明.md) | 决策信息流 | `PostService.feed` | `pages/index` | ✅ 已实现 |
| [F05](F05-投票与盲投/功能逻辑说明.md) | 投票与盲投 | `VoteService` / `PostService.buildCards` | `pages/detail` 投票区 | ✅ 已实现 |
| [F06](F06-提供方案与点赞/功能逻辑说明.md) | 提供方案与点赞 | `SolutionService.create/like` | `pages/detail` 方案区 | ✅ 已实现 |
| [F07](F07-方案采纳与星星标签/功能逻辑说明.md) | 方案采纳与星星标签 | `SolutionService.adopt` | `pages/detail` 采纳按钮 | ✅ 已实现 |
| [F08](F08-过来人建议/功能逻辑说明.md) | 过来人建议（评论） | `CommentService` | `pages/detail` 建议区 | ✅ 已实现（含“我经历过/路过”身份） |
| [F09](F09-决策回访与结局归档/功能逻辑说明.md) | 决策回访与结局归档 | `FollowUpService` / `FollowUpReminderService` | `pages/detail` 回访区 / `pages/profile` 待回访 + 提醒 | ✅ 已实现（含订阅消息推送） |
| [F10](F10-案例库与相似推荐/功能逻辑说明.md) | 案例库与相似推荐 | `CaseService` | `pages/cases` / `pages/create` | ✅ 已实现 |
| [F11](F11-内容安全与举报/功能逻辑说明.md) | 内容安全与举报 | `ContentAuditService` / `WxApiService` / `ReportService` / `ReportAdminService` | `pages/detail` 举报入口 / `admin/reports.html` | ✅ 已实现（生产需配真实微信与 `ADMIN_TOKEN`） |
| [F12](F12-前端工程骨架与请求层/功能逻辑说明.md) | 前端工程骨架与请求层 | — | `app.js` / `utils/api.js` / `app.wxss` | ✅ 已实现 |
| [F13](F13-自定义场景标签/功能逻辑说明.md) | 自定义场景标签 | `SceneService` | `pages/create` / `pages/index` / `pages/cases` | ✅ 已实现 |

## 约定

**接口前缀**：`/api`，返回体统一 `{ code, msg, data }`，`code = 0` 表示成功。

**鉴权**：请求头 `Authorization: Bearer {token}`。后端拦截器只解析不拦截，具体接口用 `UserContext.require()` 决定是否必须登录；未登录调用必需登录的接口会返回 `401`。

**时间格式**：`yyyy-MM-dd HH:mm:ss`（GMT+8），前端统一截断展示。

**结果标签**：`worth` = 值得 / `regret` = 后悔 / `watching` = 仍在观察，三处复用（建议、经历档案、回访）。

**场景标签**：预设 8 个（职场、感情、考研、留学、买房、创业、家庭、其他）由 `SceneService.PRESET` 与前端本地兜底各存一份；用户可自定义（2–10 字），统一走 `GET /api/scenes` 下发。

## 相关文档

- [需求文档](../01-需求文档.md) — 产品定位、功能点编号（F-xx）、业务规则
- [项目文档](../02-项目文档.md) — 技术栈、数据库全表、接口全量清单、部署与上线检查
- 本目录 `F-xx` 是**功能模块编号**，与需求文档里的 `F-xx` **功能点编号**不是同一套体系：一个功能模块通常覆盖多个功能点。
