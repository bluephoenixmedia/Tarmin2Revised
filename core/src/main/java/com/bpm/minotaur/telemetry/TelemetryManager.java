package com.bpm.minotaur.telemetry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Collects gameplay metrics for balance analysis, one file per Expedition.
 *
 * <p>A Life runs from a respawn (or new game) to the next death ({@link #startNewRun()}
 * opens one). An Expedition is one trip out from the Shelter: it begins on the first turn
 * the player stands outside the shelter and ends in a return ({@code returned}), a death
 * ({@code died}) or a quit to the menu ({@code abandoned}). Each one is written to
 * {@code logs/telemetry/expedition_<timestamp>.json} with a summary line appended to
 * {@code logs/game_balance_session.log}. See {@code tools/telemetry/aggregate.py}.
 *
 * <p>The scalar counters ({@link Counter}) are kept for the whole Life, because the death
 * screen and the unlock roll read them; an expedition file reports how much each one moved
 * since the expedition began.
 */
public class TelemetryManager {

    /** Bumped whenever the file layout changes, so the script can tell old files apart. */
    public static final int SCHEMA_VERSION = 2;

    /** A fight with no attack either way for this many turns is closed as disengaged. */
    public static final int DISENGAGE_TURNS = 5;

    public enum HitType { HIT, GLANCING, MISS }

    /** How the player attacked; hit rate is reported per mode. */
    public enum AttackMode { MELEE, RANGED, THROWN, FIREARM, SPELL, BOOK }

    /** How an expedition ended. */
    public enum Outcome { RETURNED, DIED, ABANDONED }

    /**
     * Told when an expedition is about to close, while its counters still describe it: the
     * unlock roll on a return reads them here (#184), and what it records lands in the file.
     */
    public interface ExpeditionEndListener {
        void onExpeditionEnding(Outcome outcome);
    }

    /** Scalar counters, kept for the whole Life. The JSON key is the camel-cased name. */
    private enum Counter {
        ATTACKS_MADE("attacksMade"), ATTACKS_HIT("attacksHit"), ATTACKS_GLANCED("attacksGlanced"),
        ATTACKS_MISSED("attacksMissed"), DAMAGE_DEALT("damageDealt"), DAMAGE_TAKEN("damageTaken"),
        DAMAGE_MITIGATED("damageMitigatedByArmor"), FOOD("foodConsumed"), WATER("waterConsumed"),
        POTIONS("potionsQuaffed"), SCROLLS("scrollsRead"), DIVINITIES_EARNED("divinitiesEarned"),
        DIVINITIES_SPENT("divinitiesSpent"), INJURIES("injuriesSustained"),
        MERCHANT_FF_DAMAGE("merchantFriendlyFireDamage"), MERCHANT_CLIPS("merchantClips"),
        HP_VENGEANCE("hpVengeance"), KILLS("kills"), HP_REGENERATED("hpRegenerated"),
        MP_REGENERATED("mpRegenerated"), GOLD_GAINED("goldGained"), GOLD_SPENT("goldSpent"),
        XP_GAINED("xpGained"), ITEMS_PICKED_UP("itemsPickedUp"), UNLOCKS_EARNED("unlocksEarned"),
        TURNS_HUNGRY("turnsHungry"), TURNS_THIRSTY("turnsThirsty"), TURNS_COLD("turnsCold"),
        TURNS_POISONED("turnsPoisoned");

        final String key;

        Counter(String key) {
            this.key = key;
        }
    }

    /** One player-versus-monster engagement. */
    private static final class Fight {
        final String monsterType;
        final int monsterLevel;
        final int depth;
        final int openTurn;
        int lastTurn;
        int playerAttacks;
        int playerHits;
        int monsterAttacks;
        int monsterHits;
        long damageDealt;
        long damageTaken;
        final EnumSet<AttackMode> modes = EnumSet.noneOf(AttackMode.class);
        String outcome;
        int closeTurn;

        Fight(String monsterType, int monsterLevel, int depth, int turn) {
            this.monsterType = monsterType;
            this.monsterLevel = monsterLevel;
            this.depth = depth;
            this.openTurn = turn;
            this.lastTurn = turn;
        }
    }

    private static TelemetryManager instance;

    public static synchronized TelemetryManager getInstance() {
        if (instance == null) {
            instance = new TelemetryManager();
        }
        return instance;
    }

    // ---- Life ----
    private String runId;
    private long runStartMillis;
    private int turnsLived;
    private int strataReached;
    private String causeOfDeath;
    private String killerMonster;
    private int killerHouse = -1;
    private int bleedDamageTaken;
    private final long[] life = new long[Counter.values().length];

    // ---- Context, set by the game screen ----
    private String difficulty = "UNKNOWN";
    private long worldSeed;
    private String build = "unknown";
    private boolean cheatsUsed;

    // ---- Expedition ----
    private boolean expeditionActive;
    private long expeditionStartMillis;
    private int expeditionStartTurn;
    private final long[] expeditionStart = new long[Counter.values().length];
    private int bleedAtExpeditionStart;
    private int expeditionMaxDepth;
    private int hpStart = -1, mpStart = -1, hpLast = -1, mpLast = -1;
    private final Map<String, Integer> killsByType = new TreeMap<>();
    private final Map<String, Integer> itemsByCategoryAndDepth = new TreeMap<>();
    private final Map<AttackMode, long[]> attacksByMode = new EnumMap<>(AttackMode.class);
    private final Map<Integer, Integer> depthFirstReachedTurn = new TreeMap<>();
    private final Map<Integer, Integer> encountersByDepth = new TreeMap<>();
    private final List<Integer> levelUpTurns = new ArrayList<>();
    private final Map<Object, Fight> openFights = new IdentityHashMap<>();
    private final List<Fight> closedFights = new ArrayList<>();
    private int currentDepth = 1;
    private ExpeditionEndListener expeditionEndListener;

    private TelemetryManager() {
        startNewRun();
    }

    /** Starts a new Life: every counter is cleared and no expedition is open. */
    public synchronized void startNewRun() {
        this.runId = UUID.randomUUID().toString();
        this.runStartMillis = System.currentTimeMillis();
        this.turnsLived = 0;
        this.strataReached = 1;
        this.causeOfDeath = null;
        this.killerMonster = null;
        this.killerHouse = -1;
        this.bleedDamageTaken = 0;
        java.util.Arrays.fill(life, 0L);
        this.cheatsUsed = false;
        resetExpedition();
        this.expeditionActive = false;
        this.expeditionStartMillis = runStartMillis;
    }

    private void resetExpedition() {
        System.arraycopy(life, 0, expeditionStart, 0, life.length);
        bleedAtExpeditionStart = bleedDamageTaken;
        expeditionStartTurn = turnsLived;
        expeditionMaxDepth = currentDepth;
        hpStart = hpLast;
        mpStart = mpLast;
        killsByType.clear();
        itemsByCategoryAndDepth.clear();
        attacksByMode.clear();
        depthFirstReachedTurn.clear();
        encountersByDepth.clear();
        levelUpTurns.clear();
        openFights.clear();
        closedFights.clear();
    }

    public String getRunId() {
        return runId;
    }

    /** The one listener told about expedition ends, or null to clear it. */
    public synchronized void setExpeditionEndListener(ExpeditionEndListener listener) {
        this.expeditionEndListener = listener;
    }

    public synchronized ExpeditionEndListener getExpeditionEndListener() {
        return expeditionEndListener;
    }

    // ---- Context ----

    /** What every file is stamped with, so the script can filter like with like. */
    public synchronized void setContext(String difficulty, long worldSeed, String build) {
        this.difficulty = difficulty != null ? difficulty : "UNKNOWN";
        this.worldSeed = worldSeed;
        this.build = build != null ? build : "unknown";
    }

    /** A debug cheat was used; the script leaves this Life's files out of the analysis. */
    public synchronized void markCheatsUsed() {
        this.cheatsUsed = true;
    }

    public boolean isCheatsUsed() {
        return cheatsUsed;
    }

    public boolean isExpeditionActive() {
        return expeditionActive;
    }

    // ---- Turn tick ----

    /**
     * Called once per player turn. Opens an expedition on the first turn outside the
     * shelter, closes one on the first turn back inside, and closes fights that have gone
     * quiet.
     */
    public synchronized void onTurn(int turn, int depth, boolean atShelter, int hp, int mp,
                                    boolean hungry, boolean thirsty, boolean cold, boolean poisoned) {
        this.turnsLived = turn;
        this.currentDepth = depth;
        setStrataReached(depth);
        this.hpLast = hp;
        this.mpLast = mp;

        if (!expeditionActive) {
            if (atShelter) {
                return;
            }
            beginExpedition();
        } else if (atShelter) {
            endExpedition(Outcome.RETURNED, null);
            return;
        }

        expeditionMaxDepth = Math.max(expeditionMaxDepth, depth);
        depthFirstReachedTurn.putIfAbsent(depth, turn - expeditionStartTurn);
        if (hungry) life[Counter.TURNS_HUNGRY.ordinal()]++;
        if (thirsty) life[Counter.TURNS_THIRSTY.ordinal()]++;
        if (cold) life[Counter.TURNS_COLD.ordinal()]++;
        if (poisoned) life[Counter.TURNS_POISONED.ordinal()]++;

        Iterator<Fight> it = openFights.values().iterator();
        while (it.hasNext()) {
            Fight f = it.next();
            if (turn - f.lastTurn >= DISENGAGE_TURNS) {
                close(f, "disengaged");
                it.remove();
            }
        }
    }

    /**
     * Counters are not reset here but when the previous expedition ended, so what happens in
     * the shelter between trips (spending Divinity at the altar, mostly) lands in the next file.
     */
    private void beginExpedition() {
        expeditionActive = true;
        expeditionStartMillis = System.currentTimeMillis();
        expeditionStartTurn = turnsLived;
        expeditionMaxDepth = currentDepth;
        hpStart = hpLast;
        mpStart = mpLast;
        depthFirstReachedTurn.clear();
        depthFirstReachedTurn.put(currentDepth, 0);
    }

    // ---- Combat ----

    /** Legacy entry point: a melee attack with no monster to attribute it to. */
    public synchronized void recordAttack(HitType type, int damage) {
        recordPlayerAttack(null, null, 0, AttackMode.MELEE, type, damage);
    }

    /**
     * A player attack. {@code monster} identifies the fight (any stable object, normally the
     * Monster); null records the totals without a fight.
     */
    public synchronized void recordPlayerAttack(Object monster, String monsterType, int monsterLevel,
                                                AttackMode mode, HitType type, int damage) {
        life[Counter.ATTACKS_MADE.ordinal()]++;
        long[] byMode = attacksByMode.computeIfAbsent(mode, m -> new long[3]);
        byMode[type.ordinal()]++;
        switch (type) {
            case HIT:
                life[Counter.ATTACKS_HIT.ordinal()]++;
                life[Counter.DAMAGE_DEALT.ordinal()] += Math.max(0, damage);
                break;
            case GLANCING:
                life[Counter.ATTACKS_GLANCED.ordinal()]++;
                life[Counter.DAMAGE_DEALT.ordinal()] += Math.max(0, damage);
                break;
            case MISS:
            default:
                life[Counter.ATTACKS_MISSED.ordinal()]++;
                break;
        }
        Fight f = fightFor(monster, monsterType, monsterLevel);
        if (f != null) {
            f.playerAttacks++;
            if (type != HitType.MISS) {
                f.playerHits++;
                f.damageDealt += Math.max(0, damage);
            }
            f.modes.add(mode);
        }
    }

    /** A monster attacked the player; {@code damage} is what landed after armour. */
    public synchronized void recordMonsterAttack(Object monster, String monsterType, int monsterLevel,
                                                 boolean hit, int damage) {
        Fight f = fightFor(monster, monsterType, monsterLevel);
        if (f != null) {
            f.monsterAttacks++;
            if (hit) {
                f.monsterHits++;
                f.damageTaken += Math.max(0, damage);
            }
        }
    }

    private Fight fightFor(Object monster, String monsterType, int monsterLevel) {
        if (monster == null) {
            return null;
        }
        Fight f = openFights.get(monster);
        if (f == null) {
            f = new Fight(monsterType != null ? monsterType : "UNKNOWN", monsterLevel, currentDepth,
                    turnsLived - expeditionStartTurn);
            openFights.put(monster, f);
            encountersByDepth.merge(currentDepth, 1, Integer::sum);
        }
        f.lastTurn = turnsLived;
        return f;
    }

    private void close(Fight f, String outcome) {
        f.outcome = outcome;
        f.closeTurn = turnsLived - expeditionStartTurn;
        closedFights.add(f);
    }

    public synchronized void recordDamageTaken(int amount) {
        if (amount > 0) {
            life[Counter.DAMAGE_TAKEN.ordinal()] += amount;
        }
    }

    public synchronized void recordDamageMitigated(int amount) {
        if (amount > 0) {
            life[Counter.DAMAGE_MITIGATED.ordinal()] += amount;
        }
    }

    public synchronized void recordKill(String monsterType) {
        recordKill(null, monsterType);
    }

    /** A monster the player killed; closes its fight as {@code killed}. */
    public synchronized void recordKill(Object monster, String monsterType) {
        String key = (monsterType == null || monsterType.isEmpty()) ? "UNKNOWN" : monsterType;
        killsByType.merge(key, 1, Integer::sum);
        life[Counter.KILLS.ordinal()]++;
        if (monster != null) {
            Fight f = openFights.remove(monster);
            if (f != null) {
                close(f, "killed");
            }
        }
    }

    // ---- Survival ----

    public synchronized void recordFoodConsumed() {
        life[Counter.FOOD.ordinal()]++;
    }

    public synchronized void recordWaterConsumed() {
        life[Counter.WATER.ordinal()]++;
    }

    public synchronized void recordPotionQuaffed() {
        life[Counter.POTIONS.ordinal()]++;
    }

    public synchronized void recordScrollRead() {
        life[Counter.SCROLLS.ordinal()]++;
    }

    /** Natural regeneration only, not potions or spells. */
    public synchronized void recordRegen(int hp, int mp) {
        life[Counter.HP_REGENERATED.ordinal()] += Math.max(0, hp);
        life[Counter.MP_REGENERATED.ordinal()] += Math.max(0, mp);
    }

    /** Counts a lasting anatomical wound inflicted on the player this run. */
    public synchronized void recordInjurySustained() {
        life[Counter.INJURIES.ordinal()]++;
    }

    /** Total HP this run lost to untreated bleeding, reported at export time. */
    public synchronized void setBleedDamageTaken(int amount) {
        this.bleedDamageTaken = Math.max(0, amount);
    }

    public int getInjuriesSustained() {
        return (int) life[Counter.INJURIES.ordinal()];
    }

    public int getBleedDamageTaken() {
        return bleedDamageTaken;
    }

    /** A stray from the merchant's chain laser clipped the player for this much. */
    public synchronized void recordMerchantFriendlyFire(int amount) {
        if (amount > 0) {
            life[Counter.MERCHANT_FF_DAMAGE.ordinal()] += amount;
            life[Counter.MERCHANT_CLIPS.ordinal()]++;
        }
    }

    public int getMerchantFriendlyFireDamage() {
        return (int) life[Counter.MERCHANT_FF_DAMAGE.ordinal()];
    }

    public int getMerchantClips() {
        return (int) life[Counter.MERCHANT_CLIPS.ordinal()];
    }

    /** HP a kill gave back as Vengeance (#190). */
    public synchronized void recordVengeance(int hp) {
        life[Counter.HP_VENGEANCE.ordinal()] += Math.max(0, hp);
    }

    public int getHpVengeance() {
        return (int) life[Counter.HP_VENGEANCE.ordinal()];
    }

    // ---- Loot and economy ----

    /** An item the player picked up; {@code category} is the item's type or category name. */
    public synchronized void recordItemPickedUp(String category) {
        life[Counter.ITEMS_PICKED_UP.ordinal()]++;
        String key = (category == null ? "UNKNOWN" : category) + "@" + currentDepth;
        itemsByCategoryAndDepth.merge(key, 1, Integer::sum);
    }

    /** A change in gold: positive is gained, negative is spent. */
    public synchronized void recordGold(int delta) {
        if (delta > 0) {
            life[Counter.GOLD_GAINED.ordinal()] += delta;
        } else if (delta < 0) {
            life[Counter.GOLD_SPENT.ordinal()] += -delta;
        }
    }

    public synchronized void recordDivinitiesEarned(int amount) {
        if (amount > 0) {
            life[Counter.DIVINITIES_EARNED.ordinal()] += amount;
        }
    }

    public synchronized void recordDivinitiesSpent(int amount) {
        if (amount > 0) {
            life[Counter.DIVINITIES_SPENT.ordinal()] += amount;
        }
    }

    // ---- Progression ----

    public synchronized void recordExperienceGained(int amount) {
        if (amount > 0) {
            life[Counter.XP_GAINED.ordinal()] += amount;
        }
    }

    public synchronized void recordLevelUp() {
        levelUpTurns.add(turnsLived - expeditionStartTurn);
    }

    public synchronized void recordUnlocksEarned(int count) {
        if (count > 0) {
            life[Counter.UNLOCKS_EARNED.ordinal()] += count;
        }
    }

    // ---- Life accessors ----

    public synchronized void setTurnsLived(int turns) {
        this.turnsLived = turns;
    }

    public synchronized void setStrataReached(int level) {
        this.strataReached = Math.max(this.strataReached, level);
    }

    /** Tracks the most recent source of player damage, used for cause-of-death attribution. */
    public synchronized void setLastDamageSource(String attackerName) {
        this.killerMonster = attackerName;
    }

    /** The Maze house of whatever last hurt the player, or -1 (Houses of the Maze D45). */
    public synchronized void setLastDamageHouse(int houseId) {
        this.killerHouse = houseId;
    }

    public int getKillerHouse() {
        return killerHouse;
    }

    public String getKillerMonster() {
        return killerMonster;
    }

    public synchronized int getTotalMonstersKilled() {
        return (int) life[Counter.KILLS.ordinal()];
    }

    public int getDivinitiesEarned() {
        return (int) life[Counter.DIVINITIES_EARNED.ordinal()];
    }

    public int getStrataReached() {
        return strataReached;
    }

    public int getTurnsLived() {
        return turnsLived;
    }

    // ---- Expedition accessors: how far each counter moved since the last expedition ended ----

    public synchronized int getExpeditionKills() {
        return (int) delta(Counter.KILLS);
    }

    public synchronized int getExpeditionDivinitiesEarned() {
        return (int) delta(Counter.DIVINITIES_EARNED);
    }

    public synchronized int getExpeditionTurns() {
        return turnsLived - expeditionStartTurn;
    }

    /** The deepest level reached on this expedition. */
    public synchronized int getExpeditionMaxDepth() {
        return expeditionMaxDepth;
    }

    public synchronized float getAccuracyPercent() {
        long made = life[Counter.ATTACKS_MADE.ordinal()];
        if (made == 0) {
            return 0f;
        }
        return 100f * (life[Counter.ATTACKS_HIT.ordinal()] + life[Counter.ATTACKS_GLANCED.ordinal()]) / (float) made;
    }

    // ---- Export ----

    /** The player died: closes the current expedition as {@code died}. */
    public synchronized String exportRun(String causeOfDeath) {
        return endExpedition(Outcome.DIED, causeOfDeath);
    }

    /**
     * Closes the current expedition and writes it to disk, returning the JSON. A death closes
     * whatever has happened since the last return, even inside the shelter; a quit from inside
     * the shelter has nothing to write and returns null.
     */
    public synchronized String endExpedition(Outcome outcome, String cause) {
        if (!expeditionActive && outcome != Outcome.DIED) {
            return null;
        }
        if (outcome == Outcome.DIED) {
            this.causeOfDeath = cause;
        }
        if (expeditionEndListener != null) {
            expeditionEndListener.onExpeditionEnding(outcome);
        }
        String closeAs = outcome == Outcome.DIED ? "died" : "abandoned";
        for (Fight f : openFights.values()) {
            close(f, closeAs);
        }
        openFights.clear();

        String json = toJson(outcome, cause);
        if (Gdx.files != null) {
            try {
                FileHandle file = Gdx.files.local("logs/telemetry/expedition_" + expeditionStartMillis + ".json");
                file.writeString(json, false);

                String summary = String.format(
                        "[TELEMETRY] life=%s outcome=%s turns=%d depth=%d cause=%s killer=%s kills=%d accuracy=%.1f%% dmgDealt=%d dmgTaken=%d divinities=%d",
                        runId, outcome.name().toLowerCase(), turnsLived - expeditionStartTurn, expeditionMaxDepth,
                        cause, killerMonster, delta(Counter.KILLS), getAccuracyPercent(),
                        delta(Counter.DAMAGE_DEALT), delta(Counter.DAMAGE_TAKEN), delta(Counter.DIVINITIES_EARNED));
                Gdx.files.local("logs/game_balance_session.log").writeString(summary + "\n", true);
            } catch (Exception e) {
                if (Gdx.app != null) {
                    Gdx.app.error("TelemetryManager", "Failed to export expedition telemetry", e);
                }
            }
        }
        expeditionActive = false;
        resetExpedition();
        return json;
    }

    private long delta(Counter c) {
        return life[c.ordinal()] - expeditionStart[c.ordinal()];
    }

    /** The current expedition as JSON, as if it ended now as a death. */
    public synchronized String toJson() {
        return toJson(Outcome.DIED, causeOfDeath);
    }

    private String toJson(Outcome outcome, String cause) {
        Json j = new Json();
        j.open();
        j.num("schemaVersion", SCHEMA_VERSION);
        j.str("build", build);
        j.str("difficulty", difficulty);
        j.num("worldSeed", worldSeed);
        j.bool("cheatsUsed", cheatsUsed);
        j.str("lifeId", runId);
        j.str("runId", runId);
        j.str("outcome", outcome.name().toLowerCase());
        j.num("turns", turnsLived - expeditionStartTurn);
        j.num("turnsLived", turnsLived);
        j.num("maxDepth", expeditionMaxDepth);
        j.num("strataReached", strataReached);
        j.str("causeOfDeath", outcome == Outcome.DIED ? cause : null);
        j.str("killerMonster", outcome == Outcome.DIED ? killerMonster : null);
        j.num("hpStart", hpStart);
        j.num("hpEnd", hpLast);
        j.num("mpStart", mpStart);
        j.num("mpEnd", mpLast);
        for (Counter c : Counter.values()) {
            j.num(c.key, delta(c));
        }
        long made = delta(Counter.ATTACKS_MADE);
        long landed = delta(Counter.ATTACKS_HIT) + delta(Counter.ATTACKS_GLANCED);
        j.raw("accuracyPercent", String.format(java.util.Locale.ROOT, "%.2f", made == 0 ? 0f : 100f * landed / made));
        j.num("bleedDamageTaken", bleedDamageTaken - bleedAtExpeditionStart);

        j.openObject("attacksByMode");
        for (Map.Entry<AttackMode, long[]> e : attacksByMode.entrySet()) {
            long[] v = e.getValue();
            j.raw(e.getKey().name().toLowerCase(),
                    "{\"hit\": " + v[0] + ", \"glancing\": " + v[1] + ", \"miss\": " + v[2] + "}");
        }
        j.close();

        j.intMap("monstersKilledByType", killsByType);
        j.intMap("itemsPickedUpByCategoryAtDepth", itemsByCategoryAndDepth);
        j.intMap("depthFirstReachedTurn", stringKeys(depthFirstReachedTurn));
        j.intMap("encountersByDepth", stringKeys(encountersByDepth));
        j.raw("levelUpTurns", levelUpTurns.toString());

        j.openArray("fights");
        for (Fight f : closedFights) {
            StringBuilder modes = new StringBuilder("[");
            for (AttackMode m : f.modes) {
                if (modes.length() > 1) modes.append(", ");
                modes.append('"').append(m.name().toLowerCase()).append('"');
            }
            modes.append(']');
            j.element("{\"monster\": " + quoteOrNull(f.monsterType)
                    + ", \"level\": " + f.monsterLevel
                    + ", \"depth\": " + f.depth
                    + ", \"openTurn\": " + f.openTurn
                    + ", \"turns\": " + (f.closeTurn - f.openTurn + 1)
                    + ", \"playerAttacks\": " + f.playerAttacks
                    + ", \"playerHits\": " + f.playerHits
                    + ", \"monsterAttacks\": " + f.monsterAttacks
                    + ", \"monsterHits\": " + f.monsterHits
                    + ", \"damageDealt\": " + f.damageDealt
                    + ", \"damageTaken\": " + f.damageTaken
                    + ", \"modes\": " + modes
                    + ", \"outcome\": " + quoteOrNull(f.outcome) + "}");
        }
        j.closeArray();
        j.close();
        return j.toString();
    }

    private static Map<String, Integer> stringKeys(Map<Integer, Integer> in) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : in.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static String quoteOrNull(String s) {
        return s == null ? "null" : "\"" + escape(s) + "\"";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** A small pretty-printing JSON writer: one field per line, matching the old layout. */
    private static final class Json {
        private final StringBuilder sb = new StringBuilder();
        private final List<Boolean> first = new ArrayList<>();
        private int depth;

        void open() {
            sb.append("{\n");
            depth = 1;
            first.add(true);
        }

        private void sep() {
            int top = first.size() - 1;
            if (!first.get(top)) {
                sb.append(",\n");
            }
            first.set(top, false);
            for (int i = 0; i < depth; i++) sb.append("  ");
        }

        private void key(String k) {
            sep();
            sb.append('"').append(escape(k)).append("\": ");
        }

        void num(String k, long v) {
            key(k);
            sb.append(v);
        }

        void bool(String k, boolean v) {
            key(k);
            sb.append(v);
        }

        void str(String k, String v) {
            key(k);
            sb.append(quoteOrNull(v));
        }

        void raw(String k, String v) {
            key(k);
            sb.append(v);
        }

        void openObject(String k) {
            key(k);
            sb.append("{\n");
            depth++;
            first.add(true);
        }

        void openArray(String k) {
            key(k);
            sb.append("[\n");
            depth++;
            first.add(true);
        }

        void element(String v) {
            sep();
            sb.append(v);
        }

        void intMap(String k, Map<String, Integer> m) {
            openObject(k);
            for (Map.Entry<String, Integer> e : m.entrySet()) {
                num(e.getKey(), e.getValue());
            }
            close();
        }

        void close() {
            end('}');
        }

        void closeArray() {
            end(']');
        }

        private void end(char c) {
            boolean empty = first.remove(first.size() - 1);
            depth--;
            if (empty) {
                sb.setLength(sb.length() - 1); // drop the newline after the opening bracket
            } else {
                sb.append('\n');
                for (int i = 0; i < depth; i++) sb.append("  ");
            }
            sb.append(c);
            if (depth == 0) sb.append('\n');
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
