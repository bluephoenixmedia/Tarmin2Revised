package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion; // New imports
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.Renderable;
import java.util.ArrayList;
import java.util.List;

public class Item implements Renderable {
    private boolean isSeen = false; // Minimap tracking

    public enum Beatitude {
        CURSED, UNCURSED, BLESSED
    }

    public enum ItemType {
        // ... (Keep all existing Enum values) ...
        BOW, CROSSBOW, KNIFE, AXE, DART, SPEAR, PROJECTILE, SCROLL, BOOK,
        SMALL_FIREBALL, LARGE_FIREBALL, SMALL_LIGHTNING, LARGE_LIGHTNING,
        SMALL_SHIELD, LARGE_SHIELD, GAUNTLETS, HAUBERK, BREASTPLATE, HELMET,
        BOOTS, LEGS, ARMS, EYES, CLOAK, AMULET, RING,
        SMALL_RING, LARGE_RING, RING_BLUE, RING_PINK, RING_GREEN, RING_PURPLE,
        POTION_BLUE, POTION_PINK, POTION_GREEN, POTION_GOLD, POTION_SWIRLY, POTION_BUBBLY,
        KEY, QUIVER, WAR_BOOK, SPIRITUAL_BOOK, SPECIAL_BOOK, FLOUR_SACK,
        MONEY_BELT, SMALL_BAG, BOX, MEDIUM_PACK, LARGE_PACK, LARGE_BAG,
        FOOD, COINS, CHALICE, INGOT, NECKLACE, CROWN, TARMIN_TREASURE,
        REGULAR_CHEST, LAMP, UNKNOWN, LADDER, LADDER_UP, WAND, SHIELD, BACKPACK,

        // NEW TYPES FOR DICE INTEGRATION
        SWORD, TWO_HANDED_SWORD, SKULL, RING_GOLD, RUSTY_SWORD,

        // NEW: Toxic Communion Gibs
        GIB_FLESH, GIB_BONE, GIB_ORGAN, GIB_BILE, GIB_GLAZE,
        // NEW: Alchemy Potions
        POTION_FERAL_DRAUGHT, POTION_TITAN_SLUDGE,

        // NEW WEAPONS (Generated)
        ALHULAK, ANKUS_ELEPHANT_GOAD, ARQUEBUS, ARROW_DAIKYU, ARROW_FLIGHT, ARROW_FORGET, ARROW_GIANT_KIN, ARROW_KENYAN,
        ARROW_MAIL_PIERCER, ARROW_SHEAF, ARROW_SLEEP, ARROW_STONE_FLIGHT, ARROW_WAR, ASSEGAI, AXE_BATTLE, AXE_FOREARM,
        AXE_HAND_THROWING, AXE_TWO_HANDED_BATTLE, BAGH_NAKH_TIGER_CLAWS, BARDS_FRIEND, BASILARD, BELAYING_PIN, BLOWGUN,
        BLOWGUN_BARBED_DART, BLOWGUN_NEEDLE, BLUNDERBUS, BO_STICK, BOLA, BOW_COMPOSITE_LONG, BOW_COMPOSITE_SHORT,
        BOW_ELVEN, BOW_FOLDING_SHORT, BOW_GIANT_KIN_LONG, BOW_KENYAN_LONG, BOW_LONG, BOW_PIXIE, BOW_SHORT,
        BUCKLER_SPIKED, CAHULAKS, CALTROP, CARRIKAL, CAVILER, CESTUS, CHAIN, CHAKRAM, CHATKCHA, CLUB, CLUB_DATCHI,
        CLUB_DWARVEN_WAR, CLUB_GREAT, CLUB_RIM, CROSSBOW_DISK, CROSSBOW_DOUBLED, CROSSBOW_HAND, CROSSBOW_HEAVY,
        CROSSBOW_LIGHT, DAGGER, DAGGER_BONE, DAGGER_CLIMBING, DAGGER_GIANT_KIN, DAGGER_PARRYING, DAGGER_STONE, DAIKYU,
        DART_BARBED, DEJADA, DEJADA_CESTUS, DIRK, DISK_CROSSBOW, DRAGONS_PAW, FLAIL_BLADEBACK, FLAIL_CHAIN,
        FLAIL_FOOTMANS, FLAIL_GRAIN, FLAIL_HORSEMANS, FLINDBAR, GADA, GAFF_HOOK_ATTACHED, GAFF_HOOK_HELD,
        GLADIATORS_FRIEND_FOOTMANS, GLADIATORS_FRIEND_HORSEMANS, GLOVE_NAIL, GOUGE, HARPOON_ONE_HANDED,
        HARPOON_TWO_HANDED, HATCHET, HATCHET_HAWK, HORA, IMPALER, IUAK_SNOW_BLADE, JAMBIYA, JAVELIN_ONE_HANDED,
        JAVELIN_STONE_ONE_HANDED, JAVELIN_STONE_TWO_HANDED, JAVELIN_TWO_HANDED, KATAR_PUNCH_DAGGER, KICK_SLASHER,
        KNIFE_BONE, KNIFE_HARNESS, KNIFE_STONE, KNIFE_THROWING, KNIFE_WIDOWS, KNOBKERRIE, KORA, KUKRI, LANCE_FLIGHT,
        LANCE_HEAVY_HORSE1, LANCE_JOUSTING1, LANCE_LIGHT_HORSE1, LANCE_MEDIUM_HORSE1, LASSO, LOTULIS, MACA,
        MACE_BLADEBACK, MACE_FOOTMANS, MACE_FOOTMANS_WHISTLING, MACE_GIANT_KIN, MACE_GREAT, MACE_HORSEMANS,
        MACE_HORSEMANS_WHISTLING, MACHETE, MADU, MAIN_GAUCHE, MANCATCHER2, MORNING_STAR, MORNING_STAR_DOUBLE_BALL,
        MUSKET, NET, NUNCHAKU, NYEK_PLE_NEN_TOH, PATA1, PESHKABZ, PICK_FOOTMANS, PICK_HORSEMANS, PICK_ICE,
        PIKE_WEIGHTED, PISTOL_STARWHEEL, POLEARM_AWL_PIKE3, POLEARM_BARDICHE, POLEARM_BEC_DE_CORBIN,
        POLEARM_BILL_GUISARME, POLEARM_CRUSHER, POLEARM_FAUCHARD, POLEARM_FAUCHARD_FORK, POLEARM_GIANT_KIN_HALBERD,
        POLEARM_GLAIVE_GUISARME4, POLEARM_GLAIVE4, POLEARM_GUISARME, POLEARM_GUISARME_VOULGE, POLEARM_GYTHKA,
        POLEARM_HALBERD, POLEARM_HOOK_FAUCHARD, POLEARM_LUCERN_HAMMER3, POLEARM_MILITARY_FORK4, POLEARM_NAGINATA3,
        POLEARM_PARTISAN3, POLEARM_QUAD_FAUCHARD, POLEARM_RANSEUR3, POLEARM_SPETUM3, POLEARM_TETSUBO, POLEARM_TRIKAL,
        POLEARM_VOULGE, PUCHIK, PUNCH_CUTTER, QUABONE, QUARREL_HAND, QUARREL_HEAVY, QUARREL_LIGHT, QUARTERSTAFF, RAZOR,
        RITIIK, SAI, SAP, SCOURGE, SCYTHE, SHIELD_SPIKE, SHOKA, SHOTEL, SHURIKEN, SICKLE, SLING, SLING_BULLET,
        SLING_STONE, SPEAR_CASTER, SPEAR_DOUBLE_BLADED, SPEAR_HEAVY_ONE_HANDED, SPEAR_HEAVY_TWO_HANDED,
        SPEAR_HOOK_TAILED, SPEAR_LONG_ONE_HANDED, SPEAR_LONG_TWO_HANDED3, SPEAR_ONE_HANDED, SPEAR_PADDLE,
        SPEAR_STONE_ONE_HANDED, SPEAR_STONE_TWO_HANDED, SPEAR_THROWING, SPEAR_TWO_HANDED3, SPIKE_ELBOW, SPIKE_HEAD,
        SPIKE_KNEE, SPIKES_BODY, STAFF_SLING, STAFF_SLING_STINKPOT, STAFF_HAMANUS, STAFF_HORNHEAD, STICK_GOBLIN,
        STICKS_SINGING, STILETTO, SWORD_BASTARD_ONE_HANDED, SWORD_BASTARD_TWO_HANDED, SWORD_BROAD, SWORD_CLAYMORE,
        SWORD_CUTLASS, SWORD_DRUSUS, SWORD_DWARVEN_CLAYMORE, SWORD_FALCHION, SWORD_FLAMBERGE,
        SWORD_GIANT_KIN_TWO_HANDED, SWORD_GREAT_SCIMITAR, SWORD_HOOK, SWORD_KATANA_ONE_HANDED, SWORD_KATANA_TWO_HANDED,
        SWORD_KHANDAR_ONE_HANDED, SWORD_KHANDAR_TWO_HANDED, SWORD_KHOPESH, SWORD_LONG, SWORD_MANDIBLE, SWORD_MARINERS,
        SWORD_PIERCER, SWORD_PIXIE, SWORD_RAPIER, SWORD_SABRE, SWORD_SCIMITAR, SWORD_SHORT, SWORD_TALWAR,
        SWORD_TWO_HANDED, SWORD_WAKIZASHI, SWORDLET, TALID, TORTOISE_BLADES, TRIDENT_ONE_HANDED, TRIDENT_TWO_HANDED,
        TROMBASH_HELD, TROMBASH_THROWN, TUFENK, WARHAMMER, WHIP, WHIP_CHAIN, WHIP_MASTERS, WRIST_RAZOR, ZAGHNAL,

        // NEW ARMOR (Generated)
        BANDED_MAIL, BRIGANDINE, BACK_AND_BREAST, BASINET, BRONZE_PLATE, CHAIN_HAUBERK, CHAIN_LAMELLAR, CHAIN_MAIL,
        CORD_MAIL, FIELD_PLATE, FULL_PLATE, GALLIC_ARMOR, HALF_PLATE, CAP, CLOSED_FACE, MAIL_COIF, GREAT_HELM,
        LEATHER_HELM, OPEN_FACED, HIDE_ARMOR, HOPLITE_ARMOR, IMPROVED_MAIL, LAMELLAR_SHIRT, LEATHER_ARMOR,
        LIGHT_SCALE, LORICA_HAMATA, LORICA_SEGMENTA, MAIL_AND_PLATE, METAL_LAMELLAR, PADDED_ARMOR, PLATE_MAIL,
        RING_MAIL, SCALE_ARMOR, SHIELD_BODY, BUCKLER, SHIELD_SMALL, SHIELD_MEDIUM, SHIELD_LARGE, SPLINT_MAIL,
        STUDDED_LEATHER, THREE_QUARTER_PLATE, WOOD_BONE_ARMOR,
        // Missing Boot/Armor Types
        LEATHER_BOOTS, WOOD_BONE_BOOTS, STUDDED_LEATHER_BOOTS, CHAINMAIL_BOOTS, FULL_PLATE_BOOTS,
        HALF_PLATE_BOOTS,
        SPLINT_MAIL_BOOTS, SCALE_MAIL_BOOTS, RING_MAIL_BOOTS, PLATE_MAIL_BOOTS, PADDED_BOOTS,
        HOPLITE_BOOTS, HOPLITE_LEGGINGS, HOPLITE_HELMET, HOPLITE_SILVER_BOOTS, HOPLITE_SILVER_ARMS,
        HOPLITE_SILVER_GAUNTLETS,
        HOPLITE_SILVER_HELMET, HOPLITE_SILVER_LEGGINGS,
        HIDE_BOOTS, HIDE_LEGGINGS, HIDE_HELMET, HIDE_ARMS,
        LEATHER_LEGGINGS, LEATHER_ARMS,
        CHAIN_LEGGINGS,
        FIELD_PLATE_BOOTS, FIELD_PLATE_LEGGINGS, FIELD_PLATE_HELMET, FIELD_PLATE_GAUNTLETS,
        SPLINT_MAIL_LEGGINGS, SPLINT_MAIL_GAUNTLETS,
        STUDDED_LEATHER_LEGGINGS, STUDDED_LEATHER_GAUNTLETS, STUDDED_LEATHER_ARMS,
        PADDED_LEGGINGS, PADDED_HELMET, PADDED_ARMS,
        WOOD_BONE_LEGGINGS, WOOD_BONE_HELMET, WOOD_BONE_GAUNTLETS,
        BRONZE_BOOTS, BRONZE_LEGGINGS, BRONZE_HELMET, BRONZE_GAUNTLETS,

        // Missing Armor Types (Synced from armor.json)
        CHAINMAIL_ARMOR, CHAINMAIL_ARMS, CHAINMAIL_GAUNTLETS, CHAINMAIL_HELMET, CHAINMAIL_LEGGINGS,
        CHAIN_HELMET,
        FULL_PLATE_ARMS, FULL_PLATE_GAUNTLETS, FULL_PLATE_HELMET, FULL_PLATE_LEGGINGS,
        GALLIC_ARMS, GALLIC_HELMET, GALLIC_LEGGINGS, GALLIC_SHIELD,
        HALF_PLATE_ARMS, HALF_PLATE_GAUNTLETS, HALF_PLATE_HELMET, HALF_PLATE_LEGGINGS,
        HOPLITE_SHIELD, HOPLITE_SILVER_ARMOR,
        LIGHT_SCALE_ARMS, LIGHT_SCALE_BOOTS, LIGHT_SCALE_GAUNTLETS, LIGHT_SCALE_HELMET, LIGHT_SCALE_LEGGINGS,
        LORICA_HAMATA_ARMS,
        SHIELD_BODY_2,
        SPLINT_MAIL_ARMS, SPLINT_MAIL_HELMET,
        STUDDED_LEATHER_HELMET,
        WOOD_BONE_ARMS,

        // Randomized Items
        SCROLL_A, SCROLL_B, SCROLL_C, SCROLL_D, SCROLL_E, SCROLL_F, SCROLL_G, SCROLL_H,
        WAND_A, WAND_B, WAND_C, WAND_D, WAND_E, WAND_F, WAND_G, WAND_H,

        // Home Props
        HOME_CHEST, HOME_CRAFTING_BENCH, HOME_SLEEPING_BAG, HOME_FIRE_POT,

        // Corpse & Resources
        CORPSE, MEAT, COOKED_MEAT, BONE, CHITIN, TOOTH, CLAW, NAIL, BLOOD_VIAL, ORGAN, LEATHER_SCRAP, MYSTERIOUS_PORTAL,
        MONSTER_EYE,

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
    private ItemType type; // Removed final
    private final Vector2 position;
    private ItemColor itemColor;
    private List<ItemModifier> modifiers = new ArrayList<>();

    // --- Base Properties ---
    private String friendlyName;
    private String[] spriteData; // Removed final
    private Texture texture; // Removed final
    private TextureRegion textureRegion; // New field
    private int baseValue; // Removed final
    private String damageDice; // Removed final
    private int armorClassBonus; // Removed final
    private String description;

    // --- Type Flags ---
    private boolean isWeapon; // Removed final
    private boolean isRanged; // Removed final
    private boolean isArmor; // Removed final
    private boolean isPotion; // Removed final
    private boolean isFood; // Removed final
    private boolean isTreasure; // Removed final
    private boolean isKey; // Removed final
    private boolean isUsable; // Removed final
    private boolean isContainer; // Removed final
    private boolean isRing; // Removed final
    private boolean isShield; // New Field, Removed final
    private boolean isHelmet; // New Field, Removed final
    private boolean isGauntlets; // New Field, Removed final
    private boolean isBoots; // New Field, Removed final
    private boolean isLegs; // New Field, Removed final
    private boolean isTorso; // New Field, Removed final
    private boolean isArms; // New Field, Removed final
    private boolean isCloak; // New Field, Removed final
    private boolean isAmulet; // New Field, Removed final
    private boolean isImpassable; // New Field, Removed final

    // --- NetHack-style Properties ---
    private Beatitude beatitude = Beatitude.UNCURSED; // Default
    private int erosion = 0; // 0 = none, 1 = rusty/burnt, 2 = very rusty/burnt, 3 = corroded/rotted
    private int enchantment = 0; // +/- to hit/damage/AC
    private int charges = 0; // For wands and tools
    private boolean trapped = false; // chests/doors

    // --- Corpse Property ---
    private com.bpm.minotaur.gamedata.monster.Monster.MonsterType corpseSource;

    private boolean isLocked;
    private int range; // Removed final
    private int hydrationValue; // New
    private int nutrition; // New
    private float warmthBonus; // New
    private Vector2 scale; // Removed final

    private float offsetX = 0f;
    private float offsetY = 0f;
    private float rotation = 0f;
    private List<Item> contents = new ArrayList<>();

    private PotionEffectType trueEffect; // For potions only
    private ScrollEffectType scrollEffect;
    private WandEffectType wandEffect;
    private RingEffectType ringEffect;

    private boolean isIdentified = false;

    private ItemTemplate template;
    private final ItemDataManager dataManager;

    public Item() {
        this.position = new Vector2(); // Default position for items created via fromTemplate
        this.spriteData = null; // Will be set later if needed
        this.texture = null; // Will be set later if needed
        this.textureRegion = null; // Will be set later if needed
        this.dataManager = null; // Will be set later if needed
    }

    public float getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(float offsetX) {
        this.offsetX = offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(float offsetY) {
        this.offsetY = offsetY;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public int getHydrationValue() {
        return hydrationValue;
    }

    public int getNutrition() {
        return nutrition;
    }

    public float getWarmthBonus() {
        return warmthBonus;
    }

    public static Item fromTemplate(ItemType type, ItemTemplate template) {
        Item item = new Item();
        item.type = type;
        item.friendlyName = template.friendlyName;
        item.description = template.description;
        item.baseValue = template.baseValue;
        item.damageDice = template.damageDice;
        item.armorClassBonus = template.armorClassBonus;
        item.isWeapon = template.isWeapon;
        item.isRanged = template.isRanged;
        item.isArmor = template.isArmor;
        item.isShield = template.isShield;
        item.isHelmet = template.isHelmet;
        item.isPotion = template.isPotion;
        item.isFood = template.isFood;
        item.isTreasure = template.isTreasure;
        item.isKey = template.isKey;
        item.isUsable = template.isUsable;
        item.isContainer = template.isContainer;
        item.isRing = template.isRing;
        item.isGauntlets = template.isGauntlets;
        item.isLegs = template.isLegs;
        item.isBoots = template.isBoots;
        item.isTorso = template.isTorso;
        item.isArms = template.isArms;
        item.isCloak = template.isCloak;
        item.isAmulet = template.isAmulet;
        item.isImpassable = template.isImpassable;
        item.isLocked = template.locked;
        item.range = template.range;
        item.ringEffect = template.ringEffect;

        item.hydrationValue = template.hydrationValue;
        item.nutrition = template.nutrition;
        item.warmthBonus = template.warmthBonus;

        item.scale = new Vector2(template.scaleX, template.scaleY);
        item.offsetX = template.offsetX;
        item.offsetY = template.offsetY;
        item.rotation = template.rotation;

        // Variants logic...
        item.template = template;
        return item;
    }

    public Item(ItemType type, int x, int y, ItemColor color,
            ItemDataManager dataManager, AssetManager assetManager) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.itemColor = color;

        ItemTemplate template = dataManager.getTemplate(type);

        this.friendlyName = template.friendlyName;
        this.description = template.description; // Initialize new field
        this.spriteData = template.spriteData;
        this.baseValue = template.baseValue;
        this.damageDice = template.damageDice;
        this.armorClassBonus = template.armorClassBonus;
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
        this.isShield = template.isShield;
        this.isHelmet = template.isHelmet;
        this.isGauntlets = template.isGauntlets;
        this.isBoots = template.isBoots;
        this.isLegs = template.isLegs;
        this.isTorso = template.isTorso;
        this.isArms = template.isArms;
        this.isCloak = template.isCloak;
        this.isAmulet = template.isAmulet;
        this.range = template.range;
        this.isImpassable = template.isImpassable; // Assign from template
        this.isLocked = template.locked; // Initialize from template
        this.ringEffect = template.ringEffect; // Initialize from template

        this.dataManager = dataManager;

        if (template.scale != null) {
            this.scale = new Vector2(template.scale.x, template.scale.y);
        } else {
            this.scale = new Vector2(1.0f, 1.0f);
        }

        Texture tempTexture = null;
        TextureRegion tempRegion = null;

        if (template.texturePath != null && !template.texturePath.isEmpty()
                && Gdx.app.getType() != Application.ApplicationType.HeadlessDesktop) {

            // Check for Debris Atlas (Hardcoded for now)
            if (template.texturePath.contains("images/debris") && assetManager.isLoaded("packed/debris.atlas")) {
                TextureAtlas atlas = assetManager.get("packed/debris.atlas");
                String name = template.texturePath.substring(template.texturePath.lastIndexOf('/') + 1);
                if (name.endsWith(".png"))
                    name = name.substring(0, name.length() - 4);

                tempRegion = atlas.findRegion(name);
                if (tempRegion == null) {
                    Gdx.app.error("Item", "Could not find region '" + name + "' in debris.atlas for " + type);
                    // Fallback to loose texture if possible (though probably not loaded)
                    if (assetManager.isLoaded(template.texturePath)) {
                        tempTexture = assetManager.get(template.texturePath, Texture.class);
                    }
                }
            }
            // Check for Items Atlas
            else if (template.texturePath.contains("images/items") && assetManager.isLoaded("packed/items.atlas")) {
                TextureAtlas atlas = assetManager.get("packed/items.atlas");
                String name = template.texturePath.substring(template.texturePath.lastIndexOf('/') + 1);
                if (name.endsWith(".png"))
                    name = name.substring(0, name.length() - 4);

                tempRegion = atlas.findRegion(name);
                if (tempRegion == null) {
                    Gdx.app.error("Item", "Could not find region '" + name + "' in items.atlas for " + type);
                    if (assetManager.isLoaded(template.texturePath)) {
                        tempTexture = assetManager.get(template.texturePath, Texture.class);
                    }
                }
            } else { // Standard Texture Loading
                // NEW: Texture Atlas Support for Armor
                boolean loadedFromAtlas = false;
                if (template.texturePath.contains("images/armor") && assetManager.isLoaded("packed/armor.atlas")) {
                    TextureAtlas atlas = assetManager.get("packed/armor.atlas");
                    String name = template.texturePath.substring(template.texturePath.lastIndexOf('/') + 1);
                    if (name.endsWith(".png"))
                        name = name.substring(0, name.length() - 4);

                    tempRegion = atlas.findRegion(name);
                    if (tempRegion != null) {
                        loadedFromAtlas = true;
                    } else {
                        Gdx.app.error("Item", "Could not find region '" + name + "' in armor.atlas for " + type);
                    }
                }

                // Fallback to standalone texture if not in atlas
                if (!loadedFromAtlas && assetManager.isLoaded(template.texturePath)) {
                    tempTexture = assetManager.get(template.texturePath, Texture.class);
                }
            }
        }

        this.texture = tempTexture;
        this.textureRegion = tempRegion;

        // DEBUG: Check Food Status
        if (this.isFood) {
            Gdx.app.log("Item [DEBUG]", "Created FOOD Item: " + type + " (isFood=true)");
        } else if (type == ItemType.FLOUR_SACK || type == ItemType.FOOD || type == ItemType.MEAT) {
            Gdx.app.log("Item [DEBUG]", "WARNING: Item " + type + " has isFood=FALSE!");
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

    public String getDisplayName() {
        if (isPotion)
            return this.friendlyName;

        // NEW: Dynamic Bone Naming
        if (this.type == ItemType.BONE && this.corpseSource != null) {
            String sourceName = toTitleCase(this.corpseSource.name());
            return sourceName + " Bone";
        }

        // NEW: Dynamic Gib Naming
        if (this.type.name().startsWith("GIB_") && this.corpseSource != null) {
            String sourceName = toTitleCase(this.corpseSource.name());
            String partName = "Flesh";
            if (type == ItemType.GIB_BILE)
                partName = "Bile";
            else if (type == ItemType.GIB_ORGAN)
                partName = "Organ";
            else if (type == ItemType.GIB_GLAZE)
                partName = "Glaze";
            else if (type == ItemType.GIB_BONE)
                partName = "Bone";

            return sourceName + " " + partName;
        }

        if (!isModified())
            return this.friendlyName;
        StringBuilder nameBuilder = new StringBuilder();
        String prefix = null;
        String suffix = null;
        String bonus = null;
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DAMAGE || mod.type == ModifierType.BONUS_AC) {
                bonus = mod.displayName;
            } else if (mod.displayName.startsWith("of ")) {
                suffix = mod.displayName;
            } else {
                prefix = mod.displayName;
            }
        }
        if (prefix != null)
            nameBuilder.append(prefix).append(" ");
        nameBuilder.append(this.friendlyName);
        if (bonus != null)
            nameBuilder.append(" ").append(bonus);
        if (suffix != null)
            nameBuilder.append(" ").append(suffix);
        return nameBuilder.toString();
    }

    // Helper for Title Case
    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.charAt(0) + input.substring(1).toLowerCase().replace('_', ' ');
    }

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

    public boolean isImpassable() {
        return this.isImpassable;
    } // New Getter

    public boolean isHelmet() {
        return this.isHelmet;
    }

    public boolean isGauntlets() {
        return this.isGauntlets;
    }

    public boolean isBoots() {
        return this.isBoots;
    }

    public boolean isLegs() {
        return this.isLegs;
    }

    public boolean isTorso() {
        return this.isTorso;
    }

    public boolean isArms() {
        return this.isArms;
    }

    public boolean isCloak() {
        return this.isCloak;
    }

    public boolean isAmulet() {
        return this.isAmulet;
    }

    public boolean isShield() {
        return this.isShield;
    }

    public String getDamageDice() {
        return this.damageDice;
    }

    public int getArmorClassBonus() {
        int totalBonus = this.armorClassBonus;
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_AC)
                totalBonus += mod.value;
        }
        return totalBonus;
    }

    public boolean isModified() {
        return modifiers != null && !modifiers.isEmpty();
    }

    public void addModifier(ItemModifier modifier) {
        if (this.modifiers == null)
            this.modifiers = new ArrayList<>();
        this.modifiers.add(modifier);
    }

    public List<ItemModifier> getModifiers() {
        if (this.modifiers == null)
            this.modifiers = new ArrayList<>();
        return modifiers;
    }

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

    public TextureRegion getTextureRegion() {
        return textureRegion;
    }

    public Vector2 getScale() {
        return this.scale;
    }

    @Override
    public String toString() {
        return "Item{" + "type=" + type + ", name='" + friendlyName + '\'' + '}';
    }

    public ItemCategory getCategory() {
        // Fix: Ensure Scrolls are always treated as USEFUL (Readable), never as Weapons
        if (type.name().startsWith("SCROLL"))
            return ItemCategory.USEFUL;

        if (isWeapon) {
            // Refactor: With removal of War/Spirit stats, distinction is less relevant.
            // We can treat everything as WAR_WEAPON or differentiate by Type if needed
            // later.
            return ItemCategory.WAR_WEAPON;
        }
        if (isArmor)
            return ItemCategory.ARMOR;
        if (isRing)
            return ItemCategory.RING;
        if (isTreasure)
            return ItemCategory.TREASURE;
        if (isFood)
            return ItemCategory.FOOD;
        if (isContainer)
            return ItemCategory.CONTAINER;
        if (isUsable || isPotion || isKey)
            return ItemCategory.USEFUL;
        return ItemCategory.MISC;
    }

    public boolean isLocked() {
        return this.isLocked;
    }

    public void unlock() {
        this.isLocked = false;
    }

    public boolean unlocks(Item key) {
        return key != null && key.isKey();
    }

    public List<Item> getContents() {
        if (this.contents == null)
            this.contents = new ArrayList<>();
        return this.contents;
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
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

    public ScrollEffectType getScrollEffect() {
        return scrollEffect;
    }

    public void setScrollEffect(ScrollEffectType scrollEffect) {
        this.scrollEffect = scrollEffect;
    }

    public WandEffectType getWandEffect() {
        return wandEffect;
    }

    public void setWandEffect(WandEffectType wandEffect) {
        this.wandEffect = wandEffect;
    }

    public RingEffectType getRingEffect() {
        return ringEffect;
    }

    public void setRingEffect(RingEffectType ringEffect) {
        this.ringEffect = ringEffect;
    }

    public boolean isIdentified() {
        return isIdentified;
    }

    public void setIdentified(boolean identified) {
        isIdentified = identified;
    }

    public void setName(String name) {
        this.friendlyName = name;
    }

    public void setDescription(String description) {
    }

    public ItemTemplate getTemplate() {
        if (this.template == null && this.dataManager != null) {
            this.template = this.dataManager.getTemplate(this.type);
        }
        return this.template;
    }

    public int getErosion() {
        return erosion;
    }

    public void setErosion(int erosion) {
        this.erosion = erosion;
    }

    public void incrementErosion() {
        this.erosion++;
    }

    public int getCharges() {
        return charges;
    }

    public void setCharges(int charges) {
        this.charges = charges;
    }

    public void decrementCharges() {
        this.charges--;
    }

    public void setCorpseSource(com.bpm.minotaur.gamedata.monster.Monster.MonsterType source) {
        this.corpseSource = source;
    }

    public com.bpm.minotaur.gamedata.monster.Monster.MonsterType getCorpseSource() {
        return this.corpseSource;
    }

    // --- NEW: Dice Integration ---
    private com.bpm.minotaur.gamedata.dice.Die grantedDie;

    public void setGrantedDie(com.bpm.minotaur.gamedata.dice.Die die) {
        this.grantedDie = die;
    }

    public com.bpm.minotaur.gamedata.dice.Die getGrantedDie() {
        return this.grantedDie;
    }
    // ----------------------------

    // --- Corpse Intrinsics ---
    private com.bpm.minotaur.gamedata.effects.StatusEffectType grantedIntrinsic;

    public void setGrantedIntrinsic(com.bpm.minotaur.gamedata.effects.StatusEffectType effect) {
        this.grantedIntrinsic = effect;
    }

    public com.bpm.minotaur.gamedata.effects.StatusEffectType getGrantedIntrinsic() {
        return this.grantedIntrinsic;
    }

    public Beatitude getBeatitude() {
        return beatitude;
    }

    public void setBeatitude(Beatitude beatitude) {
        this.beatitude = beatitude;
    }

    public int getEnchantment() {
        return enchantment;
    }

    public void setEnchantment(int enchantment) {
        this.enchantment = enchantment;
    }

    public boolean isTrapped() {
        return trapped;
    }

    public void setTrapped(boolean trapped) {
        this.trapped = trapped;
    }

    public boolean isSeen() {
        return isSeen;
    }

    public void setSeen(boolean seen) {
        this.isSeen = seen;
    }
}
