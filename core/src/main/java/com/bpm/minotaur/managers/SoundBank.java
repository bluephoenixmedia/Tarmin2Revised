package com.bpm.minotaur.managers;

import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Sound events and their interchangeable variants, read from {@code assets/data/soundbank.json}.
 * Playing the same sword hit forty times in a row is what makes a game sound cheap, so each event
 * holds several recordings and {@link #pick} never returns the one it returned last.
 */
public final class SoundBank {

    public static final class Event {
        public final List<String> files;
        public final float volume;
        public final float pitchJitter;
        private int last = -1;

        Event(List<String> files, float volume, float pitchJitter) {
            this.files = Collections.unmodifiableList(files);
            this.volume = volume;
            this.pitchJitter = pitchJitter;
        }
    }

    private final Map<String, Event> events = new HashMap<>();

    public static SoundBank parse(JsonValue root) {
        SoundBank bank = new SoundBank();
        JsonValue evs = root.get("events");
        for (JsonValue e = evs == null ? null : evs.child; e != null; e = e.next) {
            List<String> files = new ArrayList<>();
            for (JsonValue f = e.get("files").child; f != null; f = f.next) {
                files.add(f.asString());
            }
            if (!files.isEmpty()) {
                bank.events.put(e.name, new Event(files, e.getFloat("volume", 1f), e.getFloat("pitchJitter", 0f)));
            }
        }
        return bank;
    }

    public boolean has(String event) {
        return events.containsKey(event);
    }

    public Event get(String event) {
        return events.get(event);
    }

    public Iterable<String> eventNames() {
        return events.keySet();
    }

    /** A variant of the event other than the last one picked (when it has more than one), or null if unknown. */
    public String pick(String event, Random random) {
        Event e = events.get(event);
        if (e == null) {
            return null;
        }
        int n = e.files.size();
        int i = random.nextInt(n);
        if (n > 1 && i == e.last) {
            i = (i + 1 + random.nextInt(n - 1)) % n;
        }
        e.last = i;
        return e.files.get(i);
    }
}
