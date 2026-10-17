"""
prepare_tiling_texture.py

Turns a generated pixel-art tiling texture (usually a large JPEG from an image
model) into game art: box-reduces it onto its own pixel grid and snaps it to a
small palette taken from the image, which clears the JPEG smear and the
half-blended edges between pixel blocks. A box reduce by a whole factor keeps a
seamless texture seamless.

Run:
    python tools/prepare_tiling_texture.py <source> <output.png> [size] [colours]

size defaults to 256, colours to 8. The source should be a whole multiple of size.
"""

import sys

from PIL import Image


def prepare(source, output, size=256, colours=8):
    img = Image.open(source).convert("RGB")
    if img.width % size or img.height % size:
        print(f"warning: {img.size} is not a multiple of {size}; the reduce will blend across blocks")
    small = img.resize((size, size), Image.BOX)
    # Median cut on the reduced image keeps the art's own colours; no dithering,
    # which would put noise back in.
    snapped = small.quantize(colors=colours, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    snapped.convert("RGB").save(output, optimize=True)
    print("wrote", output)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    prepare(sys.argv[1], sys.argv[2],
            int(sys.argv[3]) if len(sys.argv) > 3 else 256,
            int(sys.argv[4]) if len(sys.argv) > 4 else 8)
