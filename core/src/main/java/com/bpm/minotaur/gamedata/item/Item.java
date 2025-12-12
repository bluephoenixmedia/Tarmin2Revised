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

    public enum ItemType {
        // ... (Keep all existing Enum values) ...
        BOW, CROSSBOW, KNIFE, AXE, DART, SPEAR, PROJECTILE, SCROLL, BOOK,
        SMALL_FIREBALL, LARGE_FIREBALL, SMALL_LIGHTNING, LARGE_LIGHTNING,
        SMALL_SHIELD, LARGE_SHIELD, GAUNTLETS, HAUBERK, BREASTPLATE, HELMET,
        BOOTS, LEGS, ARMS, EYES, CLOAK, AMULET,
        SMALL_RING, LARGE_RING, RING_BLUE, RING_PINK, RING_GREEN, RING_PURPLE,
        POTION_BLUE, POTION_PINK, POTION_GREEN, POTION_GOLD, POTION_SWIRLY, POTION_BUBBLY,
        KEY, QUIVER, WAR_BOOK, SPIRITUAL_BOOK, SPECIAL_BOOK, FLOUR_SACK,
        MONEY_BELT, SMALL_BAG, BOX, MEDIUM_PACK, LARGE_PACK, LARGE_BAG,
        FOOD, COINS, CHALICE, INGOT, NECKLACE, CROWN, TARMIN_TREASURE,
        REGULAR_CHEST, LAMP, UNKNOWN, LADDER,

        // Home Props
        HOME_CHEST, HOME_CRAFTING_BENCH, HOME_SLEEPING_BAG, HOME_FIRE_POT,

        // Debris
        STICK, LEAVES, SMALL_ROCK, LARGE_BOULDER, BROKEN_COLUMN, BROKEN_WALL, VINES,
        PUDDLE_WATER, STRANGE_METAL, BONES, ANCIENT_FOSSIL, RUSTY_WEAPON, RUSTY_ARMOR,
        DIRTY_CLOTH, METAL_SCRAP, BROKEN_GLASS, DEAD_PLANT_MATTER, MUD,
        CHARRED_WOOD, CERAMIC_SHARD, ROTTEN_ROPE, FUNGAL_SPORE, BAT_GUANO, LOOSE_BRICK,
        TWISTED_ROOT, SLIME_RESIDUE, RAT_SKULL, RUSTED_CHAIN, MOSS_CLUMP, FLINT_SHARD,
        HOLLOW_LOG, SPIDER_SILK, BAT_WING, PARCHMENT_SCRAP, BROKEN_HILT, BENT_NAIL,
        STAGNANT_POOL, GLOWING_LICHEN
    }

    // --- Core Item Properties (Dynamic) ---
    private final ItemType type;
    private final Vector2 position;
    private ItemColor itemColor;
    private List<ItemModifier> modifiers = new ArrayList<>();

    // --- Base Properties ---
    private String friendlyName;
    private String description;
    private final String[] spriteData;
    private final Texture texture;
    private final int baseValue;
    private final int warDamage;
    private final int spiritDamage;
    private final int armorDefense;

    // --- Type Flags ---
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
    private final boolean isImpassable; // New Field

    private boolean isLocked;
    private final int range;
    private final Vector2 scale;
    private List<Item> contents = new ArrayList<>();

    private PotionEffectType trueEffect;
    private boolean isIdentified = false;

    private ItemTemplate template;
    private final ItemDataManager dataManager;

    public Item(ItemType type, int x, int y, ItemColor color,
                ItemDataManager dataManager, AssetManager assetManager) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.itemColor = color;

        ItemTemplate template = dataManager.getTemplate(type);

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
        this.isImpassable = template.isImpassable; // Assign from template

        this.dataManager = dataManager;

        if (template.scale != null) {
            this.scale = new Vector2(template.scale.x, template.scale.y);
        } else {
            this.scale = new Vector2(1.0f, 1.0f);
        }

        if (template.texturePath != null && !template.texturePath.isEmpty() && !this.isPotion) {
            this.texture = assetManager.get(template.texturePath, Texture.class);
        } else {
            this.texture = null;
        }

        if (this.isContainer) {
            if (type == ItemType.BOX || type == ItemType.REGULAR_CHEST || type == ItemType.HOME_CHEST) {
                this.isLocked = true;
            } else {
                this.isLocked = false;
            }
        }

        if (this.dataManager != null) {
            this.template = this.dataManager.getTemplate(this.type);
        } else {
            Gdx.app.error("Item", "Item created with a NULL ItemDataManager: " + this.type);
        }
    }

    // ... (Keep existing getters) ...
    public ItemType getType() { return type; }
    public String getTypeName() { return type.name(); }
    public String getFriendlyName() { return friendlyName; }
    public int getBaseValue() { return baseValue; }
    public String getDisplayName() {
        if (isPotion) return this.friendlyName;
        if (!isModified()) return this.friendlyName;
        StringBuilder nameBuilder = new StringBuilder();
        String prefix = null;
        String suffix = null;
        String bonus = null;
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DAMAGE || mod.type == ModifierType.BONUS_DEFENSE) {
                bonus = mod.displayName;
            } else if (mod.displayName.startsWith("of ")) {
                suffix = mod.displayName;
            } else {
                prefix = mod.displayName;
            }
        }
        if (prefix != null) nameBuilder.append(prefix).append(" ");
        nameBuilder.append(this.friendlyName);
        if (bonus != null) nameBuilder.append(" ").append(bonus);
        if (suffix != null) nameBuilder.append(" ").append(suffix);
        return nameBuilder.toString();
    }

    public boolean isWeapon() { return this.isWeapon; }
    public boolean isRanged() { return this.isRanged; }
    public boolean isArmor() { return this.isArmor; }
    public boolean isPotion() { return this.isPotion; }
    public boolean isFood() { return this.isFood; }
    public boolean isTreasure() { return this.isTreasure; }
    public boolean isKey() { return this.isKey; }
    public boolean isUsable() { return this.isUsable; }
    public boolean isContainer() { return this.isContainer; }
    public boolean isRing() { return this.isRing; }
    public boolean isImpassable() { return this.isImpassable; } // New Getter

    public int getWarDamage() {
        int totalDamage = this.warDamage;
        for (ItemModifier mod : modifiers) { if (mod.type == ModifierType.BONUS_DAMAGE) totalDamage += mod.value; }
        return totalDamage;
    }
    public int getSpiritDamage() {
        int totalDamage = this.spiritDamage;
        for (ItemModifier mod : modifiers) { if (mod.type == ModifierType.BONUS_DAMAGE) totalDamage += mod.value; }
        return totalDamage;
    }
    public int getArmorDefense() {
        int totalDefense = this.armorDefense;
        for (ItemModifier mod : modifiers) { if (mod.type == ModifierType.BONUS_DEFENSE) totalDefense += mod.value; }
        return totalDefense;
    }

    public boolean isModified() { return modifiers != null && !modifiers.isEmpty(); }
    public void addModifier(ItemModifier modifier) {
        if (this.modifiers == null) this.modifiers = new ArrayList<>();
        this.modifiers.add(modifier);
    }
    public List<ItemModifier> getModifiers() {
        if (this.modifiers == null) this.modifiers = new ArrayList<>();
        return modifiers;
    }

    @Override
    public Vector2 getPosition() { return position; }
    @Override
    public Color getColor() { return itemColor.getColor(); }
    public ItemColor getItemColor() { return itemColor; }
    public String[] getSpriteData() { return spriteData; }
    public Texture getTexture() { return texture; }
    public Vector2 getScale() { return this.scale; }

    @Override
    public String toString() {
        return "Item{" + "type=" + type + ", name='" + friendlyName + '\'' + '}';
    }

    public ItemCategory getCategory() {
        if (isWeapon) return (spiritDamage > 0) ? ItemCategory.SPIRITUAL_WEAPON : ItemCategory.WAR_WEAPON;
        if (isArmor) return ItemCategory.ARMOR;
        if (isRing) return ItemCategory.RING;
        if (isTreasure || isFood) return ItemCategory.TREASURE;
        if (isContainer) return ItemCategory.CONTAINER;
        if (isUsable || isPotion || isKey) return ItemCategory.USEFUL;
        return ItemCategory.MISC;
    }

    public boolean isLocked() { return this.isLocked; }
    public void unlock() { this.isLocked = false; }
    public boolean unlocks(Item key) { return key != null && key.isKey(); }

    public List<Item> getContents() {
        if (this.contents == null) this.contents = new ArrayList<>();
        return this.contents;
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }

    public void setContents(List<Item> contents) { this.contents = contents; }
    public int getRange() { return this.range; }
    public PotionEffectType getTrueEffect() { return trueEffect; }
    public void setTrueEffect(PotionEffectType trueEffect) { this.trueEffect = trueEffect; }
    public boolean isIdentified() { return isIdentified; }
    public void setIdentified(boolean identified) { isIdentified = identified; }
    public void setName(String name) { this.friendlyName = name; }
    public void setDescription(String description) { this.description = description; }
    public ItemTemplate getTemplate() {
        if (this.template == null && this.dataManager != null) {
            this.template = this.dataManager.getTemplate(this.type);
        }
        return this.template;
    }
}
