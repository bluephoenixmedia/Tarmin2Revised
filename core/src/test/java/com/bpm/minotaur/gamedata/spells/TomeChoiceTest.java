package com.bpm.minotaur.gamedata.spells;

import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.managers.DivinityManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/** A Tome Choice offers unknown spells from the Tome's curated pool; the Altar widens it. */
public class TomeChoiceTest {

    private static final TomeChoice.Perks BASE = new TomeChoice.Perks(3, 0, 8);
    private final Random rng = new Random(7);

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        SpellDataManager.getInstance().load();
    }

    @Test
    public void candidatesComeFromTheTomesPoolAndSkipKnownSpells() {
        List<String> candidates = TomeChoice.candidates(Tome.INITIATE,
                Arrays.asList("MOTE_OF_LIGHT", "MAGIC_MISSILE"), BASE);

        assertTrue(candidates.contains("FIRE_BOLT"));
        assertTrue(candidates.contains("SHIELD"));
        assertFalse("Known spells are never offered", candidates.contains("MAGIC_MISSILE"));
        assertFalse("Only the Tome's own pool", candidates.contains("FIREBALL"));
        for (String id : candidates) {
            assertTrue(id, SpellDataManager.getSpell(id).isInTomePool(Tome.INITIATE));
        }
    }

    @Test
    public void offersThePerkCountOfDistinctCandidates() {
        TomeChoice choice = TomeChoice.offer(Tome.ARCANE, null, Collections.emptyList(), BASE, rng);

        assertEquals(3, choice.getOptions().size());
        assertEquals(3, new HashSet<>(choice.getOptions()).size());
    }

    @Test
    public void offersWhatIsLeftWhenThePoolRunsLow() {
        List<String> allInTier = new ArrayList<>(TomeChoice.curatedCandidates(Tome.ELEMENTS, Collections.emptyList(), BASE));
        allInTier.addAll(TomeChoice.generalCandidates(Tome.ELEMENTS, Collections.emptyList(), BASE));
        String last = allInTier.get(allInTier.size() - 1);

        TomeChoice choice = TomeChoice.offer(Tome.ELEMENTS, null, allInTier.subList(0, allInTier.size() - 1), BASE, rng);

        assertEquals(Collections.singletonList(last), choice.getOptions());
    }

    @Test
    public void anExhaustedPoolOffersFallbackMaxMpBoon() {
        List<String> allInTier = new ArrayList<>(TomeChoice.curatedCandidates(Tome.ELEMENTS, Collections.emptyList(), BASE));
        allInTier.addAll(TomeChoice.generalCandidates(Tome.ELEMENTS, Collections.emptyList(), BASE));

        TomeChoice choice = TomeChoice.offer(Tome.ELEMENTS, null, allInTier, BASE, rng);

        assertNotNull(choice);
        assertEquals(Collections.singletonList(TomeChoice.FALLBACK_MP_BONUS_ID), choice.getOptions());
    }

    @Test
    public void tarminOffersLevelNineOnlyWithTheAltarsTopTier() {
        List<String> base = TomeChoice.generalCandidates(Tome.TARMIN, Collections.emptyList(), BASE);
        List<String> attuned = TomeChoice.generalCandidates(Tome.TARMIN, Collections.emptyList(), new TomeChoice.Perks(5, 1, 9));

        assertFalse(base.contains("METEOR_SWARM"));
        assertTrue(attuned.contains("METEOR_SWARM"));
        for (String id : base) {
            assertTrue(id, SpellDataManager.getSpell(id).getLevel() <= 8);
        }
    }

    @Test
    public void aRerollDrawsNewOptionsAndSpendsTheReroll() {
        TomeChoice choice = TomeChoice.offer(Tome.INITIATE, null, Collections.emptyList(), new TomeChoice.Perks(3, 1, 7), rng);
        Set<String> first = new HashSet<>(choice.getOptions());

        assertTrue(choice.reroll(Collections.emptyList(), rng));

        assertEquals(0, choice.getRerollsLeft());
        assertEquals(3, choice.getOptions().size());
        for (String id : choice.getOptions()) {
            assertFalse("A reroll shows spells not just offered", first.contains(id));
        }
        assertFalse("No rerolls left", choice.reroll(Collections.emptyList(), rng));
    }

    @Test
    public void aRerollWithNothingNewToShowIsNotSpent() {
        List<String> allInTier = new ArrayList<>(TomeChoice.curatedCandidates(Tome.ELEMENTS, Collections.emptyList(), BASE));
        allInTier.addAll(TomeChoice.generalCandidates(Tome.ELEMENTS, Collections.emptyList(), BASE));
        List<String> known = allInTier.subList(0, allInTier.size() - 2);
        TomeChoice choice = TomeChoice.offer(Tome.ELEMENTS, null, known, new TomeChoice.Perks(3, 1, 8), rng);

        assertFalse(choice.canReroll(known));
        assertFalse(choice.reroll(known, rng));
        assertEquals(1, choice.getRerollsLeft());
    }

    @Test
    public void arcaneAttunementTiersWidenTheChoice() {
        ShelterAltar altar = ShelterAltar.getInstance();
        altar.reset();
        DivinityManager.getInstance().addDivinities(1000);

        assertEquals(new TomeChoice.Perks(3, 0, 8), altar.getTomeChoicePerks());
        altar.purchaseUpgrade(ShelterAltar.Tree.ARCANE_ATTUNEMENT);
        assertEquals(new TomeChoice.Perks(4, 0, 8), altar.getTomeChoicePerks());
        altar.purchaseUpgrade(ShelterAltar.Tree.ARCANE_ATTUNEMENT);
        assertEquals(new TomeChoice.Perks(4, 1, 8), altar.getTomeChoicePerks());
        altar.purchaseUpgrade(ShelterAltar.Tree.ARCANE_ATTUNEMENT);
        assertEquals(new TomeChoice.Perks(5, 1, 9), altar.getTomeChoicePerks());

        altar.reset();
    }
}
