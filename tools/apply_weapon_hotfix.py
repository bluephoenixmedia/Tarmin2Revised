import json
import os
from PIL import Image

def hotfix():
    # 1. Update weapons.json
    json_path = 'assets/data/weapons.json'
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # 9 ready weapons
    ready_mappings = {
        "SWORD_FLAMBERGE": "images/weapons/sword_flamberge.png",
        "SWORD_GIANT_KIN_TWO_HANDED": "images/weapons/sword_giant_kin_two_handed.png",
        "SWORD_GREAT_SCIMITAR": "images/weapons/sword_great_scimitar.png",
        "SWORD_HOOK": "images/weapons/sword_hook.png",
        "SWORD_KATANA_ONE_HANDED": "images/weapons/sword_katana_one_handed.png",
        "SWORD_KATANA_TWO_HANDED": "images/weapons/sword_katana_two_handed.png",
        "SWORD_KHANDAR_ONE_HANDED": "images/weapons/sword_khandar_one_handed.png",
        "SWORD_KHANDAR_TWO_HANDED": "images/weapons/sword_khandar_two_handed.png",
        "ARROW_FLIGHT": "images/weapons/flightarrow.png"
    }

    rewired_count = 0
    for k, p in ready_mappings.items():
        if k in data:
            data[k]['texturePath'] = p
            rewired_count += 1
            print(f"Rewired {k} -> {p}")

    # Fallback items pointing to images/items/bow.png -> point to images/weapons/bow.png
    redirected_count = 0
    for k, v in data.items():
        if v.get('texturePath') == 'images/items/bow.png':
            v['texturePath'] = 'images/weapons/bow.png'
            redirected_count += 1

    print(f"Redirected {redirected_count} fallback weapons from items/bow.png to weapons/bow.png (RGBA)")

    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent='\t')

    # 2. Convert assets/images/items/bow.png to RGBA transparent
    item_bow_path = 'assets/images/items/bow.png'
    if os.path.exists(item_bow_path):
        img = Image.open(item_bow_path).convert('RGBA')
        # Background is grey around (191, 191, 189)
        # Convert any pixels close to the background color to transparent
        datas = img.getdata()
        new_data = []
        bg_sample = img.getpixel((5, 5))
        for item in datas:
            # Check Euclidean or manhattan distance to bg_sample
            diff = abs(item[0] - bg_sample[0]) + abs(item[1] - bg_sample[1]) + abs(item[2] - bg_sample[2])
            if diff < 35: # within grey threshold
                new_data.append((0, 0, 0, 0))
            else:
                new_data.append(item)
        img.putdata(new_data)
        img.save(item_bow_path, 'PNG')
        print(f"Patched {item_bow_path} to RGBA transparent.")

if __name__ == '__main__':
    hotfix()
