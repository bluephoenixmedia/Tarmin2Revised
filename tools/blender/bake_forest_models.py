"""
bake_forest_models.py

Renders the forest's tree models (.glb) into raw, lit billboard frames. These
are not game art yet: tools/stylize_forest_sprites.py turns them into the
palette-locked pixel-art PNGs the forest uses.

Unlike bake_forest_assets.py, which renders flat-colour Synty meshes unlit,
these models carry real bark and needle textures, so they are lit: a key and a
fill sun, in two setups (key from the left, key from the right). Mirroring at
runtime doubles that again.

Every frame shares one portrait canvas with the trunk base on the bottom edge,
so the game can size every tree billboard from a single aspect ratio.

Run:
    "C:/Program Files/Blender Foundation/Blender 5.1/blender.exe" ^
        --background --python tools/blender/bake_forest_models.py -- [models_dir]

models_dir defaults to assets/models and must contain trees/pine2-5.glb and
forest_other/dead_tree*.glb. The models are gitignored source files; only the
stylized PNGs are committed.
"""

import math
import os
import sys

import bpy
from mathutils import Vector

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RAW_DIR = os.path.join(REPO, "build", "forest_raw")

# Art canvas in final sprite pixels, rendered at RAW_SCALE so the stylize pass
# can downsample. Must match stylize_forest_sprites.py and the tree aspect in
# ForestChunkGenerator.
CANVAS_W = 648
CANVAS_H = 864
RAW_SCALE = 2

# (sprite name, path relative to models_dir)
MODELS = [
    ("tree_pine_a", "trees/pine2.glb"),
    ("tree_pine_b", "trees/pine3.glb"),
    ("tree_pine_c", "trees/pine4.glb"),
    ("tree_pine_d", "trees/pine5.glb"),
    ("tree_dead_a", "forest_other/dead_tree.glb"),
    ("tree_dead_b", "forest_other/dead_tree2.glb"),
    ("tree_dead_c", "forest_other/dead_tree3.glb"),
    ("tree_dead_d", "forest_other/dead_tree4.glb"),
    ("tree_dead_e", "forest_other/dead_tree5.glb"),
]

# Key light azimuth per lighting setup, degrees. Elevation is shared.
LIGHTINGS = [("l", -40.0), ("r", 40.0)]
KEY_ELEVATION = 50.0


def models_dir():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    return argv[0] if argv else os.path.join(REPO, "assets", "models")


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue
    scene.render.resolution_x = CANVAS_W * RAW_SCALE
    scene.render.resolution_y = CANVAS_H * RAW_SCALE
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.view_settings.view_transform = 'Standard'

    world = bpy.data.worlds.new("TreeWorld")
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


def import_glb(path):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    return [o for o in set(bpy.data.objects) - before if o.type == 'MESH']


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


def frame_camera(meshes):
    """Front orthographic view, trunk base on the bottom edge, model fitted to the canvas."""
    bpy.context.view_layer.update()
    min_v, max_v = bounds_of(meshes)
    size = max_v - min_v
    aspect = CANVAS_W / CANVAS_H
    # ortho_scale spans the longer side of the frame, which is the height here.
    extent = max(size.z, size.x / aspect) * 1.02

    cam_data = bpy.data.cameras.new("TreeCam")
    cam_data.type = 'ORTHO'
    cam_data.ortho_scale = extent
    cam = bpy.data.objects.new("TreeCam", cam_data)
    bpy.context.scene.collection.objects.link(cam)
    bpy.context.scene.camera = cam

    centre_x = (min_v.x + max_v.x) * 0.5
    distance = max(size.x, size.y, size.z, 1.0) * 4.0
    cam.location = (centre_x, min_v.y - distance, min_v.z + extent * 0.5)
    cam.rotation_euler = (math.radians(90), 0, 0)
    cam_data.clip_end = distance * 4.0


def main():
    root = models_dir()
    os.makedirs(RAW_DIR, exist_ok=True)
    jobs = [(n, p, l, a) for n, p in MODELS for l, a in LIGHTINGS]
    print(f"--- Baking {len(jobs)} raw tree frames from {root} with Blender {bpy.app.version_string} ---")

    for i, (name, rel, light, azimuth) in enumerate(jobs, 1):
        src = os.path.join(root, rel)
        out = os.path.join(RAW_DIR, f"{name}_{light}.png")
        if not os.path.exists(src):
            print(f"[{i}/{len(jobs)}] MISSING {src}")
            continue
        reset_scene()
        meshes = import_glb(src)
        if not meshes:
            print(f"[{i}/{len(jobs)}] no meshes in {src}")
            continue
        add_lighting(azimuth)
        frame_camera(meshes)
        bpy.context.scene.render.filepath = out
        bpy.ops.render.render(write_still=True)
        print(f"[{i}/{len(jobs)}] {out}")


if __name__ == "__main__":
    main()
