# **Architecture of Dynamic 2D RPG Paperdoll Systems: Resolving Perspective, Occlusion, and Asset Scaling in LibGDX**

Attempting to project standalone 2D inventory icons directly onto an orthographic front-facing paperdoll via algorithmic image warping is mathematically impossible and visually catastrophic. Standalone inventory icons illustrated at a 3/4 isometric perspective encode non-planar surface normals, directional lighting vectors, and out-of-plane self-occlusions. Planar transformations—such as affine maps, homographies, and thin-plate splines—operate exclusively on two-dimensional coordinates; they cannot hallucinate surfaces culled during perspective projection, nor can they reconstruct the cylindrical enclosing geometry required to wrap armor plates around human limbs. Furthermore, slicing paired items drawn together on an icon sheet discards foreshortening geometry, leaving detached metal surfaces floating beside the character silhouette.

Landmark RPGs faced this exact combinatorial scaling crisis and universally rejected 1:1 sprite generation for every piece of loot. Classic engines—including Blizzard North’s *Diablo II* and BioWare’s Infinity Engine (*Baldur's Gate*)—decoupled inventory UI icons from on-character visual representations1. They resolved asset scaling by implementing **visual archetype tiering**, mapping dozens of database items onto a compact set of canonical silhouettes (such as Light, Medium, and Heavy armor classes)3 and modulating surface appearance through dynamic palette lookup tables (PLTs) and colormaps4. When inventory icons are allowed to convey intricate narrative details (e.g., rust, inscriptions, crude welds) while the on-character paperdoll reflects macro-silhouette weight and material palettes, production demands drop by orders of magnitude while preserving player immersion.

For an indie desktop project in LibGDX targeting a dark fantasy aesthetic, the optimal production architecture is a **Decoupled Visual Archetype System** executed through an offline **Hybrid 3D-to-2D Render Baking Pipeline** (with an optional ControlNet generative inpainting track). By consolidating 117 database definitions into 26 canonical paperdoll overlay silhouettes, fitting proxy 3D geometry over a calibrated orthographic mannequin, and rendering them via headless Blender scripts with custom non-photorealistic (NPR) shaders6, asset generation is reduced from hundreds of hours of manual labor to approximately 35 hours of reproducible pipeline execution. At runtime, memory bloat and fill-rate waste are eliminated by stripping transparent whitespace via texture atlas dictionaries, resolving facial feature clashing through bitmask occlusion flags, and caching the composite character into an offscreen FrameBuffer (FBO) to ensure zero multi-layer overdraw during standard frame rendering8.

## **The Mathematical and Geometric Limits of 2D Sprite Warping**

The failure of programmatic batch scripts to map standalone inventory icons onto an orthographic front-facing body stems from fundamental limits of projective geometry and computer graphics. Evaluating why planar transformations fail demonstrates why this problem cannot be solved in post-processing.

### **Projective Perspective Inversion and Missing Geometric Manifolds**

A 2D sprite is a discrete array of radiance samples ![][image1] (RGBA) captured from a specific camera ray direction ![][image2]. When an inventory icon is illustrated at an axonometric 3/4 perspective (typically yawed 30° to 45° and pitched downward), the surface normal vectors ![][image3] of the equipment are oriented away from the viewing plane. Under orthographic or perspective projection ![][image4], all surface regions where ![][image5] are culled.

Planar homographies and non-rigid deformation algorithms—including Thin-Plate Splines (TPS), Moving Least Squares (MLS), and Spine2D mesh deformations—apply a continuous mapping function ![][image6] to the visible source coordinates:

![][image7]

where the radial basis kernel is defined as ![][image8], ![][image9] denotes control points, and ![][image10] represents deformation weights.

While this mapping can shear, rotate, and compress visible pixels, it operates strictly on a planar domain. It cannot generate the missing non-planar surfaces required when an object is rotated into an orthographic front-facing orientation (![][image11]). Forcing a 3/4 icon flat results in extreme distortion:

* Circular shapes (bosses, studs, visor rivets) deform into distorted ellipses.  
* Symmetrical features (nasal guards, eye slits, breastplate ridges) curve along the original perspective vanishing lines rather than aligning with the character's vertical sagittal axis.  
* Medial surfaces that were fully occluded in the 3/4 view (such as the interior curve of a right pauldron or the inner seam of a vambrace) do not exist in the raster data, forcing the algorithm to stretch edge pixels into blurred streaks.

### **Directional Lighting Vector Divergence**

In painterly 2D art, illumination is permanently baked into the RGB color channels:

![][image12]

The exitant radiance ![][image13] combines the bidirectional reflectance distribution function (BRDF) ![][image14] and incident lighting ![][image15]. An artist painting an isometric icon typically employs a local key-fill-rim setup (for example, top-left key lighting relative to the turned item). When that armor asset is mapped onto a front-facing character, its baked lighting vector ![][image16] remains locked to the icon's local reference frame rather than the character's global coordinate space:

![][image17]

If the master character portrait is illuminated by a diffused top-center directional light with symmetrical specular falloff, an armor piece warped from a 3/4 asset will exhibit strong asymmetrical rim lighting and cast drop shadows on surfaces that should be fully exposed to the environment. The human eye identifies this lighting divergence instantly, registering the equipment as a flat sticker hovering over the character rather than an object sharing physical space.

### **Geometric Disconnection of Paired Assets**

Inventory icons for boots, gauntlets, and pauldrons frequently illustrate both items in a dynamic arrangement, with the foreground limb partially occluding the background limb to convey depth. Slicing these sheets introduces critical geometric inconsistencies:

* **Asymmetrical Foreshortening:** In an isometric illustration, the foreground boot is rendered larger than the background boot to simulate perspective depth. Segmenting the sheet produces two boots of unequal scale, thickness, and ankle circumference.  
* **Absence of Cylindrical Enclosure:** Limb armor must conform to cylindrical or conical volumes (the forearm, calf, and thigh). Standalone icons show only the lateral outer profile. When mapped onto a front-facing limb, the overlay lacks the medial contour and enclosing straps, leaving raw, open geometry hanging in midair beside the character's limbs.

## **Industry Paradigms: How Landmark RPGs Scaled Visual Equipment**

The problem of outfitting modular RPG characters without generating thousands of bespoke full-body sprites was solved in classic and modern game development through asset decoupling, visual tiering, and palette modification.

&nbsp;

| Game / Engine | Visual Mapping Strategy | Slot Separation Architecture | Engine Recolor / Variation Mechanism |
| :---- | :---- | :---- | :---- |
| **Diablo II** (Blizzard North) | Visual Archetype Tiering: collapsed all body armors into 3 canonical models (LIT, MED, HVY)3. | Full decoupling: unique painterly grid icons; componentized 2D baked sprites (TR, LG, RA, LA, HD, SH)2. | Palette Translation Tables (transtbl colormaps) mapped over indexed/grayscale animation frames3. |
| **Baldur's Gate I & II** (BioWare / Infinity Engine) | Armor Tiering: 4 broad visual levels (Robes/Unarmored, Leather, Chain, Plate)1. | Full decoupling: distinct BAM inventory icons; shared composite paperdoll sprites5. | Dynamic Palette Lookup Tables (.plt files) mapped directly onto indexed sprite layers5. |
| **Ultima VII: Serpent Isle** (Origin Systems) | Paperdoll Shape Indexing: fixed body equipment coordinates using shared silhouette archetypes13. | Direct interaction: icons functioned as paperdoll elements, but item classes shared base shapes14. | Static 8-bit VGA indexed color palettes with localized palette cycling13. |
| **Fallout 1 & 2** (Black Isle / Interplay) | Complete Armor Swapping: entire sprite sheets swapped based on 5 armor tiers (Jacket, Leather, Metal, Combat, Power). | Decoupled high-detail inventory icons; full-body pre-rendered 3D-to-2D sprites. | Hard-baked color variations per sprite sheet tier; weapons swapped via directional animation sets. |
| **Pillars of Eternity** (Obsidian Entertainment) | Full-Suit Mesh Swapping: eliminated micro-modular gloves and boots to prevent visual clutter17. | Decoupled 2D UI concept art icons; dynamic 3D assembled model in paperdoll UI17. | Real-time PBR material tinting and texture masking in Unity17. |
| **Dark Souls / Gothic** (FromSoftware / Piranha Bytes) | Complete abandonment of 2D paperdoll layering; static/animated UI models paired with isolated icons. | Clean separation: 2D slot icons show item fidelity; 3D viewport renders equipped character. | Material shaders and runtime 3D skeletal mesh attachment. |

### **Diablo II: The Componentized Animation Token Architecture**

Blizzard North’s implementation in *Diablo II* remains an authoritative case study in high-performance 2D composite equipment rendering. The engine divided character animation into discrete tokens: HD (Head), TR (Torso), LG (Legs), RA (Right Arm), LA (Left Arm), RH (Right Hand), LH (Left Hand), and SH (Shield)2.

Despite containing dozens of distinct chest armors in armor.txt (Quilted Armor, Leather, Studded Leather, Ring Mail, Scale Mail, Chain Mail, Breast Plate, Splint Mail, Plate Mail, Field Plate, Gothic Plate, Full Plate, Ancient Armor), the engine never generated unique walking, running, and attacking sprite sheets for each item. Instead, via ItemTypes.txt and ArmType.txt, all torso armors were mapped to exactly three visual component tokens3:

> 1. **LIT (Light):** Quilted, Leather, Hard Leather, Studded Leather.  
> 2. **MED (Medium):** Ring Mail, Scale Mail, Chain Mail, Breast Plate.  
> 3. **HVY (Heavy):** Splint Mail, Plate Mail, Field Plate, Gothic Plate, Full Plate.

When a character equipped an "Ancient Armor," the engine loaded the HVY torso and leg sprite layers3. Visual distinctiveness across unique, rare, or magic items was achieved using **Palette Translation Tables** (transtbl)4. The underlying DCC/DC6 sprite frames were pre-rendered 3D-to-2D graphics using specific indexed color bands3. By shifting the color lookup table in memory, the engine tinted the armor gold, emerald green, dark crimson, or ethereal blue3. The player’s psychological perception of high loot variety was anchored by the high-resolution, uniquely illustrated inventory slot icon, whereas the on-character representation was an archetype silhouette that guaranteed anatomical fit and correct animation playback.

### **BioWare’s Infinity Engine: BAMs and PLT Recoloring**

In *Baldur's Gate*, BioWare addressed the same combinatorial challenge across multiple player races and genders. The engine utilized distinct asset formats:

* **Inventory Icons:** Stored as standalone BioWare Animation Format (.bam) files5, painted with fine, descriptive details (engravings, jewels, heraldry).  
* **Paperdoll Sprites:** Displayed in the inventory screen, tiered strictly into four visual armor levels: Level 1 (Robes/Unarmored), Level 2 (Leather/Hide), Level 3 (Chainmail/Splint), and Level 4 (Full Plate)1.

The inventory paperdoll was rendered using indexed .plt (Palette Lookup Table) textures5. A single base grayscale sprite was dynamically colored at runtime using the character’s primary and secondary clothing/skin color selections and the item’s internal material color index5. Hundreds of unique magical items shared fewer than ten underlying visual paperdoll templates per character class.

### **Pillars of Eternity: Abandoning Micro-Modular Meshes**

When Obsidian designed *Pillars of Eternity*, lead character artist Dimitri Berman initially attempted to allow players to equip and visually swap micro-modular equipment (individual boots, gauntlets, and pauldrons) directly on the character models17. The system was scrapped prior to launch due to severe engine instability, geometric clipping, and silhouette visual noise17. Obsidian opted to unify character armor into comprehensive body suits17. The design team recognized that fine visual equipment details—such as buckle designs, leather stitching, and small pouches—are best communicated through 2D inventory icons, while the avatar must prioritize macro-silhouette legibility and structural cohesion17.

### **Criteria for Abandoning the Layered Paperdoll**

Eliminating the dynamic paperdoll in favor of a static, high-fidelity hero portrait (the paradigm used in *Gothic*, *Dark Souls*, and classic tabletop cRPGs) becomes the optimal design choice when specific production and aesthetic thresholds are crossed:

* **Narrative Priority on Character Aging and Trauma:** If the protagonist's emotional state, facial scars, weeping eyes, and weary expressions are the primary narrative drivers, a single hand-painted master portrait—or an evolving portrait that updates at major story milestones—communicates emotional weight far more effectively than modular sprites.  
* **Aesthetic Superiority of Slot Icons:** When the user interface features large, detailed inventory slot icons surrounding the portrait, the player's imagination bridges the gap. In *Dark Souls*, the inventory screen displays the character model in its current equipment, but the rich history of the item is digested entirely through the 2D icon and accompanying lore text.  
* **Engineering and Art Budget Preservation:** If allocating 40 hours to modular paperdoll production directly threatens core combat mechanics, enemy AI, or level design, abandoning dynamic paperdoll layering is the correct indie triage decision.

However, because the project's central aesthetic pillar is visual "desperation"—manifested specifically by the comedic and poignant contrast of wearing mismatched scraps (e.g., a rusty kettle helm paired with patched hide greaves and an ancient hoplite shield)—a modular paperdoll remains essential. To make this production feasible, the project must implement archetype tiering.

## **Architectural Strategy: Visual Archetype Tiering and Dynamic Shaders**

To eliminate the manual labor bottleneck for 117 items while realizing the narrative pillar of a desperate father scrounging through a brutal labyrinth, the equipment architecture must decouple the **Inventory Slot Icon** from the **Paperdoll Overlay Sprite**.

The inventory icon remains an individual, highly detailed 2D illustration conveying rust, improvised repairs, and provenance. On the central golden portrait paperdoll, those 117 items map to a consolidated visual taxonomy of **canonical silhouette archetypes**.

### **Canonical Equipment Taxonomy**

The structural relationship between the database definitions, UI icons, and paperdoll visual layers operates via functional decoupling. Each equipment item in the database references both a unique inventory icon asset and a shared paperdoll archetype identifier. The runtime renderer looks up the archetype sprite, applies the appropriate material palette row, and layers the resulting visual onto the character frame.

| Equipment Slot | Total Items | Canonical Paperdoll Archetypes | Shared Silhouette Categories | Material / Tint Variants |
| :---- | :---- | :---- | :---- | :---- |
| **Head** | 18 | 4 Archetypes | Open Coif / Hood; Nasal Barbute; Rusted Greathelm; Improvised Pot/Kettle | Cast Iron, Rusted Steel, Boiled Leather, Brass |
| **Torso** | 22 | 4 Archetypes | Scavenged Padded Tunic; Crude Banded/Wooden Mail; Riveted Scale Cuirass; Battered Heavy Plate | Rotten Cloth, Weathered Leather, Rusted Iron, Scratched Bronze |
| **Arms/Pauldrons** | 16 | 3 Archetypes | Asymmetrical Scrap Plate (Right only); Tied Leather/Wood Spaulders; Heavy Iron Guards | Raw Hide, Chipped Steel, Darkened Iron |
| **Hands/Gauntlets** | 12 | 2 Archetypes | Wrapped Burlap/Leather Bindings; Articulated Plate Mitten | Stained Canvas, Hard Leather, Tarnished Steel |
| **Legs** | 16 | 3 Archetypes | Patched Trousers; Studded Leather Greaves; Segmented Iron Poleyns | Ragged Wool, Muddy Hide, Rusted Iron |
| **Feet** | 12 | 2 Archetypes | Tied Scavenger Wraps; Heavy Hobnail Riding Boots | Decayed Leather, Cured Black Leather, Iron-Toed |
| **Shield (Off)** | 9 | 3 Archetypes | Improvised Plank Buckler; Ancient Bronze Hoplite; Reinforced Wooden Heater | Weathered Oak, Verdigris Bronze, Battered Metal |
| **Weapon (Main)** | 12 | 3 Archetypes | Crude Scraps/Machete/Club; Arming Sword/Spear; Heavy Cleaver/Axe | Rusted Steel, Chipped Obsidian, Forged Iron |
| **Cloak** | 8 | 2 Archetypes | Torn Scavenger Rags (Back/Front); Tattered Traveler's Mantle | Faded Charcoal, Stained Burlap, Blood-Soaked Crimson |
| **Total** | **117** | **26 Total Silhouettes** | — | — |

By consolidating 117 items into **26 canonical silhouettes**, the art asset requirement drops from 117 full-body calibrated overlays to 26 base sprite assets.

### **Dynamic Palette Swapping via GLSL Shaders**

To prevent 22 chest armors from looking identical across their shared archetype silhouettes, surface color and wear are modulated dynamically using an indexed color-ramp shader in LibGDX20.

The canonical overlay sprites are authored in grayscale:

* **Red Channel (![][image18]):** Encodes tonal luminance from shadow to highlight (![][image19]).  
* **Green Channel (![][image20]):** Encodes a material zone identifier (![][image21] for primary plating, ![][image22] for secondary leather straps, ![][image23] for trim or underlying cloth)22.

At runtime, a 2D lookup texture (palette table) containing material gradient columns is sampled by the fragment shader21. The horizontal coordinate corresponds to the sprite's luminance, while the vertical coordinate is set by the item's material ID:

&nbsp;

&nbsp;

&nbsp;

OpenGL Shading Language

// GLSL Fragment Shader: palette\_swap.frag  
\#ifdef GL\_ES  
precision mediump float;  
\#endif

varying vec4 v\_color;  
varying vec2 v\_texCoords;

uniform sampler2D u\_texture;       // Grayscale Archetype Sprite (R=Luminance, G=Material Zone)  
uniform sampler2D u\_paletteTable;  // 256 x 16 Material Ramp Matrix  
uniform float u\_paletteRow;        // Row index normalized to \[0.0, 1.0\]

void main() {  
    vec4 texColor \= texture2D(u\_texture, v\_texCoords);  
&nbsp;&nbsp;&nbsp;&nbsp;  
    // Immediately discard fully transparent pixels to preserve depth and fill-rate  
    if (texColor.a \< 0.01) {  
        discard;  
    }  
&nbsp;&nbsp;&nbsp;&nbsp;  
    // Sample the palette table: X \= luminance ramp, Y \= material variant  
    float rampU \= texColor.r;  
    float rampV \= u\_paletteRow;  
&nbsp;&nbsp;&nbsp;&nbsp;  
    vec4 finalColor \= texture2D(u\_paletteTable, vec2(rampU, rampV));  
    gl\_FragColor \= vec4(finalColor.rgb \* v\_color.rgb, texColor.a \* v\_color.a);  
}

This single shader enables a single "Crude Banded Chestpiece" silhouette to render as:

> 1. *Rusted Scrap Metal:* Dark oxidization in shadows, orange-red iron rust highlights.  
> 2. *Decayed Crypt Wood:* Desaturated gray-brown grain with green rot accents.  
> 3. *Ancient Mycenaean Bronze:* Tarnished greenish-cyan verdigris with dull metallic bronze crests.

## **Automated Asset Generation: Generative Inpainting vs. 3D Render Baking**

To author the 26 canonical archetype overlays with pixel-perfect alignment to the 1024×1536 master character canvas, manual inpainting in image editing software is replaced by an automated production pipeline. Two technical options exist: headless AI inpainting via ComfyUI and orthographic 3D render baking via Blender.

### **ComfyUI Automated Inpainting Pipeline**

For an art pipeline focused purely on 2D painted assets, a headless batch workflow using ComfyUI with Stable Diffusion XL (SDXL) or Flux, ControlNet, and LayerDiffuse produces transparent, perspective-aligned layers24.

The pipeline operates through sequential execution stages:

&nbsp;

| Stage | Input Data | Processing Mechanism | Output State |
| :---- | :---- | :---- | :---- |
| **1\. Ingestion** | Item Taxonomy JSON & Master Canvas | Python batch runner reads prompt, seed, and target slot29. | Inpainting bounding mask and metadata queued27. |
| **2\. Conditioning** | Base Mannequin Image | ControlNet extracts DWPose joints, ZoeDepth maps, and Canny edges27. | Structural spatial conditioning tensors bound to UNet29. |
| **3\. Latent Diffusion** | Target Slot Mask \+ Positive Text Prompt | LayeredDiffusionApply injects latent transparency offset25. | RGBA latent representation generated without background contamination25. |
| **4\. Decoding & Export** | Inpainted Latent Tensor | LayeredDiffusionDecodeRGBA decodes 32-bit RGBA pixels25. | Perspective-aligned 1024×1536 PNG written directly to asset directory. |

#### **ControlNet Guidance and Anatomical Anchoring**

To guarantee that generated equipment matches the front-facing camera angle and conforms to the character's body contours:

> 1. **Pose Invariance (DWPose / OpenPose):** An OpenPose骨 skeleton map extracted from the base mannequin locks skeletal joints (head center, clavicle, shoulders, elbows, pelvis, knees)27. This prevents the diffusion model from rotating the limbs into isometric space.  
> 2. **Depth Conditioning (Marigold / ZoeDepth):** A normalized depth map generated from the unarmored mannequin forces the diffusion network to treat limbs as cylindrical 3D volumes rather than flat planes29. When generating pauldrons or chest plates, the model respects the curvature of the chest and deltoids.  
> 3. **Contour Enforcement (Canny / LineArt):** Edge detection runs over the silhouette boundary of the master character, passing strict boundary lines into the ControlNet stack with a conditioning strength of ![][image24] to anchor necklines, waistbands, and armhole cutouts29.

#### **Latent Transparency via LayerDiffuse**

Traditional diffusion models generate fully opaque RGB scenes; stripping backgrounds via green-screen chroma keying or post-hoc thresholding produces halo fringes and edge bleeding. The modern solution is ComfyUI-layerdiffuse (Layered Diffusion)24.

LayerDiffuse modifies the latent space of the diffusion model by injecting an explicit transparency offset34. The workflow utilizes the LayeredDiffusionApply node25 configured for SDXL:

* **Background Condition:** Null/Transparent latent.  
* **Foreground Condition:** Prompt describing the specific armor piece ("weathered scrap iron breastplate, heavy rivets, front orthographic view, centered, dark fantasy, highly detailed oil painting texture, gritty").  
* **Decoding:** The output latent is processed through LayeredDiffusionDecodeRGBA25, directly writing a 32-bit RGBA PNG with alpha blending around fine details (e.g., frayed cloth edges, fur lining, iron rivets).

#### **Headless Batch Execution via Python**

ComfyUI exposes an internal REST API on port 818828. Workflows are constructed visually in the web UI, exported as an API Prompt JSON (File \-\> Export Workflow (API))29, and invoked through an automated Python runner:

&nbsp;

&nbsp;

&nbsp;

Python

\# Headless Batch Inpainting Script: batch\_comfy\_generator.py  
import json  
import urllib.request  
import urllib.parse  
import os

COMFY\_URL \= "http://127.0.0.1:8188/prompt"

def queue\_prompt(workflow\_payload):  
    data \= json.dumps({"prompt": workflow\_payload}).encode('utf-8')  
    req \= urllib.request.Request(COMFY\_URL, data=data, headers={'Content-Type': 'application/json'})  
    with urllib.request.urlopen(req) as response:  
        return json.loads(response.read().decode('utf-8'))

def generate\_equipment\_batch(taxonomy\_file, base\_api\_template):  
    with open(base\_api\_template, 'r') as f:  
        workflow \= json.load(f)  
&nbsp;&nbsp;&nbsp;&nbsp;  
    with open(taxonomy\_file, 'r') as f:  
        items \= json.load(f)

    for item in items:  
        item\_id \= item\["id"\]  
        prompt\_text \= item\["prompt"\]  
        mask\_path \= item\["mask\_path"\]  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;  
        \# Inject dynamic prompt and inpaint mask into ComfyUI graph nodes  
        workflow\["6"\]\["inputs"\]\["text"\] \= prompt\_text  \# Positive CLIP Text Encode  
        workflow\["12"\]\["inputs"\]\["image"\] \= mask\_path  \# Load Inpaint Mask Node  
        workflow\["25"\]\["inputs"\]\["filename\_prefix"\] \= f"paperdoll\_{item\_id}"  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;  
        print(f"Queuing generation for: {item\_id}")  
        queue\_prompt(workflow)

if \_\_name\_\_ \== "\_\_main\_\_":  
    generate\_equipment\_batch("taxonomy\_archetypes.json", "layerdiffuse\_workflow\_api.json")

### **Hybrid 3D-to-2D Render Baking (Blender \+ Python)**

While generative AI can produce painterly textures, it suffers from stochastic variations: metal materials may shift between layers, and tiny seam mismatches can appear at joints. The deterministic engineering solution is an **offline 3D-to-2D render baking pipeline** using Blender.

Rather than attempting to render 3D meshes at runtime in LibGDX (which introduced bone-binding failures and UV inversion bugs), 3D is confined strictly to an offline asset pre-compilation step.

The baking pipeline executes in four structured stages:

&nbsp;

| Baking Stage | Primary Components | Technical Operation | Output Asset |
| :---- | :---- | :---- | :---- |
| **1\. Rig Matching** | Mannequin Rig & Camera | Pose human armature to match 2D portrait stance; lock Orthographic Camera to front sagittal view. | Calibrated 3D alignment scene. |
| **2\. Modular Fit** | 26 Proxy Armor Meshes | Parent armor models to skeletal bones; apply Shrinkwrap modifiers to conform geometry to flesh. | Anatomically fitted proxy models. |
| **3\. Shader Pass** | Blender EEVEE NPR Tree | Route Shader to RGB through stepped ColorRamp nodes; apply inverted hull outline pass. | Stylized painterly lighting output. |
| **4\. Headless Render** | Python Script (bpy) | Toggle collection visibility sequentially; render 1024×1536 frames with film\_transparent \= True6. | 26 Aligned 32-bit RGBA PNG overlays6. |

#### **Deterministic Python Batch Script (bpy)**

Blender executes headless Python scripts via command line (blender scene.blend \-b \-P batch\_bake.py) to systematically toggle visibility and render transparent PNGs for all equipment archetypes6:

&nbsp;

&nbsp;

&nbsp;

Python

\# Headless Blender Baking Script: batch\_bake.py  
import bpy  
import os

def setup\_render\_settings():  
    scene \= bpy.context.scene  
    scene.render.engine \= 'BLENDER\_EEVEE\_NEXT'  
    scene.render.film\_transparent \= True  \# Enable alpha channel transparency  
    scene.render.image\_settings.file\_format \= 'PNG'  
    scene.render.image\_settings.color\_mode \= 'RGBA'  
    scene.render.image\_settings.color\_depth \= '8'  
    scene.render.resolution\_x \= 1024  
    scene.render.resolution\_y \= 1536

def render\_armor\_layers(output\_dir):  
    setup\_render\_settings()  
    armor\_collection \= bpy.data.collections.get("ArmorArchetypes")  
&nbsp;&nbsp;&nbsp;&nbsp;  
    if not armor\_collection:  
        print("ArmorArchetypes collection not found\!")  
        return

    \# Disable all armor objects from render view initially  
    for obj in armor\_collection.objects:  
        obj.hide\_render \= True

    \# Render each equipment archetype in isolation  
    for obj in armor\_collection.objects:  
        obj.hide\_render \= False  \# Enable current item  
        output\_filename \= f"{obj.name}.png"  
        bpy.context.scene.render.filepath \= os.path.join(output\_dir, output\_filename)  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;  
        print(f"Baking Orthographic Layer: {output\_filename}")  
        bpy.ops.render.render(write\_still=True) \[cite: 6, 7\]  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;  
        obj.hide\_render \= True  \# Reset visibility for next pass

if \_\_name\_\_ \== "\_\_main\_\_":  
    output\_path \= bpy.path.abspath("//baked\_paperdoll\_layers")  
    os.makedirs(output\_path, exist\_ok=True)  
    render\_armor\_layers(output\_path)

### **Production Paradigm Trade-Offs**

&nbsp;

| Production Attribute | Manual Hand-Illustration | ComfyUI Inpaint (LayerDiffuse) | Blender 3D-to-2D Bake |
| :---- | :---- | :---- | :---- |
| **Initial Setup Overhead** | 0 hours | 6–8 hours (workflow tuning) | 8–12 hours (rigging & NPR shader) |
| **Asset Generation Speed** | \~3–4 hours per item (350+ hrs) | \~1.5 minutes per item (Batch run) | \< 5 seconds per item (Batch render) |
| **Anatomical Conformity** | Variable; prone to human error | High (via ControlNet Depth/Pose)27 | Absolute mathematical precision |
| **Lighting Consistency** | High manual variance | Requires strict prompt locking | 100% uniform directional lighting |
| **Alpha Matting Quality** | Perfect clean cuts | Soft fringes requiring tuning38 | Crisp, anti-aliased RGBA channels6 |
| **Artistic Authenticity** | Peak painterly handcraft | High painterly retro-modern look | Stylized NPR; requires texture tuning |

For an indie developer balancing limited time against visual cohesion, **Blender 3D-to-2D Render Baking is the recommended foundation**. It permanently eliminates alignment bugs and lighting desynchronization. If hyper-specific painterly brushwork is required, the baked 3D renders serve as pixel-perfect initialization guides for a low-denoise (0.25 to 0.35) ComfyUI pass, combining the geometric precision of 3D with the organic surface richness of pure 2D digital painting.

## **Engine-Level Architecture in Java and LibGDX**

Attempting to composite 10 uncompressed 1024×1536 layers every frame in LibGDX introduces severe texture memory bloat and wastes GPU fragment fill-rate. A production-ready technical architecture requires whitespace cropping, atlas packing, offscreen FBO composition caching, and tag-driven occlusion handling8.

### **Resolving Texture Memory Bloat: Whitespace Trimming**

A single 1024×1536 uncompressed 32-bit RGBA texture occupies:

![][image25]

Loading 117 full-screen textures into VRAM consumes **702 MB**, which risks out-of-memory crashes on integrated GPUs and older desktop hardware. Furthermore, drawing 10 full-frame quads where 85% of the pixels have an alpha value of zero wastes GPU fragment fill-rate.

The solution is automated whitespace trimming via LibGDX’s TexturePacker:

> 1. During the asset compilation build step, TexturePacker scans the 1024×1536 PNG, computes the non-zero alpha bounding box, strips the empty margins, and records the original frame dimensions alongside the source offsets (offsetX, offsetY):

&nbsp;

&nbsp;

&nbsp;

JSON

{  
  "name": "helm\_greathelm\_rusted",  
  "bounds": { "x": 384, "y": 1180, "width": 256, "height": 290 },  
  "offsets": { "originalWidth": 1024, "originalHeight": 1536, "offsetX": 384, "offsetY": 66 }  
}

> 2. The small cropped sprites are packed into two 2048×2048 texture atlas pages, reducing resident VRAM consumption from **702 MB to under 32 MB** (a 95% reduction).  
> 3. At runtime, LibGDX’s AtlasRegion automatically reconstructs the quad's world-space position using its internal offset values, rendering the armor piece onto the body without custom coordinate math.

### **FrameBuffer Object (FBO) Composition Caching**

Because the character stands in an idle pose within the inventory UI, re-rendering 10 distinct alpha-blended sprite layers at 60 FPS every frame is completely redundant. The paperdoll should be rendered **only when equipment changes** using a dirty-flag pattern on an offscreen FrameBuffer (FBO)8.

The operational lifecycle of this caching system ensures minimal GPU overhead:

&nbsp;

| Execution Trigger | Active Component | Graphics Pipeline Operations | Performance Impact |
| :---- | :---- | :---- | :---- |
| **Equipment Change** (isDirty \= true) | PaperdollRenderer.updateComposite() | Binds FBO; clears color buffer; draws 10 sorted layers; applies palette shader; unbinds FBO8. | Occurs once per equip action (\< 0.5 ms). |
| **Standard Frame Loop** (isDirty \= false) | PaperdollRenderer.render() | Main UI SpriteBatch samples cached FBO texture and draws a single textured quad8. | 1 draw call, 0 overdraw, 60+ FPS constant. |

### **Tag-Driven Occlusion and Feature Masking**

To resolve facial feature clashing—where helmets with open visors expose missing hair or collide with beards, and collar cutouts expose mismatched undertunics—the engine relies on **metadata-driven visibility flags** in the item definitions.

Rather than authoring hundreds of sprite permutations, the base mannequin's anatomy and clothing are split into modular sub-layers:

* BaseBody\_Torso  
* BaseBody\_Head  
* BaseBody\_Hair  
* BaseBody\_Beard  
* BaseBody\_Undertunic

The JSON definition for headwear and chestpieces includes an occlusion configuration:

&nbsp;

&nbsp;

&nbsp;

JSON

{  
  "id": "helm\_greathelm\_rusted",  
  "name": "Rusted Greathelm",  
  "slot": "HEAD",  
  "archetype\_sprite": "helm\_archetype\_greathelm",  
  "palette\_row": 0.125,  
  "occlusion": {  
    "suppress\_hair": true,  
    "suppress\_beard": true,  
    "suppress\_ears": true  
  }  
}

&nbsp;

&nbsp;

&nbsp;

JSON

{  
  "id": "helm\_barbute\_open",  
  "name": "Nasal Barbute",  
  "slot": "HEAD",  
  "archetype\_sprite": "helm\_archetype\_barbute",  
  "palette\_row": 0.250,  
  "occlusion": {  
    "suppress\_hair": true,  
    "suppress\_beard": false,  
    "suppress\_ears": true  
  }  
}

When compositing the paperdoll, the renderer queries the equipped helmet. If suppress\_beard is true, the BaseBody\_Beard layer pass is skipped. If an open-face nasal helmet is equipped, BaseBody\_Hair is suppressed (preventing hair from clipping through the steel crown) while BaseBody\_Beard is drawn normally, seating the visor directly above the protagonist's facial hair.

### **LibGDX Paperdoll System Implementation**

The following Java implementation integrates FBO composition caching, whitespace-trimmed atlas rendering, occlusion filtering, and shader-driven palette swapping.

&nbsp;

&nbsp;

&nbsp;

Java

// Java Implementation: PaperdollRenderer.java  
package com.indie.rpg.paperdoll;

import com.badlogic.gdx.Gdx;  
import com.badlogic.gdx.graphics.GL20;  
import com.badlogic.gdx.graphics.Pixmap;  
import com.badlogic.gdx.graphics.Texture;  
import com.badlogic.gdx.graphics.g2d.SpriteBatch;  
import com.badlogic.gdx.graphics.g2d.TextureAtlas;  
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;  
import com.badlogic.gdx.graphics.glutils.FrameBuffer;  
import com.badlogic.gdx.graphics.glutils.ShaderProgram;  
import com.badlogic.gdx.utils.Disposable;

public class PaperdollRenderer implements Disposable {  
    private static final int MASTER\_WIDTH \= 1024;  
    private static final int MASTER\_HEIGHT \= 1536;  
    private static final int UI\_TARGET\_WIDTH \= 330;  
    private static final int UI\_TARGET\_HEIGHT \= 558;

    private final FrameBuffer fbo;  
    private final SpriteBatch fboBatch;  
    private final TextureAtlas paperdollAtlas;  
    private final Texture paletteTableTexture;  
    private final ShaderProgram paletteShader;

    private boolean isDirty \= true;

    public PaperdollRenderer(TextureAtlas atlas, Texture paletteTable, ShaderProgram shader) {  
        this.paperdollAtlas \= atlas;  
        this.paletteTableTexture \= paletteTable;  
        this.paletteShader \= shader;  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;  
        // Allocate FBO matching target UI golden frame resolution  
        this.fbo \= new FrameBuffer(Pixmap.Format.RGBA8888, UI\_TARGET\_WIDTH, UI\_TARGET\_HEIGHT, false);  
        this.fboBatch \= new SpriteBatch();  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;  
        // Orthographic projection mapping 1024x1536 master coordinates to 330x558 FBO  
        fboBatch.getProjectionMatrix().setToOrtho2D(0, 0, UI\_TARGET\_WIDTH, UI\_TARGET\_HEIGHT);  
    }

    public void markDirty() {  
        this.isDirty \= true;  
    }

    public void updateComposite(CharacterEquipment equipment) {  
        if (\!isDirty) return;

        fbo.begin(); \[cite: 8\]  
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);  
        Gdx.gl.glClear(GL20.GL\_COLOR\_BUFFER\_BIT); \[cite: 21\]

        // Bind palette texture to Texture Unit 1 for the palette-swap shader \[cite: 21\]  
        paletteTableTexture.bind(1);  
        Gdx.gl.glActiveTexture(GL20.GL\_TEXTURE0); \[cite: 21\]

        fboBatch.begin();

        // 1\. Back Cloak Layer  
        drawLayer(equipment.getCloak(), false);

        // 2\. Base Mannequin Body Anatomy  
        drawBaseRegion("mannequin\_body");

        // 3\. Occlusion checks for Head Features  
        boolean hideHair \= equipment.getHelmet() \!= null && equipment.getHelmet().suppressesHair();  
        boolean hideBeard \= equipment.getHelmet() \!= null && equipment.getHelmet().suppressesBeard();

        if (\!hideHair) drawBaseRegion("mannequin\_hair");  
        if (\!hideBeard) drawBaseRegion("mannequin\_beard");

        // 4\. Primary Equipment Layers (Z-ordered)  
        drawLayer(equipment.getLeggings(), true);  
        drawLayer(equipment.getBoots(), true);  
        drawLayer(equipment.getChestArmor(), true);  
        drawLayer(equipment.getPauldrons(), true);  
        drawLayer(equipment.getGauntlets(), true);  
        drawLayer(equipment.getOffhandShield(), true);  
        drawLayer(equipment.getMainWeapon(), true);  
        drawLayer(equipment.getHelmet(), true);  
        drawLayer(equipment.getCloak(), true); // Front mantle pass

        fboBatch.end();  
        fbo.end(); \[cite: 8\]

        isDirty \= false;  
    }

    private void drawLayer(EquipmentItem item, boolean useShader) {  
        if (item \== null) return;

        AtlasRegion region \= paperdollAtlas.findRegion(item.getArchetypeSprite());  
        if (region \== null) return;

        if (useShader) {  
            fboBatch.setShader(paletteShader);  
            paletteShader.setUniformi("u\_paletteTable", 1); \[cite: 21\]  
            paletteShader.setUniformf("u\_paletteRow", item.getPaletteRow()); \[cite: 21\]  
        } else {  
            fboBatch.setShader(null);  
        }

        // Scale down from 1024x1536 coordinate space to UI\_TARGET dimensions  
        float scaleX \= (float) UI\_TARGET\_WIDTH / MASTER\_WIDTH;  
        float scaleY \= (float) UI\_TARGET\_HEIGHT / MASTER\_HEIGHT;

        float drawX \= region.offsetX \* scaleX;  
        float drawY \= (MASTER\_HEIGHT \- region.offsetY \- region.packedHeight) \* scaleY;  
        float drawW \= region.packedWidth \* scaleX;  
        float drawH \= region.packedHeight \* scaleY;

        fboBatch.draw(region, drawX, drawY, drawW, drawH);  
    }

    private void drawBaseRegion(String regionName) {  
        AtlasRegion region \= paperdollAtlas.findRegion(regionName);  
        if (region \== null) return;

        fboBatch.setShader(null);  
        float scaleX \= (float) UI\_TARGET\_WIDTH / MASTER\_WIDTH;  
        float scaleY \= (float) UI\_TARGET\_HEIGHT / MASTER\_HEIGHT;

        fboBatch.draw(region,   
            region.offsetX \* scaleX,   
            (MASTER\_HEIGHT \- region.offsetY \- region.packedHeight) \* scaleY,   
            region.packedWidth \* scaleX,   
            region.packedHeight \* scaleY);  
    }

    public void render(SpriteBatch uiBatch, float screenX, float screenY) {  
        // Draw the cached, flattened composite texture directly into the UI  
        // FrameBuffer textures are vertically flipped in OpenGL, flip Y coordinate during draw  
        Texture fboTexture \= fbo.getColorBufferTexture();  
        uiBatch.draw(fboTexture, screenX, screenY, UI\_TARGET\_WIDTH, UI\_TARGET\_HEIGHT, 0, 0,   
                     UI\_TARGET\_WIDTH, UI\_TARGET\_HEIGHT, false, true);  
    }

    @Override  
    public void dispose() {  
        fbo.dispose();  
        fboBatch.dispose();  
    }  
}

## **Architectural Decision Matrix and Implementation Roadmap**

To transition the project from the current asset failure to a shippable system, the technical trade-offs across production methodologies must be systematically evaluated.

&nbsp;

| Evaluation Metric | 1\. 1:1 Hand Inpainting | 2\. 2D Sprite Warping | 3\. Archetype Tiering \+ Shaders | 4\. ComfyUI LayerDiffuse Batch | 5\. Hybrid 3D-to-2D Blender Bake | 6\. Static Portrait \+ Slot Icons |
| :---- | :---- | :---- | :---- | :---- | :---- | :---- |
| **Total Production Time** | 350+ hours | \~15 hours | **\~40 hours** | \~20 hours | **\~35 hours** | \~10 hours |
| **Artistic Fidelity** | High | Unusable (Distorted) | High | High (Painterly) | High (NPR Cel/Clean) | Maximum |
| **Visual Cohesion** | High | Zero (Lighting clash) | **Maximum** | Medium (Edge halos)38 | **Maximum** | Maximum |
| **LibGDX Complexity** | High (Memory crash) | High (Warp runtime) | **Low (FBO \+ Shader)** \[cite: 8, 21\] | Medium (Alpha tuning) | **Low (Standard PNGs)** | Lowest |
| **"Desperation" Aesthetic** | High | Broken | **Exceptional** | High | **Exceptional** | Degraded (Abstract) |
| **Final Verdict** | Unsustainable | **Reject** | **Recommended Foundation** | Viable Alternative | **Recommended Toolchain** | Fallback Option |

### **Four-Phase Execution Roadmap**

The implementation progresses through four structured delivery phases:

&nbsp;

| Phase | Core Objective | Key Deliverables and Action Items |
| :---- | :---- | :---- |
| **Phase 1: Atlas & Runtime Scaffolding** | Engine Infrastructure | Update equipment JSON definitions with archetype\_sprite, palette\_row, and occlusion blocks; configure LibGDX TexturePacker to strip whitespace; implement PaperdollRenderer.java with dirty-flag FBO caching; compile palette\_swap.frag8. |
| **Phase 2: Silhouette & Mesh Setup** | Asset Standardization | Collapse 117 database items into 26 canonical archetypes across 9 slots; pose 3D human mannequin to match 1024×1536 front-facing portrait; lock orthographic camera; fit 26 proxy armor meshes to the mannequin skeleton. |
| **Phase 3: Automated Offline Baking** | Batch Asset Generation | Calibrate Blender EEVEE NPR painterly shader (stepped color ramps, ink outlines); execute batch\_bake.py to render 26 transparent 1024×1536 PNGs6; pack rendered layers into a single 2048×2048 texture atlas (VRAM \< 32 MB). |
| **Phase 4: Occlusion Logic & Tuning** | Visual Polishing | Paint 256×16 material gradient matrix (rusted iron, decayed wood, bronze, leather); tune JSON occlusion flags (suppress\_hair, suppress\_beard, suppress\_ears) for all 18 helmets; test extreme mismatched gear combinations to verify aesthetic cohesion. |

The failure of the initial batch processing script was not an engineering defect in execution, but the predictable mathematical breakdown of applying planar 2D transformations to out-of-plane perspective illustrations. By adopting the decoupled visual archetype architecture that powered classic titles from *Diablo II* to *Baldur's Gate*, the production scope is brought under control from 117 custom overlays to 26 structural silhouettes1. Utilizing an offline 3D-to-2D Blender baking pipeline guarantees anatomical conformity, perspective alignment, and unified lighting without introducing 3D runtime overhead to the engine6. Combined with whitespace-trimmed texture atlases, GLSL material palette swapping, and offscreen FBO composition caching, this architecture provides a robust, performant foundation in LibGDX that scales cleanly while fully realizing the gritty, mismatched aesthetic of the protagonist's desperate descent8.

#### **Works cited**

> 1. Spellhold Studios \- Infinity Animations \- GwendolyneFreddy.github.io, [https://gwendolynefreddy.github.io/docs/spellholdstudios/infinityanimations/documentation/infinityanimations-readme-english.html](https://gwendolynefreddy.github.io/docs/spellholdstudios/infinityanimations/documentation/infinityanimations-readme-english.html)  
> 2. Full text of "Extracting Diablo II Animations" \- Internet Archive, [https://archive.org/stream/ExtractingDiabloIIAnimations/Extracting%20Diablo%20II%20Animations\_djvu.txt](https://archive.org/stream/ExtractingDiabloIIAnimations/Extracting%20Diablo%20II%20Animations_djvu.txt)  
> 3. Extracting Diablo II Animations \- Paul Siramy, [http://paul.siramy.free.fr/\_divers2/Extracting%20Diablo%20II%20Animations.pdf](http://paul.siramy.free.fr/_divers2/Extracting%20Diablo%20II%20Animations.pdf)  
> 4. charstats.txt \- D2Runewizard, [https://d2runewizard.com/game-files/guide.html](https://d2runewizard.com/game-files/guide.html)  
> 5. Review of the Original Baldur's Gate 1 \- Lilura1, [https://lilura1.blogspot.com/2019/10/Baldurs-Gate-Retrospective-Review-Preamble.html](https://lilura1.blogspot.com/2019/10/Baldurs-Gate-Retrospective-Review-Preamble.html)  
> 6. Transparent backgrounds in Blender Python scripts, [https://parth3d.co.uk/transparent-backgrounds-in-blender-python-scripts](https://parth3d.co.uk/transparent-backgrounds-in-blender-python-scripts)  
> 7. Saving a rendered image with a transparent background thanks to a, [https://stackoverflow.com/questions/61142585/saving-a-rendered-image-with-a-transparent-background-thanks-to-a-python-script](https://stackoverflow.com/questions/61142585/saving-a-rendered-image-with-a-transparent-background-thanks-to-a-python-script)  
> 8. Alpha gradients not smooth in WebGL when using premultiplied alpha, [https://stackoverflow.com/questions/31481029/alpha-gradients-not-smooth-in-webgl-when-using-premultiplied-alpha](https://stackoverflow.com/questions/31481029/alpha-gradients-not-smooth-in-webgl-when-using-premultiplied-alpha)  
> 9. NLP Assignment \- Kaggle, [https://www.kaggle.com/code/mihirprajapati01/nlp-assignment](https://www.kaggle.com/code/mihirprajapati01/nlp-assignment)  
> 10. BG1 character sprites appreciation : r/baldursgate \- Reddit, [https://www.reddit.com/r/baldursgate/comments/1n6qrqy/bg1\_character\_sprites\_appreciation/](https://www.reddit.com/r/baldursgate/comments/1n6qrqy/bg1_character_sprites_appreciation/)  
> 11. IR Revised V1.3.800 (2022 January 11th) \- Item Revisions, [https://www.gibberlings3.net/forums/topic/28894-ir-revised-v13800-2022-january-11th/](https://www.gibberlings3.net/forums/topic/28894-ir-revised-v13800-2022-january-11th/)  
> 12. \[MOD\] Dragonspear UI++ (v2.42) \- Page 25 \- Beamdog Forums, [https://forums.beamdog.com/discussion/50357/mod-dragonspear-ui-v2-42/p25](https://forums.beamdog.com/discussion/50357/mod-dragonspear-ui-v2-42/p25)  
> 13. Ultima VII Part 2: Serpent Isle \- Hardcore Gaming 101, [https://www.hardcoregaming101.net/ultima-vii-part-2-serpent-isle/](https://www.hardcoregaming101.net/ultima-vii-part-2-serpent-isle/)  
> 14. Ultima VII: The Black Gate \- Wikipedia, [https://en.wikipedia.org/wiki/Ultima\_VII:\_The\_Black\_Gate](https://en.wikipedia.org/wiki/Ultima_VII:_The_Black_Gate)  
> 15. Ultima VII: Serpent Isle – Day 1 \- yggdrasille.com, [https://yggdrasille.com/ultima-2/blogging-avatar/ultima-vii-serpent-isle/ultima-vii-serpent-isle-day-1/](https://yggdrasille.com/ultima-2/blogging-avatar/ultima-vii-serpent-isle/ultima-vii-serpent-isle-day-1/)  
> 16. Ultima VII | The Digital Antiquarian, [https://www.filfre.net/2019/02/ultima-vii/](https://www.filfre.net/2019/02/ultima-vii/)  
> 17. Pillars of Eternity and the Infinity Engine Era of RPGs \- Shacknews, [https://www.shacknews.com/article/103473/beneath-a-starless-sky-pillars-of-eternity-and-the-infinity-engine-era-of-rpgs?page=13](https://www.shacknews.com/article/103473/beneath-a-starless-sky-pillars-of-eternity-and-the-infinity-engine-era-of-rpgs?page=13)  
> 18. \[NPC LIST\] (In)Complete list of vanilla and mod NPCs for BG, SoD, [https://forums.beamdog.com/discussion/89242/npc-list-in-complete-list-of-vanilla-and-mod-npcs-for-bg-sod-soa-tob-iwd-iwd2](https://forums.beamdog.com/discussion/89242/npc-list-in-complete-list-of-vanilla-and-mod-npcs-for-bg-sod-soa-tob-iwd-iwd2)  
> 19. Coolest looking sprite \+ weapon \+ armour combo? : r/baldursgate, [https://www.reddit.com/r/baldursgate/comments/1rzmn5v/coolest\_looking\_sprite\_weapon\_armour\_combo/](https://www.reddit.com/r/baldursgate/comments/1rzmn5v/coolest_looking_sprite_weapon_armour_combo/)  
> 20. Re-coloring PNG images with an indexed palette, [https://gamedev.stackexchange.com/questions/204867/re-coloring-png-images-with-an-indexed-palette](https://gamedev.stackexchange.com/questions/204867/re-coloring-png-images-with-an-indexed-palette)  
> 21. Simulating palette swaps with OpenGL Shaders (in LibGDX), [https://stackoverflow.com/questions/26132160/simulating-palette-swaps-with-opengl-shaders-in-libgdx](https://stackoverflow.com/questions/26132160/simulating-palette-swaps-with-opengl-shaders-in-libgdx)  
> 22. Palette swap using fragment shaders \- Stack Overflow, [https://stackoverflow.com/questions/14572745/palette-swap-using-fragment-shaders](https://stackoverflow.com/questions/14572745/palette-swap-using-fragment-shaders)  
> 23. Creating a retro-style palette swapping effect in OpenGL, [https://gamedev.stackexchange.com/questions/43294/creating-a-retro-style-palette-swapping-effect-in-opengl](https://gamedev.stackexchange.com/questions/43294/creating-a-retro-style-palette-swapping-effect-in-opengl)  
> 24. awesome-comfyui/README.md at main \- GitHub, [https://github.com/ComfyUI-Workflow/awesome-comfyui/blob/main/README.md](https://github.com/ComfyUI-Workflow/awesome-comfyui/blob/main/README.md)  
> 25. Layer Diffuse Joint Apply \- ComfyUI Cloud, [https://comfy.icu/node/LayeredDiffusionJointApply](https://comfy.icu/node/LayeredDiffusionJointApply)  
> 26. ComfyUI-layerdiffuse (layerdiffusion) detailed guide \- RunComfy, [https://www.runcomfy.com/comfyui-nodes/ComfyUI-layerdiffuse](https://www.runcomfy.com/comfyui-nodes/ComfyUI-layerdiffuse)  
> 27. Best ComfyUI Workflows: Templates, Examples, and Downloads, [https://www.beam.cloud/blog/top-comfyui-workflows](https://www.beam.cloud/blog/top-comfyui-workflows)  
> 28. ComfyUI Cloud GPU Guide: RTX 5090 Setup \- Hivenet, [https://www.hivenet.com/post/comfyui-cloud-gpu](https://www.hivenet.com/post/comfyui-cloud-gpu)  
> 29. 2.2 Creator \- Diffusion Guidance \- ComfyUI Workflow, [https://comfy.org/workflows/gsc\_creator\_2\_2-bfba92751be8/](https://comfy.org/workflows/gsc_creator_2_2-bfba92751be8/)  
> 30. Comfyui — Generate images, video, and audio via diffusion workflows, [https://hermes-agent.nousresearch.com/docs/user-guide/skills/optional/creative/creative-comfyui](https://hermes-agent.nousresearch.com/docs/user-guide/skills/optional/creative/creative-comfyui)  
> 31. shitagaki-lab/see-through: "Single-image Layer Decomposition for, [https://github.com/shitagaki-lab/see-through](https://github.com/shitagaki-lab/see-through)  
> 32. ComfyUI's ControlNet Auxiliary Preprocessors · GitHub, [https://github.com/Fannovel16/comfyui\_controlnet\_aux](https://github.com/Fannovel16/comfyui_controlnet_aux)  
> 33. LayeredDiffusionApply — ComfyUI Node Guide & Examples \- InstaSD, [https://www.instasd.com/comfyui/custom-nodes/comfyui-layerdiffuse/layereddiffusionapply](https://www.instasd.com/comfyui/custom-nodes/comfyui-layerdiffuse/layereddiffusionapply)  
> 34. Generating Transparent Images with LayerDiffuse in ComfyUI, [https://www.runcomfy.com/comfyui-workflows/generating-transparent-images-with-layerdiffuse-in-comfyui](https://www.runcomfy.com/comfyui-workflows/generating-transparent-images-with-layerdiffuse-in-comfyui)  
> 35. LayeredDiffusionJointApply — ComfyUI Node Guide & Examples, [https://www.instasd.com/comfyui/custom-nodes/comfyui-layerdiffuse/layereddiffusionjointapply](https://www.instasd.com/comfyui/custom-nodes/comfyui-layerdiffuse/layereddiffusionjointapply)  
> 36. ComfyUI-layerdiffuse (layerdiffusion) – ComfyUI Node \- Floyo, [https://www.floyo.ai/all-comfyui-nodes/comfyui-layerdiffuse-huchenlei](https://www.floyo.ai/all-comfyui-nodes/comfyui-layerdiffuse-huchenlei)  
> 37. Python \- Render object with transparent background, [https://blender.stackexchange.com/questions/149710/python-render-object-with-transparent-background](https://blender.stackexchange.com/questions/149710/python-render-object-with-transparent-background)  
> 38. Layered Diffusion Node in ComfyUI : r/StableDiffusion \- Reddit, [https://www.reddit.com/r/StableDiffusion/comments/1b6dmlp/layered\_diffusion\_node\_in\_comfyui/](https://www.reddit.com/r/StableDiffusion/comments/1b6dmlp/layered_diffusion_node_in_comfyui/)  
> 39. LABCESEX1 | PDF | Shader | Ios \- Scribd, [https://www.scribd.com/document/409210315/LABCESEX1](https://www.scribd.com/document/409210315/LABCESEX1)  
> 40. "Vixen Update: New Features & Content" | PDF \- Scribd, [https://www.scribd.com/document/756600814/Changelog](https://www.scribd.com/document/756600814/Changelog)

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGsAAAAaCAYAAACwwaJoAAAE3ElEQVR4Xu2YW8hUVRTH/6GSYhGlqKlhd82SitAICgqKisogpaJMfcuHHiJQscILGghBV7oQRRREF7q8JEX18IGSQhAVmb2IF7TAyEAqiq7rx9q7b3/bfWbGOfPNVJwf/JmZvc/M7LPW2mutfaSGhoaOmWgalw+OBuNNJ+SDfaQvNzmKTDJ9YLokn+iEZaYXTM8l4vMd6UXGLNPnpmuy8UHAOhblg31if4V2m143bTCd+8/VI7nc9LT8+q6cdalpsekV01+mb8JnnJPyhukx03HZ+CBYYtqlo9fYD0hhL5suDJ8fkGebmXJHfCi35bQwn7LZdKVqOCvyvNxZL+YTAZx4QT44IE4y7dDggudJDRub95EJcjueIQ9u1hlhnRfLv1fLWSebPpE7685sDljEq6ax+cQAucd0QG6YY4WdsECeQaJuNp2SXtSCKmfBg/J0t9a0MBnn/6C2s9jSR0w/qfwj7KiSEwcJa/rBdHc+0QbS1Xfy+hJr9Cr1zlmrTTcF8R5oyB4xnWq61nQwvHbVqD0h31Vs4RIvmabmg/KtfZc8UohyHH6DaY88bX6mY6srNA6HgmJNgFtMX2tk4ea/qQ07TZOT8SrOlv9+jPA6rFDZWWSeZ0znmd6RB0ZOrZ3VLgXi/aHwmkPH+Kw8TQKpCeNhmIfkv3lfmGvHFLkh55t+Nt0fxklZW0zfy42Qwn+UxnNwLEalMekFubPGyB2zzrRd7rCLwnwKWYCg+zO83jhyuj3cKDfMrkijOcKOoi0tFXKMeFrymcWw9eN3WNR1yXwrYjrD4Tg5fj7T9K1pq+nEMBbhv6pSd8oM+VpL2aEbUmfRkP0hD3gcRWPRLni6ht2EcfgzdlkOeXZfPhhYmrzHmSyc4grsLpxfcnKJZfId+r68FsXO82q500nVOTiLOa5pBYZlbdPl91MSO5sd0gn5zuJsxdgkeSN2epjrOWnLXjJsK2elUDdIgXWiFwOwU97UcOcZ02kpReMs5ijmreB32Z35QTbVx6az4hfakDsLJ70tD1AeGlAaRoVWXSDEmsZhMIdojLWMtIXhIhj7dvmNdApOytMxRq7a9Z2mQc4776p350QclDoLCPSHTbfJ/48zVc/BwK06qljg83oBfJc5HIahf0vmMAwGigdDCi4H2XtVfV4bku9idnOkKgUCWSG/vgqMSPfYi2eLJWcBTQZd4CwdfSiuDT+Gwd8yHZ/NpWCsUhvKdzE+XRxnl1/DOOeV9zTyUEj7z/WtdgKpOK1X3HRVCoxB9JHKuz4HJz0l79Tqkjrr0XRCfs88WVkkD47aDrteboRcQyq36CygVMQ5W3G4/NR0lek1+QK/krelaQ3krPSL6XdV1xic86V8txBAnNWqWnM6vL3ymtYpOIwnDMvlKbxUo9vBmuj+eKXW/Wj6wjQvzPMfBAS24Tq6YmpZ38AwJaPgWBqKmFq4+fRziY3yYKmCjgxD8juzVR1ABA/17bJ8ogM2yc85hzUcqOzoaPD/PEQKDUMdrpDXmVLNIq2QRqPxSa0YsOT48+VRTefVUIDTP49riPpuIMVtU3WXRE1cKW8WbpU/cqKGlWCcVFNyeoM8wtfLzxClaG8HTcA5+WDG4/KOkYeec1VdU2gsahfu/zs4aY26eKbVQ+aocVRDQ0NDQ8O/jr8B1sMGL8x6wMUAAAAASUVORK5CYII=>

[image2]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACcAAAAZCAYAAACy0zfoAAABn0lEQVR4Xu2VPShFYRjH/4oiHwslpQwG5SNKkQGTMvgYbMokkuwiw12kDAaURVaTTEoYTkwWJpEYyGKQyaB8/f+e93TPud3u4naP4f3Vr3vO8773nuc87/O+F/B4PB5PolTQuoi1tMSNlUbi1bTIxQvGPP2O+EY73dhwJB7AXqSg6IEN9Jh+0FHEK/dKe5FQ5UIGYRXaRDqJsoz7RAmXcI4W011Ygv+CJ1hy13SAvsSHk0VLGFbvBpZkNproPl2gVS5WT1N0jTa6mOijk7DvrNJl2Fy5TpvTU3PTQ9+RTjAVGzW66DnsyLmH7XaxTU9oN72lLS6ujaT7MVjvqmX00lN0iF7SGjc3J+qvQ1hi2qWt8eHf3XsAe2M9SImGP6wd30Yr6RnsGArRveJCcbWPKqcj69F9/hklckUXM+I6jo7okrsO6Agtd+O6D89IJfcAO9jzmpxI0VN3raXVMrXTZ9oPq4gqM05n3LwABUpO1VB/qck3YMupQ3uLXtAVugfrpWk6Sz/pDp2gd7C2UXtozpf77EAeyfaPoepkxjyeKD9YG1IC2fWmTwAAAABJRU5ErkJggg==>

[image3]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACgAAAAaCAYAAADFTB7LAAACcUlEQVR4Xu2WPWhUQRSFjxhBjRb+oAkqomgRDCgIppEQgoIWQdCAFhbpLLRSRLDalBYBUUSQQEhhkwREQkDEIkq6VAEtJcQmWIgoKKignsN9Nzt79+1mn+xW7oGP3b3z5s2duT+zQFttrasN5AbZFAfqSHNuk0coNu+fdIksRmMDkmNPMlqmI+Q9ORsHGpTPb4kUpvvkBdkSxoqoRPZEYzPUSz6RK3GgoPSeuu9QLuwl3eQc2ZnZt5EBsp9szGyuzWSOvCKdYUzS87uT31pDp1SrIFbJ4Wh0nSTfyJ/sc5A8JD9hE2VfgG3Ape8rZDyxuXaRl+Qd6SMjZJm8IV9hqRH1m5yJRpd2dQz2Aj34hYyinFdyUJSy35Jv6k5ic5VgbecobJOPUX7XCPIL6he5Fo1RWkyOxKT/ntknE9v5zHYxsUk7yHNYuE6TZ6gMqzaWd+q1NlshdzA+6OFPHRzKbPpMdYBch+XoTVSfip6fh+V3Kq3xINiq5A7GRYs46FKBKAfTQvHCik5LWuNuNEYVcVDhU97olPLk42lBKOwqukOJzVUoxI042APrgbV2rVPSnFQqnGhz5a27JvW9C+Qp7MF7sCLYDuuDOgnZVeXDZB/KIdSc2DY6yAxsjhfIKfIZte9dtZ/j0ehK+6CjHqc2MR/svlM5NYHqPJPced2xr8k0+UhuIb9R6136s6EO0FTpZniLyv4oKS+1kbxiyJNuL113LdFV8gHlxPfwKi1UKOtJ/XYW1WnSNClkU7B/Nf2wcOr2+AHLTzlcT5fJUjQ2W12wu1eVrSJKqaeDsDteBdRyyUn962lUW8kYOREH2vrv9BeulIrbwe4afgAAAABJRU5ErkJggg==>

[image4]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAG8AAAAaCAYAAAC5KgISAAADZklEQVR4Xu2Y26tNQRzHf3KJkMsRueUkRE4korwdJXkQoVzepPDs5JJXSSjkTjghlyQlwoPcH+Qv8OCFRFGekBC+3zMzzm/NWVd72nvLfOrT2Xtm7c76zm/WrFlLJBKJRMoyBV6Hl+Eq2CvZ/c8xEL6EH+EB+70Ui+Gpiq7v+mVt9IOH4OsMz8EOON79wDIEPhNTuD7wM9yYOKIxMI+fwflCivO4gu2Cr+DoP0fkMA2uhLvhL7jHftdug99tP09iXtcvwzAWjhJTiBOwHfaHN+AleBeelezZ+AFO9xsbBLNck2QeZpkqyTw6C1eNZfYvqVQ8Bw9mcZb4HWC2mBme1V8Lg8T8b/7lxHAnzQHohMPELI2HbbtjJjwP27z2RqIzuM8OnYdZ+qo+Rwt8Do+LKX5pmq14PHmGnGg/cxb7cACewnF+R4PIK57OwywcUw2vvK3wtpiltBLNVjzCsC4kl+403kj3/S8kc+AOv7GAvOIRl4dZ/HFcAU9L9u0hl5DFWyjmRp22NKSRVjwuL/w+As6HV2w7N1g/4Tr7/R08KOF3nJwMx+Byv6OAnZJePJ2HWdjvmAvXiNlfrIUn4WDVX0jI4vEkeay+ivLwi8eic1fLmzvva4+le2kcAI+IGSSGvSN/scyUhBuhR35jAX7xmGWyJPPoZZ7HPRQzXs6LUnEyhiwerzxuj6teeffEPOv8EPP7vbBVegbh9zFwUUpfaLicDfcbc9DFYx5muS/JPMEJWbyq6Jk6QczsnCTmXhbqqhoq5v9UlZOEA78U9pZi/CuPWTiZQ+dJ0CzF42cGbYGb4HYJc3UdlZ4Pz2Vlbr4BWSDF+MVjFr4N0nmC06jiZe02L4h5GdAKb0pjHgd4Trf8xhx0Br1h4X1a5wmeJWTxZsAHUu6KySreLHhVzDLD+w632fWGV8p+vzGHrOIRnYdZyu4HSsEBZ3E2SHJtZwHa4Rfbv1r1ZcGT5rGuEHm44vH5plOSs3KzleewRQIHLoCDzKuuze/IQRfP5dG4PMyyTwLk4QMjB9rXzRq/XfdlwWejr2KWiyz4IvcMfC/mQZv3lm/wE3xij+EA8o0D+9/a9io7v1rgasN3kXw3WQbmYRbuLnUenvNIe4zLwywcx3rm+a9g0bhDjUQikUgkEolEIpFIpC78Bj8t7dJG9/q9AAAAAElFTkSuQmCC>

[image5]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAIMAAAAaCAYAAACU9O/tAAAFKklEQVR4Xu2Zaai1UxTH/zJk5jXPUyRDxpCItzeKEPEWGerNB5ThAyFDOvggicxKuEkiREIkHy7eED4g8kmGREiiKGRYP+tZnW3d55zznHvOuefQ86t/5z7rmfaz99prrb2v1NLS0tLSMlHWMF1kWjuf6AP3XG66R8Pd1zLjnGZ6JxsbgBPcX6llvNC3B5k2zScmye6mT0zH5hMNiftberOe6UzTUflEDUTcE03fmx40fWZ63LRJcc1E4MW3m16SN3ixdExbZWPLP1xh+tJ0i2njdK4OJuXvplXV8ZbyqD2nCafjfeUeeEY+MSQ8Z9Rn/N/YXO4A16qZEwATkon5kWmLwn6+6U/TcYWtJ3jM1qZt5TdsVtk3NC037WBas7IF65peML1i2iCdA64vG8Q7mP29vPNr027ZuAh4L+/hW0J0LFGM/FnalzSfNuBpecpkYvTqp36cZfrL9HCyH2z62TSf7LXExTyI3xWmu0y/yQcJ+2p5Bwb8/bnpgcIW0Pkvyz30MHnI+tT0mukn+cBk8NxjsnERRLtoc2he7th0UmnPnTYN6Iv95P11shZOumG4UvXfFeNLvwwEL9xHPlgMyo+m69WtA6LzOtUxxAtoQKYjX2ruIXeo+9R91irVF5vkOcLZqERk4DtoM99EZKPTiXiPyJ10L00/MuxvetP0rnwCjsqdGoMzBOFZuSD8pbKXLzm+sp1a2GCZ6Vl5yD/S9Iz+HfJoWF006eVYGdJTE96St+9X0+GVjZXLV3JHnQWONn1gelEeHUYlIt9YnSEPSqSQ8iUnVTZ+S3Y0XSgftEu1cLZz/bw8bJfwDjy7H8ykH0wH5hM1XKBuRHvMtJY8YlGZ79q9bOoQyYgKRAdSKhFssTDJ8jjBSM6QB3gYZwgoHnNVG0VndhDgHVdnY4IIg4M1XcZ21HWIG+UrH1Yus8ye8kLyYw2/wupVQB4ij+6vJ3tfhnEGUgB5nsGpI86Xnk7qoCCtm5lN08QwUAd9J287z48I8V+APpoznavmzh99nld4MXF5XmOGcQYKMGZar9nM7OeeEnJ1tgV17x0VHPFudaNDXeEau3vPy7fF2eXjPn7vkNc8l1TX4khU/PeaTpCnNb6ffYC9q2OK1XFymzy1XazBTsEuI7USm0zUbkGMRaNIQ5XNRz4qv+lmeYG4kXyfAW/DTmW+0rS9ummAe3Keo9Oekt8TxeOh8nzf6/8QLDmpCcYNxSNFJG2p60zaw8YOuZsoR15lz4X1Psd821Xye/l7O/kykL4IZ3vDdIr8GaTBpkVuU3A2nI6+H8TZpj9Mp1fHOAhtRY22pMt9hhCdwtJwPtljBtMRc1pYF0A4Ch36qulJ0zemy1S/mcKzsjePC973hLyD6mB1QZQD2o3T0h5+47vIueUeCxGSSQBEU3IxEwfot/LapYbvxbnZHqCGeNv0oXwlNVFYz/OiTrIzo3CaukKxDnY9p1XYEYJzaGe1giMfUR0zWXYyrVMd4wyRMnGGeXVXR9N2hqlCWPpC3aIwUgSphYJmEITf57Qw1SwVDHqEVCCv4szvyXdSgb0UouHy6nhcznCOvO+aiHZSp8w0EYb57yVFFymBXUfyNKF0UPXOQLyfjUvICnlKu850jek8087yNj0k3zO5Sb4xRLq4Qb5MQ6S9b+UpiGsZXCIidcMuGgy1Rfm/kn6ijqlLsTPHNvKiitxLkVOqH3T6anl+niaxhV0WfkQqIkPeHGtpAA6Rc28/1jfdajogn2hpaWlpaWmZSf4G8VIxhxQs+MMAAAAASUVORK5CYII=>

[image6]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGwAAAAaCAYAAABSHbkRAAADV0lEQVR4Xu2ZTahNURTHl1CExJWP0KOkDISUAaYGBiRP+RqIkaERQiQZSU/kqxRPSSQGRD4GiiImEjMGJAOiiJHE/9/a591z9rv3nrXvOce92L/69+4765x3z//svdda+zyRSCQSIcOhbdBH6BI0ORv+6xgFvYI+Q33u98LMh26KPqQVXqxd3jbRa9GB2A/NhoYkF4ChLsZzyEboO7Rw4IzO4fto5ifNWOix1AfpIPQGmjJwRhtMhF5Ap6BfUH823Da8yfPQPPf7bmgENA06Ad2FLkC3XTxhCbTZfe6mAbP6SWcETsZV7icpZcDWiQ7UNegntDIbLsQxqT9sfk4YCZ2BZkKLRWeiD1PjZegONNqLdQqLH95zIz816Al0EhrmxYLgF/3wD5ZEM4NkD7TUfW40SXqhp1CPH+ggFj97ZbAfrrDtomWn0WCaGQM9gN75gZJoZXCH1OslP/twxVfVcEyHDvkHDVj8UL4fTj4ujLYbjsOiadDXlvRJTWAefy7aqOSxVRobZEpgapgDzRKtAwlcUTegNU7nvHhZ+A/VgsUPJ1r6fhdB60W9bIBOiy6UIFgTWPh2iQ7UAWiSaN3Ig00Ar7EY9g2yC6SZfdAjUZPPXJwwdZyV7CRiBgg2aIDNFh9mCBY/6YnM53xfsn7YmKQ74yCOiv6R5X6gBXygrC2hK4zXsanhtTTH4swZSdOd4qHoCrdi8VMZzKf3oE9S3Rf5M5J7FR6rQRehGS5WlCRjhIq1hXsplgJ2enn8KT8NYQrkpu8lNMGLlYVvkMauis7qZaJ7P0sazmOnDN7QWvReNMN8gDZJPhY/lbFa9GZZx6qCptIGCfM3O7S1oi0uDXcCNgpstUPqicXPAne8dLjr5oCFvoriPuOW2Dq3RgYJr2U31SNa+AvtTdqEK8KSBtNY/DTbOBeCs+sK9FXqr1qshHSJaYN96YDo5vKI6L2wcyrdZAv4nf3+QQMWP71SgR/WLNYudjjjvFgeXF1fRN/5tYL1gV0Uf7JefBPdv811cdYutsHJeaynIR1bEaZC1/2DOVj98CVE6X64qri62JqG5PB/BT7c8f7BboSvR5jOuP/ii8haNhzpNjhg/Eca06H/gjLShfBl6nHJrz+RSCQSiUQikf+B36MJ2GdDebS6AAAAAElFTkSuQmCC>

[image7]: <data:image/png;base64,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>

[image8]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAIYAAAAaCAYAAABy3SSpAAAEx0lEQVR4Xu2aTahVVRTHl2RRVlR+EhVZSCgKDkzErzAoqEENMigwTQxRsIkfICmIII5sECoIEYiDkCgoiIoo6JEDo5wIhqPgKSY4CDFKqEhbv9bZ3v3W3ee883G/ovODP/fdvc+59+z/Xnutfc59Ii0tLS3D5HbVfb6xgKrHD5MnVJ+rLqnGVa9M6O0vVX2qenySZaoPVO86vZH1Y8iHOX0BLuIr1VbXXoZHVWt94wjyh2pH9vcDqh+ivphnVBfFAuhv1QsTuyuDt3V9Pesbq/CQ6mXVTbGB7M3eL8j6p4tN3EnVDdU6sS+NOaD6VHWXay/Leen+zFFju9gigaLAwM8Nqp/EPG0SGFPEvK3r62vSA18ZBINl0CmeV33jGzN+Vj3nGyvwneodMSP+C6xWXfWNjhPSPDAWinlbF7JNI185kUEc9h0RBzN5OJeJbVLT3hRLvY/5jhGEcX6p2uk7HE0DA1+PinnbhEa+kiUYBGUixZ1ipYKs4Zkj6YCpwiKxFbjFd4wYbOqOqDbK5KuwaWDgKyW2qbeNfCUgTqnu9R0ZbKi+UN3tO5TdqsW+UWxFnVH9pnpbtVL1reqK6qXoOMDk91U/qma6vkFAHWb8bNbYOB4Sm5RzYnciZAk2nqxg9l9o179n5hMHBmNibLxH9FG2+S78uSbdiw5ff5Vub/EKb2Nf+Wy89b5CbV/5ouNSr4wAg3zQNyofqWZIxyAubr3qz6zNw+f/Ip1Nb4qpqjXSmZyymmwDtk9so7ZE9bvqLdUs1Vj2PrSHiQ0qIg6M21SzVa+LbeDHpVN656kuS/fkcf4F6faWsoC3sa98Bt7W9TVJ2GGnog2KyggZ5GtJZxoinnM/E7vbeVb1idhg9ncOuwXHh0nIox+BwWQwPu4myIxMHHcBa8Suu26NTpWSEGDxxvUemRiAEHxNZXHKAl7Fvt4h5u3+zmG3KONrknCxeRH1uFjg8OoJg+LVw+c9LGZsuNsh+leI1WoPA2BSmJxBMle1WSzo9kgnE7DKudZHsvdVKQqMsagtFRihDXlvyTp4G/sKeNtTX9lfMIC829Si/UVRYEBYgUVlKsAAvJGDJGQ30m4v6FdgBPru63uSXy+pXdwu8ZqCVUa9YwftCUanNlApyqS8aWK3iqHGl1VemYwJQYwfvaBJYARfeUiW520vfU0SMkaKj2Xyx7FELSXDQ80el+KHZjFMSGqzNSjCysq7Za9Kk8AAfKVc5Hnbd1/ZLZ8Tq7UxXBC7c2ptEWyGlvpGZZXqLymX7kJ2YcOVKln9JqzQWrv3BNR6fn8iMF6N2p9SXVedjtpmZO9pfzpqx1fa8rwt4ys08nWu2EUwGH4kIxoxaUp0TB5EeeoByiYxY170HQlCdsm7Je43TA7PMMYkv6aXhQzhSxkpn1Ubt5FRQpaKFVZ2yC553pbxFcaloa9cCNFNKn1S0jvcFBjJamPVxXD+dCkXXNR3auZy3zFA7hfLXKNCKDF468HbMr7CUH39XtL30GXhCSD34y0TIZPhbV34EW6ovvJrIztoLqQqRP8x6c44LQbe1vX1uAzZV9LaRrGNTtVfWddK9XP+T+BtXV85b+gwAJ7Xb/MdBcwX+3+BlmLq+lo1mFpaWlpaWlpa+sY/GFg+3IjjRq8AAAAASUVORK5CYII=>

[image9]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABEAAAAZCAYAAADXPsWXAAAA9klEQVR4XmNgGAWjYICAFBD7AnEIELsBsTCqNGGgBsT/gfgvED8B4i9Q/nRkRfiAPBDfAuIVQMwNxXsYIIZ8QlKHE7AA8XIGiAYbqBgHEK+Hir2FiuEFxkD8lQGiQRJNDhuIYICEGwrQZ4A4mVhDsAIRIL7KADEE5CpcgA+Iq4A4FYhZ0eQYGIG4mQFiSDmanDgQX2eAaGoFYiMgPgzElsiKYICfARIbv4C4jgESWyC8lAFiiAoQRzNANIP4ihBtuAEPEIsBMTOaOMhVF4HYA4h50eSIBp5AfAqIpYG4DE2OaOAAxEeAuAWITVClSAMgr4IS4nAGAL8nJuDJbOIAAAAAAElFTkSuQmCC>

[image10]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABcAAAAaCAYAAABctMd+AAABN0lEQVR4Xu2UIUuDURSGjzDBsYluxWASJgYFQVaEBcGiwT7QH2AwmLR+Qf+A2MVkWVtZd2HMMJZMBkEwGCwWMej7cs7dzq5DJ1swfA884b737tx7z90mkpKS8r85hZ/OR3gYZddD1obsR/JwBXbgM1yHObgM70QLFd3aA/gG5y0biUS0UNXGGVizbMmyKXgFGzYemTXRQvxg1savlh3ZGm7y5MaeY3gTh4EZ0UIsyMIJfIC30t+QRVk83MTDG+/FoSc81gVswXO4A9/htugmPB1b9mcupb9BOC0JGR+4YBmZFj3AhugNN93cNyrwQwb7THhyZonLSAnuixa9l+Ht6jEn2o64r3X4Aldd5mEbeWt+mybKAuyKvs1sNDc2u7ANF+FJNDc2W7AJz2B5cGoy8G+Bv5OU3/kC0nBDBIhbLHgAAAAASUVORK5CYII=>

[image11]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAE4AAAAaCAYAAAAZtWr8AAAC8UlEQVR4Xu2YW6gNYRTHl1CE3IoUCUVKSUSuSRQPEinEq1vJkZJ4lQeeJA+SiBKhJCm3cooonjx5kEQuRfJESeH/a83Ya+bsM2fvbR/aZ8+v/u2ZNZdz5j/fWuv7xqykpKQF6SdtkN5KN5LfdmKQNCNRXeySPkhzk/3N0qTK4T5Lf2m39Fm6Ip2TjkgD40lF/JB2hP3Z5ub1dXjmb9KyEHsvHTDPwkLGSE+k0SGGcYfDfl/luXTTPFVTjktfrIa0JU3352KrzYdtK7FHWpwP9sAvc6MieEF8ey6eYaX0UxoaYgzRC9LeEGsFJpiPHn5rBYOqDRrinZb1JQNuc9KpoLPSJ2l5OK9VoGadzAe7gfRsyLhh0gPrahzTEUbh5MqpLQPdEOPolj2BKQ0ZN958vkYrjpDbXDggF/+X8OA0rXENiBd+XZpmxQyxBo2jc36VnoYYZl01v/B/MkV6JL1pUPz/mIeRRfyVcbF70mG5aEuItRKzpIf5YAE86+lcLO2qeUP/MFZ6aRXj6EYvzOsctWKUtEZan/yyH7dJp7XmI3RTcg2k562SlibbaHqID07ObSbck9n/ivyBAihV/P+xLNEwv0vzQywD044T0i3zP4rz96ThyXGMWCB9NB+J7HeaT2HYnmk+UWSbTnzM/J7scw1Dnjp1XjpjPuw7zY2rpXjXCw+KCfW8lEPmWTcvxBhMPE/hsmui9CzRZfPREknNvWj+VjrCMW68MNleZ9mawMtIhzorEOroyBDrDbh34aS1CjQITHpnvmbdJt22rj5UBQNG5IMB5nOvzbsVdTEFU5dIl6Q7ljWOEco+6c9IfCwtsuyasNnwDHHpVCs8x1Rpo/n6vMc1aj1gBHO7CKl9Ldk+aG4U6Ym5aXd+Zf6VhTRiUt128PCkX2SOeXHdJx01byyMPuaHwNsjDXiD1M27SbytwDjyPw9Fvrv0oARQQ1JYqbQFdE06DB83t1oNn1lKHFLuvvmXkp3W5KJZUlJS0ov8BoSPnVnlStnbAAAAAElFTkSuQmCC>

[image12]: <data:image/png;base64,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>

[image13]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABYAAAAaCAYAAACzdqxAAAABKklEQVR4XmNgGAU4QAUQzwXiWUh4IRCHIysiB1gBcQwQ3wDi/0C8C4hDgFgeWRG5QBCITwPxPyB2QZOjCOgD8Scgfg7ESmhyFIEgBkgwbAViDjQ5sgEPEB9ggBjsiSpFGQB5HRQEVA8GPwaIaw8wQFxPNdDKADF4EroEFPADMSu6IDFgDwPEYFAEogOQD5YzkBlEoLQLSsOgtIwMQK5cCcQRaOJEA5Br56CJMQJxAhDfAWJFNHFzBkiwdQGxOpIcChBmgBicgyQG8v5GqDgoGFiQ5KYCcT0QcwKxChCfQZIDA1C2BQUBSDM+DEoxyGA/AyLlwNI/xUAEiNOR+NJA/ACJTzYAGWQKZcMitgwhTRlYBcTxQLyeARIvzKjS5ANQASXGQEUDR8EgAQCeODauSnDz4AAAAABJRU5ErkJggg==>

[image14]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABIAAAAbCAYAAABxwd+fAAABIUlEQVR4Xu2UoUtDURSHj2CY6IJMEEEQF9ZMsmYZahyIVYxq39LWFvYPiE2TMIwml1aMYtjKmgZBMArCLAv6nd032D3z6b3JoB98PN77XS733nPuE/mTzGAV+3iJc34czi6+4TG+4oYfhzGLV/iMe9jGBW9EIMv4iB2cN1kUmzjACxvEUsYPrNgglGtxE1hzk4NCWcR7cWVfMlkUeXzBG8yYLIotHGLTBrGciDuXAxvEcorvWLRBDNp82oTa0avmew2PxBWgnrynktbRh1jCW3HXZRufJvIp9iX9fHawKz+0hG6jIa5Suq11Px6hWUvc7+VLxjddV6K9cybTg7WfNNOKpjKe6AHvcM2PR6xgT9xl/hbd2jkWbJCgK8wmz39+m0+oKzJtR5WbuAAAAABJRU5ErkJggg==>

[image15]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAaCAYAAAC3g3x9AAABCklEQVR4XmNgGPEgAIjnAvEsNDwTiIWR1BENdIE4BIiXAvF/IH4P5TsDMSuSOpLBJAaIgfPRJcgBvEB8mAFiYDSaHFlAE4jfAvFXIDZGkyMLwLw7B12CHEAz734CYn00ObJAEAPEdaeBWBBNDgSYGbCL4wSEws+SAVWuAIiXAzEHkhgKwBe7ILHjQCyOLoEP4PKuBBAfAuIpQMwIxHxAXAXEqQx4chA/A8RAULYDaQIBkOJkIP4IxP+A2AUq3grERgyQFAEKBhQAEvjJADEMH0Z2OShJgfRdB2JFqBjFABSBsCCgGHgC8SkglgbiMiDmRJUmHTgA8REgbgFiE1Qp8gEPA570NwoGEQAAhOA41PCKeUoAAAAASUVORK5CYII=>

[image16]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADAAAAAaCAYAAADxNd/XAAACMklEQVR4Xu2VT0gVURTGv4igKCorDDEqQoyiSJEUSwpqkVBuWogVhS2ilQm2CFzpQkgXUuKmSMRNf3QZ4U6lIFwVgeKmoESCNrVKaJH2fZ1z3ww336qkEeYHP2bumTvz5pw59z4gJycnJycH2EAfx0FnG31Jv9CD0bXMcJG+iYPOHjpHl2lTdC0TbKLPYUkUo5F+o8fjC1ngECwBtYpQO92nbYUZQBl9TQ+kYpnhCr2TGtfQ73QgFVOSY3SLj9VWSuh0YUZCM13EytdWhRFYEgElo57f7eOddILWF2ZkDCXwAlZdVfoDrN9r/fojOgprLbGO9tNKH6fRemqlD2mVx7bS27Df0Fc97HHtaHfpU3oDdm857fb4XtpOT8F+syg3YTvMVz9+pj/oT7oAS6a6MBtooNfpM7o+FRcan4DdE3ascXoJ9hIqxhQ9B0toh8d1fYhupC2wLbuHboZ1gwpbFPXzO9jLq9JqGVVMY3kmmfobJfyEdkXxgBb8JyQJzNJdfr6PHqXTsOcEdM9HP9caVOHCjpd+VlHUHqHnA9tXiAVUlSNx0IkTmEKy+APxS4V7hBKY96OI5/41+qxa9GddcZJO+nmcgFqyzs/VLrfoPTroY6Fi6EuJVU9AfdpHO5EsbG2dWjP7Yb2t1puBLeSrsC+mF++l52FFeECHaQd9BWtVzX9Ll/yoP1c96z29gH9ICf7cGY5F4zRKtNSPaVQMrbn4Wf+Fy3FgLVFBr8XBnJw1wC+czWPd+IlrOwAAAABJRU5ErkJggg==>

[image17]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAmwAAAAwCAYAAACsRiaAAAAFZ0lEQVR4Xu3ca8hv2RwH8J8Yud+LaeQacgsxlFvnBUVCIXelhHK/1aCpOY3kVsr9muIFCnklknhQkhdCZDKUmSYvRmPywhtyWd9Za83e9vznmWee8zzHOcfnU7/2/q///u/LWrv27/zWfk4VAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADA4tHbBgAAzhzntXj6thEAgDPHV7YNAADH7RYtbrltPE1y7PNbnGjx0OrVqxe0uM1qmzPJbVs8Zdt4AHfdNpyl7l59vDJW8fAWj1i+BgCOwh1avHHTdnGLk5u20yXn8+8R725xbYu/t/jreqMzyPNa3GrbuLKrfyPXdy7Yq2WsXtLi6vHZFDEAHKG9bUP1qskXt43HINW0xNZjqz/0k6zFHcfnVHKOU6p5z902rjyherVvxodafGf1+ZHLptfb2zYMP9s27JCq4n7nc7plHHb5W/WxmpW1H7e4Yvn6eg9qccG2EQC4aS8bywe0+NZYT8XoyWP9HWM5Pa7Fgzdth/W9EVszYZvfzarbcSds8Yttwz4+W/tX12JX/8aXxjJTiZlWvTFXbhtOUfr2MG5ffQyy3ErClrGa3+3V7oQt4/fsbeMpmNOwAHBOSyI0H6BZ3n+sv2csn9/iXmM9kli8spb/wuLtLT461u/U4sst7t3ibi0+0uKl1fdxc82EbVb51gnbiVoqWnM9VZuntvhmLUlDKlMvavGmsZ7z+GT184wknkmaZoXvIS0+WPsnbJ+uZbp2V7xr2fQ6N9a/OebJ6u8Jvne0rX2iln0lYXtz9XOfFcn0ba4r1teZ/X2m+tTklOtKv+S6c30XVR+f+EL1fkv/vb/Fh0f7zZWEbV2R3aslYctxk6g+rPr45VyT6M53JHO/fL3FA8fn3E+vbnGPuuGYrveVRPnP1cc/sn22zT361hbPGAEA54QkGn9q8bux/pfqiditqz9MX7xsep1ZJXlt9YfkfUf7rB7lwZ2kYj6w98byoPIgTlKRc/lR9WTi5eNzHuSxV70CmLhktD1+LH9VSyUuD/85jZd9xh9b3LnF28bnz7f4TS2VsP0StrWDJje7+jfLyLttPxnr0+zHeT1XjeXs9/Rt+v3C0RbzOjOlm+Qm79UlWcy+8t38TbafFbafjmXePYvL63B/2HGixT+qj1WSp1S9Lqs+RZop5PRtZLo4x5/nNsdj3j+ZRo205zyS0K3HNNb7inWSOKuxP69+nKfV7mogAJyV8pC/y1jPgzQxfbzF7VafYyYO62Qo1glbHpSHTdgOIsd6Z/X3wJ442v4wlkm47jPWZ1KwXk9icM/qCed0TS2VsIMkbG+o3e/e7bJf/2Yf6eN1ojSTkmlOic5+f0yLJ1VPvLbX+Zzq74llu7yHmH3NhCxmwpbkdPbXdJDrPoyZiMW8hpmwZfnd6snZXvX7Zj1m6zHNPyDW+4p5ryXm/RfrxBQAznk/rKWq9c8W96v+YPx19WTjty0+ML5/RYvXV08Qsl2qSGnL7143tjkqeR8slbeTq7ZfVj9+lt9ucWn1Ks9Mln7Q4jXVp+Ticy1eVb2KmPh+9URsVob2s04qTkWSqkw/py9f2OIt1ZOPJDEfqz71/K/qVan0+++rV6Qylfm+uuF1Zpo37Zn6/Gr1faWCmOvM71K9/Fr1Kli+y+8/VctxnllHL/dApmufVcs15Pzz16RJZnP8nN83qo9P2uf/a7ce00fVf+8rkvxdPNbnd5kGzXFS5QOA/zt5H+1sdlRJVhKD4zL/yhIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+B/5D1Jw4RtLUeRQAAAAAElFTkSuQmCC>

[image18]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAaCAYAAAC+aNwHAAABJ0lEQVR4Xu2TPS9EURCGT6IhwUYkEok/gGIL1YqShAiVQq/cjk6v0kl2iw0JfoGGH0BHq9ASiUJERYIEz5tz9u7ccVbUsm/yFPeduXNmzkcI/04bcAJ3iRtoJZqwBgNFdkazISadwxccpm+xB+8hFv5VQ3ABnzDvYovJH3N+SZPwBPcw4WIz8AJzzi9pKcT2T6G/S6zq/EL6QT/m2tf+PMO180tSy2pdI9RgHNbhGF5hF4aL7Iy0qlZ/gIMQj+8W3mDZ5HXVdogz7hhvFC7hEaaNn5XmV4FV5x8lf9P5P/QBVzBivPa+qMCK8bNS0r7z2vtiC0x1wh1pViXVoc/4ujTqzBbQmymk49IuK8Gi45MG4Sx5DdiCSor9WepI11iPasHFeuopfAP870KX6/TMzgAAAABJRU5ErkJggg==>

[image19]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAFUAAAAZCAYAAABAb2JNAAACyElEQVR4Xu2YTYhOURzGnwlFFPJREiYfycrXirAQolgooihWWNgpmpJsZG8lH8mKkFKUhTQhTWNhQxZSw8LORliQj+fxnzNz3v/rvu89973mncX51a/pnnvuufc893/ve+4AmUwm85cZdD1dSCe5fe2YS9fReXSC2zde0Jx6aY9rb8cyugaWTxK36BC9Su/TT3RH3KEFvfQVvUSf0rd0VdxhHKBiGaT9dFrjrkIU/k5YHspliN6k06M+hUyhL+ic4W0Ndph+Dh1aoApVoBpDqErP03cjPbrLFvqRfqO/kRbqVvoDo5WtfJTTtZEeLdhOj7q22fQ1RsMq4jj95dpWwCo99TH7H0yGvZJ2IS1UzfshLIMY5eTn24QmruR1R2N04n5YQEWEPgowRpN4D7sxdbLRNySQGupa+pU+cO1hnJaEADSI5zo95Rsjwol1fEwIWxdQJ5rgAt9YktRQNW/1VwYxYc7KrZDwqFYJVdWtR2GsQj1GLyJ9ZSJSQ72ADkINnaqEGi50rEJVmAq1D+nLttRQNffKoa6E/cpXCVVLrqqhKhStHHRxKS6md+k9uhzlSQ31CjoIVTu78U5dQp/TDxX8Apuwgi1LaqgdvVMn0jtoXuiHYP4VdmAmbN3m17PhRs137Z2ymj6ji/yOEqSGugG2Rn3k2sM4Pa69if30gGsL69T462EbHYi2xVnYyWPCj59uWJ3chi3Iq9AuVD83zVvbKpoYrVM1Tltm0SdoDHAf/R5th8rVgPFFLYV9sYRfZN3BPtgjUjd6otp9jBRxBHbtL9Fc6UVzO0h/RtvKRznJUryhj2EVew72WXcy2q+wTsBO4kt/M70Bq/jLsGP3NvSoB//VV4bwDlRg3kDR3FQop+kZWC7634E+yVVIpdAAm2AH74ZVbwp6fA4N/53q9tWFPjm7wR5YLrpBqUu5TCaTyWQymUw6fwD1ebVcMxsDWAAAAABJRU5ErkJggg==>

[image20]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAaCAYAAAC+aNwHAAABFElEQVR4Xu2Sv0tCURTHj1Rg2NIYGTo4Bg3hWJNEo7gUCP4LbQUObk2NbdHiUkurSzhEa/9ARJOjQxEkmFD2OR1/vHt8kK7iBz7wON/7zr3vviMyt6xiBZ/xE9v4jke4hBfjpZPk8RV7WIvU03iPN1iO1ANKYi8+YTaM/tgWO8mOD4Z84wtmfDAghU1c94GSxS/cc3VPHRO+ODzaoQ8ceoFFX1TOsI+bPpiGZbwTa6DPM7OGD2IN4tiIMbiD/xq0xAZJc/UNk8EKOBcLJ253gA6O5g2JeVnZxQ7mfCB2L7diDaouG6E7n+Kj2MhGOcEu/mDBZQHa5ENsJx3lK7GhucQtvJYpfvMK7uOx2HcfhPGCBfH8AnapMQv0JUFVAAAAAElFTkSuQmCC>

[image21]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABoAAAAZCAYAAAAv3j5gAAABcElEQVR4Xu2UPS8EURSGj6CQSGx8FBoVhWoTOhEdIaGQbGKTVej8AAmN6PwBNRGdQodEISIqoaVfP0AjoSA+3nfP3MlxxiwzEYXMkzzJ3ns+7p2Ze1ek4JcowTE4ANtd7DuG4Ihoj6Z0wzrcgUfwHs7YhBRa4KxoDWvrcB92mZyYDngI+6Ixi5fgQ0howiR8Ea0h7HENd+MMwzR8c3O98FZ0E2kwdiKaZ1mWZL/GTri6D3TCczjs5i2j8BEeu/k5+O7mpB/eiRZ49uCanzQwxobMs4QNsHcMd8wPn2ehLcmwUJjMsxDjP16oLHq68iy0LRkW+rNv1AYPJHlKwqljURrjonfo1M2HUxfuVkw1CljCPbI3fApemjFjHPOCWniPfL8GPfBKPjddgM9mHJ6QDfg7sAhfzZg9LiK/hMf8DNbgJnyCqybO17Ai2tS+Ev75rsMN0Vpu+AYOmpwEE6LJ86JPmYWKaC2/aauLFRT8Zz4A6lRVAl7VuxEAAAAASUVORK5CYII=>

[image22]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABoAAAAZCAYAAAAv3j5gAAABiklEQVR4Xu2UvytGURjHv0JRQn4kJVkok8FEMUhiMChFWZQ/wKCYjP4JJVkMogiL9GYwiLLZMdgYhCK/vs/73KPnnnvPHVyT3k996p7nnHuec+65zwFK/BH1tJ+200qvL0QtrfNiZbTFi/3QQK/pGj2g93TMDggwTr+g763SXfpEj+wgRzXdp81RW1Y0Sx/dgAxcIusWdOEJRumnF2uiV9BFZCGJLqC7WaRd0IUmkOA6kolq6Ant9uI+kmjDD6bRSm/os98BnWDJD3q4RMvQXc3TxtiICFmxHHyeRHL4A7STbtIHOmwHCb3QJHkSHZu27OaS3plYkR7o3/XbRGnIe/L3xch7RmmsQBNV2WAF3Y46LO6vk08bwtXQhxd3O5K5Y0xHHRZXR/Z6GaFnpt1HbxHfteziEMn5isgBniM+6RR9M223Q5lAngUp5h06F7WFIfqKwBUkyG9eoDPQb/wCrXSHFPYC9DPZym+jp9Aa2qPvyLiCHIPQRBMIFF2Acuh7k7QDgSuoRIl/yjevhlKAQ+DB7AAAAABJRU5ErkJggg==>

[image23]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABoAAAAZCAYAAAAv3j5gAAABJ0lEQVR4XmNgGAVUBIxArALEzOgSBIAqEBsBsQC6BDagBcQbgfgBEEuiSuEEIIf5APEWIJ7LANG7Aoj5kdTAgS4QXwHib0D8H4gfMhBvkSsQ/2aAWAgCokB8Gojnw1UgAVYgFgdiYyD+ykC8RZxAvAOIr6KJpwPxPzQxFECqRTD1W9HEfRkgIYMTkGpROQPEwIVo4jBzcJpBqkWTGOhkEcgCulg0h4FOFtEtjmwYIHloD5o4LNXB8hYGIGSRGxCfQOKDcj+ID8qgyACUj/Ambw8GiAtfAbEBA6qLeID4AAPEABAbBmKA+C8SH2T5ISjGACDXg3wBMgQdwwwFWVrMADEU2QGgUqUGiOuAOBqITzFAijNQwUwTEMIAsQgU/KSW/KNgFAxlAAASUVDIjNcW9gAAAABJRU5ErkJggg==>

[image24]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGkAAAAZCAYAAAAyoAD7AAAEf0lEQVR4Xu2YXahVVRDHRyxQktSstMgPwoQgMqyEIkQiJSGVMlKwD+lF8MEehMKIEKTXQsEepJAIsUJS0Cwt4upT5IsPhVIGV4nEwiIpCUNrfs6ec+auc/Ze55x7rldw/+HPOXv2WrPXmpk1a9YSqVGjRo3rEpOUjypnKG9M3nWCCcp5yntkaP8xyjuVY4MM3KSckshGG4ydOWCLbvGYtM49Be961S+fKAeV7yv3K88pl8QGGXyq/Ev5sXJAeSy8w3nIeL9d7BvfKS8qlzWbjSoIpKfE5s74BpUfKSeGNmW4Rcx+9BtQnle+IKbT4fqxa7f6r2C88qjytuIZhWvEPtYJZip3Km8unnHun83XDSf9F8h7Bh0nMppYpPxXmuPBFthkR6NFe7AyCMyNQUY2+l35fJC5/jXFc9RftfIaeFK5NpHdqvxezIFVwAGHlNOD7AMxR4wrnmlzQGwVbVUuFUt11wqY4xdi843AJpcTWYpHxPrdkcjfUn5V/I/6savD9WP/ShA5ePOJRO7Rf28iT/GMtE5kjvLt8IyuvcoHg2yk8FAq6ACM62/lZ4mcYCLYqkCbP5T3JfLXxOwHon4PXOD6CdxKEAGnpL0BWRF8rAxTlT+JLeN3lL8oLyh3Ke8K7dxJ85Xvig2KlJAWEv3ADWKB0w2YI8ZivhFu3HSVRNwutr+6sdmfNim/kWa/nH7sXwlWCptZL07yj+CYlWKrkkF+rfwhtPOUOCi2fz2n/E35oYxM2jss9p1OgXGrjFjlJPCA8rSYDrLKPhlaveX0Z53kDYfjJAYQCwBKUVYXUQ1w0kHlikYL20DptznI+gW+87lYwHQC30PLjJhzEhnhZRlaGG2TZgDm9GedNFesihuuk9rJq/Yzb/Nz+iIBEYmRuiFnsleUJ5TLJZ9W35NqI1Y5iaKA/keUjytPStNRFEogpz/rJAZAo16c5A5OCwfXycZYhruVZ6S1bwSbLBFJKumWv4oZBqNhvCrk9owqJ21Q/qicVTyzejy9XSpkOf1ZJ5GSdkvrwdWru3bOc3jfdCX5Pkfai2ckigeHD5C02G9wII2pNQdPz14yO7z6GpPIHR6M7QKZwsmzRNQf92DXvyPISrFKuTqR+TkpnogXi1UtEfRNnfSwWDFBXxxJtfel8v7QhpKfVZTq6wco/8sM2w6Mk3FwuIzgHBPnRjuqU18R3g8jp9/jesidFPVPbrRo6seGWUwRy6nRIVRrXNs44orgv8NLUO/LYDdKc6kDTtsDymnFM3mcFUibfl8LMY70zNIJuB2IY0YPNoEOj/zoOPqxSrCXw/cproocrt/bRf3R7pU4LlY6s6I4LbMSXg3vMT75lw+lUUN6w1GvK/eI9X0xvKf9OrHI4t6KfWKkroVIo/HA2Cm4mnlD+aaYDb4Vm9Ps0GZmIaMgcVCUrFf+U/xuUZ6V1ns518+8y/RngZIFYgqelu5vp+n7kli0lfVFjv6F0pshO8Fw9T4rNkacnasKI3AI/eg/S8qDj+uzXvTXqFGjRo0aNWrUqFHj6uB/POcgqZUETMsAAAAASUVORK5CYII=>

[image25]: <data:image/png;base64,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>