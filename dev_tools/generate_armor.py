import json
import re

def parse_price(price_str):
    if not price_str: return 0
    clean = price_str.replace('gp', '').replace(',', '')
    try:
        return int(clean)
    except:
        return 0

def get_defense(price, is_shield=False, is_helm=False):
    # Heuristic for defense based on price
    if price == 0: return 1
    
    if is_shield:
        if price <= 1: return 1 # Buckler
        if price <= 3: return 2 # Small
        if price <= 7: return 3 # Medium
        return 4 # Large/Body
        
    if is_helm:
        if price <= 5: return 1
        if price <= 15: return 2
        if price <= 30: return 3
        return 4 # Great Helm
        
    # Body Armor
    # Padded(4) -> 1
    # Leather(5) -> 2
    # Studded(20) -> 3
    # Chain(75) -> 4
    # Scale(120) -> 5
    # Plate(600) -> 7
    # Full Plate(4000) -> 10
    
    if price <= 4: return 1
    if price <= 10: return 2
    if price <= 30: return 3
    if price <= 80: return 4
    if price <= 150: return 5
    if price <= 300: return 6
    if price <= 800: return 7
    if price <= 1500: return 8
    if price <= 2500: return 9
    return 10

SPRITE_BODY = [
    "........................",
    "........................",
    "........................",
    "........................",
    "..######........######..",
    "..####################..",
    "..####################..",
    "..####################..",
    "..####################..",
    ".....##############.....",
    ".....##############.....",
    "...##################...",
    "...##################...",
    "......############......",
    "........########........",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................"
]

SPRITE_HELM = [
    "........................",
    "........................",
    "........................",
    "........................",
    "..........###...........",
    ".......#########........",
    "....###############.....",
    "....###############.....",
    "....###############.....",
    "..#########.#########...",
    "..#########.#########...",
    "..######.......######...",
    ".....###.......###......",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................"
]

SPRITE_SHIELD = [
    "........................",
    "........................",
    "........................",
    "......########..........",
    ".....##########.........",
    "....############........",
    "....############........",
    "....############........",
    "....############........",
    "....############........",
    "....############........",
    "....############........",
    ".....##########.........",
    "......########..........",
    ".......######...........",
    "........####............",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................",
    "........................"
]

def main():
    armor_data = {}
    enum_list = []
    
    current_category = "BODY" # Default
    
    skip_barding = False
    with open('dev_tools/armor_list.txt', 'r') as f:
        lines = f.readlines()
        
    for raw_line in lines:
        if not raw_line.strip(): continue
        
        # Calculate Indent
        indent = len(raw_line) - len(raw_line.lstrip())
        line = raw_line.strip()

        # HEADERS (Unindented lines that don't have price?)
        # "Helmet"
        # "Shield"
        # "Barding"
        
        is_header = "gp" not in line and ("Helmet" in line or "Shield" in line or "Barding" in line)
        
        if is_header:
            if "Barding" in line:
                skip_barding = True
                current_category = "BARDING"
            elif "Helmet" in line:
                skip_barding = False
                current_category = "HELMET"
            elif "Shield" in line:
                skip_barding = False
                current_category = "SHIELD"
            continue
            
        # If unindented and has price -> BODY ARMOR (reset category)
        if indent == 0 and "gp" in line:
             current_category = "BODY"
             skip_barding = False
             
        if skip_barding:
            continue
            
        # Parse item
        match = re.match(r"^(.*?)\s+([\d,]+)gp\s+(\d+)", line)
        if not match:
             # Try parsing without weight?
             match = re.match(r"^(.*?)\s+([\d,]+)gp", line)
        
        item_name = ""
        price = 0
        
        if match:
            item_name = match.group(1).strip()
            price = parse_price(match.group(2))
        else:
             continue

        # ID Generation
        item_id = item_name.upper().replace(' ', '_').replace('-', '_').replace('/', '_')
        
        # Fix specific IDs
        if current_category == "SHIELD" and "SHIELD" not in item_id and "BUCKLER" not in item_id:
            item_id = "SHIELD_" + item_id
            
        # Fix CHAIN conflict
        if item_id == "CHAIN":
            item_id = "CHAIN_ARMOR"
            
        # Deduplication
        if item_id in armor_data:
            print(f"Skipping duplicate {item_id}")
            continue
            
        enum_list.append(item_id)
        
        # Determine properties
        is_helm = current_category == "HELMET" or "HELM" in item_id or "CAP" in item_id or "COIF" in item_id or "BASINET" in item_id or "FACE" in item_id
        is_shield = current_category == "SHIELD" or "SHIELD" in item_id or "BUCKLER" in item_id
        is_armor = not is_helm and not is_shield

        
        defense = get_defense(price, is_shield, is_helm)
        
        sprite = SPRITE_BODY
        if is_helm: sprite = SPRITE_HELM
        elif is_shield: sprite = SPRITE_SHIELD
        
        texture = "images/items/helmet.png"
        if is_shield: texture = "images/items/shield.png" # Assuming it exists, or fallback
        
        entry = {
            "friendlyName": item_name,
            "description": f"Defense: {defense}",
            "texturePath": texture,
            "spriteData": sprite,
            "baseValue": price,
            "warDamage": 0,
            "spiritDamage": 0,
            "armorDefense": defense,
            "isWeapon": False,
            "isRanged": False,
            "isArmor": True, # For game logic
            "isShield": is_shield, # Custom field?
            "isHelmet": is_helm, # Custom field?
            "isPotion": False,
            "isFood": False,
            "isTreasure": False,
            "isKey": False,
            "isUsable": False,
            "isContainer": False,
            "isRing": False,
            "scale": {"x": 1.0, "y": 1.0},
            "variants": [
                {"color": "TAN", "minLevel": max(1, defense * 2), "maxLevel": 99, "weight": 5}
            ]
        }
        
        armor_data[item_id] = entry

    # Write JSON
    with open('assets/data/armor.json', 'w') as f:
        json.dump(armor_data, f, indent=2)
        
    # Write Enums
    with open('dev_tools/armor_enums.txt', 'w') as f:
        f.write(",\n".join(enum_list) + ",\n")
        
    print(f"Generated {len(armor_data)} armor entries.")

if __name__ == "__main__":
    main()
