"""
bake_forest_assets.py

Batch-renders Synty forest and foliage meshes into front-facing billboard PNGs
and prepares seamless terrain textures for the forest biome overhaul.

Run:
    "C:/Program Files/Blender Foundation/Blender 5.1/blender.exe" ^
        --background --python tools/blender/bake_forest_assets.py
"""

import math
import os
import sys
import shutil

import bpy
from mathutils import Vector

# --- Paths -----------------------------------------------------------------

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GAME_ASSETS = os.path.join(REPO, "docs", "game_assets")
OUT_DIR = os.path.join(REPO, "assets", "images", "forest")
ROOT_IMG_DIR = os.path.join(REPO, "assets", "images")

ADVENTURE = os.path.join(GAME_ASSETS, "POLYGON_Adventure_Pack_SourceFiles_v6")
ADVENTURE_FBX = os.path.join(ADVENTURE, "FBX")
ADVENTURE_TEX = os.path.join(ADVENTURE, "Textures", "PolyAdventureTexture_01.png")

ALPINE = os.path.join(GAME_ASSETS, "POLYGON_NatureBiomes_AlpineMountain_SourceFiles_v3")
ALPINE_TEX = os.path.join(ALPINE, "Textures")

# --- Prop table ------------------------------------------------------------
# (filename, fbx path, texture path, resolution, pitch_down_deg)

FOREST_PROPS = [
    # Tall Trees (512x512, head-on orthographic billboard)
    ("tree_pine_01.png",  os.path.join(ADVENTURE_FBX, "SM_Env_TreePine_01.fbx"),  ADVENTURE_TEX, 512, 0.0),
    ("tree_pine_02.png",  os.path.join(ADVENTURE_FBX, "SM_Env_TreePine_02.fbx"),  ADVENTURE_TEX, 512, 0.0),
    ("tree_pine_03.png",  os.path.join(ADVENTURE_FBX, "SM_Env_TreePine_03.fbx"),  ADVENTURE_TEX, 512, 0.0),
    ("tree_birch_01.png", os.path.join(ADVENTURE_FBX, "SM_Env_TreeBirch_01.fbx"), ADVENTURE_TEX, 512, 0.0),
    ("tree_birch_02.png", os.path.join(ADVENTURE_FBX, "SM_Env_TreeBirch_02.fbx"), ADVENTURE_TEX, 512, 0.0),
    ("tree_dead_01.png",  os.path.join(ADVENTURE_FBX, "SM_Env_TreeDead_01.fbx"),  ADVENTURE_TEX, 512, 0.0),

    # Rocks & Boulders (256x256, slight down-pitch for 3D depth)
    ("rock_boulder_01.png", os.path.join(ADVENTURE_FBX, "SM_Env_Rock_01.fbx"),     ADVENTURE_TEX, 256, 12.0),
    ("rock_boulder_02.png", os.path.join(ADVENTURE_FBX, "SM_Env_Rock_02.fbx"),     ADVENTURE_TEX, 256, 12.0),
    ("rock_boulder_03.png", os.path.join(ADVENTURE_FBX, "SM_Env_Rock_03.fbx"),     ADVENTURE_TEX, 256, 12.0),

    # Forest Ground Scenery (256x256, moderate pitch for ground alignment)
    ("stump_pine_01.png", os.path.join(ADVENTURE_FBX, "SM_Env_TreeStump_01.fbx"), ADVENTURE_TEX, 256, 22.0),
    ("log_fallen_01.png", os.path.join(ADVENTURE_FBX, "SM_Env_TreeLog_01.fbx"),   ADVENTURE_TEX, 256, 24.0),
    ("log_pile_01.png",   os.path.join(ADVENTURE_FBX, "SM_Prop_Logpile_01.fbx"),   ADVENTURE_TEX, 256, 18.0),

    # Undergrowth & Bushes (256x256)
    ("bush_01.png",       os.path.join(ADVENTURE_FBX, "SM_Env_Bush_01.fbx"),       ADVENTURE_TEX, 256, 10.0),
    ("bush_02.png",       os.path.join(ADVENTURE_FBX, "SM_Env_Bush_02.fbx"),       ADVENTURE_TEX, 256, 10.0),
]


# --- Scene setup -----------------------------------------------------------

def reset_scene(resolution):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene

    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue

    scene.render.resolution_x = resolution
    scene.render.resolution_y = resolution
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'

    world = bpy.data.worlds.new("PropWorld")
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs[0].default_value = (1.0, 1.0, 1.0, 1.0)
        bg.inputs[1].default_value = 0.65


def add_lighting():
    key = bpy.data.lights.new(name="Key", type='SUN')
    key.energy = 3.0
    key_obj = bpy.data.objects.new("Key", key)
    bpy.context.scene.collection.objects.link(key_obj)
    key_obj.rotation_euler = (math.radians(55), 0, math.radians(-35))

    fill = bpy.data.lights.new(name="Fill", type='SUN')
    fill.energy = 1.2
    fill_obj = bpy.data.objects.new("Fill", fill)
    bpy.context.scene.collection.objects.link(fill_obj)
    fill_obj.rotation_euler = (math.radians(70), 0, math.radians(140))


def import_fbx(path):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.fbx(filepath=path)
    fresh = [o for o in set(bpy.data.objects) - before if o.type == 'MESH']

    keep = []
    for obj in fresh:
        if "_shadow" in obj.name.lower():
            bpy.data.objects.remove(obj, do_unlink=True)
            continue
        keep.append(obj)
    return keep


def load_image(path):
    image = bpy.data.images.load(path, check_existing=False)
    if not image.has_data:
        try:
            image.reload()
        except RuntimeError:
            pass
    return image


def apply_texture(meshes, texture_path):
    image = load_image(texture_path) if os.path.exists(texture_path) else None

    for obj in meshes:
        if not obj.data.materials:
            mat = bpy.data.materials.new(name="PropMat")
            mat.use_nodes = True
            obj.data.materials.append(mat)

        for mat in obj.data.materials:
            if mat is None:
                continue

            mat.use_nodes = True
            tree = mat.node_tree
            tree.nodes.clear()

            output = tree.nodes.new("ShaderNodeOutputMaterial")
            emission = tree.nodes.new("ShaderNodeEmission")
            tex_node = tree.nodes.new("ShaderNodeTexImage")
            transparent = tree.nodes.new("ShaderNodeBsdfTransparent")
            mix = tree.nodes.new("ShaderNodeMixShader")

            tex_node.image = image
            tex_node.interpolation = 'Closest'
            emission.inputs["Strength"].default_value = 1.0

            if obj.data.uv_layers:
                uv_node = tree.nodes.new("ShaderNodeUVMap")
                uv_node.uv_map = obj.data.uv_layers[0].name
                tree.links.new(uv_node.outputs["UV"], tex_node.inputs["Vector"])

            tree.links.new(tex_node.outputs["Color"], emission.inputs["Color"])
            tree.links.new(tex_node.outputs["Alpha"], mix.inputs["Fac"])
            tree.links.new(transparent.outputs["BSDF"], mix.inputs[1])
            tree.links.new(emission.outputs["Emission"], mix.inputs[2])
            tree.links.new(mix.outputs["Shader"], output.inputs["Surface"])

            if hasattr(mat, "blend_method"):
                try:
                    mat.blend_method = 'BLEND'
                except TypeError:
                    pass


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


def frame_camera(meshes, pitch_deg=0.0):
    min_v, max_v = bounds_of(meshes)
    center = (min_v + max_v) * 0.5
    size = max_v - min_v

    pitch_down = math.radians(pitch_deg)
    extent = max(size.x, size.z, size.y * math.sin(pitch_down), 0.001) * 1.06

    cam_data = bpy.data.cameras.new("PropCam")
    cam_data.type = 'ORTHO'
    cam_data.ortho_scale = extent

    cam = bpy.data.objects.new("PropCam", cam_data)
    bpy.context.scene.collection.objects.link(cam)
    bpy.context.scene.camera = cam

    radius = max(size.x, size.y, size.z, 1.0) * 4.0
    cam.location = (
        center.x,
        center.y - radius * math.cos(pitch_down),
        center.z + radius * math.sin(pitch_down),
    )
    cam.rotation_euler = (math.radians(90) - pitch_down, 0, 0)


def render_to(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def process_textures():
    print("--- Processing Biome Terrain Textures ---")
    grass_src = os.path.join(ALPINE_TEX, "Synty_Alpine_Ground_GrassPine_01_basecolor.png")
    cliff_src = os.path.join(ALPINE_TEX, "Synty_Alpine_Ground_MossyRockPine_01_basecolor.png")

    if os.path.exists(grass_src):
        dst = os.path.join(ROOT_IMG_DIR, "floor_forest.png")
        img = bpy.data.images.load(grass_src, check_existing=False)
        img.scale(1024, 1024)
        img.filepath_raw = dst
        img.file_format = 'PNG'
        img.save()
        bpy.data.images.remove(img)
        print(f"Saved terrain floor: {dst}")

    if os.path.exists(cliff_src):
        dst = os.path.join(ROOT_IMG_DIR, "forest_cliff.png")
        img = bpy.data.images.load(cliff_src, check_existing=False)
        img.scale(1024, 1024)
        img.filepath_raw = dst
        img.file_format = 'PNG'
        img.save()
        bpy.data.images.remove(img)
        print(f"Saved cliff boundary: {dst}")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    process_textures()

    total = len(FOREST_PROPS)
    print(f"\n--- Baking {total} Forest Billboards with Blender {bpy.app.version_string} ---")

    for i, (fn, fbx_path, tex_path, res, pitch) in enumerate(FOREST_PROPS, 1):
        out_path = os.path.join(OUT_DIR, fn)
        print(f"[{i}/{total}] Baking {fn} ({res}x{res}, pitch={pitch}deg)...")

        if not os.path.exists(fbx_path):
            print(f"  ERROR: FBX not found: {fbx_path}")
            continue

        reset_scene(res)
        add_lighting()
        meshes = import_fbx(fbx_path)
        if not meshes:
            print(f"  ERROR: No meshes imported from {fbx_path}")
            continue

        apply_texture(meshes, tex_path)
        frame_camera(meshes, pitch)
        render_to(out_path)
        print(f"  -> Wrote {out_path}")

    # Copy defaults to root images directory for backward compatibility
    print("\n--- Updating Default Asset Links ---")
    copies = [
        ("tree_pine_01.png", "tree_pine.png"),
        ("rock_boulder_01.png", "mossy_rock.png"),
        ("bush_01.png", "bush.png"),
    ]
    for src_name, dst_name in copies:
        src = os.path.join(OUT_DIR, src_name)
        dst = os.path.join(ROOT_IMG_DIR, dst_name)
        if os.path.exists(src):
            shutil.copyfile(src, dst)
            print(f"  Copied {src_name} -> {dst_name}")

    print("\nForest asset baking completed successfully!")


if __name__ == "__main__":
    main()
