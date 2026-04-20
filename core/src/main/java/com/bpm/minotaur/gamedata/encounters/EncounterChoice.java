package com.bpm.minotaur.gamedata.encounters;

public class EncounterChoice {
    public enum CheckType {
        LEAVE, RISK_REWARD, CHECK, TRADE, COMBAT
    }

    public static class Requirement {
        public String stat; // "PERCEPTION", "SPIRITUAL_STRENGTH", "DEXTERITY"
        public int value;
    }

    public static class Cost {
        public String type; // "MAX_HP", "HP", "GOLD"
        public int amount;
    }

    public String text;
    public CheckType type = CheckType.LEAVE;
    public float successChance = 1.0f; // For RISK_REWARD

    public Requirement requirement;
    public Cost cost;

    public EncounterResult successEffect;
    public EncounterResult failEffect;

    // For simple combat trigger
    public String monsterId;
}
