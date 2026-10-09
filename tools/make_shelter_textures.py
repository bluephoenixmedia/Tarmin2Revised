"""Derives each biome's outpost-shelter wall texture from the licensed Viking Realm pack.

Outpost shelters stand in every surface biome, and each should read as its own
building: a log cabin in the Forest, a snowed-in lodge in the Tundra, a stilt hut
of weathered planks in the Lakelands, a fortified ruin in the Blight. The Viking
Realm pack (docs/game_assets, a Humble Bundle purchase) has tileable logs, planks
and fieldstone at 2048px. They are painted smooth; the game's walls are 128px with
flat posterised shading. So each source is downscaled, remapped onto a biome ramp
(luminance kept, so the artist's grain survives), and quantised.

The Desert's adobe has no source in the packs, so it is not made here: it is
AI-generated art (source docs/art/shelter_wall_desert_source.jpg, prompt in
docs/DEsign/Requirements_ Shelter Roads.md, section 3), cropped to six whole brick
courses so it tiles vertically, downscaled to 128px and quantised to 12 colours.

Run from the repo root:  python tools/make_shelter_textures.py
"""
import os

from PIL import Image, ImageOps

SRC = os.path.join("docs", "game_assets", "POLYGON_Viking_Realm_SourceFiles_v3", "SourceFiles", "Textures")
OUT = os.path.join("assets", "images")
SIZE = 128
LEVELS = 6

# (output, source, ramp from shadow to highlight, contrast)
WALLS = [
    ("shelter_wall_forest.png", "Logs_01.png",
     [(0.0, (34, 24, 14)), (0.5, (96, 70, 40)), (1.0, (168, 134, 88))], 1.3),
    ("shelter_wall_tundra.png", "Logs_02.png",
     [(0.0, (22, 24, 30)), (0.5, (70, 66, 64)), (1.0, (150, 156, 168))], 1.3),
    ("shelter_wall_lakelands.png", "Planks_01.png",
     [(0.0, (26, 32, 28)), (0.5, (84, 92, 76)), (1.0, (150, 156, 132))], 1.25),
    ("shelter_wall_blight.png", "PolygonVikingRealm_Stone_Wall_03.png",
     [(0.0, (16, 10, 10)), (0.5, (62, 44, 40)), (1.0, (130, 110, 100))], 1.35),
]


def ramp(lum, stops):
    for (l0, c0), (l1, c1) in zip(stops, stops[1:]):
        if lum <= l1:
            t = 0.0 if l1 == l0 else (lum - l0) / (l1 - l0)
            return tuple(int(a + (b - a) * t) for a, b in zip(c0, c1))
    return stops[-1][1]


def make(source, stops, contrast):
    img = Image.open(os.path.join(SRC, source)).convert("RGB").resize((SIZE, SIZE), Image.LANCZOS)
    # The paintings sit in a narrow mid band; stretch it so the ramp's shadow and highlight are both used.
    grey = ImageOps.autocontrast(img.convert("L"), cutoff=2).load()
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            lum = grey[x, y] / 255.0
            lum = max(0.0, min(1.0, 0.5 + (lum - 0.5) * contrast))
            lum = round(lum * (LEVELS - 1)) / (LEVELS - 1)  # the game's flat bands
            px[x, y] = ramp(lum, stops)
    return img


def main():
    for out, source, stops, contrast in WALLS:
        path = os.path.join(OUT, out)
        make(source, stops, contrast).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
