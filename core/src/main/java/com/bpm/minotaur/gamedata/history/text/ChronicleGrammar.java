package com.bpm.minotaur.gamedata.history.text;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.history.CasusBelli;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Turns history events into prose (plan D11, D29) from the handwritten grammar in
 * {@code data/chronicle_grammar.json}. Deterministic: the same event, told by the same side,
 * always reads the same.
 */
public final class ChronicleGrammar {

    public static final String DATA_PATH = "data/chronicle_grammar.json";

    /** Whose side the teller is on, relative to the house that acted. */
    public enum Bias { NEUTRAL, FOR, AGAINST }

    private static ChronicleGrammar instance;

    private final Map<String, Map<Bias, List<String>>> events = new HashMap<>();
    private final Map<CasusBelli, Map<Bias, List<String>>> casus = new EnumMap<>(CasusBelli.class);
    private final Map<String, List<String>> symbols = new HashMap<>();
    private final Map<String, Map<String, List<String>>> voices = new HashMap<>();
    private final List<String> ordinals = new ArrayList<>();

    private ChronicleGrammar() {
    }

    public static synchronized ChronicleGrammar getInstance() {
        if (instance == null) {
            FileHandle file = Gdx.files != null ? Gdx.files.internal(DATA_PATH) : null;
            if (file == null || !file.exists()) {
                throw new IllegalStateException(DATA_PATH + " not found; the chronicle cannot be written.");
            }
            instance = fromJson(file.readString("UTF-8"));
        }
        return instance;
    }

    public static ChronicleGrammar fromJson(String jsonText) {
        ChronicleGrammar g = new ChronicleGrammar();
        JsonValue root = new JsonReader().parse(jsonText);
        for (JsonValue ev : root.get("events")) {
            g.events.put(ev.name, biasLists(ev));
        }
        for (JsonValue cb : root.get("casus")) {
            g.casus.put(CasusBelli.valueOf(cb.name), biasLists(cb));
        }
        for (JsonValue sym : root.get("symbols")) {
            g.symbols.put(sym.name, strings(sym));
        }
        JsonValue voices = root.get("voices");
        if (voices != null) {
            for (JsonValue voice : voices) {
                Map<String, List<String>> m = new HashMap<>();
                for (JsonValue sym : voice) m.put(sym.name, strings(sym));
                g.voices.put(voice.name, m);
            }
        }
        g.ordinals.addAll(strings(root.get("ordinals")));
        return g;
    }

    public int templateCount(String key, Bias bias) {
        Map<Bias, List<String>> m = events.get(key);
        return m == null || !m.containsKey(bias) ? 0 : m.get(bias).size();
    }

    public int casusCount(CasusBelli cb, Bias bias) {
        Map<Bias, List<String>> m = casus.get(cb);
        return m == null || !m.containsKey(bias) ? 0 : m.get(bias).size();
    }

    /** The event as {@code teller} tells it: one or two sentences of ASCII prose. */
    public String render(HistoryWorld world, HistoryEvent e, Chronicler teller) {
        Random rng = new Random(world.seed * 0x9E3779B97F4A7C15L + e.id * 1009L + teller.bias.ordinal() * 7L);
        List<String> options = events.get(templateKey(e)).get(teller.bias);
        String text = expand(options.get(rng.nextInt(options.size())), world, e, teller, rng);
        return capitaliseSentences(text);
    }

    /** The template set for an event; some events read differently in their special cases. */
    static String templateKey(HistoryEvent e) {
        if (e.type == EventType.BATTLE && e.figureB >= 0) return "BATTLE_LORD_SLAIN";
        if (e.type == EventType.VASSAL_OATH && e.detail == 1) return "VASSAL_OATH_FORCED";
        if (e.type == EventType.SEEKER_FELL && e.houseA < 0) return "SEEKER_FELL_UNKNOWN";
        return e.type.name();
    }

    private String expand(String template, HistoryWorld world, HistoryEvent e, Chronicler teller, Random rng) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i);
                out.append(token(template.substring(i + 1, end), world, e, teller, rng));
                i = end + 1;
            } else if (c == '#') {
                int end = template.indexOf('#', i + 1);
                out.append(expand(symbol(template.substring(i + 1, end), teller, rng), world, e, teller, rng));
                i = end + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private String token(String name, HistoryWorld world, HistoryEvent e, Chronicler teller, Random rng) {
        House a = world.house(e.houseA);
        House b = world.house(e.houseB);
        switch (name) {
            case "A": return a != null ? a.name : "a house of the Maze";
            case "B": return b != null ? b.name : "a house of the Maze";
            case "a": return figure(world, world.figure(e.figureA), teller);
            case "b": return figure(world, world.figure(e.figureB), teller);
            case "place": return e.place != null ? e.place : "a field no one names";
            case "gash": return e.gashIndex >= 0 ? world.gashName(e.gashIndex) : "a gash";
            // Chroniclers count from the first year, not the zeroth.
            case "year": return Integer.toString(e.season / HistoryWorld.SEASONS_PER_YEAR + 1);
            case "wordsA": return a != null ? "\"" + a.words + "\"" : "";
            case "sigilA": return a != null ? a.sigil : "a torn banner";
            case "ordinal": return e.detail >= 1 && e.detail <= ordinals.size() ? ordinals.get(e.detail - 1) : e.detail + "th";
            case "cb": {
                if (e.casusBelli == null) return "for reasons of its own";
                List<String> phrases = casus.get(e.casusBelli).get(teller.bias);
                return expand(phrases.get(rng.nextInt(phrases.size())), world, e, teller, rng);
            }
            default:
                throw new IllegalArgumentException("Unknown chronicle token {" + name + "}");
        }
    }

    /** A name, with the epithet this teller would give it; rumours give none. */
    private static String figure(HistoryWorld world, Figure f, Chronicler teller) {
        if (f == null) return "one whose name is lost";
        if (teller.bias == Bias.NEUTRAL) return f.name;
        String epithet = Epithets.of(world, f, teller.houseId);
        return epithet == null ? f.name : f.name + " " + epithet;
    }

    private String symbol(String name, Chronicler teller, Random rng) {
        List<String> base = symbols.getOrDefault(name, Collections.emptyList());
        List<String> voiced = teller.voice != null && voices.containsKey(teller.voice)
                ? voices.get(teller.voice).get(name) : null;
        if (voiced != null && (base.isEmpty() || rng.nextBoolean())) {
            return voiced.get(rng.nextInt(voiced.size()));
        }
        if (base.isEmpty()) throw new IllegalArgumentException("Unknown chronicle symbol #" + name + "#");
        return base.get(rng.nextInt(base.size()));
    }

    static String capitaliseSentences(String s) {
        StringBuilder sb = new StringBuilder(s);
        boolean start = true;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (start && Character.isLetter(c)) {
                sb.setCharAt(i, Character.toUpperCase(c));
                start = false;
            } else if (c == '.' || c == '!' || c == '?') {
                start = true;
            } else if (!Character.isWhitespace(c) && c != '"') {
                start = false;
            }
        }
        return sb.toString();
    }

    private static Map<Bias, List<String>> biasLists(JsonValue node) {
        Map<Bias, List<String>> m = new EnumMap<>(Bias.class);
        for (Bias b : Bias.values()) {
            JsonValue list = node.get(b.name());
            if (list != null) m.put(b, strings(list));
        }
        return m;
    }

    private static List<String> strings(JsonValue list) {
        List<String> out = new ArrayList<>();
        if (list != null) {
            for (JsonValue v : list) out.add(v.asString());
        }
        return out;
    }
}
