package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.Renderable;
import com.bpm.minotaur.managers.StatusManager;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;

public class Monster implements Renderable {

    public enum Category {
        BAD,
        NASTY,
        HORRIBLE
    }

    public enum MonsterType {
        // Bad Monsters (Resistant to War / PHYSICAL, vulnerable to SPIRITUAL)
        DWARF(Category.BAD),
        KOBOLD(Category.BAD),
        GOBLIN(Category.BAD),
        TROGLODYTE(Category.BAD),
        HOBGOBLIN(Category.BAD),
        PLAYER_GHOST(Category.BAD),

        // Nasty Monsters (Resistant to SPIRITUAL, vulnerable to War / PHYSICAL)
        GHOUL(Category.NASTY),
        SKELETON(Category.NASTY),
        CLOAKED_SKELETON(Category.NASTY),
        ZOMBIE(Category.NASTY),
        MUMMY(Category.NASTY),
        HARPY(Category.NASTY),
        GARGOYLE(Category.NASTY),
        WERERAT(Category.NASTY),

        // Horrible Monsters (Vulnerable to both War and Spiritual)
        GIANT_ANT(Category.HORRIBLE),
        GIANT_SCORPION(Category.HORRIBLE),
        GIANT_SNAKE(Category.HORRIBLE),
        ALLIGATOR(Category.HORRIBLE),
        DRAGON(Category.HORRIBLE),
        WRAITH(Category.HORRIBLE),
        GIANT(Category.HORRIBLE),
        MINOTAUR(Category.HORRIBLE),
        GHAST(Category.HORRIBLE),
        BEHOLDER(Category.HORRIBLE),
        GELATINOUS_CUBE(Category.HORRIBLE),
        RUST_MONSTER(Category.HORRIBLE),
        LICH(Category.HORRIBLE),
        MIMIC(Category.HORRIBLE),
        MIND_FLAYER(Category.HORRIBLE),
        OWLBEAR(Category.HORRIBLE),
        DISPLACER_BEAST(Category.HORRIBLE),
        UMBER_HULK(Category.HORRIBLE),
        SPIDER(Category.HORRIBLE),
        ORC(Category.HORRIBLE),
        WEREWOLF(Category.HORRIBLE),
        TROLL(Category.HORRIBLE),
        OGRE(Category.HORRIBLE),
        BASILISK(Category.HORRIBLE),
        MEDUSA(Category.HORRIBLE),
        WYVERN(Category.HORRIBLE),
        CHIMERA(Category.HORRIBLE),
        HYDRA(Category.HORRIBLE),
        VAMPIRE(Category.HORRIBLE),
        IRON_GOLEM(Category.HORRIBLE),
        PURPLE_WORM(Category.HORRIBLE),
        AGIS(Category.HORRIBLE),
        DEMON_SLIME(Category.HORRIBLE),
        BRINGER_OF_DEATH(Category.HORRIBLE),
        FALL_ANGEL(Category.HORRIBLE);

        private final Category category;

        MonsterType(Category category) {
            this.category = category;
        }

        MonsterType() {
            this(Category.HORRIBLE);
        }

        public Category getCategory() {
            return category;
        }
    }

    private final MonsterType type;
    private final Vector2 position;
    private final MonsterColor monsterColor;
    private int currentHP;
    private int maxHP;
    private int currentMP;
    private int maxMP;
    private String damageDice;
    private int armorClass;
    private int magicResistance; // 0-100%
    private int moveSpeed;
    private int baseExperience;
    private int level = 1;

    private final String[] spriteData;
    private Texture texture = null;
    private Texture texNorth = null;
    private Texture texEast = null;
    private Texture texWest = null;
    private Texture[] southFrames = null;
    private float frameDuration = 0.15f;
    private float animTimer = 0f;
    private int currentFrame = 0;
    
    private boolean isSpriteSheet = false;
    private TextureRegion[] animationFrames = null;
    private int animStartFrame = 0;
    private int animEndFrame = 0;

    public Vector2 scale;
    private MonsterFamily family;

    private int intelligence;

    private int dexterity;
    private boolean hasRangedAttack;
    private int attackRange;
    private String rangedProjectile = "ARROW";
    private String rangedDamageDice = null;
    private DamageType rangedDamageType = null;
    private String rangedEffect = null;
    private float rangedEffectChance = 0.5f;
    private float rangedPreferredDistance = 4f;
    private int rangedShotCooldown;

    private long lastRangedTelegraphTimeMillis = -1L;
    private Color rangedTelegraphColor = null;
    private float rangedTelegraphDurationSec = 0.35f;

    private final java.util.List<com.bpm.minotaur.gamedata.gore.WoundDecal> woundDecals = new java.util.ArrayList<>();
    private Texture bakedWoundTexture = null;

    public java.util.List<com.bpm.minotaur.gamedata.gore.WoundDecal> getWoundDecals() {
        return woundDecals;
    }

    public void addWoundDecal(com.bpm.minotaur.gamedata.gore.WoundDecal decal) {
        if (decal != null) {
            if (woundDecals.size() >= 12) {
                woundDecals.remove(0);
            }
            woundDecals.add(decal);
        }
    }

    public Texture getBakedWoundTexture() {
        return bakedWoundTexture;
    }

    public void setBakedWoundTexture(Texture tex) {
        if (this.bakedWoundTexture != null && this.bakedWoundTexture != tex) {
            this.bakedWoundTexture.dispose();
        }
        this.bakedWoundTexture = tex;
    }

    public void disposeBakedTexture() {
        if (bakedWoundTexture != null) {
            bakedWoundTexture.dispose();
            bakedWoundTexture = null;
        }
    }

    // --- AI Fields ---
    private MonsterTemplate.AiType aiType;
    private float healThreshold;
    private int spellChance;

    // --- Spellcasting Fields ---
    private boolean spellcaster = false;
    private MonsterSpellbook spellbook = null;
    private long lastSpellFlashTimeMillis = -1L;
    private Color spellFlashColor = null;
    private float spellFlashDurationSec = 0.4f;

    // --- Door & Ladder Capabilities ---
    private Boolean canOpenDoors = null;
    private Boolean canClimbLadders = null;
    private int searchTurnsRemaining = 0;

    // --- Refactored Instance Data ---
    private com.bpm.minotaur.gamedata.Inventory inventory;
    private int tameness = 0; // 0 = Hostile
    private boolean invisible = false;
    private boolean hidden = false; // e.g., underwater, buried

    private final StatusManager statusManager;
    private final MonsterDataManager dataManager; // Stored reference for template access
    private MonsterTemplate testTemplate;

    // --- State Machine Fields ---
    public enum MonsterState {
        IDLE,
        WANDERING, // Moves randomly occasionally
        HUNTING // Actively pathfinding to target
    }

    private MonsterState state = MonsterState.IDLE;

    private Faction faction = Faction.BEASTS_AND_VERMIN;
    private Monster targetMonster = null;
    private int retaliationTurnsRemaining = 0;

    // Grace flag: true for the first turn after spawn so the auto-adjacent attack
    // cannot one-shot a monster before it has been rendered even once.
    private boolean justSpawned = true;
    private com.badlogic.gdx.math.GridPoint2 lastKnownTargetPos = null;
    private int turnsSinceLastSeen = 0;
    private boolean isTagged = false; // Persistent minimap tracking
    private boolean themeChampion = false; // Designated champion of a themed chunk
    private boolean invulnerable = false; // Objective-critical NPC; damage is discarded

    // Public constructor for unit testing combat mechanics without LibGDX asset loaders
    public Monster(MonsterType type, int hp, int ac) {
        this(type, hp, ac, 0f, 0f);
    }

    public Monster(MonsterType type, int hp, int ac, float startX, float startY) {
        this.type = type;
        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        // The data-driven constructor takes scale from the template; this one has
        // no template, and a null scale is a null dereference the moment anything
        // renders the monster in retro mode.
        this.scale = new Vector2(1f, 1f);
        this.monsterColor = MonsterColor.WHITE;
        this.dataManager = null;
        this.maxHP = hp;
        this.currentHP = hp;
        this.maxMP = 50;
        this.currentMP = 50;
        this.armorClass = ac;
        this.damageDice = "1d4";
        this.spriteData = new String[0];
        this.statusManager = new StatusManager();
        this.family = MonsterFamily.NONE;
        this.faction = Faction.getDefaultFaction(type != null ? type.name() : "", this.family);
    }

    public Monster(MonsterType type, float startX, float startY, MonsterColor color,
            MonsterDataManager dataManager, AssetManager assetManager) {
        this.type = type;
        this.position = new Vector2(startX + 0.5f, startY + 0.5f); // Center
        this.monsterColor = color;
        this.dataManager = dataManager;

        MonsterTemplate template = dataManager.getTemplate(type);

        this.armorClass = template.armorClass;
        this.magicResistance = template.magicResistance;

        this.maxHP = template.maxHP;
        this.currentHP = this.maxHP;

        this.maxMP = template.maxMP;
        this.currentMP = this.maxMP;
        this.damageDice = template.damageDice != null ? template.damageDice : "1d4";

        this.baseExperience = template.baseExperience;
        this.level = template.baseLevel;
        this.moveSpeed = template.moveSpeed; // Init speed

        this.family = template.family;
        this.faction = Faction.getDefaultFaction(type != null ? type.name() : "", this.family);
        this.spriteData = template.spriteData;
        this.scale = new Vector2(template.scale.x, template.scale.y);
        this.statusManager = new StatusManager();
        if (template.texturePath != null && !template.texturePath.isEmpty()) {
            if (assetManager.isLoaded(template.texturePath, Texture.class)) {
                this.texture = assetManager.get(template.texturePath, Texture.class);
                if (template.isSpriteSheet) {
                    this.isSpriteSheet = true;
                    this.frameDuration = template.spriteFrameDuration;
                    int frameWidth = this.texture.getWidth() / template.spriteCols;
                    int frameHeight = this.texture.getHeight() / template.spriteRows;
                    TextureRegion[][] tmp = TextureRegion.split(this.texture, frameWidth, frameHeight);
                    this.animationFrames = new TextureRegion[template.spriteCols * template.spriteRows];
                    int index = 0;
                    for (int r = 0; r < template.spriteRows; r++) {
                        for (int c = 0; c < template.spriteCols; c++) {
                            this.animationFrames[index++] = tmp[r][c];
                        }
                    }
                    this.animStartFrame = Math.max(0, template.animStartFrame);
                    this.animEndFrame = (template.animEndFrame >= 0)
                            ? Math.min(template.animEndFrame, this.animationFrames.length - 1)
                            : this.animationFrames.length - 1;
                    this.currentFrame = this.animStartFrame;
                }
            } else {
                com.badlogic.gdx.Gdx.app.error("Monster",
                        "CRITICAL: Texture not loaded for " + type + "! Path: " + template.texturePath);
            }
        }

        if (template.directionTextures != null) {
            MonsterTemplate.DirectionTextures dt = template.directionTextures;
            this.texNorth = loadTex(assetManager, dt.north);
            this.texEast  = loadTex(assetManager, dt.east);
            this.texWest  = loadTex(assetManager, dt.west);
            this.frameDuration = dt.frameDuration;
            if (dt.southFrames != null && dt.southFrames.length > 0) {
                this.southFrames = new Texture[dt.southFrames.length];
                for (int i = 0; i < dt.southFrames.length; i++) {
                    this.southFrames[i] = loadTex(assetManager, dt.southFrames[i]);
                }
            }
        }

        this.intelligence = template.intelligence;
        this.dexterity = template.dexterity;
        this.hasRangedAttack = template.hasRangedAttack;
        this.attackRange = template.attackRange;
        this.rangedProjectile = template.rangedProjectile != null ? template.rangedProjectile : "ARROW";
        this.rangedDamageDice = template.rangedDamageDice;
        this.rangedDamageType = template.rangedDamageType;
        this.rangedEffect = template.rangedEffect;
        this.rangedEffectChance = template.rangedEffectChance;
        this.rangedPreferredDistance = template.rangedPreferredDistance > 0 ? template.rangedPreferredDistance : 4f;

        // Apply color-tier HP multiplier (elite/rare variants are proportionally tougher).
        float colorMult = color.getStrengthMultiplier();
        if (colorMult != 1.0f) {
            this.maxHP = Math.max(1, (int) (this.maxHP * colorMult));
            this.currentHP = this.maxHP;
        }

        // --- Tarmin's Hunger Scaling (The Meat Grinder) ---
        float scaling = com.bpm.minotaur.managers.DoomManager.getInstance().getEnemyScalingMultiplier(template.baseLevel);
        if (scaling > 1.0f) {
            this.maxHP = (int) (this.maxHP * scaling);
            this.currentHP = this.maxHP;
            com.badlogic.gdx.Gdx.app.log("Monster", "Spawned " + type + " with Doom Scaling: x"
                    + String.format("%.2f", scaling) + " HP: " + this.maxHP);
        }
        // --------------------------------------------------

        // --- AI Initialization ---
        this.aiType = template.aiType != null ? template.aiType : MonsterTemplate.AiType.AGGRESSIVE;
        this.healThreshold = template.healThreshold;
        this.spellChance = template.spellChance;

        if (template.isSpellcaster != null) {
            this.spellcaster = template.isSpellcaster;
        } else {
            this.spellcaster = (this.intelligence >= 12);
        }

        if (this.spellcaster) {
            this.maxMP = Math.max(this.maxMP, 20 + this.intelligence * 2);
            this.currentMP = this.maxMP;
            this.spellbook = MonsterSpellbook.createForMonster(
                    this.intelligence,
                    this.level,
                    template.spellSchools,
                    template.innateSpells,
                    com.bpm.minotaur.gamedata.spells.SpellDataManager.getInstance());
        }

        if (template.canOpenDoors != null) {
            this.canOpenDoors = template.canOpenDoors;
        }
        if (template.canClimbLadders != null) {
            this.canClimbLadders = template.canClimbLadders;
        }

        this.inventory = new com.bpm.minotaur.gamedata.Inventory();
    }

    // --- Spellcasting Methods ---
    public boolean isSpellcaster() {
        return spellcaster;
    }

    public void setSpellcaster(boolean spellcaster) {
        this.spellcaster = spellcaster;
    }

    public MonsterSpellbook getSpellbook() {
        return spellbook;
    }

    public void setSpellbook(MonsterSpellbook spellbook) {
        this.spellbook = spellbook;
    }

    public void deductMana(int amount) {
        this.currentMP = Math.max(0, this.currentMP - amount);
    }

    public boolean hasEnoughMana(int amount) {
        return this.currentMP >= amount;
    }

    public int heal(int amount) {
        int oldHP = currentHP;
        currentHP = Math.min(maxHP, currentHP + amount);
        return currentHP - oldHP;
    }

    public void triggerSpellFlash(Color color, float durationSec) {
        this.spellFlashColor = color != null ? color.cpy() : Color.WHITE;
        this.spellFlashDurationSec = durationSec > 0 ? durationSec : 0.4f;
        this.lastSpellFlashTimeMillis = System.currentTimeMillis();
    }

    public float getSpellFlashProgress() {
        if (lastSpellFlashTimeMillis < 0) return 1f;
        float elapsed = (System.currentTimeMillis() - lastSpellFlashTimeMillis) / 1000f;
        return Math.min(1f, elapsed / spellFlashDurationSec);
    }

    public Color getSpellFlashColor() {
        return spellFlashColor;
    }

    public void triggerRangedAttackTelegraph(Color color, float durationSec) {
        this.rangedTelegraphColor = color != null ? color.cpy() : Color.WHITE;
        this.rangedTelegraphDurationSec = durationSec > 0 ? durationSec : 0.35f;
        this.lastRangedTelegraphTimeMillis = System.currentTimeMillis();
    }

    public float getRangedAttackTelegraphProgress() {
        if (lastRangedTelegraphTimeMillis < 0) return 1f;
        float elapsed = (System.currentTimeMillis() - lastRangedTelegraphTimeMillis) / 1000f;
        return Math.min(1f, elapsed / rangedTelegraphDurationSec);
    }

    public Color getRangedAttackTelegraphColor() {
        return rangedTelegraphColor;
    }

    public boolean isRangedTelegraphing() {
        return getRangedAttackTelegraphProgress() < 1f;
    }

    // --- AI Getters ---
    public MonsterTemplate.AiType getAiType() {
        return aiType;
    }

    public float getHealThreshold() {
        return healThreshold;
    }

    public int getSpellChance() {
        return spellChance;
    }

    public int getMaxHP() {
        return maxHP;
    }

    public enum Affinity {
        RESISTANT,
        WEAK,
        NEUTRAL
    }

    public static Affinity getAffinity(MonsterType type, DamageType damageType) {
        if (type == null || damageType == null) return Affinity.NEUTRAL;
        Category cat = type.getCategory();
        if (cat == Category.BAD) {
            if (damageType == DamageType.PHYSICAL) return Affinity.RESISTANT;
            if (damageType == DamageType.SPIRITUAL) return Affinity.WEAK;
        } else if (cat == Category.NASTY) {
            if (damageType == DamageType.SPIRITUAL) return Affinity.RESISTANT;
            if (damageType == DamageType.PHYSICAL) return Affinity.WEAK;
        }
        return Affinity.NEUTRAL;
    }

    public Affinity getAffinity(DamageType damageType) {
        return getAffinity(this.type, damageType);
    }

    public static boolean isImmuneToType(MonsterType type, DamageType damageType) {
        // Soft resistance replaces hard immunity. Retained for backwards compatibility.
        return false;
    }

    public boolean isImmuneToType(DamageType damageType) {
        return isImmuneToType(this.type, damageType);
    }

    public int takeDamage(int amount, DamageType damageType, boolean isCrit) {
        if (isImmuneToType(damageType)) {
            return 0;
        }
        Affinity affinity = getAffinity(damageType);
        int modifiedAmount = amount;
        if (isCrit) {
            // Critical strikes pierce category resistance completely
            if (affinity == Affinity.WEAK) {
                modifiedAmount = (int) (amount * 1.5f);
            }
        } else {
            if (affinity == Affinity.RESISTANT) {
                modifiedAmount = Math.max(1, amount / 2);
            } else if (affinity == Affinity.WEAK) {
                modifiedAmount = (int) (amount * 1.5f);
            }
        }
        return takeDamage(modifiedAmount, isCrit);
    }

    public int takeDamage(int amount, DamageType damageType) {
        return takeDamage(amount, damageType, false);
    }

    public int takeDamage(int amount, boolean isCrit) {
        // Objective-critical NPCs shrug off everything. A themed chunk seals its
        // gates, so letting a stray arrow kill the Gravedigger would strand the
        // player behind an objective that can no longer be completed.
        if (invulnerable) {
            lastHitTimeMillis = System.currentTimeMillis();
            return 0;
        }
        // AC >= 14 provides partial damage soak (every 2 AC above 14 = -1 damage).
        // Creatures with AC 10-13 rely on evasion (to-hit d20 gate) with 0 flat soak.
        // Critical strikes bypass armor soak completely.
        int reduction = isCrit ? 0 : Math.max(0, (armorClass - 14) / 2);
        int taken = Math.max(1, amount - reduction);
        this.currentHP -= taken;
        if (this.currentHP < 0) {
            this.currentHP = 0;
        }
        lastHitTimeMillis = System.currentTimeMillis();
        return taken;
    }

    // --- Hit Recoil / Flash Feedback ---
    private long lastHitTimeMillis = -1L;
    private static final float HIT_FLASH_DURATION_SEC = 0.12f;
    private static final float HIT_RECOIL_DISTANCE = 0.18f;

    /** 0.0 = just hit, 1.0 = flash fully faded (or never hit). */
    public float getHitFlashProgress() {
        if (lastHitTimeMillis < 0) return 1f;
        float elapsed = (System.currentTimeMillis() - lastHitTimeMillis) / 1000f;
        return Math.min(1f, elapsed / HIT_FLASH_DURATION_SEC);
    }

    /** Spring-back displacement magnitude for the current moment, easing to 0. */
    public float getHitRecoilOffset() {
        float progress = getHitFlashProgress();
        if (progress >= 1f) return 0f;
        return HIT_RECOIL_DISTANCE * (1f - progress);
    }

    public int takeDamage(int amount) {
        return takeDamage(amount, false);
    }

    public String[] getSpriteData() {
        return spriteData;
    }

    private static Texture loadTex(AssetManager assetManager, String path) {
        if (path == null || path.isEmpty()) return null;
        if (assetManager.isLoaded(path, Texture.class)) return assetManager.get(path, Texture.class);
        com.badlogic.gdx.Gdx.app.error("Monster", "Directional texture not loaded: " + path);
        return null;
    }

    public void updateAnimation(float delta) {
        if (isSpriteSheet && animationFrames != null && animationFrames.length > 0) {
            animTimer += delta;
            if (animTimer >= frameDuration) {
                animTimer -= frameDuration;
                currentFrame++;
                if (currentFrame > animEndFrame) currentFrame = animStartFrame;
            }
            return;
        }

        if (southFrames == null || southFrames.length <= 1) return;
        animTimer += delta;
        if (animTimer >= frameDuration) {
            animTimer -= frameDuration;
            currentFrame = (currentFrame + 1) % southFrames.length;
        }
    }

    /**
     * Returns the texture to display based on the angle from this monster to the player.
     * angle = atan2(player.y - monster.y, player.x - monster.x)
     */
    public Texture getTextureForPlayerAngle(float angle) {
        // E: (-π/4, π/4), N: (π/4, 3π/4), W: outside those, S: (-3π/4, -π/4)
        if (angle > -Math.PI / 4 && angle <= Math.PI / 4) {
            return texEast != null ? texEast : texture;
        } else if (angle > Math.PI / 4 && angle <= 3 * Math.PI / 4) {
            return texNorth != null ? texNorth : texture;
        } else if (angle > -3 * Math.PI / 4 && angle <= -Math.PI / 4) {
            if (southFrames != null && southFrames.length > 0 && southFrames[currentFrame] != null)
                return southFrames[currentFrame];
            return texture;
        } else {
            return texWest != null ? texWest : texture;
        }
    }

    public boolean hasDirectionalTextures() {
        return texNorth != null || texEast != null || texWest != null || southFrames != null;
    }

    public Texture getTexture() {
        return texture;
    }

    public TextureRegion getTextureRegion() {
        if (isSpriteSheet && animationFrames != null && animationFrames.length > 0) {
            int f = Math.min(Math.max(currentFrame, animStartFrame), animEndFrame);
            return animationFrames[f];
        }
        return null;
    }

    public Vector2 getScale() {
        return scale;
    }

    public void scaleStats(int level) {
        if (level <= 1)
            return;
        float multiplier = 1.0f + ((level - 1) * 0.15f);
        // float armorMultiplier = 1.0f + ((level - 1) * 0.05f); // AC shouldn't scale
        // linearly like armor DR
        this.maxHP = (int) (this.maxHP * multiplier);
        this.currentHP = this.maxHP;

        // Maybe improve AC slightly?
        this.armorClass += (level / 5); // +1 AC every 5 levels

        this.baseExperience = (int) (this.baseExperience * (1.0f + ((level - 1) * 0.25f)));
    }

    public MonsterType getType() {
        return type;
    }

    public String getMonsterType() {
        return type.name();
    }

    @Override
    public Vector2 getPosition() {
        return position;
    }

    public int getCurrentHP() {
        return currentHP;
    }

    // Deprecated alias for compatibility if needed, but better to remove
    public int getWarStrength() {
        return currentHP;
    }

    public int getSpiritualStrength() {
        return 0;
    } // Removed

    public int getBaseExperience() {
        return baseExperience;
    }

    public void setCurrentHP(int hp) {
        this.currentHP = Math.max(0, Math.min(maxHP, hp));
    }

    public int getCurrentMP() {
        return currentMP;
    }

    public void setCurrentMP(int mp) {
        this.currentMP = Math.max(0, Math.min(maxMP, mp));
    }

    public int getMaxMP() {
        return maxMP;
    }

    public String getDamageDice() {
        return damageDice;
    }

    public int getLevel() {
        if (level > 1) return level;
        MonsterTemplate t = getTemplate();
        return t != null ? t.baseLevel : level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    // Deprecated: Compatibility
    public int getMaxWarStrength() {
        return maxHP;
    }

    @Override
    public Color getColor() {
        return monsterColor.getColor();
    }

    public MonsterColor getMonsterColor() {
        return monsterColor;
    }

    public MonsterFamily getFamily() {
        return family;
    }

    public void setFamily(MonsterFamily family) {
        this.family = family;
    }

    public int getIntelligence() {
        return intelligence;
    }

    public StatusManager getStatusManager() {
        return statusManager;
    }

    public int getDexterity() {
        return dexterity;
    }

    public boolean hasRangedAttack() {
        if (hasRangedAttack) return true;
        MonsterTemplate t = getTemplate();
        return t != null && t.hasRangedAttack;
    }

    public int getAttackRange() {
        if (attackRange > 0) return attackRange;
        MonsterTemplate t = getTemplate();
        return (t != null && t.attackRange > 0) ? t.attackRange : 6;
    }

    public String getRangedProjectile() {
        if (rangedProjectile != null) return rangedProjectile;
        MonsterTemplate t = getTemplate();
        return (t != null && t.rangedProjectile != null) ? t.rangedProjectile : "ARROW";
    }

    public void setRangedProjectile(String rangedProjectile) {
        this.rangedProjectile = rangedProjectile;
    }

    public String getRangedDamageDice() {
        if (rangedDamageDice != null) return rangedDamageDice;
        MonsterTemplate t = getTemplate();
        if (t != null && t.rangedDamageDice != null) return t.rangedDamageDice;
        return getDamageDice();
    }

    public void setRangedDamageDice(String rangedDamageDice) {
        this.rangedDamageDice = rangedDamageDice;
    }

    public DamageType getRangedDamageType() {
        if (rangedDamageType != null) return rangedDamageType;
        MonsterTemplate t = getTemplate();
        if (t != null && t.rangedDamageType != null) return t.rangedDamageType;
        return (t != null && t.damageType != null) ? t.damageType : DamageType.PHYSICAL;
    }

    public void setRangedDamageType(DamageType rangedDamageType) {
        this.rangedDamageType = rangedDamageType;
    }

    public String getRangedEffect() {
        if (rangedEffect != null) return rangedEffect;
        MonsterTemplate t = getTemplate();
        return t != null ? t.rangedEffect : null;
    }

    public void setRangedEffect(String rangedEffect) {
        this.rangedEffect = rangedEffect;
    }

    public float getRangedEffectChance() {
        if (rangedEffectChance > 0) return rangedEffectChance;
        MonsterTemplate t = getTemplate();
        return t != null ? t.rangedEffectChance : 0.5f;
    }

    public void setRangedEffectChance(float rangedEffectChance) {
        this.rangedEffectChance = rangedEffectChance;
    }

    /** Turns left before this monster may shoot again; spent walking toward the player. */
    public int getRangedShotCooldown() {
        return rangedShotCooldown;
    }

    public void setRangedShotCooldown(int turns) {
        this.rangedShotCooldown = Math.max(0, turns);
    }

    public void tickRangedShotCooldown() {
        if (rangedShotCooldown > 0) {
            rangedShotCooldown--;
        }
    }

    public float getRangedPreferredDistance() {
        if (rangedPreferredDistance > 0) return rangedPreferredDistance;
        MonsterTemplate t = getTemplate();
        return (t != null && t.rangedPreferredDistance > 0) ? t.rangedPreferredDistance : 4f;
    }

    public void setRangedPreferredDistance(float rangedPreferredDistance) {
        this.rangedPreferredDistance = rangedPreferredDistance;
    }

    public int getArmorClass() {
        return armorClass;
    }

    public boolean isJustSpawned() {
        return justSpawned;
    }

    public void clearJustSpawned() {
        this.justSpawned = false;
    }

    public com.bpm.minotaur.gamedata.Inventory getInventory() {
        if (inventory == null) {
            inventory = new com.bpm.minotaur.gamedata.Inventory();
        }
        return inventory;
    }

    public int getTameness() {
        return tameness;
    }

    public void setTameness(int tameness) {
        this.tameness = tameness;
    }

    public boolean isInvisible() {
        return invisible;
    }

    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getMagicResistance() {
        return magicResistance;
    }

    public MonsterTemplate getTemplate() {
        if (testTemplate != null)
            return testTemplate;
        if (dataManager != null)
            return dataManager.getTemplate(type);
        return null;
    }

    public void setTemplate(MonsterTemplate template) {
        this.testTemplate = template;
        if (template != null) {
            this.level = template.baseLevel;
            this.family = template.family;
            this.maxHP = template.maxHP;
            this.currentHP = template.maxHP;
            this.armorClass = template.armorClass;
            this.damageDice = template.damageDice;
            this.hasRangedAttack = template.hasRangedAttack;
            this.attackRange = template.attackRange;
            this.rangedProjectile = template.rangedProjectile;
            this.rangedDamageDice = template.rangedDamageDice;
            this.rangedDamageType = template.rangedDamageType;
            this.rangedEffect = template.rangedEffect;
            this.rangedEffectChance = template.rangedEffectChance;
            this.rangedPreferredDistance = template.rangedPreferredDistance;
        }
    }

    public int getEffectiveSpeed() {
        int speed = moveSpeed;
        if (statusManager.hasEffect(StatusEffectType.SLOWED)) {
            speed /= 2;
        }
        if (statusManager.hasEffect(StatusEffectType.SLOW)) {
            speed /= 2;
        }
        return Math.max(1, speed);
    }

    private float energy = 0;

    public float getEnergy() {
        return energy;
    }

    public void setEnergy(float energy) {
        this.energy = energy;
    }

    public void addEnergy(float amount) {
        this.energy += amount;
    }

    // --- AI State Getters/Setters ---
    public MonsterState getState() {
        return state;
    }

    public void setState(MonsterState state) {
        this.state = state;
    }

    public com.badlogic.gdx.math.GridPoint2 getLastKnownTargetPos() {
        return lastKnownTargetPos;
    }

    public void setLastKnownTargetPos(com.badlogic.gdx.math.GridPoint2 pos) {
        this.lastKnownTargetPos = pos;
    }

    public int getTurnsSinceLastSeen() {
        return turnsSinceLastSeen;
    }

    public void setTurnsSinceLastSeen(int turns) {
        this.turnsSinceLastSeen = turns;
    }

    public boolean isTagged() {
        return isTagged;
    }

    public void setTagged(boolean tagged) {
        this.isTagged = tagged;
    }

    /**
     * True for the designated champion of a themed chunk (the Castle Warden, the
     * Legion Commander). Killing it resolves a SLAY_CHAMPION objective.
     */
    public boolean isThemeChampion() {
        return themeChampion;
    }

    public void setThemeChampion(boolean themeChampion) {
        this.themeChampion = themeChampion;
    }

    /**
     * True for objective-critical NPCs such as the Graveyard's Gravedigger.
     *
     * <p>Themed chunks seal their gates, so an objective anchored on an NPC that
     * a stray arrow or a faction brawl can kill would strand the player. Damage
     * to an invulnerable monster is discarded. See
     * {@code docs/DEsign/Themed Chunk Contract.md} section 3.
     */
    public boolean isInvulnerable() {
        return invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public Faction getFaction() {
        return faction;
    }

    public void setFaction(Faction faction) {
        this.faction = faction;
    }

    public Monster getTargetMonster() {
        return targetMonster;
    }

    public void setTargetMonster(Monster targetMonster) {
        this.targetMonster = targetMonster;
    }

    public int getRetaliationTurnsRemaining() {
        return retaliationTurnsRemaining;
    }

    public void setRetaliationTurnsRemaining(int turns) {
        this.retaliationTurnsRemaining = turns;
    }

    public void decrementRetaliationTurns() {
        if (this.retaliationTurnsRemaining > 0) {
            this.retaliationTurnsRemaining--;
            if (this.retaliationTurnsRemaining == 0) {
                this.targetMonster = null;
            }
        }
    }

    /**
     * Provoked retaliation: When damaged by another monster, switch aggro to the attacker for 3 turns.
     */
    public void onAttackedBy(Monster attacker) {
        if (attacker != null && attacker != this && attacker.isAlive()) {
            this.targetMonster = attacker;
            this.retaliationTurnsRemaining = 3;
            this.state = MonsterState.HUNTING;
        }
    }

    public boolean isAlive() {
        return currentHP > 0;
    }

    public void setMaxHP(int maxHP) {
        this.maxHP = maxHP;
    }

    public void setMaxMP(int maxMP) {
        this.maxMP = maxMP;
    }

    public void setArmorClass(int ac) {
        this.armorClass = ac;
    }

    public void setDamageDice(String damageDice) {
        this.damageDice = damageDice;
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = intelligence;
    }

    public void setDexterity(int dexterity) {
        this.dexterity = dexterity;
    }

    public void setBaseExperience(int exp) {
        this.baseExperience = exp;
    }

    public void setMoveSpeed(int moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    public void setHasRangedAttack(boolean hasRanged) {
        this.hasRangedAttack = hasRanged;
    }

    public void setAttackRange(int range) {
        this.attackRange = range;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }

    public void setAiType(MonsterTemplate.AiType aiType) {
        this.aiType = aiType;
    }

    public void setSpellChance(int spellChance) {
        this.spellChance = spellChance;
    }

    public void setHealThreshold(float healThreshold) {
        this.healThreshold = healThreshold;
    }

    public boolean canOperateDoors() {
        if (canOpenDoors != null) return canOpenDoors;
        if (testTemplate != null && testTemplate.canOpenDoors != null) return testTemplate.canOpenDoors;
        if (family == MonsterFamily.BEAST) return false;
        if (type != null && type == MonsterType.DRAGON) return false;
        return intelligence >= 12;
    }

    public boolean canClimbLadders() {
        if (canClimbLadders != null) return canClimbLadders;
        if (testTemplate != null && testTemplate.canClimbLadders != null) return testTemplate.canClimbLadders;
        return canOperateDoors() && family != MonsterFamily.BEAST;
    }

    public void setCanOpenDoors(Boolean canOpenDoors) {
        this.canOpenDoors = canOpenDoors;
    }

    public void setCanClimbLadders(Boolean canClimbLadders) {
        this.canClimbLadders = canClimbLadders;
    }

    public int getSearchTurnsRemaining() {
        return searchTurnsRemaining;
    }

    public void setSearchTurnsRemaining(int searchTurnsRemaining) {
        this.searchTurnsRemaining = searchTurnsRemaining;
    }
}
