package com.bpm.minotaur.gamedata.history;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A house's authored creed (plan D44): which soldiers it fields, how it looks, what its gash
 * interior becomes, what its lord fights as, and the voice its chroniclers write in.
 * Loaded from {@code data/doctrines.json}.
 */
public class Doctrine {
    public String id;
    public String name;
    public String creed;
    /** Monster types from {@code monsters.json} this doctrine's houses field (plan D27). */
    public List<String> roster = new ArrayList<>();
    /** The monster type a lord of this doctrine is composed on as a seal boss (plan D30). */
    public String bossBase;
    public String primaryColor;
    public String secondaryColor;
    public String interiorTheme;
    /** Chronicler grammar flavour key. */
    public String voice;
    /**
     * {@link Trait} name to relative weight, for lords born to this doctrine. Transient so the
     * reflective reader skips it; {@link DoctrineCatalog} fills it by hand.
     */
    public transient Map<String, Integer> traitWeights = new LinkedHashMap<>();
    public List<String> givenNames = new ArrayList<>();
    public List<String> givenEndings = new ArrayList<>();
    public List<String> houseNames = new ArrayList<>();
    /** Heraldic charges for generated sigils. */
    public List<String> charges = new ArrayList<>();
    public List<String> mottos = new ArrayList<>();
    /** True for a doctrine only one fixed house may hold (Tarmin-Zul's). */
    public boolean unique;
}
