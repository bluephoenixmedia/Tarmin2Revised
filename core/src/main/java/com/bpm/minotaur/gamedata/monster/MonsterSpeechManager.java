package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.ui.UiGlyphs;

import java.util.*;

/**
 * Manages native monster languages, phrases, and translations for Comprehend Languages.
 *
 * <p>When Comprehend Languages is active, overhead speech text reveals the exact translation
 * of what the monster is speaking, tagged with their native tongue (e.g. "[Goblin] \"Gut the big walker!\"").
 * Without the spell, the monster barks in their untranslated native dialect (e.g. "[Goblin] \"Grak'nar vor!\"").
 */
public class MonsterSpeechManager {

    public enum Language {
        COMMON("Common"),
        GOBLIN("Goblin"),
        ORCISH("Orcish"),
        DRACONIC("Draconic"),
        DEEP_SPEECH("Deep Speech"),
        NECRIL("Necril"),
        ABYSSAL("Abyssal"),
        GIANT("Giant"),
        DWARVISH("Dwarvish"),
        LYCANTHROPE("Lycanthrope"),
        BEAST_SPEECH("Beast Speech"),
        TERRAN("Terran");

        private final String displayName;

        Language(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class Phrase {
        public final String untranslated;
        public final String translated;

        public Phrase(String untranslated, String translated) {
            this.untranslated = untranslated;
            this.translated = translated;
        }
    }

    private static final Map<Language, List<Phrase>> PHRASES = new EnumMap<>(Language.class);
    private static final Random RNG = new Random();

    static {
        register(Language.GOBLIN,
                new Phrase("Grak'nar vor!", "Surround the flesh-walker!"),
                new Phrase("Skrrk nar zog!", "Stick him with the shiv!"),
                new Phrase("Urzog snark!", "Take his shiny boots!"),
                new Phrase("Zog zog narn!", "More meat for the pot!")
        );

        register(Language.ORCISH,
                new Phrase("Uglak grom kash!", "Crush his skull!"),
                new Phrase("Kash nak dakh!", "Break his bones!"),
                new Phrase("Grommash tor!", "No mercy for the weak!"),
                new Phrase("Dakh urk!", "Tear off his armor!")
        );

        register(Language.DRACONIC,
                new Phrase("Ignis val'drakar!", "Burn to cinders, mortal!"),
                new Phrase("Mor'vash kahl!", "Bow before true majesty!"),
                new Phrase("Veth kal thur!", "Your ashes shall warm my lair!"),
                new Phrase("Zul dras!", "Insolent pest, be gone!")
        );

        register(Language.DEEP_SPEECH,
                new Phrase("Ph'nglui nafh!", "Your thoughts taste sweet..."),
                new Phrase("Xul'nath oog!", "The Void hungers for your mind."),
                new Phrase("Ia mglw khtul!", "Sanity is a fleeting whisper..."),
                new Phrase("Khtul-khtul...", "We shall devour your will.")
        );

        register(Language.NECRIL,
                new Phrase("Kaza-mor mortis...", "Join our cold chorus..."),
                new Phrase("Vraka tuul...", "Flesh rots, but bone endures..."),
                new Phrase("Ash-kuhl mort...", "The crypt hungers for your soul..."),
                new Phrase("Morta vana...", "Embrace the eternal chill...")
        );

        register(Language.ABYSSAL,
                new Phrase("Khaz'goroth az!", "Your agony shall be eternal!"),
                new Phrase("Nul'shadar mor!", "Chaos shall swallow this realm!"),
                new Phrase("Varkul az'goth!", "Bleed for the dark masters!"),
                new Phrase("Bal'a dash!", "A sacrificial offering arrives!")
        );

        register(Language.GIANT,
                new Phrase("Thrum gar!", "I will stomp you into jelly!"),
                new Phrase("Brak thul mor!", "Crunch your bones like bread!"),
                new Phrase("Krugg mor!", "Tiny squishy morsel!"),
                new Phrase("Grumm!", "Smash the little thief!")
        );

        register(Language.DWARVISH,
                new Phrase("Baruk khazad!", "Axes of the mountain, strike!"),
                new Phrase("Dum grimst!", "You entered the wrong hall!"),
                new Phrase("Krag thrum!", "Taste iron and ancient granite!"),
                new Phrase("Zan dar!", "Intruder upon sacred stone!")
        );

        register(Language.LYCANTHROPE,
                new Phrase("*Grrr-snarl!*", "The hunt begins under moonlight!"),
                new Phrase("Harrk-kash!", "Your throat looks so tender..."),
                new Phrase("*Snarl-rip!*", "Flesh... warm, fresh meat!"),
                new Phrase("Ur-kroll!", "The pack claims you!")
        );

        register(Language.BEAST_SPEECH,
                new Phrase("*Hiss-snarl!*", "Intruder! Defend the den!"),
                new Phrase("*Chitter-click!*", "Warm prey detected... strike!"),
                new Phrase("*Low growl...*", "Step back or face teeth and claws!"),
                new Phrase("*Skrrr-snap!*", "Territory invaded... kill!")
        );

        register(Language.TERRAN,
                new Phrase("Klang-gruun...", "Eliminating organic anomaly."),
                new Phrase("Tor-vash...", "Intrusion protocol engaged."),
                new Phrase("Grom-vuhl...", "Stone does not yield."),
                new Phrase("Krun-dar...", "Target identified: hostile.")
        );

        register(Language.COMMON,
                new Phrase("Stand and fight!", "Stand and fight!"),
                new Phrase("You shall not pass!", "You shall not pass!"),
                new Phrase("Yield or fall!", "Yield or fall!"),
                new Phrase("Defend the realm!", "Defend the realm!")
        );
    }

    private static void register(Language language, Phrase... phraseList) {
        PHRASES.put(language, Arrays.asList(phraseList));
    }

    /**
     * Determines the native language spoken by a monster.
     */
    public static Language getLanguage(Monster.MonsterType monsterType) {
        if (monsterType == null) {
            return Language.COMMON;
        }

        switch (monsterType) {
            case DWARF:
                return Language.DWARVISH;

            case KOBOLD:
            case TROGLODYTE:
            case LIZARD_WARRIOR:
            case DRAGON:
            case WYVERN:
            case CHIMERA:
            case HYDRA:
                return Language.DRACONIC;

            case GOBLIN:
            case HOBGOBLIN:
                return Language.GOBLIN;

            case ORC:
                return Language.ORCISH;

            case TROLL:
            case OGRE:
            case GIANT:
                return Language.GIANT;

            case BEHOLDER:
            case MIND_FLAYER:
            case UMBER_HULK:
                return Language.DEEP_SPEECH;

            case DEMON_SLIME:
            case BRINGER_OF_DEATH:
            case FALL_ANGEL:
            case MINOTAUR:
                return Language.ABYSSAL;

            case SKELETON:
            case CLOAKED_SKELETON:
            case ZOMBIE:
            case GHOUL:
            case MUMMY:
            case SPECTER:
            case SKELETAL_WIZARD:
            case LICH:
            case VAMPIRE:
            case WRAITH:
            case GHAST:
            case PLAYER_GHOST:
                return Language.NECRIL;

            case WERERAT:
            case WEREWOLF:
                return Language.LYCANTHROPE;

            case JESTER:
            case SAGE:
                return Language.COMMON;

            case HARPY:
            case GARGOYLE:
            case IRON_GOLEM:
            case GELATINOUS_CUBE:
            case RUST_MONSTER:
            case MIMIC:
            case AGIS:
                return Language.TERRAN;

            case GIANT_ANT:
            case GIANT_SCORPION:
            case GIANT_SNAKE:
            case ALLIGATOR:
            case SPIDER:
            case OWLBEAR:
            case DISPLACER_BEAST:
            case BAT:
            case GIANT_BEE:
            case GIANT_SNAIL:
            case GIANT_CENTIPEDE:
            case LANDSTALKER:
            case COCKATRICE:
            case BEETLESCRATCH:
            case PURPLE_WORM:
            default:
                return Language.BEAST_SPEECH;
        }
    }

    /**
     * Formats an overhead speech string for the given monster, factoring in whether Comprehend Languages is active.
     */
    public static String getSpeechPhrase(Monster monster, boolean comprehendLanguages) {
        if (monster == null) return null;
        Language language = getLanguage(monster.getType());
        List<Phrase> list = PHRASES.get(language);
        if (list == null || list.isEmpty()) {
            list = PHRASES.get(Language.COMMON);
        }

        Phrase phrase = list.get(RNG.nextInt(list.size()));
        String quote = comprehendLanguages ? phrase.translated : phrase.untranslated;

        String formatted = "[" + language.getDisplayName() + "] \"" + quote + "\"";
        return UiGlyphs.sanitize(formatted);
    }
}
