package com.bpm.minotaur.gamedata.dice;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a physical Die in the player's pool.
 */
public class Die {
    private final List<DieFace> faces;
    private Color dieColor;
    private String name; // e.g. "Iron Die", "Ruby Die"

    public Die(String name, Color color, DieFace... loadedFaces) {
        this.name = name;
        this.dieColor = color;
        this.faces = new ArrayList<>();
        if (loadedFaces != null) {
            this.faces.addAll(Arrays.asList(loadedFaces));
        }
        // Ensure strictly 6 faces for a standard cube?
        // For roguelite flexibility, we might allow more or less, but let's aim for 6
        // for now.
        while (this.faces.size() < 6) {
            this.faces.add(new DieFace(DieFaceType.BLANK, 0));
        }
    }

    public DieFace getFace(int index) {
        if (index < 0 || index >= faces.size())
            return faces.get(0);
        return faces.get(index);
    }

    public List<DieFace> getFaces() {
        return faces;
    }

    public Color getDieColor() {
        return dieColor;
    }

    public String getName() {
        return name;
    }
}
