"""
generate_paperdoll_templates.py

Bakes the standardized 1024x1536 master canvas assets for the 2D Paperdoll System:
1. assets/images/paperdoll/base_father.png
2. tools/paperdoll_templates/base_father_1024x1536.png
3. tools/paperdoll_templates/slot_guides_1024x1536.png
4. The 6 calibrated baseline armor pieces as 1024x1536 auto-snapping transparent PNGs:
   - head/bascinet.png & head/basinet.png
   - chest/breastplate.png
   - arms/chainmail_arms.png
   - legs/bronze_leggings.png
   - feet/boots.png
   - shield/buckler.png
"""

import os
from PIL import Image, ImageDraw, ImageFont

CANVAS_W = 1024
CANVAS_H = 1536

# Golden portrait inner box in assets/images/new_inventory.png (2676 x 1568):
# Left: 485, Top: 222, Right: 946, Bottom: 1032
CROP_BOX = (485, 222, 946, 1032)

def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

def generate_base_father(repo_root):
    new_inv_path = os.path.join(repo_root, "assets", "images", "new_inventory.png")
    if not os.path.exists(new_inv_path):
        raise FileNotFoundError(f"Missing {new_inv_path}")
    
    inv = Image.open(new_inv_path)
    cropped = inv.crop(CROP_BOX)
    father_1024 = cropped.resize((CANVAS_W, CANVAS_H), Image.Resampling.LANCZOS)
    
    # Save to assets/images/paperdoll/base_father.png
    out_base = os.path.join(repo_root, "assets", "images", "paperdoll", "base_father.png")
    ensure_dir(os.path.dirname(out_base))
    father_1024.save(out_base)
    print(f"Saved {out_base}")
    
    # Also save to templates directory
    tmpl_base = os.path.join(repo_root, "tools", "paperdoll_templates", "base_father_1024x1536.png")
    ensure_dir(os.path.dirname(tmpl_base))
    father_1024.save(tmpl_base)
    print(f"Saved {tmpl_base}")
    
    return father_1024

def generate_slot_guides(repo_root, father_img):
    guides = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(guides)
    
    # Slot regions: (name, (x1, y1, x2, y2), color_rgba)
    zones = [
        ("HEAD", (320, 60, 704, 460), (255, 200, 50, 100)),
        ("CHEST", (270, 420, 754, 980), (50, 150, 255, 80)),
        ("ARMS / SHOULDERS", (80, 310, 944, 620), (255, 100, 100, 80)),
        ("MAIN HAND (R)", (110, 780, 260, 980), (100, 255, 100, 90)),
        ("OFF HAND (L)", (764, 780, 914, 980), (100, 255, 100, 90)),
        ("LEGS", (260, 740, 764, 1200), (200, 100, 255, 80)),
        ("FEET / BOOTS", (180, 1120, 844, 1500), (255, 165, 0, 90)),
        ("MAIN WEAPON REACH", (50, 350, 300, 1150), (100, 255, 100, 50)),
        ("SHIELD ZONE", (640, 560, 990, 1160), (150, 200, 255, 60)),
    ]
    
    for name, box, col in zones:
        draw.rectangle(box, fill=col, outline=(col[0], col[1], col[2], 220), width=3)
        draw.text((box[0] + 8, box[1] + 8), name, fill=(255, 255, 255, 240))
        
    out_guides = os.path.join(repo_root, "tools", "paperdoll_templates", "slot_guides_1024x1536.png")
    guides.save(out_guides)
    print(f"Saved {out_guides}")
    
    # Composite preview with father for artists
    preview = Image.alpha_composite(father_img.copy(), guides)
    out_preview = os.path.join(repo_root, "tools", "paperdoll_templates", "template_overview_guide.png")
    preview.save(out_preview)
    print(f"Saved {out_preview}")

def bake_calibrated_layers(repo_root):
    paperdoll_dir = os.path.join(repo_root, "assets", "images", "paperdoll")
    armor_dir = os.path.join(repo_root, "assets", "images", "armor")
    
    # 1. LEGS: bronze_leggings
    legs_src = Image.open(os.path.join(armor_dir, "bronze_leggings.png")).convert("RGBA")
    s_l = 0.53
    lw = int(round(legs_src.size[0] * s_l))
    lh = int(round(legs_src.size[1] * s_l))
    legs_scaled = legs_src.resize((lw, lh), Image.Resampling.LANCZOS)
    layer_legs = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer_legs.paste(legs_scaled, (int(round(512 - lw / 2.0)), 740), legs_scaled)
    legs_path = os.path.join(paperdoll_dir, "legs", "bronze_leggings.png")
    ensure_dir(os.path.dirname(legs_path))
    layer_legs.save(legs_path)
    print(f"Baked {legs_path}")

    # 2. FEET: boots
    boots_src = Image.open(os.path.join(armor_dir, "boots.png")).convert("RGBA")
    bw, bh = boots_src.size
    mid = bw // 2
    b_left = boots_src.crop((0, 0, mid, bh))
    b_right = boots_src.crop((mid, 0, bw, bh))
    s_b = 0.86
    bl_s = b_left.resize((int(round(b_left.size[0] * s_b)), int(round(b_left.size[1] * s_b))), Image.Resampling.LANCZOS)
    br_s = b_right.resize((int(round(b_right.size[0] * s_b)), int(round(b_right.size[1] * s_b))), Image.Resampling.LANCZOS)
    layer_boots = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer_boots.paste(bl_s, (210, 1130), bl_s)
    layer_boots.paste(br_s, (575, 1130), br_s)
    boots_path = os.path.join(paperdoll_dir, "feet", "boots.png")
    ensure_dir(os.path.dirname(boots_path))
    layer_boots.save(boots_path)
    print(f"Baked {boots_path}")

    # 3. CHEST: breastplate
    bp_src = Image.open(os.path.join(armor_dir, "breastplate.png")).convert("RGBA")
    s_bp = 0.65
    bpw = int(round(bp_src.size[0] * s_bp))
    bph = int(round(bp_src.size[1] * s_bp))
    bp_s = bp_src.resize((bpw, bph), Image.Resampling.LANCZOS)
    layer_bp = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer_bp.paste(bp_s, (int(round(512 - bpw / 2.0)), int(round(455 - 38 * s_bp))), bp_s)
    bp_path = os.path.join(paperdoll_dir, "chest", "breastplate.png")
    ensure_dir(os.path.dirname(bp_path))
    layer_bp.save(bp_path)
    print(f"Baked {bp_path}")

    # 4. ARMS: chainmail_arms
    arms_src = Image.open(os.path.join(armor_dir, "chainmail_arms.png")).convert("RGBA")
    aw, ah = arms_src.size
    mid_a = aw // 2
    p_left = arms_src.crop((0, 0, mid_a, ah))
    p_right = arms_src.crop((mid_a, 0, aw, ah))
    s_a = 0.60
    pl_s = p_left.resize((int(round(p_left.size[0] * s_a)), int(round(p_left.size[1] * s_a))), Image.Resampling.LANCZOS)
    pr_s = p_right.resize((int(round(p_right.size[0] * s_a)), int(round(p_right.size[1] * s_a))), Image.Resampling.LANCZOS)
    layer_arms = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer_arms.paste(pl_s, (110, 330), pl_s)
    layer_arms.paste(pr_s, (590, 330), pr_s)
    arms_path = os.path.join(paperdoll_dir, "arms", "chainmail_arms.png")
    ensure_dir(os.path.dirname(arms_path))
    layer_arms.save(arms_path)
    print(f"Baked {arms_path}")

    # 5. SHIELD: buckler
    buckler_src = Image.open(os.path.join(armor_dir, "buckler.png")).convert("RGBA")
    s_sh = 0.30
    shw = int(round(buckler_src.size[0] * s_sh))
    shh = int(round(buckler_src.size[1] * s_sh))
    sh_s = buckler_src.resize((shw, shh), Image.Resampling.LANCZOS)
    layer_shield = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer_shield.paste(sh_s, (int(round(830 - shw / 2.0)), int(round(860 - shh / 2.0))), sh_s)
    shield_path = os.path.join(paperdoll_dir, "shield", "buckler.png")
    ensure_dir(os.path.dirname(shield_path))
    layer_shield.save(shield_path)
    print(f"Baked {shield_path}")

    # 6. HEAD: bascinet (save both bascinet.png and basinet.png)
    helm_src = Image.open(os.path.join(armor_dir, "bascinet.png")).convert("RGBA")
    s_h = 0.78
    hw = int(round(helm_src.size[0] * s_h))
    hh = int(round(helm_src.size[1] * s_h))
    helm_s = helm_src.resize((hw, hh), Image.Resampling.LANCZOS)
    layer_helm = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer_helm.paste(helm_s, (int(round(512 - 229 * s_h)), int(round(283 - 309.5 * s_h)) - 16), helm_s)
    
    for fname in ["bascinet.png", "basinet.png"]:
        helm_path = os.path.join(paperdoll_dir, "head", fname)
        ensure_dir(os.path.dirname(helm_path))
        layer_helm.save(helm_path)
        print(f"Baked {helm_path}")

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    print(f"Processing paperdoll standard assets in {repo_root}...")
    father = generate_base_father(repo_root)
    generate_slot_guides(repo_root, father)
    bake_calibrated_layers(repo_root)
    print("Paperdoll templates and calibrated master layers successfully generated!")

if __name__ == "__main__":
    main()
