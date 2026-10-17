"""Cut the hand-drawn monster death sheets into frames for the game.

Writes assets/data/death_animations.json and the atlases it points at, from
the PNGs in assets/images/monsters/death_spritesheets/.

The sheets are not uniform grids. Each one is described in SPEC by how many
frames sit on each row, top to bottom; the row and column cuts are found by
looking for empty space near where they should be, and XCUTS / YCUTS pin the
cuts by hand where frames touch or blood streaks run across them.

Each frame is then redrawn alone -- only its own art, so a neighbour's limb or
blood never bleeds in -- into an atlas of identical cells at
assets/images/monsters/death_frames/. In every cell the body sits on the
centre line (the middle of the big pieces' outline; flying heads, dropped
weapons and blood specks do not pull it) and the floor is the bottom edge.

Usage:
    python tools/build_death_animations.py            # rewrite the JSON
    python tools/build_death_animations.py --review D # also draw review strips into D

Requires numpy, scipy and Pillow.
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHEETS = os.path.join(ROOT, 'assets', 'images', 'monsters', 'death_spritesheets')
OUT = os.path.join(ROOT, 'assets', 'data', 'death_animations.json')

# Frames per row, top to bottom, read left to right.
SPEC = {
    'alligator': [2, 2, 2], 'basilisk': [4, 4, 4], 'beholder': [4, 4, 4], 'chimera': [4, 4, 3],
    'dbeast': [6, 6, 6], 'dragon': [4, 4], 'dwarf': [4, 3], 'flayer': [3, 2, 1], 'gargoyle': [4, 4],
    'ghast': [4, 4, 1], 'ghoul': [5, 1], 'giant_ant': [2, 2, 2], 'giant_snake': [5, 2], 'giantt': [4, 4],
    'goblin': [4, 4, 4, 4], 'harpy': [5, 3], 'hobgoblin': [3, 3], 'hooded_skeleton': [5, 4, 4],
    'hydra': [4, 4], 'iron_golem': [4, 4], 'kobold': [3, 2], 'lich': [4, 4], 'medusa': [4, 4],
    'merchant': [4, 3], 'mimic': [4, 4], 'minotaur': [4, 4], 'mummy': [5, 4], 'ogre': [4, 3],
    'orc': [4, 4], 'owlbear': [3, 2], 'purple_worm': [3, 2, 2, 2], 'rust': [3, 2], 'scorpion': [4, 4],
    'skeleton': [3, 3], 'spider': [4, 4], 'troglodyte': [3, 3], 'troll': [5, 4, 4], 'wererat': [4, 4],
    'werewolf': [3, 2], 'wraith': [4, 4], 'wyvern': [3, 3], 'zombie': [4, 2],
}
# Hand-placed cuts, in sheet pixels, where the automatic ones fail.
YCUTS = {'ghast': [500, 1050]}
XCUTS = {
    'ghast': {1: [424, 848, 1272]},      # blood streaks run straight through row 2
    'giantt': {0: [488, 976, 1464]},     # the first two skeletons touch
    'giant_snake': {1: [560]},           # the coils of the last two frames touch
}
# Sheet name -> living monster texture, where they differ.
ALIAS = {'giantt': 'giant', 'scorpion': 'giant_scorpion'}
# Reading order, where the sheet's own left-to-right order is not the order of events.
ORDER = {
    # The sheet puts a standing, arm-raised frame after the first fall; it
    # belongs before the wing tears off, or the body pops back up.
    'gargoyle': [0, 1, 2, 5, 3, 4, 6, 7],
}
# Atlases larger than this on either side are scaled down to fit a GPU texture.
MAX_ATLAS = 4096
OUT_DIR = os.path.join(ROOT, 'assets', 'images', 'monsters', 'death_frames')
# Sheets with no monster to attach to yet.
SKIP = {'merchant'}

# Pacing follows Project Brutality (Doom runs at 35 tics a second). Its short
# deaths hold each frame 5-8 tics -- Imp TR97 A8 B8 C6 D6, Zombieman PSSR A6
# BCD6 -- and its long ones drop to 2-3 tics so they still land inside a
# second (BrutalizedImp4: 15 frames in 0.86s). Across its 120 death states the
# median time to corpse is 0.94s. So: every death lands in about TARGET_SECONDS,
# no frame shorter than 2 tics (it blurs past) or longer than 7 (it drags).
TIC = 1.0 / 35.0
TARGET_SECONDS = 0.65  # 0.8 still read a fraction slow in play (2026-10-03)
MIN_FRAME = 2 * TIC
MAX_FRAME = 7 * TIC


def mask_of(path):
    a = np.array(Image.open(path).convert('RGBA')).astype(int)
    alpha = a[..., 3]
    corners = [a[0, 0], a[0, -1], a[-1, 0], a[-1, -1]]
    if not any(c[3] > 200 for c in corners):
        return alpha > 16
    # Some sheets keep patches of opaque near-black background. Clear only the
    # near-black that is connected to the outside: a colour test alone also
    # eats the dark scales inside a dragon and leaves it full of holes.
    open_space = (alpha <= 16) | (a[..., :3].max(-1) <= 14)
    lab, _ = ndi.label(open_space)
    outside = set(np.unique(lab[alpha <= 16])) | set(np.unique(np.concatenate(
        [lab[0], lab[-1], lab[:, 0], lab[:, -1]])))
    outside.discard(0)
    return (alpha > 16) & ~np.isin(lab, list(outside))


def zero_runs(profile, thr):
    runs, start = [], None
    for i, v in enumerate(profile):
        if v <= thr and start is None:
            start = i
        if v > thr and start is not None:
            runs.append((start, i))
            start = None
    if start is not None:
        runs.append((start, len(profile)))
    return runs


def row_cuts(m, rows, name):
    h = m.shape[0]
    if name in YCUTS:
        return [0] + YCUTS[name] + [h]
    p = m.mean(1)
    gaps = [(a, b) for a, b in zero_runs(p, 0.0015) if a > 0 and b < h and b - a >= 3]
    cuts = [0]
    for j in range(1, rows):
        target = j * h / rows
        near = [(abs((a + b) / 2 - target), (a + b) // 2) for a, b in gaps
                if abs((a + b) / 2 - target) < 0.35 * h / rows]
        if near:
            cuts.append(min(near)[1])
        else:
            lo = max(cuts[-1] + 10, int(target - 0.3 * h / rows))
            hi = min(h - 1, int(target + 0.3 * h / rows))
            cuts.append(lo + int(np.argmin(p[lo:hi])))
    return cuts + [h]


def split_cols(band, n):
    w = band.shape[1]
    p = band.mean(0)
    segs, s = [], 0
    for a, b in zero_runs(p, 0.0005):
        if a > s:
            segs.append([s, a])
        s = b
    if s < w:
        segs.append([s, w])
    # A stray sliver (a border line, a lone speck) is not a frame.
    total = band.sum()
    segs = [sg for sg in segs if band[:, sg[0]:sg[1]].sum() >= 0.02 * total] or segs
    while len(segs) > n:  # too many pieces: fold the lightest into its nearer neighbour
        mass = [band[:, a:b].sum() for a, b in segs]
        i = int(np.argmin(mass))
        left_gap = segs[i][0] - segs[i - 1][1] if i > 0 else None
        right_gap = segs[i + 1][0] - segs[i][1] if i < len(segs) - 1 else None
        j = i - 1 if right_gap is None or (left_gap is not None and left_gap <= right_gap) else i + 1
        lo, hi = min(i, j), max(i, j)
        segs[lo] = [segs[lo][0], segs[hi][1]]
        del segs[hi]
    while len(segs) < n:  # frames touching: split the widest at its thinnest point
        i = int(np.argmax([b - a for a, b in segs]))
        a, b = segs[i]
        lo, hi = a + int((b - a) * 0.3), a + int((b - a) * 0.7)
        x = lo + int(np.argmin(p[lo:hi]))
        segs[i:i + 1] = [[a, x], [x, b]]
    return segs


def pieces(sub):
    """Labelled connected pieces of a frame, specks dropped."""
    lab, n = ndi.label(sub, structure=np.ones((3, 3)))
    if n == 0:
        return sub, lab, []
    sizes = ndi.sum(sub, lab, range(1, n + 1))
    keep_ids = [i + 1 for i, v in enumerate(sizes) if v >= 60]
    if not keep_ids:
        return sub, lab, [(i + 1, v) for i, v in enumerate(sizes)]
    return np.isin(lab, keep_ids), lab, [(i, sizes[i - 1]) for i in keep_ids]


def body_anchor(sub, lab, kept):
    """Horizontal centre of the body's outline: the bounding box of the pieces
    at least a quarter the size of the largest. A flying head, a dropped weapon
    or a spray of blood does not move it; a wing or an outflung limb does,
    because that is what the eye reads as the monster's extent."""
    biggest = max(v for _, v in kept)
    body = np.isin(lab, [i for i, v in kept if v >= 0.25 * biggest])
    ys, xs = np.where(body)
    return (xs.min() + xs.max() + 1) / 2.0


def cells_of(m, name):
    """The rectangle each frame is drawn in, from the row and column cuts.

    These are seeds, not crops: art that strays over a cut still goes home to
    its own frame in assign()."""
    counts = SPEC[name]
    cuts = row_cuts(m, len(counts), name)
    cells = []
    for j, n in enumerate(counts):
        y0, y1 = cuts[j], cuts[j + 1]
        if name in XCUTS and j in XCUTS[name]:
            xc = [0] + XCUTS[name][j] + [m.shape[1]]
            segs = [(xc[i], xc[i + 1]) for i in range(len(xc) - 1)]
        else:
            segs = split_cols(m[y0:y1], n)
        for a, b in segs:
            cells.append((a, y0, b, y1))
    return cells


def assign(m, cells):
    """Label every art pixel with the frame it belongs to.

    A piece of art goes whole to the cell holding most of it, so a severed leg
    that falls past a row cut stays with the frame it fell in. Only a large
    piece that really spans frames -- blood streaks drawn straight across a
    row -- is split at the cuts."""
    owner = np.full(m.shape, -1, dtype=np.int32)
    cell_id = np.full(m.shape, -1, dtype=np.int32)
    for k, (x0, y0, x1, y1) in enumerate(cells):
        cell_id[y0:y1, x0:x1] = k
    lab, n = ndi.label(m, structure=np.ones((3, 3)))
    median_cell = float(np.median([(x1 - x0) * (y1 - y0) for x0, y0, x1, y1 in cells]))
    for i, sl in enumerate(ndi.find_objects(lab)):
        piece = lab[sl] == i + 1
        ids = cell_id[sl][piece]
        counts = np.bincount(ids[ids >= 0], minlength=len(cells))
        if counts.sum() == 0:
            continue
        shares = counts / counts.sum()
        spans = (np.sort(shares)[-2] if len(shares) > 1 else 0) > 0.10
        if spans and piece.sum() > 0.10 * median_cell:
            owner[sl][piece] = ids
        else:
            owner[sl][piece] = int(np.argmax(counts))
    return owner


def frame_pixels(owner, k):
    """One frame's own art, specks dropped, and its body anchor."""
    mine = owner == k
    ys, xs = np.where(mine)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    sub, lab, kept = pieces(mine[y0:y1, x0:x1])
    ys2, xs2 = np.where(sub)
    floor = int(y0 + ys2.max())          # lowest real art: where it meets the floor
    anchor = float(x0 + body_anchor(sub, lab, kept))
    return mine, sub, (int(x0), int(y0)), floor, anchor


def extract(name):
    """Cut a sheet into clean frames: [(rgba_crop, anchor_x_in_crop)], in play order.

    Each crop holds only that frame's own art, and its bottom row is the floor."""
    path = os.path.join(SHEETS, name + '.png')
    m = mask_of(path)
    rgba = np.array(Image.open(path).convert('RGBA'))
    cells = cells_of(m, name)
    if len(cells) != sum(SPEC[name]):
        raise SystemExit('%s: expected %d frames, cut %d' % (name, sum(SPEC[name]), len(cells)))
    owner = assign(m, cells)
    frames = []
    for k in ORDER.get(name, range(len(cells))):
        mine, sub, (ox, oy), floor, anchor = frame_pixels(owner, k)
        h, w = sub.shape
        keep = np.zeros_like(mine)
        keep[oy:oy + h, ox:ox + w] = sub          # this frame's pieces, nothing else
        keep[floor + 1:, :] = False
        ys, xs = np.where(keep)
        x0, x1, y0 = xs.min(), xs.max() + 1, ys.min()
        crop = rgba[y0:floor + 1, x0:x1].copy()
        crop[..., 3] = np.where(keep[y0:floor + 1, x0:x1], crop[..., 3], 0)
        frames.append((crop, anchor - x0))
    areas = [c.shape[0] * c.shape[1] for c, _ in frames]
    for k, a in enumerate(areas):
        if a < 0.15 * float(np.median(areas)):
            raise SystemExit('%s frame %d is a sliver (%dx%d): fix SPEC/XCUTS/YCUTS'
                             % (name, k, frames[k][0].shape[1], frames[k][0].shape[0]))
    return frames


def pack(frames):
    """Lay the frames out in one atlas of identical cells: body anchor on the
    cell's centre line, floor on its bottom edge. Scales down to fit MAX_ATLAS."""
    half = max(max(ax, c.shape[1] - ax) for c, ax in frames)
    cw = int(np.ceil(2 * half)) + 2
    ch = max(c.shape[0] for c, _ in frames)
    n = len(frames)
    best = None
    for cols in range(1, n + 1):
        rows = -(-n // cols)
        dim = max(cols * cw, rows * ch)
        if best is None or dim < best[0]:
            best = (dim, cols, rows)
    _, cols, rows = best
    s = min(1.0, MAX_ATLAS / float(max(cols * cw, rows * ch)))
    cw, ch = int(cw * s), int(ch * s)
    atlas = Image.new('RGBA', (cols * cw, rows * ch), (0, 0, 0, 0))
    sizes = []
    for k, (crop, ax) in enumerate(frames):
        img = Image.fromarray(crop)
        if s < 1.0:
            img = img.resize((max(1, int(round(img.width * s))), max(1, int(round(img.height * s)))),
                             Image.LANCZOS)
        cx, cy = (k % cols) * cw, (k // cols) * ch
        atlas.alpha_composite(img, (int(round(cx + cw / 2.0 - ax * s)), cy + ch - img.height))
        sizes.append(img.size)
    return atlas, cols, rows, cw, ch, sizes


def frame_duration(n):
    return round(min(MAX_FRAME, max(MIN_FRAME, TARGET_SECONDS / n)), 3)


def review(entry, live_scale, out_dir):
    """One strip per monster, drawn from the atlas exactly the way the game
    draws it: frame 0 sized like the living monster, cell centre on the tile
    centre line (yellow), cell bottom on the floor (yellow)."""
    atlas = Image.open(os.path.join(ROOT, 'assets', entry['sheet']))
    cw, ch, cols = entry['frameWidth'], entry['frameHeight'], entry['columns']
    tile = 150.0                                  # px per world unit in the strip
    ux = live_scale[0] * tile / entry['bodyWidth']
    uy = live_scale[1] * tile / entry['bodyHeight']
    out_w, out_h = max(1, int(cw * ux)), max(1, int(ch * uy))
    slot = max(out_w, int(tile * 1.4)) + 10
    floor = out_h + 20
    strip = Image.new('RGBA', (slot * entry['frameCount'], floor + 10), (52, 52, 78, 255))
    d = ImageDraw.Draw(strip)
    for k in range(entry['frameCount']):
        x, y = (k % cols) * cw, (k // cols) * ch
        cell = atlas.crop((x, y, x + cw, y + ch)).resize((out_w, out_h), Image.NEAREST)
        mid = k * slot + slot // 2
        strip.alpha_composite(cell, (mid - out_w // 2, floor - out_h))
        d.line([(mid, 0), (mid, strip.height)], fill=(255, 220, 0, 200), width=1)
        d.line([(mid - tile / 2, floor), (mid + tile / 2, floor)], fill=(255, 220, 0, 200), width=1)
        d.line([(k * slot, 0), (k * slot, strip.height)], fill=(0, 0, 0, 255), width=2)
        d.text((k * slot + 4, 4), str(k), fill=(255, 255, 255, 255))
    strip.convert('RGB').save(os.path.join(out_dir, entry['name'] + '.png'))


def main():
    review_dir = None
    if '--review' in sys.argv:
        review_dir = sys.argv[sys.argv.index('--review') + 1]
        os.makedirs(review_dir, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    monsters = json.load(open(os.path.join(ROOT, 'assets', 'data', 'monsters.json')))
    live = {v['texturePath']: v.get('scale', {'x': 1.0, 'y': 1.0})
            for v in monsters.values() if isinstance(v, dict) and 'texturePath' in v}

    entries = []
    for name in sorted(SPEC):
        if name in SKIP:
            continue
        tex = 'images/monsters/%s.png' % ALIAS.get(name, name)
        if not os.path.exists(os.path.join(ROOT, 'assets', tex)):
            raise SystemExit('%s: no monster texture %s' % (name, tex))
        frames = extract(name)
        atlas, cols, rows, cw, ch, sizes = pack(frames)
        rel = 'images/monsters/death_frames/%s.png' % name
        atlas.save(os.path.join(ROOT, 'assets', rel), optimize=True)
        entry = {
            'name': name, 'monster': tex, 'sheet': rel,
            'columns': cols, 'rows': rows, 'frameCount': len(frames),
            'frameWidth': cw, 'frameHeight': ch,
            'bodyWidth': sizes[0][0], 'bodyHeight': sizes[0][1],
            'widestFrame': max(w for w, _ in sizes),
            'frameDuration': frame_duration(len(frames)),
        }
        entries.append(entry)
        if review_dir:
            sc = live.get(tex, {'x': 1.0, 'y': 1.0})
            review(entry, (sc['x'], sc['y']), review_dir)
        print('%-16s %2d frames  %.3fs/frame  atlas %dx%d  cell %dx%d' % (
            name, len(frames), entry['frameDuration'], atlas.width, atlas.height, cw, ch))

    doc = {
        '_comment': ('Generated by tools/build_death_animations.py from images/monsters/death_spritesheets. '
                     'Each sheet is a grid of frameWidth x frameHeight cells, read left to right, top to bottom; '
                     'every cell has the body centred and the floor on its bottom edge. bodyWidth/bodyHeight '
                     'is the visible size of frame 0, which the game matches to the living monster.'),
        'animations': [{k: v for k, v in e.items() if k != 'name'} for e in entries],
    }
    with open(OUT, 'w', newline='\n') as f:
        json.dump(doc, f, indent=2)
        f.write('\n')
    print('wrote %d animations to %s' % (len(entries), OUT))


if __name__ == '__main__':
    main()
