package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.war.Disguise;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import static org.junit.Assert.*;

/** Living War W13-W16: the war as a rogue's cover. */
public class RogueToolsTest {

    private static Monster soldier() {
        Monster m = new Monster(Monster.MonsterType.ORC, 20, 12);
        m.setFaction(Faction.MAZE_HOUSE);
        m.setHouseId(2);
        m.setWarBand(true);
        return m;
    }

    @Test
    public void aBattlesDinCutsHowFarAMonsterHears() {
        int quiet = MonsterAiManager.hearingRange(10, 1f, 1f);
        int din = MonsterAiManager.hearingRange(10, 1f, MonsterAiManager.BATTLE_DIN);
        assertEquals(15, quiet);
        assertTrue("the din covers the player's footsteps: " + din, din <= 5);
    }

    @Test
    public void soldiersInAFightTurnOnThePlayerOnlyWhenStruckOrBeside() {
        assertFalse(MonsterAiManager.warBandTurnsOnPlayer(3, false));
        assertTrue("adjacent", MonsterAiManager.warBandTurnsOnPlayer(1, false));
        assertTrue("struck", MonsterAiManager.warBandTurnsOnPlayer(5, true));
    }

    @Test
    public void aGateGuardLetsThroughASeekerWhoHasStruckNoOne() {
        MonsterAiManager ai = new MonsterAiManager();
        Monster guard = soldier();
        guard.setGateGuard(true);
        assertTrue("let through", ai.ignoresPlayer(guard, 1));
        ai.setPlayerInTheFight(true);
        assertFalse("not once the player has joined the fight", ai.ignoresPlayer(guard, 1));
        ai.setPlayerInTheFight(false);
        guard.markSeekerDrewBlood();
        assertFalse("nor by one the player struck", ai.ignoresPlayer(guard, 1));
    }

    @Test
    public void aBorrowedBodyFoolsKinUntilTheyLookTooLong() {
        MonsterAiManager ai = new MonsterAiManager();
        Monster kin = soldier();
        Monster stranger = soldier();
        stranger.setHouseId(3);
        ai.setTakesPlayerForKin(m -> m.getHouseId() == 2);
        assertFalse("another house's soldier is not fooled", ai.ignoresPlayer(stranger, 4));
        assertTrue("at a distance, kin pay no mind", ai.ignoresPlayer(kin, 4));
        for (int i = 1; i < Disguise.SCRUTINY_TURNS; i++) assertTrue("beside them, turn " + i, ai.ignoresPlayer(kin, 1));
        assertFalse("and then they see", ai.ignoresPlayer(kin, 1));
        Monster struck = soldier();
        struck.markSeekerDrewBlood();
        assertFalse("strike one, and it knows", ai.ignoresPlayer(struck, 4));
    }
}
