package com.bpm.minotaur.gamedata.item;

/**
 * The Father's Sword: the protagonist's rusty heirloom and a Keepsake (see CONTEXT.md).
 *
 * <p>This is the one place its perk values live. It returns with every Incarnation
 * ({@code GameScreen.respawnInShelter} hands a new one over, #187), and its perk deepens
 * slowly with the soul's milestones -- the deepest stratum ever reached and the seals won --
 * in small, capped steps. Applying the perk while the sword is carried is #177's rule; it
 * reads {@link #currentPerk()}.
 */
public final class FathersSword {

    /** The item type that is the Father's Sword. */
    public static final Item.ItemType TYPE = Item.ItemType.RUSTY_SWORD;

    /** The starting perk (#177): +3 max HP and +5% crit. */
    public static final int BASE_MAX_HP_BONUS = 3;
    public static final float BASE_CRIT_BONUS = 0.05f;

    /** One milestone step per this many strata reached below the surface. */
    public static final int STRATA_PER_STEP = 3;
    /** Each step adds this much to the perk. */
    public static final int MAX_HP_PER_STEP = 1;
    public static final float CRIT_PER_STEP = 0.01f;
    /** The perk never deepens past this many steps: +7 max HP and +9% crit at most. */
    public static final int MAX_STEPS = 4;

    private FathersSword() {
    }

    /** What the perk grants at a given depth of the soul. */
    public static final class Perk {
        public final int steps;
        public final int maxHpBonus;
        public final float critBonus;

        Perk(int steps) {
            this.steps = steps;
            this.maxHpBonus = BASE_MAX_HP_BONUS + steps * MAX_HP_PER_STEP;
            this.critBonus = BASE_CRIT_BONUS + steps * CRIT_PER_STEP;
        }
    }

    public static boolean isFathersSword(Item item) {
        return item != null && item.getType() == TYPE;
    }

    /** Milestone steps earned: one per {@link #STRATA_PER_STEP} strata, one per seal, capped. */
    public static int milestoneSteps(int deepestStratum, int sealsWon) {
        int steps = Math.max(0, deepestStratum) / STRATA_PER_STEP + Math.max(0, sealsWon);
        return Math.min(MAX_STEPS, steps);
    }

    public static Perk perkFor(int deepestStratum, int sealsWon) {
        return new Perk(milestoneSteps(deepestStratum, sealsWon));
    }

    /** Dungeon level 1 is the surface; level 2 is the first stratum. */
    public static int strataBelowSurface(int deepestLevel) {
        return Math.max(0, deepestLevel - 1);
    }

    /**
     * The perk as the soul has earned it so far, from the save-scoped milestones that
     * survive death: {@code UnlockData.deepestLevelReached} and the seals in
     * {@code ShelterNetwork}. Falls back to the starting perk if either is unavailable.
     */
    public static Perk currentPerk() {
        int deepestStratum = 0;
        int seals = 0;
        try {
            com.bpm.minotaur.gamedata.UnlockData data =
                    com.bpm.minotaur.managers.UnlockManager.getInstance().getData();
            if (data != null) deepestStratum = strataBelowSurface(data.deepestLevelReached);
        } catch (RuntimeException ignored) {
            // No unlock data yet: the soul has no depth to remember.
        }
        try {
            seals = com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().getSealCount();
        } catch (RuntimeException ignored) {
            // No shelter network yet: no seals won.
        }
        return perkFor(deepestStratum, seals);
    }
}
