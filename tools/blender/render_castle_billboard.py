"""Renders Castle Tarmin's citadel into the billboard that stands at the castle site.

Run headless from the repo root:

    "C:/Program Files/Blender Foundation/Blender 5.1/blender.exe" ^
        --background --python tools/blender/render_castle_billboard.py

Why a billboard
---------------
The renderer draws scenery as camera-facing quads. A real mesh at the site would be
the only per-object 3D draw in the overworld. The source is the same
castle_citadel.obj the skybox uses, so the silhouette the player walked toward
across the Marches is the one waiting at the gate.

The look follows the skybox: blackened stone, lava-lit apertures, plus a crimson
rim light from behind so the castle separates from the Blight's crimson-ochre fog.
Output: assets/images/blight/castle_tarmin_billboard.png (transparent).
"""

import math
import os

import bpy
from mathutils import Vector

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OBJ_PATH = os.path.join(REPO, "assets", "models", "skybox", "castle_citadel.obj")
OUT_PATH = os.path.join(REPO, "assets", "images", "blight", "castle_tarmin_billboard.png")

RES_X = 768
RES_Y = 640


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue
    scene.render.resolution_x = RES_X
    scene.render.resolution_y = RES_Y
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'

    world = bpy.data.worlds.new("CastleWorld")
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg:
        # Dim ochre ambient: the stone should read dark, never pure black.
        bg.inputs[0].default_value = (0.55, 0.32, 0.22, 1.0)
        bg.inputs[1].default_value = 0.35


def import_castle():
    before = set(bpy.data.objects)
    bpy.ops.wm.obj_import(filepath=OBJ_PATH)
    meshes = [o for o in set(bpy.data.objects) - before if o.type == 'MESH']
    return meshes


def bounds(objs):
    lo = Vector((1e9, 1e9, 1e9))
    hi = Vector((-1e9, -1e9, -1e9))
    for o in objs:
        for corner in o.bound_box:
            w = o.matrix_world @ Vector(corner)
            lo = Vector((min(lo.x, w.x), min(lo.y, w.y), min(lo.z, w.z)))
            hi = Vector((max(hi.x, w.x), max(hi.y, w.y), max(hi.z, w.z)))
    return lo, hi


def add_light(name, kind, energy, rot, color=(1, 1, 1)):
    data = bpy.data.lights.new(name=name, type=kind)
    data.energy = energy
    data.color = color
    obj = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(obj)
    obj.rotation_euler = rot
    return obj


def main():
    reset_scene()
    meshes = import_castle()
    if not meshes:
        raise SystemExit("castle_citadel.obj imported no meshes")

    lo, hi = bounds(meshes)
    centre = (lo + hi) / 2
    size = hi - lo

    # Key from the front-left, low; crimson rim from behind; weak fill.
    add_light("Key", 'SUN', 2.2, (math.radians(70), 0, math.radians(-30)), (1.0, 0.85, 0.75))
    add_light("Rim", 'SUN', 6.0, (math.radians(100), 0, math.radians(180)), (1.0, 0.25, 0.10))
    add_light("Fill", 'SUN', 0.6, (math.radians(60), 0, math.radians(120)), (0.7, 0.6, 0.6))

    # Orthographic, looking along +Y (OBJ import maps the model's -Z forward to +Y),
    # raised a touch so the gatehouse reads over the curtain wall.
    cam_data = bpy.data.cameras.new("Cam")
    cam_data.type = 'ORTHO'
    width = max(size.x, size.y)
    cam_data.ortho_scale = max(width, size.z * RES_X / RES_Y) * 1.04
    cam = bpy.data.objects.new("Cam", cam_data)
    bpy.context.scene.collection.objects.link(cam)
    dist = max(size) * 3
    cam.location = (centre.x, centre.y - dist, centre.z + size.z * 0.08)
    cam.rotation_euler = (math.radians(88), 0, 0)
    bpy.context.scene.camera = cam

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    bpy.context.scene.render.filepath = OUT_PATH
    bpy.ops.render.render(write_still=True)
    print("wrote", OUT_PATH)


main()
