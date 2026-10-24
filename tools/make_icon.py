#!/usr/bin/env python3
"""生成「内存守护」启动图标（无第三方依赖，纯 Python 写 PNG）

设计：深色圆角方块 + 青绿渐变盾牌 + 白色四角星（守护 + 清理）
"""
import math
import os
import struct
import zlib

SS = 4  # 超采样倍数

BG_TOP = (27, 42, 51)
BG_BOTTOM = (12, 18, 24)
SHIELD_TOP = (53, 224, 161)
SHIELD_BOTTOM = (20, 184, 126)
SPARK = (255, 255, 255)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rounded_rect(x, y, w, h, r):
    """返回判断点是否在圆角矩形内的函数（归一化坐标）"""
    def inside(px, py):
        cx = min(max(px, r), w - r)
        cy = min(max(py, r), h - r)
        dx = px - cx
        dy = py - cy
        return dx * dx + dy * dy <= r * r
    return inside


def point_in_poly(px, py, pts):
    inside = False
    n = len(pts)
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / (yj - yi) + xi):
            inside = not inside
        j = i
    return inside


def shield_points(cx, cy, w, h):
    """盾牌多边形，坐标以 (cx,cy) 为中心"""
    l, r = cx - w / 2, cx + w / 2
    t, b = cy - h / 2, cy + h / 2
    return [
        (l, t),
        (r, t),
        (r, t + h * 0.52),
        (cx, b),
        (l, t + h * 0.52),
    ]


def star_points(cx, cy, outer, inner):
    pts = []
    for i in range(8):
        ang = math.pi / 2 * (i / 2.0) + math.pi / 4 * (i % 2)
        rr = outer if i % 2 == 0 else inner
        # 4 长 4 短的四角星
        ang = (math.pi / 2) * (i // 2) + (0 if i % 2 == 0 else math.pi / 4)
        rr = outer if i % 2 == 0 else inner
        pts.append((cx + rr * math.cos(ang), cy - rr * math.sin(ang)))
    return pts


def render(size):
    n = size * SS
    buf = [[(0, 0, 0, 0)] * n for _ in range(n)]

    radius = n * 0.22
    in_bg = rounded_rect(0, 0, n, n, radius)

    shield = shield_points(n / 2, n / 2 + n * 0.02, n * 0.56, n * 0.66)
    star = star_points(n / 2, n / 2 + n * 0.01, n * 0.155, n * 0.052)

    for y in range(n):
        for x in range(n):
            px, py = x + 0.5, y + 0.5
            if not in_bg(px, py):
                continue
            bg = lerp(BG_TOP, BG_BOTTOM, py / n)
            if point_in_poly(px, py, shield):
                t = (py - shield[0][1]) / (shield[3][1] - shield[0][1])
                t = max(0.0, min(1.0, t))
                col = lerp(SHIELD_TOP, SHIELD_BOTTOM, t)
                if point_in_poly(px, py, star):
                    col = SPARK
            else:
                col = bg
            buf[y][x] = (col[0], col[1], col[2], 255)

    # 降采样
    out = []
    for y in range(size):
        row = []
        for x in range(size):
            r = g = b = a = 0
            for dy in range(SS):
                for dx in range(SS):
                    c = buf[y * SS + dy][x * SS + dx]
                    r += c[0] * c[3]
                    g += c[1] * c[3]
                    b += c[2] * c[3]
                    a += c[3]
            if a == 0:
                row.append((0, 0, 0, 0))
            else:
                row.append((r // a, g // a, b // a, a // (SS * SS)))
        out.append(row)
    return out


def write_png(path, pixels):
    h = len(pixels)
    w = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for px in row:
            raw.extend(px)
    comp = zlib.compress(bytes(raw), 9)

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', comp)
    png += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    res = os.path.join(here, '..', 'res')
    sizes = {
        'mdpi': 48, 'hdpi': 72, 'xhdpi': 96,
        'xxhdpi': 144, 'xxxhdpi': 192,
    }
    for dpi, size in sizes.items():
        d = os.path.join(res, 'mipmap-' + dpi)
        os.makedirs(d, exist_ok=True)
        write_png(os.path.join(d, 'ic_launcher.png'), render(size))
        print('生成 mipmap-%s/ic_launcher.png (%dpx)' % (dpi, size))


if __name__ == '__main__':
    main()
