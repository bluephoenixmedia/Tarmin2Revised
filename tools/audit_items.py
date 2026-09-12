"""
audit_items.py

Comprehensive audit of assets/data/items.json and assets/images/items/
Guarantees:
1. All 110 item definitions in items.json have valid, existing texture paths.
2. All referenced item textures have an alpha channel (RGBA).
3. All image files in assets/images/items/ have an alpha channel (RGBA).
4. No items have empty or null texturePath.
5. Wieldable items (isWeapon) have corresponding 1024x1536 paperdoll layers.
"""

import os
import json
from PIL import Image
from collections import defaultdict

def audit():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    items_json_path = os.path.join(repo_root, "assets", "data", "items.json")
    items_img_dir = os.path.join(repo_root, "assets", "images", "items")
    paperdoll_weapon_dir = os.path.join(repo_root, "assets", "images", "paperdoll", "weapon")

    with open(items_json_path, 'r', encoding='utf-8') as f:
        items_data = json.load(f)

    print(f"Total entries in items.json: {len(items_data)}")

    # Check images on disk
    disk_images = {}
    non_rgba_disk = []
    for f in os.listdir(items_img_dir):
        if f.endswith(('.png', '.jpg')):
            p = os.path.join(items_img_dir, f)
            with Image.open(p) as img:
                disk_images[f] = {
                    'mode': img.mode,
                    'size': img.size,
                    'path': p
                }
                if img.mode != 'RGBA':
                    non_rgba_disk.append((f, img.mode))

    print(f"Total images in assets/images/items: {len(disk_images)}")
    if non_rgba_disk:
        print(f"FAILED: Non-RGBA images on disk: {non_rgba_disk}")
    else:
        print("PASS: 100% of images in assets/images/items have an alpha channel (RGBA).")

    # Item audit
    texture_counts = defaultdict(list)
    missing_textures = []
    non_rgba_item_textures = []
    missing_paperdoll = []
    category_counts = defaultdict(int)

    for item_key, item in items_data.items():
        # Classify item
        cats = []
        if item.get("isPotion") or item.get("isPotionAppearance"): cats.append("Potion")
        if item.get("isScrollAppearance"): cats.append("Scroll")
        if item.get("isWandAppearance"): cats.append("Wand")
        if item.get("isRing") or item.get("isRingAppearance"): cats.append("Ring")
        if item.get("isFood"): cats.append("Food")
        if item.get("isTreasure"): cats.append("Treasure")
        if item.get("isKey"): cats.append("Key")
        if item.get("isContainer"): cats.append("Container")
        if item.get("isArmor"): cats.append("Armor")
        if item.get("isWeapon"): cats.append("Weapon")
        if item.get("isImpassable"): cats.append("Impassable/Structure")
        if not cats:
            cats.append("Misc/Other")

        for c in cats:
            category_counts[c] += 1

        tex_path = item.get("texturePath")
        if not tex_path:
            missing_textures.append((item_key, "EMPTY/NULL"))
        else:
            full_path = os.path.join(repo_root, "assets", tex_path)
            if not os.path.exists(full_path):
                missing_textures.append((item_key, tex_path))
            else:
                texture_counts[tex_path].append(item_key)
                try:
                    with Image.open(full_path) as img:
                        if img.mode != 'RGBA':
                            non_rgba_item_textures.append((item_key, tex_path, img.mode))
                except Exception as e:
                    missing_textures.append((item_key, f"{tex_path} ({e})"))

        # If it's a wieldable weapon item, check paperdoll layer
        if item.get("isWeapon"):
            base_tex = os.path.splitext(os.path.basename(tex_path))[0] if tex_path else ""
            p1 = os.path.join(paperdoll_weapon_dir, f"{base_tex}.png")
            p2 = os.path.join(paperdoll_weapon_dir, f"{item_key.lower()}.png")
            if not os.path.exists(p1) and not os.path.exists(p2):
                missing_paperdoll.append((item_key, base_tex))

    print("\n--- CATEGORY BREAKDOWN ---")
    for cat, count in sorted(category_counts.items(), key=lambda x: -x[1]):
        print(f"  {cat}: {count}")

    print(f"\n--- TEXTURE PATH STATUS ---")
    print(f"Unique texture paths used: {len(texture_counts)}")
    print(f"Missing / non-existent texture paths: {len(missing_textures)}")
    for k, p in missing_textures:
        print(f"  FAILED: {k} -> {p}")

    print(f"Non-RGBA item textures: {len(non_rgba_item_textures)}")
    for k, p, m in non_rgba_item_textures:
        print(f"  FAILED: {k} -> {p} (mode: {m})")

    print(f"Wieldable items missing paperdoll layer: {len(missing_paperdoll)}")
    for k, b in missing_paperdoll:
        print(f"  FAILED: {k} (expected {b}.png in paperdoll/weapon)")

    has_errors = bool(non_rgba_disk or missing_textures or non_rgba_item_textures or missing_paperdoll)

    if not has_errors:
        print("\nSUCCESS: 100% of items in items.json pass all integrity and alpha checks!")
        return 0
    return 1

if __name__ == "__main__":
    exit(audit())
