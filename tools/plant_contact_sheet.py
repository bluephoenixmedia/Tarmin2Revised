"""
plant_contact_sheet.py

Lays the thumbnails from tools/blender/render_plant_sheet.py out as numbered
contact sheets, after the same stylize pass the forest sprites get, so plants
are picked by how they will actually look in game.

Run:
    python tools/plant_contact_sheet.py
Writes build/plant_sheet_<page>.png. The number under each plant is the
<index> prefix of its thumbnail, which is what the bake list refers to.
"""

import glob
import os

from PIL import Image, ImageDraw

import stylize_forest_sprites as stylize

REPO = stylize.REPO
THUMB_DIR = os.path.join(REPO, "build", "plant_thumbs")
OUT_PATTERN = os.path.join(REPO, "build", "plant_sheet_{}.png")

COLUMNS = 12
PER_PAGE = 96
CELL = 128
LABEL = 14
BACKDROP = (46, 58, 66)


def main():
    paths = sorted(glob.glob(os.path.join(THUMB_DIR, "*.png")))
    if not paths:
        raise SystemExit("No thumbnails in " + THUMB_DIR + "; run tools/blender/render_plant_sheet.py first.")
    frames = [stylize.load(p) for p in paths]
    green_cuts = stylize.thresholds(frames, True)
    other_cuts = stylize.thresholds(frames, False)

    for page_start in range(0, len(paths), PER_PAGE):
        page = list(zip(paths, frames))[page_start:page_start + PER_PAGE]
        rows = (len(page) + COLUMNS - 1) // COLUMNS
        sheet = Image.new("RGB", (COLUMNS * CELL, rows * (CELL + LABEL)), BACKDROP)
        draw = ImageDraw.Draw(sheet)
        for i, (path, (rgb, mask)) in enumerate(page):
            sprite = stylize.stylize(rgb, mask, green_cuts, other_cuts)
            sprite.thumbnail((CELL, CELL), Image.NEAREST)
            x = (i % COLUMNS) * CELL
            y = (i // COLUMNS) * (CELL + LABEL)
            sheet.paste(sprite, (x + (CELL - sprite.width) // 2, y + CELL - sprite.height), sprite)
            draw.text((x + 3, y + CELL), os.path.basename(path)[:3], fill=(230, 220, 180))
        out = OUT_PATTERN.format(page_start // PER_PAGE + 1)
        sheet.save(out)
        print("wrote", out)


if __name__ == "__main__":
    main()
