package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.player.PlayerStats;

import java.util.*;

/**
 * Singleton repository holding definitions and prerequisites for all player skills.
 */
public class SkillRegistry {

    private static SkillRegistry instance;
    private final Map<SkillId, SkillDefinition> skills = new EnumMap<>(SkillId.class);

    private SkillRegistry() {
        registerAll();
    }

    public static synchronized SkillRegistry getInstance() {
        if (instance == null) {
            instance = new SkillRegistry();
        }
        return instance;
    }

    private void registerAll() {
        // --- WARFARE DISCIPLINE ---
        register(new SkillDefinition(
                SkillId.DUAL_WIELDER,
                "Dual Wielder",
                "Dual-Wielding Expert (Open5e)",
                "You master fighting with a weapon in each hand. Unlocks equipping off-hand melee weapons, executes an alternating 3-hit combo chain culminating in a scissor finisher, and grants +1 AC while wielding two weapons.",
                Collections.emptySet(),
                ShelterAltar.StatType.STRENGTH,
                11
        ));

        register(new SkillDefinition(
                SkillId.SHIELD_MASTER,
                "Shield Master",
                "Shield Focus (Open5e)",
                "Your shield is an impassable bulwark. Shield bashes stagger and knock back foes, and you gain +15% block deflection against physical blows.",
                Collections.emptySet(),
                ShelterAltar.StatType.CONSTITUTION,
                11
        ));

        register(new SkillDefinition(
                SkillId.BRUTAL_CLEAVE,
                "Brutal Cleave",
                "Brutal Attack / Powerful Attacker (Open5e)",
                "Combo finishers deal +20% damage. Landing a lethal melee blow cleaves 50% of the overkill damage into an adjacent enemy.",
                Set.of(SkillId.DUAL_WIELDER),
                ShelterAltar.StatType.STRENGTH,
                13
        ));

        register(new SkillDefinition(
                SkillId.HEAVY_ARMOR_MASTERY,
                "Heavy Armor Mastery",
                "Heavy Armor Expertise (Open5e)",
                "Plate and chain become second skin. Incoming physical damage is reduced by 3 when wearing heavy armor, and heavy armor movement/stealth penalties are negated.",
                Set.of(SkillId.SHIELD_MASTER),
                ShelterAltar.StatType.CONSTITUTION,
                13
        ));

        register(new SkillDefinition(
                SkillId.WHIRLWIND_EXECUTIONER,
                "Whirlwind Executioner",
                "Dual-Wielding Expert (Open5e Master)",
                "A spinning storm of steel. The dual-wield Scissor Finisher deals an extra +50% critical strike damage and causes arterial bleeding on the victim.",
                Set.of(SkillId.BRUTAL_CLEAVE),
                ShelterAltar.StatType.STRENGTH,
                15
        ));

        // --- FINESSE DISCIPLINE ---
        register(new SkillDefinition(
                SkillId.SKIRMISHER,
                "Skirmisher",
                "Skirmisher / Mobile (Open5e)",
                "You are as swift and elusive as the wind. Movement recovery is accelerated by +15%, and maneuvering away from a struck foe prevents reaction strikes.",
                Collections.emptySet(),
                ShelterAltar.StatType.AGILITY,
                11
        ));

        register(new SkillDefinition(
                SkillId.RAPID_QUAFF,
                "Rapid Quaff",
                "Rapid Drinker (Open5e)",
                "Battle-honed reflexes let you drink potions and consume rations instantly without consuming an action turn in combat.",
                Collections.emptySet(),
                ShelterAltar.StatType.DEXTERITY,
                11
        ));

        register(new SkillDefinition(
                SkillId.DEADEYE_SNIPER,
                "Deadeye Sniper",
                "Deadeye / Crossbow Expertise (Open5e)",
                "Lethal precision with bows, crossbows, and firearms. Disadvantage at point-blank melee range is negated, and ranged strikes at distance deal +25% bonus damage.",
                Set.of(SkillId.SKIRMISHER),
                ShelterAltar.StatType.DEXTERITY,
                13
        ));

        register(new SkillDefinition(
                SkillId.DUNGEON_SCAVENGER,
                "Dungeon Scavenger",
                "Dungeoneer / Woodcraft Training (Open5e)",
                "An eye honed to subterranean resourcefulness. +35% chance to discover bonus arrows, shot, torches, rations, and crafting components from containers and remains.",
                Set.of(SkillId.RAPID_QUAFF),
                ShelterAltar.StatType.WISDOM,
                12
        ));

        register(new SkillDefinition(
                SkillId.ELUSIVE_REFLEXES,
                "Elusive Reflexes",
                "Deflector / Survivor (Open5e Master)",
                "Your lightning reflexes grant +15% passive dodge chance against all incoming attacks. Once per delve, lethal damage is averted, leaving you at 1 HP.",
                Set.of(SkillId.DEADEYE_SNIPER),
                ShelterAltar.StatType.AGILITY,
                15
        ));

        // --- ARCANA DISCIPLINE ---
        register(new SkillDefinition(
                SkillId.BATTLE_CASTER,
                "Battle Caster",
                "Battle Caster / War Caster (Open5e)",
                "You cast with fluid ease amidst the chaos of combat. You can weave spells seamlessly while dual-wielding or holding shields, and your Maximum MP increases by +15%.",
                Collections.emptySet(),
                ShelterAltar.StatType.INTELLIGENCE,
                11
        ));

        register(new SkillDefinition(
                SkillId.SPELL_WEAVER,
                "Spell Weaver",
                "Mystical Talent (Open5e)",
                "Effortless resonance with magical currents. The MP cost of all cast spells is reduced by 1 (minimum 1 MP).",
                Collections.emptySet(),
                ShelterAltar.StatType.WISDOM,
                11
        ));

        register(new SkillDefinition(
                SkillId.PRIMORDIAL_FOCUS,
                "Primordial Focus",
                "Primordial Caster / Rimecaster (Open5e)",
                "Your magic seethes with raw elemental energy. Fire, Ice, Poison, and Lightning spells deal +25% bonus damage and ignore enemy elemental resistances.",
                Set.of(SkillId.BATTLE_CASTER),
                ShelterAltar.StatType.INTELLIGENCE,
                13
        ));

        register(new SkillDefinition(
                SkillId.RUNIC_CONSERVATION,
                "Runic Conservation",
                "Sorcerous Vigor (Open5e)",
                "You tap ambient planar ether. 25% chance that casting a spell or activating a magic device consumes 0 MP and preserves scroll/tome charges.",
                Set.of(SkillId.SPELL_WEAVER),
                ShelterAltar.StatType.WISDOM,
                13
        ));

        register(new SkillDefinition(
                SkillId.ARCHMAGES_SURGE,
                "Archmage's Surge",
                "Power Caster (Open5e Master)",
                "Casting any spell supercharges your weapons with crackling arcane energy, causing your next physical attack to deal +50% bonus magic damage.",
                Set.of(SkillId.PRIMORDIAL_FOCUS, SkillId.RUNIC_CONSERVATION),
                ShelterAltar.StatType.INTELLIGENCE,
                15
        ));
    }

    private void register(SkillDefinition def) {
        skills.put(def.getId(), def);
    }

    public SkillDefinition getSkill(SkillId id) {
        return skills.get(id);
    }

    public SkillDefinition getDefinition(SkillId id) {
        return getSkill(id);
    }

    public List<SkillDefinition> getSkillsByDisciplineAndTier(SkillId.Discipline discipline, int tier) {
        List<SkillDefinition> list = new ArrayList<>();
        for (SkillDefinition def : skills.values()) {
            if (def.getDiscipline() == discipline && def.getTier() == tier) {
                list.add(def);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public List<SkillDefinition> getSkillsForDiscipline(SkillId.Discipline discipline) {
        List<SkillDefinition> list = new ArrayList<>();
        for (SkillDefinition def : skills.values()) {
            if (def.getDiscipline() == discipline) {
                list.add(def);
            }
        }
        list.sort(Comparator.comparingInt(SkillDefinition::getTier));
        return Collections.unmodifiableList(list);
    }

    public Collection<SkillDefinition> getAllSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }

    /**
     * Checks if player meets all prerequisites to learn this skill (except point cost, checked by caller).
     */
    public boolean canLearn(PlayerStats stats, SkillId id) {
        if (stats == null || id == null) return false;
        if (stats.hasSkill(id)) return false;

        SkillDefinition def = getSkill(id);
        if (def == null) return false;

        // Check prerequisite skills
        for (SkillId prereq : def.getPrerequisites()) {
            if (!stats.hasSkill(prereq)) {
                return false;
            }
        }

        // Check required attribute score
        if (def.getRequiredStat() != null && def.getRequiredStatValue() > 0) {
            int currentStat = stats.getEffectiveStat(def.getRequiredStat());
            if (currentStat < def.getRequiredStatValue()) {
                return false;
            }
        }

        return true;
    }
}
