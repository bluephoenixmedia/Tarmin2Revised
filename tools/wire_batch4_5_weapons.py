"""
wire_batch4_5_weapons.py

Rewires Batch 3 (remaining), Batch 4 (Daggers & Knives), and Batch 5 (Axes & Polearms)
to their newly rendered, bespoke transparent textures in assets/data/weapons.json.
"""

import json
import os

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    json_path = os.path.join(repo_root, "assets", "data", "weapons.json")

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    rewire_map = {
        # Batch 3 Flails & Clubs
        "FLAIL_FOOTMAN_S": "images/weapons/flail_footman.png",
        "FLAIL_CHAIN": "images/weapons/flail_footman.png",
        "FLAIL_GRAIN": "images/weapons/flail_footman.png",
        "FLAIL_HORSEMAN_S": "images/weapons/flail_horseman.png",
        "FLAIL_BLADEBACK": "images/weapons/flail_horseman.png",
        "MORNING_STAR_DOUBLE_BALL": "images/weapons/morning_star_double_ball.png",
        "CLUB_GREAT": "images/weapons/club_great.png",
        "CLUB_DWARVEN_WAR": "images/weapons/club_great.png",

        # Batch 4 Daggers & Knives
        "DAGGER_BONE": "images/weapons/dagger_bone.png",
        "DAGGER_GIANT_KIN": "images/weapons/dagger_bone.png",
        "DAGGER_STONE": "images/weapons/dagger_stone.png",
        "KNIFE_THROWING": "images/weapons/knife_throwing.png",
        "KNIFE_HARNESS": "images/weapons/knife_throwing.png",
        "KNIFE_WIDOW_S": "images/weapons/knife_throwing.png",
        "KNIFE_BONE": "images/weapons/knife_bone.png",
        "KNIFE_STONE": "images/weapons/knife_stone.png",

        # Batch 5 Axes & Polearms
        "AXE_TWO_HANDED_BATTLE": "images/weapons/axe_two_handed_battle.png",
        "HATCHET": "images/weapons/hatchet.png",
        "HATCHET_HAWK": "images/weapons/hatchet.png",
        "AXE_HAND_THROWING": "images/weapons/hatchet.png",
        "TRIDENT_ONE_HANDED": "images/weapons/trident.png",
        "TRIDENT_TWO_HANDED": "images/weapons/trident.png",
        "POLEARM_GUISARME": "images/weapons/polearm_guisarme.png",
        "POLEARM_BILL_GUISARME": "images/weapons/polearm_guisarme.png",
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

    print(f"\nSuccessfully rewired {updated_count} weapons to bespoke Batch 3-5 textures.")

if __name__ == "__main__":
    main()
