"""
bake_weapon_paperdolls.py

Auto-calibrates and bakes standardized 1024x1536 master canvas layers for weapons
in the 2D Paperdoll System:
1. Loads weapon sprites from assets/images/weapons/
2. Crops alpha bounding box
3. Scales to appropriate hand scale based on archetype
4. Rotates upright to align within MAIN WEAPON REACH corridor (50, 350, 300, 1150)
5. Detects grip center between guard and pommel
6. Anchors grip at Father's right hand palm (X: 185, Y: 870)
7. Saves to assets/images/paperdoll/weapon/<name>.png
"""

import os
import json
from PIL import Image

CANVAS_W = 1024
CANVAS_H = 1536
PALM_ANCHOR_X = 185
PALM_ANCHOR_Y = 870

def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

def find_grip_point(img):
    bbox = img.getbbox()
    if not bbox:
        return None
    min_x, min_y, max_x, max_y = bbox
    total_h = max_y - min_y
    # Grip is located between pommel and guard in lower section
    grip_top = min_y + int(total_h * 0.70)
    grip_bot = min_y + int(total_h * 0.95)
    xs, ys = [], []
    pix = img.load()
    for y in range(grip_top, grip_bot):
        for x in range(min_x, max_x):
            if pix[x, y][3] > 64:
                xs.append(x)
                ys.append(y)
    if not xs:
        return ((min_x + max_x) // 2, min_y + int(total_h * 0.85))
    return (sum(xs) // len(xs), sum(ys) // len(ys))

def get_scale_for_weapon(name):
    n = name.lower()
    if 'two_handed' in n or 'great' in n or 'claymore' in n or 'flamberge' in n:
        return 660
    if 'short' in n or 'pixie' in n or 'dagger' in n or 'knife' in n:
        return 520
    if 'rapier' in n:
        return 600
    return 580

def bake_weapon(src_path, dst_path):
    im = Image.open(src_path).convert('RGBA')
    bbox = im.getbbox()
    if not bbox:
        print(f"Skipping empty image: {src_path}")
        return False
    
    name = os.path.splitext(os.path.basename(src_path))[0]
    scale_size = get_scale_for_weapon(name)
    
    cropped = im.crop(bbox)
    scaled = cropped.resize((scale_size, scale_size), Image.Resampling.LANCZOS)
    
    # Most generated / standard weapons are diagonal 45 degrees
    rot = scaled.rotate(45, resample=Image.Resampling.BICUBIC, expand=True)
    
    pt = find_grip_point(rot)
    if not pt:
        print(f"Could not find grip point for {src_path}")
        return False
        
    gx, gy = pt
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    paste_x = PALM_ANCHOR_X - gx
    paste_y = PALM_ANCHOR_Y - gy
    layer.paste(rot, (paste_x, paste_y), rot)
    
    ensure_dir(os.path.dirname(dst_path))
    layer.save(dst_path)
    print(f"Baked paperdoll weapon: {dst_path} (scale={scale_size}, grip={pt})")
    return True

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    weapons_dir = os.path.join(repo_root, 'assets', 'images', 'weapons')
    out_dir = os.path.join(repo_root, 'assets', 'images', 'paperdoll', 'weapon')
    ensure_dir(out_dir)
    
    # Process Batch 1 swords
    batch1_swords = [
        'sword_khopesh.png',
        'sword_long.png',
        'sword_mandible.png',
        'sword_mariners.png',
        'sword_piercer.png',
        'sword_pixie.png',
        'sword_rapier.png',
        'sword_sabre.png',
        'sword_scimitar.png',
        'sword_short.png',
        'sword_talwar.png',
        'sword_two_handed.png',
        'sword_wakizashi.png',
    ]
    
    count = 0
    for fname in batch1_swords:
        src = os.path.join(weapons_dir, fname)
        if os.path.exists(src):
            dst = os.path.join(out_dir, fname)
            if bake_weapon(src, dst):
                count += 1
                
    print(f"Successfully baked {count} weapon paperdoll layers.")

if __name__ == '__main__':
    main()
