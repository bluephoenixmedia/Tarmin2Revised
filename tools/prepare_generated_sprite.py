"""
prepare_generated_sprite.py

Turns a generated 2D sprite (an image model's output on a flat background)
into a raw frame for tools/stylize_forest_sprites.py, so it gets the same
palette, outline and rim as the baked 3D sprites.

  1. Keys out the background the way docs/DEsign/image_magick_script.md does:
     the colour at (5, 5), within 20% colour distance, then erodes the alpha
     edge by two pixels to eat the blended fringe.
  2. Crops to the sprite and stands it on the bottom edge of a canvas sized to
     its billboard at the trees' density (96 pixels per world unit), rendered
     at the stylize pass's RAW_SCALE.

Run:
    python tools/prepare_generated_sprite.py <image> <raw name> <billboard width>
then python tools/stylize_forest_sprites.py. The billboard height follows from
the sprite's own proportions and is printed.
"""

import os
import sys

from PIL import Image, ImageChops, ImageFilter

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RAW_DIR = os.path.join(REPO, "build", "forest_raw")
PIXELS_PER_UNIT = 96
RAW_SCALE = 2  # must match stylize_forest_sprites.py
FUZZ = 0.20
ERODE = 5  # a 5x5 minimum is the two-pixel erosion of the ImageMagick script


def key_out_background(img):
    rgb = img.convert("RGB")
    bg = rgb.getpixel((5, 5))
    diff = ImageChops.difference(rgb, Image.new("RGB", rgb.size, bg)).convert("L")
    # Distance from the background colour, against FUZZ of the largest possible.
    alpha = diff.point(lambda d: 255 if d > FUZZ * 255 else 0)
    alpha = alpha.filter(ImageFilter.MinFilter(ERODE))
    out = rgb.convert("RGBA")
    out.putalpha(alpha)
    return out


def main():
    if len(sys.argv) < 4:
        raise SystemExit(__doc__)
    source, name, width_units = sys.argv[1], sys.argv[2], float(sys.argv[3])
    sprite = key_out_background(Image.open(source))
    sprite = sprite.crop(sprite.getchannel("A").getbbox())

    w = round(width_units * PIXELS_PER_UNIT)
    h = round(w * sprite.height / sprite.width)
    raw = sprite.resize((w * RAW_SCALE, h * RAW_SCALE), Image.LANCZOS)
    os.makedirs(RAW_DIR, exist_ok=True)
    out = os.path.join(RAW_DIR, name + ".png")
    raw.save(out)
    print(f"wrote {out}: billboard {width_units:.2f} x {h / PIXELS_PER_UNIT:.2f} units")


if __name__ == "__main__":
    main()
