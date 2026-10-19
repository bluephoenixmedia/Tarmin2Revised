package com.bpm.minotaur.telemetry;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.telemetry.TelemetryManager.HitType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** The expedition file records Weapon Families, Mastery and spells cast (#234). */
public class MasteryTelemetryTest {

    private TelemetryManager telemetry;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        telemetry = TelemetryManager.getInstance();
        telemetry.startNewRun();
    }

    private JsonValue exported() {
        return new JsonReader().parse(telemetry.toJson());
    }

    private static Monster monsterOfLevel(int level) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = level;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 10, 10);
        m.setTemplate(t);
        return m;
    }

    @Test
    public void theSchemaIsVersionThree() {
        assertEquals(3, exported().getInt("schemaVersion"));
    }

    @Test
    public void attacksAreCountedByWeaponFamilyWithTheQualifyingHits() {
        telemetry.recordFamilyAttack(null, "AXE", HitType.HIT);
        telemetry.recordFamilyAttack(null, "AXE", HitType.HIT);
        telemetry.recordFamilyAttack(null, "AXE", HitType.GLANCING);
        telemetry.recordFamilyAttack(null, "AXE", HitType.MISS);
        telemetry.recordQualifyingHit("AXE");

        JsonValue axe = exported().get("attacksByFamily").get("axe");
        assertEquals(2, axe.getInt("hit"));
        assertEquals(1, axe.getInt("glancing"));
        assertEquals(1, axe.getInt("miss"));
        assertEquals(1, axe.getInt("qualifying"));
    }

    @Test
    public void rankUpsAreRecordedWithTheirTurn() {
        telemetry.onTurn(40, 1, false, 20, 10, false, false, false, false);
        telemetry.recordMasteryRankUp("BLADE", "NOVICE");

        JsonValue up = exported().get("masteryRankUps").child;
        assertEquals("blade", up.getString("family"));
        assertEquals("novice", up.getString("rank"));
        assertTrue(up.has("turn"));
    }

    @Test
    public void spellsCastAndTheHealingTheyGaveAreRecorded() {
        telemetry.recordSpellCast("CURE_WOUNDS");
        telemetry.recordSpellCast("CURE_WOUNDS");
        telemetry.recordSpellCast("MOTE_OF_LIGHT");
        telemetry.recordSpellHealing("CURE_WOUNDS", 7);

        JsonValue json = exported();
        assertEquals(2, json.get("spellsCast").getInt("CURE_WOUNDS"));
        assertEquals(1, json.get("spellsCast").getInt("MOTE_OF_LIGHT"));
        assertEquals(7, json.get("spellHealing").getInt("CURE_WOUNDS"));
    }

    @Test
    public void theExpeditionRecordsThePlayersStartingLevel() {
        telemetry.notePlayerLevel(3);
        telemetry.onTurn(1, 1, false, 20, 10, false, false, false, false); // the first field turn opens the expedition

        assertEquals(3, exported().getInt("playerLevelStart"));
    }

    @Test
    public void aFightRecordsTheFamiliesUsedThePlayersLevelAndTheMonstersRole() {
        telemetry.notePlayerLevel(1);
        Monster goblin = monsterOfLevel(1);
        telemetry.recordPlayerAttack(goblin, "GOBLIN", 1, TelemetryManager.AttackMode.MELEE, HitType.HIT, 4);
        telemetry.recordFamilyAttack(goblin, "BLADE", HitType.HIT);
        telemetry.recordFamilyAttack(goblin, "THROWN", HitType.MISS);

        // Fights are written once they close, and ending the expedition closes them.
        JsonValue fight = new JsonReader().parse(telemetry.endExpedition(TelemetryManager.Outcome.DIED, "test"))
                .get("fights").child;
        assertEquals(1, fight.getInt("playerLevel"));
        assertEquals("soldier", fight.getString("role"));
        String families = fight.get("families").toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json);
        assertTrue(families, families.contains("blade") && families.contains("thrown"));
    }

    @Test
    public void artUsesHaveAnEmptySlotUntilTheArtsExist() {
        assertEquals(0, exported().get("artUses").size);
    }
}
