# Shrinks the generated charges to 64x64 white silhouettes,.
import os
from PIL import Image
SRC = 'docs/game_assets/heralds'
OUT = 'assets/images/arms'
MAP = {
 'flayed_hand': 'flayed_hand.png', 'open_mouth': 'open_mouth_singing.png', 'hymnal': 'hymnal_boundinskin.png',
 'hook_crown': 'hook_crown.png', 'chain_links': '3_chain_links.png', 'manacle': 'closed_manacle.png',
 'lidless_eye': 'lidless_eye.png', 'white_mask': 'white_mask.png', 'tongueless_bell': 'bell_no_tongue.png',
 'gilded_fly': 'gilded_fly.png', 'rotting_crown': 'rotting_crown.png', 'weeping_coin': 'weeping_coin.png',
 'scourge': 'scourge_aflame.png', 'burning_hand': 'burning_hand.png', 'ash_field': 'field_ash.png',
 'skull_tower': 'tower_skulls.png', 'femur_spade': 'femur_spade.png', 'crowned_jawbone': 'crown_jawbone.png',
 'glass_mouth': 'glass_mouth.png', 'starving_hound': 'starving_hound.png', 'cracked_bowl': 'cracked_bowl.png',
 'antlered_skull': 'antlered skull.png', 'horned_crown': 'horned_crown.png',
}
os.makedirs(OUT, exist_ok=True)
for slug, f in MAP.items():
    a = Image.open(os.path.join(SRC, f)).convert('RGBA').getchannel('A')
    a = a.point(lambda v: 255 if v > 128 else 0)
    a = a.crop(a.getbbox())
    w, h = a.size
    s = 60 / max(w, h)
    a2 = a.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    im = Image.new('RGBA', (64, 64), (255, 255, 255, 0))
    white = Image.new('RGBA', a2.size, (255, 255, 255, 255))
    white.putalpha(a2)
    im.paste(white, ((64 - a2.width) // 2, (64 - a2.height) // 2))

    im.save(os.path.join(OUT, slug + '.png'))
