"""
stylize_forest_sprites.py

Turns the raw lit frames from tools/blender/bake_forest_models.py into the
forest's pixel-art billboards, the same treatment creature_stylize.frag gives
monsters, done offline:

  1. Box-downsample to the art canvas (RAW_SCALE -> 1), median-filter the
     colour to drop render noise, and hard-threshold alpha.
  2. Sort each pixel into green (needles, leaves, moss) or not (bark, stone)
     by hue.
  3. Quantize luminance into four tones of that material's palette. Which
     palettes a sprite uses depends on what it is (see GROUPS). Thresholds are
     percentiles over every frame of the group together, so a dark tree stays
     darker than a pale one rather than each being stretched alone.
  4. Draw a 1px darkest-tone outline on the inside of the silhouette.
  5. Add a faint ember rim under each top edge: the volcanic sky catching the
     side that faces it.

It also quantizes the forest's tiling floor and cliff textures from their
Synty sources into the same palettes (no outline: they tile).

Run after the Blender bake:
    python tools/stylize_forest_sprites.py [game_assets_dir]
Reads build/forest_raw/*.png, writes assets/images/forest/<name>.png, and
writes assets/images/floor_forest.png and forest_cliff.png.
"""

import glob
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RAW_DIR = os.path.join(REPO, "build", "forest_raw")
OUT_DIR = os.path.join(REPO, "assets", "images", "forest")
IMG_DIR = os.path.join(REPO, "assets", "images")

RAW_SCALE = 2  # must match bake_forest_models.py

OUTLINE = (8, 15, 12)
NEEDLES = [(18, 34, 24), (28, 52, 34), (41, 72, 44), (58, 94, 54)]
BARK = [(24, 20, 18), (42, 33, 27), (61, 47, 36), (84, 66, 48)]
STONE = [(30, 32, 31), (48, 51, 49), (68, 71, 67), (92, 95, 88)]
MOSS = [(20, 28, 17), (31, 42, 24), (44, 57, 31), (58, 72, 38)]
UMBER = [(22, 17, 13), (36, 27, 19), (52, 39, 26), (70, 53, 34)]
CAP = [(40, 16, 14), (72, 28, 22), (104, 46, 34), (150, 110, 84)]
PETAL = [(60, 58, 52), (96, 92, 80), (140, 132, 112), (176, 168, 140)]
TEAL = [(14, 38, 40), (24, 70, 70), (50, 120, 112), (120, 200, 180)]
EMBER_RIM = (112, 44, 26)

# Sprite name prefix -> (palette for green pixels, palette for the rest). The first
# matching prefix wins, so specific names sit above the general ones.
GROUPS = {
    "tree_": (NEEDLES, BARK),
    "bush_": (NEEDLES, BARK),
    "rock_": (MOSS, STONE),
    "stump_": (MOSS, BARK),
    "log_": (MOSS, BARK),
    "landmark_campfire": (MOSS, BARK),
    "landmark_camp_tent": (MOSS, BARK),
    "landmark_bramble": (NEEDLES, BARK),
    "landmark_": (MOSS, STONE),
    "scatter_mushroom": (NEEDLES, CAP),
    "scatter_flowers": (NEEDLES, PETAL),
    "scatter_glowcap": (TEAL, TEAL),
    "scatter_moss": (MOSS, STONE),
    "scatter_branch": (MOSS, BARK),
    "scatter_": (NEEDLES, BARK),
}

# Where a sprite is written, by name prefix: landmark_<id> is the forest's own
# variant of the shared prop <id>, so it lives apart from the other themes' art.
OUTPUT_BY_PREFIX = {
    "landmark_": os.path.join(OUT_DIR, "props"),
}

# Where each material's tone boundaries fall, as luminance percentiles.
TONE_PERCENTILES = (22, 52, 82)

# Tiling ground textures: (output, Synty source under the Alpine pack, size, green palette,
# other palette, green ratio). Downsampling averages grass blades into the needles around
# them, so the floor needs a looser green test than the sprites to keep any moss at all.
GROUND_MEDIAN = 5
ALPINE_TEXTURES = os.path.join("POLYGON_NatureBiomes_AlpineMountain_SourceFiles_v3", "Textures")
GROUND = [
    ("floor_forest.png", "Synty_Alpine_Ground_GrassPine_01_basecolor.png", 128, MOSS, UMBER, 0.9),
    ("forest_cliff.png", "Synty_Alpine_Ground_MossyRockPine_01_basecolor.png", 128, MOSS, STONE, 1.0),
]


def load(path):
    raw = Image.open(path).convert("RGBA")
    small = raw.reduce(RAW_SCALE)
    # Eevee's soft shadows leave single-pixel noise that quantizes into speckle.
    rgb = np.asarray(small.convert("RGB").filter(ImageFilter.MedianFilter(3))).astype(np.float32)
    mask = np.asarray(small)[..., 3] > 127
    return rgb, mask


def luminance(rgb):
    return rgb[..., 0] * 0.299 + rgb[..., 1] * 0.587 + rgb[..., 2] * 0.114


def is_green(rgb, ratio=1.08):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    return (g > r * ratio) & (g > b)


def thresholds(frames, green, ratio=1.08):
    values = [luminance(rgb)[mask & (is_green(rgb, ratio) == green)] for rgb, mask in frames]
    values = [v for v in values if v.size]
    return np.percentile(np.concatenate(values), TONE_PERCENTILES) if values else np.array([64.0, 128.0, 192.0])


def quantize(rgb, mask, green_cuts, other_cuts, palettes, ratio=1.08):
    out = np.zeros(rgb.shape[:2] + (4,), dtype=np.uint8)
    lum = luminance(rgb)
    green = is_green(rgb, ratio)
    for material, cuts, tones in ((True, green_cuts, palettes[0]), (False, other_cuts, palettes[1])):
        sel = mask & (green == material)
        out[sel, :3] = np.array(tones, dtype=np.uint8)[np.digitize(lum, cuts)[sel]]
    out[..., 3] = np.where(mask, 255, 0)
    return out


def stylize(rgb, mask, green_cuts, other_cuts, palettes=(NEEDLES, BARK)):
    out = quantize(rgb, mask, green_cuts, other_cuts, palettes)

    # Inside edge: an opaque pixel with any transparent 4-neighbour.
    padded = np.pad(mask, 1, constant_values=False)
    edge = mask & ~(padded[:-2, 1:-1] & padded[2:, 1:-1] & padded[1:-1, :-2] & padded[1:-1, 2:])
    # Rim: the pixel just under a top edge, i.e. one whose upper neighbour is an edge
    # pixel that has open sky directly above it.
    top_edge = edge & ~padded[:-2, 1:-1]
    rim = np.zeros_like(mask)
    rim[1:, :] = top_edge[:-1, :]
    rim &= mask & ~edge

    out[rim, :3] = EMBER_RIM
    out[edge, :3] = OUTLINE
    return Image.fromarray(out, "RGBA")


def group_of(name):
    for prefix, palettes in GROUPS.items():
        if name.startswith(prefix):
            return prefix, palettes
    raise SystemExit("No palette group for " + name)


def stylize_sprites():
    paths = sorted(glob.glob(os.path.join(RAW_DIR, "*.png")))
    if not paths:
        raise SystemExit("No raw frames in " + RAW_DIR + "; run tools/blender/bake_forest_models.py first.")
    by_group = {}
    for path in paths:
        prefix, palettes = group_of(os.path.basename(path))
        by_group.setdefault(prefix, (palettes, []))[1].append((path, load(path)))

    for prefix, (palettes, items) in by_group.items():
        frames = [frame for _, frame in items]
        green_cuts = thresholds(frames, True)
        other_cuts = thresholds(frames, False)
        for path, (rgb, mask) in items:
            out = output_path(os.path.basename(path))
            os.makedirs(os.path.dirname(out), exist_ok=True)
            stylize(rgb, mask, green_cuts, other_cuts, palettes).save(out, optimize=True)
            print("wrote", out)


def output_path(name):
    for prefix, directory in OUTPUT_BY_PREFIX.items():
        if name.startswith(prefix):
            return os.path.join(directory, name[len(prefix):])
    return os.path.join(OUT_DIR, name)


def stylize_ground(packs):
    for out_name, source, size, green_palette, other_palette, ratio in GROUND:
        src = os.path.join(packs, ALPINE_TEXTURES, source)
        if not os.path.exists(src):
            print("MISSING", src)
            continue
        # A box reduce keeps the texture tiling as long as the source is a multiple of size.
        img = Image.open(src).convert("RGB").resize((size, size), Image.BOX)
        # Per-pixel noise reads as static, not pixel art: merge it into clusters with a
        # median filter run over a 3x3 tiling, so the seams filter like the middle.
        tiled = Image.new("RGB", (size * 3, size * 3))
        for ox in range(3):
            for oy in range(3):
                tiled.paste(img, (ox * size, oy * size))
        img = tiled.filter(ImageFilter.MedianFilter(GROUND_MEDIAN)).crop((size, size, size * 2, size * 2))
        rgb = np.asarray(img).astype(np.float32)
        mask = np.ones(rgb.shape[:2], dtype=bool)
        frames = [(rgb, mask)]
        out = quantize(rgb, mask, thresholds(frames, True, ratio), thresholds(frames, False, ratio),
                       (green_palette, other_palette), ratio)
        dst = os.path.join(IMG_DIR, out_name)
        Image.fromarray(out[..., :3], "RGB").save(dst, optimize=True)
        print("wrote", dst)


def main():
    packs = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "docs", "game_assets")
    stylize_sprites()
    stylize_ground(packs)


if __name__ == "__main__":
    main()
