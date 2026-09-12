package com.bpm.minotaur.paperdoll;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.item.Item;

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
 * layers using a standardized 1024x1536 master canvas standard.
 *
 * All armor pieces authored at 1024x1536 auto-snap seamlessly onto the father's body
 * with zero coordinate offsets or scale factors.
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
        Texture texture;
        boolean hidesHair;
        boolean hidesBeard;

        LayerEntry(PaperDollSlot slot, Texture texture) {
            this.slot = slot;
            this.texture = texture;
        }
    }

    private Texture baseFatherTexture;
    private final Map<PaperDollSlot, LayerEntry> activeLayers = new HashMap<>();
    private final Map<String, Texture> textureCache = new HashMap<>();
    private final Set<String> missingTextureWarned = new HashSet<>();
    private final List<LayerEntry> renderList = new ArrayList<>();

    public PaperDoll2DWidget() {
        loadBaseFather();
    }

    private void loadBaseFather() {
        FileHandle handle = resolveFile("images/paperdoll/base_father.png");
        if (handle != null && handle.exists()) {
            baseFatherTexture = new Texture(handle);
            baseFatherTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            activeLayers.put(PaperDollSlot.BASE_FATHER, new LayerEntry(PaperDollSlot.BASE_FATHER, baseFatherTexture));
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

        Texture tex = resolveTexture(slot, item);
        if (tex != null) {
            LayerEntry entry = new LayerEntry(slot, tex);
            // Check for flags if item defines them
            if (item.getTemplate() != null) {
                // Helmet flags if present in template
            }
            activeLayers.put(slot, entry);
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
        } else if (item.isTorso()) {
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
            activeLayers.put(PaperDollSlot.BASE_FATHER, new LayerEntry(PaperDollSlot.BASE_FATHER, baseFatherTexture));
        }
    }

    /**
     * Resolves an item's texture from disk cache.
     * Looks up images/paperdoll/<slot>/<item_id>.png
     */
    private Texture resolveTexture(PaperDollSlot slot, Item item) {
        if (slot.folderName == null) return null;

        List<String> candidateNames = getCandidateNames(item);
        for (String candidate : candidateNames) {
            String path = "images/paperdoll/" + slot.folderName + "/" + candidate + ".png";
            if (textureCache.containsKey(path)) {
                return textureCache.get(path);
            }

            FileHandle handle = resolveFile(path);
            if (handle != null && handle.exists()) {
                try {
                    Texture tex = new Texture(handle);
                    tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                    textureCache.put(path, tex);
                    return tex;
                } catch (Exception e) {
                    Gdx.app.error("PaperDoll2DWidget", "Failed loading paperdoll texture: " + path, e);
                }
            }
        }

        String warnKey = slot.folderName + "/" + (candidateNames.isEmpty() ? "unknown" : candidateNames.get(0));
        if (!missingTextureWarned.contains(warnKey)) {
            missingTextureWarned.add(warnKey);
            Gdx.app.debug("PaperDoll2DWidget", "No paperdoll sprite found for slot [" + slot + "]: candidateNames=" + candidateNames);
        }

        return null;
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
            if (entry.texture != null) {
                batch.draw(entry.texture, x, y, width, height);
            }
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
