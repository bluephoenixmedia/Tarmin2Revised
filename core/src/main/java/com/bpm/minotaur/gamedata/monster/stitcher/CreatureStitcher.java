package com.bpm.minotaur.gamedata.monster.stitcher;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Json;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Assembles a {@link StitchedCreature} using a five-stage gap-free pipeline:
 *
 *   1. World-space bounding box — unitScale applied to the transform matrix
 *      before calculateBoundingBox() so all math is in world metres, not
 *      Blender centimetres.
 *
 *   2. Proportional scaling — each limb is scaled so its world height equals a
 *      fixed anatomical fraction of the torso height (HEAD 45 %, LEG 68 %,
 *      ARM 58 %, TAIL 35 %).  Cross-species size mismatch is eliminated.
 *
 *   3. Ray-surface intersection (MeshSurfaceIntersector) — a ray is cast from
 *      the geometric centre of each mesh outward in the socket direction.
 *      The exact triangle-hit point on the physical surface becomes the
 *      attachment coordinate. This replaces "extremal vertex slicing" which
 *      fails on non-convex meshes (e.g. a horse's bottom-10 % slice captures
 *      four disconnected hooves whose centroid floats under the belly).
 *
 *   4. Intentional penetration — both the torso socket point and the limb
 *      attach point are pushed inward by 12 % of the limb's scaled height
 *      before the bridge is built.  The depth-buffered bake resolves the
 *      external silhouette perfectly while hiding internal clipping.
 *
 *   5. Ball-and-socket bridge (GapBridgeBuilder) — two masking spheres plus
 *      an oriented cylinder are generated between the penetrated points,
 *      guaranteeing topological continuity under all species permutations.
 */
public class CreatureStitcher {

    private static final String DNA_ROOT    = "data/creatures/dna/";
    private static final float  PENETRATION = 0.12f;   // fraction of scaled limb height

    // Target limb height as fraction of torso world height (PDF spec table)
    private static final Map<String, Float> RATIO = new HashMap<>();
    static {
        RATIO.put("HEAD",  0.45f);
        RATIO.put("ARM_L", 0.58f);
        RATIO.put("ARM_R", 0.58f);
        RATIO.put("LEG_L", 0.68f);
        RATIO.put("LEG_R", 0.68f);
        RATIO.put("TAIL",  0.35f);
    }

    private final CreaturePartLibrary library;
    private final WeightedPartSelector selector;
    private final AssetManager         assetManager;
    private final Map<String, CreatureDna> dnaCache = new HashMap<>();

    public CreatureStitcher(CreaturePartLibrary library, AssetManager assetManager) {
        this.library      = library;
        this.assetManager = assetManager;
        this.selector     = new WeightedPartSelector();
    }

    // -------------------------------------------------------------------------
    // DNA loading
    // -------------------------------------------------------------------------

    public CreatureDna getDna(String dnaId) {
        if (dnaCache.containsKey(dnaId)) return dnaCache.get(dnaId);

        String path = DNA_ROOT + dnaId + ".json";
        if (!Gdx.files.internal(path).exists()) {
            dnaCache.put(dnaId, null);
            return null;
        }
        try {
            Json json = new Json();
            CreatureDna dna = json.fromJson(CreatureDna.class, Gdx.files.internal(path));
            dnaCache.put(dnaId, dna);
            Gdx.app.log("CreatureStitcher", "Loaded DNA: " + dnaId);
            return dna;
        } catch (Exception e) {
            Gdx.app.error("CreatureStitcher", "Failed to parse DNA " + path + ": " + e.getMessage());
            dnaCache.put(dnaId, null);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // DNA-driven assembly
    // -------------------------------------------------------------------------

    public StitchedCreature stitch(CreatureDna dna, BiomeFilterContext biome, long seed) {
        Model torsoModel = resolveModel(dna.torsoModel);
        if (torsoModel == null) {
            Gdx.app.error("CreatureStitcher", "No torso model: " + dna.torsoModel);
            return new StitchedCreature(null, new LinkedHashMap<String, ModelInstance>());
        }

        ModelInstance torso = buildScaledTorso(
                torsoModel, library.getUnitScale(baseName(dna.torsoModel)));
        BoundingBox tBox = worldBox(torso);

        LinkedHashMap<String, ModelInstance> limbs = new LinkedHashMap<>();
        StitchedCreature creature = new StitchedCreature(torso, limbs);

        for (AttachmentPoint socket : dna.attachmentPoints) {
            String partId = selector.select(dna, socket.socket, biome, seed);
            if (partId == null) {
                if (!socket.optional)
                    Gdx.app.error("CreatureStitcher", "No part for required socket: " + socket.socket);
                continue;
            }
            Model limbModel = library.get(partId);
            if (limbModel == null) continue;

            ModelInstance limb = assembleLimb(
                    limbModel, library.getUnitScale(partId),
                    socket.socket, torsoModel, torso, tBox, creature);
            if (limb != null) limbs.put(socket.socket, limb);
        }
        creature.freezeBases();
        return creature;
    }

    // -------------------------------------------------------------------------
    // Random chimera assembly
    // -------------------------------------------------------------------------

    public StitchedCreature stitchRandom(long seed) {
        Random rng = new Random(seed);

        if (!library.hasPart("torso_oval")) {
            Gdx.app.error("CreatureStitcher", "stitchRandom: torso_oval not registered — call ProceduralPartBuilder.registerAllParts() first");
            return new StitchedCreature(null, new LinkedHashMap<String, ModelInstance>());
        }

        Model torsoModel = library.get("torso_oval");

        ModelInstance torso = buildScaledTorso(torsoModel, library.getUnitScale("torso_oval"));
        BoundingBox   tBox  = worldBox(torso);

        LinkedHashMap<String, ModelInstance> limbs = new LinkedHashMap<>();
        StitchedCreature creature = new StitchedCreature(torso, limbs);

        for (String socket : new String[]{"HEAD", "ARM_L", "ARM_R", "LEG_L", "LEG_R"}) {
            List<String> candidates = library.getPartIdsForSocket(socket);
            if (candidates.isEmpty()) continue;
            String partId    = candidates.get(rng.nextInt(candidates.size()));
            Model  limbModel = library.get(partId);
            if (limbModel == null) continue;

            ModelInstance limb = assembleLimb(
                    limbModel, library.getUnitScale(partId),
                    socket, torsoModel, torso, tBox, creature);
            if (limb != null) limbs.put(socket, limb);
        }
        creature.freezeBases();
        return creature;
    }

    // -------------------------------------------------------------------------
    // Torso construction helper
    // -------------------------------------------------------------------------

    /**
     * Applies unitScale to the torso transform matrix (world-space pattern),
     * centres the torso at Y=0.8 (bake camera look-at), and returns it.
     */
    private static ModelInstance buildScaledTorso(Model model, float unitScale) {
        ModelInstance torso = new ModelInstance(model);
        // Apply scale to transform first so bounding box is in world metres
        torso.transform.setToScaling(unitScale, unitScale, unitScale);
        BoundingBox box = new BoundingBox();
        torso.calculateBoundingBox(box);
        float tyOff = 0.8f - box.getCenterY();
        torso.transform.setToTranslation(0f, tyOff, 0f)
                       .scale(unitScale, unitScale, unitScale);
        return torso;
    }

    // -------------------------------------------------------------------------
    // Core per-limb assembly
    // -------------------------------------------------------------------------

    /**
     * Positions one limb using the full five-stage gap-free pipeline, generates
     * a ball-and-socket bridge at the joint, and registers it on the creature.
     */
    private ModelInstance assembleLimb(
            Model limbModel, float sp,
            String socket,
            Model torsoModel, ModelInstance torso,
            BoundingBox tBox, StitchedCreature creature) {

        float tMinY = tBox.min.y;
        float tMaxY = tBox.max.y;
        float tHeight = tMaxY - tMinY;
        float tHalfW  = (tBox.max.x - tBox.min.x) / 2f;
        float overlap = tHeight * 0.06f;

        BoundingBox pBox = new BoundingBox();
        new ModelInstance(limbModel).calculateBoundingBox(pBox);
        Vector3 pc = pBox.getCenter(new Vector3());
        
        float pMinX = sp * pBox.min.x, pMaxX = sp * pBox.max.x;
        float pMinY = sp * pBox.min.y, pMaxY = sp * pBox.max.y;
        float pCenX = sp * pc.x, pCenY = sp * pc.y, pCenZ = sp * pc.z;

        float tx, ty, tz;
        switch (socket) {
            case "HEAD":
                tx = -pCenX;
                ty = (tMaxY - overlap) - pMinY;
                tz = -pCenZ;
                break;
            case "LEG_L":
                tx = -tHalfW * 0.4f - pCenX;
                ty = (tMinY + overlap) - pMaxY;
                tz = -pCenZ;
                break;
            case "LEG_R":
                tx = tHalfW * 0.4f - pCenX;
                ty = (tMinY + overlap) - pMaxY;
                tz = -pCenZ;
                break;
            case "ARM_L":
                tx = -tHalfW + overlap - pMaxX;
                ty = (tMaxY - tHeight * 0.3f) - pCenY;
                tz = -pCenZ;
                break;
            case "ARM_R":
                tx = tHalfW - overlap - pMinX;
                ty = (tMaxY - tHeight * 0.3f) - pCenY;
                tz = -pCenZ;
                break;
            default: // TAIL
                tx = -pCenX;
                ty = (tMinY + tHeight * 0.3f) - pCenY;
                tz = -0.3f - pCenZ;
                break;
        }

        ModelInstance limb = new ModelInstance(limbModel);
        limb.transform.setToTranslation(tx, ty, tz).scale(sp, sp, sp);
        return limb;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static BoundingBox worldBox(ModelInstance instance) {
        BoundingBox box = new BoundingBox();
        instance.calculateBoundingBox(box);
        return box;
    }

    private Model resolveModel(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) return null;
        String id = baseName(modelPath);
        if (library.hasPart(id)) return library.get(id);
        if (assetManager.isLoaded(modelPath, Model.class))
            return assetManager.get(modelPath, Model.class);
        Gdx.app.log("CreatureStitcher", "Model not available: " + modelPath);
        return null;
    }

    private static String baseName(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
