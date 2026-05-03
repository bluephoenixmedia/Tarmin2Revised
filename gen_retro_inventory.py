"""
Generate a retro (Atari/Intellivision 2-bit style) inventory screen image
matching the layout of the modern inventory screen.
"""
from PIL import Image, ImageDraw

# ═══════════════════════════════════════════
# PALETTE  (Intellivision-inspired, ~4-bit)
# ═══════════════════════════════════════════
BLACK      = (8,   5,   2)
BG_DARK    = (22,  14,  5)
BG_MID     = (72,  48,  20)
TILE_A     = (105, 75,  38)
TILE_B     = (88,  62,  30)
GOLD       = (210, 170, 32)
GOLD_DK    = (148, 115, 18)
SLOT_BG    = (18,  12,  6)
CREAM      = (228, 210, 168)
SKIN       = (210, 158, 102)
TUNIC_LT   = (178, 125, 65)
TUNIC_DK   = (138,  92, 42)
BELT_BRN   = (78,  48,  18)
BELT_MTL   = (188, 178, 148)
PANTS_DK   = (55,  46,  38)
PANTS_MD   = (72,  60,  48)
BOOT_BRN   = (115, 72,  32)
GREY_LT    = (188, 182, 168)
GREY_MD    = (128, 122, 112)
GREY_DK    = (72,  68,  62)
RED        = (210, 42,  15)
BLUE       = (30,  62,  205)
GREEN      = (18,  148, 62)
YELLOW_LT  = (238, 218, 28)
PURPLE     = (128, 52,  215)
CYAN_DK    = (8,   118, 128)
ORANGE     = (218, 95,  22)

# ═══════════════════════════════════════════
# CANVAS  (320×240 logical → ×7 = 2240×1680)
# ═══════════════════════════════════════════
SCALE = 7
LW, LH = 342, 256          # logical width/height
W, H    = LW*SCALE, LH*SCALE

img = Image.new('RGB', (W, H), BG_DARK)
d   = ImageDraw.Draw(img)

def px(lx, ly, color, lw=1, lh=1):
    x1 = lx * SCALE
    y1 = ly * SCALE
    x2 = (lx + lw) * SCALE - 1
    y2 = (ly + lh) * SCALE - 1
    d.rectangle([x1, y1, x2, y2], fill=color)

def hline(lx, ly, length, color):
    for i in range(length): px(lx+i, ly, color)

def vline(lx, ly, length, color):
    for i in range(length): px(lx, ly+i, color)

def filled_rect(lx, ly, lw, lh, color):
    d.rectangle([lx*SCALE, ly*SCALE, (lx+lw)*SCALE-1, (ly+lh)*SCALE-1], fill=color)

def border(lx, ly, lw, lh, color, thick=1):
    for t in range(thick):
        hline(lx+t,      ly+t,      lw-2*t, color)   # top
        hline(lx+t,      ly+lh-1-t, lw-2*t, color)   # bottom
        vline(lx+t,      ly+t+1,    lh-2-2*t, color)  # left
        vline(lx+lw-1-t, ly+t+1,    lh-2-2*t, color)  # right

def draw_slot(lx, ly, size):
    filled_rect(lx, ly, size, size, SLOT_BG)
    border(lx, ly, size, size, GOLD_DK)
    border(lx+1, ly+1, size-2, size-2, GOLD)

# ═══════════════════════════════════════════
# OUTER FRAME  (gold ornate border)
# ═══════════════════════════════════════════
filled_rect(0, 0, LW, LH, BG_MID)

# Multi-layer gold frame
layers = [
    (0,   0,   LW,   LH,   GOLD_DK),
    (1,   1,   LW-2, LH-2, GOLD),
    (2,   2,   LW-4, LH-4, GOLD_DK),
    (3,   3,   LW-6, LH-6, GOLD),
    (4,   4,   LW-8, LH-8, BG_DARK),
]
for lx,ly,lw,lh,c in layers:
    border(lx, ly, lw, lh, c)

# Corner ornament dots
for (cx, cy) in [(4,4),(LW-5,4),(4,LH-5),(LW-5,LH-5)]:
    px(cx, cy, GOLD)
    px(cx-1,cy, GOLD_DK); px(cx+1,cy, GOLD_DK)
    px(cx,cy-1, GOLD_DK); px(cx,cy+1, GOLD_DK)

# ═══════════════════════════════════════════
# CENTRE TILED MANNEQUIN AREA
# ═══════════════════════════════════════════
CX, CY = 89, 6
CW, CH = 166, 244
TILE = 12

for ty in range(CH // TILE + 2):
    for tx in range(CW // TILE + 2):
        c = TILE_A if (tx+ty)%2==0 else TILE_B
        x0 = CX + tx*TILE
        y0 = CY + ty*TILE
        w_ = min(TILE, CX+CW-x0)
        h_ = min(TILE, CY+CH-y0)
        if w_ > 0 and h_ > 0:
            filled_rect(x0, y0, w_, h_, c)

# thin inner border on tile area
border(CX, CY, CW, CH, GOLD_DK)

# ═══════════════════════════════════════════
# SLOT LAYOUT
# ═══════════════════════════════════════════
S  = 36   # slot size
G  = 4    # gap

# Left column x positions
LPX = 11          # left panel x
LPX2 = LPX + S + G  # second column in left panel

# Right column x positions
RPX = LW - S*2 - G - 11
RPX2 = RPX + S + G

# Row y positions
R1 = 12
R2 = R1 + S + G
R3 = R2 + S + G
R4 = R3 + S + G
R5 = R4 + S + G
R6 = R5 + S + G

# ═══════════════════════════════════════════
# DRAW LEFT SLOTS  (empty – items drawn dynamically at runtime)
# ═══════════════════════════════════════════
draw_slot(LPX+S//2-S//2, R1, S)   # helmet
draw_slot(LPX+S//2-S//2, R2, S)   # chest
draw_slot(LPX,  R3, S)            # left glove
draw_slot(LPX2, R3, S)            # right glove
draw_slot(LPX+S//2-S//2, R4, S)   # pants
draw_slot(LPX,  R5, S)            # left boot
draw_slot(LPX2, R5, S)            # right boot

# ═══════════════════════════════════════════
# DRAW RIGHT SLOTS  (empty – items drawn dynamically at runtime)
# ═══════════════════════════════════════════
draw_slot(RPX2, R1, S)   # necklace
draw_slot(RPX,  R2, S)   # body slot 1
draw_slot(RPX2, R2, S)   # body slot 2
draw_slot(RPX,  R3, S)   # weapon
draw_slot(RPX2, R3, S)   # off-hand / shield
draw_slot(RPX,  R4, S)   # ring 1
draw_slot(RPX2, R4, S)   # ring 2
draw_slot(RPX,  R5, S)   # ring 3
draw_slot(RPX2, R5, S)   # ring 4

# ═══════════════════════════════════════════
# GIANT SPRITE  (24×24, from MonsterSpriteData.java – TAN variant)
# ═══════════════════════════════════════════
# '#' = entity color (TAN variant of Giant)
GIANT_COLOR   = (200, 180, 130)   # Intellivision TAN
GIANT_COLOR_DK = (148, 130,  88)  # darker shade for simple shading

GIANT = [
    "........................",
    "........................",
    "..........##............",
    ".........####...........",
    ".........####...........",
    ".........####...........",
    "..........##............",
    ".......########.........",
    ".......########.........",
    "......##########........",
    "......##########........",
    ".....##.######.##.......",
    "....##..######..##......",
    "....#...######...#......",
    ".....#.########.#.......",
    ".......########.........",
    ".......########.........",
    ".......###..###.........",
    ".......###..###.........",
    ".......###..###.........",
    "........##..##..........",
    "......####..####........",
    "........................",
    "........................",
]

SPRITE_SCALE = 7   # each sprite pixel = 7×7 logical pixels → 168×168 logical

fig_w = 24 * SPRITE_SCALE
fig_h = 24 * SPRITE_SCALE
fig_x = CX + (CW - fig_w) // 2
fig_y = CY + (CH - fig_h) // 2

for ry, row_str in enumerate(GIANT):
    for rx, ch in enumerate(row_str):
        if ch == '#':
            # simple top-lit shading: upper rows slightly brighter
            c = GIANT_COLOR if ry < 14 else GIANT_COLOR_DK
            px(fig_x + rx * SPRITE_SCALE,
               fig_y + ry * SPRITE_SCALE,
               c, SPRITE_SCALE, SPRITE_SCALE)

# ═══════════════════════════════════════════
# SCANLINE OVERLAY  (every other row darker = CRT/retro effect)
# ═══════════════════════════════════════════
scan_overlay = Image.new('RGBA', (W, H), (0,0,0,0))
sd = ImageDraw.Draw(scan_overlay)
for row in range(0, H, 2):
    sd.rectangle([0, row, W-1, row], fill=(0,0,0,28))
img_rgba = img.convert('RGBA')
img_rgba.alpha_composite(scan_overlay)
img = img_rgba.convert('RGB')

# ═══════════════════════════════════════════
# SAVE
# ═══════════════════════════════════════════
out_path = r'c:\Users\denni\Desktop\Tarmin2\assets\images\ui\retro_inventory.png'
import os
os.makedirs(os.path.dirname(out_path), exist_ok=True)
img.save(out_path)
print(f"Saved {W}x{H} image to {out_path}")
