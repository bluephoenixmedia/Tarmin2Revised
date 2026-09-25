package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Every {@code color} in the item data must name a real {@link ItemColor}.
 *
 * <p>A colour that does not resolve is not a soft failure: libGDX's Json throws
 * SerializationException, `ItemDataManager.loadArmor` propagates it out of
 * `Tarmin2.create`, and the game dies before reaching a screen. A single typo in
 * one armour variant took the whole build down that way, which is far too quiet
 * a failure for a one-word mistake in a data file.
 */
public class ItemDataColorIntegrityTest {

    private static final String[] DATA_FILES = {
            "data/armor.json",
            "data/items.json",
            "data/weapons.json"
    };

    private static File asset(String relativePath) {
        File f = new File("assets/" + relativePath);
        if (!f.exists()) {
            f = new File("../assets/" + relativePath);
        }
        return f;
    }

    @Test
    public void everyColourInTheItemDataResolvesToAnItemColor() {
        List<String> offenders = new ArrayList<>();

        for (String path : DATA_FILES) {
            File file = asset(path);
            if (!file.exists()) continue;

            JsonValue root = new JsonReader().parse(new com.badlogic.gdx.files.FileHandle(file));
            collectBadColours(root, path, "", offenders);
        }

        assertTrue("Item data names colours that ItemColor does not define, which"
                        + " crashes the game at startup:\n  " + String.join("\n  ", offenders),
                offenders.isEmpty());
    }

    private void collectBadColours(JsonValue node, String file, String path, List<String> offenders) {
        if (node == null) return;

        for (JsonValue child = node.child; child != null; child = child.next) {
            String childPath = child.name != null ? path + "/" + child.name : path + "[]";

            if ("color".equals(child.name) && child.isString()) {
                String value = child.asString();
                if (!isKnownColour(value)) {
                    offenders.add(file + childPath + " = \"" + value + "\"");
                }
            }
            collectBadColours(child, file, childPath, offenders);
        }
    }

    private boolean isKnownColour(String name) {
        for (ItemColor colour : ItemColor.values()) {
            if (colour.name().equals(name)) return true;
        }
        return false;
    }
}
