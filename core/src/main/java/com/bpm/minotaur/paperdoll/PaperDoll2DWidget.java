package com.bpm.minotaur.paperdoll;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.paperdoll.calibration.CalibrationStore;
import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;
import com.bpm.minotaur.paperdoll.calibration.LayerPlacement;
import com.bpm.minotaur.paperdoll.calibration.PaperdollLayerMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * Layers are rendered strictly according to the agreed 10-layer Z-ordering:
 *   1. Back Cloak (Z=10)
 *   2. Base Father Character (Z=20)
 *   3. Legs/Trousers (Z=30)
 *   4. Boots (Z=40)
 *   5. Chest Armor (Z=50)
 *   6. Arms/Pauldrons (Z=60)
 *   7. Gauntlets/Hands (Z=70)
 *   8. Main Hand Weapon (Z=80)
 *   9. Off-Hand Shield (Z=85)
 *  10. Helmet/Headgear (Z=90)
 *  11. Front Cloak Clasp (Z=95)
 */
public class PaperDoll2DWidget extends Widget implements Disposable {

    public enum PaperDollSlot {
        CLOAK_BACK(10, "cloak"),
        BASE_FATHER(20, null),
        LEGS(30, "legs"),
        BOOTS(40, "feet"),
        CHEST(50, "chest"),
        ARMS(60, "arms"),
        HANDS(70, "hands"),
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

        LayerEntry(PaperDollSlot slot, Texture texture, String layerId) {
            this.slot = slot;
            this.texture = texture;
            this.layerId = layerId;
        }
    }

    private static final String CALIBRATION_PATH = "data/paperdoll_calibration.json";
    private static final String LAYER_MAP_PATH = "data/paperdoll_layers.json";

    /** The base portrait is not calibratable; it defines the frame everything else fits. */
    private static final String BASE_LAYER_ID = null;

    private static final int CANVAS_W = 1024;
    private static final int CANVAS_H = 1536;

    private Texture baseFatherTexture;
    private final Map<PaperDollSlot, LayerEntry> activeLayers = new HashMap<>();
    private final Map<String, Texture> textureCache = new HashMap<>();
    private final Set<String> missingTextureWarned = new HashSet<>();
    private final List<LayerEntry> renderList = new ArrayList<>();

    private final CalibrationStore calibration = new CalibrationStore();
    private final PaperdollLayerMap layerMap = new PaperdollLayerMap();

    public PaperDoll2DWidget() {
        loadBaseFather();
        reloadCalibration();
    }

    /** Re-reads calibration and the layer map from disk. */
    public void reloadCalibration() {
        calibration.load(resolveFile(CALIBRATION_PATH));
        layerMap.load(resolveFile(LAYER_MAP_PATH));
    }

    public CalibrationStore getCalibration() {
        return calibration;
    }

    public PaperdollLayerMap getLayerMap() {
        return layerMap;
    }

    private void loadBaseFather() {
        FileHandle handle = resolveFile("images/paperdoll/base_father.png");
        if (handle != null && handle.exists()) {
            baseFatherTexture = new Texture(handle);
            baseFatherTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
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

        if (item == null) {
            activeLayers.remove(slot);
            return;
        }

        PaperdollLayerMap.LayerRef ref = resolveLayerRef(slot, item);
        Texture tex = resolveTexture(slot, item, ref);
        if (tex != null) {
            activeLayers.put(slot, new LayerEntry(slot, tex, ref != null ? ref.layerId() : null));
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
            Texture tex = new Texture(handle);
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            textureCache.put(path, tex);
            return tex;
        } catch (Exception e) {
            Gdx.app.error("PaperDoll2DWidget", "Failed loading paperdoll texture: " + path, e);
            return null;
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

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();

        Color color = getColor();
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);

        float x = getX();
        float y = getY();
        float width = getWidth();
        float height = getHeight();

        renderList.clear();
        renderList.addAll(activeLayers.values());
        Collections.sort(renderList, new Comparator<LayerEntry>() {
            @Override
            public int compare(LayerEntry o1, LayerEntry o2) {
                return Integer.compare(o1.slot.zIndex, o2.slot.zIndex);
            }
        });

        for (LayerEntry entry : renderList) {
            if (entry.texture == null) {
                continue;
            }

            LayerCalibration cal = calibration.get(entry.layerId);

            if (cal.isIdentity()) {
                // Fast path, and the exact behaviour of the pre-calibration renderer.
                batch.draw(entry.texture, x, y, width, height);
                continue;
            }

            LayerPlacement p = LayerPlacement.compute(cal, x, y, width, height, CANVAS_W, CANVAS_H);
            batch.draw(entry.texture,
                    p.x, p.y,
                    p.originX, p.originY,
                    p.width, p.height,
                    1f, 1f,
                    p.rotation,
                    0, 0,
                    entry.texture.getWidth(), entry.texture.getHeight(),
                    false, false);
        }
    }

    @Override
    public void dispose() {
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
        activeLayers.clear();
    }
}
