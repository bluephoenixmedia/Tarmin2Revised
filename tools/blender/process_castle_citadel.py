"""Re-export Castle Tarmin's citadel mesh as a blackened silhouette with glowing apertures.

Run headless from the repo root:

    blender --background --python tools/blender/process_castle_citadel.py

Why this exists
---------------
The citadel arrives as a single-material photogrammetry mesh whose diffuse texture is a
scrambled purple atlas with no windows in it. At the 140-unit horizon distance the castle reads
purely as a silhouette, so surface detail is wasted; what it actually needs is a handful of
lava-lit apertures punched into an otherwise black shape.

There is nothing in the texture to key those apertures off, so they are derived from geometry:
near-vertical faces in the upper keep, thinned to a sparse scatter, become an emissive material.
Everything else becomes volcanic stone. That gives two real materials the renderer can light
differently, which is what a single baked texture could never provide.

Transform is kept identical to the previous export (same grounding, SCALE and decimation) so the
placement and scale in Skybox3DRenderer continue to work unchanged.
"""

import math
import os

import bpy

GLB_PATH = r"assets/models/castle_citadel.glb"
OUTPUT_DIR = r"assets/models/skybox"
OBJ_PATH = os.path.join(OUTPUT_DIR, "castle_citadel.obj")

SCALE = 30.0
DECIMATE_RATIO = 0.15

# Aperture selection. Windows belong to the built keep, not the rock it stands on, and they are
# punched into walls rather than roofs -- hence the height band and the verticality test.
APERTURE_MIN_HEIGHT = 0.62   # fraction of total height: below this is the rocky mount
APERTURE_MAX_HEIGHT = 0.96   # above this is battlement tops, which should stay dark
APERTURE_MAX_SLOPE = 0.55    # |normal.z| below this counts as a wall rather than a roof/floor
APERTURE_DENSITY = 0.07      # fraction of eligible faces that become lit

MAT_STONE = "CastleStone"
MAT_EMBER = "CastleEmber"


def deterministic_unit(index):
    """Stable pseudo-random value in [0, 1) for a face index.

    Deliberately not `random`: the export must produce the same castle every time it is run, or
    the mesh silently changes under version control.
    """
    x = math.sin(index * 12.9898) * 43758.5453
    return x - math.floor(x)


def main():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=GLB_PATH)

    obj = bpy.data.objects["Mesh_0"]

    # 1. Ground the mesh and centre it on X/Y, matching the original export exactly.
    coords = [v.co for v in obj.data.vertices]
    min_x = min(c.x for c in coords)
    max_x = max(c.x for c in coords)
    min_y = min(c.y for c in coords)
    max_y = max(c.y for c in coords)
    min_z = min(c.z for c in coords)

    center_x = (min_x + max_x) / 2.0
    center_y = (min_y + max_y) / 2.0

    for v in obj.data.vertices:
        v.co.x = (v.co.x - center_x) * SCALE
        v.co.y = (v.co.y - center_y) * SCALE
        v.co.z = (v.co.z - min_z) * SCALE

    obj.data.update()

    # 2. Decimate to a game-ready polycount before classifying, so face indices match the
    #    geometry that actually ships.
    decimate = obj.modifiers.new(name="Decimate", type="DECIMATE")
    decimate.ratio = DECIMATE_RATIO
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.modifier_apply(modifier="Decimate")
    print("Decimated to %d polygons, %d vertices." % (len(obj.data.polygons), len(obj.data.vertices)))

    # 3. Two untextured materials. Exact colours are written into the .mtl afterwards; what
    #    matters here is that the split exists and the names are stable.
    obj.data.materials.clear()
    for name in (MAT_STONE, MAT_EMBER):
        mat = bpy.data.materials.new(name=name)
        mat.use_nodes = True
        obj.data.materials.append(mat)

    stone_index = 0
    ember_index = 1

    # 4. Classify faces.
    zs = [v.co.z for v in obj.data.vertices]
    min_height = min(zs)
    max_height = max(zs)
    span = max(max_height - min_height, 1e-6)

    lit = 0
    for face in obj.data.polygons:
        centroid_z = face.center.z
        height_fraction = (centroid_z - min_height) / span
        vertical = abs(face.normal.z) < APERTURE_MAX_SLOPE
        in_keep = APERTURE_MIN_HEIGHT <= height_fraction <= APERTURE_MAX_HEIGHT
        sparse = deterministic_unit(face.index) < APERTURE_DENSITY

        if in_keep and vertical and sparse:
            face.material_index = ember_index
            lit += 1
        else:
            face.material_index = stone_index

    print("Lit %d of %d faces as apertures (%.2f%%)."
          % (lit, len(obj.data.polygons), 100.0 * lit / max(len(obj.data.polygons), 1)))

    # 5. Export with LibGDX's axis convention.
    bpy.ops.wm.obj_export(
        filepath=OBJ_PATH,
        export_selected_objects=False,
        forward_axis="NEGATIVE_Z",
        up_axis="Y",
        export_materials=True,
        export_triangulated_mesh=True,
    )
    print("Exported %s" % OBJ_PATH)

    # 6. Overwrite Blender's default material file. Blender exports both materials as identical
    #    grey, which would silently revert the castle every time this script is re-run.
    write_material_file()


MTL_CONTENTS = """# Castle Tarmin (citadel mesh) -- generated by tools/blender/process_castle_citadel.py.
# Blackened volcanic stone with sparse lava-lit apertures. At 140 units the castle reads as a
# silhouette, so the only surface detail worth paying for is the glow coming out of it.
# Kd is overridden at load time by Skybox3DRenderer.blackenToSilhouette(); Ke is what survives,
# and is therefore what decides which faces glow.

newmtl CastleStone
Ns 4.000000
Ka 1.000000 1.000000 1.000000
Kd 0.050000 0.042000 0.055000
Ks 0.000000 0.000000 0.000000
Ke 0.000000 0.000000 0.000000
Ni 1.500000
d 1.000000
illum 2

newmtl CastleEmber
Ns 30.000000
Ka 1.000000 1.000000 1.000000
Kd 0.600000 0.180000 0.050000
Ks 0.000000 0.000000 0.000000
Ke 2.600000 0.750000 0.180000
Ni 1.500000
d 1.000000
illum 2
"""


def write_material_file():
    mtl_path = os.path.join(OUTPUT_DIR, "castle_citadel.mtl")
    with open(mtl_path, "w", encoding="utf-8") as handle:
        handle.write(MTL_CONTENTS)
    print("Wrote %s" % mtl_path)


if __name__ == "__main__":
    main()
