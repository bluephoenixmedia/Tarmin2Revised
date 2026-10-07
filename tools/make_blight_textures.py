"""Derives the Blighted Marches textures from the licensed biome art already in assets/.

The Blight has no source pack of its own. Its ground and timber are the desert's
and the tundra's, burnt: luminance is kept (so the shading and silhouettes stay
the artist's), colour is remapped onto an ash ramp with a crimson undertone, and
the trees are charred toward black with an ember rim.

Run from the repo root:  python tools/make_blight_textures.py
"""
import os
import random

from PIL import Image, ImageFilter

ASSETS = os.path.join("assets", "images")
OUT_DIR = os.path.join(ASSETS, "blight")

# Ash ramp: (luminance stop, rgb). Dark crimson-brown soot up to pale bone ash.
ASH_RAMP = [
    (0.00, (14, 8, 8)),
    (0.30, (52, 30, 26)),
    (0.55, (92, 66, 58)),
    (0.80, (138, 118, 108)),
    (1.00, (186, 170, 160)),
]

CHAR_RAMP = [
    (0.00, (6, 4, 4)),
    (0.45, (34, 22, 20)),
    (0.80, (72, 44, 36)),
    (1.00, (128, 70, 48)),
]


def ramp(lum, stops):
    for (l0, c0), (l1, c1) in zip(stops, stops[1:]):
        if lum <= l1:
            t = 0.0 if l1 == l0 else (lum - l0) / (l1 - l0)
            return tuple(int(a + (b - a) * t) for a, b in zip(c0, c1))
    return stops[-1][1]


def remap(img, stops, contrast=1.0, rng=None, speckle=0.0, lum_scale=1.0):
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            lum = max(0.0, min(1.0, (0.5 + (lum - 0.5) * contrast) * lum_scale))
            nr, ng, nb = ramp(lum, stops)
            if rng is not None and speckle > 0 and rng.random() < speckle:
                # Embers and cinders in the ash.
                if rng.random() < 0.25:
                    nr, ng, nb = 170, 60, 24
                else:
                    nr, ng, nb = (int(c * 0.55) for c in (nr, ng, nb))
            px[x, y] = (nr, ng, nb, a)
    return img


def save(img, *path):
    out = os.path.join(*path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("wrote", out)


def main():
    rng = random.Random(0xB116)

    # Tundra permafrost, not desert sand: dune ripples read as sand even in ash.
    floor = Image.open(os.path.join(ASSETS, "floor_tundra.png"))
    save(remap(floor, ASH_RAMP, contrast=1.4, rng=rng, speckle=0.02, lum_scale=0.62), ASSETS, "floor_blight.png")

    cliff = Image.open(os.path.join(ASSETS, "tundra_cliff.png"))
    save(remap(cliff, ASH_RAMP, contrast=1.25), ASSETS, "blight_cliff.png")

    trees = [
        (os.path.join(ASSETS, "desert", "tree_dead_01.png"), "tree_charred_01.png"),
        (os.path.join(ASSETS, "desert", "tree_dead_02.png"), "tree_charred_02.png"),
        (os.path.join(ASSETS, "forest", "tree_dead_a_l.png"), "tree_charred_03.png"),
        (os.path.join(ASSETS, "tundra", "tree_snag_01.png"), "tree_snag_01.png"),
    ]
    for src, name in trees:
        save(remap(Image.open(src), CHAR_RAMP, contrast=1.1), OUT_DIR, name)

    mound = Image.open(os.path.join(ASSETS, "tundra", "snow_mound_01.png"))
    save(remap(mound, ASH_RAMP, contrast=0.9, rng=rng, speckle=0.03), OUT_DIR, "ash_mound_01.png")

    stump = Image.open(os.path.join(ASSETS, "tundra", "pine_stump_01.png"))
    save(remap(stump, CHAR_RAMP), OUT_DIR, "stump_charred_01.png")

    bones = Image.open(os.path.join(ASSETS, "desert", "bones_rib.png"))
    save(remap(bones, ASH_RAMP, contrast=1.1), OUT_DIR, "bones_ash_01.png")

    # The castle billboard comes from tools/blender/render_castle_billboard.py. Crop it
    # to its pixels so the quad's base is the crag's base: billboards stand on their
    # bottom edge, and empty margin below would float the castle off the ground.
    billboard = os.path.join(OUT_DIR, "castle_tarmin_billboard.png")
    if os.path.exists(billboard):
        img = Image.open(billboard).convert("RGBA")
        box = img.getbbox()
        if box and box != (0, 0) + img.size:
            save(img.crop(box), billboard)


if __name__ == "__main__":
    main()
