"""
wire_batch9_10_weapons.py

Rewires Batch 9 (Exotic Blades & Fist Weapons) and Batch 10 (Specialty Polearms)
in assets/data/weapons.json to point to their newly rendered bespoke textures.
"""

import json
import os

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    json_path = os.path.join(repo_root, "assets", "data", "weapons.json")

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    rewire_map = {
        # Batch 9: Exotic Blades & Fist Weapons
        "DAGGER_CLIMBING": "images/weapons/dagger_climbing.png",
        "SWORDLET": "images/weapons/swordlet.png",
        "GLADIATOR_S_FRIEND_FOOTMAN_S": "images/weapons/gladiators_friend.png",
        "GLADIATOR_S_FRIEND_HORSEMAN_S": "images/weapons/gladiators_friend.png",
        "TORTOISE_BLADES": "images/weapons/tortoise_blades.png",
        "TALID": "images/weapons/talid.png",
        "WRIST_RAZOR": "images/weapons/wrist_razor.png",

        # Batch 10: Specialty Polearms
        "POLEARM_MILITARY_FORK4": "images/weapons/polearm_military_fork.png",
        "POLEARM_RANSEUR3": "images/weapons/polearm_ranseur.png",
        "POLEARM_PARTISAN3": "images/weapons/polearm_partisan.png",
    }

    updated_count = 0
    for key, new_tex in rewire_map.items():
        if key in data:
            old_tex = data[key].get("texturePath", "")
            data[key]["texturePath"] = new_tex
            print(f"Updated {key:30s} ({data[key].get('friendlyName', ''):30s}): {old_tex} -> {new_tex}")
            updated_count += 1
        else:
            print(f"WARNING: {key} not found in weapons.json")

    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent="\t")

    print(f"\nSuccessfully rewired {updated_count} weapons to bespoke Batch 9-10 textures.")

if __name__ == "__main__":
    main()
