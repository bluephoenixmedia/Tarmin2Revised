"""
bake_forest_models.py

Renders the forest's models into raw, lit billboard frames. These are not game
art yet: tools/stylize_forest_sprites.py turns them into the palette-locked
pixel-art PNGs the forest uses.

The frames are lit, a key and a fill sun, so the stylize pass has real form
to quantize; the unlit flat-colour bakes this replaced read as paper cutouts. Trees render in two setups (key from the left,
key from the right) and mirroring at runtime doubles that again.

Every frame of a kind shares one canvas with the model's base on the bottom
edge, so the game can size every billboard of that kind from one aspect ratio.

Sources:
  * .glb models carry their own textures (trees, boulders).
  * Synty .fbx models need theirs assigned: each entry maps material slot
    names to texture files, with a fallback for every other slot.

Run:
    "C:/Program Files/Blender Foundation/Blender 5.1/blender.exe" ^
        --background --python tools/blender/bake_forest_models.py -- [models_dir] [game_assets_dir]

models_dir defaults to assets/models (trees/, forest_other/); game_assets_dir
to docs/game_assets (the Synty packs). Both are gitignored source files; only
the stylized PNGs are committed.
"""

import math
import os
import sys
from collections import namedtuple

import bpy
from mathutils import Vector

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RAW_DIR = os.path.join(REPO, "build", "forest_raw")

# Art canvas per kind, in final sprite pixels, rendered at RAW_SCALE so the
# stylize pass can downsample. Must match the aspects in ForestChunkGenerator.
# A "sized" model takes its canvas from its in-game billboard size instead, at
# the trees' density, so it never stretches and its pixels match theirs.
CANVAS = {
    "tree": (648, 864),
    "prop": (240, 192),
}
PIXELS_PER_UNIT = 96
RAW_SCALE = 2

LIGHTINGS = {
    "tree": [("_l", -40.0), ("_r", 40.0)],
    "prop": [("", -40.0)],
    "sized": [("", -40.0)],
}

# source is ("models", path) under models_dir or (pack, path) under game_assets_dir.
# slots maps Synty material slot names to textures; None means the model brings its own.
# size is the billboard's (width, height) in world units, for kind "sized".
Model = namedtuple("Model", "name kind source slots fallback size", defaults=(None, None, None))
KEY_ELEVATION = 50.0

ALPINE = "POLYGON_NatureBiomes_AlpineMountain_SourceFiles_v3"
ADVENTURE = "POLYGON_Adventure_Pack_SourceFiles_v6"
ALPINE_ATLAS = (ALPINE, "Textures/PolygonNatureBiomesS2_Alpine_Texture_01.png")
ADVENTURE_ATLAS = (ADVENTURE, "Textures/PolyAdventureTexture_01.png")
ALPINE_BUSH = (ALPINE, "Textures/Alpine_Bush_02.tga")
GENERIC = "POLYGON_Generic_SourceFiles_v3"
GENERIC_ATLAS = (GENERIC, "Textures/Alts/Generic_01_A.png")
KENNEY_NATURE = "Kenney Game Assets 1 version 42/3D assets/Nature Kit/Models/glTF format"
GOBLIN = "POLYGON_Goblin_War_Camp_SourceFiles_v3"
VIKING = "POLYGON_Viking_Realm_SourceFiles_v3/SourceFiles"
GOBLIN_ATLAS = (GOBLIN, "Textures/Alts/PolygonGoblinWarCamp_Texture_01_A.png")
VIKING_ATLAS = (VIKING, "Textures/Alts/PolygonVikingRealm_Texture_01_A.png")

MODELS = [
    Model("tree_pine_a", "tree", ("models", "trees/pine2.glb")),
    Model("tree_pine_b", "tree", ("models", "trees/pine3.glb")),
    Model("tree_pine_c", "tree", ("models", "trees/pine4.glb")),
    Model("tree_pine_d", "tree", ("models", "trees/pine5.glb")),
    Model("tree_dead_a", "tree", ("models", "forest_other/dead_tree.glb")),
    Model("tree_dead_b", "tree", ("models", "forest_other/dead_tree2.glb")),
    Model("tree_dead_c", "tree", ("models", "forest_other/dead_tree3.glb")),
    Model("tree_dead_d", "tree", ("models", "forest_other/dead_tree4.glb")),
    Model("tree_dead_e", "tree", ("models", "forest_other/dead_tree5.glb")),

    Model("rock_boulder_01", "prop", ("models", "forest_other/rock1.glb")),
    Model("rock_boulder_02", "prop", ("models", "forest_other/rock2.glb")),
    Model("rock_boulder_03", "prop", ("models", "forest_other/rock3.glb")),

    # One material each, named BerryBush_01_MAT in the FBX whatever the material list says.
    Model("bush_01", "prop", (ALPINE, "FBX/Environment/SM_Env_Bush_02.fbx"), {}, ALPINE_BUSH),
    Model("bush_02", "prop", (ALPINE, "FBX/Environment/SM_Env_Bush_02_Alt.fbx"), {}, ALPINE_BUSH),
    Model("stump_pine_01", "prop", (ALPINE, "FBX/Environment/SM_Env_Pine_Stump_01.fbx"), {}, ALPINE_ATLAS),
    Model("log_fallen_01", "prop", (ADVENTURE, "FBX/SM_Env_TreeLog_01.fbx"), {}, ADVENTURE_ATLAS),
    Model("log_pile_01", "prop", (ADVENTURE, "FBX/SM_Prop_Logpile_01.fbx"), {}, ADVENTURE_ATLAS),

    # Forest variants of the shared landmark props, sized from props.json. Written to
    # images/forest/props/<id>.png; other themes keep their own art.
    Model("landmark_campfire", "sized", (GOBLIN, "FBX/Props/SM_Prop_Camp_Fire_01.fbx"), {}, GOBLIN_ATLAS, (1.0, 0.7)),
    Model("landmark_camp_tent", "sized", (GOBLIN, "FBX/Buildings/SM_Bld_Tent_Medium_01.fbx"), {}, GOBLIN_ATLAS, (1.6, 1.4)),
    Model("landmark_cairn", "sized", (VIKING, "FBX/SM_Prop_Cairn_01.fbx"), {}, VIKING_ATLAS, (0.8, 1.0)),
    Model("landmark_runestone", "sized", (VIKING, "FBX/SM_Prop_RuneStone_01.fbx"), {}, VIKING_ATLAS, (0.8, 1.3)),
    Model("landmark_ruined_pillar", "sized", (GOBLIN, "FBX/Props/SM_Prop_Ruins_Pillar_01.fbx"), {}, GOBLIN_ATLAS, (0.9, 1.9)),
    Model("landmark_rubble_pile", "sized", (GOBLIN, "FBX/Props/SM_Prop_Ruins_Damaged_01.fbx"), {}, GOBLIN_ATLAS, (1.1, 0.6)),
    Model("landmark_grave_mound", "sized", (GOBLIN, "FBX/Environment/SM_Env_Swamp_Mound_01.fbx"), {}, GOBLIN_ATLAS, (1.1, 0.6)),
    Model("landmark_bramble", "sized", (ALPINE, "FBX/Environment/SM_Env_Bush_01.fbx"), {},
          (ALPINE, "Textures/Alpine_Bush_01.tga"), (1.1, 1.1)),

    # Ground scatter: render-only decoration on walkable tiles.
    Model("scatter_grass", "sized", (ALPINE, "FBX/Environment/SM_Env_Grass_01.fbx"), {},
          (ALPINE, "Textures/Alpine_Grass_01.tga"), (0.6, 0.4)),
    Model("scatter_flowers", "sized", (ALPINE, "FBX/Environment/SM_Env_Flowers_01.fbx"), {},
          (ALPINE, "Textures/Flowers_01.tga"), (0.6, 0.4)),
    Model("scatter_moss_01", "sized", (ALPINE, "FBX/Environment/SM_Env_Moss_Lumps_01.fbx"), {}, ALPINE_ATLAS, (0.7, 0.35)),
    Model("scatter_moss_02", "sized", (ALPINE, "FBX/Environment/SM_Env_Moss_Lumps_02.fbx"), {}, ALPINE_ATLAS, (0.7, 0.35)),
    Model("scatter_branch_01", "sized", (ALPINE, "FBX/Environment/SM_Env_Branch_01.fbx"), {}, ALPINE_ATLAS, (0.8, 0.3)),
    Model("scatter_branch_02", "sized", (ALPINE, "FBX/Environment/SM_Env_Branch_02.fbx"), {}, ALPINE_ATLAS, (0.8, 0.3)),
    Model("scatter_mushroom", "sized", (ADVENTURE, "FBX/SM_Env_Mushroom_01.fbx"), {}, ADVENTURE_ATLAS, (0.4, 0.4)),
    Model("scatter_glowcap", "sized", ("models", "forest_other/luminescent_plants.glb"), None, None, (0.6, 0.5)),

    # Desert: written to images/desert/. Kenney's cacti carry flat material colours; the
    # boulder is the forest's rock model, recoloured into sandstone by the stylize pass.
    Model("desert_cactus_tall", "sized", (KENNEY_NATURE, "cactus_tall.gltf"), None, None, (0.9, 2.4)),
    Model("desert_cactus_large", "sized", (KENNEY_NATURE, "cactus_large.gltf"), None, None, (1.4, 2.2)),
    Model("desert_cactus_short", "sized", (KENNEY_NATURE, "cactus_short.gltf"), None, None, (0.9, 1.2)),
    Model("desert_rock_01", "sized", ("models", "forest_other/rock2.glb"), None, None, (1.2, 1.0)),
    Model("desert_rock_02", "sized", (GENERIC, "Models/SM_Gen_Env_Rock_04.fbx"), {}, GENERIC_ATLAS, (1.2, 1.0)),
    Model("desert_rock_03", "sized", (GOBLIN, "FBX/Environment/SM_Env_Rock_02.fbx"), {}, GOBLIN_ATLAS, (1.2, 1.0)),
    Model("desert_tree_dead_01", "sized", (GENERIC, "Models/SM_Gen_Env_Tree_Dead_03.fbx"), {}, GENERIC_ATLAS, (1.8, 3.2)),
    Model("desert_tree_dead_02", "sized", (ADVENTURE, "FBX/SM_Env_TreeDead_01.fbx"), {}, ADVENTURE_ATLAS, (1.8, 3.2)),
    Model("desert_tree_agave", "sized", ("models", "forest_other/small_desert_Tree_Textured.glb"), None, None, (1.6, 1.6)),
    Model("desert_palm_tall", "sized", ("models", "trees/tall_palm_Tree_Textured.glb"), None, None, (2.0, 3.6)),
    Model("desert_palm_huge", "sized", ("models", "trees/huge_palm_Tree_Textured.glb"), None, None, (2.6, 4.0)),
    Model("desert_palm_date", "sized", ("models", "trees/date_palm_Tree_Textured.glb"), None, None, (2.4, 3.2)),
    Model("desert_reeds_01", "sized", (ADVENTURE, "FBX/SM_Env_Reeds_01.fbx"), {}, ADVENTURE_ATLAS, (0.6, 0.9)),
    Model("desert_reeds_02", "sized", (ADVENTURE, "FBX/SM_Env_Reeds_02.fbx"), {}, ADVENTURE_ATLAS, (0.6, 0.9)),
    Model("desert_hoodoo", "sized", (GENERIC, "Models/SM_Gen_Env_Cliff_Pillar_01.fbx"), {}, GENERIC_ATLAS, (1.1, 3.6)),
    Model("desert_arch", "sized", (GENERIC, "Models/SM_Gen_Env_Cliff_Arch_02.fbx"), {}, GENERIC_ATLAS, (2.8, 2.2)),
    Model("desert_bones_01", "sized", (GOBLIN, "FBX/Environment/SM_Env_Bones_01.fbx"), {}, GOBLIN_ATLAS, (0.8, 0.6)),
    Model("desert_bones_rib", "sized", (GOBLIN, "FBX/Buildings/SM_Bld_Part_Bone_Rib_01.fbx"), {}, GOBLIN_ATLAS, (0.9, 0.6)),
    Model("desert_skull_pile", "sized", (GOBLIN, "FBX/Props/SM_Prop_Skull_Pile_01.fbx"), {}, GOBLIN_ATLAS, (0.9, 0.5)),
    Model("desert_beast_skull", "sized", (GOBLIN, "FBX/Buildings/SM_Bld_Part_Bone_Skull_01.fbx"), {}, GOBLIN_ATLAS, (1.6, 1.2)),
    Model("desert_titan_skull", "sized", (GOBLIN, "FBX/Props/SM_Prop_Skull_01.fbx"), {}, GOBLIN_ATLAS, (2.6, 2.4)),
    Model("desert_ruin_pillar", "sized", (GOBLIN, "FBX/Props/SM_Prop_Ruins_Pillar_02.fbx"), {}, GOBLIN_ATLAS, (0.8, 2.2)),
    Model("desert_ruin_idol", "sized", (GOBLIN, "FBX/Props/SM_Prop_Idol_01.fbx"), {}, GOBLIN_ATLAS, (0.9, 1.4)),
    Model("desert_ruin_arch", "sized", (GOBLIN, "FBX/Props/SM_Prop_Ruins_Archway_01.fbx"), {}, GOBLIN_ATLAS, (2.4, 1.6)),
    Model("desert_campfire", "sized", (GOBLIN, "FBX/Props/SM_Prop_Camp_Fire_01.fbx"), {}, GOBLIN_ATLAS, (1.0, 0.7)),
    Model("desert_camp_tent", "sized", (GOBLIN, "FBX/Buildings/SM_Bld_Tent_Medium_01.fbx"), {}, GOBLIN_ATLAS, (1.6, 1.4)),
    Model("desert_scrub_01", "sized", (GENERIC, "Models/SM_Gen_Env_Bush_02.fbx"), {}, GENERIC_ATLAS, (0.7, 0.45)),
    Model("desert_scrub_02", "sized", (GOBLIN, "FBX/Environment/SM_Env_Bush_03.fbx"), {}, GOBLIN_ATLAS, (0.7, 0.45)),

    # Lakelands: written to images/lakelands/. Reeds, lilypads, docks, boat wrecks, shrines, swamp mounds.
    Model("lakelands_reeds_01", "sized", (GOBLIN, "FBX/Environment/SM_Env_Reeds_01.fbx"), {}, GOBLIN_ATLAS, (0.7, 1.0)),
    Model("lakelands_reeds_02", "sized", (GOBLIN, "FBX/Environment/SM_Env_Reeds_02.fbx"), {}, GOBLIN_ATLAS, (0.7, 1.0)),
    Model("lakelands_swamp_grass_01", "sized", (GOBLIN, "FBX/Environment/SM_Env_Swamp_Grass_01.fbx"), {}, GOBLIN_ATLAS, (0.6, 0.45)),
    Model("lakelands_swamp_grass_02", "sized", (GOBLIN, "FBX/Environment/SM_Env_Swamp_Grass_02.fbx"), {}, GOBLIN_ATLAS, (0.6, 0.45)),
    Model("lakelands_lilypads_01", "sized", (GENERIC, "Models/SM_Gen_Env_Lilypads_01.fbx"), {}, GENERIC_ATLAS, (0.8, 0.3)),
    Model("lakelands_lilypads_02", "sized", (GENERIC, "Models/SM_Gen_Env_Lilypads_02.fbx"), {}, GENERIC_ATLAS, (0.8, 0.3)),
    Model("lakelands_dock_post", "sized", (VIKING, "FBX/SM_Bld_Dock_Pillar_01.fbx"), {}, VIKING_ATLAS, (0.8, 1.5)),
    Model("lakelands_dock_ramp", "sized", (VIKING, "FBX/SM_Bld_Dock_Wood_End_01.fbx"), {}, VIKING_ATLAS, (1.4, 0.9)),
    Model("lakelands_boat_wreck", "sized", (VIKING, "FBX/SM_Veh_Boat_Small_01.fbx"), {}, VIKING_ATLAS, (2.2, 1.0)),
    Model("lakelands_shrine_01", "sized", (GOBLIN, "FBX/Props/SM_Prop_Shrine_01.fbx"), {}, GOBLIN_ATLAS, (1.2, 1.8)),
    Model("lakelands_statue_01", "sized", (VIKING, "FBX/SM_Prop_Statue_01.fbx"), {}, VIKING_ATLAS, (1.0, 2.0)),
    Model("lakelands_mound_01", "sized", (GOBLIN, "FBX/Environment/SM_Env_Swamp_Mound_01.fbx"), {}, GOBLIN_ATLAS, (1.3, 0.7)),
    Model("lakelands_bones_rib", "sized", (GOBLIN, "FBX/Buildings/SM_Bld_Part_Bone_Rib_01.fbx"), {}, GOBLIN_ATLAS, (1.0, 0.7)),
    Model("lakelands_beast_skull", "sized", (GOBLIN, "FBX/Buildings/SM_Bld_Part_Bone_Skull_01.fbx"), {}, GOBLIN_ATLAS, (1.6, 1.2)),
    Model("lakelands_glowplant", "sized", ("models", "forest_other/luminescent_plants.glb"), None, None, (0.8, 0.7)),
    Model("lakelands_underwater_plant", "sized", ("models", "forest_other/lowpoly_marine_plant.glb"), None, None, (0.7, 1.4)),
    Model("lakelands_tall_grass", "sized", ("models", "forest_other/common_grass.glb"), None, None, (0.8, 1.1)),
    Model("lakelands_swamp_plant", "sized", ("models", "forest_other/plant.glb"), None, None, (0.9, 0.35)),
    Model("lakelands_swamp_fern", "sized", ("models", "forest_other/tropical_plant_2.glb"), None, None, (1.3, 1.3)),
    Model("lakelands_tropical_bush", "sized", ("models", "forest_other/tropical_plant_bush.glb"), None, None, (1.5, 0.55)),
    Model("lakelands_water_kelp", "sized", ("models", "forest_other/underwater_plant_pack.glb"), None, None, (1.1, 1.1)),
    Model("lakelands_root_01", "sized", (ALPINE, "FBX/Environment/SM_Env_Branch_01.fbx"), {}, ALPINE_ATLAS, (0.9, 0.4)),
    Model("lakelands_rock_moss", "sized", ("models", "forest_other/rock1.glb"), None, None, (1.1, 0.9)),
    Model("lakelands_tree_dead_01", "sized", (ADVENTURE, "FBX/SM_Env_TreeDead_01.fbx"), {}, ADVENTURE_ATLAS, (1.9, 3.2)),
    Model("lakelands_tree_dead_02", "sized", (GENERIC, "Models/SM_Gen_Env_Tree_Dead_01.fbx"), {}, GENERIC_ATLAS, (1.9, 3.2)),
    Model("lakelands_tree_cypress_01", "sized", ("models", "trees/tall_cypress_Tree_Textured.glb"), None, None, (2.0, 3.8)),
    Model("lakelands_tree_dead_03", "sized", ("models", "forest_other/Dead_Tree_Textured.glb"), None, None, (2.2, 3.4)),
    Model("lakelands_tree_willow_01", "sized", ("models", "trees/dense_jungle_Tree_Textured.glb"), None, None, (2.4, 3.6)),
    Model("lakelands_tree_snag_01", "sized", ("models", "trees/dead_tree3.glb"), None, None, (1.9, 3.2)),
]


def source_dirs():
    argv = [a for a in (sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []) if not a.startswith("--")]
    models = argv[0] if len(argv) > 0 else os.path.join(REPO, "assets", "models")
    packs = argv[1] if len(argv) > 1 else os.path.join(REPO, "docs", "game_assets")
    return models, packs


def resolve(ref, models, packs):
    root, rel = ref
    return os.path.join(models if root == "models" else os.path.join(packs, root), rel)


def canvas_of(model):
    if model.kind == "sized":
        w, h = model.size
        return max(8, round(w * PIXELS_PER_UNIT)), max(8, round(h * PIXELS_PER_UNIT))
    return CANVAS[model.kind]


def reset_scene(canvas):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue
    w, h = canvas
    scene.render.resolution_x = w * RAW_SCALE
    scene.render.resolution_y = h * RAW_SCALE
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.view_settings.view_transform = 'Standard'

    world = bpy.data.worlds.new("ForestWorld")
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs[0].default_value = (0.55, 0.62, 0.58, 1.0)
        bg.inputs[1].default_value = 0.35


def add_lighting(azimuth_deg):
    def sun(name, energy, elevation, azimuth):
        data = bpy.data.lights.new(name=name, type='SUN')
        data.energy = energy
        obj = bpy.data.objects.new(name, data)
        bpy.context.scene.collection.objects.link(obj)
        # A sun points down its local -Z; tilt it off vertical, then swing it round.
        obj.rotation_euler = (math.radians(90 - elevation), 0, math.radians(azimuth))

    sun("Key", 3.2, KEY_ELEVATION, azimuth_deg)
    sun("Fill", 0.9, 25.0, azimuth_deg + 160.0)


def import_model(path):
    before = set(bpy.data.objects)
    if path.lower().endswith(".fbx"):
        bpy.ops.import_scene.fbx(filepath=path)
    else:
        bpy.ops.import_scene.gltf(filepath=path)
    meshes = []
    for obj in set(bpy.data.objects) - before:
        if obj.type != 'MESH':
            continue
        name = obj.name.lower()
        # Synty ships lower LODs and shadow cards alongside the mesh; keep LOD0 only.
        if ("_lod" in name and "_lod0" not in name) or "_shadow" in name:
            bpy.data.objects.remove(obj, do_unlink=True)
            continue
        meshes.append(obj)
    return meshes


def textured_material(name, image_path):
    """Lit material with the texture's alpha as a cutout."""
    mat = bpy.data.materials.new(name=name)
    mat.use_nodes = True
    tree = mat.node_tree
    tree.nodes.clear()
    out = tree.nodes.new("ShaderNodeOutputMaterial")
    bsdf = tree.nodes.new("ShaderNodeBsdfPrincipled")
    tex = tree.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(image_path, check_existing=True)
    tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    tree.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    bsdf.inputs["Roughness"].default_value = 0.9
    tree.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    return mat


def assign_textures(meshes, slots, fallback, models, packs):
    """Point each material slot at its Synty texture; unlisted slots take the atlas."""
    made = {}
    for obj in meshes:
        for i, slot in enumerate(obj.material_slots):
            slot_name = slot.material.name.split(".")[0] if slot.material else ""
            ref = slots.get(slot_name, fallback)
            if ref not in made:
                made[ref] = textured_material(f"{slot_name or 'atlas'}_lit", resolve(ref, models, packs))
            obj.material_slots[i].material = made[ref]
        if not obj.material_slots:
            if fallback not in made:
                made[fallback] = textured_material("atlas_lit", resolve(fallback, models, packs))
            obj.data.materials.append(made[fallback])


def bounds_of(meshes):
    min_v = Vector((1e9, 1e9, 1e9))
    max_v = Vector((-1e9, -1e9, -1e9))
    for obj in meshes:
        for corner in obj.bound_box:
            world = obj.matrix_world @ Vector(corner)
            for i in range(3):
                min_v[i] = min(min_v[i], world[i])
                max_v[i] = max(max_v[i], world[i])
    return min_v, max_v


def frame_camera(meshes, canvas):
    """Front orthographic view, base on the bottom edge, model fitted to the canvas."""
    bpy.context.view_layer.update()
    min_v, max_v = bounds_of(meshes)
    size = max_v - min_v
    w, h = canvas
    aspect = w / h
    # ortho_scale spans the frame's longer side.
    vertical = max(size.z, size.x / aspect) * 1.02
    longest = vertical if h >= w else vertical * aspect

    cam_data = bpy.data.cameras.new("ForestCam")
    cam_data.type = 'ORTHO'
    cam_data.ortho_scale = longest
    cam = bpy.data.objects.new("ForestCam", cam_data)
    bpy.context.scene.collection.objects.link(cam)
    bpy.context.scene.camera = cam

    centre_x = (min_v.x + max_v.x) * 0.5
    distance = max(size.x, size.y, size.z, 1.0) * 4.0
    cam.location = (centre_x, min_v.y - distance, min_v.z + vertical * 0.5)
    cam.rotation_euler = (math.radians(90), 0, 0)
    cam_data.clip_end = distance * 4.0


def main():
    models, packs = source_dirs()
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    only_arg = next((a.split("=", 1)[1] for a in argv if a.startswith("--only=")), None)
    if only_arg is None:
        only_arg = os.environ.get("FOREST_BAKE_ONLY", "")
    only_tokens = set(a.strip() for a in only_arg.split(",") if a.strip())

    def matches_only(name):
        if not only_tokens:
            return True
        for t in only_tokens:
            if name == t or name.startswith(t):
                return True
        return False

    os.makedirs(RAW_DIR, exist_ok=True)
    jobs = [(m, suffix, azimuth)
            for m in MODELS if matches_only(m.name)
            for suffix, azimuth in LIGHTINGS[m.kind]]
    print(f"--- Baking {len(jobs)} raw frames with Blender {bpy.app.version_string} ---")

    for i, (model, suffix, azimuth) in enumerate(jobs, 1):
        path = resolve(model.source, models, packs)
        out = os.path.join(RAW_DIR, f"{model.name}{suffix}.png")
        if not os.path.exists(path):
            print(f"[{i}/{len(jobs)}] MISSING {path}")
            continue
        canvas = canvas_of(model)
        reset_scene(canvas)
        meshes = import_model(path)
        if not meshes:
            print(f"[{i}/{len(jobs)}] no meshes in {path}")
            continue
        if model.slots is not None:
            assign_textures(meshes, model.slots, model.fallback, models, packs)
        add_lighting(azimuth)
        frame_camera(meshes, canvas)
        bpy.context.scene.render.filepath = out
        bpy.ops.render.render(write_still=True)
        print(f"[{i}/{len(jobs)}] {out}")


if __name__ == "__main__":
    main()
