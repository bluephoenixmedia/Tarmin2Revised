package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

/**
 * A single inventory slot widget. Renders its contained item and serves as
 * both a DragAndDrop source (anything with an item) and a target (validated
 * via {@link #accepts}).
 */
public class InventorySlot extends Table {

    public enum SlotCategory {
        EQUIPMENT, BACKPACK, QUICK_SLOT, ALCHEMY_INPUT, ALCHEMY_OUTPUT
    }

    // Package-visible so DragDropHandler and panels can read them without getters.
    final SlotCategory category;
    final int          index;      // position in backpack / quick-slot array; -1 for equipment
    final String       slotName;   // "Head", "Chest", "Ring 2", "Q1", etc. — null = anonymous
    final ItemType     restrictType; // optional strict-type restriction

    private Item     item;
    private final InventorySkin      skin;
    private final ItemDataManager    idm;

    private final Drawable normalBg;
    private final Drawable validBg;
    private final Drawable invalidBg;

    // ── Construction ─────────────────────────────────────────────────

    public InventorySlot(SlotCategory category, int index, String slotName,
                         ItemType restrictType, InventorySkin skin, ItemDataManager idm) {
        this.category     = category;
        this.index        = index;
        this.slotName     = slotName;
        this.restrictType = restrictType;
        this.skin         = skin;
        this.idm          = idm;

        boolean isEquip = (category == SlotCategory.EQUIPMENT || category == SlotCategory.ALCHEMY_OUTPUT);
        normalBg  = isEquip ? skin.getEquipSlotDrawable()  : skin.getNormalSlotDrawable();
        validBg   = skin.getHighlightValidDrawable();
        invalidBg = skin.getHighlightInvalidDrawable();

        setBackground(normalBg);
        setTouchable(Touchable.enabled);

        if (slotName != null) {
            Label.LabelStyle ls = new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_MUTED);
            Label lbl = new Label(slotName, ls);
            lbl.setTouchable(Touchable.disabled);
            lbl.setAlignment(Align.center);
            add(lbl).top().center().expandX().padTop(2).row();
        }
    }

    // ── Public API ────────────────────────────────────────────────────

    public void setItem(Item item) {
        this.item = item;
    }

    public Item getItem() {
        return item;
    }

    public boolean isEmpty() {
        return item == null;
    }

    /** Called by the drag handler to show valid / invalid drop feedback. */
    public void setHighlight(boolean on, boolean valid) {
        setBackground(on ? (valid ? validBg : invalidBg) : normalBg);
    }

    /** Human-readable name used in debug logs. */
    public String getDebugName() {
        if (category == SlotCategory.EQUIPMENT)    return "Equip:" + slotName;
        if (category == SlotCategory.QUICK_SLOT)   return "Quick:" + index;
        if (category == SlotCategory.ALCHEMY_INPUT) return "Alch:" + index;
        return "Pack:" + index;
    }

    // ── Slot-type acceptance ─────────────────────────────────────────

    /**
     * Returns true if this slot can receive the given item.
     * Mirrors the logic from the original InventoryScreen.InventorySlot.accepts().
     */
    public boolean accepts(Item candidate) {
        if (candidate == null) return false;

        switch (category) {
            case BACKPACK:
            case QUICK_SLOT:
            case ALCHEMY_INPUT:
                return true;
            case ALCHEMY_OUTPUT:
                return false;   // output slot is drag-from only
            case EQUIPMENT:
                return acceptsEquipment(candidate);
            default:
                return false;
        }
    }

    private boolean acceptsEquipment(Item it) {
        if (slotName == null) return false;
        switch (slotName) {
            case "R.Hand": return true;
            case "L.Hand":
                if (restrictType == ItemType.SHIELD) return it.isShield();
                return true;
            case "Head": {
                if (it.isHelmet()) return true;
                try {
                    var t = idm.getTemplate(it.getType());
                    return t != null && t.isHelmet;
                } catch (Exception e) { return false; }
            }
            case "Eyes":   return it.getType() == ItemType.EYES;
            case "Neck":   return it.getType() == ItemType.AMULET || it.getType() == ItemType.NECKLACE;
            case "Back":   return it.isCloak();
            case "Chest":  return it.isArmor() && !it.isHelmet() && !it.isShield() && !it.isRing()
                               && !it.isGauntlets() && !it.isBoots() && !it.isLegs()
                               && !it.isArms() && !it.isCloak();
            case "Arms":   return it.isArms();
            case "Hands":  return it.isGauntlets();
            case "Legs":   return it.isLegs();
            case "Feet":   return it.isBoots();
            case "Ring":
            case "Ring 2": return it.isRing();
            default:       return restrictType != null && it.getType() == restrictType;
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha); // draws background + label children
        if (item != null) {
            drawItem(batch, item, parentAlpha);
        }
    }

    private void drawItem(Batch batch, Item it, float alpha) {
        float pad = 7f;
        float x = getX() + pad;
        float y = getY() + pad;
        float w = getWidth()  - pad * 2f;
        float h = getHeight() - pad * 2f;
        if (w <= 0 || h <= 0) return;

        // Prefer atlas texture region, then raw texture, then sprite-data pixels
        if (it.getTextureRegion() != null) {
            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(it.getTextureRegion(), x, y, w, h);
            if (it.isModified()) enchantGlow(batch, x, y, w, h);
            batch.setColor(Color.WHITE);
            return;
        }
        if (it.getTexture() != null) {
            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(it.getTexture(), x, y, w, h);
            if (it.isModified()) enchantGlow(batch, x, y, w, h);
            batch.setColor(Color.WHITE);
            return;
        }

        // Pixel-art sprite fallback
        String[] sprite = it.getSpriteData();
        if (sprite == null) return;
        Color c = it.getColor();
        batch.setColor(c.r, c.g, c.b, alpha);
        float pw = w / 24f;
        float ph = h / 24f;
        Texture wp = skin.getWhitePixel();
        for (int row = 0; row < 24 && row < sprite.length; row++) {
            String line = sprite[row];
            for (int col = 0; col < 24 && col < line.length(); col++) {
                if (line.charAt(col) == '#') {
                    batch.draw(wp, x + col * pw, y + (23 - row) * ph, pw, ph);
                }
            }
        }
        if (it.isModified()) enchantGlow(batch, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    private void enchantGlow(Batch batch, float x, float y, float w, float h) {
        batch.setColor(1f, 0.88f, 0.2f, 0.28f);
        batch.draw(skin.getWhitePixel(), x, y, w, h);
    }
}
