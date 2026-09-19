"""Builds assets/images/paperdoll/base_head.png: the father's head and beard, alone.

Chest armour and cloaks are drawn over the body, and anything with a collar or a high
neckline covers the beard and chin. The head is redrawn on top of them from this cutout,
below any helmet. It is cut from base_father.png on the same 1024x1536 canvas, so it
lands on the body pixel-for-pixel with no calibration.

Above the shoulders the whole silhouette is head. Below them the beard overlaps the
tunic, so it is separated by colour inside a generous beard outline: brown cloth is
dropped, grey beard and the black outline are kept, and enclosed holes (the mouth) are
filled back in.

Usage: python tools/make_head_overlay.py [preview.png]
"""
import colorsys
import sys

import numpy as np
from PIL import Image, ImageDraw

BODY = "assets/images/paperdoll/base_father.png"
OUT = "assets/images/paperdoll/base_head.png"

# Where the shoulders begin; above this every opaque pixel is head.
SHOULDER_Y = 296
# Generous outline of the beard below the shoulder line, master-canvas pixels.
BEARD_OUTLINE = [
    (398, 280), (400, 330), (415, 370), (435, 398), (458, 420), (480, 434), (500, 440),
    (530, 440), (570, 434), (598, 420), (622, 398), (642, 362), (656, 318), (662, 280),
]


def is_cloth(r, g, b):
    """The tunic: a saturated mid brown. Beard is near-grey; the outline is near-black."""
    h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
    return 0.03 <= h <= 0.12 and s > 0.32 and 0.18 < v < 0.75


def fill_holes(mask):
    """Everything not reachable from the border without crossing the mask is a hole."""
    h, w = mask.shape
    outside = np.zeros_like(mask)
    stack = [(0, x) for x in range(w)] + [(h - 1, x) for x in range(w)] \
        + [(y, 0) for y in range(h)] + [(y, w - 1) for y in range(h)]
    while stack:
        y, x = stack.pop()
        if outside[y, x] or mask[y, x]:
            continue
        outside[y, x] = True
        if y > 0: stack.append((y - 1, x))
        if y < h - 1: stack.append((y + 1, x))
        if x > 0: stack.append((y, x - 1))
        if x < w - 1: stack.append((y, x + 1))
    return ~outside


FACE_SEED = (150, 526)  # (y, x): the forehead


def connected_to(mask, seed):
    reached = np.zeros_like(mask)
    h, w = mask.shape
    stack = [seed]
    while stack:
        y, x = stack.pop()
        if y < 0 or y >= h or x < 0 or x >= w or reached[y, x] or not mask[y, x]:
            continue
        reached[y, x] = True
        stack.extend(((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)))
    return reached


def main(preview=None):
    body = Image.open(BODY).convert("RGBA")
    px = np.asarray(body)
    alpha = px[..., 3] > 0

    outline = Image.new("L", body.size, 0)
    ImageDraw.Draw(outline).polygon(BEARD_OUTLINE, fill=255)
    in_beard = np.asarray(outline) > 0

    keep = np.zeros(alpha.shape, dtype=bool)
    keep[:SHOULDER_Y] = alpha[:SHOULDER_Y]
    ys, xs = np.nonzero(in_beard & alpha)
    for y, x in zip(ys, xs):
        if y >= SHOULDER_Y and not is_cloth(*px[y, x, :3]):
            keep[y, x] = True

    # Hole-fill within the head's bounding box only, so it stays cheap.
    ys, xs = np.nonzero(keep)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    keep[y0:y1, x0:x1] = fill_holes(keep[y0:y1, x0:x1]) & alpha[y0:y1, x0:x1]

    # Only the piece joined to the face survives: specks and the tops of the shoulders
    # that poke above SHOULDER_Y are separate islands.
    keep &= connected_to(keep, FACE_SEED)

    out = px.copy()
    out[..., 3] = np.where(keep, px[..., 3], 0)
    Image.fromarray(out, "RGBA").save(OUT, optimize=True)
    ys, xs = np.nonzero(keep)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    print(f"wrote {OUT}: {int(keep.sum())} head pixels, bbox x{x0}-{x1} y{y0}-{y1}")

    if preview:
        bg = Image.new("RGBA", body.size, (40, 140, 40, 255))
        bg.alpha_composite(Image.fromarray(out, "RGBA"))
        bg.crop((x0 - 20, y0 - 20, x1 + 20, y1 + 20)).convert("RGB").resize(
            ((x1 - x0 + 40) * 2, (y1 - y0 + 40) * 2), Image.NEAREST).save(preview)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
