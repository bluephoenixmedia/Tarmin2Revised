"""Derive a seamless, square canopy tile from the authored forest canopy art.

CanopyMeshBuilder repeats its texture every two world units, so the canopy has
to tile. The authored images/forest/canopy.png is an illustrated leaf mass on a
transparent 16:9 canvas: repeated as-is it stretches to a square and leaves a
grid of see-through borders. This keeps the art and takes the dense middle of
it, squares it, and blends it against a half-offset copy of itself so opposite
edges match.

    python tools/make_canopy_tile.py

Writes assets/images/forest/canopy_tile.png, which World3DRenderer prefers over
canopy.png when it exists. Rerun whenever canopy.png is repainted.
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "assets", "images", "forest", "canopy.png")
DST = os.path.join(ROOT, "assets", "images", "forest", "canopy_tile.png")
SIZE = 512
# Pixels crossfaded across each wrap seam.
OVERLAP = 128
# Fraction of the shorter side kept around the centre: the leaf mass is dense there.
CROP = 0.62
# Shadow between the fronds, where the source canvas is see-through. Its
# transparent pixels carry a blue key colour that must never reach the screen.
SHADOW = np.array([8.0, 16.0, 14.0], dtype=np.float32)
# The shader multiplies frond alpha by tile coverage before a 0.5 cutoff; the
# old tile never went below ~0.52, so a closed canopy stayed closed.
MIN_ALPHA = 133.0


def wrap_crossfade(img, axis):
    """Crossfade the strip past the end into the start, so the tile wraps on this axis."""
    img = np.moveaxis(img, axis, 0)
    t = np.linspace(0.0, 1.0, OVERLAP, dtype=np.float32).reshape(-1, *([1] * (img.ndim - 1)))
    out = img[:SIZE].copy()
    out[:OVERLAP] = img[SIZE:SIZE + OVERLAP] * (1.0 - t) + img[:OVERLAP] * t
    return np.moveaxis(out, 0, axis)


def main():
    src = Image.open(SRC).convert("RGBA")
    w, h = src.size
    side = int(min(w, h) * CROP)
    cx, cy = w // 2, h // 2
    tile = src.crop((cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2))
    tile = tile.resize((SIZE + OVERLAP, SIZE + OVERLAP), Image.LANCZOS)
    a = np.asarray(tile).astype(np.float32)

    # Premultiply so the crossfade never blends in a transparent pixel's colour.
    alpha = a[..., 3:4] / 255.0
    a[..., :3] *= alpha
    a = wrap_crossfade(wrap_crossfade(a, 1), 0)

    # Fill the gaps between fronds with shadow, then floor alpha.
    alpha = a[..., 3:4] / 255.0
    a[..., :3] = a[..., :3] + SHADOW * (1.0 - alpha)
    a[..., 3] = np.maximum(a[..., 3], MIN_ALPHA)

    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(DST)
    print(f"wrote {DST} ({SIZE}x{SIZE}) from {w}x{h} source")


if __name__ == "__main__":
    main()
