package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.managers.UnlockRotation.Candidate;
import com.bpm.minotaur.managers.UnlockRotation.Category;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

public class UnlockRotationTest {

    private static List<Candidate> pool(int weapons, int armour, int items) {
        List<Candidate> pool = new ArrayList<>();
        Item.ItemType[] types = Item.ItemType.values();
        int next = 0;
        for (int i = 0; i < weapons; i++) {
            pool.add(new Candidate(types[next++], Category.WEAPON));
        }
        for (int i = 0; i < armour; i++) {
            pool.add(new Candidate(types[next++], Category.ARMOR));
        }
        for (int i = 0; i < items; i++) {
            pool.add(new Candidate(types[next++], Category.ITEM));
        }
        return pool;
    }

    @Test
    public void aHugeCategoryNoLongerTakesEveryUnlock() {
        // 200 armour against 10 of everything else: a flat shuffle would give ~91% armour.
        List<Candidate> pool = pool(10, 200, 10);
        Random rng = new Random(1);
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        int trials = 3000;
        for (int i = 0; i < trials; i++) {
            Category c = UnlockRotation.pick(pool, new ArrayList<>(), 1, rng).get(0).category;
            counts.merge(c, 1, Integer::sum);
        }
        assertTrue("armour took " + counts.get(Category.ARMOR) + "/" + trials,
                counts.get(Category.ARMOR) < trials * 0.45);
        assertTrue(counts.get(Category.WEAPON) > trials * 0.2);
        assertTrue(counts.get(Category.ITEM) > trials * 0.2);
    }

    @Test
    public void aNeglectedCategoryIsLikelierThanOneJustReceived() {
        List<Candidate> pool = pool(20, 20, 20);
        Random rng = new Random(2);
        // Armour three times running, weapons before that, never an item.
        List<Category> recent = Arrays.asList(Category.ARMOR, Category.ARMOR, Category.ARMOR, Category.WEAPON);
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        int trials = 4000;
        for (int i = 0; i < trials; i++) {
            counts.merge(UnlockRotation.pick(pool, recent, 1, rng).get(0).category, 1, Integer::sum);
        }
        assertTrue(counts.get(Category.ITEM) > counts.get(Category.WEAPON));
        assertTrue(counts.get(Category.WEAPON) > counts.get(Category.ARMOR));
    }

    @Test
    public void nothingIsScriptedEvenARecentCategoryCanStillComeUp() {
        List<Candidate> pool = pool(20, 20, 20);
        Random rng = new Random(3);
        List<Category> recent = Arrays.asList(Category.ARMOR, Category.WEAPON, Category.ITEM);
        boolean armourAgain = false;
        for (int i = 0; i < 500 && !armourAgain; i++) {
            armourAgain = UnlockRotation.pick(pool, recent, 1, rng).get(0).category == Category.ARMOR;
        }
        assertTrue(armourAgain);
    }

    @Test
    public void twoUnlocksInOneRollTendToDiffer() {
        List<Candidate> pool = pool(30, 30, 30);
        Random rng = new Random(4);
        int different = 0;
        int trials = 2000;
        for (int i = 0; i < trials; i++) {
            List<Candidate> two = UnlockRotation.pick(pool, new ArrayList<>(), 2, rng);
            assertNotSame(two.get(0), two.get(1));
            if (two.get(0).category != two.get(1).category) {
                different++;
            }
        }
        // Uniform would differ two thirds of the time; the rotation should beat that.
        assertTrue("only " + different + "/" + trials, different > trials * 0.70);
    }

    @Test
    public void anEmptyCategoryIsSkippedAndAnEmptyPoolYieldsNothing() {
        List<Candidate> onlyItems = pool(0, 0, 5);
        List<Candidate> picked = UnlockRotation.pick(onlyItems, new ArrayList<>(), 3, new Random(5));
        assertEquals(3, picked.size());
        for (Candidate c : picked) {
            assertEquals(Category.ITEM, c.category);
        }
        assertTrue(UnlockRotation.pick(new ArrayList<>(), new ArrayList<>(), 2, new Random(5)).isEmpty());
    }

    @Test
    public void neverMoreThanThePoolHolds() {
        assertEquals(2, UnlockRotation.pick(pool(1, 1, 0), new ArrayList<>(), 5, new Random(6)).size());
    }
}
