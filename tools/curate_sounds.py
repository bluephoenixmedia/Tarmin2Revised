"""Builds the game's sound effects from the raw sound packs.

The packs in assets/sounds (FL_GFX_*, 24-bit, hundreds of megabytes) are source material, not game
assets: libGDX cannot decode 24-bit PCM and most of the files are never played. This copies the
curated picks below into assets/sounds/sfx as 16-bit WAV, named by role (sword_1.wav ...), which is
what assets/data/soundbank.json refers to.

Usage, from the repository root:
    python tools/curate_sounds.py            # build assets/sounds/sfx from the packs
    python tools/curate_sounds.py --stash    # then move the raw packs out to ../Tarmin2_audio_originals/packs

Nothing is ever deleted: --stash moves, and refuses to overwrite.
"""
import argparse
import glob
import os
import shutil
import sys
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS = os.path.join(ROOT, 'assets', 'sounds')
OUT = os.path.join(SOUNDS, 'sfx')
STASH = os.path.join(os.path.dirname(ROOT), 'Tarmin2_audio_originals', 'packs')
PREFIX = 'FL_GFX_'

# dest base name -> (source folder relative to assets/sounds, source file stem pattern with {n:02d}, numbers)
CURATED = {
    'swing': ('', 'Swing_Dash_{n:02d}', range(1, 6)),
    'sword': ('combat_sounds', 'Medieval_Fighting_Sword_{n:02d}', range(1, 7)),
    'club': ('combat_sounds', 'Medieval_Fighting_War_Club_{n:02d}', range(1, 4)),
    'arrow': ('combat_sounds', 'Medieval_Fighting_Arrow_{n:02d}', range(1, 5)),
    'zombie_roar': ('monster_sounds', 'Voice_Monster_Zombie_Roar_{n:02d}', range(1, 7)),
    'notify': ('event_sounds/notifications', 'Notification_Sfx_{n:02d}', range(1, 4)),
    'impact': ('event_sounds/orchestral_impact', 'Orchestral_Impact_{n:02d}', range(1, 4)),
    'bandage': ('', 'Clothing_Fabric_Material_Foley_Bandage_{n:02d}', range(1, 3)),
    'bag': ('', 'Clothing_Fabric_Material_Foley_Put_In_a_Backpack_{n:02d}', range(1, 3)),
    'drop': ('', 'Clothing_Fabric_Material_Foley_Drop_Material_{n:02d}', range(1, 4)),
}

# Spell casts: two of the thirty Magic_Spell_Attack recordings per archetype, in this order.
ARCHETYPES = ['FLAME_BOLT', 'FROST_RAY', 'LIGHTNING_ARC', 'FORCE_MISSILE', 'EXPLOSIVE_BURST', 'HOLY_RADIANCE',
              'NECROTIC_DRAIN', 'TOXIC_CLOUD', 'OBSCURING_MIST', 'SPATIAL_WARP', 'ARCANE_WARD', 'PSYCHIC_SHOCK',
              'THUNDER_CONCUSSION']
for _i, _a in enumerate(ARCHETYPES):
    CURATED['spell_' + _a.lower()] = ('spells', 'Magic_Spell_Attack_Sfx_{n:02d}', range(2 * _i + 1, 2 * _i + 3))
CURATED['spell_self'] = ('spells/Dreamy Whooshes', 'Motion_Dreamy_Whoosh_{n:02d}', range(1, 4))
CURATED['spell_warp'] = ('spells', 'Orchestral_Whoosh_{n:02d}', range(1, 3))

PACK_FOLDERS = ['alarms_sirens', 'ambient_maze', 'combat_sounds', 'cooking_ambient', 'event_sounds',
                'monster_sounds', 'spells']


def to_16bit(frames):
    out = bytearray(len(frames) // 3 * 2)
    j = 0
    for i in range(0, len(frames) - 2, 3):
        out[j] = frames[i + 1]
        out[j + 1] = frames[i + 2]
        j += 2
    return bytes(out)


def convert(src, dst):
    with wave.open(src, 'rb') as w:
        p = w.getparams()
        frames = w.readframes(p.nframes)
    if p.sampwidth == 3:
        frames = to_16bit(frames)
    elif p.sampwidth != 2:
        raise SystemExit('%s is %d-bit; expected 24 or 16' % (src, p.sampwidth * 8))
    with wave.open(dst, 'wb') as o:
        o.setnchannels(p.nchannels)
        o.setsampwidth(2)
        o.setframerate(p.framerate)
        o.writeframes(frames)


def build():
    os.makedirs(OUT, exist_ok=True)
    made = 0
    for base, (folder, pattern, numbers) in CURATED.items():
        for k, n in enumerate(numbers, 1):
            rel = os.path.join(*(folder.split('/') if folder else []), PREFIX + pattern.format(n=n) + '.wav')
            # The packs live in assets/sounds until stashed, then in the stash; accept either.
            src = next((c for c in (os.path.join(SOUNDS, rel), os.path.join(STASH, rel)) if os.path.exists(c)), None)
            if src is None:
                raise SystemExit('missing source ' + rel)
            convert(src, os.path.join(OUT, '%s_%d.wav' % (base, k)))
            made += 1
    print('wrote %d files to %s (%d KB)' % (made, OUT, sum(
        os.path.getsize(f) for f in glob.glob(os.path.join(OUT, '*.wav'))) // 1024))


def stash():
    dest_root = STASH
    os.makedirs(dest_root, exist_ok=True)
    for name in PACK_FOLDERS + [os.path.basename(f) for f in glob.glob(os.path.join(SOUNDS, PREFIX + '*.wav'))]:
        src = os.path.join(SOUNDS, name)
        dst = os.path.join(dest_root, name)
        if not os.path.exists(src):
            continue
        if os.path.exists(dst):
            raise SystemExit('refusing to overwrite ' + dst)
        shutil.move(src, dst)
        print('moved', name)


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--stash', action='store_true', help='after building, move the raw packs out of the repo')
    a = ap.parse_args()
    build()
    if a.stash:
        stash()
    sys.exit(0)
