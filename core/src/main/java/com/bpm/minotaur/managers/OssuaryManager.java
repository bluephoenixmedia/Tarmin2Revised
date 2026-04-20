package com.bpm.minotaur.managers;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.dice.Die;
import com.bpm.minotaur.gamedata.dice.DieFace;
import com.bpm.minotaur.gamedata.dice.BoneTrait;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;

public class OssuaryManager {

    /**
     * Crafts a custom die from 3 bone items.
     * 
     * @param boneStructure The bone determining color and Faces 1 & 6.
     * @param boneEdge      The bone determining Faces 2 & 5.
     * @param boneCore      The bone determining Faces 3 & 4.
     */
    public Die craftBoneDie(Item boneStructure, Item boneEdge, Item boneCore) {
        // 1. Validate Inputs
        if (boneStructure.getType() != Item.ItemType.BONE ||
                boneEdge.getType() != Item.ItemType.BONE ||
                boneCore.getType() != Item.ItemType.BONE) {
            return null; // Invalid ingredients
        }

        // 2. Fetch Traits
        BoneTrait traitStruct = BoneTrait.get(boneStructure.getCorpseSource());
        BoneTrait traitEdge = BoneTrait.get(boneEdge.getCorpseSource());
        BoneTrait traitCore = BoneTrait.get(boneCore.getCorpseSource());

        // 3. Assemble Faces (The 6-sided geometry)
        // Array Order: [1 (Top), 2 (Front), 3 (Right), 4 (Left), 5 (Back), 6 (Bottom)]
        DieFace[] newFaces = new DieFace[6];

        // Slot 1 (Structure) takes Poles (1 & 6) (Indices 0 and 5)
        newFaces[0] = copyFace(traitStruct.faces[0]);
        newFaces[5] = copyFace(traitStruct.faces[1]);

        // Slot 2 (Edge) takes Faces 2 & 5 (Indices 1 and 4)
        newFaces[1] = copyFace(traitEdge.faces[0]);
        newFaces[4] = copyFace(traitEdge.faces[1]);

        // Slot 3 (Core) takes Faces 3 & 4 (Indices 2 and 3)
        newFaces[2] = copyFace(traitCore.faces[0]);
        newFaces[3] = copyFace(traitCore.faces[1]);

        // 4. Determine Color (Visual blending could be added, but for now use
        // Structure)
        Color dieColor = traitStruct.boneColor;

        // 5. Determine Name & Resonance
        String name;
        boolean isResonant = (traitStruct.source == traitEdge.source && traitEdge.source == traitCore.source);

        if (isResonant) {
            name = "Pure " + formatName(traitStruct.source) + " Die";
            // Logic to apply Resonance Buff to the die object would go here
            // e.g. die.setPassiveEffect(traitStruct.resonanceName);
        } else {
            // Hybrid Name: e.g. "Minotaur-Snake-Wraith Die"
            // Or simpler: "Minotaur Composite Die"
            name = formatName(traitStruct.source) + " Composite Die";
        }

        return new Die(name, dieColor, newFaces);
    }

    private DieFace copyFace(DieFace original) {
        return new DieFace(original.getType(), original.getValue(), original.getLabel());
    }

    private String formatName(MonsterType type) {
        return type.name().charAt(0) + type.name().substring(1).toLowerCase().replace('_', ' ');
    }
}
