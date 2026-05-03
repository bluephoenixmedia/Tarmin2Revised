import json
import os

WEAPONS_FILE = 'assets/data/weapons.json'

def update_modifiers():
    if not os.path.exists(WEAPONS_FILE):
        print(f"Error: {WEAPONS_FILE} not found.")
        return

    with open(WEAPONS_FILE, 'r') as f:
        data = json.load(f)

    modified_count = 0

    for key, item in data.items():
        name = (item.get('friendlyName') or '').lower()
        desc = (item.get('description') or '').lower()
        
        # Reset flags to ensure clean state (or keep existing if we want additive? User didn't specify, but implies we are introducing them)
        # Let's initialize them to False if not present
        if 'isBleed' not in item: item['isBleed'] = False
        if 'isStun' not in item: item['isStun'] = False
        if 'isBludgeon' not in item: item['isBludgeon'] = False
        if 'isPierce' not in item: item['isPierce'] = False
        if 'isDisease' not in item: item['isDisease'] = False
        if 'isPoison' not in item: item['isPoison'] = False

        # keywords for Bleed
        if any(x in name for x in ['sword', 'axe', 'razor', 'saw', 'katana', 'blade', 'cutlass', 'glaive', 'halberd', 'scimitar', 'sickle', 'cleaver', 'knife', 'dagger']):
            item['isBleed'] = True
        
        # keywords for Stun
        if any(x in name for x in ['mace', 'hammer', 'club', 'flail', 'staff', 'sap', 'morning star', 'warhammer']):
            item['isStun'] = True

        # keywords for Bludgeon
        if any(x in name for x in ['mace', 'hammer', 'club', 'flail', 'staff', 'sap', 'morning star', 'warhammer', 'rock', 'sling']):
            item['isBludgeon'] = True

        # keywords for Pierce
        if any(x in name for x in ['dagger', 'spear', 'arrow', 'bolt', 'rapier', 'pick', 'needle', 'trident', 'javelin', 'lance', 'shuriken', 'dart']):
            item['isPierce'] = True

        # keywords for Disease
        if any(x in name for x in ['rusty', 'rotten', 'decay']):
            item['isDisease'] = True

        # keywords for Poison
        if any(x in name for x in ['poison', 'venom', 'toxic']):
            item['isPoison'] = True

        modified_count += 1

    with open(WEAPONS_FILE, 'w') as f:
        json.dump(data, f, indent=4)

    print(f"Updated {modified_count} weapons in {WEAPONS_FILE}.")

if __name__ == "__main__":
    update_modifiers()
