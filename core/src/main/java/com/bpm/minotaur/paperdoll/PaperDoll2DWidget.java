package com.bpm.minotaur.paperdoll;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.gore.BloodCoat;
import com.bpm.minotaur.gamedata.gore.BloodStain;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.paperdoll.blood.AlphaMask;
import com.bpm.minotaur.paperdoll.blood.BloodOverlayRasterizer;
import com.bpm.minotaur.paperdoll.calibration.AssetDataFiles;
import com.bpm.minotaur.paperdoll.calibration.CalibrationStore;
import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;
import com.bpm.minotaur.paperdoll.calibration.LayerPlacement;
import com.bpm.minotaur.paperdoll.calibration.PaperdollLayerMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2D Composite Paperdoll Widget.
 *
 * Renders the player character ("a desperate father on a mission") and equipped armor
 * layers over a standardised 1024x1536 master canvas.
 *
 * Layers are baked NORMALISED -- cropped to their artwork, scaled into a per-slot box
 * and centred -- with placement supplied at draw time from
 * assets/data/paperdoll_calibration.json. Placement therefore lives in data that can be
 * corrected once per item, rather than being burnt into pixels where every correction
 * means re-touching a PNG.
 *
 * A layer with no calibration entry falls back to its slot's default placement, NOT to
 * identity. Identity would stretch the layer across the whole portrait, which was the
 * right behaviour for the old un-normalised pixels but puts a normalised layer at the
 * centre of the canvas — a new helmet would render at chest height.
 *
 * Layers are rendered strictly according to the agreed 10-layer Z-ordering, over an
 * opaque portrait backdrop that is always drawn first:
 *   0. Portrait backdrop (frame and vignette, no figure)
 *   1. Back Cloak (Z=10)
 *   2. Base Father Character (Z=20)
 *   3. Legs/Trousers (Z=30)
 *   4. Boots (Z=40)
 *   5. Chest Armor (Z=50)
 *   6. Arms/Pauldrons (Z=60)
 *   7. Gauntlets/Hands (Z=70)
 *   8. Main Hand Weapon (Z=80)
 *   9. Off-Hand Shield (Z=85)
 *      Head redrawn (Z=88) -- always, see headTexture
 *  10. Helmet/Headgear (Z=90)
 *  11. Front Cloak Clasp (Z=95)
 *
 * Every layer can carry blood, drawn straight over it with the same placement. See
 * {@link #absorbBlood} for how a splash picks which layer it lands on.
 */
public class PaperDoll2DWidget extends Widget implements Disposable {

    public enum PaperDollSlot {
        CLOAK_BACK(10, "cloak"),
        BASE_FATHER(20, null),
        LEGS(30, "legs"),
        BOOTS(40, "feet"),
        // Paired slots draw both limbs. Each limb is its own layer with its own
        // placement, because one rigid transform freezes the spacing between them into
        // the artwork -- the boots were baked 337px apart where the feet are 502px
        // apart, so no calibration could fit both size and spacing at once.
        BOOTS_RIGHT(41, "feet"),
        CHEST(50, "chest"),
        ARMS(60, "arms"),
        ARMS_RIGHT(61, "arms"),
        HANDS(70, "hands"),
        HANDS_RIGHT(71, "hands"),
        WEAPON_MAIN(80, "weapon"),
        SHIELD_OFF(85, "shield"),
        HELMET(90, "head"),
        CLOAK_FRONT(95, "cloak");

        public final int zIndex;
        public final String folderName;

        PaperDollSlot(int zIndex, String folderName) {
            this.zIndex = zIndex;
            this.folderName = folderName;
        }
    }

    private static class LayerEntry {
        final PaperDollSlot slot;
        final String layerId;
        Texture texture;
        /** The item this layer draws, whose blood it shows; null for the base and the editor. */
        Item item;
        /** Which of the item's blood coats: "" for a single layer, "left"/"right" for a pair. */
        String bloodPart = "";

        LayerEntry(PaperDollSlot slot, Texture texture, String layerId) {
            this.slot = slot;
            this.texture = texture;
            this.layerId = layerId;
        }

        LayerEntry withItem(Item item, String part) {
            this.item = item;
            this.bloodPart = part;
            return this;
        }
    }

    /** A blood overlay texture and the coat version it was painted from. */
    private static final class BloodTexture {
        Texture texture;
        int version = Integer.MIN_VALUE;
    }

    /** The mirror slot that carries a paired layer's right limb, or null if unpaired. */
    private static PaperDollSlot mirrorOf(PaperDollSlot slot) {
        switch (slot) {
            case BOOTS: return PaperDollSlot.BOOTS_RIGHT;
            case ARMS:  return PaperDollSlot.ARMS_RIGHT;
            case HANDS: return PaperDollSlot.HANDS_RIGHT;
            default:    return null;
        }
    }

    private static final String CALIBRATION_PATH = "data/paperdoll_calibration.json";
    private static final String LAYER_MAP_PATH = "data/paperdoll_layers.json";

    /** The base portrait is not calibratable; it defines the frame everything else fits. */
    private static final String BASE_LAYER_ID = null;

    private static final int CANVAS_W = 1024;
    private static final int CANVAS_H = 1536;

    private Texture baseFatherTexture;
    /**
     * The portrait with the father removed, drawn under every layer. The page art
     * underneath (new_inventory.png) has its own father painted into the frame -- a
     * different painting, in a slightly different pose -- and since base_father became a
     * transparent cutout that copy showed through around the body and any clothing as
     * doubled boots, legs and hands. This covers it. Built by
     * tools/make_portrait_backdrop.py.
     */
    private Texture backdropTexture;
    /**
     * The father's head and beard alone, redrawn over the armour and under any helmet.
     * Chest pieces are fitted to the torso, but anything with a collar or a high neck
     * reaches up over the beard, and no placement can move a collar without moving the
     * garment. Cut from base_father on the same canvas by tools/make_head_overlay.py, so
     * it lands on the body exactly.
     */
    private Texture headTexture;
    /** Above every body layer, below the helmet and the front cloak clasp. */
    static final int HEAD_Z = 88;
    private final Map<PaperDollSlot, LayerEntry> activeLayers = new HashMap<>();
    private final Map<String, Texture> textureCache = new HashMap<>();
    /** Every loaded layer's silhouette at blood resolution, for aiming and clipping blood. */
    private final Map<Texture, AlphaMask> masks = new IdentityHashMap<>();
    /** Painted overlays, per coat and per silhouette: the body coat is painted twice, body and head. */
    private final Map<BloodCoat, Map<AlphaMask, BloodTexture>> bloodTextures = new IdentityHashMap<>();
    /** Blood on bare skin, drawn over the base layer and again over the redrawn head. */
    private BloodCoat bodyBlood;
    private final Set<String> missingTextureWarned = new HashSet<>();
    private final List<LayerEntry> renderList = new ArrayList<>();

    private final CalibrationStore calibration = new CalibrationStore();
    private final PaperdollLayerMap layerMap = new PaperdollLayerMap();

    public PaperDoll2DWidget() {
        loadBackdrop();
        loadBaseFather();
        loadHead();
        reloadCalibration();
    }

    /** Re-reads calibration and the layer map from disk. */
    public void reloadCalibration() {
        calibration.load(AssetDataFiles.readable(CALIBRATION_PATH));
        layerMap.load(AssetDataFiles.readable(LAYER_MAP_PATH));
    }

    public CalibrationStore getCalibration() {
        return calibration;
    }

    public PaperdollLayerMap getLayerMap() {
        return layerMap;
    }

    /**
     * Shows one layer by its id, for the calibration editor.
     *
     * The normal path goes through an equipped {@link Item}, but the editor walks the
     * baked layers directly -- including layers no item maps to yet, which are exactly
     * the ones most likely to need calibrating.
     */
    public boolean showLayer(PaperDollSlot slot, String layerId) {
        if (slot == null || slot == PaperDollSlot.BASE_FATHER || layerId == null) {
            return false;
        }
        int sep = layerId.indexOf('/');
        String folder = sep > 0 ? layerId.substring(0, sep) : slot.folderName;
        String name = sep > 0 ? layerId.substring(sep + 1) : layerId;

        Texture tex = loadTexture("images/paperdoll/" + folder + "/" + name + ".png");
        if (tex == null) {
            activeLayers.remove(slot);
            return false;
        }
        activeLayers.put(slot, new LayerEntry(slot, tex, layerId));
        return true;
    }

    /** True for the mirror half of a paired slot, which no folder resolves to directly. */
    private static boolean isMirror(PaperDollSlot slot) {
        return slot == PaperDollSlot.BOOTS_RIGHT
                || slot == PaperDollSlot.ARMS_RIGHT
                || slot == PaperDollSlot.HANDS_RIGHT;
    }

    /**
     * The visual slot a layer folder belongs to, or null if the folder is unknown.
     *
     * Several slots share a folder, so the first match would be ambiguous. CLOAK_BACK
     * wins over CLOAK_FRONT because it shows the garment, with CLOAK_FRONT reserved for
     * the clasp drawn over everything else; the paired slots resolve to their primary
     * half, since the mirror is only ever reached through {@link #mirrorOf}.
     */
    public static PaperDollSlot slotForFolder(String folder) {
        if (folder == null) {
            return null;
        }
        for (PaperDollSlot s : PaperDollSlot.values()) {
            if (folder.equals(s.folderName) && s != PaperDollSlot.CLOAK_FRONT && !isMirror(s)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Persists calibration back to the assets tree. Editor only -- a packaged build has
     * no business rewriting its own assets.
     */
    public boolean saveCalibration() {
        FileHandle handle = AssetDataFiles.writable(CALIBRATION_PATH);
        if (handle == null) {
            Gdx.app.error("PaperDoll2DWidget", "No writable path for " + CALIBRATION_PATH);
            return false;
        }
        calibration.save(handle);
        return true;
    }

    private void loadBackdrop() {
        FileHandle handle = resolveFile("images/paperdoll/portrait_backdrop.png");
        if (handle != null && handle.exists()) {
            backdropTexture = new Texture(handle);
            backdropTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        } else {
            Gdx.app.error("PaperDoll2DWidget", "Missing portrait backdrop: images/paperdoll/portrait_backdrop.png");
        }
    }

    private void loadHead() {
        FileHandle handle = resolveFile("images/paperdoll/base_head.png");
        if (handle != null && handle.exists()) {
            headTexture = loadWithMask(handle);
        } else {
            Gdx.app.error("PaperDoll2DWidget", "Missing head overlay: images/paperdoll/base_head.png");
        }
    }

    private void loadBaseFather() {
        FileHandle handle = resolveFile("images/paperdoll/base_father.png");
        if (handle != null && handle.exists()) {
            baseFatherTexture = loadWithMask(handle);
            activeLayers.put(PaperDollSlot.BASE_FATHER,
                    new LayerEntry(PaperDollSlot.BASE_FATHER, baseFatherTexture, BASE_LAYER_ID));
        } else {
            Gdx.app.error("PaperDoll2DWidget", "Missing base father portrait: images/paperdoll/base_father.png");
        }
    }

    /**
     * Equips an item into a specific paperdoll visual slot.
     */
    public void equip(PaperDollSlot slot, Item item) {
        if (slot == null || slot == PaperDollSlot.BASE_FATHER) return;

        PaperDollSlot mirror = mirrorOf(slot);

        if (item == null) {
            activeLayers.remove(slot);
            // Clearing only the primary would leave a paired item's right limb — a
            // lone boot or gauntlet — floating on the doll after it was taken off.
            if (mirror != null) {
                activeLayers.remove(mirror);
            }
            return;
        }

        if (mirror != null) {
            activeLayers.remove(mirror);
        }

        PaperdollLayerMap.LayerRef ref = resolveLayerRef(slot, item);

        // Paired artwork is baked as two half-layers, "<layer>.left" and "<layer>.right".
        if (ref != null && mirror != null) {
            Texture left = loadTexture("images/paperdoll/" + ref.slot + "/" + ref.layer + ".left.png");
            Texture right = loadTexture("images/paperdoll/" + ref.slot + "/" + ref.layer + ".right.png");
            if (left != null && right != null) {
                activeLayers.put(slot, new LayerEntry(slot, left, ref.layerId() + ".left").withItem(item, "left"));
                activeLayers.put(mirror, new LayerEntry(mirror, right, ref.layerId() + ".right").withItem(item, "right"));
                return;
            }
        }

        Texture tex = resolveTexture(slot, item, ref);
        if (tex != null) {
            activeLayers.put(slot, new LayerEntry(slot, tex, ref != null ? ref.layerId() : null).withItem(item, ""));
        } else {
            activeLayers.remove(slot);
        }
    }

    /**
     * Convenience helper to equip an item into its natural visual slot.
     */
    public void equip(Item item) {
        if (item == null) return;

        if (item.isHelmet()) {
            equip(PaperDollSlot.HELMET, item);
        } else if (item.isTorso() || (item.isArmor() && !item.isHelmet() && !item.isShield() && !item.isRing()
                && !item.isGauntlets() && !item.isBoots() && !item.isLegs() && !item.isArms() && !item.isCloak())) {
            equip(PaperDollSlot.CHEST, item);
        } else if (item.isArms()) {
            equip(PaperDollSlot.ARMS, item);
        } else if (item.isGauntlets()) {
            equip(PaperDollSlot.HANDS, item);
        } else if (item.isLegs()) {
            equip(PaperDollSlot.LEGS, item);
        } else if (item.isBoots()) {
            equip(PaperDollSlot.BOOTS, item);
        } else if (item.isCloak()) {
            equip(PaperDollSlot.CLOAK_BACK, item);
            equip(PaperDollSlot.CLOAK_FRONT, item);
        } else if (item.isShield()) {
            equip(PaperDollSlot.SHIELD_OFF, item);
        } else if (item.isWeapon()) {
            equip(PaperDollSlot.WEAPON_MAIN, item);
        }
    }

    /**
     * Clears all equipped armor layers (preserving base father).
     */
    public void clearEquipment() {
        activeLayers.clear();
        if (baseFatherTexture != null) {
            activeLayers.put(PaperDollSlot.BASE_FATHER,
                    new LayerEntry(PaperDollSlot.BASE_FATHER, baseFatherTexture, BASE_LAYER_ID));
        }
    }

    /**
     * Which calibrated layer an item draws.
     *
     * The explicit map in paperdoll_layers.json wins; name guessing remains only as a
     * fallback for items the map has not caught up with, and the layer id it derives
     * still keys calibration correctly.
     */
    private PaperdollLayerMap.LayerRef resolveLayerRef(PaperDollSlot slot, Item item) {
        if (slot.folderName == null) return null;

        if (item.getType() != null) {
            PaperdollLayerMap.LayerRef ref = layerMap.resolve(item.getType().name());
            if (ref != null) {
                return ref;
            }
        }

        List<String> candidates = getCandidateNames(item);
        for (String candidate : candidates) {
            FileHandle handle = resolveFile("images/paperdoll/" + slot.folderName + "/" + candidate + ".png");
            if (handle != null && handle.exists()) {
                return new PaperdollLayerMap.LayerRef(slot.folderName, candidate);
            }
        }
        return null;
    }

    /**
     * Resolves an item's texture from disk cache.
     *
     * The mapped reference decides the folder, not the equipped slot: the two normally
     * agree, but when they disagree the map is the authority, and reading the folder
     * off the slot would silently load the wrong file or none at all.
     */
    private Texture resolveTexture(PaperDollSlot slot, Item item, PaperdollLayerMap.LayerRef ref) {
        if (slot.folderName == null) return null;

        if (ref != null) {
            Texture mapped = loadTexture(ref.texturePath());
            if (mapped != null) {
                return mapped;
            }
        }

        List<String> candidateNames = getCandidateNames(item);
        for (String candidate : candidateNames) {
            Texture tex = loadTexture("images/paperdoll/" + slot.folderName + "/" + candidate + ".png");
            if (tex != null) {
                return tex;
            }
        }

        String warnKey = slot.folderName + "/" + (candidateNames.isEmpty() ? "unknown" : candidateNames.get(0));
        if (!missingTextureWarned.contains(warnKey)) {
            missingTextureWarned.add(warnKey);
            Gdx.app.debug("PaperDoll2DWidget", "No paperdoll sprite found for slot [" + slot + "]: candidateNames=" + candidateNames);
        }

        return null;
    }

    /** Cached texture load; null when the path holds nothing loadable. */
    private Texture loadTexture(String path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }
        FileHandle handle = resolveFile(path);
        if (handle == null || !handle.exists()) {
            return null;
        }
        try {
            Texture tex = loadWithMask(handle);
            textureCache.put(path, tex);
            return tex;
        } catch (Exception e) {
            Gdx.app.error("PaperDoll2DWidget", "Failed loading paperdoll texture: " + path, e);
            return null;
        }
    }

    /**
     * Loads a layer and samples its silhouette on the way, while the pixels are still on
     * the CPU -- reading them back from the GPU later is not possible on every backend.
     */
    private Texture loadWithMask(FileHandle handle) {
        Pixmap pixmap = new Pixmap(handle);
        try {
            final Pixmap src = pixmap;
            AlphaMask mask = AlphaMask.sample(src.getWidth(), src.getHeight(), BloodOverlayRasterizer.DOWNSAMPLE,
                    (px, py) -> src.getPixel(px, py) & 0xFF);
            Texture tex = new Texture(pixmap);
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            masks.put(tex, mask);
            return tex;
        } finally {
            pixmap.dispose();
        }
    }

    private List<String> getCandidateNames(Item item) {
        List<String> candidates = new ArrayList<>();

        // 1. From template texture path (e.g. images/armor/bascinet.png -> bascinet)
        if (item.getTemplate() != null && item.getTemplate().texturePath != null) {
            String path = item.getTemplate().texturePath;
            int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            int lastDot = path.lastIndexOf('.');
            if (lastDot > lastSlash) {
                candidates.add(path.substring(lastSlash + 1, lastDot).toLowerCase());
            }
        }

        // 2. From item type enum name (e.g. BASINET -> basinet)
        if (item.getType() != null) {
            candidates.add(item.getType().name().toLowerCase());
        }

        // 3. From display name (e.g. "Bronze Leggings" -> "bronze_leggings")
        if (item.getDisplayName() != null) {
            candidates.add(item.getDisplayName().toLowerCase().replace(' ', '_').replace('-', '_'));
        }

        return candidates;
    }

    private FileHandle resolveFile(String internalPath) {
        // First try Gdx.files.internal
        if (Gdx.files != null) {
            FileHandle internal = Gdx.files.internal(internalPath);
            if (internal.exists()) return internal;

            // Also check assets/ prefix if running in desktop local path
            FileHandle local = Gdx.files.local("assets/" + internalPath);
            if (local.exists()) return local;

            FileHandle localDirect = Gdx.files.local(internalPath);
            if (localDirect.exists()) return localDirect;
        }
        return null;
    }

    /** The coat for bare skin; the base layer and the redrawn head both show it. */
    public void setBodyBlood(BloodCoat coat) {
        this.bodyBlood = coat;
    }

    /**
     * Settles splashes, given in master-canvas pixels, onto whatever is outermost where
     * each one lands: helmet over head, head over collar, armour over skin. A splash that
     * misses the figure entirely is lost, as it would be in the air.
     *
     * Call after equipping, so blood lands on what is actually being worn.
     */
    public void absorbBlood(List<BloodStain> splashes) {
        if (splashes == null || splashes.isEmpty()) {
            return;
        }
        List<LayerEntry> topDown = new ArrayList<>(activeLayers.values());
        Collections.sort(topDown, new Comparator<LayerEntry>() {
            @Override
            public int compare(LayerEntry o1, LayerEntry o2) {
                return Integer.compare(o2.slot.zIndex, o1.slot.zIndex);
            }
        });
        AlphaMask headMask = headTexture != null ? masks.get(headTexture) : null;
        float rectW = getWidth() > 0f ? getWidth() : CANVAS_W;
        float rectH = getHeight() > 0f ? getHeight() : CANVAS_H;

        for (BloodStain splash : splashes) {
            boolean headChecked = headMask == null;
            for (LayerEntry entry : topDown) {
                if (!headChecked && entry.slot.zIndex < HEAD_Z) {
                    headChecked = true;
                    if (bodyBlood != null && headMask.solidAtLayerPixel(splash.x, splash.y)) {
                        bodyBlood.add(splash.copy());
                        break;
                    }
                }
                if (landOn(entry, splash, rectW, rectH)) {
                    break;
                }
            }
        }
    }

    /** Adds the splash to this layer's coat if it hits the layer's art. */
    private boolean landOn(LayerEntry entry, BloodStain splash, float rectW, float rectH) {
        AlphaMask mask = entry.texture != null ? masks.get(entry.texture) : null;
        if (mask == null) {
            return false;
        }
        BloodCoat coat = entry.slot == PaperDollSlot.BASE_FATHER ? bodyBlood
                : entry.item != null ? entry.item.getBloodCoat(entry.bloodPart) : null;
        if (coat == null) {
            return false;
        }
        LayerCalibration cal = calibration.get(entry.layerId);
        float lx = splash.x;
        float ly = splash.y;
        float radius = splash.radius;
        if (!cal.isIdentity()) {
            float[] at = LayerPlacement.toLayerPixel(cal, splash.x, splash.y, rectW, rectH, CANVAS_W, CANVAS_H);
            lx = at[0];
            ly = at[1];
            // A piece drawn at twice its size needs half the stain to look the same size.
            radius = splash.radius / Math.max(0.05f, (Math.abs(cal.scaleX) + Math.abs(cal.scaleY)) / 2f);
        }
        if (!mask.solidAtLayerPixel(lx, ly)) {
            return false;
        }
        BloodStain landed = splash.copy();
        landed.x = lx;
        landed.y = ly;
        landed.radius = radius;
        coat.add(landed);
        return true;
    }

    /** The coat this layer shows, or null when it is clean. Never creates one. */
    private BloodCoat coatFor(LayerEntry entry) {
        if (entry.slot == PaperDollSlot.BASE_FATHER) {
            return bodyBlood;
        }
        if (entry.item == null || entry.item.getBloodCoats() == null) {
            return null;
        }
        return entry.item.getBloodCoats().get(entry.bloodPart);
    }

    /** The painted overlay for a coat over one silhouette, repainted only when the coat changed. */
    private Texture bloodTexture(BloodCoat coat, AlphaMask mask, int seed) {
        if (coat == null || mask == null || coat.isEmpty()) {
            return null;
        }
        Map<AlphaMask, BloodTexture> byMask = bloodTextures.get(coat);
        if (byMask == null) {
            byMask = new IdentityHashMap<>();
            bloodTextures.put(coat, byMask);
        }
        BloodTexture cached = byMask.get(mask);
        if (cached == null) {
            cached = new BloodTexture();
            byMask.put(mask, cached);
        }
        if (cached.version != coat.version() || cached.texture == null) {
            int[] rgba = BloodOverlayRasterizer.render(coat, mask, seed);
            Pixmap pixmap = new Pixmap(mask.width, mask.height, Pixmap.Format.RGBA8888);
            for (int i = 0; i < rgba.length; i++) {
                if (rgba[i] != 0) {
                    pixmap.drawPixel(i % mask.width, i / mask.width, rgba[i]);
                }
            }
            if (cached.texture != null) {
                cached.texture.dispose();
            }
            cached.texture = new Texture(pixmap);
            // Nearest: blood pixels stay crisp squares, like the art they sit on.
            cached.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            pixmap.dispose();
            cached.version = coat.version();
        }
        return cached.texture;
    }

    private static final int BODY_BLOOD_SEED = 0x5EED_B0D1;

    /** A stable noise seed per layer, so an item's soak pattern is the same every session. */
    private static int bloodSeed(LayerEntry entry) {
        if (entry.slot == PaperDollSlot.BASE_FATHER) {
            return BODY_BLOOD_SEED;
        }
        String id = entry.layerId != null ? entry.layerId : entry.slot.name();
        return id.hashCode() * 31 + entry.bloodPart.hashCode();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();

        Color color = getColor();
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);

        float x = getX();
        float y = getY();
        float width = getWidth();
        float height = getHeight();

        if (backdropTexture != null) {
            // Same canvas and rectangle as base_father, so it sits exactly behind him.
            batch.draw(backdropTexture, x, y, width, height);
        }

        renderList.clear();
        renderList.addAll(activeLayers.values());
        Collections.sort(renderList, new Comparator<LayerEntry>() {
            @Override
            public int compare(LayerEntry o1, LayerEntry o2) {
                return Integer.compare(o1.slot.zIndex, o2.slot.zIndex);
            }
        });

        boolean headDrawn = headTexture == null;
        for (LayerEntry entry : renderList) {
            if (!headDrawn && entry.slot.zIndex > HEAD_Z) {
                drawHead(batch, x, y, width, height);
                headDrawn = true;
            }
            if (entry.texture == null) {
                continue;
            }

            LayerCalibration cal = calibration.get(entry.layerId);
            Texture blood = bloodTexture(coatFor(entry), masks.get(entry.texture), bloodSeed(entry));

            if (cal.isIdentity()) {
                // Fast path, and the exact behaviour of the pre-calibration renderer.
                batch.draw(entry.texture, x, y, width, height);
                if (blood != null) {
                    batch.draw(blood, x, y, width, height);
                }
                continue;
            }

            LayerPlacement p = LayerPlacement.compute(cal, x, y, width, height, CANVAS_W, CANVAS_H);
            drawPlaced(batch, entry.texture, p);
            if (blood != null) {
                // Same quad as the layer: the overlay is the layer's canvas at lower resolution.
                drawPlaced(batch, blood, p);
            }
        }
        if (!headDrawn) {
            drawHead(batch, x, y, width, height);
        }
    }

    private static void drawPlaced(Batch batch, Texture texture, LayerPlacement p) {
        batch.draw(texture,
                p.x, p.y,
                p.originX, p.originY,
                p.width, p.height,
                1f, 1f,
                p.rotation,
                0, 0,
                texture.getWidth(), texture.getHeight(),
                false, false);
    }

    /** The head, and the skin's blood again over it so a bloodied face stays bloodied. */
    private void drawHead(Batch batch, float x, float y, float width, float height) {
        batch.draw(headTexture, x, y, width, height);
        // Same seed as the base layer's overlay, so the skin's soak pattern continues
        // across the join instead of changing at the collar line.
        Texture blood = bloodTexture(bodyBlood, masks.get(headTexture), BODY_BLOOD_SEED);
        if (blood != null) {
            batch.draw(blood, x, y, width, height);
        }
    }

    @Override
    public void dispose() {
        if (backdropTexture != null) {
            backdropTexture.dispose();
            backdropTexture = null;
        }
        if (headTexture != null) {
            headTexture.dispose();
            headTexture = null;
        }
        if (baseFatherTexture != null) {
            baseFatherTexture.dispose();
            baseFatherTexture = null;
        }
        for (Texture tex : textureCache.values()) {
            if (tex != null) {
                tex.dispose();
            }
        }
        textureCache.clear();
        for (Map<AlphaMask, BloodTexture> byMask : bloodTextures.values()) {
            for (BloodTexture blood : byMask.values()) {
                if (blood.texture != null) {
                    blood.texture.dispose();
                }
            }
        }
        bloodTextures.clear();
        masks.clear();
        activeLayers.clear();
    }
}
