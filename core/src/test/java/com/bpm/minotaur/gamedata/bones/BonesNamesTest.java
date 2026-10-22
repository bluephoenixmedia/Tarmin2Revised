package com.bpm.minotaur.gamedata.bones;

import com.badlogic.gdx.utils.Json;
import org.junit.Test;

import static org.junit.Assert.*;

/** "World Log: graves and ghosts carry the life's name" (#282). */
public class BonesNamesTest {

    private static BonesData brannoc(String killer) {
        BonesData b = new BonesData();
        b.lifeName = "Brannoc Thatch";
        b.lifeSettlement = "the Ashen Ford";
        b.killer = killer;
        return b;
    }

    @Test
    public void aGraveNamesThePrisonerTheirHomeAndWhatFelledThem() {
        assertEquals("Here lies Brannoc Thatch of the Ashen Ford, who fell to Gorvak the Gnawer",
                brannoc("Gorvak the Gnawer").graveText());
        assertEquals("Here lies Brannoc Thatch of the Ashen Ford", brannoc(null).graveText());
    }

    @Test
    public void aGhostIsTheShadeOfThatLifeNeverAFallenHero() {
        assertEquals("the shade of Brannoc Thatch", brannoc("a Kobold").shadeName());
        assertEquals("Brannoc Thatch", brannoc("a Kobold").name());
    }

    @Test
    public void bonesFromBeforeLivesStillReadByTheNameTheyKept() {
        BonesData old = new Json().fromJson(BonesData.class,
                "{\"id\":\"x\",\"playerName\":\"Hero the Oathbreaker\",\"epitaph\":\"Fell\"}");
        assertEquals("Here lies Hero the Oathbreaker", old.graveText());
        assertEquals("the shade of Hero the Oathbreaker", old.shadeName());
        assertEquals("Hero the Oathbreaker", old.name());
    }
}
