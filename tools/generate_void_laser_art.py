"""Generates item art for the traveling merchant Void chain laser.

Outputs (RGBA, 1024x1024):
  assets/images/items/void_chain_laser_spent.png  - the weapon as it drops: dead cell, cracked lens
  assets/images/items/void_chain_laser.png        - restored: live violet cell and bloom
  assets/images/items/rift_filament.png           - coiled thread of coherent light from the Void

Deterministic (fixed seed); re-run to regenerate after tweaking.
"""
import math
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SIZE = 1024
OUT_DIR = "assets/images/items/"
VIOLET = (200, 92, 255)
RNG = random.Random(20260915)


def blank():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def bloom(layer, radius):
    return layer.filter(ImageFilter.GaussianBlur(radius))


def draw_emitter(charged):
    body = blank()
    d = ImageDraw.Draw(body)
    cx, cy = SIZE // 2 - 40, SIZE // 2

    # Three parallel barrels: the chain of the chain laser.
    barrel_ys = [cy - 86, cy - 40, cy + 6]
    for y in barrel_ys:
        d.rounded_rectangle((cx - 40, y - 16, cx + 400, y + 16), radius=14,
                            fill=(58, 62, 72, 255), outline=(18, 20, 24, 255), width=6)
        d.line((cx - 30, y - 7, cx + 388, y - 7), fill=(126, 132, 148, 255), width=4)
        d.ellipse((cx + 380, y - 21, cx + 422, y + 21), fill=(28, 30, 36, 255),
                  outline=(96, 100, 114, 255), width=5)

    # Shroud bands clamping the barrels together.
    for bx in (cx + 70, cx + 210, cx + 330):
        d.rounded_rectangle((bx, cy - 116, bx + 34, cy + 36), radius=8,
                            fill=(42, 44, 52, 255), outline=(14, 15, 18, 255), width=5)

    # Main housing.
    d.rounded_rectangle((cx - 330, cy - 124, cx + 60, cy + 54), radius=42,
                        fill=(46, 48, 58, 255), outline=(12, 13, 16, 255), width=8)
    d.line((cx - 300, cy - 104, cx + 32, cy - 104), fill=(134, 138, 154, 255), width=6)

    # Grip, wrapped.
    d.polygon([(cx - 220, cy + 44), (cx - 128, cy + 44), (cx - 168, cy + 266), (cx - 272, cy + 254)],
              fill=(40, 30, 34, 255), outline=(10, 8, 10, 255))
    for gy in range(cy + 76, cy + 246, 32):
        t = (gy - cy) / 266.0
        d.line((cx - 222 - 50 * t, gy, cx - 132 - 36 * t, gy - 8), fill=(74, 58, 62, 255), width=7)

    # Rivet line.
    for rx in range(cx - 300, cx + 40, 56):
        d.ellipse((rx - 7, cy + 26, rx + 7, cy + 40), fill=(152, 156, 170, 255))

    # Cell housing and lens.
    lx0, ly0, lx1, ly1 = cx - 252, cy - 92, cx - 112, cy + 20
    d.ellipse((lx0 - 16, ly0 - 16, lx1 + 16, ly1 + 16), fill=(22, 22, 28, 255),
              outline=(104, 108, 122, 255), width=8)
    lens_fill = (250, 236, 255, 255) if charged else (44, 24, 56, 255)
    d.ellipse((lx0, ly0, lx1, ly1), fill=lens_fill)

    if not charged:
        # A hairline crack across the dead lens, and grime on the housing.
        crack = [(lx0 + 18, ly0 + 30)]
        for _ in range(6):
            px, py = crack[-1]
            crack.append((px + RNG.randint(12, 24), py + RNG.randint(-10, 16)))
        d.line(crack, fill=(150, 140, 160, 255), width=3)
        for _ in range(140):
            gx = RNG.randint(cx - 320, cx + 410)
            gy = RNG.randint(cy - 120, cy + 50)
            if body.getpixel((gx, gy))[3] > 0:
                d.line((gx, gy, gx + RNG.randint(-14, 14), gy + RNG.randint(-3, 3)),
                       fill=(24, 22, 24, 170), width=2)
        return body.rotate(24, resample=Image.BICUBIC)

    # Live cell: violet bloom around the lens and energy traced along each barrel.
    glow = blank()
    g = ImageDraw.Draw(glow)
    g.ellipse((lx0 - 70, ly0 - 70, lx1 + 70, ly1 + 70), fill=VIOLET + (210,))
    for y in barrel_ys:
        g.line((cx - 20, y, cx + 400, y), fill=VIOLET + (150,), width=10)
        g.ellipse((cx + 392, y - 26, cx + 444, y + 26), fill=(240, 200, 255, 220))
    glow = bloom(glow, 42)
    core = blank()
    c = ImageDraw.Draw(core)
    c.ellipse((lx0 + 30, ly0 + 26, lx1 - 30, ly1 - 26), fill=(255, 255, 255, 255))
    core = bloom(core, 10)
    out = ImageChops.add(ImageChops.add(body, glow), core)
    return out.rotate(24, resample=Image.BICUBIC)


def draw_filament():
    """A single helical thread of light, coiled like a spring along a diagonal axis.

    The front half of each turn is drawn brighter and thicker than the back half,
    which is what sells it as a coil in depth rather than a flat squiggle.
    """
    x0, y0, x1, y1 = 230, 730, 810, 310
    ax, ay = x1 - x0, y1 - y0
    length = math.hypot(ax, ay)
    ux, uy = ax / length, ay / length
    px, py = -uy, ux
    turns = 9
    points = []
    front = []
    for i in range(1600):
        t = i / 1599.0
        angle = t * turns * 2 * math.pi
        taper = math.sin(t * math.pi) ** 0.6
        radius = 72 * taper
        along = t * length + math.sin(angle) * 16 * taper
        points.append((x0 + ux * along + px * math.cos(angle) * radius,
                       y0 + uy * along + py * math.cos(angle) * radius))
        front.append(math.sin(angle) > 0)

    halo = blank()
    ImageDraw.Draw(halo).line(points, fill=VIOLET + (170,), width=24, joint="curve")
    halo = bloom(halo, 18)

    mid = blank()
    core = blank()
    md = ImageDraw.Draw(mid)
    cd = ImageDraw.Draw(core)
    for i in range(len(points) - 1):
        seg = (points[i], points[i + 1])
        if front[i]:
            md.line(seg, fill=(226, 158, 255, 255), width=10)
            cd.line(seg, fill=(255, 250, 255, 255), width=4)
        else:
            md.line(seg, fill=(150, 80, 200, 150), width=6)
            cd.line(seg, fill=(230, 200, 255, 110), width=2)
    mid = bloom(mid, 4)

    for _ in range(40):
        sx, sy = points[RNG.randint(0, len(points) - 1)]
        sx += RNG.uniform(-50, 50)
        sy += RNG.uniform(-50, 50)
        r = RNG.uniform(2, 5)
        cd.ellipse((sx - r, sy - r, sx + r, sy + r), fill=(255, 240, 255, 255))

    return ImageChops.add(ImageChops.add(halo, mid), core)


def main():
    outputs = {
        "void_chain_laser_spent.png": draw_emitter(charged=False),
        "void_chain_laser.png": draw_emitter(charged=True),
        "rift_filament.png": draw_filament(),
    }
    for name, image in outputs.items():
        image.save(OUT_DIR + name)
        bbox = image.getbbox()
        print("wrote", OUT_DIR + name, image.size, "content bbox", bbox)


if __name__ == "__main__":
    main()
