"""PROTOTYPE, throwaway: drafts a Weapon Family for every weapons.json entry.

Answers "Prototype: the Weapon Family of every weapon" (map #221). Prints a markdown table;
notes starting with ? are the hard calls for the user to settle. Not used by the game.
Run from the repo root: python docs/prototypes/PROTOTYPE_weapon_families.py
"""
import json
import sys
from collections import Counter

W = json.load(open('assets/data/weapons.json', encoding='utf-8'))

# Explicit calls first (id -> (family, note)); None = no Family.
EXPLICIT = {
    'RUSTY_SWORD': ('Blade', "? Father's Sword: an ordinary Blade, or its own track"),
    'WOODEN_CROSS': ('Blunt', '? holy symbol (Spiritual): Blunt or no Family'),
    'ARQUEBUS': ('Firearm', ''), 'BLUNDERBUS': ('Firearm', ''), 'MUSKET': ('Firearm', ''),
    'PISTOL_STARWHEEL': ('Firearm', ''), 'TUFENK': ('Firearm', 'already a firearm in gamedata/firearm'),
    'SLING': ('Thrown', '? sling: Thrown or Bow'), 'STAFF_SLING': ('Thrown', '? sling'),
    'STAFF_SLING_STINKPOT': ('Thrown', '? sling'),
    'BLOWGUN': ('Thrown', '? blowgun: Thrown, Bow or none'),
    'BO_STICK': ('Blunt', '? staff: Blunt or Polearm'), 'QUARTERSTAFF': ('Blunt', '? staff'),
    'STAFF_HAMANUS': ('Blunt', '? staff'), 'STAFF_HORNHEAD': ('Blunt', '? staff'),
    'POLEARM_TETSUBO': ('Blunt', 'named Polearm, but an iron club'),
    'WHIP': ('Blade', '? whip: Blade, Unarmed or none'), 'WHIP_CHAIN': ('Blade', '? whip'),
    'WHIP_MASTERS': ('Blade', '? whip'), 'SCOURGE': ('Blade', '? whip'),
    'CHAIN': ('Blunt', '? chain'), 'NUNCHAKU': ('Blunt', 'data says Slashing'),
    'SCYTHE': ('Polearm', '? scythe: Polearm or Blade'), 'SICKLE': ('Blade', ''),
    'BAGH_NAKH_TIGER_CLAWS': ('Unarmed', ''), 'CESTUS': ('Unarmed', ''), 'DEJADA_CESTUS': ('Unarmed', ''),
    'GLOVE_NAIL': ('Unarmed', ''), 'KICK_SLASHER': ('Unarmed', ''), 'WRIST_RAZOR': ('Unarmed', ''),
    'SPIKE_ELBOW': ('Unarmed', '? armour spikes'), 'SPIKE_HEAD': ('Unarmed', '? armour spikes'),
    'SPIKE_KNEE': ('Unarmed', '? armour spikes'), 'SPIKES_BODY': ('Unarmed', '? armour spikes'),
    'TALID': ('Unarmed', 'gauntlet'), 'CAVILER': ('Unarmed', 'claw'),
    'TORTOISE_BLADES': ('Unarmed', 'shell-mounted blades'), 'PUNCH_CUTTER': ('Unarmed', ''),
    'GAFF_HOOK_ATTACHED': ('Unarmed', 'hook prosthetic'), 'PATA1': ('Blade', 'gauntlet-sword'),
    'DRAGONS_PAW': ('Polearm', 'double-bladed staff'),
    'BUCKLER_SPIKED': ('Blunt', '? shield as weapon: Blunt or a Shield Family'),
    'SHIELD_SPIKE': ('Blunt', '? shield as weapon'),
    'BOLA': ('Thrown', ''), 'CHAKRAM': ('Thrown', ''), 'CHATKCHA': ('Thrown', ''), 'SHURIKEN': ('Thrown', ''),
    'DART': ('Thrown', ''), 'DART_BARBED': ('Thrown', ''), 'TROMBASH_THROWN': ('Thrown', ''),
    'CAHULAKS': ('Thrown', ''), 'DEJADA': ('Thrown', 'sling-basket'), 'SPEAR_CASTER': ('Thrown', 'atlatl'),
    'TROMBASH_HELD': ('Axe', ''),
    'NET': ('Thrown', '? net: Thrown or no Family (utility)'), 'LASSO': ('Thrown', '? lasso'),
    'CALTROP': (None, 'trap, not a weapon'),
    'BLOWGUN_BARBED_DART': (None, 'ammunition'), 'BLOWGUN_NEEDLE': (None, 'ammunition'),
    'DISK_CROSSBOW': (None, 'ammunition'),
    'SMALL_FIREBALL': (None, 'spell projectile'), 'LARGE_FIREBALL': (None, 'spell projectile'),
    'SMALL_LIGHTNING': (None, 'spell projectile'), 'LARGE_LIGHTNING': (None, 'spell projectile'),
    'WAND': (None, 'magic'), 'BOOK': (None, '? a thrown or swung book: no Family, or Thrown or Blunt'),
    'SPEAR': ('Polearm', '? the basic Spear is flagged Ranged'),
    'MANCATCHER2': ('Polearm', ''), 'IMPALER': ('Polearm', ''), 'LOTULIS': ('Polearm', ''),
    'GOUGE': ('Polearm', ''), 'ASSEGAI': ('Polearm', 'short spear'), 'ANKUS_ELEPHANT_GOAD': ('Polearm', ''),
    'HARPOON_ONE_HANDED': ('Polearm', ''), 'HARPOON_TWO_HANDED': ('Polearm', ''),
    'TRIDENT_ONE_HANDED': ('Polearm', ''), 'TRIDENT_TWO_HANDED': ('Polearm', ''),
    'GLADIATORS_FRIEND_FOOTMANS': ('Polearm', ''), 'GLADIATORS_FRIEND_HORSEMANS': ('Polearm', ''),
    'GAFF_HOOK_HELD': ('Dagger', ''),
    'BELAYING_PIN': ('Blunt', ''), 'SAP': ('Blunt', 'data says Slashing'),
    'KNOBKERRIE': ('Blunt', 'data says Slashing'), 'GADA': ('Blunt', 'data says Slashing'),
    'MACA': ('Blunt', 'data says Slashing'), 'MORNING_STAR': ('Blunt', ''),
    'MORNING_STAR_DOUBLE_BALL': ('Blunt', ''), 'STICK_GOBLIN': ('Blunt', ''), 'STICKS_SINGING': ('Blunt', ''),
    'QUABONE': ('Blunt', ''), 'FLINDBAR': ('Blunt', ''), 'ALHULAK': ('Blunt', 'flail'),
    'HORA': ('Blunt', ''), 'SHOKA': ('Blunt', ''), 'RITIIK': ('Blunt', ''),
    'PICK_FOOTMANS': ('Axe', '? picks: Axe or Blunt'), 'PICK_HORSEMANS': ('Axe', '? pick'),
    'PICK_ICE': ('Axe', '? pick'), 'ZAGHNAL': ('Axe', '? war pick'), 'CARRIKAL': ('Axe', ''),
    'HATCHET': ('Axe', ''), 'HATCHET_HAWK': ('Axe', ''),
    'AXE_HAND_THROWING': ('Axe', '? throwing axe: Axe, or Thrown when thrown'),
    'MADU': ('Dagger', ''), 'SAI': ('Dagger', ''), 'MAIN_GAUCHE': ('Dagger', ''), 'SWORDLET': ('Dagger', ''),
    'RAZOR': ('Dagger', ''), 'IUAK_SNOW_BLADE': ('Dagger', ''), 'KUKRI': ('Dagger', ''),
    'BARDS_FRIEND': ('Dagger', ''), 'BASILARD': ('Dagger', ''), 'JAMBIYA': ('Dagger', ''),
    'PESHKABZ': ('Dagger', ''), 'PUCHIK': ('Dagger', ''), 'NYEK_PLE_NEN_TOH': ('Dagger', ''),
    'KATAR_PUNCH_DAGGER': ('Dagger', ''), 'SWORD_PIXIE': ('Dagger', 'tiny sword'),
    'MACHETE': ('Blade', ''), 'KORA': ('Blade', ''), 'SHOTEL': ('Blade', ''),
    'DAIKYU': ('Bow', 'data says melee Slashing, no Ranged flag'),
}


def classify(k):
    if k in EXPLICIT:
        return EXPLICIT[k]
    if k.startswith(('ARROW_', 'QUARREL_', 'SLING_')):
        return (None, 'ammunition')
    if k.startswith('CROSSBOW'):
        return ('Crossbow', '')
    if k.startswith('BOW'):
        return ('Bow', '')
    if k.startswith(('POLEARM', 'PIKE', 'LANCE', 'SPEAR_')) and k != 'SPEAR_THROWING':
        return ('Polearm', '')
    if k.startswith('JAVELIN') or k == 'SPEAR_THROWING':
        return ('Thrown', '? javelin: Thrown or Polearm')
    if k == 'KNIFE_THROWING':
        return ('Dagger', '? throwing knife: Dagger, or Thrown when thrown')
    if k.startswith(('DAGGER', 'DIRK', 'STILETTO', 'KNIFE')):
        return ('Dagger', '')
    if k.startswith(('SWORD', 'TWO_HANDED_SWORD')):
        return ('Blade', '')
    if k.startswith('AXE'):
        return ('Axe', '')
    if k.startswith(('CLUB', 'MACE', 'FLAIL', 'WARHAMMER')):
        return ('Blunt', '')
    return ('?', '? unclassified')


rows = [(k, v.get('friendlyName'), *classify(k)) for k, v in W.items()]
print('| id | name | family | note |\n|---|---|---|---|')
for k, name, fam, note in rows:
    print(f"| {k} | {name} | {fam or '-'} | {note} |")
print('\nCounts:', dict(Counter(f or 'none' for _, _, f, _ in rows)), file=sys.stderr)
print('Hard calls:', sum(1 for r in rows if r[3].startswith('?')), file=sys.stderr)
