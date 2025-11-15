package com.bpm.minotaur.gamedata.monster;

// Note: Fields are public for easy parsing by libGDX Json.
// This class is just a data container.
public class MonsterTemplate {

    // This nested static class must also have public fields or a no-arg constructor
    public static class ScaleData {
        public float x;
        public float y;
    }

    public int warStrength;
    public int spiritualStrength;
    public int armor;
    public int baseExperience;
    public MonsterFamily family; // libGDX Json automatically converts "BEAST" string to MonsterFamily.BEAST
    public String texturePath;
    public String[] spriteData;
    public ScaleData scale;

    // A no-argument constructor is required for the Json parser
    public MonsterTemplate() { }
}
