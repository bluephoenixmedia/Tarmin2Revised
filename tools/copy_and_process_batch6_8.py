import os
import shutil
import subprocess

brain_dir = r"C:\Users\denni\.gemini\antigravity-ide\brain\b803c96d-ed89-42ad-81d4-66b7f16ba4d8"
raw_weapons_dir = r"tools\raw_weapons"
output_dir = r"assets\images\weapons"

new_weapons = {
    "whip": "whip_1789603547198.jpg",
    "net": "net_1789603557305.jpg",
    "caltrop": "caltrop_1789603567737.jpg",
    "blowgun": "blowgun_1789603577782.jpg",
    "harpoon_two_handed": "harpoon_two_handed_1789603587852.jpg",
    "mancatcher": "mancatcher_1789603598990.jpg",
    "lance_jousting": "lance_jousting_1789603609102.jpg",
    "lance_cavalry": "lance_cavalry_1789603631463.jpg",
    "pick_horseman": "pick_horseman_1789603641239.jpg",
    "belaying_pin": "belaying_pin_1789603652609.jpg",
    "lasso": "lasso_1789603664367.jpg",
    "razor": "razor_1789603678752.jpg",
}

os.makedirs(raw_weapons_dir, exist_ok=True)
os.makedirs(output_dir, exist_ok=True)

# 1. Copy to raw_weapons/<name>.jpg
for name, filename in new_weapons.items():
    src = os.path.join(brain_dir, filename)
    dst = os.path.join(raw_weapons_dir, f"{name}.jpg")
    if os.path.exists(src):
        shutil.copyfile(src, dst)
        print(f"Copied {filename} -> {dst}")
    else:
        print(f"WARNING: Missing {src}")

# 2. Run ImageMagick transparency pipeline
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
