package com.bpm.minotaur.gamedata.worldlog;

import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Life;
import com.bpm.minotaur.gamedata.history.LifeForge;
import com.bpm.minotaur.gamedata.history.Nemesis;
import com.bpm.minotaur.gamedata.history.SeekerDeath;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * "World Log: the coherence check for many lives" (#286): sixty lives lived through the real
 * history, prisoner forge, Nemeses, World Log and Tellings, and nothing they say may disagree.
 * Bridge and Age checks join when those slices land.
 */
public class WorldLogCoherenceTest {

    private static final int LIVES = 60;
    private static HistoryManager history;
    private static WorldLog log;
    private static final List<Life> forged = new ArrayList<>();

    @BeforeClass
    public static void liveSixtyLives() throws IOException {
        history = HistoryManager.create(31L, DoctrineCatalogTest.loadCatalog());
        log = new WorldLog(5L);
        Random r = new Random(9L);
        String[] kinds = {"KOBOLD", "ORC", "OGRE", "GIANT_SPIDER", "SKELETON"};
        int bridgeBegan = 0;
        for (int i = 0; i < LIVES; i++) {
            // The Bridge ends twice: its Warden falls after life 20, Tarmin crosses after life 40 (#290).
            if (i == 20) {
                log.closeBridge(Reckoning.End.WARDEN_FELL, Reckonings.wardenFell(Bridge.nameOf(log.currentBridge()), log.last()));
                history.recordWardenFell();
                bridgeBegan = i;
            } else if (i == 40) {
                history.recordCrossing();
                com.bpm.minotaur.gamedata.history.OldGod god =
                        com.bpm.minotaur.gamedata.history.OldGod.spendable(history.world()).get(0);
                history.recordGodSpent(god);
                log.closeBridge(Reckoning.End.CROSSED,
                        Reckonings.crossed(Bridge.nameOf(log.currentBridge()), god, "House Gall", log.last()));
                bridgeBegan = i;
            }
            int hunger = i - bridgeBegan;
            Set<String> taken = new HashSet<>();
            for (LifeRecord lived : log.lives()) taken.add(lived.name);
            Life life = LifeForge.forge(history.world(), 1000L + i, taken);
            forged.add(life);
            log.wake(life, hunger);

            List<House> houses = history.world().livingHouses();
            House house = houses.get(r.nextInt(houses.size()));
            if (r.nextInt(3) == 0) history.swear(house.id);
            if (r.nextInt(4) == 0) history.breakOath();
            if (r.nextInt(3) == 0 && !history.world().livingMembers(house).isEmpty()) {
                Figure f = history.world().livingMembers(house).get(0);
                history.recordKill(f.id);
            }

            List<Nemesis> alive = new ArrayList<>();
            for (Nemesis n : history.world().nemeses()) if (n.isAlive()) alive.add(n);
            SeekerDeath death;
            List<Figure> members = history.world().livingMembers(house);
            if (r.nextInt(6) == 0 && !members.isEmpty()) {
                Figure f = members.get(members.size() - 1);
                death = SeekerDeath.by(f.name, "ORC", house.id).figure(f.id);
            } else if (!alive.isEmpty() && r.nextBoolean()) {
                Nemesis again = alive.get(r.nextInt(alive.size()));
                death = SeekerDeath.by(again.name, again.monsterType, -1).nemesis(again.id);
            } else {
                int killerHouse = r.nextBoolean() ? house.id : -1;
                death = SeekerDeath.by("a beast", kinds[r.nextInt(kinds.length)], killerHouse);
            }
            Nemesis killer = history.recordSeekerFell(death.of(life.name).at(2 + r.nextInt(4), r.nextInt(5), r.nextInt(5)));
            log.die("Slain", killer != null ? killer.name : "a beast", "level 2", 10 * i);

            LifeRecord ended = log.last();
            // As the game does at a death (WorldLogManager.tellLastLife), at the Hunger after it.
            log.tell(ended.number, Tellings.forLife(history.world(), history.lastLifeDeeds(), ended, hunger + 1));
        }
    }

    @Test
    public void sixtyLivesTellNothingThatDisagrees() {
        assertEquals(LIVES, log.lives().size());
        assertEquals("a Warden's fall and a crossing make three Bridges", 3, log.bridges().size());
        // Every rule is exercised, so none can pass for want of a case.
        assertTrue("a Nemesis took more than one life",
                history.world().nemeses().stream().anyMatch(n -> n.tally() >= 2));
        assertTrue("a house Figure became a Nemesis",
                history.world().nemeses().stream().anyMatch(n -> n.figureId >= 0));
        java.util.EnumSet<Tellings.Role> voices = java.util.EnumSet.noneOf(Tellings.Role.class);
        for (LifeRecord life : log.lives()) for (Telling t : life.tellings) voices.add(t.role);
        assertTrue("voices heard: " + voices, voices.containsAll(java.util.EnumSet.of(
                Tellings.Role.KILLER, Tellings.Role.KNELL, Tellings.Role.OATHBROKEN, Tellings.Role.SWORN)));
        for (int i = 0; i < LIVES; i++) {
            LifeRecord life = log.lives().get(i);
            assertEquals(forged.get(i).name, life.name);
            assertEquals(forged.get(i).settlement, life.homeName());
        }
        assertEquals(new ArrayList<String>(), WorldLogCoherence.check(log, history.world()));
    }

    @Test
    public void itCatchesALogThatDisagrees() {
        WorldLog broken = new WorldLog(5L);
        Life brenna = new Life("Brenna Thatch", 1, "the Ashen Ford",
                com.bpm.minotaur.gamedata.history.town.Allegiance.REFUGEE_COUNCIL, Life.Origin.TITHED,
                null, "debt", "the Refugee Council");
        broken.wake(brenna, 0);
        broken.note(Fact.of(Fact.Kind.SWORE_OATH, "House Gall"));
        String stranger = history.world().nemeses().get(0).name;
        broken.die("Slain", stranger, "level 2", 5);
        broken.wake(brenna, 1);
        broken.note(Fact.of(Fact.Kind.SWORE_OATH, "House Gall"));
        broken.die("Slain", "a beast", "level 2", 9);
        Telling wrong = new Telling();
        wrong.teller = "House Gall";
        wrong.plain = "Skrag Ratbite of the Hook Mire, the ninth seeker, died.";
        wrong.mythic = wrong.plain;
        broken.tell(1, java.util.Collections.singletonList(wrong));

        List<String> problems = WorldLogCoherence.check(broken, history.world());
        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("Brenna Thatch") && p.contains("two lives")));
        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("House Gall")));
        assertTrue("a Nemesis named the killer of a life it never took: " + problems,
                problems.stream().anyMatch(p -> p.contains(stranger) && p.contains("Brenna Thatch")));
    }
}
