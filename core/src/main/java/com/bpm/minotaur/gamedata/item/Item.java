package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.Renderable;
import java.util.ArrayList;
import java.util.List;

public class Item implements Renderable {

    public enum Beatitude {
        CURSED, UNCURSED, BLESSED
    }

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
        REGULAR_CHEST, LAMP, UNKNOWN, LADDER, LADDER_UP, WAND,

        // NEW TYPES FOR DICE INTEGRATION
        SWORD, TWO_HANDED_SWORD, SKULL, RING_GOLD,

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
    private final ItemType type;
    private final Vector2 position;
    private ItemColor itemColor;
    private List<ItemModifier> modifiers = new ArrayList<>();

    // --- Base Properties ---
    private String friendlyName;
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
    private final boolean isShield; // New Field
    private final boolean isHelmet; // New Field
    private final boolean isImpassable; // New Field

    // --- NetHack-style Properties ---
    private Beatitude beatitude = Beatitude.UNCURSED; // Default
    private int erosion = 0; // 0 = none, 1 = rusty/burnt, 2 = very rusty/burnt, 3 = corroded/rotted
    private int enchantment = 0; // +/- to hit/damage/AC
    private int charges = 0; // For wands and tools
    private boolean trapped = false; // chests/doors

    // --- Corpse Property ---
    private com.bpm.minotaur.gamedata.monster.Monster.MonsterType corpseSource;

    private boolean isLocked;
    private final int range;
    private final Vector2 scale;
    private List<Item> contents = new ArrayList<>();

    private PotionEffectType trueEffect; // For potions only
    private ScrollEffectType scrollEffect;
    private WandEffectType wandEffect;
    private RingEffectType ringEffect;

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
        this.isShield = template.isShield;
        this.isHelmet = template.isHelmet;
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
            // Formats "MINOTAUR" to "Minotaur Bone"
            String sourceName = this.corpseSource.name().charAt(0) +
                    this.corpseSource.name().substring(1).toLowerCase().replace('_', ' ');
            return sourceName + " Bone";
        }

        if (!isModified())
            return this.friendlyName;
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
        if (prefix != null)
            nameBuilder.append(prefix).append(" ");
        nameBuilder.append(this.friendlyName);
        if (bonus != null)
            nameBuilder.append(" ").append(bonus);
        if (suffix != null)
            nameBuilder.append(" ").append(suffix);
        return nameBuilder.toString();
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

    public boolean isShield() {
        return this.isShield;
    }

    public int getWarDamage() {
        int totalDamage = this.warDamage;
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DAMAGE)
                totalDamage += mod.value;
        }
        return totalDamage;
    }

    public int getSpiritDamage() {
        int totalDamage = this.spiritDamage;
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DAMAGE)
                totalDamage += mod.value;
        }
        return totalDamage;
    }

    public int getArmorDefense() {
        int totalDefense = this.armorDefense;
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DEFENSE)
                totalDefense += mod.value;
        }
        return totalDefense;
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

        if (isWeapon)
            return (spiritDamage > 0) ? ItemCategory.SPIRITUAL_WEAPON : ItemCategory.WAR_WEAPON;
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
}
