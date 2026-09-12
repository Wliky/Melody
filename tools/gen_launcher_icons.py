"""生成传统 mipmap PNG 启动图标（Android 7.x 及以下 / 部分启动器需要）。
纯 zlib+struct 手写 PNG，不依赖 Pillow。
"""
import os
import struct
import zlib

ROOT = r"D:\Study\Workbuddy\Github\Melody\app\src\main\res"
BG = (0xC2, 0x0C, 0x0C)
BG_DARK = (0x8E, 0x07, 0x07)
FG = (255, 255, 255)


def in_rounded_rect(x, y, size):
    r = 24.0 / 108.0 * size
    if x < 0 or y < 0 or x > size or y > size:
        return False
    cx = min(max(x, r), size - r)
    cy = min(max(y, r), size - r)
    dx = x - cx
    dy = y - cy
    return dx * dx + dy * dy <= r * r + 1e-6


def in_circle(x, y, size):
    c = size / 2.0
    dx = x - c
    dy = y - c
    return dx * dx + dy * dy <= c * c


def dist_seg(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    denom = vx * vx + vy * vy
    t = 0.0 if denom == 0 else max(0.0, min(1.0, (wx * vx + wy * vy) / denom))
    dx = px - (ax + t * vx)
    dy = py - (ay + t * vy)
    return (dx * dx + dy * dy) ** 0.5


def note_color(x, y, s):
    """s = size / 108 缩放系数"""
    def c1(cx, cy, r):
        dx = x - cx * s
        dy = y - cy * s
        return dx * dx + dy * dy <= (r * s) ** 2

    if c1(41, 66, 9) or c1(65, 61, 9):
        return True
    for x0, y0, x1, y1 in ((48, 40, 52, 66), (72, 35, 76, 61)):
        if x0 * s <= x <= x1 * s and y0 * s <= y <= y1 * s:
            return True
    if dist_seg(x, y, 50 * s, 43 * s, 74 * s, 38 * s) <= 3.0 * s:
        return True
    return False


def render(size, round_icon):
    ss = 3
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            r_sum = g_sum = b_sum = a_sum = 0
            for sy in range(ss):
                for sx in range(ss):
                    x = px + (sx + 0.5) / ss
                    y = py + (sy + 0.5) / ss
                    inside = in_circle(x, y, size) if round_icon else in_rounded_rect(x, y, size)
                    if not inside:
                        continue
                    if note_color(x, y, size / 108.0):
                        cr, cg, cb = FG
                    else:
                        # 背景：右下角用深色做出层次
                        cr, cg, cb = BG_DARK if (x + y) > size * 1.18 else BG
                    r_sum += cr
                    g_sum += cg
                    b_sum += cb
                    a_sum += 255
            n = ss * ss
            if a_sum == 0:
                row += bytes((0, 0, 0, 0))
            else:
                k = a_sum / 255.0
                row += bytes((
                    round(r_sum / k),
                    round(g_sum / k),
                    round(b_sum / k),
                    round(a_sum / n),
                ))
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

for name, size in DENSITIES.items():
    folder = os.path.join(ROOT, "mipmap-" + name)
    os.makedirs(folder, exist_ok=True)
    write_png(os.path.join(folder, "ic_launcher.png"), size, render(size, False))
    write_png(os.path.join(folder, "ic_launcher_round.png"), size, render(size, True))
    print("generated", name, size)
print("done")
