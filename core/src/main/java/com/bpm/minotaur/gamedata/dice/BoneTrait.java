package com.bpm.minotaur.gamedata.dice;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import java.util.HashMap;
import java.util.Map;

public class BoneTrait {
        public MonsterType source;
        public Color boneColor;
        public DieFace[] faces; // Array of 2 faces provided by this bone per slot
        public String resonanceName; // Name of effect if you match 3

        public BoneTrait(MonsterType source, Color color, DieFace faceA, DieFace faceB, String resonance) {
                this.source = source;
                this.boneColor = color;
                this.faces = new DieFace[] { faceA, faceB };
                this.resonanceName = resonance;
        }

        // --- STATIC REGISTRY ---
        private static final Map<MonsterType, BoneTrait> REGISTRY = new HashMap<>();

        static {
                // MINOTAUR: High Physical Damage / Bleed
                REGISTRY.put(MonsterType.MINOTAUR, new BoneTrait(
                                MonsterType.MINOTAUR,
                                Color.MAROON,
                                new DieFace(DieFaceType.SWORD, 3, "Gore"), // High Dmg
                                new DieFace(DieFaceType.SKULL, 1, "Stun"), // CC
                                "Bull's Rage" // Resonance: +1 to all SWORD faces
                ));

                // GIANT_SNAKE: Poison / Debuffs
                REGISTRY.put(MonsterType.GIANT_SNAKE, new BoneTrait(
                                MonsterType.GIANT_SNAKE,
                                Color.CHARTREUSE,
                                new DieFace(DieFaceType.POISON, 2, "Venom"),
                                new DieFace(DieFaceType.BLANK, 0, "Miss"), // Risk factor
                                "Toxic Coating" // Resonance: Poison stacks do not decay for 1 turn
                ));

                // WRAITH: Spiritual / Cursed
                REGISTRY.put(MonsterType.WRAITH, new BoneTrait(
                                MonsterType.WRAITH,
                                Color.PURPLE,
                                new DieFace(DieFaceType.LIGHTNING, 2, "Spirit Bolt"),
                                new DieFace(DieFaceType.HEART, -1, "Drain Life"), // Self-damage for power?
                                "Ethereal Form" // Resonance: 20% Dodge Chance while holding die
                ));

                // SKELETON: Basic / Defense
                REGISTRY.put(MonsterType.SKELETON, new BoneTrait(
                                MonsterType.SKELETON,
                                Color.LIGHT_GRAY,
                                new DieFace(DieFaceType.SHIELD, 1, "Block"),
                                new DieFace(DieFaceType.SWORD, 1, "Bone Chip"),
                                "Calcium Fortification" // Resonance: Start combat with 2 Block
                ));

                // GOBLIN: Chaotic / Greedy
                REGISTRY.put(MonsterType.GOBLIN, new BoneTrait(
                                MonsterType.GOBLIN,
                                Color.OLIVE,
                                new DieFace(DieFaceType.GOLD, 10, "Steal"),
                                new DieFace(DieFaceType.SWORD, 1, "Stab"),
                                "Pack Tactics" // Resonance: +1 Damage if you have another Goblin Die
                ));

                // GIANT ANT: Swarm / Defense
                REGISTRY.put(MonsterType.GIANT_ANT, new BoneTrait(
                                MonsterType.GIANT_ANT,
                                Color.BROWN,
                                new DieFace(DieFaceType.SHIELD, 2, "Chitin"),
                                new DieFace(DieFaceType.SWORD, 1, "Bite"),
                                "Hive Mind" // Resonance: +1 Block for every Ant die
                ));

                // ZOMBIE: Resilience / Infection
                REGISTRY.put(MonsterType.ZOMBIE, new BoneTrait(
                                MonsterType.ZOMBIE,
                                new Color(0.4f, 0.5f, 0.4f, 1f), // Rotting Green
                                new DieFace(DieFaceType.HEART, 1, "Eat Brain"),
                                new DieFace(DieFaceType.POISON, 1, "Rot"),
                                "Undying" // Resonance: Survive fatal damage once per combat
                ));
        }

        public static BoneTrait get(MonsterType type) {
                return REGISTRY.getOrDefault(type, REGISTRY.get(MonsterType.SKELETON)); // Fallback
        }
}
