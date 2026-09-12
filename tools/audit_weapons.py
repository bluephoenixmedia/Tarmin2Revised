import json
import os
import re
from PIL import Image

def norm(s):
    return re.sub(r'[^a-z0-9]', '', str(s).lower())

def main():
    json_path = 'assets/data/weapons.json'
    with open(json_path, 'r', encoding='utf-8') as f:
        weapons = json.load(f)

    weapon_dir = 'assets/images/weapons'
    disk_images = [f for f in os.listdir(weapon_dir) if f.endswith('.png')]
    disk_norm = {norm(os.path.splitext(f)[0]): f for f in disk_images}

    print(f"Total entries in weapons.json: {len(weapons)}")
    print(f"Total weapon images in assets/images/weapons: {len(disk_images)}")

    # Check alpha on all images
    alpha_modes = {}
    no_alpha = []
    for f in disk_images:
        p = os.path.join(weapon_dir, f)
        with Image.open(p) as img:
            alpha_modes[img.mode] = alpha_modes.get(img.mode, 0) + 1
            if 'A' not in img.mode:
                no_alpha.append(f)

    print(f"Image modes in assets/images/weapons: {alpha_modes}")
    if no_alpha:
        print(f"Images lacking alpha channel in weapons dir: {no_alpha}")
    else:
        print("All images in assets/images/weapons have an alpha channel.")

    # Check items/bow.png
    item_bow_path = 'assets/images/items/bow.png'
    if os.path.exists(item_bow_path):
        with Image.open(item_bow_path) as img:
            print(f"assets/images/items/bow.png mode: {img.mode}, size: {img.size}")

    # Categorize weapons.json
    bow_item_fallback = [] # images/items/bow.png
    bow_weapon_fallback = [] # images/weapons/bow.png (for non-bow)
    other_fallback = []
    has_valid_texture = []

    for k, v in weapons.items():
        tex = v.get('texturePath', '')
        if tex == 'images/items/bow.png':
            bow_item_fallback.append(k)
        elif tex == 'images/weapons/bow.png':
            if k in ['BOW', 'BOW_SHORT', 'BOW_FOLDING_SHORT']:
                has_valid_texture.append(k)
            else:
                bow_weapon_fallback.append(k)
        elif not tex:
            other_fallback.append(k)
        else:
            full = os.path.join('assets', tex)
            if os.path.exists(full):
                has_valid_texture.append(k)
            else:
                other_fallback.append(k)

    print("\n--- WEAPON TEXTURE STATUS ---")
    print(f"Properly mapped with existing textures: {len(has_valid_texture)}")
    print(f"Pointing to images/items/bow.png (RGB, NO ALPHA): {len(bow_item_fallback)}")
    print(f"Pointing to images/weapons/bow.png (Generic bow placeholder): {len(bow_weapon_fallback)}")
    print(f"Other missing / empty texturePath: {len(other_fallback)}")

    # Unreferenced disk images
    referenced_filenames = set()
    for k, v in weapons.items():
        tex = v.get('texturePath', '')
        if tex:
            referenced_filenames.add(os.path.basename(tex).lower())

    unref_disk = [f for f in disk_images if f.lower() not in referenced_filenames]
    print(f"\nUnreferenced images on disk in assets/images/weapons: {len(unref_disk)}")

    # Can we match unreferenced images to fallback items?
    fallback_keys = bow_item_fallback + bow_weapon_fallback
    matched_to_disk = []
    unmatched_keys = []

    for k in fallback_keys:
        data = weapons[k]
        fname = data.get('friendlyName', '')
        nk = norm(k)
        nf = norm(fname)

        match = None
        # Exact norm match
        if nk in disk_norm:
            match = disk_norm[nk]
        elif nf in disk_norm:
            match = disk_norm[nf]
        else:
            # Try specific heuristics
            # e.g., ARROW_FLIGHT -> flightarrow.png
            # DOUBLE_CROSSBOW -> double_crossbow.png / CROSSBOW_DOUBLED -> crossbow_doubled.png
            for unref in unref_disk:
                nu = norm(os.path.splitext(unref)[0])
                if nu == nk or nu == nf:
                    match = unref
                    break
                # subwords
                if nk.startswith(nu) or nu.startswith(nk):
                    pass

        if match:
            matched_to_disk.append((k, fname, match))
        else:
            unmatched_keys.append((k, fname))

    print(f"\nFallback weapons that can immediately match an existing disk image: {len(matched_to_disk)}")
    for k, fname, img in matched_to_disk:
        print(f"  {k} ('{fname}') -> images/weapons/{img}")

    print(f"\nFallback weapons that have NO matching image on disk: {len(unmatched_keys)}")
    
    # Let's inspect unreferenced images remaining
    matched_img_set = {m[2].lower() for m in matched_to_disk}
    remaining_unref = [f for f in unref_disk if f.lower() not in matched_img_set]
    print(f"Remaining unreferenced disk images after matches: {len(remaining_unref)}")
    for f in sorted(remaining_unref):
        print(f"  {f}")

    # Breakdown by weapon class/type of truly missing items
    print("\n--- BREAKDOWN OF MISSING WEAPON ASSETS ---")
    categories = {}
    for k, fname in unmatched_keys:
        # derive category from key prefix
        prefix = k.split('_')[0]
        categories[prefix] = categories.get(prefix, 0) + 1

    for cat, count in sorted(categories.items(), key=lambda x: x[1], reverse=True):
        print(f"  {cat}: {count}")

if __name__ == '__main__':
    main()
