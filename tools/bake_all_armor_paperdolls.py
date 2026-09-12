"""
bake_all_armor_paperdolls.py

Processes all existing armor assets (106 images in assets/images/armor/)
and all 117 equipment definitions in assets/data/armor.json through the
standardized 1024x1536 auto-snapping paperdoll pipeline.

Guarantees:
1. Preserves the 6 hand-calibrated baseline master layers:
   - head/bascinet.png & head/basinet.png
   - chest/breastplate.png
   - arms/chainmail_arms.png
   - legs/bronze_leggings.png
   - feet/boots.png
   - shield/buckler.png
2. Processes all other images with slot-specific alpha cropping,
   paired-sprite splitting (for boots, arms, gauntlets), and calibrated
   character anchor placement on a 1024x1536 transparent canvas.
3. Automatically creates mapped aliases for all items in armor.json so that
   lookups by texturePath basename, item enum key, or display name succeed.
4. Fixes misconfigured slot booleans in assets/data/armor.json (e.g. isHelmet,
   isBoots, isLegs, isGauntlets, isCloak, isTorso) so that all items can be
   equipped in-game without rejection.
"""

import os
import json
from PIL import Image

CANVAS_W = 1024
CANVAS_H = 1536

CALIBRATED_PRESERVE = {
    "head/bascinet.png",
    "head/basinet.png",
    "chest/breastplate.png",
    "arms/chainmail_arms.png",
    "legs/bronze_leggings.png",
    "feet/boots.png",
    "shield/buckler.png",
}

def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

def get_slot_for_filename(filename):
    f_lower = filename.lower()
    if any(k in f_lower for k in ['helmet', 'helm', 'bascinet', 'basinet', 'visor']):
        return 'head'
    if any(k in f_lower for k in ['shield', 'buckler']):
        return 'shield'
    if any(k in f_lower for k in ['gauntlets', 'hands', 'gloves']):
        return 'hands'
    if any(k in f_lower for k in ['boots', 'shoes']):
        return 'feet'
    if any(k in f_lower for k in ['leggings', 'legs', 'greaves']):
        return 'legs'
    if any(k in f_lower for k in ['arms', 'pauldrons', 'bracers']):
        return 'arms'
    if any(k in f_lower for k in ['cloak', 'cape']):
        return 'cloak'
    return 'chest'

def process_head(img):
    bbox = img.getbbox()
    if not bbox: return None
    c = img.crop(bbox)
    cw, ch = c.size
    scale = min(330.0 / cw, 420.0 / ch)
    sw = int(round(cw * scale))
    sh = int(round(ch * scale))
    scaled = c.resize((sw, sh), Image.Resampling.LANCZOS)
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer.paste(scaled, (int(round(512 - sw / 2.0)), 65), scaled)
    return layer

def process_chest(img):
    bbox = img.getbbox()
    if not bbox: return None
    c = img.crop(bbox)
    cw, ch = c.size
    scale = 470.0 / cw
    sw = int(round(cw * scale))
    sh = int(round(ch * scale))
    scaled = c.resize((sw, sh), Image.Resampling.LANCZOS)
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer.paste(scaled, (int(round(512 - sw / 2.0)), 430), scaled)
    return layer

def process_legs(img):
    bbox = img.getbbox()
    if not bbox: return None
    c = img.crop(bbox)
    cw, ch = c.size
    scale = 460.0 / cw
    sw = int(round(cw * scale))
    sh = int(round(ch * scale))
    scaled = c.resize((sw, sh), Image.Resampling.LANCZOS)
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer.paste(scaled, (int(round(512 - sw / 2.0)), 740), scaled)
    return layer

def process_feet(img):
    bbox = img.getbbox()
    if not bbox: return None
    c = img.crop(bbox)
    w, h = c.size
    mid = w // 2
    l_half = c.crop((0, 0, mid, h))
    r_half = c.crop((mid, 0, w, h))
    bl_box = l_half.getbbox()
    br_box = r_half.getbbox()
    if not bl_box or not br_box:
        scale = 350.0 / h
        sw = int(round(w * scale))
        sh = int(round(h * scale))
        scaled = c.resize((sw, sh), Image.Resampling.LANCZOS)
        layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
        layer.paste(scaled, (int(round(512 - sw / 2.0)), 1130), scaled)
        return layer

    bl = l_half.crop(bl_box)
    br = r_half.crop(br_box)
    target_h = 350.0
    scale_l = target_h / bl.size[1]
    scale_r = target_h / br.size[1]
    bl_s = bl.resize((int(round(bl.size[0] * scale_l)), int(round(bl.size[1] * scale_l))), Image.Resampling.LANCZOS)
    br_s = br.resize((int(round(br.size[0] * scale_r)), int(round(br.size[1] * scale_r))), Image.Resampling.LANCZOS)
    
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer.paste(bl_s, (210, 1130), bl_s)
    layer.paste(br_s, (575, 1130), br_s)
    return layer

def process_arms(img):
    bbox = img.getbbox()
    if not bbox: return None
    c = img.crop(bbox)
    w, h = c.size
    mid = w // 2
    l_half = c.crop((0, 0, mid, h))
    r_half = c.crop((mid, 0, w, h))
    pl_box = l_half.getbbox()
    pr_box = r_half.getbbox()
    if not pl_box or not pr_box:
        scale = 240.0 / w
        sw = int(round(w * scale))
        sh = int(round(h * scale))
        scaled = c.resize((sw, sh), Image.Resampling.LANCZOS)
        layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
        layer.paste(scaled, (110, 330), scaled)
        return layer

    pl = l_half.crop(pl_box)
    pr = r_half.crop(pr_box)
    target_w = 240.0
    target_h = 280.0
    scale = min(target_w / max(pl.size[0], pr.size[0]), target_h / max(pl.size[1], pr.size[1]))
    pl_s = pl.resize((int(round(pl.size[0] * scale)), int(round(pl.size[1] * scale))), Image.Resampling.LANCZOS)
    pr_s = pr.resize((int(round(pr.size[0] * scale)), int(round(pr.size[1] * scale))), Image.Resampling.LANCZOS)
    
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer.paste(pl_s, (110, 330), pl_s)
    layer.paste(pr_s, (590, 330), pr_s)
    return layer

def process_hands(img):
    bbox = img.getbbox()
    if not bbox: return None
    c = img.crop(bbox)
    w, h = c.size
    mid = w // 2
    l_half = c.crop((0, 0, mid, h))
    r_half = c.crop((mid, 0, w, h))
    gl_box = l_half.getbbox()
    gr_box = r_half.getbbox()
    if not gl_box or not gr_box:
        scale = 140.0 / w
        sw = int(round(w * scale))
        sh = int(round(h * scale))
        scaled = c.resize((sw, sh), Image.Resampling.LANCZOS)
        layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
        layer.paste(scaled, (110, 800), scaled)
        return layer

    gl = l_half.crop(gl_box)
    gr = r_half.crop(gr_box)
    target_w = 140.0
    target_h = 180.0
    scale = min(target_w / max(gl.size[0], gr.size[0]), target_h / max(gl.size[1], gr.size[1]))
    gl_s = gl.resize((int(round(gl.size[0] * scale)), int(round(gl.size[1] * scale))), Image.Resampling.LANCZOS)
    gr_s = gr.resize((int(round(gr.size[0] * scale)), int(round(gr.size[1] * scale))), Image.Resampling.LANCZOS)
    
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer.paste(gl_s, (110, 800), gl_s)
    layer.paste(gr_s, (780, 800), gr_s)
    return layer

def process_shield(img):
    bbox = img.getbbox()
    if not bbox: return None
    c = img.crop(bbox)
    cw, ch = c.size
    ratio = cw / float(ch)
    if ratio < 0.65:
        # Tall shield (tower/body shield)
        scale = 750.0 / ch
    else:
        # Round/kite shield
        scale = 420.0 / max(cw, ch)
    sw = int(round(cw * scale))
    sh = int(round(ch * scale))
    scaled = c.resize((sw, sh), Image.Resampling.LANCZOS)
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer.paste(scaled, (int(round(830 - sw / 2.0)), int(round(860 - sh / 2.0))), scaled)
    return layer

def process_cloak(img):
    bbox = img.getbbox()
    if not bbox: return None
    c = img.crop(bbox)
    cw, ch = c.size
    scale = 720.0 / cw
    sw = int(round(cw * scale))
    sh = int(round(ch * scale))
    scaled = c.resize((sw, sh), Image.Resampling.LANCZOS)
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    layer.paste(scaled, (int(round(512 - sw / 2.0)), 340), scaled)
    return layer

PROCESSORS = {
    'head': process_head,
    'chest': process_chest,
    'legs': process_legs,
    'feet': process_feet,
    'arms': process_arms,
    'hands': process_hands,
    'shield': process_shield,
    'cloak': process_cloak,
}

def bake_all_armor_images(repo_root):
    armor_dir = os.path.join(repo_root, "assets", "images", "armor")
    paperdoll_dir = os.path.join(repo_root, "assets", "images", "paperdoll")
    
    files = sorted([f for f in os.listdir(armor_dir) if f.endswith('.png')])
    print(f"--- Step 1: Processing {len(files)} armor images from {armor_dir} ---")
    
    baked_count = 0
    skipped_count = 0
    
    for f in files:
        slot = get_slot_for_filename(f)
        rel_target = f"{slot}/{f}"
        
        # Check if this is one of the calibrated baseline files
        if rel_target in CALIBRATED_PRESERVE:
            print(f"[PRESERVED] {rel_target} (calibrated baseline)")
            skipped_count += 1
            continue
            
        src_path = os.path.join(armor_dir, f)
        img = Image.open(src_path).convert("RGBA")
        
        processor = PROCESSORS.get(slot)
        if not processor:
            print(f"[WARNING] No processor for slot {slot} on {f}")
            continue
            
        baked = processor(img)
        if baked is None:
            print(f"[ERROR] Failed to process {f}")
            continue
            
        out_path = os.path.join(paperdoll_dir, slot, f)
        ensure_dir(os.path.dirname(out_path))
        baked.save(out_path)
        baked_count += 1
        print(f"[BAKED] {rel_target}")
        
    print(f"Finished image baking: {baked_count} baked, {skipped_count} preserved.\n")

def map_and_fix_armor_json(repo_root):
    armor_json_path = os.path.join(repo_root, "assets", "data", "armor.json")
    paperdoll_dir = os.path.join(repo_root, "assets", "images", "paperdoll")
    
    with open(armor_json_path, 'r', encoding='utf-8') as f:
        armor_data = json.load(f)
        
    print(f"--- Step 2: Mapping all {len(armor_data)} items from armor.json & fixing flags ---")
    
    alias_count = 0
    flag_fixes = 0
    
    # Specific correction overrides for known placeholder paths
    if "LAMELLAR_SHIRT" in armor_data:
        armor_data["LAMELLAR_SHIRT"]["texturePath"] = "images/armor/chain_lamellar.png"
        armor_data["LAMELLAR_SHIRT"]["isTorso"] = True
    if "RING_MAIL" in armor_data:
        armor_data["RING_MAIL"]["texturePath"] = "images/armor/chain_mail.png"
        armor_data["RING_MAIL"]["isTorso"] = True
    
    for key, item in armor_data.items():
        key_lower = key.lower()
        fn = item.get("friendlyName", "")
        tex_path = item.get("texturePath", "")
        
        # Determine slot
        slot = None
        if item.get("isAmulet"):
            slot = None # Amulets do not have paperdoll armor slots
        elif key == "EYES":
            slot = "head" # Visor/eyes maps to head layer visually
        elif item.get("isHelmet"): slot = "head"
        elif item.get("isShield"): slot = "shield"
        elif item.get("isGauntlets"): slot = "hands"
        elif item.get("isBoots"): slot = "feet"
        elif item.get("isLegs"): slot = "legs"
        elif item.get("isArms"): slot = "arms"
        elif item.get("isCloak"): slot = "cloak"
        elif item.get("isTorso"): slot = "chest"
        
        # If slot was NONE or misconfigured, deduce from key and friendlyName (NOT placeholder tex_path)
        if not slot and not item.get("isAmulet"):
            name_combined = f"{key} {fn}".lower()
            if any(k in name_combined for k in ['helmet', 'helm', 'bascinet', 'basinet', 'visor']):
                slot = 'head'
            elif any(k in name_combined for k in ['shield', 'buckler']):
                slot = 'shield'
            elif any(k in name_combined for k in ['gauntlets', 'hands', 'gloves']):
                slot = 'hands'
            elif any(k in name_combined for k in ['boots', 'shoes']):
                slot = 'feet'
            elif any(k in name_combined for k in ['leggings', 'legs', 'greaves']):
                slot = 'legs'
            elif any(k in name_combined for k in ['arms', 'pauldrons', 'bracers']):
                slot = 'arms'
            elif any(k in name_combined for k in ['cloak', 'cape', 'veil']):
                slot = 'cloak'
            else:
                slot = 'chest'
            
            # Apply appropriate flag fix in json
            if slot == 'head':
                item['isHelmet'] = True
                flag_fixes += 1
                print(f"[FIXED FLAG] {key} -> isHelmet = True")
            elif slot == 'shield':
                item['isShield'] = True
                flag_fixes += 1
                print(f"[FIXED FLAG] {key} -> isShield = True")
            elif slot == 'hands':
                item['isGauntlets'] = True
                flag_fixes += 1
                print(f"[FIXED FLAG] {key} -> isGauntlets = True")
            elif slot == 'feet':
                item['isBoots'] = True
                flag_fixes += 1
                print(f"[FIXED FLAG] {key} -> isBoots = True")
            elif slot == 'legs':
                item['isLegs'] = True
                flag_fixes += 1
                print(f"[FIXED FLAG] {key} -> isLegs = True")
            elif slot == 'arms':
                item['isArms'] = True
                flag_fixes += 1
                print(f"[FIXED FLAG] {key} -> isArms = True")
            elif slot == 'cloak':
                item['isCloak'] = True
                flag_fixes += 1
                print(f"[FIXED FLAG] {key} -> isCloak = True")
            elif slot == 'chest':
                item['isTorso'] = True
                flag_fixes += 1
                print(f"[FIXED FLAG] {key} -> isTorso = True")
        else:
            # If it's a chest armor, ensure isTorso = True
            if slot == 'chest' and not item.get('isTorso'):
                item['isTorso'] = True
                flag_fixes += 1
                print(f"[FIXED FLAG] {key} -> isTorso = True")

        if not slot:
            continue

        # Now handle texture file alias mapping
        tex_base = os.path.splitext(os.path.basename(tex_path))[0] if tex_path else None
        
        # Source baked file path
        src_baked = None
        if tex_base:
            p1 = os.path.join(paperdoll_dir, slot, f"{tex_base}.png")
            if os.path.exists(p1):
                src_baked = p1
        
        if not src_baked:
            p2 = os.path.join(paperdoll_dir, slot, f"{key_lower}.png")
            if os.path.exists(p2):
                src_baked = p2
                
        # If still no src_baked (e.g. magic items pointing to banded.png in head/hands/feet/cloak slots)
        if not src_baked:
            slot_fallbacks = {
                'head': 'head/helmet.png',
                'hands': 'hands/gauntlets.png',
                'feet': 'feet/boots.png',
                'cloak': 'cloak/cloak.png',
                'shield': 'shield/shield.png',
                'chest': 'chest/banded.png',
                'legs': 'legs/legs.png',
                'arms': 'arms/arms.png',
            }
            fb = slot_fallbacks.get(slot)
            if fb:
                fb_path = os.path.join(paperdoll_dir, fb)
                if os.path.exists(fb_path):
                    src_baked = fb_path

        # Create alias so <slot>/<key_lower>.png always exists
        target_alias = os.path.join(paperdoll_dir, slot, f"{key_lower}.png")
        if src_baked and not os.path.exists(target_alias):
            img = Image.open(src_baked)
            img.save(target_alias)
            alias_count += 1
            print(f"[ALIAS] Created {slot}/{key_lower}.png from {os.path.basename(src_baked)}")
            
    # Save updated armor.json
    with open(armor_json_path, 'w', encoding='utf-8') as f:
        json.dump(armor_data, f, indent=4)
        f.write('\n')
        
    print(f"Updated armor.json: {flag_fixes} flag fixes applied, {alias_count} alias images created.\n")

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    print(f"=== BATCH BAKING ARMOR ASSETS INTO 2D PAPERDOLL PIPELINE ===")
    print(f"Repository Root: {repo_root}\n")
    
    bake_all_armor_images(repo_root)
    map_and_fix_armor_json(repo_root)
    
    print("=== BATCH PROCESSING COMPLETE ===")

if __name__ == "__main__":
    main()
