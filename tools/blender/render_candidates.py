"""
render_candidates.py

Renders a set of candidate models as lit thumbnails, so the art for a biome can
be picked by eye before it goes into bake_forest_models.py. Uses the bake's own
import, texturing, lighting and framing, so a thumbnail looks like the bake.

Run:
    "C:/Program Files/Blender Foundation/Blender 5.1/blender.exe" ^
        --background --python tools/blender/render_candidates.py -- <set> [models_dir] [game_assets_dir]

Writes build/candidates/<set>/<index>_<label>.png; tools/candidate_sheet.py
lays them out as a numbered sheet.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import bake_forest_models as bake  # noqa: E402
import bpy  # noqa: E402

THUMB = 160

GOBLIN = bake.GOBLIN
ADVENTURE = bake.ADVENTURE
ALPINE = bake.ALPINE
GENERIC = "POLYGON_Generic_SourceFiles_v3"
GENERIC_ATLAS = (GENERIC, "Textures/Alts/Generic_01_A.png")

# (label, source, fallback texture or None when the model brings its own)
SETS = {
    "desert": [
        ("palm_tall", ("models", "trees/tall_palm_Tree_Textured.glb"), None),
        ("palm_huge", ("models", "trees/huge_palm_Tree_Textured.glb"), None),
        ("palm_date", ("models", "trees/date_palm_Tree_Textured.glb"), None),
        ("desert_tree", ("models", "forest_other/small_desert_Tree_Textured.glb"), None),
        ("gen_dead_01", (GENERIC, "FBX/SM_Gen_Env_Tree_Dead_01.fbx"), GENERIC_ATLAS),
        ("gen_dead_02", (GENERIC, "FBX/SM_Gen_Env_Tree_Dead_02.fbx"), GENERIC_ATLAS),
        ("gen_dead_03", (GENERIC, "FBX/SM_Gen_Env_Tree_Dead_03.fbx"), GENERIC_ATLAS),
        ("adv_dead_01", (ADVENTURE, "FBX/SM_Env_TreeDead_01.fbx"), bake.ADVENTURE_ATLAS),
        ("adv_dead_02", (ADVENTURE, "FBX/SM_Env_TreeDead_02.fbx"), bake.ADVENTURE_ATLAS),
        ("gen_cliff_pillar", (GENERIC, "FBX/SM_Gen_Env_Cliff_Pillar_01.fbx"), GENERIC_ATLAS),
        ("gen_cliff_arch_01", (GENERIC, "FBX/SM_Gen_Env_Cliff_Arch_01.fbx"), GENERIC_ATLAS),
        ("gen_cliff_arch_02", (GENERIC, "FBX/SM_Gen_Env_Cliff_Arch_02.fbx"), GENERIC_ATLAS),
        ("alp_cliff_arch", (ALPINE, "FBX/Environment/SM_Env_Rock_Cliff_Arch_01.fbx"), bake.ALPINE_ATLAS),
        ("alp_cliff_01", (ALPINE, "FBX/Environment/SM_Env_Rock_Cliff_01.fbx"), bake.ALPINE_ATLAS),
        ("alp_cliff_03", (ALPINE, "FBX/Environment/SM_Env_Rock_Cliff_03.fbx"), bake.ALPINE_ATLAS),
        ("gen_rock_01", (GENERIC, "FBX/SM_Gen_Env_Rock_01.fbx"), GENERIC_ATLAS),
        ("gen_rock_04", (GENERIC, "FBX/SM_Gen_Env_Rock_04.fbx"), GENERIC_ATLAS),
        ("gen_rock_07", (GENERIC, "FBX/SM_Gen_Env_Rock_07.fbx"), GENERIC_ATLAS),
        ("gob_rock_02", (GOBLIN, "FBX/Environment/SM_Env_Rock_02.fbx"), bake.GOBLIN_ATLAS),
        ("gob_rock_05", (GOBLIN, "FBX/Environment/SM_Env_Rock_05.fbx"), bake.GOBLIN_ATLAS),
        ("adv_rock_03", (ADVENTURE, "FBX/SM_Env_Rock_03.fbx"), bake.ADVENTURE_ATLAS),
        ("gob_bones_01", (GOBLIN, "FBX/Environment/SM_Env_Bones_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_bones_03", (GOBLIN, "FBX/Environment/SM_Env_Bones_03.fbx"), bake.GOBLIN_ATLAS),
        ("gob_bones_05", (GOBLIN, "FBX/Environment/SM_Env_Bones_05.fbx"), bake.GOBLIN_ATLAS),
        ("gob_bone_rib", (GOBLIN, "FBX/Buildings/SM_Bld_Part_Bone_Rib_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_bone_spine", (GOBLIN, "FBX/Buildings/SM_Bld_Part_Bone_Spine_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_titan_skull", (GOBLIN, "FBX/Buildings/SM_Bld_Part_Bone_Skull_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_skull_01", (GOBLIN, "FBX/Props/SM_Prop_Skull_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_skull_pile_01", (GOBLIN, "FBX/Props/SM_Prop_Skull_Pile_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_skeleton_01", (GOBLIN, "FBX/Props/SM_Prop_Skeleton_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_ruins_arch", (GOBLIN, "FBX/Props/SM_Prop_Ruins_Archway_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_ruins_pillar_02", (GOBLIN, "FBX/Props/SM_Prop_Ruins_Pillar_02.fbx"), bake.GOBLIN_ATLAS),
        ("gob_ruins_wall", (GOBLIN, "FBX/Props/SM_Prop_Ruins_Wall_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_idol", (GOBLIN, "FBX/Props/SM_Prop_Idol_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_brazier", (GOBLIN, "FBX/Props/SM_Prop_Brazier_01.fbx"), bake.GOBLIN_ATLAS),
        ("gen_statue_01", (GENERIC, "FBX/SM_Gen_Prop_Statue_01.fbx"), GENERIC_ATLAS),
        ("gen_statue_03", (GENERIC, "FBX/SM_Gen_Prop_Statue_03.fbx"), GENERIC_ATLAS),
        ("gen_statue_05", (GENERIC, "FBX/SM_Gen_Prop_Statue_05.fbx"), GENERIC_ATLAS),
        ("adv_well", (ADVENTURE, "FBX/SM_Bld_Well_01.fbx"), bake.ADVENTURE_ATLAS),
        ("adv_reeds_01", (ADVENTURE, "FBX/SM_Env_Reeds_01.fbx"), bake.ADVENTURE_ATLAS),
        ("adv_reeds_02", (ADVENTURE, "FBX/SM_Env_Reeds_02.fbx"), bake.ADVENTURE_ATLAS),
        ("adv_pebble_02", (ADVENTURE, "FBX/SM_Env_Pebble_02.fbx"), bake.ADVENTURE_ATLAS),
        ("adv_pebble_05", (ADVENTURE, "FBX/SM_Env_Pebble_05.fbx"), bake.ADVENTURE_ATLAS),
        ("gob_bush_01", (GOBLIN, "FBX/Environment/SM_Env_Bush_01.fbx"), bake.GOBLIN_ATLAS),
        ("gob_bush_03", (GOBLIN, "FBX/Environment/SM_Env_Bush_03.fbx"), bake.GOBLIN_ATLAS),
        ("gen_bush_02", (GENERIC, "FBX/SM_Gen_Env_Bush_02.fbx"), GENERIC_ATLAS),
    ],
}


def find(path):
    """Synty packs nest their FBX folders inconsistently; fall back to a search by file name."""
    if os.path.exists(path):
        return path
    root = path
    name = os.path.basename(path)
    while root and not os.path.isdir(root):
        root = os.path.dirname(root)
    pack = root
    for _ in range(3):
        if os.path.basename(os.path.dirname(pack)) == "game_assets":
            break
        pack = os.path.dirname(pack)
    for dirpath, _, files in os.walk(pack):
        if name in files:
            return os.path.join(dirpath, name)
    return path


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    set_name = argv[0] if argv else "desert"
    models = argv[1] if len(argv) > 1 else os.path.join(bake.REPO, "assets", "models")
    packs = argv[2] if len(argv) > 2 else os.path.join(bake.REPO, "docs", "game_assets")
    out_dir = os.path.join(bake.REPO, "build", "candidates", set_name)
    os.makedirs(out_dir, exist_ok=True)

    for i, (label, source, fallback) in enumerate(SETS[set_name]):
        path = find(bake.resolve(source, models, packs))
        if not os.path.exists(path):
            print(f"[{i:03d}] MISSING {path}")
            continue
        bake.reset_scene((THUMB, THUMB))
        bpy.context.scene.render.resolution_x = THUMB
        bpy.context.scene.render.resolution_y = THUMB
        meshes = bake.import_model(path)
        if not meshes:
            print(f"[{i:03d}] no meshes in {path}")
            continue
        if fallback is not None:
            bake.assign_textures(meshes, {}, fallback, models, packs)
        bake.add_lighting(-40.0)
        bake.frame_camera(meshes, (THUMB, THUMB))
        bpy.context.scene.render.filepath = os.path.join(out_dir, f"{i:03d}_{label}.png")
        bpy.ops.render.render(write_still=True)
        print(f"[{i:03d}] {label}")


if __name__ == "__main__":
    main()
