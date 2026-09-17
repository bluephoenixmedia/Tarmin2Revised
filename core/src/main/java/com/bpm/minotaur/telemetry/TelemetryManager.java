package com.bpm.minotaur.telemetry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Collects per-run gameplay metrics for balance analysis. A new run begins on
 * game start / respawn-in-shelter ({@link #startNewRun()}) and is finalized
 * on death or shelter return ({@link #exportRun(String)}), which serializes
 * the run to logs/telemetry/run_&lt;timestamp&gt;.json and appends a summary
 * line to logs/game_balance_session.log.
 */
public class TelemetryManager {

    public enum HitType { HIT, GLANCING, MISS }

    public enum DirectorPacingState {
        HIGH_STRAIN,
        NEUTRAL,
        FLOW
    }

    private static TelemetryManager instance;

    public static synchronized TelemetryManager getInstance() {
        if (instance == null) {
            instance = new TelemetryManager();
        }
        return instance;
    }

    public DirectorPacingState evaluateDirectorPacing(com.bpm.minotaur.gamedata.player.Player player) {
        if (player == null) return DirectorPacingState.NEUTRAL;

        float currentHpPercent = ((float) player.getCurrentHP() / Math.max(1, player.getMaxHP())) * 100f;
        int injuryCount = (player.getInjuryManager() != null && player.getInjuryManager().getInjuries() != null)
                ? player.getInjuryManager().getInjuries().size() : 0;
        int recentDeaths = (causeOfDeath != null) ? 1 : 0;

        float strainScore = currentHpPercent - (injuryCount * 15f) - (recentDeaths * 25f);

        if (strainScore < 40f) {
            return DirectorPacingState.HIGH_STRAIN;
        } else if (strainScore > 75f) {
            return DirectorPacingState.FLOW;
        } else {
            return DirectorPacingState.NEUTRAL;
        }
    }

    private String runId;
    private long runStartMillis;
    private int turnsLived;
    private int strataReached;
    private String causeOfDeath;
    private String killerMonster;

    private int attacksMade;
    private int attacksHit;
    private int attacksGlanced;
    private int attacksMissed;

    private long damageDealt;
    private long damageTaken;
    private long damageMitigatedByArmor;

    private int foodConsumed;
    private int waterConsumed;
    private int potionsQuaffed;
    private int scrollsRead;

    private final Map<String, Integer> monstersKilledByType = new HashMap<>();
    private int divinitiesEarned;

    private int injuriesSustained;
    private int bleedDamageTaken;

    private int merchantFriendlyFireDamage;
    private int merchantClips;

    private int fieldRestUses;

    private TelemetryManager() {
        startNewRun();
    }

    public synchronized void startNewRun() {
        this.runId = UUID.randomUUID().toString();
        this.runStartMillis = System.currentTimeMillis();
        this.turnsLived = 0;
        this.strataReached = 1;
        this.causeOfDeath = null;
        this.killerMonster = null;
        this.attacksMade = 0;
        this.attacksHit = 0;
        this.attacksGlanced = 0;
        this.attacksMissed = 0;
        this.damageDealt = 0;
        this.damageTaken = 0;
        this.damageMitigatedByArmor = 0;
        this.foodConsumed = 0;
        this.waterConsumed = 0;
        this.potionsQuaffed = 0;
        this.scrollsRead = 0;
        this.monstersKilledByType.clear();
        this.divinitiesEarned = 0;
        this.injuriesSustained = 0;
        this.bleedDamageTaken = 0;
        this.merchantFriendlyFireDamage = 0;
        this.merchantClips = 0;
        this.fieldRestUses = 0;
    }

    public String getRunId() {
        return runId;
    }

    public synchronized void recordAttack(HitType type, int damage) {
        attacksMade++;
        switch (type) {
            case HIT:
                attacksHit++;
                damageDealt += Math.max(0, damage);
                break;
            case GLANCING:
                attacksGlanced++;
                damageDealt += Math.max(0, damage);
                break;
            case MISS:
            default:
                attacksMissed++;
                break;
        }
    }

    public synchronized void recordDamageTaken(int amount) {
        if (amount > 0) {
            damageTaken += amount;
        }
    }

    public synchronized void recordDamageMitigated(int amount) {
        if (amount > 0) {
            damageMitigatedByArmor += amount;
        }
    }

    public synchronized void recordKill(String monsterType) {
        String key = (monsterType == null || monsterType.isEmpty()) ? "UNKNOWN" : monsterType;
        monstersKilledByType.merge(key, 1, Integer::sum);
    }

    public synchronized void recordFoodConsumed() {
        foodConsumed++;
    }

    public synchronized void recordWaterConsumed() {
        waterConsumed++;
    }

    public synchronized void recordPotionQuaffed() {
        potionsQuaffed++;
    }

    public synchronized void recordScrollRead() {
        scrollsRead++;
    }

    public synchronized void recordDivinitiesEarned(int amount) {
        if (amount > 0) {
            divinitiesEarned += amount;
        }
    }

    /** Counts a lasting anatomical wound inflicted on the player this run. */
    public synchronized void recordInjurySustained() {
        injuriesSustained++;
    }

    /** Total HP this run lost to untreated bleeding, reported at export time. */
    public synchronized void setBleedDamageTaken(int amount) {
        this.bleedDamageTaken = Math.max(0, amount);
    }

    public int getInjuriesSustained() {
        return injuriesSustained;
    }

    public int getBleedDamageTaken() {
        return bleedDamageTaken;
    }

    /** A stray from the merchant's chain laser clipped the player for this much. */
    public synchronized void recordMerchantFriendlyFire(int amount) {
        if (amount > 0) {
            merchantFriendlyFireDamage += amount;
            merchantClips++;
        }
    }

    public int getMerchantFriendlyFireDamage() {
        return merchantFriendlyFireDamage;
    }

    public int getMerchantClips() {
        return merchantClips;
    }

    /** The field Rest action (H) was used this run -- see the "way off again" balance investigation. */
    public synchronized void recordFieldRestUsed() {
        fieldRestUses++;
    }

    public int getFieldRestUses() {
        return fieldRestUses;
    }

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

    public String getKillerMonster() {
        return killerMonster;
    }

    public synchronized int getTotalMonstersKilled() {
        int total = 0;
        for (int v : monstersKilledByType.values()) {
            total += v;
        }
        return total;
    }

    public int getDivinitiesEarned() {
        return divinitiesEarned;
    }

    public int getStrataReached() {
        return strataReached;
    }

    public int getTurnsLived() {
        return turnsLived;
    }

    public synchronized float getAccuracyPercent() {
        if (attacksMade == 0) {
            return 0f;
        }
        return 100f * (attacksHit + attacksGlanced) / (float) attacksMade;
    }

    /**
     * Finalizes and serializes the current run to disk, then returns the JSON payload.
     */
    public synchronized String exportRun(String causeOfDeath) {
        this.causeOfDeath = causeOfDeath;
        String json = toJson();
        if (Gdx.files != null) {
            try {
                FileHandle runFile = Gdx.files.local("logs/telemetry/run_" + runStartMillis + ".json");
                runFile.writeString(json, false);

                String summary = String.format(
                        "[TELEMETRY] run=%s turns=%d strata=%d cause=%s killer=%s kills=%d accuracy=%.1f%% dmgDealt=%d dmgTaken=%d bleedDmg=%d injuries=%d merchantFF=%d divinities=%d",
                        runId, turnsLived, strataReached, causeOfDeath, killerMonster,
                        getTotalMonstersKilled(), getAccuracyPercent(), damageDealt, damageTaken,
                        bleedDamageTaken, injuriesSustained, merchantFriendlyFireDamage, divinitiesEarned);
                Gdx.files.local("logs/game_balance_session.log").writeString(summary + "\n", true);
            } catch (Exception e) {
                if (Gdx.app != null) {
                    Gdx.app.error("TelemetryManager", "Failed to export run telemetry", e);
                }
            }
        }
        return json;
    }

    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"runId\": \"").append(escape(runId)).append("\",\n");
        sb.append("  \"turnsLived\": ").append(turnsLived).append(",\n");
        sb.append("  \"strataReached\": ").append(strataReached).append(",\n");
        sb.append("  \"causeOfDeath\": ").append(quoteOrNull(causeOfDeath)).append(",\n");
        sb.append("  \"killerMonster\": ").append(quoteOrNull(killerMonster)).append(",\n");
        sb.append("  \"attacksMade\": ").append(attacksMade).append(",\n");
        sb.append("  \"attacksHit\": ").append(attacksHit).append(",\n");
        sb.append("  \"attacksGlanced\": ").append(attacksGlanced).append(",\n");
        sb.append("  \"attacksMissed\": ").append(attacksMissed).append(",\n");
        sb.append("  \"accuracyPercent\": ").append(String.format("%.2f", getAccuracyPercent())).append(",\n");
        sb.append("  \"damageDealt\": ").append(damageDealt).append(",\n");
        sb.append("  \"damageTaken\": ").append(damageTaken).append(",\n");
        sb.append("  \"damageMitigatedByArmor\": ").append(damageMitigatedByArmor).append(",\n");
        sb.append("  \"foodConsumed\": ").append(foodConsumed).append(",\n");
        sb.append("  \"waterConsumed\": ").append(waterConsumed).append(",\n");
        sb.append("  \"potionsQuaffed\": ").append(potionsQuaffed).append(",\n");
        sb.append("  \"scrollsRead\": ").append(scrollsRead).append(",\n");
        sb.append("  \"divinitiesEarned\": ").append(divinitiesEarned).append(",\n");
        sb.append("  \"injuriesSustained\": ").append(injuriesSustained).append(",\n");
        sb.append("  \"bleedDamageTaken\": ").append(bleedDamageTaken).append(",\n");
        sb.append("  \"merchantFriendlyFireDamage\": ").append(merchantFriendlyFireDamage).append(",\n");
        sb.append("  \"merchantClips\": ").append(merchantClips).append(",\n");
        sb.append("  \"fieldRestUses\": ").append(fieldRestUses).append(",\n");
        sb.append("  \"monstersKilledByType\": {\n");
        int i = 0;
        int size = monstersKilledByType.size();
        for (Map.Entry<String, Integer> e : monstersKilledByType.entrySet()) {
            sb.append("    \"").append(escape(e.getKey())).append("\": ").append(e.getValue());
            if (++i < size) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
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
}
