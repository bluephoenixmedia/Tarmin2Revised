package com.bpm.minotaur.gamedata.dice;

import com.badlogic.gdx.graphics.Color;
import java.util.List;

/**
 * A definition used in ItemTemplates to generate Dice instances.
 * This is useful for parsing from JSON or defining static data.
 */
public class DieDefinition {
    public String name;
    public String colorHex; // e.g. "#FF0000"

    // Simple way to define 6 faces: pairs of [Type, Value]
    // Or a list of simplified objects.
    // For now, let's assume a helper method parses this or we use a builder.

    // We can store a list of faces.
    public List<DieFaceDefinition> faces;

    public static class DieFaceDefinition {
        public DieFaceType type;
        public int value;
        public String label;

        public DieFaceDefinition() {
        }

        public DieFaceDefinition(DieFaceType type, int value) {
            this.type = type;
            this.value = value;
        }
    }

    public Die createDie() {
        Color c = Color.WHITE;
        if (colorHex != null) {
            try {
                c = Color.valueOf(colorHex);
            } catch (Exception e) {
                // Keep white
            }
        }

        DieFace[] faceArray = new DieFace[6];
        if (faces != null) {
            for (int i = 0; i < Math.min(faces.size(), 6); i++) {
                DieFaceDefinition def = faces.get(i);
                faceArray[i] = new DieFace(def.type, def.value, def.label);
            }
        }

        // Fill remainder with blanks inside the constructor logic if null passed,
        // but here we might pass nulls in the array.
        // Let's rely on Die constructor to fill blanks if we pass fewer.

        // Actually, let's be safe and fill blanks here.
        for (int i = 0; i < 6; i++) {
            if (faceArray[i] == null) {
                faceArray[i] = new DieFace(DieFaceType.BLANK, 0);
            }
        }

        return new Die(name, c, faceArray);
    }
}
