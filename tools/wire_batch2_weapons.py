"""
wire_batch2_weapons.py

Rewires Batch 2 Bows, Crossbows, and Ammunition to their newly rendered,
bespoke transparent textures in assets/data/weapons.json.
"""

import json
import os

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    json_path = os.path.join(repo_root, "assets", "data", "weapons.json")

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    rewire_map = {
        # Bows
        "BOW_SHORT": "images/weapons/bow_short.png",
        "BOW_FOLDING_SHORT": "images/weapons/bow_short.png",
        "BOW_LONG": "images/weapons/bow_long.png",
        "BOW_GIANT_KIN_LONG": "images/weapons/bow_long.png",
        "BOW_COMPOSITE_SHORT": "images/weapons/bow_composite_short.png",

        # Crossbows
        "CROSSBOW": "images/weapons/crossbow_light.png",
        "CROSSBOW_LIGHT": "images/weapons/crossbow_light.png",
        "CROSSBOW_HEAVY": "images/weapons/crossbow_heavy.png",
        "CROSSBOW_DISK": "images/weapons/crossbow_heavy.png",
        "DISK_CROSSBOW": "images/weapons/crossbow_heavy.png",
        "CROSSBOW_HAND": "images/weapons/crossbow_hand.png",

        # Ammunition - Arrows
        "ARROW_WAR": "images/weapons/arrow_war.png",
        "ARROW_MAIL_PIERCER": "images/weapons/arrow_war.png",
        "ARROW_GIANT_KIN": "images/weapons/arrow_war.png",
        "ARROW_SHEAF": "images/weapons/arrow_sheaf.png",
        "ARROW_KENYAN": "images/weapons/arrow_sheaf.png",
        "ARROW_FORGET": "images/weapons/arrow_sheaf.png",
        "ARROW_SLEEP": "images/weapons/arrow_sheaf.png",

        # Ammunition - Quarrels
        "QUARREL_LIGHT": "images/weapons/quarrel_light.png",
        "QUARREL_HEAVY": "images/weapons/quarrel_heavy.png",
    }

    updated_count = 0
    for key, new_tex in rewire_map.items():
        if key in data:
            old_tex = data[key].get("texturePath", "")
            data[key]["texturePath"] = new_tex
            print(f"Updated {key:22s} ({data[key].get('friendlyName', ''):25s}): {old_tex} -> {new_tex}")
            updated_count += 1
        else:
            print(f"WARNING: {key} not found in weapons.json")

    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent="\t")

    print(f"\nSuccessfully rewired {updated_count} weapons to bespoke Batch 2 textures.")

if __name__ == "__main__":
    main()
