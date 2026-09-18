"""
check_phase0.py

Verifies a hand-masked base_father.png before anything downstream depends on it.

Run:  python tools/check_phase0.py path/to/your_edited_base_father.png

The one thing that must not change is WHERE the character sits. Every one of the 335
calibrations, and the portrait rectangle itself, is measured against his current pixel
position -- so a mask that nudges, crops or rescales him invalidates all of it. This
checks that against the committed original and refuses anything that moved.
"""

import os
import subprocess
import sys
import io

from PIL import Image
import numpy as np

ORIGINAL_REF = "82c166c1:assets/images/paperdoll/base_father.png"
CANVAS = (1024, 1536)

# Mean per-channel difference allowed between the surviving pixels and the original
# image at the same coordinates. Erasing the background does not touch the pixels that
# remain, so a correct mask scores 0.0 and the bar is set just above it. It has to be
# this tight: the art has large flat regions, so even a six-pixel shift only moves the
# mean to ~11, which a generous tolerance would wave through.
RGB_TOL = 1.0


def load_original():
    blob = subprocess.check_output(["git", "show", ORIGINAL_REF], stderr=subprocess.DEVNULL)
    return Image.open(io.BytesIO(blob)).convert("RGB")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1

    path = sys.argv[1]
    if not os.path.exists(path):
        print("FAIL  no such file: %s" % path)
        return 1

    img = Image.open(path).convert("RGBA")
    problems = []
    notes = []

    if img.size != CANVAS:
        problems.append("canvas is %dx%d, must be %dx%d -- do not crop or resize"
                        % (img.size[0], img.size[1], CANVAS[0], CANVAS[1]))
        print("FAIL  " + problems[-1])
        return 1

    alpha = np.asarray(img.getchannel("A"), dtype=np.float32)
    opaque = float((alpha > 250).sum())
    clear = float((alpha < 5).sum())
    total = float(CANVAS[0] * CANVAS[1])

    if clear < total * 0.30:
        problems.append("only %.0f%% of the canvas is transparent; the background does "
                        "not look removed" % (100 * clear / total))
    if opaque < total * 0.05:
        problems.append("only %.0f%% of the canvas is opaque; the figure looks to have "
                        "been erased too" % (100 * opaque / total))

    # The figure must still be where it was, unaltered.
    #
    # Erasing the background does not change the pixels that survive, so every opaque
    # pixel should still equal the original at the same coordinates. That is a far
    # stronger test than comparing centroids: it catches a one-pixel nudge, a rescale
    # and an accidental recolour alike.
    original = np.asarray(load_original(), dtype=np.float32)
    masked = np.asarray(img.convert("RGB"), dtype=np.float32)
    kept = alpha > 250

    if kept.sum() > 0:
        diff = float(np.abs(original[kept] - masked[kept]).mean())
        notes.append("kept pixels differ from the original by %.1f / 255 on average" % diff)
        if diff > RGB_TOL:
            problems.append("the surviving pixels no longer match the original (%.1f > %.1f). "
                            "Erase around the figure -- never move, scale, re-centre or "
                            "recolour him" % (diff, RGB_TOL))

    ys, xs = np.nonzero(alpha > 128)
    if len(ys):
        notes.append("figure bounding box: (%d, %d) to (%d, %d)"
                     % (xs.min(), ys.min(), xs.max(), ys.max()))

    # A hard-edged mask on soft pixel art leaves a halo of the old background.
    edge = ((alpha > 5) & (alpha < 250)).sum()
    notes.append("soft-edge pixels: %d (some is good; a hard 1-bit mask will look cut out)" % edge)

    for n in notes:
        print("      " + n)

    if problems:
        print()
        for p in problems:
            print("FAIL  " + p)
        return 1

    print()
    print("PASS  looks good. Copy it over assets/images/paperdoll/base_father.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
