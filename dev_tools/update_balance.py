import json
import os
import re

ARMOR_PATH = r"c:\Users\denni\Desktop\Tarmin2\assets\data\armor.json"
WEAPONS_PATH = r"c:\Users\denni\Desktop\Tarmin2\assets\data\weapons.json"

# Lore-accurate AC Bonuses (AC - 10)
ARMOR_VALUES = {
    # Light Armor
    "PADDED_ARMOR": 1,
    "LEATHER_ARMOR": 1,
    "STUDDED_LEATHER": 2,
    
    # Medium Armor
    "HIDE_ARMOR": 2,
    "CHAIN_SHIRT": 3,
    "SCALE_ARMOR": 4, # Scale Mail
    "BREASTPLATE": 4,
    "HALF_PLATE": 5,
    "CHAIN_LAMELLAR": 4, # Similar to Chain Shirt/Scale
    
    # Heavy Armor
    "RING_MAIL": 4,
    "CHAIN_MAIL": 6,
    "SPLINT_MAIL": 7,
    "PLATE_MAIL": 8, # Plate
    "FULL_PLATE": 8,
    "FIELD_PLATE": 8,
    "BRONZE_PLATE": 8,
    
    # Shields
    "SHIELD_SMALL": 2, # Buckler
    "SHIELD_MEDIUM": 2, 
    "SHIELD_LARGE": 2,
    "BUCKLER": 2,
    "SHIELD_BODY": 2,
    
    # Helmets (Not strictly 5e, giving small bonus)
    "HELMET": 1,
    "GREAT_HELM": 1,
    "BASINET": 1,
    "CLOSED_FACE": 1,
    "OPEN_FACED": 1,
    "CAP": 0,
    
    # Others
    "BANDED_MAIL": 7, # Splint equivalent
    "BRIGANDINE": 4, # Studded Leather / Scale equivalent
}

# Lore-accurate Weapon Dice
WEAPON_DICE = {
    "DAGGER": "1d4",
    "KNIFE": "1d4",
    "SLING": "1d4",
    "DART": "1d4",
    "WHIP": "1d4",
    "SICKLE": "1d4",
    "CLUB": "1d4",
    "LIGHT_HAMMER": "1d4",
    
    "SHORTBOW": "1d6",
    "HANDAXE": "1d6",
    "SPEAR": "1d6",
    "MACE": "1d6",
    "QUARTERSTAFF": "1d6",
    "SCIMITAR": "1d6",
    "SHORTSWORD": "1d6",
    "TRIDENT": "1d6",
    "HAND_CROSSBOW": "1d6",
    
    "LONGBOW": "1d8",
    "BATTLEAXE": "1d8",
    "WARHAMMER": "1d8",
    "LONGSWORD": "1d8",
    "RAPIER": "1d8",
    "MORNING_STAR": "1d8",
    "FLAIL": "1d8",
    "WAR_PICK": "1d8",
    "GREAT_CLUB": "1d8",
    "LIGHT_CROSSBOW": "1d8",
    
    "GLAIVE": "1d10",
    "HALBERD": "1d10",
    "PIKE": "1d10",
    "HEAVY_CROSSBOW": "1d10",
    
    "GREATAXE": "1d12",
    "LANCE": "1d12",
    
    "GREATSWORD": "2d6",
    "MAUL": "2d6",
    
    # Custom/Exotic mappings based on name similarity
    "ALHULAK": "1d6",
    "ARQUEBUS": "2d8", # Firearm
    "BLUNDERBUS": "2d8",
    "MUSKET": "1d12",
    "PISTOL": "1d10",
    "KATANA": "1d10", # Versatile Longsword-ish
    "WAKIZASHI": "1d6",
    "CLAYMORE": "2d6",
}

def update_armor():
    print(f"Updating {ARMOR_PATH}...")
    with open(ARMOR_PATH, 'r') as f:
        data = json.load(f)
        
    updated_count = 0
    for key, item in data.items():
        # Heuristic Matching
        new_ac = 0
        
        # 1. Direct key match
        if key in ARMOR_VALUES:
            new_ac = ARMOR_VALUES[key]
        else:
            # 2. Heuristic based on name parts
            name_upper = key.upper()
            if "PLATE" in name_upper: new_ac = 8
            elif "SPLINT" in name_upper: new_ac = 7
            elif "CHAIN" in name_upper and "MAIL" in name_upper: new_ac = 6
            elif "CHAIN" in name_upper: new_ac = 5 # Hauberk etc
            elif "SCALE" in name_upper: new_ac = 4
            elif "STUDDED" in name_upper: new_ac = 2
            elif "LEATHER" in name_upper: new_ac = 1
            elif "PADDED" in name_upper: new_ac = 1
            elif "SHIELD" in name_upper or "BUCKLER" in name_upper: new_ac = 2
            elif "HELM" in name_upper or "FACE" in name_upper or "COIF" in name_upper: new_ac = 1
            else:
                pass 
        
        if new_ac > 0:
            item["armorClassBonus"] = new_ac
            # Update description if it contains "Defense: X"
            if "description" in item:
                item["description"] = re.sub(r"Defense: \d+", f"Defense: {new_ac}", item["description"])
            updated_count += 1
            
    with open(ARMOR_PATH, 'w') as f:
        json.dump(data, f, indent=2)
    print(f"Updated {updated_count} armor entries.")

def update_weapons():
    print(f"Updating {WEAPONS_PATH}...")
    with open(WEAPONS_PATH, 'r') as f:
        data = json.load(f)
        
    updated_count = 0
    for key, item in data.items():
        current_val = item.get("damageDice")
        
        new_dice = None
        
        # 1. Check strict/heuristic mapping first (Priority 1)
        for wep_key, wep_dice in WEAPON_DICE.items():
            if wep_key in key: 
               new_dice = wep_dice
               break
        
        # 2. Heuristic mapping Strategy (Priority 2)
        if not new_dice:
            name_upper = key.upper()
            if "SWORD_TWO_HANDED" in name_upper or "GREATSWORD" in name_upper or "CLAYMORE" in name_upper:
                new_dice = "2d6"
            elif "GREATAXE" in name_upper:
                new_dice = "1d12"
            elif "AXE_TWO_HANDED" in name_upper:
                new_dice = "1d12"
            elif "HALBERD" in name_upper or "GLAIVE" in name_upper or "PIKE" in name_upper:
                new_dice = "1d10"
            elif "SWORD_LONG" in name_upper or "LONGSWORD" in name_upper or "KATANA" in name_upper:
                new_dice = "1d8"
            elif "BATTLEAXE" in name_upper or "WARHAMMER" in name_upper or "MORNING_STAR" in name_upper:
                new_dice = "1d8"
            elif "SWORD_SHORT" in name_upper or "SHORTSWORD" in name_upper or "SCIMITAR" in name_upper or "SABRE" in name_upper:
                new_dice = "1d6"
            elif "MACE" in name_upper or "SPEAR" in name_upper or "HANDAXE" in name_upper:
                new_dice = "1d6"
            elif "DAGGER" in name_upper or "KNIFE" in name_upper or "DART" in name_upper:
                new_dice = "1d4"
            elif "BOW_LONG" in name_upper:
                new_dice = "1d8"
            elif "BOW_SHORT" in name_upper or "CROSSBOW" in name_upper:
                if "HEAVY" in name_upper: new_dice = "1d10"
                else: new_dice = "1d6"
            elif "MUSKET" in name_upper:
                new_dice = "1d12"
            elif "PISTOL" in name_upper:
                new_dice = "1d10"

        # 3. Decision Logic
        should_update = False
        
        if new_dice:
             # We have a specific map for this item. Apply it.
             # Note: This will Standardize everything to 5e values, even if it was "1d7" before.
             # This is desired behavior for "Balancing".
             if item.get("damageDice") != new_dice:
                 item["damageDice"] = new_dice
                 should_update = True
        elif isinstance(current_val, int):
             # No specific map, but it is an INT. Convert it.
             if current_val >= 10: new_dice = "1d10"
             elif current_val >= 8: new_dice = "1d8"
             elif current_val >= 6: new_dice = "1d6"
             elif current_val >= 4: new_dice = "1d4"
             else: new_dice = "1d4"
             item["damageDice"] = new_dice
             should_update = True
        elif isinstance(current_val, str) and not re.match(r"\d+d\d+([+-]\d+)?", current_val):
             # It is a string but INVALID format (e.g. "5"). Treat as int?
             # Or just garbage. Default to 1d6.
             item["damageDice"] = "1d6"
             should_update = True
        elif not current_val:
             # Missing or null
             item["damageDice"] = "1d6"
             should_update = True
        
        if should_update:
            updated_count += 1

    with open(WEAPONS_PATH, 'w') as f:
        json.dump(data, f, indent=2)
    print(f"Updated {updated_count} weapon entries.")

if __name__ == "__main__":
    try:
        # update_armor() # Skip armor for this run
        update_weapons()
        print("Done.")
    except Exception as e:
        print(f"Error: {e}")
