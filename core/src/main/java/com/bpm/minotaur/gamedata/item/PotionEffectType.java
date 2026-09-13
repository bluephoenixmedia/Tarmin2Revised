package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.managers.StatusManager;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.ModifierType;

/**
 * Defines the true, underlying effect of a potion.
 */
public enum PotionEffectType {

    // --- Core / Randomized Tier 1 Potions ---
    HEALING(true, "Healing", "Healing", "You feel restorative energy course through you!"),
    POISON(true, "Poison", "Poison", "You feel very sick..."),
    GAIN_STRENGTH(true, "Gain Strength", "Gain Strength", "You feel permanently stronger!"),
    BLINDNESS(true, "Blindness", "Blindness", "You can't see!"),
    CONFUSION(true, "Confusion", "Confusion", "You feel confused..."),
    LEVITATION(false, "Levitation", "Levitation", "You begin to float!"),
    BERZERK(true, "Berzerk", "Berzerk", "Rage consumes you!"),
    RESTORE_ENERGY(true, "Energy", "Energy", "You feel energized!"),
    SUPER_VISION(true, "Vision", "Vision", "Your senses expand!"),
    SLEEP(true, "Sleeping", "Sleeping", "You yawn uncontrollably..."),

    // --- Open5e Tier 2 & 3 Named / Subterranean Potions ---
    GREATER_HEALING(true, "Greater Healing", "Greater Healing", "A rush of deep vitality mends your wounds!"),
    SUPERIOR_HEALING(true, "Superior Healing", "Superior Healing", "Potent healing energies knit your flesh and bones!"),
    SUPREME_HEALING(true, "Supreme Healing", "Supreme Healing", "Supreme radiant life surges through your body!"),
    SPEED(true, "Speed", "Speed", "Everything around you slows to a crawl! You are accelerated!"),
    GIANT_STRENGTH(true, "Giant Strength", "Giant Strength", "Muscles bulge with crude, monstrous power! (STR 21)"),
    HEROISM(true, "Heroism", "Heroism", "Bravery and holy vigor infuse your spirit! (+10 Temp HP, Blessed)"),
    HILL_GIANT_STRENGTH(true, "Hill Giant Strength", "Hill Giant Strength", "Muscles bulge with crude, monstrous power! (STR 21)"),
    FIRE_GIANT_STRENGTH(true, "Fire Giant Strength", "Fire Giant Strength", "Molten fury courses through your sinews! (STR 25)"),
    STORM_GIANT_STRENGTH(true, "Storm Giant Strength", "Storm Giant Strength", "Crackling lightning pulses through your godlike limbs! (STR 29)"),
    RESISTANCE_FIRE(true, "Fire Resistance", "Fire Resistance", "You feel cool and immune to scorching heat."),
    RESISTANCE_COLD(true, "Cold Resistance", "Cold Resistance", "A warm inner flame shields you from freezing frost."),
    RESISTANCE_LIGHTNING(true, "Lightning Resistance", "Lightning Resistance", "Your skin grounds electrical static safely."),
    RESISTANCE_ACID(true, "Acid Resistance", "Acid Resistance", "A protective sheen neutralizes corrosive fumes."),
    RESISTANCE_NECROTIC(true, "Necrotic Resistance", "Necrotic Resistance", "Spiritual warding repels withering decay."),
    INVULNERABILITY(true, "Invulnerability", "Invulnerability", "Your body hardens like diamond, resisting all harm!"),
    DIMINUTION(true, "Diminution", "Diminution", "You shrink down to half your size! (+2 AC, +15% Dodge, -2 Dmg)"),
    GROWTH(true, "Growth", "Growth", "You expand to giant proportions! (+3 Dmg, +10 Max HP, -1 AC)"),
    CLIMBING(true, "Climbing", "Climbing", "Your limbs find effortless grip on sheer dungeon rock!"),
    OIL_OF_SHARPNESS(true, "Sharpness", "Oil of Sharpness", "A keen, razor-sharp edge coats your weapon!"),
    VITALITY(true, "Vitality", "Vitality", "Pure vitality surges through you, cleansing exhaustion and weakness!"),
    CLARITY(true, "Clarity", "Clarity", "Your mind clears of all fog and confusion!"),
    SLIPPERINESS(true, "Slipperiness", "Oil of Slipperiness", "You are coated in an impossibly slick film!"),
    ETHEREALNESS(true, "Etherealness", "Oil of Etherealness", "You slip partially into the ethereal border plane!"),
    ELIXIR_HEALTH(true, "Health", "Elixir of Health", "A soothing draught that cures all afflictions!"),
    INVISIBILITY(true, "Invisibility", "Invisibility", "You fade from sight completely!");

    private final boolean selfIdentifies;
    private final String baseName; // "Potion of [baseName]"
    private final String description; // "A Potion of [baseName]."
    private final String consumeMessage; // Message shown on consumption

    PotionEffectType(boolean selfIdentifies, String baseName, String description, String consumeMessage) {
        this.selfIdentifies = selfIdentifies;
        this.baseName = baseName;
        this.description = "A Potion of " + description;
        this.consumeMessage = consumeMessage;
    }

    public String getBaseName() {
        return baseName;
    }

    public String getDescription() {
        return description;
    }

    public String getConsumeMessage() {
        return consumeMessage;
    }

    public boolean doesSelfIdentify() {
        return selfIdentifies;
    }

    private static int rollDice(int count, int sides) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += 1 + (int) (Math.random() * sides);
        }
        return total;
    }

    /**
     * Applies the potion's effect to the player.
     * 
     * @param player        The player consuming the potion.
     * @param statusManager A reference to the game's StatusManager.
     */
    public void applyEffect(Player player, StatusManager statusManager) {
        PlayerStats stats = player.getStats();

        // Toxicity: standard +5, heavy elixirs +8
        int tox = (this == GIANT_STRENGTH || this == HILL_GIANT_STRENGTH || this == FIRE_GIANT_STRENGTH || this == STORM_GIANT_STRENGTH || this == SPEED || this == INVULNERABILITY) ? 8 : 5;
        stats.modifyToxicity(tox);

        switch (this) {
            case HEALING: {
                int dice = rollDice(2, 4) + 2;
                int floor = (int) (stats.getMaxHP() * 0.20f);
                stats.heal(Math.max(dice, Math.max(floor, 10)));
                break;
            }
            case GREATER_HEALING: {
                int dice = rollDice(4, 4) + 4;
                int floor = (int) (stats.getMaxHP() * 0.40f);
                stats.heal(Math.max(dice, Math.max(floor, 20)));
                break;
            }
            case SUPERIOR_HEALING: {
                int dice = rollDice(8, 4) + 8;
                int floor = (int) (stats.getMaxHP() * 0.60f);
                stats.heal(Math.max(dice, Math.max(floor, 35)));
                break;
            }
            case SUPREME_HEALING: {
                int dice = rollDice(10, 4) + 20;
                int floor = (int) (stats.getMaxHP() * 0.85f);
                stats.heal(Math.max(dice, Math.max(floor, 60)));
                break;
            }
            case POISON: {
                int dmg = rollDice(3, 6);
                stats.setCurrentHP(stats.getCurrentHP() - dmg);
                statusManager.addEffect(StatusEffectType.POISONED, 10, 2, true);
                break;
            }
            case GAIN_STRENGTH:
                stats.modifyBaseHP(1);
                break;
            case BLINDNESS:
                statusManager.addEffect(StatusEffectType.BLIND, 20, 1, false);
                break;
            case CONFUSION:
                statusManager.addEffect(StatusEffectType.CONFUSED, 15, 1, false);
                break;
            case LEVITATION:
                statusManager.addEffect(StatusEffectType.FLOATING, 30, 1, false);
                break;
            case BERZERK:
                statusManager.addEffect(StatusEffectType.BERZERK, 30, 1, false);
                break;
            case RESTORE_ENERGY:
                stats.restoreMP(10 * stats.getLevel());
                break;
            case SUPER_VISION:
                statusManager.addEffect(StatusEffectType.OMNISCIENT, 30, 1, false);
                break;
            case SLEEP:
                statusManager.addEffect(StatusEffectType.SLEEP, 20, 1, false);
                break;
            case SPEED:
                statusManager.addEffect(StatusEffectType.HASTED, 20, 1, false);
                break;
            case HEROISM:
                stats.addTemporaryHP(10);
                statusManager.addEffect(StatusEffectType.HEROISM, 150, 1, false);
                break;
            case GIANT_STRENGTH:
            case HILL_GIANT_STRENGTH:
                statusManager.addEffect(StatusEffectType.GIANT_STRENGTH, 150, 21, false);
                break;
            case FIRE_GIANT_STRENGTH:
                statusManager.addEffect(StatusEffectType.GIANT_STRENGTH, 150, 25, false);
                break;
            case STORM_GIANT_STRENGTH:
                statusManager.addEffect(StatusEffectType.GIANT_STRENGTH, 150, 29, false);
                break;
            case RESISTANCE_FIRE:
                statusManager.addEffect(StatusEffectType.RESIST_FIRE, 150, 1, false);
                break;
            case RESISTANCE_COLD:
                statusManager.addEffect(StatusEffectType.RESIST_COLD, 150, 1, false);
                break;
            case RESISTANCE_LIGHTNING:
                statusManager.addEffect(StatusEffectType.RESIST_LIGHTNING, 150, 1, false);
                break;
            case RESISTANCE_ACID:
                statusManager.addEffect(StatusEffectType.RESIST_ACID, 150, 1, false);
                break;
            case RESISTANCE_NECROTIC:
                statusManager.addEffect(StatusEffectType.RESIST_NECROTIC, 150, 1, false);
                break;
            case INVULNERABILITY:
                statusManager.addEffect(StatusEffectType.INVULNERABILITY, 20, 1, false);
                break;
            case DIMINUTION:
                statusManager.addEffect(StatusEffectType.DIMINUTIVE, 150, 1, false);
                break;
            case GROWTH:
                statusManager.addEffect(StatusEffectType.ENLARGED, 150, 1, false);
                break;
            case CLIMBING:
                statusManager.addEffect(StatusEffectType.TEMP_SPEED, 150, 1, false);
                break;
            case OIL_OF_SHARPNESS:
                if (player.getInventory() != null && player.getInventory().getRightHand() != null) {
                    player.getInventory().getRightHand().addModifier(new ItemModifier(ModifierType.BONUS_TO_HIT, 3, "Sharpened"));
                    player.getInventory().getRightHand().addModifier(new ItemModifier(ModifierType.BONUS_DAMAGE, 3, "Keen"));
                }
                break;
            case INVISIBILITY:
                statusManager.addEffect(StatusEffectType.INVISIBLE, 150, 1, false);
                break;
            case VITALITY:
                statusManager.removeEffect(StatusEffectType.POISONED);
                statusManager.removeEffect(StatusEffectType.SICK);
                statusManager.removeEffect(StatusEffectType.EXHAUSTED);
                statusManager.removeEffect(StatusEffectType.WEAKENED);
                stats.heal((int) (stats.getMaxHP() * 0.5f));
                break;
            case CLARITY:
                statusManager.removeEffect(StatusEffectType.CONFUSED);
                statusManager.removeEffect(StatusEffectType.CONFUSION);
                statusManager.removeEffect(StatusEffectType.HALLUCINATING);
                statusManager.addEffect(StatusEffectType.FOCUSED, 150, 1, false);
                stats.restoreMP(35);
                break;
            case SLIPPERINESS:
                statusManager.removeEffect(StatusEffectType.SLOWED);
                statusManager.removeEffect(StatusEffectType.SLOW);
                statusManager.removeEffect(StatusEffectType.PARALYZED);
                statusManager.addEffect(StatusEffectType.FREE_ACTION, 150, 1, false);
                break;
            case ETHEREALNESS:
                statusManager.addEffect(StatusEffectType.FLOATING, 40, 1, false);
                statusManager.addEffect(StatusEffectType.INVISIBLE, 40, 1, false);
                break;
            case ELIXIR_HEALTH:
                statusManager.removeEffect(StatusEffectType.POISONED);
                statusManager.removeEffect(StatusEffectType.SICK);
                statusManager.removeEffect(StatusEffectType.BLIND);
                statusManager.removeEffect(StatusEffectType.PARALYZED);
                statusManager.removeEffect(StatusEffectType.CONFUSED);
                stats.heal(Math.max((int) (stats.getMaxHP() * 0.25f), 20));
                break;
        }
    }
}
