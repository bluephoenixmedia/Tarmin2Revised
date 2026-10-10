package com.bpm.minotaur.gamedata.monster;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registry defining idle animation sprite sheets for monsters and NPCs.
 * Each registered monster has a looping 3x3 (9-frame) spritesheet
 * located in images/monsters/idle/.
 */
public final class MonsterIdleAnimationRegistry {

    public static final class IdleConfig {
        public final Monster.MonsterType monsterType;
        public final String texturePath;
        public final int cols;
        public final int rows;
        public final float frameDuration;

        public IdleConfig(Monster.MonsterType monsterType, String texturePath, int cols, int rows, float frameDuration) {
            this.monsterType = monsterType;
            this.texturePath = texturePath;
            this.cols = cols;
            this.rows = rows;
            this.frameDuration = frameDuration;
        }

        public IdleConfig(Monster.MonsterType monsterType, String texturePath) {
            this(monsterType, texturePath, 3, 3, 0.15f);
        }

        public int getTotalFrames() {
            return cols * rows;
        }
    }

    private static final Map<Monster.MonsterType, IdleConfig> REGISTRY = new EnumMap<>(Monster.MonsterType.class);

    static {
        register(Monster.MonsterType.ALLIGATOR, "images/monsters/idle/alligator_idle.png");
        register(Monster.MonsterType.BASILISK, "images/monsters/idle/basilisk_idle.png");
        register(Monster.MonsterType.BEHOLDER, "images/monsters/idle/beholder_idle.png");
        register(Monster.MonsterType.CHIMERA, "images/monsters/idle/chimera_idle.png");
        register(Monster.MonsterType.GELATINOUS_CUBE, "images/monsters/idle/cube_idle.png");
        register(Monster.MonsterType.DISPLACER_BEAST, "images/monsters/idle/dbeast_idle.png");
        register(Monster.MonsterType.DRAGON, "images/monsters/idle/dragon_idle.png");
        register(Monster.MonsterType.DWARF, "images/monsters/idle/dwarf_idle.png");
        register(Monster.MonsterType.MIND_FLAYER, "images/monsters/idle/flayer_idle.png");
        register(Monster.MonsterType.GARGOYLE, "images/monsters/idle/gargoyle_idle.png");
        register(Monster.MonsterType.GHAST, "images/monsters/idle/ghast_idle.png");
        register(Monster.MonsterType.GHOUL, "images/monsters/idle/ghoul_idle.png");
        register(Monster.MonsterType.GIANT_ANT, "images/monsters/idle/giant_ant_idle.png");
        register(Monster.MonsterType.GIANT, "images/monsters/idle/giant_idle.png");
        register(Monster.MonsterType.GIANT_SNAKE, "images/monsters/idle/giant_snake_idle.png");
        register(Monster.MonsterType.GOBLIN, "images/monsters/idle/goblin_idle.png");
        register(Monster.MonsterType.CLOAKED_SKELETON, "images/monsters/idle/hooded_skeleton_idle.png");
        register(Monster.MonsterType.HYDRA, "images/monsters/idle/hydra_idl.png");
        register(Monster.MonsterType.IRON_GOLEM, "images/monsters/idle/iron_golem_idle.png");
        register(Monster.MonsterType.KOBOLD, "images/monsters/idle/kobold_idle.png");
        register(Monster.MonsterType.LICH, "images/monsters/idle/lich_idle.png");
        register(Monster.MonsterType.MIMIC, "images/monsters/idle/mimic_idle.png");
        register(Monster.MonsterType.MINOTAUR, "images/monsters/idle/minotaur_idle.png");
        register(Monster.MonsterType.OGRE, "images/monsters/idle/ogre_idle.png");
        register(Monster.MonsterType.ORC, "images/monsters/idle/orc_idle.png");
        register(Monster.MonsterType.OWLBEAR, "images/monsters/idle/owlbear_idle.png");
        register(Monster.MonsterType.PURPLE_WORM, "images/monsters/idle/purple_worm_idle.png");
        register(Monster.MonsterType.RUST_MONSTER, "images/monsters/idle/rust_idle.png");
        register(Monster.MonsterType.GIANT_SCORPION, "images/monsters/idle/scorpion_idle.png");
        register(Monster.MonsterType.SKELETON, "images/monsters/idle/skeleton_idle.png");
        register(Monster.MonsterType.GIANT_SNAIL, "images/monsters/idle/snail_q_idl.png");
        register(Monster.MonsterType.SPIDER, "images/monsters/idle/spider_idle.png");
        register(Monster.MonsterType.TROGLODYTE, "images/monsters/idle/troglodye_idle.png");
        register(Monster.MonsterType.UMBER_HULK, "images/monsters/idle/u_hulk_idle.png");
        register(Monster.MonsterType.VAMPIRE, "images/monsters/idle/vampire_idle.png");
        register(Monster.MonsterType.WERERAT, "images/monsters/idle/wererat_idle.png");
        register(Monster.MonsterType.WRAITH, "images/monsters/idle/wraith_idle.png");
        register(Monster.MonsterType.ZOMBIE, "images/monsters/idle/zombie_idle.png");
    }

    private static void register(Monster.MonsterType type, String texturePath) {
        REGISTRY.put(type, new IdleConfig(type, texturePath));
    }

    public static IdleConfig getConfig(Monster.MonsterType type) {
        if (type == null) return null;
        return REGISTRY.get(type);
    }

    public static boolean hasIdleAnimation(Monster.MonsterType type) {
        return type != null && REGISTRY.containsKey(type);
    }

    public static Collection<IdleConfig> getAllConfigs() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    private MonsterIdleAnimationRegistry() {
    }
}
