package com.bpm.minotaur.gamedata.events;

import java.util.ArrayList;
import java.util.List;

/** One effect of a choice. Which fields matter depends on {@link #type}; see docs/DEsign/events.md. */
public class EventOutcome {
    public enum Type {
        HEAL, DAMAGE, RESTORE_MP, DRAIN_MP, MAX_HP, MAX_MP, ATTRIBUTE, LUCK,
        ADD_STATUS, CURE_STATUS, INJURY,
        GIVE_ITEM, TAKE_ITEM, GOLD, SATIETY, HYDRATION, XP,
        CURSE_ITEM, BLESS_ITEM, IDENTIFY_ALL,
        SPAWN_MONSTER, REVEAL_MAP, CHAIN_EVENT
    }

    /** Null for a line of narration with no effect. */
    public Type type;
    public int amount;
    /** For ATTRIBUTE: one of STR, DEX, CON, INT, WIS, AGI, CHA. */
    public String attribute;

    /** A {@link com.bpm.minotaur.gamedata.effects.StatusEffectType} name. */
    public String status;
    public int duration = 10;
    public int potency = 1;

    /** {@link com.bpm.minotaur.gamedata.injury.BodyPart} and {@link com.bpm.minotaur.gamedata.injury.InjuryType} names. */
    public String bodyPart;
    public String injury;
    public int severity = 1;

    /** An item type name; or {@link #itemPool} to pick one at random. */
    public String itemId;
    public List<String> itemPool = new ArrayList<>();
    /** For TAKE_ITEM, CURSE_ITEM and BLESS_ITEM: an {@link ItemKind} name. TAKE_ITEM may name {@link #itemId} instead. */
    public String itemKind;
    public int count = 1;

    /** A {@link com.bpm.minotaur.gamedata.monster.Monster.MonsterType} name. */
    public String monsterId;
    /** For CHAIN_EVENT: the id of the event to open next. */
    public String eventId;

    /** Posted to the message log when the outcome applies. */
    public String text;
}
