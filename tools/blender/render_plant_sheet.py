"""
render_plant_sheet.py

Renders every plant in a multi-object .glb (default: the 281-model small
plants pack) as its own lit thumbnail, so plants can be picked by eye for the
forest's bushes and ground scatter. tools/plant_contact_sheet.py lays the
thumbnails out as a numbered, stylized sheet.

Run:
    "C:/Program Files/Blender Foundation/Blender 5.1/blender.exe" ^
        --background --python tools/blender/render_plant_sheet.py -- [pack.glb]

Writes build/plant_thumbs/<index>_<object name>.png. Fences and vases in the
pack are skipped.
"""

import math
import os
import re
import sys

import bpy
from mathutils import Vector

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(REPO, "build", "plant_thumbs")
DEFAULT_PACK = os.path.join(REPO, "assets", "models", "forest_other",
                            "small_plants_-_indoor__outdoor_-_281_models.glb")
THUMB = 256
SKIP = re.compile(r"fence|vase", re.IGNORECASE)


def pack_path():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    return argv[0] if argv else DEFAULT_PACK


def setup_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue
    scene.render.resolution_x = THUMB
    scene.render.resolution_y = THUMB
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.view_settings.view_transform = 'Standard'
    world = bpy.data.worlds.new("SheetWorld")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[1].default_value = 0.35
    for name, energy, elev, azim in (("Key", 3.2, 50.0, -40.0), ("Fill", 0.9, 25.0, 120.0)):
        data = bpy.data.lights.new(name=name, type='SUN')
        data.energy = energy
        obj = bpy.data.objects.new(name, data)
        scene.collection.objects.link(obj)
        obj.rotation_euler = (math.radians(90 - elev), 0, math.radians(azim))
    cam_data = bpy.data.cameras.new("SheetCam")
    cam_data.type = 'ORTHO'
    cam = bpy.data.objects.new("SheetCam", cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    return cam


def frame(cam, obj):
    corners = [obj.matrix_world @ Vector(c) for c in obj.bound_box]
    lo = Vector([min(c[i] for c in corners) for i in range(3)])
    hi = Vector([max(c[i] for c in corners) for i in range(3)])
    size = hi - lo
    extent = max(size.x, size.z, 0.001) * 1.05
    cam.data.ortho_scale = extent
    distance = max(size.x, size.y, size.z, 0.1) * 4.0
    cam.location = ((lo.x + hi.x) * 0.5, lo.y - distance, lo.z + extent * 0.5)
    cam.rotation_euler = (math.radians(90), 0, 0)
    cam.data.clip_end = distance * 4.0


def main():
    cam = setup_scene()
    bpy.ops.import_scene.gltf(filepath=pack_path())
    plants = sorted((o for o in bpy.data.objects if o.type == 'MESH' and not SKIP.search(o.name)),
                    key=lambda o: o.name)
    os.makedirs(OUT_DIR, exist_ok=True)
    meshes = [o for o in bpy.data.objects if o.type == 'MESH']
    for i, plant in enumerate(plants):
        for o in meshes:
            o.hide_render = o is not plant
        bpy.context.view_layer.update()
        frame(cam, plant)
        safe = re.sub(r"[^A-Za-z0-9]+", "_", plant.name).strip("_")
        bpy.context.scene.render.filepath = os.path.join(OUT_DIR, f"{i:03d}_{safe}.png")
        bpy.ops.render.render(write_still=True)
    print(f"Rendered {len(plants)} plant thumbnails to {OUT_DIR}")


if __name__ == "__main__":
    main()
