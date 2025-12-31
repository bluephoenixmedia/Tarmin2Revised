package com.bpm.minotaur.gamedata.encounters;

public class EncounterResult {
    public enum Type {
        HEAL, DAMAGE, POISON, GIVE_ITEM, GIVE_XP, SPAWN_MONSTER, TELEPORT, NONE
    }

    public Type type = Type.NONE;
    public int amount;
    public String itemId;
    public String text;
    public String monsterId;
}
