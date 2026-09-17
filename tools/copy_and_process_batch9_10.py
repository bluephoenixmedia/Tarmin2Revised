"""
copy_and_process_batch9_10.py

Copies 9 newly generated weapon sprites from the brain artifact directory to tools/raw_weapons/,
samples the background color at (5, 5), and processes each through the ImageMagick
chroma-key erosion pipeline to produce clean 32-bit transparent RGBA PNGs in assets/images/weapons/.
"""

import os
import shutil
import subprocess

brain_dir = r"C:\Users\denni\.gemini\antigravity-ide\brain\b803c96d-ed89-42ad-81d4-66b7f16ba4d8"
repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
raw_weapons_dir = os.path.join(repo_root, "tools", "raw_weapons")
output_dir = os.path.join(repo_root, "assets", "images", "weapons")

new_weapons = {
    "dagger_climbing": "dagger_climbing_1789683122426.jpg",
    "swordlet": "swordlet_1789683131960.jpg",
    "gladiators_friend": "gladiators_friend_1789683142682.jpg",
    "tortoise_blades": "tortoise_blades_1789683152614.jpg",
    "talid": "talid_1789683165875.jpg",
    "wrist_razor": "wrist_razor_1789683177609.jpg",
    "polearm_military_fork": "military_fork_1789683188985.jpg",
    "polearm_ranseur": "ranseur_1789683200221.jpg",
    "polearm_partisan": "partisan_1789683212577.jpg",
}

os.makedirs(raw_weapons_dir, exist_ok=True)
os.makedirs(output_dir, exist_ok=True)

# 1. Copy raw images
for name, filename in new_weapons.items():
    src = os.path.join(brain_dir, filename)
    dst = os.path.join(raw_weapons_dir, f"{name}.jpg")
    if os.path.exists(src):
        shutil.copyfile(src, dst)
        print(f"Copied {filename} -> {dst}")
    else:
        print(f"WARNING: Missing {src}")

# 2. ImageMagick transparency pipeline
for name in new_weapons.keys():
    raw_path = os.path.join(raw_weapons_dir, f"{name}.jpg")
    out_path = os.path.join(output_dir, f"{name}.png")
    
    # Sample background color at (5, 5)
    res = subprocess.run(["magick", raw_path, "-format", "%[pixel:p{5,5}]", "info:"], capture_output=True, text=True)
    bg_color = res.stdout.strip()
    if not bg_color:
        bg_color = "#00FF00"
    
    cmd = [
        "magick", raw_path,
        "-fuzz", "10%",
        "-transparent", bg_color,
        "-channel", "A",
        "-morphology", "Erode", "Diamond:2",
        "+channel", out_path
    ]
    subprocess.run(cmd, check=True)
    print(f"Rendered transparent RGBA sprite: {out_path} (sampled bg: {bg_color})")

print(f"\nSuccessfully rendered and saved all {len(new_weapons)} weapon textures to {output_dir}!")
