package com.bpm.minotaur.gamedata.dice;

import com.badlogic.gdx.graphics.Color;

/**
 * Factory for creating specific, themed dice instances.
 */
public class DiceFactory {

    public static Die create(String dieName) {
        switch (dieName) {
            case "Rusty Iron Die":
                return createRustyIronDie();
            case "Warrior's Red Die":
                return createWarriorRedDie();
            case "Guardian's Steel Die":
                return createGuardianSteelDie();
            case "Archer's Precision Die":
                return createArcherPrecisionDie();
            case "Pyromancer's Ember Die":
                return createPyromancerEmberDie();
            case "Frostbound Die":
                return createFrostboundDie();
            case "Priest's Holy Die":
                return createPriestHolyDie();
            case "Rogue's Shadow Die":
                return createRogueShadowDie();
            case "Necromancer's Bone Die":
                return createNecromancerBoneDie();
            case "Gambler's Gold Die":
                return createGamblerGoldDie();
            default:
                // Fallback
                return new Die("Unknown Die", Color.WHITE,
                        new DieFace(DieFaceType.BLANK, 0));
        }
    }

    // 1. Rusty Iron Die (Basic Melee)
    // Themes: Unreliable, weak damage.
    private static Die createRustyIronDie() {
        return new Die("Rusty Iron Die", Color.BROWN,
                new DieFace(DieFaceType.SWORD, 1),
                new DieFace(DieFaceType.SWORD, 1),
                new DieFace(DieFaceType.BLANK, 0),
                new DieFace(DieFaceType.BLANK, 0),
                new DieFace(DieFaceType.GLANCING, 1),
                new DieFace(DieFaceType.SHIELD, 1));
    }

    // 2. Warrior's Red Die (Swords)
    // Themes: Aggressive, consistent damage.
    private static Die createWarriorRedDie() {
        return new Die("Warrior's Red Die", Color.RED,
                new DieFace(DieFaceType.SWORD, 1),
                new DieFace(DieFaceType.SWORD, 2),
                new DieFace(DieFaceType.SWORD, 2),
                new DieFace(DieFaceType.CRIT, 2, "Cleave"),
                new DieFace(DieFaceType.SKULL, 3, "Reckless Swing"),
                new DieFace(DieFaceType.SHIELD, 1));
    }

    // 3. Guardian's Steel Die (Shields)
    // Themes: Defensive, parrying.
    private static Die createGuardianSteelDie() {
        return new Die("Guardian's Steel Die", Color.GRAY,
                new DieFace(DieFaceType.SHIELD, 1),
                new DieFace(DieFaceType.SHIELD, 2),
                new DieFace(DieFaceType.SHIELD, 3, "Tower Shield"),
                new DieFace(DieFaceType.PARRY, 2, "Parry"),
                new DieFace(DieFaceType.SWORD, 1),
                new DieFace(DieFaceType.BLANK, 0));
    }

    // 4. Archer's Precision Die (Bows)
    // Themes: Accuracy, crits, weak base damage.
    private static Die createArcherPrecisionDie() {
        return new Die("Archer's Precision Die", Color.FOREST,
                new DieFace(DieFaceType.BULLSEYE, 1, "Aim"),
                new DieFace(DieFaceType.BULLSEYE, 1, "Aim"),
                new DieFace(DieFaceType.SWORD, 2, "Shot"),
                new DieFace(DieFaceType.SWORD, 3, "Power Shot"),
                new DieFace(DieFaceType.CRIT, 2, "Headshot"),
                new DieFace(DieFaceType.BLANK, 0));
    }

    // 5. Pyromancer's Ember Die (Fire)
    // Themes: High damage, burn, self-clutter (Ash).
    private static Die createPyromancerEmberDie() {
        return new Die("Pyromancer's Ember Die", Color.ORANGE,
                new DieFace(DieFaceType.FIRE, 1),
                new DieFace(DieFaceType.FIRE, 2),
                new DieFace(DieFaceType.FIRE, 3, "Inferno"),
                new DieFace(DieFaceType.ASH, 0, "Ash"),
                new DieFace(DieFaceType.ASH, 0, "Ash"),
                new DieFace(DieFaceType.SKULL, 1, "Backfire"));
    }

    // 6. Frostbound Die (Ice)
    // Themes: Crowd control, consistent low damage.
    private static Die createFrostboundDie() {
        return new Die("Frostbound Die", Color.CYAN,
                new DieFace(DieFaceType.ICE, 1),
                new DieFace(DieFaceType.ICE, 1),
                new DieFace(DieFaceType.ICE, 2, "Freeze"),
                new DieFace(DieFaceType.SHIELD, 2, "Ice Armor"),
                new DieFace(DieFaceType.BLANK, 0),
                new DieFace(DieFaceType.BLANK, 0));
    }

    // 7. Priest's Holy Die (Healing)
    // Themes: Healing, spiritual damage.
    private static Die createPriestHolyDie() {
        return new Die("Priest's Holy Die", Color.GOLD,
                new DieFace(DieFaceType.HEART, 1, "Heal"),
                new DieFace(DieFaceType.HEART, 2, "Great Heal"),
                new DieFace(DieFaceType.MOON, 1, "Pray"),
                new DieFace(DieFaceType.MOON, 2, "Smite"), // Assuming Moon handles spirit dmg contextually
                new DieFace(DieFaceType.SHIELD, 1, "Blessing"),
                new DieFace(DieFaceType.BLANK, 0));
    }

    // 8. Rogue's Shadow Die (Poison/Stealth)
    // Themes: DoT, gambling, evasion.
    private static Die createRogueShadowDie() {
        return new Die("Rogue's Shadow Die", Color.PURPLE,
                new DieFace(DieFaceType.POISON, 1),
                new DieFace(DieFaceType.POISON, 2, "Venom"),
                new DieFace(DieFaceType.POISON, 3, "Deadly Toxin"),
                new DieFace(DieFaceType.SWORD, 1, "Backstab"),
                new DieFace(DieFaceType.CRIT, 3, "Assassinate"),
                new DieFace(DieFaceType.SKULL, 0, "Caught"));
    }

    // 9. Necromancer's Bone Die (Summoning)
    // Themes: Resource gathering (Bone), life cost.
    private static Die createNecromancerBoneDie() {
        return new Die("Necromancer's Bone Die", Color.TAN,
                new DieFace(DieFaceType.BONE, 1, "Collect Bone"),
                new DieFace(DieFaceType.BONE, 2, "Ribcage"),
                new DieFace(DieFaceType.SKULL, 1, "Blood Sacrifice"),
                new DieFace(DieFaceType.SWORD, 2, "Skeletal Minion"),
                new DieFace(DieFaceType.CURSE, 1, "Curse"),
                new DieFace(DieFaceType.BLANK, 0));
    }

    // 10. Gambler's Gold Die (Economy)
    // Themes: High risk, high reward, gold generation.
    private static Die createGamblerGoldDie() {
        return new Die("Gambler's Gold Die", Color.YELLOW,
                new DieFace(DieFaceType.GOLD, 1, "Coin"),
                new DieFace(DieFaceType.GOLD, 5, "Jackpot"),
                new DieFace(DieFaceType.CURSE, 1, "Debt"),
                new DieFace(DieFaceType.SKULL, 0, "Bust"),
                new DieFace(DieFaceType.CRIT, 2, "Lucky Streak"),
                new DieFace(DieFaceType.BLANK, 0));
    }
}
