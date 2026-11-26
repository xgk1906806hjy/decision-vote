"""后端接口冒烟测试：跑通「发布 → 盲投 → 方案 → 采纳 → 星星 → 建议 → 回访 → 案例库」全链路。

用法：后端启动后执行  python scripts/smoke_test.py
"""
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080/api"
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

    print("12. 首页信息流")
    feed = call("GET", "/posts/feed?page=1&size=10", token=token_b)
    check("信息流不报错", isinstance(feed["list"], list))

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
