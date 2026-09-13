"""
wire_batch3_weapons.py

Rewires Batch 3 Maces, Flails, Clubs, and Hammers to their newly rendered,
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
        "WARHAMMER": "images/weapons/warhammer.png",
        "HAMMER": "images/weapons/warhammer.png",
        "MACE_FOOTMAN_S": "images/weapons/mace_footman.png",
        "MACE_FOOTMAN_S_WHISTLING": "images/weapons/mace_footman.png",
        "MACE_GIANT_KIN": "images/weapons/mace_footman.png",
        "MACE_BLADEBACK": "images/weapons/mace_footman.png",
        "MACE_HORSEMAN_S": "images/weapons/mace_horseman.png",
        "MACE_HORSEMAN_S_WHISTLING": "images/weapons/mace_horseman.png"
    }

    updated_count = 0
    for key, new_tex in rewire_map.items():
        if key in data:
            old_tex = data[key].get("texturePath", "")
            data[key]["texturePath"] = new_tex
            print(f"Updated {key:26s} ({data[key].get('friendlyName', ''):25s}): {old_tex} -> {new_tex}")
            updated_count += 1
        else:
            print(f"WARNING: {key} not found in weapons.json")

    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent="\t")

    print(f"\nSuccessfully rewired {updated_count} weapons to bespoke Batch 3 textures.")

if __name__ == "__main__":
    main()
