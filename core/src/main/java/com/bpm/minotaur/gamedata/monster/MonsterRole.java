package com.bpm.minotaur.gamedata.monster;

/**
 * The part a monster plays in an encounter (#185, decided in #161). Tagged per template in
 * {@code monsters.json} as {@code "role"}.
 *
 * <ul>
 *   <li>{@link #FODDER}: dies in a swing or two, hits weakly, comes in packs of 3-6.</li>
 *   <li>{@link #SOLDIER}: an on-level fight, alone or in twos and threes.</li>
 *   <li>{@link #HEAVY}: rare and telegraphed, worth fighting or avoiding; extra kill XP.</li>
 * </ul>
 */
public enum MonsterRole {
    FODDER, SOLDIER, HEAVY;

    /** The role a template plays; an untagged template is a Soldier. */
    public static MonsterRole of(MonsterTemplate template) {
        return template != null && template.role != null ? template.role : SOLDIER;
    }
}
