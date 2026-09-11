"""
Converts Mixamo/FBX models into Wavefront OBJ/MTL with textures and scale normalization for LibGDX.
Run via Blender 5.1 headless:
& "C:\\Program Files\\Blender Foundation\\Blender 5.1\\blender.exe" -b --python tools/convert_fbx_to_obj.py
"""

import os
from io_scene_fbx import parse_fbx

INPUT_FBX_NOPROPS = "assets/models/character_noprops.fbx"
INPUT_FBX_PROPS = "assets/models/character_w_sword_shield.fbx"
OUT_DIR = "assets/models/player"

os.makedirs(OUT_DIR, exist_ok=True)

# 1. Export Paladin Body + Head
elem, version = parse_fbx.parse(INPUT_FBX_NOPROPS)
objs = [c for c in elem.elems if c.id == b'Objects'][0]

mtl_path = os.path.join(OUT_DIR, "paladin.mtl")
with open(mtl_path, "w") as f:
    f.write("newmtl Paladin_Mat\n")
    f.write("Ka 1.0 1.0 1.0\n")
    f.write("Kd 1.0 1.0 1.0\n")
    f.write("Ks 0.1 0.1 0.1\n")
    f.write("d 1.0\n")
    f.write("map_Kd Paladin_diffuse.png\n")

v_offset = 0
vt_offset = 0
vn_offset = 0

all_v = []
all_vt = []
all_vn = []
all_f = []

mesh_models = []
for m in objs.elems:
    if m.id == b'Model':
        vert_elem = [c for c in m.elems if c.id == b'Vertices']
        if vert_elem:
            mesh_models.append(m)

mesh_models.sort(key=lambda m: len([c for c in m.elems if c.id == b'Vertices'][0].props), reverse=True)

for m in mesh_models:
    vert_elem = [c for c in m.elems if c.id == b'Vertices'][0]
    poly_elem = [c for c in m.elems if c.id == b'PolygonVertexIndex'][0]
    norm_elem = [c for c in m.elems if c.id == b'LayerElementNormal'][0]
    uv_elem = [c for c in m.elems if c.id == b'LayerElementUV'][0]

    raw_v = vert_elem.props
    raw_poly = poly_elem.props

    for i in range(0, len(raw_v), 3):
        all_v.append((raw_v[i] * 0.01, raw_v[i+1] * 0.01, raw_v[i+2] * 0.01))

    raw_uv = [c for c in uv_elem.elems if c.id == b'UV'][0].props
    uv_idx_elems = [c for c in uv_elem.elems if c.id == b'UVIndex']
    raw_uv_idx = uv_idx_elems[0].props if uv_idx_elems else None

    for i in range(0, len(raw_uv), 2):
        all_vt.append((raw_uv[i], raw_uv[i+1]))

    raw_norm = [c for c in norm_elem.elems if c.id == b'Normals'][0].props
    for i in range(0, len(raw_norm), 3):
        all_vn.append((raw_norm[i], raw_norm[i+1], raw_norm[i+2]))

    norm_map_type = [c for c in norm_elem.elems if c.id == b'MappingInformationType'][0].props[0]

    poly_idx = 0
    cur_face_v = []
    cur_face_vt = []
    cur_face_vn = []

    for idx in raw_poly:
        v_i = (-idx - 1) if idx < 0 else idx
        vt_i = raw_uv_idx[poly_idx] if raw_uv_idx else v_i
        vn_i = poly_idx if norm_map_type == b'ByPolygonVertex' else v_i

        cur_face_v.append(v_i + 1 + v_offset)
        cur_face_vt.append(vt_i + 1 + vt_offset)
        cur_face_vn.append(vn_i + 1 + vn_offset)

        poly_idx += 1
        if idx < 0:
            all_f.append((list(cur_face_v), list(cur_face_vt), list(cur_face_vn)))
            cur_face_v = []
            cur_face_vt = []
            cur_face_vn = []

    v_offset += len(raw_v) // 3
    vt_offset += len(raw_uv) // 2
    vn_offset += len(raw_norm) // 3

obj_path = os.path.join(OUT_DIR, "paladin.obj")
with open(obj_path, "w") as f:
    f.write("mtllib paladin.mtl\n")
    f.write("usemtl Paladin_Mat\n")
    for v in all_v:
        f.write(f"v {v[0]:.5f} {v[1]:.5f} {v[2]:.5f}\n")
    for vt in all_vt:
        f.write(f"vt {vt[0]:.5f} {vt[1]:.5f}\n")
    for vn in all_vn:
        f.write(f"vn {vn[0]:.5f} {vn[1]:.5f} {vn[2]:.5f}\n")
    f.write("s 1\n")
    for face in all_f:
        vs, vts, vns = face
        tokens = [f"{vs[i]}/{vts[i]}/{vns[i]}" for i in range(len(vs))]
        f.write("f " + " ".join(tokens) + "\n")

print(f"Generated {obj_path}")

# 2. Export Sword and Shield from PROPS FBX
elem_props, _ = parse_fbx.parse(INPUT_FBX_PROPS)
objs_props = [c for c in elem_props.elems if c.id == b'Objects'][0]

def export_prop(m_elem, filename):
    vert_elem = [c for c in m_elem.elems if c.id == b'Vertices'][0]
    poly_elem = [c for c in m_elem.elems if c.id == b'PolygonVertexIndex'][0]
    norm_elem = [c for c in m_elem.elems if c.id == b'LayerElementNormal'][0]
    uv_elem = [c for c in m_elem.elems if c.id == b'LayerElementUV'][0]

    raw_v = vert_elem.props
    raw_poly = poly_elem.props
    raw_uv = [c for c in uv_elem.elems if c.id == b'UV'][0].props
    uv_idx_elems = [c for c in uv_elem.elems if c.id == b'UVIndex']
    raw_uv_idx = uv_idx_elems[0].props if uv_idx_elems else None
    raw_norm = [c for c in norm_elem.elems if c.id == b'Normals'][0].props
    norm_map_type = [c for c in norm_elem.elems if c.id == b'MappingInformationType'][0].props[0]

    out_file = os.path.join(OUT_DIR, filename)
    with open(out_file, "w") as f:
        f.write("mtllib paladin.mtl\n")
        f.write("usemtl Paladin_Mat\n")
        for i in range(0, len(raw_v), 3):
            f.write(f"v {raw_v[i]*0.01:.5f} {raw_v[i+1]*0.01:.5f} {raw_v[i+2]*0.01:.5f}\n")
        for i in range(0, len(raw_uv), 2):
            f.write(f"vt {raw_uv[i]:.5f} {raw_uv[i+1]:.5f}\n")
        for i in range(0, len(raw_norm), 3):
            f.write(f"vn {raw_norm[i]:.5f} {raw_norm[i+1]:.5f} {raw_norm[i+2]:.5f}\n")
        f.write("s 1\n")

        poly_idx = 0
        cur_face = []
        for idx in raw_poly:
            v_i = (-idx - 1) if idx < 0 else idx
            vt_i = raw_uv_idx[poly_idx] if raw_uv_idx else v_i
            vn_i = poly_idx if norm_map_type == b'ByPolygonVertex' else v_i
            cur_face.append(f"{v_i+1}/{vt_i+1}/{vn_i+1}")
            poly_idx += 1
            if idx < 0:
                f.write("f " + " ".join(cur_face) + "\n")
                cur_face = []
    print(f"Generated {out_file}")

for m in objs_props.elems:
    if m.id == b'Model':
        vert_elem = [c for c in m.elems if c.id == b'Vertices']
        if not vert_elem: continue
        v_count = len(vert_elem[0].props) // 3
        if v_count == 116:
            export_prop(m, "paladin_sword.obj")
        elif v_count == 73:
            export_prop(m, "paladin_shield.obj")
