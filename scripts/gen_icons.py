"""
路口 · 图标生成脚本
==================
设计：暖橙渐变圆角底 + 白色「分岔路口」符号（一条路走到分岔点，分成两条方向）
含义：人生决策就是在路口做选择；三个节点分别代表「现在的你」与「两条可能的路」。

全部为程序化绘制，不含外部位图，可随时重跑复现。
用法：python scripts/gen_icons.py
输出：miniprogram/assets/icons/
"""
import math
import pathlib

from PIL import Image, ImageDraw, ImageFilter

OUT = pathlib.Path(__file__).resolve().parent.parent / "miniprogram" / "assets" / "icons"
OUT.mkdir(parents=True, exist_ok=True)

# ---- 与 app.wxss 完全一致的暖色令牌 ----
C_TL = (234, 151, 87)    # #EA9757 左上（受光面）
C_TR = (208, 103, 47)    # #D0672F 右上
C_BL = (194, 88, 38)     # #C25826 左下
C_BR = (155, 63, 24)     # #9B3F18 右下（暗部）
WHITE = (255, 255, 255)
SHADOW = (122, 52, 19)   # #7A3413 路的投影


def gradient(size: int) -> Image.Image:
    """四角插值渐变：用 2x2 像素 bicubic 放大，得到柔和的斜向暖橙渐变。"""
    base = Image.new("RGB", (2, 2))
    base.putpixel((0, 0), C_TL)
    base.putpixel((1, 0), C_TR)
    base.putpixel((0, 1), C_BL)
    base.putpixel((1, 1), C_BR)
    return base.resize((size, size), Image.BICUBIC).convert("RGBA")


def rounded_mask(size: int, radius: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def radial_glow(size: int, strength: int = 42, cx_ratio: float = 0.5, cy_ratio: float = 0.5) -> Image.Image:
    """柔光，让纯色底有呼吸感。可指定光源位置，模拟左上打光。"""
    small = 256
    g = Image.new("L", (small, small), 0)
    d = ImageDraw.Draw(g)
    cx, cy = small * cx_ratio, small * cy_ratio
    r = small * 0.46
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=strength)
    g = g.filter(ImageFilter.GaussianBlur(small * 0.17))
    g = g.resize((size, size), Image.BICUBIC)
    layer = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    layer.putalpha(g)
    return layer


def draw_path(img: Image.Image, scale: float, offset=(0, 0), color=WHITE, alpha=255, blur=0):
    """绘制「分岔路口」符号。坐标以 1024 设计稿为基准。"""
    d = ImageDraw.Draw(img)
    ox, oy = offset
    k = scale

    def P(x, y):
        return (ox + x * k, oy + y * k)

    bottom, fork, left, right = (512, 748), (512, 524), (352, 306), (672, 306)
    lw = int(70 * k)
    fill = color + (alpha,)

    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)

    # 三条路段
    ld.line([P(*bottom), P(*fork), P(*left)], fill=fill, width=lw, joint="curve")
    ld.line([P(*fork), P(*right)], fill=fill, width=lw, joint="curve")

    # 节点：现在的你（大）+ 两条路的去向（中）+ 分岔点（小，圆滑转角）
    for (x, y), r in ((bottom, 62), (left, 50), (right, 50), (fork, 35)):
        cx, cy = P(x, y)
        rr = r * k
        ld.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=fill)

    if blur:
        layer = layer.filter(ImageFilter.GaussianBlur(blur))
    img.alpha_composite(layer)
    return img


def build_app_icon(size: int = 1024, supersample: int = 2) -> Image.Image:
    S = size * supersample
    img = gradient(S)
    # 光源偏左上，与渐变亮部一致
    img.alpha_composite(radial_glow(S, strength=44, cx_ratio=0.33, cy_ratio=0.27))

    k = S / 1024
    shift = (0, -12 * k)  # 符号略微上移，抵消底部大节点的视觉下坠

    # 路的投影（先在深色层画，模糊后叠上，形成轻微浮雕感）
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw_path(shadow, k, offset=(shift[0], shift[1] + 14 * k), color=SHADOW, alpha=82,
              blur=int(16 * supersample))
    img.alpha_composite(shadow)

    # 白色路
    draw_path(img, k, offset=shift)

    # 内侧高光边，增强立体边缘
    edge = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ed = ImageDraw.Draw(edge)
    ed.rounded_rectangle(
        [2 * supersample, 2 * supersample, S - 2 * supersample, S - 2 * supersample],
        radius=int(230 * supersample) - 2 * supersample,
        outline=(255, 255, 255, 46),
        width=max(1, 2 * supersample),
    )
    img.alpha_composite(edge)

    # 圆角裁切
    img.putalpha(rounded_mask(S, int(230 * supersample)))
    return img.resize((size, size), Image.LANCZOS)


def draw_tab_icon(kind: str, color, size: int = 81, supersample: int = 4) -> Image.Image:
    S = size * supersample
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    k = S / 81
    lw = max(2, int(5.2 * k))
    fill = color + (255,)

    if kind == "decision":  # Y 形分岔，与主图标同一符号语言
        b, f, l, r = (40.5, 67), (40.5, 43), (23, 19.5), (58, 19.5)
        pts = [tuple(c * k for c in b), tuple(c * k for c in f), tuple(c * k for c in l)]
        d.line(pts, fill=fill, width=lw, joint="curve")
        d.line([tuple(c * k for c in f), tuple(c * k for c in r)], fill=fill, width=lw, joint="curve")
        for (x, y), rr in ((b, 4.6), (l, 3.8), (r, 3.8)):
            cx, cy, r2 = x * k, y * k, rr * k
            d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=fill)

    elif kind == "cases":  # 档案：圆角矩形 + 两条内容线
        d.rounded_rectangle([18 * k, 15 * k, 63 * k, 66 * k], radius=7 * k,
                            outline=fill, width=lw)
        for y in (33, 45):
            d.line([26 * k, y * k, 55 * k, y * k], fill=fill, width=lw)

    elif kind == "me":  # 人形：头 + 肩
        cx, cy, r = 40.5 * k, 29 * k, 10.5 * k
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=fill, width=lw)
        d.arc([20 * k, 47 * k, 61 * k, 88 * k], start=180, end=360, fill=fill, width=lw)

    return img.resize((size, size), Image.LANCZOS)


def main():
    # 应用图标
    for size in (1024, 512, 144):
        icon = build_app_icon(size)
        p = OUT / f"app-icon-{size}.png"
        icon.save(p, "PNG")
        print(f"生成 {p.relative_to(OUT.parent.parent.parent)}  {size}x{size}")

    # tabBar 图标（未选中 / 选中）
    for kind in ("decision", "cases", "me"):
        for suffix, color in (("", (149, 128, 112)), ("-on", (185, 86, 44))):
            tab = draw_tab_icon(kind, color)
            p = OUT / f"tab-{kind}{suffix}.png"
            tab.save(p, "PNG")
            print(f"生成 {p.relative_to(OUT.parent.parent.parent)}  81x81")


if __name__ == "__main__":
    main()
