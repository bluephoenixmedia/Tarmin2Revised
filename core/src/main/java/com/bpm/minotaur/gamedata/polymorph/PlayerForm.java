package com.bpm.minotaur.gamedata.polymorph;

import com.bpm.minotaur.gamedata.monster.MonsterFamily;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;

/**
 * The body the player has taken by polymorphing themselves: its own hit points, armour class, speed
 * and attack, for a limited time. When the form's hit points run out, or the time does, the player
 * returns to their own body at the hit points they left it with; the form's damage never kills them.
 *
 * <p>This is deliberately only numbers and attacks. Keeping it a small value object leaves room to
 * replace it with something richer later without touching the callers.
 */
public final class PlayerForm {

    public static final int MIN_TURNS = 100;
    public static final int MAX_TURNS = 300;
    /** From this monster level up, a form is too big for body armour and bursts it. */
    public static final int BURST_ARMOR_LEVEL = 9;
    static final int BASE_SPEED = 12;

    private final String name;
    private final int maxHp;
    private int hp;
    private final int armorClass;
    private final float speedFactor;
    private final String damageDice;
    private final boolean hasHands;
    private final boolean bursts;
    private int turnsLeft;
    /** The monsters.json type the body is, or null: a house fielding it may take the seeker for kin (Living War W16). */
    private String monsterType;

    public PlayerForm(String name, int maxHp, int armorClass, float speedFactor, String damageDice,
            boolean hasHands, boolean bursts, int turns) {
        this.name = name;
        this.maxHp = Math.max(1, maxHp);
        this.hp = this.maxHp;
        this.armorClass = armorClass;
        this.speedFactor = speedFactor;
        this.damageDice = damageDice == null || damageDice.isEmpty() ? "1d4" : damageDice;
        this.hasHands = hasHands;
        this.bursts = bursts;
        this.turnsLeft = turns;
    }

    public static PlayerForm of(String name, MonsterTemplate t, int turns) {
        boolean hands = t.family == MonsterFamily.HUMANOID;
        return new PlayerForm(name, t.maxHP, t.armorClass, t.moveSpeed / (float) BASE_SPEED, t.damageDice,
                hands, t.baseLevel >= BURST_ARMOR_LEVEL, turns);
    }

    /** As {@link #of(String, MonsterTemplate, int)}, remembering the monster type the body is. */
    public static PlayerForm of(String name, String monsterType, MonsterTemplate t, int turns) {
        PlayerForm f = of(name, t, turns);
        f.monsterType = monsterType;
        return f;
    }

    public String monsterType() {
        return monsterType;
    }

    /** Hits taken by the form come off its own hit points; whatever it cannot take is lost, never passed on. */
    public void absorb(int damage) {
        hp = Math.max(0, hp - Math.max(0, damage));
    }

    /** One turn passes; true when the time has run out. */
    public boolean tick() {
        turnsLeft--;
        return turnsLeft <= 0;
    }

    public boolean isSpent() {
        return hp <= 0 || turnsLeft <= 0;
    }

    public String name() {
        return name;
    }

    public int hp() {
        return hp;
    }

    public int maxHp() {
        return maxHp;
    }

    public int armorClass() {
        return armorClass;
    }

    public String damageDice() {
        return damageDice;
    }

    /** Scales the player's speed by how quick the form is (a wolf is faster than a troll). */
    public int applySpeed(int speed) {
        return Math.max(1, Math.round(speed * speedFactor));
    }

    /** A form without hands cannot cast spells, use items or wield weapons. */
    public boolean hasHands() {
        return hasHands;
    }

    public boolean burstsArmor() {
        return bursts;
    }

    public int turnsLeft() {
        return turnsLeft;
    }

    /** A random duration of {@value #MIN_TURNS} to {@value #MAX_TURNS} turns. */
    public static int randomTurns(java.util.Random rng) {
        return MIN_TURNS + rng.nextInt(MAX_TURNS - MIN_TURNS + 1);
    }
}
