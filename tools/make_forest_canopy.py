"""
make_forest_canopy.py

Builds assets/images/forest/canopy.png: the underside of the surface forest's
canopy, seen from the trail looking up.

The frond shape is the Leaf_Pine_C card embedded in the pine models, so the
canopy matches the trees. Fronds are scattered in layers over a tileable
256x256 canvas (one texture spans two tiles), each with a 1px darkest-tone
outline, in a fixed five-tone forest palette. Upper layers are lighter because
they are backlit by the sky; the lowest layers are near-black.

Alpha is not a cut-out mask. It records how many fronds overlap a pixel, and
CanopyMeshBuilder multiplies it by each vertex's canopy coverage before the
shader's 0.5 cutoff. Dense clumps survive where coverage thins, sparse ones
drop out, and that is what gives the seam above a trail its ragged edge.

Run:
    python tools/make_forest_canopy.py [path/to/pine.glb]
"""

import io
import json
import os
import random
import struct
import sys

from PIL import Image, ImageFilter

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DEFAULT_GLB = os.path.join(REPO, "assets", "models", "trees", "pine2.glb")
OUT = os.path.join(REPO, "assets", "images", "forest", "canopy.png")

SIZE = 256
SEED = 7331

# Darkest first. Index 0 is the outline.
PALETTE = [
    (8, 15, 12),
    (16, 30, 22),
    (25, 45, 31),
    (36, 62, 41),
    (50, 82, 52),
]

# Layers from the top of the canopy down: (frond count, size range in px, palette index).
LAYERS = [
    (70, (34, 58), 4),
    (80, (30, 54), 3),
    (80, (28, 50), 2),
    (60, (26, 46), 1),
]

# Overlap count -> alpha. Bare pixels sit just above the 0.5 cutoff, so a fully
# covered tile (1.0) never opens; at the seam edge (~0.625) only alpha >= 0.8 survives.
ALPHA_BY_OVERLAP = [133, 170, 205, 235, 255]


def load_frond(glb_path):
    data = open(glb_path, "rb").read()
    json_len = struct.unpack("<I", data[12:16])[0]
    gltf = json.loads(data[20:20 + json_len])
    bin_start = 20 + json_len + 8
    for image in gltf["images"]:
        if image.get("name") == "Leaf_Pine_C":
            view = gltf["bufferViews"][image["bufferView"]]
            off = bin_start + view.get("byteOffset", 0)
            png = data[off:off + view["byteLength"]]
            alpha = Image.open(io.BytesIO(png)).convert("RGBA").getchannel("A")
            return alpha.crop(alpha.getbbox())
    raise SystemExit("Leaf_Pine_C not found in " + glb_path)


def stamp(canvas, mask, x, y):
    """Paste mask onto canvas at (x, y) and wrap it across the edges so the texture tiles."""
    for ox in (-SIZE, 0, SIZE):
        for oy in (-SIZE, 0, SIZE):
            canvas.paste(255, (x + ox, y + oy), mask)


def main():
    glb = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_GLB
    frond = load_frond(glb)
    rng = random.Random(SEED)

    colour = Image.new("RGB", (SIZE, SIZE), PALETTE[0])
    overlap = [[0] * SIZE for _ in range(SIZE)]

    for count, (lo, hi), tone in LAYERS:
        for _ in range(count):
            length = rng.randint(lo, hi)
            scale = length / frond.height
            mask = frond.resize((max(4, int(frond.width * scale)), length), Image.NEAREST)
            mask = mask.rotate(rng.uniform(0, 360), resample=Image.NEAREST, expand=True)
            mask = mask.point(lambda a: 255 if a > 127 else 0)
            outline = mask.filter(ImageFilter.MaxFilter(3))
            x = rng.randrange(SIZE) - mask.width // 2
            y = rng.randrange(SIZE) - mask.height // 2

            fill = Image.new("L", (SIZE, SIZE), 0)
            edge = Image.new("L", (SIZE, SIZE), 0)
            stamp(fill, mask, x, y)
            stamp(edge, outline, x, y)
            colour.paste(PALETTE[0], (0, 0), edge)
            colour.paste(PALETTE[tone], (0, 0), fill)

            px = fill.load()
            for yy in range(SIZE):
                row = overlap[yy]
                for xx in range(SIZE):
                    if px[xx, yy]:
                        row[xx] += 1

    alpha = Image.new("L", (SIZE, SIZE))
    ap = alpha.load()
    top = len(ALPHA_BY_OVERLAP) - 1
    for yy in range(SIZE):
        for xx in range(SIZE):
            ap[xx, yy] = ALPHA_BY_OVERLAP[min(overlap[yy][xx], top)]

    out = colour.convert("RGBA")
    out.putalpha(alpha)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)

    bare = sum(1 for row in overlap for v in row if v == 0) / float(SIZE * SIZE)
    print("wrote %s (%.1f%% bare)" % (OUT, bare * 100))


if __name__ == "__main__":
    main()
