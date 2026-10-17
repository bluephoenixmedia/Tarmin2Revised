package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.MagicResistance;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The roster lives in data, and a monster that is half-registered fails at spawn time, not at
 * startup: a missing template throws, a missing sprite spawns an invisible monster, a variant whose
 * colour repeats silently loses its sprite. These check the data the way the game will read it.
 */
public class MonsterRosterDataTest {

    private static JsonValue monsters;
    private static JsonValue spawns;
    private static Set<String> spellIds;

    private static File data(String name) {
        File f = new File("../assets/data/" + name);
        return f.exists() ? f : new File("assets/data/" + name);
    }

    private static File asset(String path) {
        File f = new File("../assets/" + path);
        return f.exists() ? f : new File("assets/" + path);
    }

    @BeforeClass
    public static void load() throws Exception {
        monsters = new JsonReader().parse(new FileReader(data("monsters.json")));
        spawns = new JsonReader().parse(new FileReader(data("spawntables.json")));
        spellIds = new HashSet<>();
        for (JsonValue s = new JsonReader().parse(new FileReader(data("spells.json"))).child; s != null; s = s.next) {
            spellIds.add(s.name);
        }
    }

    @Test
    public void everyMonsterTypeHasATemplate() {
        for (Monster.MonsterType type : Monster.MonsterType.values()) {
            if (type == Monster.MonsterType.PLAYER_GHOST) {
                continue; // built from a saved player, not from data
            }
            assertNotNull(type + " has no entry in monsters.json", monsters.get(type.name()));
        }
    }

    @Test
    public void everySpriteFileExists() {
        for (JsonValue m = monsters.child; m != null; m = m.next) {
            String path = m.getString("texturePath", null);
            if (path != null && !path.isEmpty() && m.get("directionTextures") == null) {
                assertTrue(m.name + " sprite missing: " + path, asset(path).exists());
            }
            JsonValue variants = m.get("variants");
            for (JsonValue v = variants == null ? null : variants.child; v != null; v = v.next) {
                String own = v.getString("texturePath", null);
                if (own != null) {
                    assertTrue(m.name + " variant sprite missing: " + own, asset(own).exists());
                }
            }
        }
    }

    @Test
    public void retroSpriteGridsAreRectangular() {
        // The renderer reads the row and column counts from the data (ORC and GHAST are 25 rows tall),
        // so the grid only has to be rectangular and non-empty.
        for (JsonValue m = monsters.child; m != null; m = m.next) {
            JsonValue grid = m.get("spriteData");
            if (grid == null) {
                continue;
            }
            assertTrue(m.name + " has no rows", grid.size > 0);
            int width = grid.child.asString().length();
            assertTrue(m.name + " has empty rows", width > 0);
            for (JsonValue row = grid.child; row != null; row = row.next) {
                assertEquals(m.name + " row width", width, row.asString().length());
            }
        }
    }

    @Test
    public void theGeneratedBatchHasTwentyFourByTwentyFourSilhouettes() {
        for (String name : new String[] { "BAT", "GIANT_BEE", "GIANT_SNAIL", "GIANT_CENTIPEDE", "LANDSTALKER",
                "COCKATRICE", "LIZARD_WARRIOR", "BEETLESCRATCH", "JESTER", "SPECTER", "SAGE", "SKELETAL_WIZARD" }) {
            JsonValue grid = monsters.get(name).get("spriteData");
            assertEquals(name + " rows", 24, grid.size);
            boolean anySolid = false;
            for (JsonValue row = grid.child; row != null; row = row.next) {
                assertEquals(name + " width", 24, row.asString().length());
                anySolid |= row.asString().indexOf('#') >= 0;
            }
            assertTrue(name + " silhouette is empty", anySolid);
        }
    }

    @Test
    public void variantsHaveDistinctColoursAndSaneWindows() {
        for (JsonValue m = monsters.child; m != null; m = m.next) {
            JsonValue variants = m.get("variants");
            assertNotNull(m.name + " has no variants", variants);
            assertTrue(m.name + " has no variants", variants.size > 0);
            Set<String> colours = new HashSet<>();
            for (JsonValue v = variants.child; v != null; v = v.next) {
                String colour = v.getString("color");
                MonsterColor.valueOf(colour);
                assertTrue(m.name + " repeats colour " + colour, colours.add(colour));
                assertTrue(m.name + " window", v.getInt("minLevel") <= v.getInt("maxLevel"));
                assertTrue(m.name + " weight", v.getInt("weight") > 0);
            }
        }
    }

    @Test
    public void numbersStayInTheirRanges() {
        for (JsonValue m = monsters.child; m != null; m = m.next) {
            int mr = m.getInt("magicResistance", 0);
            assertTrue(m.name + " magic resistance " + mr, mr >= 0 && mr <= MagicResistance.MONSTER_CAP);
            assertTrue(m.name + " HP", m.getInt("maxHP") > 0);
            assertTrue(m.name + " level", m.getInt("baseLevel") >= 1);
            MonsterFamily.valueOf(m.getString("family", "NONE"));
            if (m.has("damageType")) {
                DamageType.valueOf(m.getString("damageType"));
            }
            if (m.has("rangedDamageType")) {
                DamageType.valueOf(m.getString("rangedDamageType"));
            }
        }
    }

    @Test
    public void everySpellAndEffectReferencedExists() {
        for (JsonValue m = monsters.child; m != null; m = m.next) {
            checkSpells(m.name, m.get("innateSpells"));
            JsonValue variants = m.get("variants");
            for (JsonValue v = variants == null ? null : variants.child; v != null; v = v.next) {
                checkSpells(m.name + "/" + v.getString("color"), v.get("innateSpells"));
                if (v.has("rangedDamageType")) {
                    DamageType.valueOf(v.getString("rangedDamageType"));
                }
            }
            JsonValue effects = m.get("onHitEffects");
            for (JsonValue e = effects == null ? null : effects.child; e != null; e = e.next) {
                StatusEffectType.valueOf(e.getString("type"));
            }
        }
    }

    private static void checkSpells(String who, JsonValue list) {
        for (JsonValue s = list == null ? null : list.child; s != null; s = s.next) {
            assertTrue(who + " casts unknown spell " + s.asString(), spellIds.contains(s.asString()));
        }
    }

    @Test
    public void everyTypeInTheSpawnTableIsARealMonster() {
        Set<String> real = new HashSet<>();
        for (Monster.MonsterType t : Monster.MonsterType.values()) {
            real.add(t.name());
        }
        for (JsonValue e = spawns.get("monsterSpawnTable").child; e != null; e = e.next) {
            assertTrue("spawn table names " + e.getString("type"), real.contains(e.getString("type")));
            assertTrue(e.getInt("minLevel") <= e.getInt("maxLevel"));
            assertTrue(e.getInt("weight") > 0);
        }
    }

    @Test
    public void everyEntryParsesIntoATemplateTheWayTheGameReadsIt() {
        // MonsterDataManager.load does exactly this; a wrongly typed field would otherwise surface at boot.
        com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
        for (Monster.MonsterType type : Monster.MonsterType.values()) {
            JsonValue data = monsters.get(type.name());
            if (data == null) {
                continue;
            }
            MonsterTemplate template = json.readValue(MonsterTemplate.class, data);
            assertNotNull(type + " variants", template.variants);
            for (MonsterVariant v : template.variants) {
                assertNotNull(type + " variant colour", v.color);
            }
        }
        MonsterTemplate beetle = json.readValue(MonsterTemplate.class, monsters.get("BEETLESCRATCH"));
        MonsterVariant fire = MonsterVariant.forColor(beetle.variants, MonsterColor.RED);
        assertEquals(DamageType.FIRE, fire.rangedDamageType);
        assertEquals("FIREBALL", fire.rangedProjectile);
        MonsterTemplate specter = json.readValue(MonsterTemplate.class, monsters.get("SPECTER"));
        assertTrue(MonsterVariant.forColor(specter.variants, MonsterColor.BLUE).innateSpells.contains("RAY_OF_FROST"));
        assertTrue(MonsterVariant.textureFor(specter.variants, MonsterColor.ORANGE, "x").endsWith("specter_fire.png"));
    }

    @Test
    public void theColourwaySpeciesGiveEachColourwayItsOwnSprite() {
        for (String species : new String[] { "BEETLESCRATCH", "SPECTER", "SAGE" }) {
            Set<String> sprites = new HashSet<>();
            for (JsonValue v = monsters.get(species).get("variants").child; v != null; v = v.next) {
                String own = v.getString("texturePath", null);
                assertNotNull(species + " variant " + v.getString("color") + " has no sprite of its own", own);
                assertTrue(species + " shares a sprite: " + own, sprites.add(own));
            }
            assertTrue(species + " should have several colourways", sprites.size() >= 4);
        }
    }
}
