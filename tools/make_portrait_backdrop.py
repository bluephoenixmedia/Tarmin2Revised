"""Builds assets/images/paperdoll/portrait_backdrop.png: the portrait with the father removed.

The inventory page art (images/new_inventory.png) has a father painted into its portrait
frame. It is a separate painting from base_father.png -- slightly different pose and
scale -- so once base_father became a transparent cutout, the painted copy showed
through wherever the two disagree, and around any clothing: doubled boots, trousers and
hands. No calibration can fix that; the painted copy has to be covered.

The backdrop is the pre-cutout portrait (frame, parchment, vignette), which covered the
page's painting exactly as the game drew it before the cutout, with the figure removed
so a back cloak can show between it and the body. base_father.png was cut from that
same image with 0.0/255 difference (tools/check_phase0.py), so its alpha is an exact
mask of the figure.

The hole is filled by diffusion from the surrounding backdrop. Its quality barely
matters: base_father is always drawn over it, so the fill only ever shows under a
back cloak's gaps or through the body's soft edge.

Usage:
    git show 82c166c1:assets/images/paperdoll/base_father.png > /tmp/portrait_full.png
    python tools/make_portrait_backdrop.py /tmp/portrait_full.png
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

BODY = "assets/images/paperdoll/base_father.png"
OUT = "assets/images/paperdoll/portrait_backdrop.png"


def diffuse(img, hole, iters):
    img = img.copy()
    for _ in range(iters):
        p = np.pad(img, ((1, 1), (1, 1), (0, 0)), mode="edge")
        avg = (p[:-2, 1:-1] + p[2:, 1:-1] + p[1:-1, :-2] + p[1:-1, 2:]) / 4.0
        img[hole] = avg[hole]
    return img


def pyramid_fill(img, hole):
    """Coarse-to-fine, so a large hole takes the low-frequency vignette quickly."""
    h, w = hole.shape
    if min(h, w) < 32:
        start = img.copy()
        start[hole] = img[~hole].mean(axis=0)
        return diffuse(start, hole, 400)
    small = np.asarray(Image.fromarray(img.clip(0, 255).astype(np.uint8)).resize(
        (w // 2, h // 2), Image.BILINEAR)).astype(np.float64)
    small_hole = np.asarray(Image.fromarray(hole.astype(np.uint8) * 255).resize(
        (w // 2, h // 2), Image.NEAREST)) > 0
    coarse = pyramid_fill(small, small_hole)
    up = np.asarray(Image.fromarray(coarse.clip(0, 255).astype(np.uint8)).resize(
        (w, h), Image.BILINEAR)).astype(np.float64)
    start = img.copy()
    start[hole] = up[hole]
    return diffuse(start, hole, 60)


def main(full_portrait_path):
    full = Image.open(full_portrait_path).convert("RGB")
    body = Image.open(BODY)
    if full.size != body.size:
        sys.exit(f"size mismatch: {full.size} vs {body.size}")

    rgb = np.asarray(full).astype(np.float64)
    # Grown a few pixels so the fill samples clean backdrop, not the figure's dark
    # outline and anti-aliasing, which would otherwise diffuse into a shadowy blob.
    grown = body.getchannel("A").point(lambda v: 255 if v > 0 else 0).filter(ImageFilter.MaxFilter(7))
    hole = np.asarray(grown) > 0
    filled = pyramid_fill(rgb, hole)
    out = np.where(hole[..., None], filled, rgb)
    Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGB").save(OUT, optimize=True)
    print(f"wrote {OUT}: {int(hole.sum())} figure pixels filled")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
