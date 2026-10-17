package com.bpm.minotaur.gamedata.save;

import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.injury.BodyPart;
import com.bpm.minotaur.gamedata.injury.IllnessStage;
import com.bpm.minotaur.gamedata.injury.InjuryRecord;
import com.bpm.minotaur.gamedata.injury.InjuryType;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * State that used to be dropped on save and load: the live status effects, every wound and the
 * illness clock, meal buffs, temporary HP and the camp supplies. A reload must not cure, feed or
 * re-stock the character.
 */
public class PlayerRunStateRoundTripTest {

    private static Player roundTrip(Player original) {
        Json json = new Json();
        String text = json.toJson(new PlayerSaveData(original));
        PlayerSaveData loaded = json.fromJson(PlayerSaveData.class, text);
        Player fresh = new Player(0, 0);
        loaded.applyToPlayer(fresh, null, null);
        return fresh;
    }

    @Test
    public void statusEffectsSurvive() {
        Player p = new Player(0, 0);
        p.getStatusManager().addEffect(StatusEffectType.POISONED, 7, 3, false);
        p.getStatusManager().addEffect(StatusEffectType.CURSED, -1, 1, false);

        Player loaded = roundTrip(p);

        ActiveStatusEffect poison = loaded.getStatusManager().getEffect(StatusEffectType.POISONED);
        assertNotNull("poison must not be cured by a reload", poison);
        assertEquals(7, poison.getDuration());
        assertEquals(3, poison.getPotency());
        assertEquals(-1, loaded.getStatusManager().getEffect(StatusEffectType.CURSED).getDuration());
    }

    @Test
    public void woundsAndIllnessSurvive() {
        Player p = new Player(0, 0);
        InjuryRecord cut = p.getInjuryManager().inflictInjury(BodyPart.TORSO, InjuryType.LACERATION_BLEEDING, 3);
        cut.consumeBleedTick();
        cut.incrementTurnsUntreated();
        InjuryRecord fracture = p.getInjuryManager().inflictInjury(BodyPart.ARMS, InjuryType.BONE_FRACTURE, 2);
        fracture.setTreated(true);
        fracture.setInfected(true);
        p.getInjuryManager().setIllnessStage(IllnessStage.values()[1]);

        Player loaded = roundTrip(p);

        InjuryRecord c = loaded.getInjuryManager().getInjury(BodyPart.TORSO);
        assertNotNull("an open wound must not close on reload", c);
        assertEquals(InjuryType.LACERATION_BLEEDING, c.getInjuryType());
        assertEquals(3, c.getSeverity());
        assertEquals(cut.getBleedTicksRemaining(), c.getBleedTicksRemaining());
        assertEquals(1, c.getTurnsUntreated());
        InjuryRecord f = loaded.getInjuryManager().getInjury(BodyPart.ARMS);
        assertTrue(f.isTreated());
        assertTrue(f.isInfected());
        assertEquals(IllnessStage.values()[1], loaded.getInjuryManager().getIllnessStage());
    }

    @Test
    public void mealBuffsTempHpAndCampSuppliesSurvive() {
        Player p = new Player(0, 0);
        p.restoreActiveMealEffects(java.util.Arrays.asList(StatusEffectType.HUNGRY));
        p.getStats().setTemporaryHP(7);
        p.getStats().setKindlingCount(2);
        p.getStats().setCookingWaterCount(1);
        p.getStats().setCookingSkill(4);

        Player loaded = roundTrip(p);

        assertEquals(java.util.Arrays.asList(StatusEffectType.HUNGRY), loaded.getActiveMealEffects());
        assertEquals(7, loaded.getStats().getTemporaryHP());
        assertEquals(2, loaded.getStats().getKindlingCount());
        assertEquals(1, loaded.getStats().getCookingWaterCount());
        assertEquals(4, loaded.getStats().getCookingSkill());
    }

    @Test
    public void anOldSaveWithNoRunStateLoadsWithoutCuringAnything() {
        Player loaded = new Player(0, 0);
        PlayerSaveData legacy = new PlayerSaveData();
        legacy.applyToPlayer(loaded, null, null);
        assertFalse(loaded.getInjuryManager().hasAnyInjuries());
    }
}
