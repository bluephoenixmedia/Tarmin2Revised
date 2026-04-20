import json
import os

def update_spawntables():
    # Load Armor Data
    with open('assets/data/armor.json', 'r') as f:
        armor_data = json.load(f)
        
    # Load Spawn Tables
    with open('assets/data/spawntables.json', 'r') as f:
        spawn_data = json.load(f)
        
    item_table = spawn_data.get("itemSpawnTable", [])
    
    # Track existing types to avoid duplicates
    existing_types = {entry["type"] for entry in item_table}
    
    new_entries = []
    
    for key, data in armor_data.items():
        if key in existing_types:
            continue
            
        defense = data.get("armorDefense", 0)
        is_shield = data.get("isShield", False)
        is_helm = data.get("isHelmet", False)
        
        # Determine Min Level
        # Defense 1 -> Lv 1
        # Defense 10 -> Lv 20?
        min_level = max(1, defense * 3 - 2)
        if is_shield or is_helm:
            min_level = max(1, defense * 4 - 3)
            
        # Determine Weight
        # Common (Low tier) -> High weight
        # Rare (High tier) -> Low weight
        weight = 5
        if defense > 5: weight = 3
        if defense > 8: weight = 1
        
        if min_level > 50: min_level = 50 # Cap start level
        
        # Max Level (usually open ended, or phased out?)
        # Let's say max level is 99
        
        entry = {
            "type": key,
            "minLevel": min_level,
            "maxLevel": 99,
            "weight": weight
        }
        
        new_entries.append(entry)
        
    print(f"Adding {len(new_entries)} new armor entries to spawn table.")
    
    item_table.extend(new_entries)
    spawn_data["itemSpawnTable"] = item_table
    
    # Save
    with open('assets/data/spawntables.json', 'w') as f:
        json.dump(spawn_data, f, indent=2)

if __name__ == "__main__":
    update_spawntables()
