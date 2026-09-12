"""
open5e_importer.py

Fetches and calibrates D&D 5e data from Open5e API:
1. Scrapes SRD 5.1 spells and tactical Kobold Press Deep Magic spells into assets/data/spells.json.
2. Calibrates weapons in assets/data/weapons.json to authentic 5e damage dice, properties, and damage types.
3. Calibrates armor in assets/data/armor.json to proportional component AC, category, maxDexBonus, and stealthDisadvantage.
4. Enriches assets/data/items.json with iconic 5e magic items linked to verified RGBA textures.
"""

import os
import re
import json
import urllib.request
import urllib.error

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(REPO_ROOT, "assets", "data")
USER_AGENT = "Tarmin2-Open5e-Importer/1.0"

def fetch_json(url):
    req = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return None

def parse_damage_dice(desc):
    # Find damage dice expressions like 1d4, 2d6, 8d6, etc.
    matches = re.findall(r'\b(\d+d\d+(?:\s*[+-]\s*\d+)?)\b', desc)
    if matches:
        return matches[0].replace(" ", "")
    return None

def parse_damage_type(desc):
    desc_lower = desc.lower()
    types = [
        ("fire", "FIRE"),
        ("cold", "COLD"),
        ("lightning", "LIGHTNING"),
        ("acid", "ACID"),
        ("poison", "POISON"),
        ("radiant", "RADIANT"),
        ("necrotic", "NECROTIC"),
        ("force", "FORCE"),
        ("psychic", "PSYCHIC"),
        ("thunder", "THUNDER"),
        ("slashing", "SLASHING"),
        ("piercing", "PIERCING"),
        ("bludgeoning", "BLUDGEONING")
    ]
    for pattern, name in types:
        if pattern + " damage" in desc_lower or pattern in desc_lower:
            return name
    return "FORCE"

def calculate_mp_cost(level):
    if level == 0:
        return 0
    elif level == 1:
        return 3
    elif level == 2:
        return 5
    elif level == 3:
        return 8
    elif level == 4:
        return 12
    elif level == 5:
        return 16
    elif level == 6:
        return 22
    elif level == 7:
        return 28
    elif level == 8:
        return 36
    else:
        return 45

def parse_range_tiles(range_str):
    r = range_str.lower()
    if "self" in r or "touch" in r:
        return 1
    if "sight" in r or "special" in r:
        return 15
    nums = re.findall(r'\d+', r)
    if nums:
        feet = int(nums[0])
        # 5 feet per tile
        return max(1, feet // 5)
    return 6

def parse_target_type(range_str, desc):
    r = range_str.lower()
    d = desc.lower()
    if "self" in r and not ("cone" in d or "radius" in d or "line" in d):
        return "SELF"
    if "touch" in r:
        return "TOUCH"
    if "cone" in d:
        return "CONE"
    if "radius" in d or "sphere" in d or "burst" in d or "cube" in d:
        return "BURST"
    if "line" in d or "beam" in d:
        return "BEAM"
    return "PROJECTILE"

def parse_status_effect(desc):
    d = desc.lower()
    if "paralyz" in d or "incapacitat" in d or "cannot move" in d:
        return "SLOWED"
    if "poisoned" in d:
        return "POISONED"
    if "blinded" in d:
        return "BLIND"
    if "fall asleep" in d or "unconscious" in d:
        return "SLEEP"
    if "invisible" in d:
        return "INVISIBLE"
    if "frightened" in d:
        return "CONFUSION"
    if "speed is doubled" in d or "haste" in d:
        return "SUPER_SPEED"
    if "shield" in d or "armor class" in d:
        return "HARDENED"
    return None

def normalize_rune_school(school):
    s = (school or "").upper()
    valid = ["EVOCATION", "ABJURATION", "NECROMANCY", "CONJURATION", "TRANSMUTATION", "DIVINATION", "ENCHANTMENT", "ILLUSION"]
    for v in valid:
        if v in s:
            return v
    return "EVOCATION"

def classify_visual_archetype(name, school, target_type, damage_type, desc):
    nl = (name or "").lower()
    dl = (desc or "").lower()
    sl = (school or "").lower()
    dt = (damage_type or "").upper()
    tt = (target_type or "").upper()

    # 1. Iconic Spells Bespoke Flourishes (The Fab Five + Iconic)
    if "fireball" in nl or "delayed blast fireball" in nl:
        return "EXPLOSIVE_BURST", "FIREBALL"
    if "magic missile" in nl:
        return "FORCE_MISSILE", "MAGIC_MISSILE"
    if "misty step" in nl or "dimension door" in nl or "teleport" in nl or "blink" in nl:
        return "SPATIAL_WARP", "MISTY_STEP"
    if "thunderwave" in nl:
        return "THUNDER_CONCUSSION", "THUNDERWAVE"
    if ("shield" in nl and "faith" not in nl) or "mage armor" in nl:
        return "ARCANE_WARD", "SHIELD"

    # 2. Damage-Type and Name/Keyword Driven
    if dt == "THUNDER" or "thunder" in nl or "shatter" in nl:
        return "THUNDER_CONCUSSION", None
    if dt == "PSYCHIC" or "mind" in nl or "psychic" in nl or "phantasm" in nl or "dissonant" in nl:
        return "PSYCHIC_SHOCK", None
    if dt == "FIRE":
        if tt in ("BURST", "CONE") or "burst" in dl or "explosion" in dl or "sphere" in dl:
            return "EXPLOSIVE_BURST", None
        return "FLAME_BOLT", None
    if dt == "COLD" or "frost" in nl or "ice" in nl or "cold" in nl or "blizzard" in nl or "ray of frost" in nl:
        return "FROST_RAY", None
    if dt == "LIGHTNING" or "lightning" in nl or "shock" in nl or "spark" in nl or "witch bolt" in nl:
        return "LIGHTNING_ARC", None
    if dt in ("ACID", "POISON") or "cloud" in nl or "acid" in nl or "poison" in nl or "stinking" in nl:
        return "TOXIC_CLOUD", None
    if dt == "RADIANT" or sl == "divination" or "heal" in nl or "cure" in nl or "aid" in nl or "sacred" in nl or "sun" in nl or "light" in nl or "bless" in nl:
        return "HOLY_RADIANCE", None
    if dt == "NECROTIC" or sl == "necromancy" or "drain" in nl or "death" in nl or "vampir" in nl or "inflict" in nl or "chill" in nl:
        return "NECROTIC_DRAIN", None
    if "teleport" in dl or "plane" in dl or "portal" in dl or "planar" in nl:
        return "SPATIAL_WARP", None
    if sl == "abjuration" or tt == "SELF" or "ward" in nl or "protect" in nl or "barrier" in nl or "armor" in nl:
        return "ARCANE_WARD", None
    if dt == "FORCE" or sl == "evocation":
        return "FORCE_MISSILE", None

    # 3. Structural Fallbacks
    if tt in ("BURST", "CONE"):
        return "EXPLOSIVE_BURST", None
    elif tt == "SELF":
        return "ARCANE_WARD", None
    return "FORCE_MISSILE", None

def import_spells():
    print("\n--- IMPORTING SPELLS FROM OPEN5E ---")
    all_spells = {}

    # 1. Fetch WotC SRD Spells
    url = "https://api.open5e.com/v1/spells/?document__slug=wotc-srd&limit=100"
    while url:
        print(f"Fetching {url}...")
        data = fetch_json(url)
        if not data or 'results' not in data:
            break
        for s in data['results']:
            slug = s.get('slug', '').replace('-', '_').upper()
            level = s.get('level_int', 0)
            desc = s.get('desc', '')
            range_str = s.get('range', '30 feet')
            dmg_dice = parse_damage_dice(desc)
            if not dmg_dice:
                if level == 0:
                    dmg_dice = "1d8"
                elif level == 1:
                    dmg_dice = "2d6"
                elif level == 2:
                    dmg_dice = "3d6"
                elif level == 3:
                    dmg_dice = "6d6"
                else:
                    dmg_dice = f"{level * 2}d6"

            target_type = parse_target_type(range_str, desc)
            school = s.get('school', 'evocation').lower()
            damage_type = parse_damage_type(desc)
            v_arch, bespoke = classify_visual_archetype(s.get('name'), school, target_type, damage_type, desc)
            rune_school = normalize_rune_school(school)

            all_spells[slug] = {
                "id": slug,
                "name": s.get('name'),
                "level": level,
                "school": school,
                "mpCost": calculate_mp_cost(level),
                "range": parse_range_tiles(range_str),
                "targetType": target_type,
                "damageDice": dmg_dice,
                "damageType": damage_type,
                "domain": "SPIRITUAL",
                "duration": s.get('duration', 'Instantaneous'),
                "statusEffect": parse_status_effect(desc),
                "visualArchetype": v_arch,
                "secondaryArchetype": target_type,
                "runeSchool": rune_school,
                "bespokeEffect": bespoke,
                "description": desc[:300].strip() + ("..." if len(desc) > 300 else "")
            }
        url = data.get('next')

    # 2. Add Curated Tactical Kobold Press Deep Magic Spells
    deep_magic_url = "https://api.open5e.com/v1/spells/?document__slug=dmag&limit=50"
    dm_data = fetch_json(deep_magic_url)
    if dm_data and 'results' in dm_data:
        print(f"Adding {len(dm_data['results'])} curated Deep Magic tactical spells...")
        for s in dm_data['results']:
            slug = "DM_" + s.get('slug', '').replace('-', '_').upper()
            level = s.get('level_int', 1)
            desc = s.get('desc', '')
            range_str = s.get('range', '30 feet')
            dmg_dice = parse_damage_dice(desc) or f"{max(1, level)}d8"
            target_type = parse_target_type(range_str, desc)
            school = s.get('school', 'evocation').lower()
            damage_type = parse_damage_type(desc)
            v_arch, bespoke = classify_visual_archetype(s.get('name'), school, target_type, damage_type, desc)
            rune_school = normalize_rune_school(school)

            all_spells[slug] = {
                "id": slug,
                "name": s.get('name'),
                "level": level,
                "school": school,
                "mpCost": calculate_mp_cost(level),
                "range": parse_range_tiles(range_str),
                "targetType": target_type,
                "damageDice": dmg_dice,
                "damageType": damage_type,
                "domain": "SPIRITUAL",
                "duration": s.get('duration', 'Instantaneous'),
                "statusEffect": parse_status_effect(desc),
                "visualArchetype": v_arch,
                "secondaryArchetype": target_type,
                "runeSchool": rune_school,
                "bespokeEffect": bespoke,
                "description": desc[:300].strip() + ("..." if len(desc) > 300 else "")
            }

    spells_file = os.path.join(DATA_DIR, "spells.json")
    with open(spells_file, 'w', encoding='utf-8') as f:
        json.dump(all_spells, f, indent=2)
    print(f"PASS: Wrote {len(all_spells)} total spells to {spells_file}")

def enrich_spells_vfx():
    spells_file = os.path.join(DATA_DIR, "spells.json")
    if not os.path.exists(spells_file):
        print(f"Spells file not found: {spells_file}")
        return
    with open(spells_file, 'r', encoding='utf-8') as f:
        spells = json.load(f)

    updated_count = 0
    for sid, s in spells.items():
        v_arch, bespoke = classify_visual_archetype(
            s.get("name"),
            s.get("school"),
            s.get("targetType"),
            s.get("damageType"),
            s.get("description", "")
        )
        rune_school = normalize_rune_school(s.get("school"))
        s["visualArchetype"] = v_arch
        s["secondaryArchetype"] = s.get("targetType", "PROJECTILE")
        s["runeSchool"] = rune_school
        s["bespokeEffect"] = bespoke
        updated_count += 1

    with open(spells_file, 'w', encoding='utf-8') as f:
        json.dump(spells, f, indent=2)
    print(f"PASS: Enriched {updated_count} spells with visual archetypes, rune schools, and bespoke effects.")


def calibrate_weapons():
    print("\n--- CALIBRATING WEAPONS PER OPEN5E ---")
    weapons_file = os.path.join(DATA_DIR, "weapons.json")
    with open(weapons_file, 'r', encoding='utf-8') as f:
        weapons = json.load(f)

    # 5e Core Weapon Mappings
    srd_weapons = {
        "DAGGER": {"dice": "1d4", "type": "PIERCING", "finesse": True, "thrown": True, "reach": False, "versatile": None, "twoHanded": False, "range": 4},
        "DIRK": {"dice": "1d4", "type": "PIERCING", "finesse": True, "thrown": True, "reach": False, "versatile": None, "twoHanded": False, "range": 4},
        "STILETTO": {"dice": "1d4", "type": "PIERCING", "finesse": True, "thrown": True, "reach": False, "versatile": None, "twoHanded": False, "range": 4},
        "KNIFE": {"dice": "1d4", "type": "PIERCING", "finesse": True, "thrown": True, "reach": False, "versatile": None, "twoHanded": False, "range": 4},
        "KNIFE_THROWING": {"dice": "1d4", "type": "PIERCING", "finesse": True, "thrown": True, "reach": False, "versatile": None, "twoHanded": False, "range": 5},
        "RUSTY_SWORD": {"dice": "1d6", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": "1d8", "twoHanded": False, "range": 1},
        "SWORD_SHORT": {"dice": "1d6", "type": "PIERCING", "finesse": True, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 1},
        "SWORD_SCIMITAR": {"dice": "1d6", "type": "SLASHING", "finesse": True, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 1},
        "SWORD_RAPIER": {"dice": "1d8", "type": "PIERCING", "finesse": True, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 1},
        "SWORD_LONG": {"dice": "1d8", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": "1d10", "twoHanded": False, "range": 1},
        "SWORD_BROAD": {"dice": "1d8", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": "1d10", "twoHanded": False, "range": 1},
        "SWORD_BASTARD_ONE_HANDED": {"dice": "1d8", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": "1d10", "twoHanded": False, "range": 1},
        "SWORD_BASTARD_TWO_HANDED": {"dice": "1d10", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 1},
        "SWORD_TWO_HANDED": {"dice": "2d6", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 1},
        "SWORD_CLAYMORE": {"dice": "2d6", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 1},
        "SWORD_FLAMBERGE": {"dice": "2d6", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 1},
        "AXE_HAND_THROWING": {"dice": "1d6", "type": "SLASHING", "finesse": False, "thrown": True, "reach": False, "versatile": None, "twoHanded": False, "range": 4},
        "HATCHET": {"dice": "1d6", "type": "SLASHING", "finesse": False, "thrown": True, "reach": False, "versatile": None, "twoHanded": False, "range": 4},
        "AXE_BATTLE": {"dice": "1d8", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": "1d10", "twoHanded": False, "range": 1},
        "AXE_TWO_HANDED_BATTLE": {"dice": "1d12", "type": "SLASHING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 1},
        "WARHAMMER": {"dice": "1d8", "type": "BLUDGEONING", "finesse": False, "thrown": False, "reach": False, "versatile": "1d10", "twoHanded": False, "range": 1},
        "MACE_FOOTMANS": {"dice": "1d6", "type": "BLUDGEONING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 1},
        "MACE_HORSEMANS": {"dice": "1d6", "type": "BLUDGEONING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 1},
        "MACE_GREAT": {"dice": "2d6", "type": "BLUDGEONING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 1},
        "CLUB": {"dice": "1d4", "type": "BLUDGEONING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 1},
        "CLUB_GREAT": {"dice": "1d8", "type": "BLUDGEONING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 1},
        "QUARTERSTAFF": {"dice": "1d6", "type": "BLUDGEONING", "finesse": False, "thrown": False, "reach": False, "versatile": "1d8", "twoHanded": False, "range": 1},
        "FLAIL_FOOTMANS": {"dice": "1d8", "type": "BLUDGEONING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 1},
        "MORNING_STAR": {"dice": "1d8", "type": "PIERCING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 1},
        "SPEAR_ONE_HANDED": {"dice": "1d6", "type": "PIERCING", "finesse": False, "thrown": True, "reach": False, "versatile": "1d8", "twoHanded": False, "range": 4},
        "SPEAR_THROWING": {"dice": "1d6", "type": "PIERCING", "finesse": False, "thrown": True, "reach": False, "versatile": "1d8", "twoHanded": False, "range": 5},
        "JAVELIN_ONE_HANDED": {"dice": "1d6", "type": "PIERCING", "finesse": False, "thrown": True, "reach": False, "versatile": None, "twoHanded": False, "range": 6},
        "TRIDENT_ONE_HANDED": {"dice": "1d6", "type": "PIERCING", "finesse": False, "thrown": True, "reach": False, "versatile": "1d8", "twoHanded": False, "range": 4},
        "POLEARM_HALBERD": {"dice": "1d10", "type": "SLASHING", "finesse": False, "thrown": False, "reach": True, "versatile": None, "twoHanded": True, "range": 2},
        "POLEARM_GLAIVE4": {"dice": "1d10", "type": "SLASHING", "finesse": False, "thrown": False, "reach": True, "versatile": None, "twoHanded": True, "range": 2},
        "POLEARM_AWL_PIKE3": {"dice": "1d10", "type": "PIERCING", "finesse": False, "thrown": False, "reach": True, "versatile": None, "twoHanded": True, "range": 2},
        "PIKE_WEIGHTED": {"dice": "1d10", "type": "PIERCING", "finesse": False, "thrown": False, "reach": True, "versatile": None, "twoHanded": True, "range": 2},
        "WHIP": {"dice": "1d4", "type": "SLASHING", "finesse": True, "thrown": False, "reach": True, "versatile": None, "twoHanded": False, "range": 2},
        "BOW_SHORT": {"dice": "1d6", "type": "PIERCING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 16},
        "BOW_LONG": {"dice": "1d8", "type": "PIERCING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 30},
        "BOW_COMPOSITE_SHORT": {"dice": "1d6", "type": "PIERCING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 18},
        "BOW_COMPOSITE_LONG": {"dice": "1d8", "type": "PIERCING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 32},
        "CROSSBOW_LIGHT": {"dice": "1d8", "type": "PIERCING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 16},
        "CROSSBOW_HEAVY": {"dice": "1d10", "type": "PIERCING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": True, "range": 20},
        "CROSSBOW_HAND": {"dice": "1d6", "type": "PIERCING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 6},
        "DART": {"dice": "1d4", "type": "PIERCING", "finesse": True, "thrown": True, "reach": False, "versatile": None, "twoHanded": False, "range": 4},
        "SLING": {"dice": "1d4", "type": "BLUDGEONING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 6},
        "BLOWGUN": {"dice": "1d1", "type": "PIERCING", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 5},
        "WOODEN_CROSS": {"dice": "1d4", "type": "SPIRITUAL", "finesse": False, "thrown": False, "reach": False, "versatile": None, "twoHanded": False, "range": 1}
    }

    calibrated_count = 0
    for key, w in weapons.items():
        matched = srd_weapons.get(key)
        if matched:
            w["damageDice"] = matched["dice"]
            w["damageType"] = matched["type"]
            w["isFinesse"] = matched["finesse"]
            w["isThrown"] = matched["thrown"]
            w["isReach"] = matched["reach"]
            w["isVersatile"] = matched["versatile"] is not None
            w["versatileDamageDice"] = matched["versatile"]
            w["isTwoHanded"] = matched["twoHanded"]
            w["range"] = matched["range"]
            calibrated_count += 1
        else:
            # Archetype inference for remaining exotic/historical weapons
            k_lower = key.lower()
            is_two_handed = "two_handed" in k_lower or "great" in k_lower or "bow" in k_lower or "polearm" in k_lower or "pike" in k_lower
            is_reach = "polearm" in k_lower or "pike" in k_lower or "lance" in k_lower or "whip" in k_lower or "spear_long" in k_lower
            is_thrown = "throwing" in k_lower or "javelin" in k_lower or "dart" in k_lower or "shuriken" in k_lower or "dagger" in k_lower
            is_finesse = "rapier" in k_lower or "dagger" in k_lower or "scimitar" in k_lower or "short" in k_lower or "whip" in k_lower or "katar" in k_lower

            dmg_type = "SLASHING"
            if "mace" in k_lower or "club" in k_lower or "hammer" in k_lower or "flail" in k_lower or "sling" in k_lower:
                dmg_type = "BLUDGEONING"
            elif "spear" in k_lower or "dagger" in k_lower or "pike" in k_lower or "rapier" in k_lower or "bow" in k_lower or "arrow" in k_lower or "quarrel" in k_lower or "javelin" in k_lower:
                dmg_type = "PIERCING"

            w["damageType"] = dmg_type
            w["isFinesse"] = is_finesse
            w["isThrown"] = is_thrown
            w["isReach"] = is_reach
            w["isVersatile"] = False
            w["versatileDamageDice"] = None
            w["isTwoHanded"] = is_two_handed
            if is_reach:
                w["range"] = 2

    with open(weapons_file, 'w', encoding='utf-8') as f:
        json.dump(weapons, f, indent=2)
    print(f"PASS: Calibrated {len(weapons)} weapons ({calibrated_count} exact SRD matches) in {weapons_file}")


def calibrate_armor():
    print("\n--- CALIBRATING ARMOR PER OPEN5E ---")
    armor_file = os.path.join(DATA_DIR, "armor.json")
    with open(armor_file, 'r', encoding='utf-8') as f:
        armor = json.load(f)

    # 5e Proportional Distribution Rules
    # Base AC 10.
    # Light Armor (Torso sets AC, uncapped DEX):
    #   Padded (11): Torso +1, Stealth Disadv
    #   Leather (11): Torso +1
    #   Studded Leather (12): Torso +2
    # Medium Armor (Torso sets AC, Max DEX +2):
    #   Hide (12): Torso +2
    #   Chain Shirt (13): Torso +3
    #   Scale Mail (14): Torso +3, Legs +1, Stealth Disadv
    #   Breastplate (14): Torso +4
    #   Half Plate (15): Torso +4, Legs +1, Stealth Disadv
    # Heavy Armor (Torso sets AC, Max DEX 0, Stealth Disadv):
    #   Ring Mail (14): Torso +3, Legs +1
    #   Chain Mail (16): Torso +4, Legs +1, Helm +1
    #   Splint / Banded (17): Torso +5, Legs +1, Helm +1
    #   Full Plate (18): Torso +5, Legs +1, Helm +1, Boots +1

    for key, a in armor.items():
        k = key.upper()
        # Classify piece type
        is_shield = a.get("isShield", False) or "SHIELD" in k or "BUCKLER" in k
        is_helm = a.get("isHelmet", False) or "HELM" in k or "CAP" in k or "BASINET" in k or "COIF" in k
        is_boots = a.get("isBoots", False) or "BOOT" in k
        is_legs = a.get("isLegs", False) or "LEGGING" in k
        is_gauntlets = a.get("isGauntlets", False) or "GAUNTLET" in k
        is_arms = a.get("isArms", False) or "ARM" in k
        is_torso = a.get("isTorso", False) or not (is_shield or is_helm or is_boots or is_legs or is_gauntlets or is_arms)

        category = "LIGHT"
        stealth_disadv = False
        max_dex = 99
        ac_bonus = 1

        if is_shield:
            category = "SHIELD"
            if "BUCKLER" in k:
                ac_bonus = 1
            elif "LARGE" in k or "BODY" in k:
                ac_bonus = 3
            else:
                ac_bonus = 2
        elif "PLATE" in k or "SPLINT" in k or "BANDED" in k or "BRONZE" in k:
            category = "HEAVY"
            stealth_disadv = True
            max_dex = 0
            if is_torso:
                ac_bonus = 5
            elif is_legs:
                ac_bonus = 1
            elif is_helm:
                ac_bonus = 1
            elif is_boots:
                ac_bonus = 1
            else:
                ac_bonus = 0
        elif "CHAIN" in k or "SCALE" in k or "LORICA" in k or "LAMELLAR" in k:
            category = "MEDIUM"
            stealth_disadv = "SCALE" in k or "CHAIN_MAIL" in k
            max_dex = 2
            if is_torso:
                ac_bonus = 3 if "SCALE" in k else 4
            elif is_legs:
                ac_bonus = 1
            elif is_helm:
                ac_bonus = 1
            else:
                ac_bonus = 0
        elif "STUDDED" in k or "BRIGANDINE" in k:
            category = "LIGHT"
            max_dex = 99
            stealth_disadv = False
            ac_bonus = 2 if is_torso else 0
        elif "LEATHER" in k or "PADDED" in k or "HIDE" in k:
            category = "LIGHT"
            max_dex = 99
            stealth_disadv = "PADDED" in k
            ac_bonus = 1 if is_torso else 0
        else:
            # Default piece
            ac_bonus = 1 if is_torso else 0

        a["armorCategory"] = category
        a["stealthDisadvantage"] = stealth_disadv
        a["maxDexBonus"] = max_dex
        a["armorClassBonus"] = ac_bonus

    with open(armor_file, 'w', encoding='utf-8') as f:
        json.dump(armor, f, indent=2)
    print(f"PASS: Calibrated {len(armor)} armor items in {armor_file}")


def enrich_magic_items():
    print("\n--- ENRICHING MAGIC ITEMS PER OPEN5E ---")
    items_file = os.path.join(DATA_DIR, "items.json")
    with open(items_file, 'r', encoding='utf-8') as f:
        items = json.load(f)

    # Magic items mapped to verified RGBA textures
    magic_additions = {
        "RING_OF_PROTECTION": {
            "friendlyName": "Ring of Protection",
            "description": "A shimmering ring that grants +1 to Armor Class and saving throws.",
            "texturePath": "images/items/ring.png",
            "baseValue": 1500,
            "armorClassBonus": 1,
            "isRing": True,
            "isRingAppearance": True,
            "isUsable": False
        },
        "BOOTS_OF_ELVENKIND": {
            "friendlyName": "Boots of Elvenkind",
            "description": "While you wear these boots, your steps make no sound.",
            "texturePath": "images/armor/leather_boots.png",
            "baseValue": 1200,
            "armorClassBonus": 1,
            "isBoots": True,
            "isArmor": True,
            "stealthDisadvantage": False,
            "armorCategory": "LIGHT"
        },
        "CLOAK_OF_PROTECTION": {
            "friendlyName": "Cloak of Protection",
            "description": "A fine elven cloak granting +1 to Armor Class and resistances.",
            "texturePath": "images/items/dirty_cloth.png",
            "baseValue": 1400,
            "armorClassBonus": 1,
            "isCloak": True,
            "isArmor": True
        },
        "WAND_OF_MAGIC_MISSILES": {
            "friendlyName": "Wand of Magic Missiles",
            "description": "This wand has 7 charges to cast Magic Missile.",
            "texturePath": "images/items/wand.png",
            "baseValue": 1000,
            "isWandAppearance": True,
            "isUsable": True
        },
        "PERIAPT_OF_WOUND_CLOSURE": {
            "friendlyName": "Periapt of Wound Closure",
            "description": "A mystical amulet that stabilizes dying heroes and doubles healing.",
            "texturePath": "images/items/necklace.png",
            "baseValue": 1800,
            "isAmulet": True,
            "isArmor": True
        },
        "BAG_OF_HOLDING": {
            "friendlyName": "Bag of Holding",
            "description": "An extradimensional bag that holds up to 500 lbs effortlessly.",
            "texturePath": "images/items/large_bag.png",
            "baseValue": 2000,
            "isContainer": True
        },
        "POTION_OF_INVISIBILITY": {
            "friendlyName": "Potion of Invisibility",
            "description": "Drinking this potion cloaks you in complete invisibility for 1 hour.",
            "texturePath": "images/items/potion_blue.png",
            "baseValue": 600,
            "isPotion": True,
            "isPotionAppearance": True,
            "isUsable": True
        },
        "POTION_OF_HEROISM": {
            "friendlyName": "Potion of Heroism",
            "description": "Grants 10 temporary HP and the effects of the Bless spell.",
            "texturePath": "images/items/potion_gold.png",
            "baseValue": 800,
            "isPotion": True,
            "isPotionAppearance": True,
            "isUsable": True
        },
        "SCROLL_FIREBALL": {
            "friendlyName": "Spell Scroll (Fireball)",
            "description": "A scroll containing the Fireball incantation. Can be cast or scribed at camp.",
            "texturePath": "images/items/scroll.png",
            "baseValue": 500,
            "isScrollAppearance": True,
            "isUsable": True
        },
        "SCROLL_MISTY_STEP": {
            "friendlyName": "Spell Scroll (Misty Step)",
            "description": "A scroll containing Misty Step. Can be cast or scribed at camp.",
            "texturePath": "images/items/scroll.png",
            "baseValue": 350,
            "isScrollAppearance": True,
            "isUsable": True
        },
        "SCROLL_MAGIC_MISSILE": {
            "friendlyName": "Spell Scroll (Magic Missile)",
            "description": "A scroll containing Magic Missile. Can be cast or scribed at camp.",
            "texturePath": "images/items/scroll.png",
            "baseValue": 200,
            "isScrollAppearance": True,
            "isUsable": True
        },
        "SCROLL_LIGHTNING_BOLT": {
            "friendlyName": "Spell Scroll (Lightning Bolt)",
            "description": "A scroll containing Lightning Bolt. Can be cast or scribed at camp.",
            "texturePath": "images/items/scroll.png",
            "baseValue": 550,
            "isScrollAppearance": True,
            "isUsable": True
        }
    }

    added = 0
    for k, v in magic_additions.items():
        if k not in items:
            items[k] = v
            added += 1
        else:
            items[k].update(v)

    with open(items_file, 'w', encoding='utf-8') as f:
        json.dump(items, f, indent=2)
    print(f"PASS: Added/Updated {len(magic_additions)} iconic magic items in {items_file}")


def main():
    print("========================================")
    print("   TARMIN2 OPEN5E INGESTION PIPELINE    ")
    print("========================================")
    import_spells()
    calibrate_weapons()
    calibrate_armor()
    enrich_magic_items()
    print("\nPIPELINE COMPLETE!")

if __name__ == "__main__":
    main()
