package com.bpm.minotaur.telemetry;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Drives a simulated Expedition through the telemetry API: the shelter boundary, fights
 * (killed and disengaged), per-mode hit rates, and the survival, loot, progression and depth
 * totals that the balance tickets read.
 */
public class ExpeditionTelemetryTest {

    private TelemetryManager t;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        t = TelemetryManager.getInstance();
        t.startNewRun();
        t.setContext("MEDIUM", 1234L, "test-build");
    }

    private void turn(int n, int depth, boolean atShelter) {
        t.onTurn(n, depth, atShelter, 20, 5, false, false, false, false);
    }

    @Test
    public void anExpeditionOpensOutsideTheShelterAndClosesOnReturn() {
        turn(1, 1, true);
        assertFalse("Standing in the shelter is not an expedition", t.isExpeditionActive());

        turn(2, 1, false);
        assertTrue(t.isExpeditionActive());

        turn(3, 1, true);
        assertFalse("Stepping back into the shelter ends it as a return", t.isExpeditionActive());
    }

    @Test
    public void quittingInsideTheShelterWritesNothing() {
        turn(1, 1, true);
        assertNull(t.endExpedition(TelemetryManager.Outcome.ABANDONED, null));
    }

    @Test
    public void theFileCarriesFightsModesAndTotals() {
        Object goblin = new Object();
        Object rat = new Object();
        t.markCheatsUsed();

        turn(1, 1, true);
        turn(2, 1, false); // expedition opens on turn 2

        turn(3, 1, false);
        t.recordPlayerAttack(goblin, "GOBLIN", 1, TelemetryManager.AttackMode.MELEE, TelemetryManager.HitType.MISS, 0);
        t.recordMonsterAttack(goblin, "GOBLIN", 1, true, 3);
        t.recordPlayerAttack(rat, "RAT", 1, TelemetryManager.AttackMode.RANGED, TelemetryManager.HitType.HIT, 2);

        turn(4, 1, false);
        t.recordPlayerAttack(goblin, "GOBLIN", 1, TelemetryManager.AttackMode.MELEE, TelemetryManager.HitType.HIT, 6);
        t.recordKill(goblin, "GOBLIN");

        turn(6, 2, false); // depth 2 first reached 4 turns in
        turn(7, 2, false);
        turn(8, 2, false); // 5 quiet turns since the rat's last exchange on turn 3

        t.recordRegen(2, 1);
        t.recordGold(10);
        t.recordGold(-4);
        t.recordItemPickedUp("WEAPON");
        t.recordExperienceGained(15);
        t.recordLevelUp();
        t.recordDivinitiesSpent(3);

        String json = t.endExpedition(TelemetryManager.Outcome.ABANDONED, null);
        assertNotNull(json);
        JsonValue root = new JsonReader().parse(json);

        assertEquals(TelemetryManager.SCHEMA_VERSION, root.getInt("schemaVersion"));
        assertEquals("MEDIUM", root.getString("difficulty"));
        assertEquals(1234L, root.getLong("worldSeed"));
        assertTrue(root.getBoolean("cheatsUsed"));
        assertEquals("abandoned", root.getString("outcome"));
        assertEquals(6, root.getInt("turns"));
        assertEquals(2, root.getInt("maxDepth"));
        assertNotNull(root.getString("lifeId"));

        JsonValue melee = root.get("attacksByMode").get("melee");
        assertEquals(1, melee.getInt("hit"));
        assertEquals(1, melee.getInt("miss"));
        assertEquals(1, root.get("attacksByMode").get("ranged").getInt("hit"));

        JsonValue fights = root.get("fights");
        assertEquals(2, fights.size);
        JsonValue goblinFight = fights.get(0);
        assertEquals("GOBLIN", goblinFight.getString("monster"));
        assertEquals("killed", goblinFight.getString("outcome"));
        assertEquals(2, goblinFight.getInt("playerAttacks"));
        assertEquals(1, goblinFight.getInt("playerHits"));
        assertEquals(1, goblinFight.getInt("monsterHits"));
        assertEquals(3, goblinFight.getInt("damageTaken"));
        JsonValue ratFight = fights.get(1);
        assertEquals("disengaged", ratFight.getString("outcome"));
        assertEquals("ranged", ratFight.get("modes").getString(0));

        assertEquals(2, root.getInt("hpRegenerated"));
        assertEquals(1, root.getInt("mpRegenerated"));
        assertEquals(10, root.getInt("goldGained"));
        assertEquals(4, root.getInt("goldSpent"));
        assertEquals(15, root.getInt("xpGained"));
        assertEquals(3, root.getInt("divinitiesSpent"));
        assertEquals(1, root.get("itemsPickedUpByCategoryAtDepth").getInt("WEAPON@2"));
        assertEquals(1, root.get("levelUpTurns").size);
        assertEquals(4, root.get("depthFirstReachedTurn").getInt("2"));
        assertEquals(2, root.get("encountersByDepth").getInt("1"));
        assertEquals(1, root.getInt("kills"));
    }

    @Test
    public void eachExpeditionReportsOnlyItsOwnCountsWhileTheLifeKeepsTheTotal() {
        turn(1, 1, false);
        t.recordKill(new Object(), "RAT");
        turn(2, 1, true); // returned

        turn(3, 1, false);
        t.recordKill(new Object(), "BAT");
        String json = t.endExpedition(TelemetryManager.Outcome.DIED, "Fell to a bat");

        JsonValue root = new JsonReader().parse(json);
        assertEquals(1, root.getInt("kills"));
        assertFalse(root.get("monstersKilledByType").has("RAT"));
        assertEquals(2, t.getTotalMonstersKilled());
    }

    @Test
    public void aDeathClosesOpenFightsAsDied() {
        Object ogre = new Object();
        turn(1, 3, false);
        t.recordMonsterAttack(ogre, "OGRE", 4, true, 30);
        String json = t.exportRun("Fell to an ogre");

        JsonValue fight = new JsonReader().parse(json).get("fights").get(0);
        assertEquals("died", fight.getString("outcome"));
        assertEquals(3, fight.getInt("depth"));
    }
}
