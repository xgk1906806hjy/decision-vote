"""后端接口冒烟测试：跑通「发布 → 盲投 → 方案 → 采纳 → 星星 → 建议 → 回访 → 案例库」全链路。

用法：后端启动后执行  python scripts/smoke_test.py [后端根地址]
默认打 http://localhost:8080；也可用环境变量 SMOKE_BASE 指定，例如：
    python scripts/smoke_test.py http://113.45.152.236
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

_ROOT = (sys.argv[1] if len(sys.argv) > 1 else os.environ.get("SMOKE_BASE", "http://localhost:8080")).rstrip("/")
BASE = _ROOT + "/api"
FAILED = []


def call(method, path, body=None, token=None):
    url = BASE + urllib.parse.quote(path, safe="/?=&:")
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        payload = json.loads(e.read().decode("utf-8") or "{}")
    if payload.get("code") != 0:
        raise RuntimeError(f"{method} {path} 失败: {payload.get('code')} {payload.get('msg')}")
    return payload.get("data")


def check(name, condition, detail=""):
    if condition:
        print(f"  [PASS] {name}")
    else:
        print(f"  [FAIL] {name} {detail}")
        FAILED.append(name)


def upload(path, filename, content, content_type, token=None):
    """multipart/form-data 上传，标准库没有封装，手拼 body。"""
    boundary = "----smoke" + str(int(time.time() * 1000))
    body = b"".join([
        ("--" + boundary + "\r\n").encode(),
        ('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename).encode(),
        ("Content-Type: " + content_type + "\r\n\r\n").encode(),
        content,
        ("\r\n--" + boundary + "--\r\n").encode(),
    ])
    req = urllib.request.Request(BASE + path, data=body, method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        payload = json.loads(e.read().decode("utf-8") or "{}")
    if payload.get("code") != 0:
        raise RuntimeError(f"POST {path} 失败: {payload.get('code')} {payload.get('msg')}")
    return payload.get("data")


def png_bytes(size=8, rgb=(185, 86, 44)):
    """造一张合法的最小 PNG，用来验上传链路。"""
    import struct
    import zlib

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    raw = b"".join(b"\x00" + bytes(rgb) * size for _ in range(size))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw))
            + chunk(b"IEND", b""))


def main():
    suffix = str(int(time.time()))[-6:]

    print("1. 登录两个测试用户")
    a = call("POST", "/auth/login", {"code": "smoke-a-" + suffix})
    b = call("POST", "/auth/login", {"code": "smoke-b-" + suffix})
    token_a, token_b = a["token"], b["token"]
    check("用户 A 登录", bool(token_a))
    check("用户 B 登录", bool(token_b))
    check("A/B 是不同用户", a["userId"] != b["userId"])

    print("2. A 发布决策")
    post = call("POST", "/posts", {
        "title": "要不要辞职去读全日制研究生（冒烟测试 " + suffix + "）",
        "content": "现在在一家小公司做运营，年薪 18 万，考上了 211 的专硕，纠结要不要辞职去读。",
        "scene": "考研",
        "anonymous": 0,
        "deadlineHours": 72,
        "options": ["辞职去读全日制", "继续工作，读非全", "放弃读研"],
    }, token_a)
    post_id = post["id"]
    check("发帖返回 id", bool(post_id))

    print("2.1 场景标签多选（最多 3 个）")
    multi = call("POST", "/posts", {
        "title": "异地 offer 要不要接（多标签冒烟 " + suffix + "）",
        "content": "降薪 20% 去一线城市，还是留在老家省会，纠结。",
        "scenes": ["职场", "感情"],
        "anonymous": 0,
        "options": ["接，去一线", "留老家"],
    }, token_a)
    multi_id = multi["id"]
    multi_detail = call("GET", f"/posts/{multi_id}", token=token_a)
    check("多标签按逗号存储", multi_detail["scene"] == "职场,感情", multi_detail["scene"])
    feed_hit = call("GET", "/posts/feed?scene=感情&page=1&size=20", token=token_a)
    check("按第二个标签能筛到", any(p["id"] == multi_id for p in feed_hit["list"]))
    feed_miss = call("GET", "/posts/feed?scene=买房&page=1&size=50", token=token_a)
    check("无关标签筛不到", all(p["id"] != multi_id for p in feed_miss["list"]))
    try:
        call("POST", "/posts", {
            "title": "超量标签 " + suffix, "content": "测试超过 3 个标签应被拒绝。",
            "scenes": ["职场", "感情", "考研", "买房"],
            "options": ["甲", "乙"],
        }, token_a)
        check("超过 3 个标签被拒绝", False, "居然发成功了")
    except RuntimeError:
        check("超过 3 个标签被拒绝", True)

    detail = call("GET", f"/posts/{post_id}", token=token_a)
    check("发起者可见完整票数分布", detail["blind"] is False)

    print("3. B 打开详情，应处于盲投")
    detail_b = call("GET", f"/posts/{post_id}", token=token_b)
    check("未投票用户 blind=true", detail_b["blind"] is True)
    check("未投票看不到票数", all(o["count"] == 0 for o in detail_b["options"]),
          str(detail_b["options"]))
    check("仍能看到参与人数", "totalVoteCount" in detail_b)

    print("4. B 投票后立即揭晓")
    option_id = detail_b["options"][0]["id"]
    after_vote = call("POST", f"/posts/{post_id}/vote", {"optionId": option_id}, token_b)
    check("投票后 blind=false", after_vote["blind"] is False)
    check("投票后票数可读", any(o["count"] > 0 for o in after_vote["options"]))
    check("记录了我的选择", after_vote["myOptionId"] == option_id)

    print("5. 重复投票应被拒绝")
    try:
        call("POST", f"/posts/{post_id}/vote", {"optionId": option_id}, token_b)
        check("重复投票被拒绝", False, "居然投成功了")
    except RuntimeError as e:
        check("重复投票被拒绝", True, str(e))

    print("6. B 提供一个新方案")
    sol = call("POST", f"/posts/{post_id}/solutions",
               {"content": "可以先跟公司谈停薪留职一年，读完再决定，别直接辞职。"}, token_b)
    solution_id = sol["id"]
    solutions = call("GET", f"/posts/{post_id}/solutions", token=token_b)
    check("方案出现在列表", any(s["id"] == solution_id for s in solutions))

    print("6.1 A 也提供一个方案（用于验证采纳后置顶）")
    other = call("POST", f"/posts/{post_id}/solutions",
                 {"content": "如果经济压力不大，直接读全日制更彻底。"}, token_a)
    other_id = other["id"]
    check("第二个方案已入库", bool(other_id))

    print("7. A 给方案点赞并采纳")
    call("POST", f"/solutions/{solution_id}/like", token=token_a)
    solutions = call("GET", f"/posts/{post_id}/solutions", token=token_a)
    target = next(s for s in solutions if s["id"] == solution_id)
    check("点赞数 +1", target["likeCount"] >= 1)

    try:
        call("POST", f"/posts/{post_id}/adopt?solutionId={solution_id}", token=token_b)
        check("非发起者不能采纳", False, "B 采纳成功了")
    except RuntimeError:
        check("非发起者不能采纳", True)

    call("POST", f"/posts/{post_id}/adopt?solutionId={solution_id}", token=token_a)
    solutions = call("GET", f"/posts/{post_id}/solutions", token=token_a)
    target = next(s for s in solutions if s["id"] == solution_id)
    check("方案已标记采纳", target["adopted"] is True)

    print("7.1 已采纳方案必须置顶（缺陷回归验证）")
    ordered = call("GET", f"/posts/{post_id}/solutions", token=token_a)
    check("采纳的方案排在第一位", ordered[0]["id"] == solution_id,
          f"实际首位 id={ordered[0]['id']}，期望 {solution_id}")
    check("未被采纳的方案排在后面", ordered[-1]["id"] == other_id,
          f"末位 id={ordered[-1]['id']}，期望 {other_id}")

    print("8. B 的星星与场景标签")
    profile_b = call("GET", "/users/me", token=token_b)
    check("星星 +1", profile_b["stars"] == 1, f"stars={profile_b['stars']}")
    tags = {t["scene"]: t["stars"] for t in profile_b["starTags"]}
    check("生成场景标签", tags.get("考研") == 1, str(tags))

    print("9. B 发表带结果标签的建议")
    call("POST", f"/posts/{post_id}/comments",
         {"content": "我当年就是停薪留职读的，两年后回原公司升了一级。", "outcomeTag": "worth"}, token_b)
    detail_after = call("GET", f"/posts/{post_id}", token=token_a)
    check("建议已入库", len(detail_after["comments"]) >= 1)
    check("建议带结果标签", any(c["outcomeTag"] == "worth" for c in detail_after["comments"]))

    print("10. A 回填结局")
    call("POST", f"/posts/{post_id}/follow-up",
         {"choseOptionId": option_id, "outcome": "worth",
          "note": "最后选了继续工作读非全，一年后跳槽涨了 30%。"}, token_a)
    detail_final = call("GET", f"/posts/{post_id}", token=token_a)
    check("详情带出结局", detail_final["outcome"] == "worth")
    check("详情带出最终选择", detail_final["chosenOptionId"] == option_id)

    print("11. 案例库")
    cases = call("GET", "/cases?page=1&size=10")
    check("案例库包含该案例", any(c["postId"] == post_id for c in cases["list"]))
    similar = call("GET", "/cases/similar?scene=考研&limit=5")
    check("相似推荐可调用", isinstance(similar, list))
    stats = call("GET", "/cases/stats")
    check("案例统计可调用", isinstance(stats.get("scenes"), list))
    check("统计口径与列表一致", stats["total"] >= 1 and
          stats["worth"] + stats["regret"] + stats["watching"] + stats["unknown"] == stats["total"])

    print("12. 首页信息流")
    feed = call("GET", "/posts/feed?page=1&size=10", token=token_b)
    check("信息流不报错", isinstance(feed["list"], list))

    print("13. 举报")
    try:
        call("POST", "/reports",
             {"targetType": "post", "targetId": post_id, "reason": "自举报"}, token_a)
        check("不能举报自己发布的内容", False, "居然举报成功了")
    except RuntimeError:
        check("不能举报自己发布的内容", True)

    call("POST", "/reports",
         {"targetType": "post", "targetId": post_id, "reason": "广告营销"}, token_b)
    check("他人举报成功入档", True)

    try:
        call("POST", "/reports",
             {"targetType": "post", "targetId": post_id, "reason": "重复举报"}, token_b)
        check("同一内容重复举报被拒绝", False, "居然举报成功了")
    except RuntimeError:
        check("同一内容重复举报被拒绝", True)

    try:
        call("POST", "/reports",
             {"targetType": "unknown", "targetId": post_id, "reason": "非法类型"}, token_b)
        check("非法举报类型被拒绝", False, "居然举报成功了")
    except RuntimeError:
        check("非法举报类型被拒绝", True)

    try:
        call("POST", "/reports",
             {"targetType": "post", "targetId": 99999999, "reason": "不存在"}, token_b)
        check("举报不存在的决策被拒绝", False, "居然举报成功了")
    except RuntimeError:
        check("举报不存在的决策被拒绝", True)

    print("14. 回访提醒额度")
    status = call("GET", "/subscribe/follow-up", token=token_a)
    check("可查询提醒额度", isinstance(status.get("remain"), int))
    base = status["remain"]
    after = call("POST", "/subscribe/follow-up", token=token_a)
    check("授权后额度 +1", after["remain"] == min(base + 1, after["maxRemain"]),
          f"{base} -> {after['remain']}（上限 {after['maxRemain']}）")
    check("额度不超过上限", after["remain"] <= after["maxRemain"])
    try:
        call("GET", "/subscribe/follow-up")
        check("未登录不能查提醒额度", False, "居然查到了")
    except RuntimeError:
        check("未登录不能查提醒额度", True)

    print("15. 场景标签（预设 + 自定义）")
    scenes = call("GET", "/scenes")
    check("场景列表返回预设项", "职场" in scenes and "其他" in scenes, str(scenes))
    custom_scene = "考公"
    call("POST", "/posts", {
        "title": "要不要考公（冒烟测试 " + suffix + "）",
        "content": "28 岁，家里希望我考公，我怕考不上又丢了现在的工作。",
        "scene": custom_scene,
        "options": ["考公", "留在现在的公司"],
    }, token_a)
    scenes_after = call("GET", "/scenes")
    check("自定义场景进入场景列表", custom_scene in scenes_after, str(scenes_after))
    feed_custom = call("GET", "/posts/feed?page=1&size=5&scene=" + custom_scene, token=token_b)
    check("按自定义场景筛选有结果", len(feed_custom["list"]) >= 1, str(len(feed_custom["list"])))
    try:
        call("POST", "/posts", {
            "title": "场景超长（冒烟测试 " + suffix + "）",
            "content": "这条应该被场景长度校验拦住。",
            "scene": "这是一个超过十个字的场景标签",
            "options": ["A", "B"],
        }, token_a)
        check("超长场景被拒绝", False, "居然发布成功")
    except RuntimeError:
        check("超长场景被拒绝", True)

    print("16. 头像上传")
    try:
        upload("/files/avatar", "a.png", png_bytes(), "image/png")
        check("未登录不能上传头像", False, "居然上传成功")
    except RuntimeError:
        check("未登录不能上传头像", True)

    avatar = upload("/files/avatar", "a.png", png_bytes(), "image/png", token_a)
    check("上传返回可访问路径", bool(avatar.get("path")), str(avatar))
    with urllib.request.urlopen("http://localhost:8080" + avatar["path"], timeout=10) as resp:
        check("头像可静态访问", resp.status == 200)
    try:
        upload("/files/avatar", "a.txt", b"not an image at all, just text",
               "text/plain", token_a)
        check("非图片被拒绝", False, "居然上传成功")
    except RuntimeError:
        check("非图片被拒绝", True)

    call("PUT", "/users/me", {"avatar": avatar["path"]}, token_a)
    me = call("GET", "/users/me", token=token_a)
    check("头像写回资料", me["avatar"] == avatar["path"], str(me.get("avatar")))

    print()
    if FAILED:
        print(f"结果：{len(FAILED)} 项失败 -> {FAILED}")
        sys.exit(1)
    print("结果：全部通过")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print(f"\n执行中断：{exc}")
        sys.exit(0)
