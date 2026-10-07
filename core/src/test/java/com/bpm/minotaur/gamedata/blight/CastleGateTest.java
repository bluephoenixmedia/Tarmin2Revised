package com.bpm.minotaur.gamedata.blight;

import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import org.junit.Test;

import static org.junit.Assert.*;

/** The castle gate opens to three seals (docs/DEsign/Requirements_ Shelter Roads.md, section 7). */
public class CastleGateTest {

    @Test
    public void theGateRefusesUntilEverySealIsHeld() {
        assertFalse(CastleGate.isOpen(0));
        assertFalse(CastleGate.isOpen(2));
        assertTrue(CastleGate.isOpen(3));
        assertTrue(CastleGate.knockMessage(2).contains("2/3"));
    }

    @Test
    public void sealsWonOnTheRoadsCountAtTheGate() {
        ShelterNetwork net = ShelterNetwork.getInstance();
        net.resetForNewGame();
        net.awardSeal(1);
        net.awardSeal(2);
        assertEquals(2, CastleGate.sealsHeld());
        assertFalse(CastleGate.isOpen());
        net.awardSeal(3);
        assertTrue(CastleGate.isOpen());
        net.resetForNewGame();
    }
}
