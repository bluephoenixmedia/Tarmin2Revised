"""
Tarmin 2 - Blender 5.1 Procedural Skybox & Horizon Landmark Generator
---------------------------------------------------------------------
Generates 3D models for Castle Tarmin, South Spire, Horizon Mountain Ring,
West Anvil Cumulus, Celestial Sky Dome, Sun and Moon.

Outputs:
  - assets/models/skybox/*.obj
  - assets/models/skybox/*.mtl
  - assets/models/skybox/skybox_landmarks.blend
"""

import bpy
import bmesh
import math
import os
import sys

def clear_scene():
    """Removes all objects, meshes, and materials from the current blend file."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    for block in bpy.data.meshes:
        bpy.data.meshes.remove(block)
    for block in bpy.data.materials:
        bpy.data.materials.remove(block)

def create_material(name, diffuse_color, emission_color=None, emission_strength=1.0, roughness=0.7):
    """Creates a Principled BSDF material with optional emission."""
    mat = bpy.data.materials.new(name=name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs['Base Color'].default_value = diffuse_color
        if 'Roughness' in bsdf.inputs:
            bsdf.inputs['Roughness'].default_value = roughness
        if emission_color is not None:
            if 'Emission Color' in bsdf.inputs:
                bsdf.inputs['Emission Color'].default_value = emission_color
                if 'Emission Strength' in bsdf.inputs:
                    bsdf.inputs['Emission Strength'].default_value = emission_strength
            elif 'Emission' in bsdf.inputs:
                bsdf.inputs['Emission'].default_value = emission_color
    return mat

def export_object_as_obj(obj, filepath):
    """Exports a single object as an .obj with .mtl file."""
    # Deselect all, select only the target object
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    
    # Check if modern C++ exporter is available
    if hasattr(bpy.ops.wm, 'obj_export'):
        bpy.ops.wm.obj_export(
            filepath=filepath,
            export_selected_objects=True,
            export_materials=True,
            export_triangulated_mesh=True,
            apply_modifiers=True
        )
    else:
        bpy.ops.export_scene.obj(
            filepath=filepath,
            use_selection=True,
            use_materials=True,
            use_triangles=True,
            apply_modifiers=True
        )
    print(f"[Blender] Exported: {filepath}")

# -----------------------------------------------------------------------------
# 1. CASTLE TARMIN GENERATOR (North Landmark)
# -----------------------------------------------------------------------------
def build_castle_tarmin():
    print("[Blender] Building Castle Tarmin citadel...")
    
    mat_mountain = create_material("MatMountain", (0.24, 0.18, 0.35, 1.0), roughness=0.85)
    mat_citadel  = create_material("MatCitadel",  (0.48, 0.32, 0.68, 1.0), roughness=0.6)
    mat_chevron  = create_material("MatChevron",  (0.78, 0.55, 0.98, 1.0), emission_color=(0.85, 0.65, 1.0, 1.0), emission_strength=2.5)
    mat_slits    = create_material("MatSlits",    (0.12, 0.08, 0.18, 1.0), roughness=0.9)
    
    parts = []
    
    # A. Stepped Volcanic Mountain Crater (5 tiers)
    tiers = [
        (28.0, 23.0, 4.0, 2.0),
        (23.0, 18.0, 4.0, 6.0),
        (18.0, 14.0, 4.0, 10.0),
        (14.0, 10.5, 4.0, 14.0),
        (10.5,  8.0, 4.0, 18.0),
    ]
    for i, (r_bot, r_top, h, y_pos) in enumerate(tiers):
        bpy.ops.mesh.primitive_cylinder_add(
            vertices=12,
            radius=r_bot,
            depth=h,
            location=(0, 0, y_pos)
        )
        tier_obj = bpy.context.active_object
        tier_obj.name = f"MountainTier_{i}"
        # Scale top slightly
        scale_ratio = r_top / r_bot
        # Apply taper via simple mesh edit or cone
        tier_obj.data.materials.append(mat_mountain)
        parts.append(tier_obj)

    # 8 Mountain Flank Ribs
    for i in range(8):
        angle = i * (math.pi / 4.0)
        x = math.cos(angle) * 16.0
        y = math.sin(angle) * 16.0
        bpy.ops.mesh.primitive_cube_add(size=1.0, location=(x, y, 9.0))
        rib = bpy.context.active_object
        rib.scale = (1.8, 1.8, 8.0)
        rib.rotation_euler = (0.2 * math.sin(angle), -0.2 * math.cos(angle), angle)
        rib.data.materials.append(mat_mountain)
        parts.append(rib)

    # B. Glowing Purple Chevron Gateway on front face (-Y or +Y; here -Y is front)
    chevron_coords = [
        (-2.2, -12.5, 9.0, 0.4, 0.4, 4.5, 0.25),
        ( 2.2, -12.5, 9.0, 0.4, 0.4, 4.5, -0.25),
        (-1.2, -10.5, 14.0, 0.4, 0.4, 3.5, 0.20),
        ( 1.2, -10.5, 14.0, 0.4, 0.4, 3.5, -0.20),
        ( 0.0,  -9.0, 17.5, 0.5, 0.5, 1.8, 0.0)
    ]
    for i, (cx, cy, cz, sx, sy, sz, rot_y) in enumerate(chevron_coords):
        bpy.ops.mesh.primitive_cube_add(size=1.0, location=(cx, cy, cz))
        c_obj = bpy.context.active_object
        c_obj.name = f"ChevronGate_{i}"
        c_obj.scale = (sx * 2, sy * 2, sz * 2)
        c_obj.rotation_euler = (0, 0, rot_y)
        c_obj.data.materials.append(mat_chevron)
        parts.append(c_obj)

    # C. Citadel Summit Keep
    # Base foundation plinth (Y=20.5)
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, 20.5))
    plinth = bpy.context.active_object
    plinth.scale = (12.0, 12.0, 1.5)
    plinth.data.materials.append(mat_citadel)
    parts.append(plinth)

    # Main Fortress Keep (Y=24.5)
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, 24.5))
    keep = bpy.context.active_object
    keep.scale = (8.5, 8.5, 6.5)
    keep.data.materials.append(mat_citadel)
    parts.append(keep)

    # 4 Corner Bastions (Towers)
    corner_offsets = [(-4.2, -4.2), (4.2, -4.2), (-4.2, 4.2), (4.2, 4.2)]
    for i, (ox, oy) in enumerate(corner_offsets):
        bpy.ops.mesh.primitive_cube_add(size=1.0, location=(ox, oy, 26.5))
        tower = bpy.context.active_object
        tower.name = f"CornerTower_{i}"
        tower.scale = (2.6, 2.6, 10.5)
        tower.data.materials.append(mat_citadel)
        parts.append(tower)
        
        # Corner Tower Battlements (merlons)
        for b in range(4):
            bx = ox + (0.9 if b in (1, 2) else -0.9)
            by = oy + (0.9 if b in (2, 3) else -0.9)
            bpy.ops.mesh.primitive_cube_add(size=1.0, location=(bx, by, 32.2))
            merlon = bpy.context.active_object
            merlon.scale = (0.5, 0.5, 1.0)
            merlon.data.materials.append(mat_citadel)
            parts.append(merlon)

    # Central Raised Keep Spire
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, 29.0))
    center_spire = bpy.context.active_object
    center_spire.scale = (4.5, 4.5, 8.0)
    center_spire.data.materials.append(mat_citadel)
    parts.append(center_spire)
    
    # Recessed Arrow Slits
    slit_locs = [(0, -4.3, 25.0), (-2.2, -4.3, 24.0), (2.2, -4.3, 24.0), (0, -2.3, 30.0)]
    for i, sl in enumerate(slit_locs):
        bpy.ops.mesh.primitive_cube_add(size=1.0, location=sl)
        slit = bpy.context.active_object
        slit.scale = (0.5, 0.2, 1.4)
        slit.data.materials.append(mat_slits)
        parts.append(slit)

    # Join all pieces into single cohesive Castle mesh
    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()
    
    castle_obj = bpy.context.active_object
    castle_obj.name = "CastleTarmin"
    return castle_obj

# -----------------------------------------------------------------------------
# 2. SOUTH SPIRE GENERATOR (South Landmark)
# -----------------------------------------------------------------------------
def build_south_spire():
    print("[Blender] Building South Spire...")
    
    mat_spire_stone = create_material("MatSpireStone", (0.13, 0.13, 0.16, 1.0), roughness=0.8)
    mat_spire_base  = create_material("MatSpireBase",  (0.20, 0.19, 0.24, 1.0), roughness=0.9)
    mat_glow_slit   = create_material("MatGlowSlit",   (1.0, 0.15, 0.10, 1.0), emission_color=(1.0, 0.22, 0.15, 1.0), emission_strength=3.0)
    
    parts = []
    
    # A. Basalt Mound Plinth
    bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=9.0, depth=3.0, location=(0, 0, 1.5))
    plinth = bpy.context.active_object
    plinth.data.materials.append(mat_spire_base)
    parts.append(plinth)

    # 6 Surrounding Basalt Needles
    for i in range(6):
        angle = i * (math.pi / 3.0) + 0.2
        bx = math.cos(angle) * 7.5
        by = math.sin(angle) * 7.5
        bpy.ops.mesh.primitive_cone_add(vertices=5, radius1=1.8, depth=7.0, location=(bx, by, 3.5))
        rock = bpy.context.active_object
        rock.rotation_euler = (0.15 * math.sin(angle), -0.15 * math.cos(angle), angle)
        rock.data.materials.append(mat_spire_base)
        parts.append(rock)

    # B. Lower Tower Body with 4 Buttresses
    bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=3.8, depth=14.0, location=(0, 0, 10.0))
    lower_body = bpy.context.active_object
    lower_body.data.materials.append(mat_spire_stone)
    parts.append(lower_body)
    
    # 4 Flared Buttress Fins
    for i in range(4):
        angle = i * (math.pi / 2.0)
        fx = math.cos(angle) * 4.0
        fy = math.sin(angle) * 4.0
        bpy.ops.mesh.primitive_cube_add(size=1.0, location=(fx, fy, 8.0))
        fin = bpy.context.active_object
        fin.scale = (0.8, 0.8, 10.0)
        fin.rotation_euler = (0.25 * math.sin(angle), -0.25 * math.cos(angle), angle)
        fin.data.materials.append(mat_spire_stone)
        parts.append(fin)

    # C. Middle & Upper Tower Body (tapered)
    bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=2.6, depth=14.0, location=(0, 0, 24.0))
    mid_body = bpy.context.active_object
    mid_body.data.materials.append(mat_spire_stone)
    parts.append(mid_body)

    bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=1.8, depth=10.0, location=(0, 0, 36.0))
    upper_body = bpy.context.active_object
    upper_body.data.materials.append(mat_spire_stone)
    parts.append(upper_body)

    # D. Trident Crown Spires
    # Center needle spire
    bpy.ops.mesh.primitive_cone_add(vertices=6, radius1=1.4, depth=15.0, location=(0, 0, 48.5))
    center_spire = bpy.context.active_object
    center_spire.data.materials.append(mat_spire_stone)
    parts.append(center_spire)

    # Left & Right secondary needle spires
    for sx, sy in [(-1.5, 0), (1.5, 0)]:
        bpy.ops.mesh.primitive_cone_add(vertices=5, radius1=0.7, depth=8.0, location=(sx, sy, 43.0))
        side_spire = bpy.context.active_object
        side_spire.data.materials.append(mat_spire_stone)
        parts.append(side_spire)

    # E. Glowing Crimson Window Slits
    slit_heights = [12.0, 19.0, 26.0, 34.0]
    for sh in slit_heights:
        bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, -2.7 if sh < 20 else -1.8, sh))
        slit = bpy.context.active_object
        slit.scale = (0.35, 0.15, 2.0)
        slit.data.materials.append(mat_glow_slit)
        parts.append(slit)

    # Join South Spire parts
    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()

    spire_obj = bpy.context.active_object
    spire_obj.name = "SouthSpire"
    return spire_obj

# -----------------------------------------------------------------------------
# 3. HORIZON MOUNTAIN RING (East/West/Perimeter)
# -----------------------------------------------------------------------------
def build_mountain_ring():
    print("[Blender] Building Mountain Ring...")
    mat_mountains = create_material("MatMountainRing", (0.30, 0.25, 0.40, 1.0), roughness=0.95)
    
    parts = []
    num_peaks = 24
    radius = 150.0
    
    for i in range(num_peaks):
        angle = i * (2.0 * math.pi / num_peaks)
        # Leave gaps directly North (angle ~ pi/2) and South (angle ~ 3pi/2) for landmarks
        dist_to_north = abs(angle - math.pi * 0.5)
        dist_to_south = abs(angle - math.pi * 1.5)
        
        # Vary peak height
        h = 14.0 + 9.0 * math.sin(i * 1.7) + 5.0 * math.cos(i * 2.3)
        base_r = 18.0 + 4.0 * math.cos(i * 1.1)
        
        # Pull back slightly if near landmarks so landmarks dominate
        peak_dist = radius
        if dist_to_north < 0.4:
            peak_dist = radius + 25.0
            h *= 0.6
        if dist_to_south < 0.4:
            peak_dist = radius + 25.0
            h *= 0.6
            
        px = math.cos(angle) * peak_dist
        py = math.sin(angle) * peak_dist
        
        bpy.ops.mesh.primitive_cone_add(vertices=5, radius1=base_r, depth=h * 2.0, location=(px, py, h * 0.4))
        peak = bpy.context.active_object
        peak.scale = (1.0 + 0.3 * math.sin(i), 1.0 + 0.2 * math.cos(i), 1.0)
        peak.data.materials.append(mat_mountains)
        parts.append(peak)

    # Join mountains
    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()

    mountain_obj = bpy.context.active_object
    mountain_obj.name = "MountainRing"
    return mountain_obj

# -----------------------------------------------------------------------------
# 4. WEST CUMULUS ANVIL CLOUD (West Horizon Landmark)
# -----------------------------------------------------------------------------
def build_west_cumulus():
    print("[Blender] Building West Cumulus Anvil Cloud...")
    mat_cloud = create_material("MatCumulusAnvil", (0.92, 0.72, 0.60, 1.0), roughness=0.9)
    
    parts = []
    # Cluster of overlapping rounded cloud puffs
    cloud_puffs = [
        (0.0, 0.0, 10.0, 12.0, 7.0, 5.0),
        (-4.0, 0.0, 16.0, 9.0, 6.0, 6.0),
        ( 3.5, 0.0, 18.0, 8.5, 6.0, 6.0),
        ( 0.0, 0.0, 24.0, 9.0, 6.5, 7.0),
        ( 1.0, 0.0, 31.0, 7.5, 5.5, 6.5),
        (-2.0, 0.0, 36.0, 14.0, 7.0, 4.0),  # Anvil flare top
        ( 3.0, 0.0, 37.0, 12.0, 6.5, 3.5),
    ]
    for i, (cx, cy, cz, sx, sy, sz) in enumerate(cloud_puffs):
        bpy.ops.mesh.primitive_uv_sphere_add(segments=12, ring_count=8, radius=1.0, location=(cx, cy, cz))
        puff = bpy.context.active_object
        puff.scale = (sx, sy, sz)
        puff.data.materials.append(mat_cloud)
        parts.append(puff)

    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()

    cloud_obj = bpy.context.active_object
    cloud_obj.name = "WestCumulus"
    return cloud_obj

# -----------------------------------------------------------------------------
# 5. CELESTIAL SUN & MOON
# -----------------------------------------------------------------------------
def build_celestial_bodies():
    print("[Blender] Building Sun & Moon meshes...")
    mat_sun  = create_material("MatSun",  (1.0, 0.92, 0.65, 1.0), emission_color=(1.0, 0.95, 0.70, 1.0), emission_strength=4.0)
    mat_moon = create_material("MatMoon", (0.85, 0.90, 1.00, 1.0), emission_color=(0.88, 0.92, 1.00, 1.0), emission_strength=2.5)

    # Sun Disk
    bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=5.0, depth=0.5, location=(0, 0, 0))
    sun = bpy.context.active_object
    sun.name = "CelestialSun"
    sun.data.materials.append(mat_sun)

    # Moon Disk
    bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=4.0, depth=0.5, location=(0, 0, 0))
    moon = bpy.context.active_object
    moon.name = "CelestialMoon"
    moon.data.materials.append(mat_moon)

    return sun, moon

# -----------------------------------------------------------------------------
# 5b. CELESTIAL SKY DOME (Hemisphere for Procedural Sky Shader)
# -----------------------------------------------------------------------------
def build_celestial_dome():
    """Generates an inverted celestial sky dome (hemisphere) for the procedural sky shader."""
    print("[Blender] Building Celestial Sky Dome...")
    mat_dome = create_material("MatCelestialDome", (0.15, 0.15, 0.25, 1.0), roughness=1.0)
    
    # UV Sphere with radius 280.0
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=20, radius=280.0, location=(0, 0, 0))
    dome = bpy.context.active_object
    dome.name = "CelestialDome"
    
    # Invert normals so faces point inward toward the player
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.flip_normals()
    bpy.ops.object.mode_set(mode='OBJECT')
    
    dome.data.materials.append(mat_dome)
    return dome

# -----------------------------------------------------------------------------
# 6. STORM CLOUD DECKS (Upper Overhead Canopy & Lower Horizon Scud)
# -----------------------------------------------------------------------------
def build_storm_clouds_upper():
    """Generates an extensive 360-degree high overhead storm cloud canopy."""
    print("[Blender] Building Upper Storm Cloud Canopy (High Sky)...")
    mat_cloud = create_material("MatStormCloudUpper", (0.16, 0.16, 0.22, 1.0), roughness=0.95)
    
    parts = []
    
    # Apex overhead puffs (center of dome, Z ~ 115)
    for i in range(4):
        angle = i * (math.pi * 0.5) + 0.3
        cx = math.cos(angle) * 12.0
        cy = math.sin(angle) * 12.0
        cz = 112.0 + 4.0 * math.sin(i * 2.0)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=10, ring_count=7, radius=1.0, location=(cx, cy, cz))
        puff = bpy.context.active_object
        puff.scale = (26.0, 26.0, 10.0)
        puff.data.materials.append(mat_cloud)
        parts.append(puff)

    # Inner ring (R ~ 45, Z ~ 100)
    for i in range(8):
        angle = i * (2.0 * math.pi / 8.0) + 0.2
        r = 44.0 + 8.0 * math.sin(i * 1.9)
        cx = math.cos(angle) * r
        cy = math.sin(angle) * r
        cz = 98.0 + 6.0 * math.cos(i * 2.1)
        sx = 32.0 + 6.0 * math.sin(i * 1.3)
        sy = 30.0 + 5.0 * math.cos(i * 1.7)
        sz = 11.0 + 3.0 * math.sin(i * 2.5)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=10, ring_count=7, radius=1.0, location=(cx, cy, cz))
        puff = bpy.context.active_object
        puff.scale = (sx, sy, sz)
        puff.data.materials.append(mat_cloud)
        parts.append(puff)

    # Middle ring (R ~ 95, Z ~ 85)
    for i in range(12):
        angle = i * (2.0 * math.pi / 12.0) + 0.4
        r = 92.0 + 12.0 * math.cos(i * 1.5)
        cx = math.cos(angle) * r
        cy = math.sin(angle) * r
        cz = 84.0 + 7.0 * math.sin(i * 2.3)
        sx = 36.0 + 7.0 * math.cos(i * 1.4)
        sy = 32.0 + 6.0 * math.sin(i * 1.8)
        sz = 12.0 + 4.0 * math.cos(i * 2.7)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=10, ring_count=7, radius=1.0, location=(cx, cy, cz))
        puff = bpy.context.active_object
        puff.scale = (sx, sy, sz)
        puff.data.materials.append(mat_cloud)
        parts.append(puff)

    # Outer ring (R ~ 145, Z ~ 68)
    for i in range(16):
        angle = i * (2.0 * math.pi / 16.0) + 0.1
        r = 142.0 + 15.0 * math.sin(i * 1.2)
        cx = math.cos(angle) * r
        cy = math.sin(angle) * r
        cz = 68.0 + 8.0 * math.cos(i * 1.9)
        sx = 42.0 + 8.0 * math.sin(i * 1.1)
        sy = 36.0 + 7.0 * math.cos(i * 1.6)
        sz = 13.0 + 4.0 * math.sin(i * 2.2)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=10, ring_count=7, radius=1.0, location=(cx, cy, cz))
        puff = bpy.context.active_object
        puff.scale = (sx, sy, sz)
        puff.data.materials.append(mat_cloud)
        parts.append(puff)

    # Under-hanging dark storm lobes / mammatus pouches (Z ~ 74)
    for i in range(8):
        angle = i * (2.0 * math.pi / 8.0) + 0.7
        r = 65.0 + 20.0 * math.sin(i * 2.2)
        cx = math.cos(angle) * r
        cy = math.sin(angle) * r
        cz = 74.0 + 5.0 * math.cos(i * 1.7)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=8, ring_count=6, radius=1.0, location=(cx, cy, cz))
        pouch = bpy.context.active_object
        pouch.scale = (18.0, 18.0, 8.0)
        pouch.data.materials.append(mat_cloud)
        parts.append(pouch)

    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()

    cloud_obj = bpy.context.active_object
    cloud_obj.name = "StormCloudsUpper"
    return cloud_obj

def build_storm_clouds_lower():
    """Generates billowing mid/horizon storm cloud banks and jagged scud bands."""
    print("[Blender] Building Lower Horizon Storm Cloud Deck (Mid Sky)...")
    mat_cloud = create_material("MatStormCloudLower", (0.13, 0.13, 0.18, 1.0), roughness=0.9)
    
    parts = []
    
    # 1. Mid-Sky Storm Cloud Banks (R ~ 130, Z in 45..68)
    num_banks = 18
    for i in range(num_banks):
        angle = i * (2.0 * math.pi / num_banks)
        r = 125.0 + 14.0 * math.sin(i * 1.8)
        cx = math.cos(angle) * r
        cy = math.sin(angle) * r
        cz = 50.0 + 8.0 * math.cos(i * 2.4)
        
        # Base puff
        bpy.ops.mesh.primitive_uv_sphere_add(segments=10, ring_count=7, radius=1.0, location=(cx, cy, cz))
        base_puff = bpy.context.active_object
        base_puff.scale = (26.0 + 6.0 * math.sin(i), 20.0 + 5.0 * math.cos(i), 10.0 + 4.0 * math.sin(i * 1.5))
        base_puff.data.materials.append(mat_cloud)
        parts.append(base_puff)

        # Upper billow
        if i % 2 == 0:
            bpy.ops.mesh.primitive_uv_sphere_add(segments=8, ring_count=6, radius=1.0, location=(cx * 0.95, cy * 0.95, cz + 10.0))
            top_puff = bpy.context.active_object
            top_puff.scale = (18.0, 16.0, 9.0)
            top_puff.data.materials.append(mat_cloud)
            parts.append(top_puff)

    # 2. Jagged Scud Formations (R ~ 105, Z in 34..46)
    num_scud = 10
    for i in range(num_scud):
        angle = i * (2.0 * math.pi / num_scud) + 0.3
        r = 102.0 + 16.0 * math.sin(i * 2.1)
        cx = math.cos(angle) * r
        cy = math.sin(angle) * r
        cz = 38.0 + 5.0 * math.sin(i * 1.7)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=8, ring_count=6, radius=1.0, location=(cx, cy, cz))
        scud = bpy.context.active_object
        scud.scale = (20.0, 12.0, 5.0)
        scud.rotation_euler = (0.1 * math.sin(i), 0.1 * math.cos(i), angle + 0.4)
        scud.data.materials.append(mat_cloud)
        parts.append(scud)

    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()

    lower_obj = bpy.context.active_object
    lower_obj.name = "StormCloudsLower"
    return lower_obj

# -----------------------------------------------------------------------------
# MAIN PIPELINE EXECUTION
# -----------------------------------------------------------------------------
def main():
    # Resolve project root relative to this script
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(script_dir, "..", ".."))
    out_dir = os.path.join(project_root, "assets", "models", "skybox")
    os.makedirs(out_dir, exist_ok=True)
    print(f"[Blender] Output directory: {out_dir}")

    # 1. Clear scene
    clear_scene()

    # 2. Build Castle Tarmin
    castle = build_castle_tarmin()
    export_object_as_obj(castle, os.path.join(out_dir, "castle_tarmin.obj"))

    # 3. Build South Spire
    clear_scene()
    spire = build_south_spire()
    export_object_as_obj(spire, os.path.join(out_dir, "south_spire.obj"))

    # 4. Build Mountain Ring
    clear_scene()
    mountains = build_mountain_ring()
    export_object_as_obj(mountains, os.path.join(out_dir, "mountain_ring.obj"))

    # 5. Build West Cumulus
    clear_scene()
    cumulus = build_west_cumulus()
    export_object_as_obj(cumulus, os.path.join(out_dir, "west_cumulus.obj"))

    # 6. Build Sun & Moon
    clear_scene()
    sun, moon = build_celestial_bodies()
    export_object_as_obj(sun, os.path.join(out_dir, "celestial_sun.obj"))
    export_object_as_obj(moon, os.path.join(out_dir, "celestial_moon.obj"))

    # 6b. Build Celestial Sky Dome
    clear_scene()
    dome = build_celestial_dome()
    export_object_as_obj(dome, os.path.join(out_dir, "celestial_dome.obj"))

    # 7. Build Storm Cloud Decks
    clear_scene()
    clouds_upper = build_storm_clouds_upper()
    export_object_as_obj(clouds_upper, os.path.join(out_dir, "storm_clouds_upper.obj"))

    clear_scene()
    clouds_lower = build_storm_clouds_lower()
    export_object_as_obj(clouds_lower, os.path.join(out_dir, "storm_clouds_lower.obj"))

    # 8. Assemble Complete Scene and Save .blend Master File
    clear_scene()
    c = build_castle_tarmin()
    c.location = (0, 110, 0)
    c.scale = (2.025, 2.025, 2.025)
    c.rotation_euler = (0, 0, 0)

    s = build_south_spire()
    s.location = (0, -110, 0)
    s.scale = (1.875, 1.875, 1.875)

    m = build_mountain_ring()

    w = build_west_cumulus()
    w.location = (-130, 0, 0)
    w.rotation_euler = (0, 0, -math.pi * 0.5)

    cu = build_storm_clouds_upper()
    cl = build_storm_clouds_lower()

    blend_file = os.path.join(out_dir, "skybox_landmarks.blend")
    bpy.ops.wm.save_as_mainfile(filepath=blend_file)
    print(f"[Blender] Master Blend file saved: {blend_file}")
    print("[Blender] Skybox Landmark pipeline complete!")

if __name__ == "__main__":
    main()
