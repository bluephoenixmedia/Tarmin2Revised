"""
bake_theme_props.py

Batch-renders the licensed Synty prop meshes into front-facing billboard PNGs
for the themed chunk system.

The 2.5D renderer draws scenery as billboards through DynamicQuadBatcher, so
props ship as PNGs rather than as .obj meshes: dozens of per-prop .obj draws
would undo what WorldMeshCache exists to protect, and flat-shaded untextured
low-poly would clash with the hand-drawn monster sprites.

Follows the same pattern as process_skullgate.py / process_castle_citadel.py.

Run:
    "C:/Program Files/Blender Foundation/Blender 5.1/blender.exe" ^
        --background --python tools/blender/bake_theme_props.py

Outputs to assets/images/props/<propId>.png, matching the ids in
assets/data/props.json.
"""

import math
import os
import sys

import bpy
from mathutils import Vector

# --- Paths -----------------------------------------------------------------

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GAME_ASSETS = os.path.join(REPO, "docs", "game_assets")
OUT_DIR = os.path.join(REPO, "assets", "images", "props")

GOBLIN = os.path.join(GAME_ASSETS, "POLYGON_Goblin_War_Camp_SourceFiles_v3")
VIKING = os.path.join(GAME_ASSETS, "POLYGON_Viking_Realm_SourceFiles_v3", "SourceFiles")
ALPINE = os.path.join(GAME_ASSETS, "POLYGON_NatureBiomes_AlpineMountain_SourceFiles_v3")
ADVENTURE = os.path.join(GAME_ASSETS, "POLYGON_Adventure_Pack_SourceFiles_v6")

GOBLIN_TEX = os.path.join(GOBLIN, "Textures", "Alts", "PolygonGoblinWarCamp_Texture_01_A.png")
VIKING_TEX = os.path.join(VIKING, "Textures", "Alts", "PolygonVikingRealm_Texture_01_A.png")
ADVENTURE_TEX = os.path.join(ADVENTURE, "Textures", "PolyAdventureTexture_01.png")

RESOLUTION = 256

# --- Prop table ------------------------------------------------------------
# (propId, fbx path, texture path). propId must match assets/data/props.json.

PROPS = [
    # Blood Colosseum
    ("gibbet_cage",       os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Gibbet_Cage_01.fbx"),      GOBLIN_TEX),
    ("head_spike",        os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Head_Spike_01.fbx"),       GOBLIN_TEX),
    ("skull_pile",        os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Skull_Pile_01.fbx"),       GOBLIN_TEX),
    ("arena_chain",       os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Chain_01.fbx"),            GOBLIN_TEX),
    ("defence_spikes",    os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Defence_Spikes_01.fbx"),   GOBLIN_TEX),
    ("war_banner",        os.path.join(GOBLIN, "FBX", "Buildings", "SM_Bld_Part_Banner_01.fbx"),   GOBLIN_TEX),

    # Flooded Caverns
    ("stalagmite",        os.path.join(ADVENTURE, "FBX", "SM_Env_Stalagmite_01.fbx"),              ADVENTURE_TEX),
    ("algae_mat",         os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Algae_01.fbx"),       GOBLIN_TEX),
    ("swamp_reeds",       os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Reeds_01.fbx"),       GOBLIN_TEX),
    ("drowned_cache",     os.path.join(VIKING, "FBX", "SM_Prop_Chest_01.fbx"),                     VIKING_TEX),

    # Wandering Battalion
    ("camp_tent",         os.path.join(GOBLIN, "FBX", "Buildings", "SM_Bld_Tent_Medium_01.fbx"),   GOBLIN_TEX),
    ("weapon_rack",       os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Rack_Weapon_01.fbx"),      GOBLIN_TEX),
    ("brazier",           os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Brazier_01.fbx"),          GOBLIN_TEX),
    ("campfire",          os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Camp_Fire_01.fbx"),        GOBLIN_TEX),
    ("helmet_pile",       os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Helmet_Pile_01.fbx"),      GOBLIN_TEX),
    ("war_drum",          os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Drum_01.fbx"),             GOBLIN_TEX),

    # Makeshift Graveyard
    ("gravestone",        os.path.join(VIKING, "FBX", "SM_Prop_RuneStone_02.fbx"),                 VIKING_TEX),
    ("grave_mound",       os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Swamp_Mound_01.fbx"), GOBLIN_TEX),
    ("bone_pile",         os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Bones_01.fbx"),       GOBLIN_TEX),
    ("cairn",             os.path.join(VIKING, "FBX", "SM_Prop_Cairn_01.fbx"),                     VIKING_TEX),
    ("runestone",         os.path.join(VIKING, "FBX", "SM_Prop_RuneStone_01.fbx"),                 VIKING_TEX),

    # Ruined Castle
    ("ruined_pillar",     os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Ruins_Pillar_01.fbx"),     GOBLIN_TEX),
    ("rubble_pile",       os.path.join(GOBLIN, "FBX", "Props", "SM_Prop_Ruins_Damaged_01.fbx"),    GOBLIN_TEX),
    ("throne",            os.path.join(VIKING, "FBX", "SM_Prop_Throne_01.fbx"),                    VIKING_TEX),
    ("shield_wall",       os.path.join(VIKING, "FBX", "SM_Wep_Shield_Set_01.fbx"),                 VIKING_TEX),
    ("chandelier",        os.path.join(VIKING, "FBX", "SM_Prop_Chandelier_01.fbx"),                VIKING_TEX),

    # Overgrown Thicket
    ("glowing_mushroom",  os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Mushroom_01.fbx"),         GOBLIN_TEX),
    ("mushroom_cluster",  os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Mushroom_Group_01.fbx"),   GOBLIN_TEX),
    ("bramble",           os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Grass_Tall_01.fbx"),       GOBLIN_TEX),
    ("hanging_vine",      os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Vine_01.fbx"),             GOBLIN_TEX),
    ("twisted_root",      os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Root_03.fbx"),             GOBLIN_TEX),
    ("heart_bloom",       os.path.join(GOBLIN, "FBX", "Environment", "SM_Env_Mushroom_Group_02.fbx"),   GOBLIN_TEX),
]


# --- Scene setup -----------------------------------------------------------

def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene

    # EEVEE is plenty for flat-lit low-poly and is an order of magnitude faster
    # than Cycles across 30+ props. The engine id moved in 4.2.
    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue

    scene.render.resolution_x = RESOLUTION
    scene.render.resolution_y = RESOLUTION
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'

    # A neutral world so nothing renders pure black.
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
    """
    Imports and drops Synty's baked shadow meshes.

    Synty ships a `<name>_Shadow` companion mesh on many props, wearing an
    untextured `Dark_Fade` material. Rendered head-on with a transparent film
    it becomes a solid black slab under the prop, which is exactly what the
    first bake produced beneath the tents.
    """
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


def strip_suffix(name):
    """Blender appends .001 on name collisions; the MaterialList does not."""
    if len(name) > 4 and name[-4] == '.' and name[-3:].isdigit():
        return name[:-4]
    return name


def atlas_key(filename):
    """
    Reduces an albedo filename to the (index, variant) pair that identifies the
    atlas, so `PolygonGoblinWarCamp_01_A.png` from the MaterialList matches
    `PolygonGoblinWarCamp_Texture_01_A.png` on disk.
    """
    stem = os.path.splitext(os.path.basename(filename))[0]
    parts = stem.split('_')
    for i in range(len(parts) - 1):
        if parts[i].isdigit() and len(parts[i + 1]) == 1 and parts[i + 1].isalpha():
            return (parts[i], parts[i + 1].upper())
    return None


def build_texture_index(pack_root):
    """
    Indexes a pack's textures two ways.

    Most meshes share a numbered atlas, addressed by (index, variant). But some
    carry a dedicated sheet instead -- `Algae_02.tga`, `Banner_01.png` -- whose
    names the atlas heuristic cannot parse. Indexing by bare stem as well lets
    the MaterialList's exact filename win before the heuristic is consulted,
    which is what stopped the algae mats and banners rendering as a slice of
    the atlas colour-swatch grid.

    Returns (atlasIndex, stemIndex).
    """
    atlas_index = {}
    stem_index = {}
    tex_root = os.path.join(pack_root, "Textures")
    if not os.path.isdir(tex_root):
        return atlas_index, stem_index

    for dirpath, _dirnames, filenames in os.walk(tex_root):
        for fn in filenames:
            low = fn.lower()
            if not low.endswith((".png", ".tga")):
                continue
            if "normal" in low or "emissive" in low or "metallic" in low:
                continue

            full = os.path.join(dirpath, fn)
            stem = os.path.splitext(fn)[0].lower()
            stem_index.setdefault(stem, full)

            key = atlas_key(fn)
            if key and key not in atlas_index:
                atlas_index[key] = full
    return atlas_index, stem_index


def parse_material_list(pack_root):
    """
    Reads Synty's MaterialList_*.txt into {meshName: [albedoFile, ...]} in slot
    order. This is the only authoritative record of which atlas each mesh wants;
    the material names that survive FBX import ("MAT_01A", "Ayslum") are not.
    """
    listing = None
    for fn in os.listdir(pack_root):
        if fn.startswith("MaterialList") and fn.endswith(".txt"):
            listing = os.path.join(pack_root, fn)
            break
    if listing is None:
        for dirpath, _d, filenames in os.walk(pack_root):
            for fn in filenames:
                if fn.startswith("MaterialList") and fn.endswith(".txt"):
                    listing = os.path.join(dirpath, fn)
                    break
            if listing:
                break
    if listing is None:
        return {}

    mapping = {}
    current_mesh = None
    with open(listing, "r", encoding="utf-8", errors="ignore") as fh:
        for raw in fh:
            line = raw.strip()
            if line.startswith("Mesh Name:"):
                current_mesh = line.split(":", 1)[1].strip()
                mapping.setdefault(current_mesh, [])
            elif line.startswith("Albedo:") and current_mesh:
                albedo = line.split(":", 1)[1].strip().split(" ")[0]
                mapping[current_mesh].append(albedo)
    return mapping


def load_image(path):
    """
    Loads an atlas, forcing a reload when Blender hands back a datablock with no
    pixel data.

    The FBX importer registers image datablocks for textures it could not find.
    `check_existing=True` then matches one of those broken placeholders by
    filepath and returns it unloaded, which renders as missing-texture magenta:
    that is what turned the gibbet cages flat purple.
    """
    # Always a fresh datablock. check_existing=True matches the importer's
    # placeholder by filepath and hands it back unloaded, and reload() does not
    # revive it; a fresh load decodes the file properly every time.
    image = bpy.data.images.load(path, check_existing=False)
    if not image.has_data:
        try:
            image.reload()
        except RuntimeError:
            pass
    return image


def resolve_albedo(albedo_name, atlas_index, stem_index):
    """Exact filename first, then the numbered-atlas heuristic."""
    if not albedo_name:
        return None
    stem = os.path.splitext(os.path.basename(albedo_name))[0].lower()
    if stem in stem_index:
        return stem_index[stem]
    key = atlas_key(albedo_name)
    if key and key in atlas_index:
        return atlas_index[key]
    return None


def apply_texture(meshes, fallback_texture, material_map, texture_index):
    """
    Wires the correct texture into each material slot, per the pack's MaterialList.

    Synty props routinely draw from more than one sheet (a gibbet's timber and
    its ironwork live on different atlases; banners and algae have dedicated
    textures entirely), so forcing a single texture onto every slot produced
    the flat untextured cages in the first bake.
    """
    atlas_index, stem_index = texture_index

    fallback_image = None
    if os.path.exists(fallback_texture):
        fallback_image = load_image(fallback_texture)

    for obj in meshes:
        slots = material_map.get(strip_suffix(obj.name), [])

        if not obj.data.materials:
            mat = bpy.data.materials.new(name="PropMat")
            mat.use_nodes = True
            obj.data.materials.append(mat)

        for slot_index, mat in enumerate(obj.data.materials):
            if mat is None:
                continue

            image = None
            if slot_index < len(slots):
                resolved = resolve_albedo(slots[slot_index], atlas_index, stem_index)
                if resolved:
                    image = load_image(resolved)
            if image is None:
                image = fallback_image
            if image is None:
                continue

            # Rebuild the material as flat emissive albedo.
            #
            # Shading the prop here would bake a light direction into a sprite
            # the engine then lights again with its own torch falloff, and the
            # first bake proved the point: a lit Principled setup blew the
            # low-poly meshes out to near-white. Emission gives exactly the
            # atlas colour, which is what a billboard wants.
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

            # Pin the albedo UV layer explicitly. Synty meshes ship a second
            # `lightmapUV` set, and when that one happens to be active for
            # render the texture samples the atlas's colour-swatch grid instead
            # of the artwork, which is what striped the algae mats and banners
            # with a rainbow.
            if obj.data.uv_layers:
                uv_node = tree.nodes.new("ShaderNodeUVMap")
                uv_node.uv_map = obj.data.uv_layers[0].name
                tree.links.new(uv_node.outputs["UV"], tex_node.inputs["Vector"])

            # Drive transparency from the texture's own alpha. Synty's foliage
            # and algae are alpha cut-outs on a quad; without this the quad
            # renders as a solid black card around the leaves.
            tree.links.new(tex_node.outputs["Color"], emission.inputs["Color"])
            tree.links.new(tex_node.outputs["Alpha"], mix.inputs["Fac"])
            tree.links.new(transparent.outputs["BSDF"], mix.inputs[1])
            tree.links.new(emission.outputs["Emission"], mix.inputs[2])
            tree.links.new(mix.outputs["Shader"], output.inputs["Surface"])

            # Respect the atlas alpha so cut-out foliage keeps its silhouette.
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


def frame_camera(meshes):
    """
    Orthographic camera framed on the prop's bounding box.

    Flat ground props (algae mats, grave mounds, roots) are horizontal planes:
    shot dead-on from the front they collapse to a one-pixel sliver, which is
    what the first bakes produced. When a prop is much wider than it is tall the
    camera pitches down, matching how the player actually sees the floor in a
    first-person view.
    """
    min_v, max_v = bounds_of(meshes)
    center = (min_v + max_v) * 0.5
    size = max_v - min_v

    footprint = max(size.x, size.y, 0.001)
    is_floor_prop = size.z < 0.45 * footprint
    pitch_down = math.radians(52) if is_floor_prop else 0.0

    extent = max(size.x, size.z, size.y * math.sin(pitch_down), 0.001) * 1.18

    cam_data = bpy.data.cameras.new("PropCam")
    cam_data.type = 'ORTHO'
    cam_data.ortho_scale = extent

    cam = bpy.data.objects.new("PropCam", cam_data)
    bpy.context.scene.collection.objects.link(cam)
    bpy.context.scene.camera = cam

    # Orbit the camera up and back around the prop centre by the pitch angle.
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


def pack_root_for(fbx_path):
    """Walks up from an FBX to the pack directory that owns its MaterialList."""
    path = os.path.dirname(os.path.abspath(fbx_path))
    for _ in range(6):
        if os.path.isdir(os.path.join(path, "Textures")):
            return path
        parent = os.path.dirname(path)
        if parent == path:
            break
        path = parent
    return os.path.dirname(os.path.abspath(fbx_path))


# Pack metadata is read once per pack, not once per prop.
_pack_cache = {}


def pack_metadata(fbx_path):
    root = pack_root_for(fbx_path)
    if root not in _pack_cache:
        _pack_cache[root] = (parse_material_list(root), build_texture_index(root))
    return _pack_cache[root]


def bake(prop_id, fbx_path, texture_path):
    if not os.path.exists(fbx_path):
        return False, "fbx missing: %s" % fbx_path

    material_map, texture_index = pack_metadata(fbx_path)

    reset_scene()
    add_lighting()

    meshes = import_fbx(fbx_path)
    if not meshes:
        return False, "no mesh in fbx"

    apply_texture(meshes, texture_path, material_map, texture_index)
    frame_camera(meshes)

    out_path = os.path.join(OUT_DIR, "%s.png" % prop_id)
    render_to(out_path)
    return os.path.exists(out_path), out_path


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    ok, failed = [], []
    for prop_id, fbx_path, texture_path in PROPS:
        print("=== baking %s" % prop_id)
        try:
            success, detail = bake(prop_id, fbx_path, texture_path)
        except Exception as exc:  # a single bad mesh must not stop the batch
            success, detail = False, "exception: %s" % exc

        if success:
            ok.append(prop_id)
            print("    -> %s" % detail)
        else:
            failed.append((prop_id, detail))
            print("    !! %s" % detail)

    print("\n================ BAKE REPORT ================")
    print("baked   : %d" % len(ok))
    print("failed  : %d" % len(failed))
    for prop_id, reason in failed:
        print("  - %s: %s" % (prop_id, reason))

    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
