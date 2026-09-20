package com.bpm.minotaur.gamedata.firearm;

import com.bpm.minotaur.gamedata.item.Item;

/**
 * A firearm being reloaded: the turns between firing and being able to fire again.
 *
 * <p><strong>The reload is yours to abandon, not the enemy's to deny.</strong> Taking a
 * hit does not break it -- you flinch and keep loading. Only the player's own choice to
 * move or attack gives it up, and the caller enforces that by dropping the channel.
 * A three-turn commitment any goblin could erase would read as the game cheating.
 *
 * <p>Deliberately <em>not</em> built on {@code TomeStudy}, whose per-turn check breaks on
 * {@code hostileInView}. Reusing it would mean a gun could never be reloaded while an
 * enemy was visible -- the only circumstance in which reloading matters.
 */
public final class ReloadChannel {

    public enum Step {
        /** Still loading. */
        CONTINUE,
        /** Loaded and ready to fire. */
        COMPLETE
    }

    private final Item.ItemType weaponType;
    private final int turnsRequired;
    private int turnsDone;

    public ReloadChannel(Item.ItemType weaponType) {
        this.weaponType = weaponType;
        this.turnsRequired = FirearmProfile.reloadTurns(weaponType);
    }

    public Item.ItemType getWeaponType() {
        return weaponType;
    }

    public int getTurnsRequired() {
        return turnsRequired;
    }

    public int getTurnsDone() {
        return turnsDone;
    }

    /** Turns left, for the HUD. A commitment you cannot see the end of is just paralysis. */
    public int getTurnsRemaining() {
        return Math.max(0, turnsRequired - turnsDone);
    }

    public boolean isComplete() {
        return turnsDone >= turnsRequired;
    }

    /**
     * Advances one world turn.
     *
     * <p>Takes no interruption arguments on purpose: there is no world state that can
     * break a reload. Everything that ends one early is the player's own action, handled
     * by discarding the channel.
     */
    public Step afterTurn() {
        if (isComplete()) {
            return Step.COMPLETE;
        }
        turnsDone++;
        return isComplete() ? Step.COMPLETE : Step.CONTINUE;
    }
}
