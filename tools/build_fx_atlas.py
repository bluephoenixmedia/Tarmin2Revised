"""Packs the frame-sequence animations in assets/images/ui into one atlas the game can play.

The animation packs arrive as folders of frame0000.png, frame0001.png, ... A folder per clip would
be hundreds of tiny texture loads, so every clip listed in CLIPS below is shelf-packed into a single
atlas (assets/images/fx/fx_atlas.png) and described in assets/data/fx.json: for each clip, the
rectangle of every frame, its duration, and whether it blends additively.

Adding an effect is adding a line to CLIPS and re-running this script; the game reads fx.json, so no
code changes are needed to make a new clip available by its id. Run from the repository root.
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'assets', 'images', 'ui')
ATLAS_PNG = os.path.join(ROOT, 'assets', 'images', 'fx', 'fx_atlas.png')
FX_JSON = os.path.join(ROOT, 'assets', 'data', 'fx.json')
ATLAS_PATH_IN_GAME = 'images/fx/fx_atlas.png'
MAX_WIDTH = 2048
PAD = 2  # transparent gutter around every frame so linear filtering never bleeds a neighbour in

# id: (folder under assets/images/ui, seconds the whole clip lasts, additive blend?)
CLIPS = {
    # Blood and hit feedback
    'blood_splatter_small': ('burst_splatter_001_small_red', 0.40, False),
    'blood_splatter_large': ('burst_splatter_001_large_red', 0.50, False),
    'hit_smoke': ('directional_smoke_burst_001/directional_smoke_burst_001_small_white', 0.60, False),
    'hit_sparks': ('directional_impact_004/directional_impact_004_small_yellow', 0.30, True),
    # Attention
    'alert': ('symbol_alert_001_large_red', 1.20, False),
}


def frames_of(folder):
    path = os.path.join(SRC, *folder.split('/'))
    names = sorted(f for f in os.listdir(path) if f.lower().endswith('.png'))
    if not names:
        raise SystemExit('no frames in ' + path)
    return [Image.open(os.path.join(path, n)).convert('RGBA') for n in names]


def main():
    placed = []  # (clip id, frame index, image)
    for clip_id, (folder, _, _) in CLIPS.items():
        for i, im in enumerate(frames_of(folder)):
            placed.append((clip_id, i, im))

    # Shelf packing: tallest first keeps the shelves tight enough for this many small frames.
    order = sorted(range(len(placed)), key=lambda k: -placed[k][2].size[1])
    x = y = shelf_h = 0
    positions = {}
    for k in order:
        w, h = placed[k][2].size
        if x + w + PAD > MAX_WIDTH:
            x, y, shelf_h = 0, y + shelf_h + PAD, 0
        positions[k] = (x + PAD // 2, y + PAD // 2)
        x += w + PAD
        shelf_h = max(shelf_h, h + PAD)
    height = y + shelf_h
    atlas_h = 1
    while atlas_h < height:
        atlas_h *= 2

    atlas = Image.new('RGBA', (MAX_WIDTH, atlas_h), (0, 0, 0, 0))
    clips = {}
    for k, (clip_id, i, im) in enumerate(placed):
        px, py = positions[k]
        atlas.paste(im, (px, py))
        clip = clips.setdefault(clip_id, {'frames': []})
        while len(clip['frames']) <= i:
            clip['frames'].append(None)
        clip['frames'][i] = [px, py, im.size[0], im.size[1]]
    for clip_id, (folder, duration, additive) in CLIPS.items():
        clips[clip_id]['duration'] = duration
        clips[clip_id]['additive'] = additive
        clips[clip_id]['frameWidth'] = clips[clip_id]['frames'][0][2]
        clips[clip_id]['frameHeight'] = clips[clip_id]['frames'][0][3]

    os.makedirs(os.path.dirname(ATLAS_PNG), exist_ok=True)
    atlas.save(ATLAS_PNG, optimize=True)
    with open(FX_JSON, 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'atlas': ATLAS_PATH_IN_GAME, 'atlasWidth': MAX_WIDTH, 'atlasHeight': atlas_h, 'clips': clips},
                  f, indent=2)
        f.write('\n')
    print('packed %d clips, %d frames into %dx%d (%d KB)' % (
        len(clips), len(placed), MAX_WIDTH, atlas_h, os.path.getsize(ATLAS_PNG) // 1024))


if __name__ == '__main__':
    sys.exit(main())
