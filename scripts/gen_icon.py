#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 Melody 原创 App 图标 PNG（纯标准库，无 PIL 依赖）。

设计：深海蓝→靛紫对角渐变圆角方形 + 白色抽象音符（符头/符杆/符尾）。
覆盖 mipmap 各密度：mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192。
"""
import struct, zlib, math, os

def lerp(a, b, t):
    return a + (b - a) * t

def make_icon(size):
    # 渐变端点
    c0 = (0x1E, 0x2A, 0x5A)  # 深海蓝
    c1 = (0x3B, 0x2E, 0x7A)  # 靛紫
    c2 = (0x6B, 0x2A, 0x8F)  # 紫红
    corner = size * 0.22  # 圆角半径

    px = bytearray()
    for y in range(size):
        row = bytearray([0])  # filter type 0
        for x in range(size):
            # 对角渐变 t
            t = (x + y) / (2.0 * size)
            if t < 0.55:
                tt = t / 0.55
                r = int(lerp(c0[0], c1[0], tt))
                g = int(lerp(c0[1], c1[1], tt))
                b = int(lerp(c0[2], c1[2], tt))
            else:
                tt = (t - 0.55) / 0.45
                r = int(lerp(c1[0], c2[0], tt))
                g = int(lerp(c1[1], c2[1], tt))
                b = int(lerp(c1[2], c2[2], tt))

            # 圆角遮罩（抗锯齿 alpha）
            alpha = rounded_alpha(x, y, size, corner)
            row += bytes((r, g, b, alpha))
        px += row

    # 叠加白色音符图形
    px = draw_note(px, size)
    return px

def rounded_alpha(x, y, size, corner):
    cx = min(max(x, corner), size - 1 - corner)
    cy = min(max(y, corner), size - 1 - corner)
    dx = x - cx
    dy = y - cy
    # 圆角矩形：在角落用圆弧
    # 简化：用圆形 alpha 在四个角
    if x < corner and y < corner:
        dist = math.hypot(x - corner, y - corner)
    elif x >= size - corner and y < corner:
        dist = math.hypot(x - (size - 1 - corner), y - corner)
    elif x < corner and y >= size - corner:
        dist = math.hypot(x - corner, y - (size - 1 - corner))
    elif x >= size - corner and y >= size - corner:
        dist = math.hypot(x - (size - 1 - corner), y - (size - 1 - corner))
    else:
        return 255
    if dist <= corner:
        return 255
    elif dist <= corner + 1:
        return int(255 * max(0.0, corner + 1 - dist))
    else:
        return 0

def draw_note(px, size):
    # 在归一化坐标 (0..1) 上定义音符，再映射到 size
    white = (255, 255, 255)
    semi = (179, 179, 255)

    def pt(nx, ny):
        return (int(nx * size), int(ny * size))

    # 符头椭圆中心 (0.50, 0.68)，半径 (0.13, 0.12)
    cx, cy = 0.50, 0.68
    rx, ry = 0.13, 0.12
    # 符杆：x 0.60~0.66, y 0.34~0.68
    # 符尾曲线（简化折线区域）
    # 右侧次符杆：x 0.72~0.77, y 0.40~0.62

    buf = bytearray(px)
    for y in range(size):
        for x in range(size):
            nx, ny = x / size, y / size
            idx = y * (size * 4 + 1) + 1 + x * 4

            hit = False
            semi_hit = False
            # 符头椭圆
            e = ((nx - cx) / rx) ** 2 + ((ny - cy) / ry) ** 2
            if e <= 1.0:
                hit = True
            # 符杆
            if 0.60 <= nx <= 0.66 and 0.34 <= ny <= 0.70:
                hit = True
            # 符尾（向右上扬的飘带，用一段折线近似）
            if 0.60 <= nx <= 0.70 and 0.34 <= ny <= 0.40:
                hit = True
            if 0.70 <= nx <= 0.80 and 0.40 <= ny <= 0.46:
                hit = True
            if 0.80 <= nx <= 0.84 and 0.46 <= ny <= 0.56:
                hit = True
            # 右侧次符杆（半透明）
            if 0.72 <= nx <= 0.77 and 0.40 <= ny <= 0.62:
                semi_hit = True

            if hit:
                buf[idx:idx+3] = bytes(white)
            elif semi_hit:
                buf[idx:idx+3] = bytes(semi)

    return bytes(buf)

def write_png(path, size, raw_rgba):
    def chunk(typ, data):
        c = struct.pack(">I", len(data)) + typ + data
        c += struct.pack(">I", zlib.crc32(typ + data) & 0xffffffff)
        return c

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    idat = zlib.compress(raw_rgba, 9)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", ihdr)
    png += chunk(b"IDAT", idat)
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)

def main():
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
    base = os.path.abspath(base)
    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for dpi, size in sizes.items():
        d = os.path.join(base, dpi)
        raw = make_icon(size)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            write_png(os.path.join(d, name), size, raw)
            print("written:", os.path.join(d, name))

if __name__ == "__main__":
    main()
