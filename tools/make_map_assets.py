"""Builds the expedition map's surface texture and its stand-in icons.

The vellum is the designer's AI-generated dark vellum (source
docs/art/map_dark_vellum_source.jpg, prompt in docs/DEsign/Requirements_
Expedition Map.md, section 9). At 2048px it would cost the game 2MB for a
backdrop that sits under ink washes, so it is downscaled to 512px and quantised
to the game's flat, posterised look.

The icons are stand-ins until the generated icon sheet arrives: Kenney's Game
Icons (docs/game_assets, licensed), white silhouettes on transparency, scaled
to 48px. The map tints them at draw time, so one white icon serves every state.

Run from the repo root:  python tools/make_map_assets.py
"""
import os

from PIL import Image

OUT = os.path.join("assets", "images", "map")
VELLUM_SRC = os.path.join("docs", "art", "map_dark_vellum_source.jpg")
VELLUM_SIZE = 512
VELLUM_COLOURS = 12

KENNEY = os.path.join("docs", "game_assets", "Kenney Game Assets 1 version 42", "2D assets")
BASE = os.path.join(KENNEY, "Game Icons", "PNG", "White", "2x")
EXPANSION = os.path.join(KENNEY, "Game Icons Expansion", "PNG", "White", "2x")
ICON_SIZE = 48

# map icon name -> Kenney source
ICONS = {
    "home": os.path.join(BASE, "home.png"),
    "shelter": os.path.join(BASE, "home.png"),
    "castle": os.path.join(BASE, "warning.png"),
    "seal": os.path.join(BASE, "star.png"),
    "seal_won": os.path.join(BASE, "checkmark.png"),
    "ladder_up": os.path.join(BASE, "arrowUp.png"),
    "ladder_down": os.path.join(BASE, "arrowDown.png"),
    "player": os.path.join(BASE, "up.png"),
    "waypoint": os.path.join(EXPANSION, "flag.png"),
    "grave": os.path.join(BASE, "cross.png"),
    "portal": os.path.join(BASE, "target.png"),
    "pin_danger": os.path.join(BASE, "warning.png"),
    "pin_loot": os.path.join(EXPANSION, "coin.png"),
    "pin_return": os.path.join(BASE, "return.png"),
    "pin_trader": os.path.join(BASE, "shoppingBasket.png"),
    "pin_locked": os.path.join(BASE, "locked.png"),
    "pin_unknown": os.path.join(BASE, "question.png"),
}


def make_vellum():
    src = Image.open(VELLUM_SRC).convert("RGB")
    small = src.resize((VELLUM_SIZE, VELLUM_SIZE), Image.LANCZOS)
    flat = small.quantize(colors=VELLUM_COLOURS, method=Image.MEDIANCUT).convert("RGB")
    flat.save(os.path.join(OUT, "dark_vellum.png"))


def make_icons():
    os.makedirs(os.path.join(OUT, "icons"), exist_ok=True)
    for name, path in ICONS.items():
        icon = Image.open(path).convert("RGBA").resize((ICON_SIZE, ICON_SIZE), Image.LANCZOS)
        # Pure white with the source's coverage as alpha, so a tint is the colour drawn.
        alpha = icon.getchannel("A")
        white = Image.new("RGBA", icon.size, (255, 255, 255, 0))
        white.putalpha(alpha)
        white.save(os.path.join(OUT, "icons", name + ".png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    make_vellum()
    make_icons()
