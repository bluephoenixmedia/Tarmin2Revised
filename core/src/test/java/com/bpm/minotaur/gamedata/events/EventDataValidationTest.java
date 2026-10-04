package com.bpm.minotaur.gamedata.events;

import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.injury.BodyPart;
import com.bpm.minotaur.gamedata.injury.InjuryType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Holds {@code assets/data/events.json} to the rules in docs/DEsign/events.md, so a typo in the
 * data fails here rather than as a silently dead button in the game.
 */
public class EventDataValidationTest {

    private static final Set<String> ATTRIBUTES = new HashSet<>(Arrays.asList("STR", "DEX", "CON", "INT", "WIS", "AGI", "CHA"));
    private static final String[] PLACEABLE_BIOMES = {"MAZE", "FOREST", "DESERT", "LAKELANDS"};

    private static File assets;
    private static EventCatalog catalog;
    private static String itemData;
    private static String monsterData;

    @BeforeClass
    public static void load() throws IOException {
        // Tests run from core/, which has a small assets/ of its own; the game's is one level up.
        assets = new File("assets/data/monsters.json").isFile() ? new File("assets") : new File("../assets");
        catalog = EventCatalog.fromJson(read("data/events.json"));
        itemData = read("data/items.json") + read("data/weapons.json") + read("data/armor.json");
        monsterData = read("data/monsters.json");
    }

    @Test
    public void theFirstBatchIsAllThere() {
        assertEquals(14, catalog.all().size());
    }

    @Test
    public void everyEventIsPlaceableSomewhere() {
        Set<String> biomes = names(Biome.values());
        for (EventDefinition e : catalog.all()) {
            assertFalse(e.id + " has no biomes", e.biomes.isEmpty());
            for (String b : e.biomes) {
                assertTrue(e.id + " names unknown biome " + b, biomes.contains(b));
            }
            assertTrue(e.id + " has an empty depth range", e.minDepth <= e.maxDepth && e.minDepth >= 1);
            assertTrue(e.id + " has no weight", e.weight > 0);
        }
    }

    @Test
    public void everyEventHasTitleTextAndAWayOut() {
        for (EventDefinition e : catalog.all()) {
            assertTrue(e.id + " needs a title", e.title != null && !e.title.isEmpty());
            assertTrue(e.id + " needs text", e.text != null && !e.text.isEmpty());
            assertTrue(e.id + " needs choices", e.choices.size() >= 2);
            boolean wayOut = false;
            for (EventChoice c : e.choices) {
                wayOut |= c.isUngated();
            }
            assertTrue(e.id + " needs a choice with no gate and no cost", wayOut);
        }
    }

    @Test
    public void everyGateIsWellFormed() {
        for (EventDefinition e : catalog.all()) {
            for (EventChoice c : e.choices) {
                for (EventGate g : c.requires) {
                    String where = e.id + " / " + c.text;
                    assertTrue(where + ": gate needs a type", g.type != null);
                    assertTrue(where + ": gate needs a label", g.label != null && !g.label.isEmpty());
                    if (g.type == EventGate.Type.ATTRIBUTE_MIN) {
                        assertTrue(where + ": bad attribute " + g.attribute, ATTRIBUTES.contains(g.attribute));
                    }
                    if (g.type == EventGate.Type.HAS_ITEM) {
                        checkItemRef(where, g.itemKind, g.itemId);
                    }
                }
                if (c.check != null && c.check.attribute != null) {
                    assertTrue(e.id + ": bad check attribute " + c.check.attribute, ATTRIBUTES.contains(c.check.attribute));
                }
            }
        }
    }

    @Test
    public void everyOutcomeReferencesRealThings() {
        for (EventDefinition e : catalog.all()) {
            for (EventChoice c : e.choices) {
                for (EventOutcome o : outcomes(c)) {
                    String where = e.id + " / " + c.text + " / " + o.type;
                    if (o.type == null) {
                        assertTrue(where + ": an outcome with no type must at least say something",
                                o.text != null && !o.text.isEmpty());
                        continue;
                    }
                    switch (o.type) {
                        case ATTRIBUTE:
                            assertTrue(where + ": bad attribute", ATTRIBUTES.contains(o.attribute));
                            break;
                        case ADD_STATUS:
                        case CURE_STATUS:
                            StatusEffectType.valueOf(o.status);
                            break;
                        case INJURY:
                            BodyPart.valueOf(o.bodyPart);
                            InjuryType.valueOf(o.injury);
                            break;
                        case GIVE_ITEM: {
                            List<String> ids = new ArrayList<>(o.itemPool);
                            if (o.itemId != null) {
                                ids.add(o.itemId);
                            }
                            assertFalse(where + ": nothing to give", ids.isEmpty());
                            for (String id : ids) {
                                checkItemType(where, id);
                            }
                            break;
                        }
                        case TAKE_ITEM:
                        case CURSE_ITEM:
                        case BLESS_ITEM:
                            checkItemRef(where, o.itemKind, o.itemId);
                            break;
                        case SPAWN_MONSTER:
                            Monster.MonsterType.valueOf(o.monsterId);
                            assertTrue(where + ": " + o.monsterId + " has no monster data",
                                    monsterData.contains("\"" + o.monsterId + "\""));
                            break;
                        case CHAIN_EVENT:
                            assertTrue(where + ": chains to unknown event " + o.eventId, catalog.get(o.eventId) != null);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    @Test
    public void everyStringSurvivesTheFont() {
        for (EventDefinition e : catalog.all()) {
            checkGlyphs(e.id + " title", e.title);
            checkGlyphs(e.id + " text", e.text);
            for (EventChoice c : e.choices) {
                checkGlyphs(e.id + " choice", c.text);
                for (EventGate g : c.requires) {
                    checkGlyphs(e.id + " gate label", g.label);
                }
                for (EventOutcome o : outcomes(c)) {
                    checkGlyphs(e.id + " outcome text", o.text);
                }
            }
        }
    }

    @Test
    public void everyImageAndBiomeBackdropExists() {
        for (String biome : PLACEABLE_BIOMES) {
            assertTrue("missing placeholder for " + biome, asset(EventArt.placeholderFor(biome)).isFile());
        }
        for (String biome : PLACEABLE_BIOMES) {
            String backdrop = EventArt.background(new EventDefinition(), biome, p -> asset(p).isFile());
            assertTrue("missing backdrop for " + biome, backdrop != null);
        }
        for (EventDefinition e : catalog.all()) {
            assertTrue(e.id + " needs an imagePath", e.imagePath != null);
            assertTrue(e.id + ": " + e.imagePath + " does not exist", asset(e.imagePath).isFile());
            for (String biome : e.biomes) {
                assertTrue(e.id + " can only appear in placeable biomes", Arrays.asList(PLACEABLE_BIOMES).contains(biome));
            }
        }
    }

    // --- Helpers -------------------------------------------------------------------------------

    private static List<EventOutcome> outcomes(EventChoice c) {
        List<EventOutcome> all = new ArrayList<>(c.costs);
        all.addAll(c.success);
        all.addAll(c.failure);
        return all;
    }

    private static void checkItemRef(String where, String kind, String itemId) {
        if (itemId != null) {
            checkItemType(where, itemId);
        } else if (kind != null) {
            ItemKind.valueOf(kind);
        }
    }

    private static void checkItemType(String where, String id) {
        Item.ItemType.valueOf(id);
        assertTrue(where + ": " + id + " has no item data", itemData.contains("\"" + id + "\""));
    }

    private static void checkGlyphs(String where, String s) {
        if (s != null) {
            int bad = UiGlyphs.firstUnsupportedIndex(s);
            assertTrue(where + " has a character the font cannot draw at " + bad + ": " + s, bad < 0);
        }
    }

    private static Set<String> names(Enum<?>[] values) {
        Set<String> out = new HashSet<>();
        for (Enum<?> v : values) {
            out.add(v.name());
        }
        return out;
    }

    private static File asset(String path) {
        return new File(assets, path);
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(asset(path).toPath()), StandardCharsets.UTF_8);
    }
}
