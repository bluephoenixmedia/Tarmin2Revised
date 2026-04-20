import json
import re
import os

def load_json(path):
    with open(path, 'r') as f:
        return json.load(f)

def save_json(path, data):
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)

def get_valid_item_types(item_java_path):
    with open(item_java_path, 'r') as f:
        content = f.read()
    match = re.search(r'public enum ItemType\s*\{(.*?)\}', content, re.DOTALL)
    if not match:
        print("Could not find ItemType enum")
        return set()
    enum_content = match.group(1)
    enum_content = re.sub(r'//.*', '', enum_content)
    tokens = re.findall(r'\b[A-Z0-9_]+\b', enum_content)
    return set(tokens)

def main():
    base_dir = r"c:\Users\denni\Desktop\Tarmin2"
    weapons_path = os.path.join(base_dir, "assets", "data", "weapons.json")
    spawntables_path = os.path.join(base_dir, "assets", "data", "spawntables.json")
    item_java_path = os.path.join(base_dir, "core", "src", "main", "java", "com", "bpm", "minotaur", "gamedata", "item", "Item.java")

    print(f"Loading weapons from {weapons_path}")
    weapons_data = load_json(weapons_path)
    
    print(f"Loading ItemType enum from {item_java_path}")
    valid_types = get_valid_item_types(item_java_path)

    print(f"Loading spawntables from {spawntables_path}")
    spawntables_data = load_json(spawntables_path)
    
    # 1. Update Essentials Weights
    essential_updates = {
        "FOOD": 30,
        "FLOUR_SACK": 20,
        "POTION_BLUE": 10,
        "POTION_PINK": 10,
        "POTION_GREEN": 8
    }
    
    existing_map = {} # Map type -> entry objects
    if "itemSpawnTable" in spawntables_data:
        for entry in spawntables_data["itemSpawnTable"]:
            existing_map[entry["type"]] = entry
            
            # Apply essential updates immediately
            if entry["type"] in essential_updates:
                print(f"Boosting {entry['type']} weight to {essential_updates[entry['type']]}")
                entry["weight"] = essential_updates[entry['type']]

    # 2. Process Weapons
    new_entries = []
    updated_count = 0
    added_count = 0
    
    for weapon_key, weapon_props in weapons_data.items():
        if weapon_key not in valid_types:
            continue
            
        damage = weapon_props.get("warDamage", 3)
        # Calculate min level based on damage
        # Curve: Dmg 3 -> Lvl 1, Dmg 10 -> Lvl 15, Dmg 20 -> Lvl 35+
        min_level = max(1, int((damage - 2) * 2))
        if min_level > 50:
            min_level = 50
            
        weight = 1 # Low weight to prevent dilution
        
        if weapon_key in existing_map:
            # Update existing spawn entry
            entry = existing_map[weapon_key]
            entry["minLevel"] = min_level
            entry["maxLevel"] = 50 # Or 99? 50 is fine for now
            entry["weight"] = weight
            updated_count += 1
        else:
            # Create new entry
            entry = {
                "type": weapon_key,
                "minLevel": min_level,
                "maxLevel": 50,
                "weight": weight
            }
            new_entries.append(entry)
            added_count += 1

    if "itemSpawnTable" not in spawntables_data:
        spawntables_data["itemSpawnTable"] = []
        
    spawntables_data["itemSpawnTable"].extend(new_entries)
    
    print(f"Updated {updated_count} existing weapon entries.")
    print(f"Added {added_count} new weapon entries.")
    print(f"Boosted survival item weights.")
    
    save_json(spawntables_path, spawntables_data)
    print("Done!")

if __name__ == "__main__":
    main()
