"""
candidate_sheet.py

Lays the thumbnails from tools/blender/render_candidates.py out as one
numbered, labelled contact sheet, for picking a biome's art by eye.

Run:
    python tools/candidate_sheet.py <set>
Writes build/candidates/<set>_sheet.png.
"""

import glob
import os
import sys

from PIL import Image, ImageDraw

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
COLUMNS = 8
CELL = 160
LABEL = 14
BACKDROP = (46, 58, 66)


def main():
    set_name = sys.argv[1] if len(sys.argv) > 1 else "desert"
    paths = sorted(glob.glob(os.path.join(REPO, "build", "candidates", set_name, "*.png")))
    if not paths:
        raise SystemExit("No thumbnails; run tools/blender/render_candidates.py first.")
    rows = (len(paths) + COLUMNS - 1) // COLUMNS
    sheet = Image.new("RGB", (COLUMNS * CELL, rows * (CELL + LABEL)), BACKDROP)
    draw = ImageDraw.Draw(sheet)
    for i, path in enumerate(paths):
        thumb = Image.open(path).convert("RGBA")
        x = (i % COLUMNS) * CELL
        y = (i // COLUMNS) * (CELL + LABEL)
        sheet.paste(thumb, (x, y), thumb)
        draw.text((x + 3, y + CELL), os.path.basename(path)[:-4][:24], fill=(230, 220, 180))
    out = os.path.join(REPO, "build", "candidates", set_name + "_sheet.png")
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
