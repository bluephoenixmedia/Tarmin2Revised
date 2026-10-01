"""Builds the first batch of new monsters from the raw sprite pack in assets/images/monsters.

For each sprite it
  * writes a copy capped at 768 px on the longest side to assets/images/monsters/batch1/
    (the raw originals are left alone; the full set would cost hundreds of MB of video memory),
  * derives the 24x24 retro silhouette the classic renderer draws, from the sprite's alpha channel,
and then appends the monster templates to assets/data/monsters.json as text, so the diff stays small and
the file keeps its formatting.

Where a monster appears is decided by its baseLevel and frequency in monsters.json (MonsterSpawner,
NetHack-style: eligible from baseLevel up to four times it, weighted by frequency). The
monsterSpawnTable in spawntables.json is not read by anything, so nothing is written there.

Idempotent: a monster already present is not added again, but its baseLevel and frequency are synced
from the table below, which is the source of truth for them. Run from the repository root.
"""
import json
import os
import re
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, 'assets', 'images', 'monsters')
OUT = os.path.join(RAW, 'batch1')
MONSTERS_JSON = os.path.join(ROOT, 'assets', 'data', 'monsters.json')
MAX_SIDE = 768
GRID = 24


def v(color, source, name, weight, min_level=1, max_level=99, **extra):
    d = {'color': color, 'minLevel': min_level, 'maxLevel': max_level, 'weight': weight,
         '_source': source, '_name': name}
    d.update(extra)
    return d


# name: spec dict -- family, level (= baseLevel, the first level it appears at), freq (= frequency, spawn
# weight), hp, ac, mr (magic resist %), xp, dice, int, dex, speed, shrink (billboard scale), hit (on-hit
# effects), optional ranged / caster blocks, and its variants (colourways).
MONSTERS = {
    'BAT': dict(family='BEAST', level=1, freq=5, hp=5, ac=13, mr=0, xp=5, dice='1d4', int=2, dex=16, speed=22, shrink=0.6,
                hit=[('BLEEDING', 3, 1, 0.25)],
                variants=[v('WHITE', 'Bat_1', 'bat', 10)]),
    'GIANT_BEE': dict(family='BEAST', level=2, freq=4, hp=6, ac=14, mr=0, xp=8, dice='1d4', int=1, dex=16, speed=20, shrink=0.5,
                      hit=[('POISONED', 3, 1, 0.4)],
                      variants=[v('YELLOW', 'Bee_1', 'giant_bee', 10)]),
    'GIANT_SNAIL': dict(family='BEAST', level=2, freq=3, hp=20, ac=8, mr=0, xp=10, dice='1d4', int=1, dex=4, speed=5, shrink=0.8,
                        hit=[],
                        variants=[v('GREEN', 'Snail_1', 'giant_snail', 10)]),
    'GIANT_CENTIPEDE': dict(family='BEAST', level=3, freq=5, hp=14, ac=12, mr=0, xp=14, dice='1d6', int=1, dex=12, speed=14,
                            shrink=0.8, hit=[('POISONED', 4, 1, 0.4)],
                            variants=[v('GREEN', 'Bug_1a', 'giant_centipede_green', 10),
                                      v('RED', 'Bug_1b', 'giant_centipede_red', 3, min_level=5)]),
    'LANDSTALKER': dict(family='BEAST', level=4, freq=4, hp=30, ac=15, mr=0, xp=40, dice='2d6', int=2, dex=8, speed=10,
                        shrink=1.0, hit=[],
                        variants=[v('BROWN', '006_Landstalker_B', 'landstalker', 10)]),
    'COCKATRICE': dict(family='MYTHICAL', level=5, freq=3, hp=22, ac=13, mr=10, xp=45, dice='1d6', int=2, dex=12, speed=12,
                       shrink=0.9, hit=[('SLOWED', 4, 1, 0.35)],
                       variants=[v('GREEN', 'Cockatrice_1', 'cockatrice', 10)]),
    'LIZARD_WARRIOR': dict(family='HUMANOID', level=5, freq=4, hp=28, ac=15, mr=0, xp=50, dice='1d8+2', int=6, dex=12,
                           speed=12, shrink=1.0, hit=[],
                           variants=[v('GREEN', '046_LizardWarrior_E', 'lizard_warrior', 10)]),
    'BEETLESCRATCH': dict(family='BEAST', level=6, freq=3, hp=26, ac=14, mr=15, xp=70, dice='1d8', int=3, dex=12, speed=12,
                          shrink=1.0, hit=[], ranged=dict(range=6, dice='2d6', projectile='FIREBALL', dtype='MAGICAL', preferred=5),
                          variants=[
                              v('PURPLE', '020_Beetlescratch_C', 'beetlescratch_storm', 3, min_level=7,
                                rangedProjectile='PSYCHIC_BOLT', rangedDamageType='SORCERY'),
                              v('YELLOW', '020_Beetlescratch_D', 'beetlescratch_lightning', 5,
                                rangedProjectile='RADIANT_SPEAR', rangedDamageType='LIGHT'),
                              v('RED', '020_Beetlescratch_E', 'beetlescratch_fire', 4,
                                rangedProjectile='FIREBALL', rangedDamageType='FIRE'),
                              v('BLACK', '020_Beetlescratch_F', 'beetlescratch_void', 2, min_level=12,
                                rangedProjectile='DEATH_RAY', rangedDamageType='DARK'),
                              v('BLUE', '020_Beetlescratch_G', 'beetlescratch_ice', 5,
                                rangedProjectile='DEATH_RAY', rangedDamageType='ICE')]),
    'JESTER': dict(family='HUMANOID', level=6, freq=2, hp=24, ac=14, mr=20, xp=60, dice='1d6', int=14, dex=14, speed=12,
                   shrink=0.9, hit=[('CONFUSED', 3, 1, 0.3)], caster=dict(
                       schools=['ENCHANTMENT', 'ILLUSION'], spells=['HIDEOUS_LAUGHTER', 'VICIOUS_MOCKERY', 'CONFUSION'],
                       chance=50),
                   variants=[v('PINK', '048_Jester_B', 'jester', 10)]),
    'SPECTER': dict(family='UNDEAD', level=7, freq=2, hp=28, ac=15, mr=30, xp=80, dice='1d8', int=14, dex=12, speed=12,
                    shrink=1.0, hit=[], caster=dict(schools=['EVOCATION', 'NECROMANCY'], spells=['FIRE_BOLT'], chance=55),
                    variants=[
                        v('ORANGE', '012_Specter_E', 'specter_fire', 4, innateSpells=['FIRE_BOLT', 'BURNING_HANDS']),
                        v('BLUE', '012_Specter_F', 'specter_frost', 5, innateSpells=['RAY_OF_FROST', 'CHILL_TOUCH']),
                        v('PURPLE', '012_Specter_G', 'specter_arcane', 3, min_level=9,
                          innateSpells=['MAGIC_MISSILE', 'RAY_OF_ENFEEBLEMENT']),
                        v('GREEN', '012_Specter_H', 'specter_venom', 4, innateSpells=['POISON_SPRAY', 'ACID_ARROW'])]),
    'SAGE': dict(family='HUMANOID', level=8, freq=2, hp=30, ac=14, mr=25, xp=100, dice='1d6', int=18, dex=12, speed=12,
                 shrink=0.9, hit=[], caster=dict(schools=['EVOCATION'], spells=['MAGIC_MISSILE'], chance=65),
                 variants=[
                     v('ORANGE', '056_Sage_B', 'sage_fire', 4, innateSpells=['FIRE_BOLT', 'FIREBALL']),
                     v('BLUE', '056_Sage_C', 'sage_frost', 5, innateSpells=['RAY_OF_FROST', 'ICE_STORM']),
                     v('RED', '056_Sage_F', 'sage_storm', 3, min_level=10,
                       innateSpells=['LIGHTNING_BOLT', 'SHOCKING_GRASP']),
                     v('WHITE', '056_Sage_G', 'sage_arcane', 5, innateSpells=['MAGIC_MISSILE', 'THUNDERWAVE'])]),
    'SKELETAL_WIZARD': dict(family='UNDEAD', level=9, freq=2, hp=34, ac=15, mr=30, xp=110, dice='1d8', int=17, dex=10,
                            speed=11, shrink=0.9, hit=[('WEAKENED', 6, 1, 0.3)],
                            caster=dict(schools=['NECROMANCY'], spells=['BLIGHT', 'VAMPIRIC_TOUCH', 'RAY_OF_ENFEEBLEMENT'],
                                        chance=60),
                            variants=[v('GRAY', '060_SkeletalWizard_A', 'skeletal_wizard', 10)]),
}


def process_sprite(source, name):
    """Writes the capped copy and returns (relative texture path, (w, h), 24x24 silhouette rows)."""
    os.makedirs(OUT, exist_ok=True)
    im = Image.open(os.path.join(RAW, source + '.png')).convert('RGBA')
    w, h = im.size
    scale = min(1.0, MAX_SIDE / float(max(w, h)))
    small = im if scale >= 1.0 else im.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    small.save(os.path.join(OUT, name + '.png'), optimize=True)

    # Silhouette from the original's alpha: a cell is solid if any pixel in it is opaque.
    alpha = im.split()[3]
    rows = []
    for gy in range(GRID):
        row = ''
        for gx in range(GRID):
            box = (gx * w // GRID, gy * h // GRID, max(gx * w // GRID + 1, (gx + 1) * w // GRID),
                   max(gy * h // GRID + 1, (gy + 1) * h // GRID))
            row += '#' if alpha.crop(box).getextrema()[1] > 32 else '.'
        rows.append(row)
    return 'images/monsters/batch1/%s.png' % name, (w, h), rows


def scale_for(size, shrink):
    w, h = size
    if w >= h:
        x, y = 0.9, max(0.45, 0.9 * h / w)
    else:
        y, x = 1.0, max(0.5, 1.0 * w / h)
    return {'x': round(x * shrink, 2), 'y': round(y * shrink, 2)}


def build_entry(spec):
    first = spec['variants'][0]
    path, size, grid = process_sprite(first['_source'], first['_name'])
    entry = {
        'baseLevel': spec['level'], 'frequency': spec['freq'], 'maxHP': spec['hp'], 'maxMP': 0,
        'armorClass': spec['ac'],
    }
    if spec['mr']:
        entry['magicResistance'] = spec['mr']
    entry.update({
        'baseExperience': spec['xp'], 'family': spec['family'], 'texturePath': path, 'spriteData': grid,
        'scale': scale_for(size, spec['shrink']), 'damageDice': spec['dice'], 'damageType': 'PHYSICAL',
        'intelligence': spec['int'], 'dexterity': spec['dex'], 'moveSpeed': spec['speed'],
    })
    caster = spec.get('caster')
    if caster:
        entry.update({'isSpellcaster': True, 'spellSchools': caster['schools'], 'innateSpells': caster['spells'],
                      'spellChance': caster['chance'], 'aiType': 'TACTICAL'})
    ranged = spec.get('ranged')
    if ranged:
        entry.update({'hasRangedAttack': True, 'attackRange': ranged['range'], 'rangedProjectile': ranged['projectile'],
                      'rangedDamageDice': ranged['dice'], 'rangedDamageType': ranged['dtype'],
                      'rangedPreferredDistance': ranged['preferred']})
    entry['onHitEffects'] = [{'type': t, 'duration': d, 'potency': p, 'stackable': False, 'chance': c}
                             for (t, d, p, c) in spec['hit']]
    variants = []
    for var in spec['variants']:
        vpath, _, _ = process_sprite(var['_source'], var['_name'])
        out = {k: val for k, val in var.items() if not k.startswith('_')}
        if len(spec['variants']) > 1:
            out['texturePath'] = vpath
        variants.append(out)
    entry['variants'] = variants
    return entry


def indent_block(text, spaces):
    pad = ' ' * spaces
    return text.replace('\n', '\n' + pad)


def sync_existing(text, nl):
    """Puts baseLevel and frequency from the table into entries that already exist."""
    for name, spec in MONSTERS.items():
        head = re.search(r'^  "%s": \{%s' % (name, re.escape(nl)), text, re.M)
        if not head:
            continue
        end = text.index(nl + '  }', head.end())
        block = text[head.end():end]
        block = re.sub(r'"baseLevel": \d+', '"baseLevel": %d' % spec['level'], block, count=1)
        if re.search(r'"frequency": \d+', block):
            block = re.sub(r'"frequency": \d+', '"frequency": %d' % spec['freq'], block, count=1)
        else:
            block = re.sub(r'("baseLevel": \d+,%s)' % re.escape(nl),
                           lambda m: m.group(1) + '    "frequency": %d,%s' % (spec['freq'], nl), block, count=1)
        text = text[:head.end()] + block + text[end:]
    return text


def main():
    with open(MONSTERS_JSON, encoding='utf-8', newline='') as f:
        raw = f.read()
    nl = '\r\n' if '\r\n' in raw else '\n'
    text = raw.replace('\r\n', '\n')  # work in LF, convert back on write
    existing = json.loads(text)

    text = sync_existing(text, '\n')

    added = []
    chunks = []
    for name, spec in MONSTERS.items():
        if name in existing:
            continue
        body = json.dumps(build_entry(spec), indent=4)
        chunks.append('  "%s": %s' % (name, indent_block(body, 2)))
        added.append(name)

    if chunks:
        stripped = text.rstrip()
        assert stripped.endswith('}')
        stripped = stripped[:-1].rstrip()
        text = stripped + ',\n' + (',\n'.join(chunks)) + '\n}\n'

    json.loads(text)  # must still parse, and is checked before anything is written
    out = text.replace('\n', nl)
    if out != raw:
        with open(MONSTERS_JSON, 'w', encoding='utf-8', newline='') as f:
            f.write(out)

    print('monsters added:', ', '.join(added) or 'none')


if __name__ == '__main__':
    sys.exit(main())
