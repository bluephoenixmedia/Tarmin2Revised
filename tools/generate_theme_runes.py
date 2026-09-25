"""
generate_theme_runes.py

Draws the six themed gate sigils to assets/images/runes/.

The renderer tints these with ChunkTheme.getRuneColor() at draw time, so they
are authored white-on-transparent. Each is an Elder-Futhark-derived stave bent
toward its theme: a thorn that drips for the Colosseum, a wave for the flood, a
sprout for the thicket.

Supersampled 4x and downsampled for clean edges, then given a soft outer glow so
the sigil still reads at torch range.

Run:
    python tools/generate_theme_runes.py
"""

import os

from PIL import Image, ImageDraw, ImageFilter

SIZE = 128
SS = 4                      # supersample factor
W = SIZE * SS
STROKE = 7 * SS
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "assets", "images", "runes")


def new_canvas():
    img = Image.new("L", (W, W), 0)
    return img, ImageDraw.Draw(img)


def line(draw, x1, y1, x2, y2, width=None):
    """Draws a rounded-cap stroke in supersampled space, taking 0..1 coords."""
    w = width or STROKE
    draw.line(
        [(x1 * W, y1 * W), (x2 * W, y2 * W)],
        fill=255,
        width=w,
        joint="curve",
    )
    r = w // 2
    for (px, py) in ((x1, y1), (x2, y2)):
        draw.ellipse(
            [px * W - r, py * W - r, px * W + r, py * W + r],
            fill=255,
        )


def dot(draw, x, y, radius):
    r = radius * W
    draw.ellipse([x * W - r, y * W - r, x * W + r, y * W + r], fill=255)


# --- The six sigils --------------------------------------------------------

def rune_colosseum(draw):
    """Thurisaz: a thorn. The bowl is a blade; a drop falls from its point."""
    line(draw, 0.34, 0.12, 0.34, 0.88)      # stave
    line(draw, 0.34, 0.26, 0.70, 0.44)      # thorn upper
    line(draw, 0.70, 0.44, 0.34, 0.62)      # thorn lower
    dot(draw, 0.70, 0.62, 0.045)            # blood drop
    dot(draw, 0.665, 0.74, 0.028)


def rune_flooded(draw):
    """Laguz: water. The stave bends into a standing wave."""
    line(draw, 0.30, 0.12, 0.30, 0.88)
    line(draw, 0.30, 0.16, 0.66, 0.36)
    # ripple lines beneath
    line(draw, 0.24, 0.66, 0.44, 0.72, STROKE - 2 * SS)
    line(draw, 0.44, 0.72, 0.64, 0.64, STROKE - 2 * SS)
    line(draw, 0.24, 0.80, 0.44, 0.86, STROKE - 2 * SS)
    line(draw, 0.44, 0.86, 0.64, 0.78, STROKE - 2 * SS)


def rune_battalion(draw):
    """Tiwaz: the spear. A martial arrowhead over a marching stave."""
    line(draw, 0.50, 0.10, 0.50, 0.90)
    line(draw, 0.50, 0.10, 0.26, 0.36)
    line(draw, 0.50, 0.10, 0.74, 0.36)
    # rank bars
    line(draw, 0.32, 0.60, 0.68, 0.60, STROKE - 2 * SS)
    line(draw, 0.36, 0.72, 0.64, 0.72, STROKE - 2 * SS)


def rune_graveyard(draw):
    """Raidho over a barrow: a grave marker standing in turned earth."""
    line(draw, 0.36, 0.14, 0.36, 0.70)
    line(draw, 0.18, 0.32, 0.56, 0.32)      # cross-piece
    line(draw, 0.36, 0.44, 0.62, 0.22)      # raidho leg
    # the mound
    line(draw, 0.16, 0.80, 0.36, 0.70, STROKE - 2 * SS)
    line(draw, 0.36, 0.70, 0.58, 0.80, STROKE - 2 * SS)
    line(draw, 0.12, 0.86, 0.66, 0.86, STROKE - 2 * SS)


def rune_castle(draw):
    """Kenaz crowned: a battlement over the torch-stave."""
    line(draw, 0.50, 0.34, 0.50, 0.90)
    line(draw, 0.50, 0.58, 0.26, 0.82)
    line(draw, 0.50, 0.58, 0.74, 0.82)
    # crenellations
    line(draw, 0.22, 0.30, 0.78, 0.30)
    for x in (0.24, 0.40, 0.58, 0.74):
        line(draw, x, 0.30, x, 0.16, STROKE - 2 * SS)


def rune_thicket(draw):
    """Berkano sprouting: the stave puts out two shoots and a cap."""
    line(draw, 0.32, 0.12, 0.32, 0.90)
    line(draw, 0.32, 0.18, 0.64, 0.32)
    line(draw, 0.64, 0.32, 0.32, 0.46)
    line(draw, 0.32, 0.50, 0.66, 0.64)
    line(draw, 0.66, 0.64, 0.32, 0.78)
    dot(draw, 0.66, 0.24, 0.05)             # spore cap
    dot(draw, 0.70, 0.56, 0.038)


def rune_necropolis(draw):
    """Algiz / Sunken Tomb: A central stave with upturned arms over a crypt basin."""
    line(draw, 0.50, 0.18, 0.50, 0.88)
    line(draw, 0.50, 0.42, 0.22, 0.20)
    line(draw, 0.50, 0.42, 0.78, 0.20)
    # Tomb basin base lines
    line(draw, 0.20, 0.70, 0.80, 0.70, STROKE - 2 * SS)
    line(draw, 0.28, 0.82, 0.72, 0.82, STROKE - 2 * SS)
    dot(draw, 0.50, 0.12, 0.04)


def rune_causeway(draw):
    """Ehwaz / Sunken Causeway: Dual staves bridged by crossing causeway staves."""
    line(draw, 0.32, 0.16, 0.32, 0.86)
    line(draw, 0.68, 0.16, 0.68, 0.86)
    line(draw, 0.32, 0.38, 0.68, 0.62)
    line(draw, 0.32, 0.62, 0.68, 0.38)
    dot(draw, 0.50, 0.50, 0.045)


RUNES = {
    "rune_colosseum": rune_colosseum,
    "rune_flooded": rune_flooded,
    "rune_battalion": rune_battalion,
    "rune_graveyard": rune_graveyard,
    "rune_castle": rune_castle,
    "rune_thicket": rune_thicket,
    "rune_necropolis": rune_necropolis,
    "rune_causeway": rune_causeway,
}


def build(name, painter):
    mask, draw = new_canvas()
    painter(draw)

    mask = mask.resize((SIZE, SIZE), Image.LANCZOS)

    # Soft outer glow so the sigil holds together at torch range.
    glow = mask.filter(ImageFilter.GaussianBlur(radius=4))
    glow = glow.point(lambda v: int(v * 0.55))

    alpha = Image.new("L", (SIZE, SIZE), 0)
    alpha.paste(glow)
    alpha = Image.composite(mask, alpha, mask.point(lambda v: 255 if v > 8 else 0))

    out = Image.merge("RGBA", (
        Image.new("L", (SIZE, SIZE), 255),
        Image.new("L", (SIZE, SIZE), 255),
        Image.new("L", (SIZE, SIZE), 255),
        alpha,
    ))
    return out


def main():
    out_dir = os.path.abspath(OUT_DIR)
    os.makedirs(out_dir, exist_ok=True)

    for name, painter in RUNES.items():
        img = build(name, painter)
        path = os.path.join(out_dir, "%s.png" % name)
        img.save(path)
        print("wrote %s" % path)


if __name__ == "__main__":
    main()
