package com.bpm.minotaur.gamedata.encounters;

import java.util.ArrayList;
import java.util.List;

public class Encounter {
    public String id;
    public String title;
    public String imagePath;
    public String text;
    public int minDepth = 1;
    public int maxDepth = 99;
    public List<EncounterChoice> choices = new ArrayList<>();
}
