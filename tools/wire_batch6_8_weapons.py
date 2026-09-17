"""
wire_batch6_8_weapons.py

Rewires Batch 6 (Exotics & Thrown), Batch 7 (Polearms & Reach), and Batch 8 (Blades & Clubs)
weapons in assets/data/weapons.json to their newly rendered, bespoke transparent textures.
"""

import json
import os

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    json_path = os.path.join(repo_root, "assets", "data", "weapons.json")

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    rewire_map = {
        # Batch 6: Exotics & Thrown
        "WHIP": "images/weapons/whip.png",
        "WHIP_MASTER_S": "images/weapons/whip.png",
        "NET": "images/weapons/net.png",
        "CALTROP": "images/weapons/caltrop.png",
        "BLOWGUN": "images/weapons/blowgun.png",
        "BLOWGUN_BARBED_DART": "images/weapons/blowgun.png",
        "BLOWGUN_NEEDLE": "images/weapons/blowgun.png",
        "HARPOON_TWO_HANDED": "images/weapons/harpoon_two_handed.png",

        # Batch 7: Polearms & Reach
        "MANCATCHER2": "images/weapons/mancatcher.png",
        "LANCE_JOUSTING1": "images/weapons/lance_jousting.png",
        "LANCE_HEAVY_HORSE1": "images/weapons/lance_jousting.png",
        "LANCE_LIGHT_HORSE1": "images/weapons/lance_cavalry.png",
        "LANCE_MEDIUM_HORSE1": "images/weapons/lance_cavalry.png",
        "PICK_HORSEMAN_S": "images/weapons/pick_horseman.png",
        "PICK_ICE": "images/weapons/pick_horseman.png",
        "BELAYING_PIN": "images/weapons/belaying_pin.png",

        # Batch 8: Special Blades & Knots
        "LASSO": "images/weapons/lasso.png",
        "RAZOR": "images/weapons/razor.png",
    }

    updated_count = 0
    for key, new_tex in rewire_map.items():
        if key in data:
            old_tex = data[key].get("texturePath", "")
            data[key]["texturePath"] = new_tex
            print(f"Updated {key:25s} ({data[key].get('friendlyName', ''):25s}): {old_tex} -> {new_tex}")
            updated_count += 1
        else:
            print(f"WARNING: {key} not found in weapons.json")

    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent="\t")

    print(f"\nSuccessfully rewired {updated_count} weapons to bespoke Batch 6-8 textures.")

if __name__ == "__main__":
    main()
