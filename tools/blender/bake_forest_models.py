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

import bpy
from mathutils import Vector

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RAW_DIR = os.path.join(REPO, "build", "forest_raw")

# Art canvas per kind, in final sprite pixels, rendered at RAW_SCALE so the
# stylize pass can downsample. Must match the aspects in ForestChunkGenerator.
CANVAS = {
    "tree": (648, 864),
    "prop": (240, 192),
}
RAW_SCALE = 2

LIGHTINGS = {
    "tree": [("_l", -40.0), ("_r", 40.0)],
    "prop": [("", -40.0)],
}
KEY_ELEVATION = 50.0

ALPINE = "POLYGON_NatureBiomes_AlpineMountain_SourceFiles_v3"
ADVENTURE = "POLYGON_Adventure_Pack_SourceFiles_v6"
ALPINE_ATLAS = (ALPINE, "Textures/PolygonNatureBiomesS2_Alpine_Texture_01.png")
ADVENTURE_ATLAS = (ADVENTURE, "Textures/PolyAdventureTexture_01.png")
ALPINE_BUSH = (ALPINE, "Textures/Alpine_Bush_02.tga")

# (sprite name, kind, source, {material slot: texture} or None, fallback texture)
# source is ("models", path) under models_dir or (pack, path) under game_assets_dir.
MODELS = [
    ("tree_pine_a", "tree", ("models", "trees/pine2.glb"), None, None),
    ("tree_pine_b", "tree", ("models", "trees/pine3.glb"), None, None),
    ("tree_pine_c", "tree", ("models", "trees/pine4.glb"), None, None),
    ("tree_pine_d", "tree", ("models", "trees/pine5.glb"), None, None),
    ("tree_dead_a", "tree", ("models", "forest_other/dead_tree.glb"), None, None),
    ("tree_dead_b", "tree", ("models", "forest_other/dead_tree2.glb"), None, None),
    ("tree_dead_c", "tree", ("models", "forest_other/dead_tree3.glb"), None, None),
    ("tree_dead_d", "tree", ("models", "forest_other/dead_tree4.glb"), None, None),
    ("tree_dead_e", "tree", ("models", "forest_other/dead_tree5.glb"), None, None),

    ("rock_boulder_01", "prop", ("models", "forest_other/rock1.glb"), None, None),
    ("rock_boulder_02", "prop", ("models", "forest_other/rock2.glb"), None, None),
    ("rock_boulder_03", "prop", ("models", "forest_other/rock3.glb"), None, None),

    # One material each, named BerryBush_01_MAT in the FBX whatever the material list says.
    ("bush_01", "prop", (ALPINE, "FBX/Environment/SM_Env_Bush_02.fbx"), {}, ALPINE_BUSH),
    ("bush_02", "prop", (ALPINE, "FBX/Environment/SM_Env_Bush_02_Alt.fbx"), {}, ALPINE_BUSH),
    ("stump_pine_01", "prop", (ALPINE, "FBX/Environment/SM_Env_Pine_Stump_01.fbx"), {}, ALPINE_ATLAS),
    ("log_fallen_01", "prop", (ADVENTURE, "FBX/SM_Env_TreeLog_01.fbx"), {}, ADVENTURE_ATLAS),
    ("log_pile_01", "prop", (ADVENTURE, "FBX/SM_Prop_Logpile_01.fbx"), {}, ADVENTURE_ATLAS),
]


def source_dirs():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    models = argv[0] if len(argv) > 0 else os.path.join(REPO, "assets", "models")
    packs = argv[1] if len(argv) > 1 else os.path.join(REPO, "docs", "game_assets")
    return models, packs


def resolve(ref, models, packs):
    root, rel = ref
    return os.path.join(models if root == "models" else os.path.join(packs, root), rel)


def reset_scene(kind):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue
    w, h = CANVAS[kind]
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
            key = ref
            if key not in made:
                made[key] = textured_material(f"{slot_name or 'atlas'}_lit", resolve(ref, models, packs))
            obj.material_slots[i].material = made[key]
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


def frame_camera(meshes, kind):
    """Front orthographic view, base on the bottom edge, model fitted to the canvas."""
    bpy.context.view_layer.update()
    min_v, max_v = bounds_of(meshes)
    size = max_v - min_v
    w, h = CANVAS[kind]
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
    only = set(a for a in os.environ.get("FOREST_BAKE_ONLY", "").split(",") if a)
    os.makedirs(RAW_DIR, exist_ok=True)
    jobs = [(name, kind, src, slots, fallback, suffix, azimuth)
            for name, kind, src, slots, fallback in MODELS
            if not only or name in only
            for suffix, azimuth in LIGHTINGS[kind]]
    print(f"--- Baking {len(jobs)} raw frames with Blender {bpy.app.version_string} ---")

    for i, (name, kind, src, slots, fallback, suffix, azimuth) in enumerate(jobs, 1):
        path = resolve(src, models, packs)
        out = os.path.join(RAW_DIR, f"{name}{suffix}.png")
        if not os.path.exists(path):
            print(f"[{i}/{len(jobs)}] MISSING {path}")
            continue
        reset_scene(kind)
        meshes = import_model(path)
        if not meshes:
            print(f"[{i}/{len(jobs)}] no meshes in {path}")
            continue
        if slots is not None:
            assign_textures(meshes, slots, fallback, models, packs)
        add_lighting(azimuth)
        frame_camera(meshes, kind)
        bpy.context.scene.render.filepath = out
        bpy.ops.render.render(write_still=True)
        print(f"[{i}/{len(jobs)}] {out}")


if __name__ == "__main__":
    main()
