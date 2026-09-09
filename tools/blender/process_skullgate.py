"""
Blender 5.1 automation script to process and separate Meshy.ai Skullgate model into
game-ready assets for Tarmin2 3D rendering pipeline.
"""
import os
import bpy
import bmesh

GLB_PATH = r"C:\Users\denni\Downloads\Meshy_AI_Skullgate_of_the_Froz_0909230742_texture.glb"
OUTPUT_DIR = r"C:\Users\denni\Desktop\Tarmin2\assets\models\gate"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 1. Reset scene and import GLB
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=GLB_PATH)

obj = bpy.data.objects['Mesh_0']

# 2. Extract and save texture
if len(bpy.data.images) > 0:
    for img in bpy.data.images:
        print(f"Found image: {img.name} ({img.size[0]}x{img.size[1]})")
        tex_path = os.path.join(OUTPUT_DIR, "gate_diffuse.png")
        img.filepath_raw = tex_path
        img.file_format = 'PNG'
        img.save()
        print(f"Saved texture to {tex_path}")
        break

# 3. Orient and ground model
coords = [v.co for v in obj.data.vertices]
min_z = min(c.z for c in coords)
for v in obj.data.vertices:
    v.co.z -= min_z

# Scale model to game scale (corridor width = 1.0, gate width ~ 1.18 to seat into walls)
SCALE = 0.65
for v in obj.data.vertices:
    v.co.x *= SCALE
    v.co.y *= SCALE
    v.co.z *= SCALE

obj.data.update()

# 4. Decimate geometry to game-ready polycount (~40k faces total)
decimate = obj.modifiers.new(name="Decimate", type='DECIMATE')
decimate.ratio = 0.15 # 268k -> ~40k faces
bpy.context.view_layer.objects.active = obj
bpy.ops.object.modifier_apply(modifier="Decimate")
print(f"Decimated mesh to {len(obj.data.polygons)} polygons, {len(obj.data.vertices)} vertices.")

# 5. Create 3 distinct mesh objects directly
door_z_max = 0.74
door_x_max = 0.35

frame_mesh = obj.data.copy()
frame_mesh.name = "mesh_frame"
frame_obj = bpy.data.objects.new("gate_frame", frame_mesh)
bpy.context.scene.collection.objects.link(frame_obj)

left_mesh = obj.data.copy()
left_mesh.name = "mesh_door_left"
left_door_obj = bpy.data.objects.new("gate_door_left", left_mesh)
bpy.context.scene.collection.objects.link(left_door_obj)

right_mesh = obj.data.copy()
right_mesh.name = "mesh_door_right"
right_door_obj = bpy.data.objects.new("gate_door_right", right_mesh)
bpy.context.scene.collection.objects.link(right_door_obj)

# Remove the original source object
bpy.data.objects.remove(obj, do_unlink=True)

# 6. Delete unwanted faces from Frame
bm_f = bmesh.new()
bm_f.from_mesh(frame_mesh)
del_faces_f = [f for f in bm_f.faces if f.calc_center_median().z < door_z_max and abs(f.calc_center_median().x) < door_x_max]
bmesh.ops.delete(bm_f, geom=del_faces_f, context='FACES')
loose_v_f = [v for v in bm_f.verts if len(v.link_faces) == 0]
bmesh.ops.delete(bm_f, geom=loose_v_f, context='VERTS')
bm_f.to_mesh(frame_mesh)
frame_mesh.update()
bm_f.free()

# 7. Delete unwanted faces from Left Door & Shift origin to Left Hinge (-door_x_max, 0, 0)
bm_l = bmesh.new()
bm_l.from_mesh(left_mesh)
del_faces_l = [f for f in bm_l.faces if not (f.calc_center_median().z < door_z_max and f.calc_center_median().x >= -door_x_max and f.calc_center_median().x < 0.0)]
bmesh.ops.delete(bm_l, geom=del_faces_l, context='FACES')
loose_v_l = [v for v in bm_l.verts if len(v.link_faces) == 0]
bmesh.ops.delete(bm_l, geom=loose_v_l, context='VERTS')
# Shift vertices so left hinge is at (0, 0, 0)
for v in bm_l.verts:
    v.co.x -= (-door_x_max) # adds +door_x_max
bm_l.to_mesh(left_mesh)
left_mesh.update()
bm_l.free()

# 8. Delete unwanted faces from Right Door & Shift origin to Right Hinge (+door_x_max, 0, 0)
bm_r = bmesh.new()
bm_r.from_mesh(right_mesh)
del_faces_r = [f for f in bm_r.faces if not (f.calc_center_median().z < door_z_max and f.calc_center_median().x > 0.0 and f.calc_center_median().x <= door_x_max)]
bmesh.ops.delete(bm_r, geom=del_faces_r, context='FACES')
loose_v_r = [v for v in bm_r.verts if len(v.link_faces) == 0]
bmesh.ops.delete(bm_r, geom=loose_v_r, context='VERTS')
# Shift vertices so right hinge is at (0, 0, 0)
for v in bm_r.verts:
    v.co.x -= door_x_max
bm_r.to_mesh(right_mesh)
right_mesh.update()
bm_r.free()

print(f"Geometry prepared: Frame={len(frame_mesh.polygons)} polys, LeftDoor={len(left_mesh.polygons)} polys, RightDoor={len(right_mesh.polygons)} polys")

# 9. Export OBJ files
def export_obj(target_obj, filename):
    bpy.ops.object.select_all(action='DESELECT')
    target_obj.select_set(True)
    bpy.context.view_layer.objects.active = target_obj
    out_path = os.path.join(OUTPUT_DIR, filename)
    bpy.ops.wm.obj_export(
        filepath=out_path,
        export_selected_objects=True,
        forward_axis='NEGATIVE_Z',
        up_axis='Y',
        apply_modifiers=True,
        export_triangulated_mesh=True
    )
    print(f"Exported {out_path} ({len(target_obj.data.polygons)} polygons)")

export_obj(frame_obj, "gate_frame.obj")
export_obj(left_door_obj, "gate_door_left.obj")
export_obj(right_door_obj, "gate_door_right.obj")

# 10. Fix texture paths in .mtl files to be relative (gate_diffuse.png)
for mtl_name in ["gate_frame.mtl", "gate_door_left.mtl", "gate_door_right.mtl"]:
    mtl_path = os.path.join(OUTPUT_DIR, mtl_name)
    if os.path.exists(mtl_path):
        with open(mtl_path, "r") as f:
            lines = f.readlines()
        with open(mtl_path, "w") as f:
            for line in lines:
                if line.strip().startswith("map_Kd"):
                    f.write("map_Kd gate_diffuse.png\n")
                else:
                    f.write(line)
        print(f"Fixed texture path in {mtl_name}")

print("SKULLGATE PROCESSING COMPLETED SUCCESSFULLY!")
