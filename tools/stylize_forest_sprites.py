"""
stylize_forest_sprites.py

Turns the raw lit tree frames from tools/blender/bake_forest_models.py into the
forest's pixel-art billboards, the same treatment creature_stylize.frag gives
monsters, done offline:

  1. Box-downsample to the art canvas (RAW_SCALE -> 1), median-filter the
     colour to drop render noise, and hard-threshold alpha.
  2. Sort each pixel into needles or bark by hue.
  3. Quantize luminance into that material's four tones of the fixed forest
     palette. Thresholds are percentiles over every frame together, so a dark
     tree stays darker than a pale one rather than each being stretched alone.
  4. Draw a 1px darkest-tone outline on the inside of the silhouette.
  5. Add a faint ember rim under each top edge: the volcanic sky catching the
     side of the tree that faces it.

Run after the Blender bake:
    python tools/stylize_forest_sprites.py
Reads build/forest_raw/*.png, writes assets/images/forest/<name>.png.
"""

import glob
import os

import numpy as np
from PIL import Image, ImageFilter

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RAW_DIR = os.path.join(REPO, "build", "forest_raw")
OUT_DIR = os.path.join(REPO, "assets", "images", "forest")

RAW_SCALE = 2  # must match bake_forest_models.py

OUTLINE = (8, 15, 12)
NEEDLES = [(18, 34, 24), (28, 52, 34), (41, 72, 44), (58, 94, 54)]
BARK = [(24, 20, 18), (42, 33, 27), (61, 47, 36), (84, 66, 48)]
EMBER_RIM = (112, 44, 26)

# Where each material's tone boundaries fall, as luminance percentiles across all frames.
TONE_PERCENTILES = (22, 52, 82)


def load(path):
    raw = Image.open(path).convert("RGBA")
    small = raw.reduce(RAW_SCALE)
    # Eevee's soft shadows leave single-pixel noise that quantizes into speckle.
    rgb = np.asarray(small.convert("RGB").filter(ImageFilter.MedianFilter(3))).astype(np.float32)
    mask = np.asarray(small)[..., 3] > 127
    return rgb, mask


def luminance(rgb):
    return rgb[..., 0] * 0.299 + rgb[..., 1] * 0.587 + rgb[..., 2] * 0.114


def is_needle(rgb):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    return (g > r * 1.08) & (g > b)


def thresholds(frames, material):
    values = [luminance(rgb)[mask & (is_needle(rgb) == material)] for rgb, mask in frames]
    values = np.concatenate([v for v in values if v.size]) if any(v.size for v in values) else np.array([0.0])
    return np.percentile(values, TONE_PERCENTILES)


def stylize(rgb, mask, needle_cuts, bark_cuts):
    h, w = mask.shape
    out = np.zeros((h, w, 4), dtype=np.uint8)
    lum = luminance(rgb)
    needle = is_needle(rgb)

    for material, cuts, tones in ((True, needle_cuts, NEEDLES), (False, bark_cuts, BARK)):
        sel = mask & (needle == material)
        tone = np.digitize(lum, cuts)
        out[sel, :3] = np.array(tones, dtype=np.uint8)[tone[sel]]

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
    out[..., 3] = np.where(mask, 255, 0)
    return Image.fromarray(out, "RGBA")


def main():
    paths = sorted(glob.glob(os.path.join(RAW_DIR, "*.png")))
    if not paths:
        raise SystemExit("No raw frames in " + RAW_DIR + "; run tools/blender/bake_forest_models.py first.")
    frames = [load(p) for p in paths]
    needle_cuts = thresholds(frames, True)
    bark_cuts = thresholds(frames, False)
    os.makedirs(OUT_DIR, exist_ok=True)
    for path, (rgb, mask) in zip(paths, frames):
        out = os.path.join(OUT_DIR, os.path.basename(path))
        stylize(rgb, mask, needle_cuts, bark_cuts).save(out, optimize=True)
        print("wrote", out)


if __name__ == "__main__":
    main()
