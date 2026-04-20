
import json
import os

debris_types = [
    "STICK", "SMALL_ROCK", "LEAVES", "MOSS_CLUMP", "PUDDLE_WATER", 
    "DEAD_PLANT_MATTER", "MUD", "ROTTEN_ROPE", "BROKEN_WALL", "LOOSE_BRICK",
    "CERAMIC_SHARD", "CHARRED_WOOD", "FLINT_SHARD", "VINES", "RAT_SKULL",
    "BAT_GUANO", "LARGE_BOULDER", "BROKEN_COLUMN", "RUSTY_WEAPON", 
    "RUSTY_ARMOR", "METAL_SCRAP", "DIRTY_CLOTH", "HOLLOW_LOG", 
    "TWISTED_ROOT", "FUNGAL_SPORE", "SLIME_RESIDUE", "SPIDER_SILK",
    "BAT_WING", "BROKEN_GLASS", "RUSTED_CHAIN", "BROKEN_HILT", 
    "BENT_NAIL", "PARCHMENT_SCRAP", "STAGNANT_POOL", "STRANGE_METAL", 
    "ANCIENT_FOSSIL"
]

items_path = "assets/data/items.json"
if not os.path.exists(items_path):
    print(f"Error: {items_path} not found")
    exit(1)

try:
    with open(items_path, 'r') as f:
        data = json.load(f)
except Exception as e:
    print(f"Error reading JSON: {e}")
    exit(1)

dummy_sprite = ["." * 24] * 24

for dt in debris_types:
    if dt in data:
        print(f"Skipping existing: {dt}")
        # Ensure texturePath exists if it's missing (optional, but good practice)
        if "texturePath" not in data[dt]:
             data[dt]["texturePath"] = "images/items/" + dt.lower() + ".png"
             print(f"Added missing texturePath to {dt}")
        continue

    name = dt.replace("_", " ").title()
    filename = dt.lower() + ".png"
    
    entry = {
        "friendlyName": name,
        "texturePath": "images/items/" + filename,
        "spriteData": dummy_sprite,
        "baseValue": 0,
        "isImpassable": False,
        "isUsable": False,
        "isContainer": False,
        "isTreasure": False, 
        "isWeapon": False,
        "isRanged": False,
        "isArmor": False,
        "isPotion": False,
        "isFood": False,
        "isKey": False,
        "isRing": False,
        "scale": {"x": 1.0, "y": 1.0},
        "variants": [{"color": "TAN", "minLevel": 1, "maxLevel": 99, "weight": 1}]
    }
    
    # Specific tweaks
    if dt == "LARGE_BOULDER" or dt == "BROKEN_COLUMN":
        entry["isImpassable"] = True
    
    data[dt] = entry
    print(f"Added: {dt}")

try:
    with open(items_path, 'w') as f:
        json.dump(data, f, indent=2)
    print("Success")
except Exception as e:
    print(f"Error writing JSON: {e}")
    exit(1)
