package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry defining projectile archetypes for monster ranged attacks, including
 * high-contrast 24x24 pixel art matrices, default elemental damage types, chromatic auras,
 * optional BearFX 3D impact explosions, and sound effect triggers.
 */
public class MonsterProjectileRegistry {

    public static class MonsterProjectileDefinition {
        private final String id;
        private final String name;
        private final String[] spriteData;
        private final Color color;
        private final DamageType defaultDamageType;
        private final ExplosionType explosionType;
        private final String soundKey;
        private final float speed;

        public MonsterProjectileDefinition(String id, String name, String[] spriteData, Color color,
                                           DamageType defaultDamageType, ExplosionType explosionType,
                                           String soundKey, float speed) {
            this.id = id;
            this.name = name;
            this.spriteData = spriteData;
            this.color = color != null ? color : Color.WHITE;
            this.defaultDamageType = defaultDamageType != null ? defaultDamageType : DamageType.PHYSICAL;
            this.explosionType = explosionType;
            this.soundKey = soundKey;
            this.speed = speed > 0 ? speed : 15.0f;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String[] getSpriteData() {
            return spriteData;
        }

        public Color getColor() {
            return color;
        }

        public DamageType getDefaultDamageType() {
            return defaultDamageType;
        }

        public ExplosionType getExplosionType() {
            return explosionType;
        }

        public String getSoundKey() {
            return soundKey;
        }

        public float getSpeed() {
            return speed;
        }
    }

    private static final Map<String, MonsterProjectileDefinition> REGISTRY = new HashMap<>();

    // --- 24x24 Pixel Art Matrices ---

    public static final String[] SPRITE_ARROW = new String[] {
            "........................",
            "...........##...........",
            "..........####..........",
            ".........######.........",
            "........########........",
            ".......##########.......",
            "......############......",
            ".........####...........",
            ".........####...........",
            ".........####...........",
            ".........####...........",
            ".........####...........",
            ".........####...........",
            ".........####...........",
            ".........####...........",
            ".........####...........",
            ".........####...........",
            ".........####...........",
            "........######..........",
            ".......########.........",
            "......###....###........",
            ".....##........##.......",
            "........................",
            "........................"
    };

    public static final String[] SPRITE_BOULDER = new String[] {
            "........................",
            "........................",
            ".........######.........",
            ".......##########.......",
            ".....##############.....",
            "....################....",
            "...##################...",
            "..####################..",
            "..####################..",
            ".######################.",
            ".######################.",
            ".######################.",
            ".######################.",
            ".######################.",
            "..####################..",
            "..####################..",
            "...##################...",
            "....################....",
            ".....##############.....",
            ".......##########.......",
            ".........######.........",
            "........................",
            "........................",
            "........................"
    };

    public static final String[] SPRITE_STONE = new String[] {
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "..........####..........",
            "........########........",
            ".......##########.......",
            "......############......",
            "......############......",
            "......############......",
            "......############......",
            ".......##########.......",
            "........########........",
            "..........####..........",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................"
    };

    public static final String[] SPRITE_WEB = new String[] {
            "........................",
            "....#..............#....",
            ".....#.....##.....#.....",
            "......#...####...#......",
            "..#....#.######.#....#..",
            "...#...##########...#...",
            "....#.############.#....",
            ".....##############.....",
            "....################....",
            "###.################.###",
            "....################....",
            ".....##############.....",
            "....#.############.#....",
            "...#...##########...#...",
            "..#....#.######.#....#..",
            "......#...####...#......",
            ".....#.....##.....#.....",
            "....#..............#....",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................"
    };

    public static final String[] SPRITE_VENOM = new String[] {
            "........................",
            "...........##...........",
            "..........####..........",
            "..........####..........",
            ".........######.........",
            ".........######.........",
            "........########........",
            "........########........",
            ".......##########.......",
            "......############......",
            "......############......",
            ".....##############.....",
            ".....##############.....",
            ".....##############.....",
            "......############......",
            ".......##########.......",
            "........########........",
            ".........######.........",
            ".....#....####....#.....",
            "....##............##....",
            "........................",
            "........................",
            "........................",
            "........................"
    };

    public static final String[] SPRITE_FIREBALL = new String[] {
            "........................",
            "...........##...........",
            ".........######.........",
            "........########........",
            ".......##########.......",
            "......############......",
            ".....##############.....",
            "....################....",
            "....################....",
            "...##################...",
            "...##################...",
            "...##################...",
            "....################....",
            "....################....",
            ".....##############.....",
            "......############......",
            ".......##########.......",
            "........########........",
            "....#....######....#....",
            "...##.....####.....##...",
            "..###......##......###..",
            "........................",
            "........................",
            "........................"
    };

    public static final String[] SPRITE_DEATH_RAY = new String[] {
            "........................",
            ".........######.........",
            ".......##########.......",
            "......############......",
            ".....##############.....",
            "....################....",
            "...##################...",
            "...######......######...",
            "..######........######..",
            "..#####..........#####..",
            "..#####..........#####..",
            "..#####..........#####..",
            "..######........######..",
            "...######......######...",
            "...##################...",
            "....################....",
            ".....##############.....",
            "......############......",
            ".......##########.......",
            ".........######.........",
            "........................",
            "........................",
            "........................",
            "........................"
    };

    public static final String[] SPRITE_PSYCHIC_BOLT = new String[] {
            "........................",
            "...........##...........",
            "..........####..........",
            "....#....######....#....",
            ".....#..########..#.....",
            "......############......",
            "....################....",
            "...##################...",
            "...##################...",
            "########################",
            "########################",
            "...##################...",
            "...##################...",
            "....################....",
            "......############......",
            ".....#..########..#.....",
            "....#....######....#....",
            "..........####..........",
            "...........##...........",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................"
    };

    public static final String[] SPRITE_RADIANT_SPEAR = new String[] {
            "........................",
            "...........##...........",
            "..........####..........",
            ".........######.........",
            "........########........",
            ".........######.........",
            "..........####..........",
            "..........####..........",
            "..........####..........",
            "..........####..........",
            ".........######.........",
            "........########........",
            ".........######.........",
            "..........####..........",
            "..........####..........",
            "..........####..........",
            "..........####..........",
            ".........######.........",
            "........########........",
            ".........######.........",
            "..........####..........",
            "...........##...........",
            "........................",
            "........................"
    };

    static {
        // 1. Archer / Skirmisher Projectiles
        register(new MonsterProjectileDefinition(
                "ARROW", "Arrow", SPRITE_ARROW,
                new Color(0.95f, 0.95f, 0.88f, 1.0f),
                DamageType.PHYSICAL, null, "player_bow_attack", 16.0f));

        register(new MonsterProjectileDefinition(
                "STONE", "Sling Stone", SPRITE_STONE,
                new Color(0.75f, 0.75f, 0.72f, 1.0f),
                DamageType.PHYSICAL, null, "weapon_swing", 14.0f));

        // 2. Brute Hurler Projectiles
        register(new MonsterProjectileDefinition(
                "BOULDER", "Hurled Boulder", SPRITE_BOULDER,
                new Color(0.65f, 0.60f, 0.55f, 1.0f),
                DamageType.PHYSICAL, ExplosionType.CONCUSSIVE, "metal_hit_heavy", 11.0f));

        // 3. Venom & Web Beast Projectiles
        register(new MonsterProjectileDefinition(
                "WEB", "Web Strands", SPRITE_WEB,
                new Color(0.92f, 0.94f, 0.98f, 0.95f),
                DamageType.PHYSICAL, null, "weapon_swing_2", 13.0f));

        register(new MonsterProjectileDefinition(
                "VENOM", "Venom Spit", SPRITE_VENOM,
                new Color(0.20f, 0.95f, 0.30f, 1.0f),
                DamageType.POISON, ExplosionType.TOXIC, "meat_hit", 13.0f));

        register(new MonsterProjectileDefinition(
                "ACID", "Acid Jet", SPRITE_VENOM,
                new Color(0.85f, 0.95f, 0.15f, 1.0f),
                DamageType.POISON, ExplosionType.TOXIC, "meat_hit", 14.0f));

        // 4. Draconic & Blaster Projectiles
        register(new MonsterProjectileDefinition(
                "FIREBALL", "Fireball", SPRITE_FIREBALL,
                new Color(1.0f, 0.45f, 0.05f, 1.0f),
                DamageType.FIRE, ExplosionType.FIRE, "monster_roar", 15.0f));

        // 5. Occult & Aberrant Ray Projectiles
        register(new MonsterProjectileDefinition(
                "DEATH_RAY", "Death Ray", SPRITE_DEATH_RAY,
                new Color(0.65f, 0.15f, 0.85f, 1.0f),
                DamageType.DARK, ExplosionType.VOID, "void_laser", 17.0f));

        register(new MonsterProjectileDefinition(
                "PSYCHIC_BOLT", "Psychic Blast", SPRITE_PSYCHIC_BOLT,
                new Color(0.90f, 0.20f, 0.85f, 1.0f),
                DamageType.SORCERY, ExplosionType.ELECTRIC, "void_laser_alt", 16.0f));

        register(new MonsterProjectileDefinition(
                "RADIANT_SPEAR", "Radiant Spear", SPRITE_RADIANT_SPEAR,
                new Color(1.0f, 0.88f, 0.35f, 1.0f),
                DamageType.LIGHT, ExplosionType.HOLY_CROSS, "player_spiritual_attack", 17.0f));
    }

    public static void register(MonsterProjectileDefinition def) {
        if (def != null && def.getId() != null) {
            REGISTRY.put(def.getId().toUpperCase(), def);
        }
    }

    public static MonsterProjectileDefinition get(String id) {
        if (id == null) {
            return REGISTRY.get("ARROW");
        }
        MonsterProjectileDefinition def = REGISTRY.get(id.trim().toUpperCase());
        return def != null ? def : REGISTRY.get("ARROW");
    }

    public static boolean contains(String id) {
        return id != null && REGISTRY.containsKey(id.trim().toUpperCase());
    }

    public static Map<String, MonsterProjectileDefinition> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
