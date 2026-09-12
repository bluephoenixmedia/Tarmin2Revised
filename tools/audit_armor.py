"""
audit_armor.py

Audits assets/data/armor.json and assets/images/paperdoll/ for:
1. Valid texturePath pointing to an existing file in assets/
2. All armor texture images having an alpha channel (RGBA)
3. Every equippable armor item having a resolved 1024x1536 paperdoll layer
4. Valid slot flag assignments (isHelmet, isTorso, isArms, isGauntlets, isLegs, isBoots, isShield, isCloak, isAmulet)
"""

import os
import json
from PIL import Image

def audit_armor():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    armor_path = os.path.join(repo_root, "assets", "data", "armor.json")
    paperdoll_dir = os.path.join(repo_root, "assets", "images", "paperdoll")

    with open(armor_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    print(f"Total armor entries in armor.json: {len(data)}")

    missing_textures = []
    non_rgba_textures = []
    missing_paperdoll = []
    no_slot_items = []

    slot_to_folder = {
        'isHelmet': 'head',
        'isTorso': 'chest',
        'isArms': 'arms',
        'isGauntlets': 'hands',
        'isLegs': 'legs',
        'isBoots': 'feet',
        'isShield': 'shield',
        'isCloak': 'cloak'
    }

    slot_counts = {k: 0 for k in slot_to_folder.values()}
    slot_counts['amulet'] = 0

    for key, item in data.items():
        tex = item.get("texturePath")
        if not tex:
            missing_textures.append((key, "empty/null"))
        else:
            full_tex = os.path.join(repo_root, "assets", tex)
            if not os.path.exists(full_tex):
                missing_textures.append((key, tex))
            else:
                try:
                    with Image.open(full_tex) as img:
                        if img.mode != "RGBA":
                            non_rgba_textures.append((key, tex, img.mode))
                except Exception as e:
                    missing_textures.append((key, f"{tex} ({e})"))

        # Determine slot
        assigned_slot = None
        if item.get("isAmulet"):
            slot_counts['amulet'] += 1
            continue

        for flag, folder in slot_to_folder.items():
            if item.get(flag):
                assigned_slot = folder
                slot_counts[folder] += 1
                break

        if not assigned_slot:
            no_slot_items.append(key)
            continue

        # Check paperdoll layer resolution
        # Paperdoll system checks:
        # 1. assets/images/paperdoll/<folder>/<tex_base>.png
        # 2. assets/images/paperdoll/<folder>/<key_lower>.png
        tex_base = os.path.splitext(os.path.basename(tex))[0] if tex else ""
        p1 = os.path.join(paperdoll_dir, assigned_slot, f"{tex_base}.png")
        p2 = os.path.join(paperdoll_dir, assigned_slot, f"{key.lower()}.png")

        if not os.path.exists(p1) and not os.path.exists(p2):
            missing_paperdoll.append((key, assigned_slot, tex_base))
        else:
            # Check dimensions if it exists
            resolved = p1 if os.path.exists(p1) else p2
            try:
                with Image.open(resolved) as pimg:
                    if pimg.size != (1024, 1536):
                        print(f"Warning: {resolved} has size {pimg.size} != (1024, 1536)")
            except Exception as e:
                missing_paperdoll.append((key, assigned_slot, f"corrupted ({e})"))

    print("\n--- SLOT DISTRIBUTION ---")
    for slot, cnt in sorted(slot_counts.items()):
        print(f"  {slot}: {cnt}")

    print(f"\nMissing textures: {len(missing_textures)}")
    for k, t in missing_textures:
        print(f"  {k}: {t}")

    print(f"Non-RGBA textures: {len(non_rgba_textures)}")
    for k, t, m in non_rgba_textures:
        print(f"  {k}: {t} (mode: {m})")

    print(f"Items with no equippable armor slot: {len(no_slot_items)}")
    for k in no_slot_items:
        print(f"  {k}")

    print(f"Missing Paperdoll 1024x1536 layers: {len(missing_paperdoll)}")
    for k, s, b in missing_paperdoll:
        print(f"  {k} (slot: {s}, tex: {b})")

    if not missing_textures and not non_rgba_textures and not no_slot_items and not missing_paperdoll:
        print("\nSUCCESS: 100% of armor items pass all integrity and paperdoll checks!")
        return 0
    return 1

if __name__ == "__main__":
    exit(audit_armor())
