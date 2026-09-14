import os
import shutil
import subprocess

brain_dir = r"C:\Users\denni\.gemini\antigravity-ide\brain\b803c96d-ed89-42ad-81d4-66b7f16ba4d8"
raw_weapons_dir = r"tools\raw_weapons"
output_dir = r"assets\images\weapons"

new_weapons = {
    "flail_footman": "flail_footman_1789419827397.jpg",
    "flail_horseman": "flail_horseman_1789419838858.jpg",
    "morning_star_double_ball": "morning_star_double_ball_1789419850506.jpg",
    "club_great": "club_great_1789419861839.jpg",
    "dagger_bone": "dagger_bone_1789419873062.jpg",
    "dagger_stone": "dagger_stone_1789419885622.jpg",
    "knife_throwing": "knife_throwing_1789419899120.jpg",
    "knife_bone": "knife_bone_1789419911978.jpg",
    "knife_stone": "knife_stone_1789419923827.jpg",
    "axe_two_handed_battle": "axe_two_handed_battle_1789419938205.jpg",
    "hatchet": "hatchet_1789419951986.jpg",
    "trident": "trident_1789419969681.jpg",
    "polearm_guisarme": "polearm_guisarme_1789419987625.jpg"
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
# magick <src> -fuzz 10% -transparent %[pixel:p{5,5}] -channel A -morphology Erode Diamond:2 +channel <dest>
for name in new_weapons.keys():
    raw_path = os.path.join(raw_weapons_dir, f"{name}.jpg")
    out_path = os.path.join(output_dir, f"{name}.png")
    
    # Get background color sample at 5,5
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
    print(f"Rendered transparent RGBA sprite: {out_path} (bg: {bg_color})")

print("\nAll 13 new weapon sprites processed into assets/images/weapons/!")
