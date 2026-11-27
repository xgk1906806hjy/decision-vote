"""小程序静态走查：JS 语法（node --check）、JSON 合法性、WXML 标签配对、WXSS 花括号配平。

用法：python scripts/wxml_check.py [miniprogram目录]
不能替代开发者工具编译与真机视觉走查，只拦结构性错误。
"""
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else
                    pathlib.Path(__file__).resolve().parent.parent / "miniprogram")
FAILED = []


def fail(path, msg):
    FAILED.append(f"{path.relative_to(ROOT)}: {msg}")
    print(f"  [FAIL] {path.relative_to(ROOT)}: {msg}")


def check_js(path):
    r = subprocess.run(["node", "--check", str(path)], capture_output=True, text=True)
    if r.returncode != 0:
        fail(path, r.stderr.strip().splitlines()[-1] if r.stderr else "语法错误")


def check_json(path):
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        fail(path, f"JSON 不合法: {e}")


VOID_OK = {"input", "image", "import", "include", "wxs"}  # 允许自闭合的常见标签


def check_wxml(path):
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    stack = []
    for m in re.finditer(r"<(/?)([a-zA-Z][\w-]*)((?:\"[^\"]*\"|'[^']*'|[^>\"'])*?)(/?)>", text):
        close, tag, attrs, selfclose = m.groups()
        if selfclose == "/":
            continue
        if close == "/":
            if not stack or stack[-1][0] != tag:
                fail(path, f"标签不配对：</{tag}> 栈顶={stack[-1][0] if stack else '空'}")
                return
            stack.pop()
        else:
            stack.append((tag, m.start()))
    if stack:
        fail(path, f"未闭合标签：{[t for t, _ in stack]}")


def check_wxss(path):
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    depth = 0
    for ch in text:
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth < 0:
                fail(path, "花括号多余 }")
                return
    if depth != 0:
        fail(path, f"花括号不配平，差 {depth}")
    # keyframes 内不允许 !important（项目踩过的坑）
    for m in re.finditer(r"@keyframes[^{]*\{(?:[^{}]|\{[^{}]*\})*\}", text):
        if "!important" in m.group(0):
            fail(path, "keyframes 内出现 !important")


def main():
    counts = {"js": 0, "json": 0, "wxml": 0, "wxss": 0}
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or ".workbuddy" in path.parts:
            continue
        suffix = path.suffix.lstrip(".")
        if suffix == "js":
            check_js(path)
        elif suffix == "json":
            check_json(path)
        elif suffix == "wxml":
            check_wxml(path)
        elif suffix == "wxss":
            check_wxss(path)
        else:
            continue
        counts[suffix] += 1
    print(f"检查完成：{counts}")
    if FAILED:
        print(f"结果：{len(FAILED)} 处问题")
        sys.exit(1)
    print("结果：全部通过")


if __name__ == "__main__":
    main()