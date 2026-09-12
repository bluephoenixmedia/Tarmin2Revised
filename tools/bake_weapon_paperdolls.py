"""
bake_weapon_paperdolls.py

Auto-calibrates and bakes standardized 1024x1536 master canvas layers for all weapons
in the 2D Paperdoll System:
1. Loads weapon sprites from assets/images/weapons/
2. Crops alpha bounding box
3. Scales to appropriate hand scale based on archetype
4. Rotates upright to align within MAIN WEAPON REACH corridor (50, 350, 300, 1150)
5. Detects grip center
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

def find_grip_point(img, is_bow=False, is_crossbow=False):
    bbox = img.getbbox()
    if not bbox:
        return None
    min_x, min_y, max_x, max_y = bbox
    total_w = max_x - min_x
    total_h = max_y - min_y
    
    if is_bow:
        # Center of the bow stave
        return (min_x + int(total_w * 0.46), min_y + int(total_h * 0.50))
    elif is_crossbow:
        # Stock trigger area
        return (min_x + int(total_w * 0.40), min_y + int(total_h * 0.75))
    
    # Standard melee / polearm / dagger: grip between pommel and guard in lower section
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

def get_scale_and_rotation(name):
    n = name.lower()
    # Rotation angle and target dimension
    if 'bow' in n and 'cross' not in n:
        return (760, 48, True, False)
    if 'cross' in n:
        return (520, 45, False, True)
    if any(k in n for k in ['polearm', 'spear', 'lance', 'pike', 'staff', 'javelin', 'halberd']):
        return (720, 45, False, False)
    if any(k in n for k in ['two_handed', 'great', 'claymore', 'flamberge', 'battle_axe']):
        return (660, 45, False, False)
    if any(k in n for k in ['short', 'pixie', 'dagger', 'knife', 'dart', 'claw', 'spike']):
        return (520, 45, False, False)
    if 'rapier' in n:
        return (600, 45, False, False)
    return (580, 45, False, False)

def bake_weapon(src_path, dst_path):
    im = Image.open(src_path).convert('RGBA')
    bbox = im.getbbox()
    if not bbox:
        return False
    
    name = os.path.splitext(os.path.basename(src_path))[0]
    scale_size, rot_angle, is_bow, is_cb = get_scale_and_rotation(name)
    
    cropped = im.crop(bbox)
    cw, ch = cropped.size
    
    if is_bow:
        scaled = cropped.resize((scale_size, int(scale_size * ch / max(1, cw))), Image.Resampling.LANCZOS)
    elif is_cb:
        scaled = cropped.resize((scale_size, int(scale_size * ch / max(1, cw))), Image.Resampling.LANCZOS)
    else:
        scaled = cropped.resize((scale_size, scale_size), Image.Resampling.LANCZOS)
        
    rot = scaled.rotate(rot_angle, resample=Image.Resampling.BICUBIC, expand=True)
    pt = find_grip_point(rot, is_bow=is_bow, is_crossbow=is_cb)
    if not pt:
        return False
        
    gx, gy = pt
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    paste_x = PALM_ANCHOR_X - gx
    paste_y = PALM_ANCHOR_Y - gy
    layer.paste(rot, (paste_x, paste_y), rot)
    
    ensure_dir(os.path.dirname(dst_path))
    layer.save(dst_path)
    return True

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    weapons_dir = os.path.join(repo_root, 'assets', 'images', 'weapons')
    out_dir = os.path.join(repo_root, 'assets', 'images', 'paperdoll', 'weapon')
    ensure_dir(out_dir)
    
    baked = 0
    for fname in sorted(os.listdir(weapons_dir)):
        if fname.endswith('.png') and not any(k in fname.lower() for k in ['debris', 'bolt', 'arrow', 'quarrel']):
            src = os.path.join(weapons_dir, fname)
            dst = os.path.join(out_dir, fname.lower())
            if bake_weapon(src, dst):
                baked += 1
                
    print(f"Successfully baked {baked} weapon paperdoll layers.")

if __name__ == '__main__':
    main()
