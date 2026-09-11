import os
import bpy

GLB_PATH = r"assets/models/castle_citadel.glb"
OUTPUT_DIR = r"assets/models/skybox"
OBJ_PATH = os.path.join(OUTPUT_DIR, "castle_citadel.obj")
DIFFUSE_PATH = os.path.join(OUTPUT_DIR, "castle_citadel_diffuse.png")

# 1. Reset scene and import GLB
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=GLB_PATH)

obj = bpy.data.objects['Mesh_0']

# 2. Extract and save base color diffuse texture
if 'Image_0' in bpy.data.images:
    img = bpy.data.images['Image_0']
    img.filepath_raw = os.path.abspath(DIFFUSE_PATH)
    img.file_format = 'PNG'
    img.save()
    print(f"Saved diffuse texture to {DIFFUSE_PATH}")
elif len(bpy.data.images) > 0:
    img = bpy.data.images[0]
    img.filepath_raw = os.path.abspath(DIFFUSE_PATH)
    img.file_format = 'PNG'
    img.save()
    print(f"Saved fallback diffuse texture to {DIFFUSE_PATH}")

# 3. Ground the mesh and center X/Y
coords = [v.co for v in obj.data.vertices]
min_x = min(c.x for c in coords)
max_x = max(c.x for c in coords)
min_y = min(c.y for c in coords)
max_y = max(c.y for c in coords)
min_z = min(c.z for c in coords)

center_x = (min_x + max_x) / 2.0
center_y = (min_y + max_y) / 2.0

SCALE = 30.0

for v in obj.data.vertices:
    v.co.x = (v.co.x - center_x) * SCALE
    v.co.y = (v.co.y - center_y) * SCALE
    v.co.z = (v.co.z - min_z) * SCALE

obj.data.update()

# 4. Decimate geometry to game-ready polycount (~45k faces)
decimate = obj.modifiers.new(name="Decimate", type='DECIMATE')
decimate.ratio = 0.15
bpy.context.view_layer.objects.active = obj
bpy.ops.object.modifier_apply(modifier="Decimate")
print(f"Decimated mesh to {len(obj.data.polygons)} polygons, {len(obj.data.vertices)} vertices.")

# 5. Fix material name and assign diffuse texture
mat = bpy.data.materials.new(name="CastleCitadelMat")
mat.use_nodes = True
nodes = mat.node_tree.nodes
bsdf = nodes.get("Principled BSDF")
tex_node = nodes.new('ShaderNodeTexImage')
if os.path.exists(DIFFUSE_PATH):
    tex_image = bpy.data.images.load(os.path.abspath(DIFFUSE_PATH))
    tex_node.image = tex_image
    mat.node_tree.links.new(bsdf.inputs['Base Color'], tex_node.outputs['Color'])

obj.data.materials.clear()
obj.data.materials.append(mat)

# 6. Export to Wavefront OBJ with standard LibGDX coordinates (Y Up, -Z Forward)
bpy.ops.wm.obj_export(
    filepath=OBJ_PATH,
    export_selected_objects=False,
    forward_axis='NEGATIVE_Z',
    up_axis='Y',
    export_materials=True,
    export_triangulated_mesh=True
)
print(f"Exported game-ready OBJ to {OBJ_PATH}")
