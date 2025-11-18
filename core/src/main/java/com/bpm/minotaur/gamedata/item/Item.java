package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.Renderable;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import java.util.ArrayList;
import java.util.List;

public class Item implements Renderable {

    /**
     * The ItemType enum is now just a list of type-safe identifiers.
     * All data (stats, names, sprites) has been moved to items.json.
     */
    public enum ItemType {
        // Weapons
        BOW,
        CROSSBOW,
        KNIFE,
        AXE,
        DART,
        SPEAR,
        PROJECTILE,
        SCROLL,
        BOOK,
        SMALL_FIREBALL,
        LARGE_FIREBALL,
        SMALL_LIGHTNING,
        LARGE_LIGHTNING,

        // Armor
        SMALL_SHIELD,
        LARGE_SHIELD,
        GAUNTLETS,
        HAUBERK,
        HELMET,
        BREASTPLATE,

        // Rings
        SMALL_RING,
        LARGE_RING,
        RING_BLUE,
        RING_PINK,
        RING_GREEN,
        RING_PURPLE,

        // Useful
        //  SMALL_POTION,
        //  LARGE_POTION,
        POTION_BLUE,
        POTION_PINK,
        POTION_GREEN,
        POTION_GOLD,
        POTION_SWIRLY,
        POTION_BUBBLY,
        KEY,
        QUIVER,
        WAR_BOOK,
        SPIRITUAL_BOOK,
        SPECIAL_BOOK,
        FLOUR_SACK,

        // Containers
        MONEY_BELT,
        SMALL_BAG,
        BOX,
        MEDIUM_PACK,
        LARGE_PACK,
        LARGE_BAG,

        // Treasure
        FOOD, // Food is now treasure
        COINS,
        CHALICE,
        INGOT,
        NECKLACE,
        CROWN,
        TARMIN_TREASURE,


        REGULAR_CHEST, LAMP, UNKNOWN,
        LADDER
    }

    // --- Core Item Properties (Dynamic) ---
    private final ItemType type;
    private final Vector2 position;
    private ItemColor itemColor; // The dynamic color (from spawn or modifiers)
    private List<ItemModifier> modifiers = new ArrayList<>();

    // --- Base Properties (Copied from Template) ---
    private String friendlyName;
    private String description; // For storing dynamic potion descriptions
    private final String[] spriteData;
    private final Texture texture;
    private final int baseValue;
    private final int warDamage;
    private final int spiritDamage;
    private final int armorDefense;

    // --- Type Flags (Copied from Template) ---
    private final boolean isWeapon;
    private final boolean isRanged;
    private final boolean isArmor;
    private final boolean isPotion;
    private final boolean isFood;
    private final boolean isTreasure;
    private final boolean isKey;
    private final boolean isUsable;
    private final boolean isContainer;
    private final boolean isRing;
    private boolean isLocked;
    private final int range;
    private final Vector2 scale; // <-- ADD THIS
    private List<Item> contents = new ArrayList<>();

    private PotionEffectType trueEffect;
    private boolean isIdentified = false;

    private ItemTemplate template;

    private final ItemDataManager dataManager;


    /**
     * The new data-driven constructor.
     * Creates an item by pulling its base data from the ItemDataManager.
     */
    public Item(ItemType type, int x, int y, ItemColor color,
                ItemDataManager dataManager, AssetManager assetManager) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.itemColor = color; // Use the passed-in (spawned) color

        // --- Get the template for this item type ---
        ItemTemplate template = dataManager.getTemplate(type);

        // --- Copy all base data from the template ---
        this.friendlyName = template.friendlyName;
        this.spriteData = template.spriteData;
        this.baseValue = template.baseValue;
        this.warDamage = template.warDamage;
        this.spiritDamage = template.spiritDamage;
        this.armorDefense = template.armorDefense;
        this.isWeapon = template.isWeapon;
        this.isRanged = template.isRanged;
        this.isArmor = template.isArmor;
        this.isPotion = template.isPotion;
        this.isFood = template.isFood;
        this.isTreasure = template.isTreasure;
        this.isKey = template.isKey;
        this.isUsable = template.isUsable;
        this.isContainer = template.isContainer;
        this.isRing = template.isRing;
        this.range = template.range;
        this.dataManager = dataManager;


        if (template.scale != null) {
            this.scale = new Vector2(template.scale.x, template.scale.y);
        } else {
            // Default scale if not specified in JSON
            this.scale = new Vector2(1.0f, 1.0f);
        }

        // --- Get the PRE-LOADED texture from the AssetManager ---
        if (template.texturePath != null && !template.texturePath.isEmpty() && !this.isPotion) {
            this.texture = assetManager.get(template.texturePath, Texture.class);
        } else {
            this.texture = null; // Or a default "unknown" texture
        }

        if (this.isContainer) {
            this.isLocked = true;
        }

        if (this.dataManager != null) {
            this.template = this.dataManager.getTemplate(this.type);
        } else {
            Gdx.app.error("Item", "Item created with a NULL ItemDataManager: " + this.type);
        }
    }


    // --- Public Getters for Stats and Flags ---

    public ItemType getType() {
        return type;
    }

    public String getTypeName() {
        return type.name();
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public int getBaseValue() {
        return baseValue;
    }

    // --- Dynamic Display Name (Handles Modifiers) ---

    /**
     * Gets the full display name, including prefixes/suffixes from modifiers.
     * e.g., "Fiery Dagger +1 of Brawn"
     *
     * @return The formatted display name.
     */
    public String getDisplayName() {
        // --- NEW POTION LOGIC ---
        // If it's a potion, 'friendlyName' holds the dynamic name ("Blue Potion" or "Potion of Healing")
        // We will set this field using setName() from PotionManager
        if (isPotion) {
            return this.friendlyName;
        }
        if (!isModified()) {
            // Just return the base name
            return this.friendlyName;
        }


        StringBuilder nameBuilder = new StringBuilder();
        String prefix = null;
        String suffix = null;
        String bonus = null;

        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DAMAGE || mod.type == ModifierType.BONUS_DEFENSE) {
                bonus = mod.displayName; // e.g., "+1"
            } else if (mod.displayName.startsWith("of ")) {
                suffix = mod.displayName; // e.g., "of Brawn"
            } else {
                prefix = mod.displayName; // e.g., "Fiery"
            }
        }

        if (prefix != null) {
            nameBuilder.append(prefix).append(" ");
        }

        nameBuilder.append(this.friendlyName);

        if (bonus != null) {
            nameBuilder.append(" ").append(bonus);
        }

        if (suffix != null) {
            nameBuilder.append(" ").append(suffix);
        }

        return nameBuilder.toString();
    }


    // --- Type Flag Getters ---

    public boolean isWeapon() {
        return this.isWeapon;
    }

    public boolean isRanged() {
        return this.isRanged;
    }

    public boolean isArmor() {
        return this.isArmor;
    }

    public boolean isPotion() {
        return this.isPotion;
    }

    public boolean isFood() {
        return this.isFood;
    }

    public boolean isTreasure() {
        return this.isTreasure;
    }

    public boolean isKey() {
        return this.isKey;
    }

    public boolean isUsable() {
        return this.isUsable;
    }

    public boolean isContainer() {
        return this.isContainer;
    }

    public boolean isRing() {
        return this.isRing;
    }


    // --- Combat Stats (with modifier logic) ---

    public int getWarDamage() {
        int totalDamage = this.warDamage;
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DAMAGE) {
                totalDamage += mod.value;
            }
        }
        return totalDamage;
    }

    public int getSpiritDamage() {
        int totalDamage = this.spiritDamage;
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DAMAGE) {
                totalDamage += mod.value;
            }
        }
        return totalDamage;
    }

    public int getArmorDefense() {
        int totalDefense = this.armorDefense;
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DEFENSE) {
                totalDefense += mod.value;
            }
        }
        return totalDefense;
    }


    // --- Modifier Methods ---

    public boolean isModified() {
        return modifiers != null && !modifiers.isEmpty();
    }

    public void addModifier(ItemModifier modifier) {
        if (this.modifiers == null) {
            this.modifiers = new ArrayList<>();
        }
        this.modifiers.add(modifier);
    }

    public List<ItemModifier> getModifiers() {
        if (this.modifiers == null) {
            this.modifiers = new ArrayList<>();
        }
        return modifiers;
    }


    // --- Renderable Interface ---

    @Override
    public Vector2 getPosition() {
        return position;
    }

    @Override
    public Color getColor() {
        return itemColor.getColor();
    }

    public ItemColor getItemColor() {
        return itemColor;
    }

    public String[] getSpriteData() {
        return spriteData;
    }

    public Texture getTexture() {
        return texture;
    }

    // Not used by items, but required by Renderable
    // Added @Override for clarity as it's part of Renderable
    public Vector2 getScale() {
        return this.scale;
    }

    @Override
    public String toString() {
        return "Item{" +
            "type=" + type +
            ", position=" + position +
            ", color=" + itemColor +
            ", name='" + friendlyName + '\'' +
            ", modifiers=" + modifiers +
            '}';
    }

    public ItemCategory getCategory() {
        if (isWeapon) {
            // Simple logic: if it does spiritual damage, it's spiritual
            return (spiritDamage > 0) ? ItemCategory.SPIRITUAL_WEAPON : ItemCategory.WAR_WEAPON;
        }
        if (isArmor) {
            return ItemCategory.ARMOR;
        }
        if (isRing) {
            return ItemCategory.RING;
        }
        if (isTreasure || isFood) {
            return ItemCategory.TREASURE;
        }
        if (isContainer) {
            return ItemCategory.CONTAINER;
        }
        if (isUsable || isPotion || isKey) {
            return ItemCategory.USEFUL;
        }
        return ItemCategory.MISC;
    }

    public boolean isLocked() {
        return this.isLocked;
    }

    public void unlock() {
        this.isLocked = false;
    }

    // Simple key check
    public boolean unlocks(Item key) {
        return key != null && key.isKey();
    }

    public List<Item> getContents() {
        if (this.contents == null) {
            this.contents = new ArrayList<>();
        }
        return this.contents;
    }

    public void setContents(List<Item> contents) {
        this.contents = contents;
    }

    public int getRange() {
        return this.range;
    }

    public PotionEffectType getTrueEffect() {
        return trueEffect;
    }

    public void setTrueEffect(PotionEffectType trueEffect) {
        this.trueEffect = trueEffect;
    }

    public boolean isIdentified() {
        return isIdentified;
    }

    public void setIdentified(boolean identified) {
        isIdentified = identified;
    }

    // Allows PotionManager to change "Blue Potion" to "Potion of Healing"
    public void setName(String name) {
        this.friendlyName = name;
    }

    // Allows PotionManager to update the description
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the data-driven template for this item.
     * The template contains all base data from items.json.
     * @return The ItemTemplate for this item's type.
     */
    public ItemTemplate getTemplate() {
        // This check is for safety, but the template should always be set in the constructor.
        if (this.template == null && this.dataManager != null) {
            Gdx.app.log("Item", "Had to perform late lookup of template for " + this.type);
            this.template = this.dataManager.getTemplate(this.type);
        }
        return this.template;
    }

}
