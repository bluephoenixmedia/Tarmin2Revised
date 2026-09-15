package com.bpm.minotaur.gamedata.injury;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Manages anatomical bodily trauma, tactical physical debuffs, wound triage,
 * and disease progression for the player.
 */
public class InjuryManager {

    public static class TreatmentResult {
        public final boolean success;
        public final String message;
        public final int turnsRequired;
        public final int hpRestored;
        public final boolean infectionTriggered;

        public TreatmentResult(boolean success, String message, int turnsRequired, int hpRestored, boolean infectionTriggered) {
            this.success = success;
            this.message = message;
            this.turnsRequired = turnsRequired;
            this.hpRestored = hpRestored;
            this.infectionTriggered = infectionTriggered;
        }
    }

    private final Map<BodyPart, InjuryRecord> injuries = new EnumMap<>(BodyPart.class);
    private IllnessStage illnessStage = IllnessStage.HEALTHY;
    private int illnessTimer = 0;
    private int stepCounter = 0;
    private final Random random = new Random();

    public Map<BodyPart, InjuryRecord> getInjuries() {
        return injuries;
    }

    public InjuryRecord getInjury(BodyPart part) {
        return injuries.get(part);
    }

    public boolean hasInjury(BodyPart part) {
        return injuries.containsKey(part);
    }

    public boolean hasAnyInjuries() {
        return !injuries.isEmpty() || illnessStage.isIll();
    }

    public boolean hasUntreatedInjuries() {
        for (InjuryRecord rec : injuries.values()) {
            if (!rec.isTreated()) return true;
        }
        return false;
    }

    public IllnessStage getIllnessStage() {
        return illnessStage;
    }

    public void setIllnessStage(IllnessStage stage) {
        this.illnessStage = stage;
        this.illnessTimer = 0;
    }

    // =========================================================================
    // INJURY INFLICTION (Combat / Hazards)
    // =========================================================================

    /**
     * Rolls for trauma on heavy strikes (>= 20% max HP) or critical hits.
     */
    public InjuryRecord inflictRandomInjury(DamageType damageType, int damageAmount, int maxHp) {
        // Weighted body part selection
        int totalWeight = 0;
        for (BodyPart part : BodyPart.values()) totalWeight += part.getHitWeight();
        int roll = random.nextInt(totalWeight);
        BodyPart selectedPart = BodyPart.TORSO;

        int current = 0;
        for (BodyPart part : BodyPart.values()) {
            current += part.getHitWeight();
            if (roll < current) {
                selectedPart = part;
                break;
            }
        }

        // Damage type determines the primary injury
        InjuryType injuryType = InjuryType.LACERATION_BLEEDING;
        if (damageType != null) {
            switch (damageType) {
                case FIRE:
                case ICE:
                case MAGICAL:
                case SORCERY:
                    injuryType = InjuryType.SEVERE_BURN;
                    break;
                case POISON:
                case DISEASE:
                    injuryType = InjuryType.PUNCTURE_WOUND;
                    break;
                case PHYSICAL:
                default:
                    injuryType = (random.nextBoolean()) ? InjuryType.BONE_FRACTURE : InjuryType.LACERATION_BLEEDING;
                    break;
            }
        }

        // Head blunt trauma causes severe concussions (represented as bone fracture mechanics)
        if (selectedPart == BodyPart.HEAD && damageType == DamageType.PHYSICAL) {
            injuryType = InjuryType.BONE_FRACTURE;
        }

        // Severity based on damage fraction
        float fraction = (maxHp > 0) ? (float) damageAmount / maxHp : 0.25f;
        int severity = (fraction >= 0.50f) ? 3 : (fraction >= 0.30f) ? 2 : 1;

        return inflictInjury(selectedPart, injuryType, severity);
    }

    public InjuryRecord inflictInjury(BodyPart part, InjuryType type, int severity) {
        InjuryRecord record = new InjuryRecord(part, type, severity);
        injuries.put(part, record);
        return record;
    }

    public void removeInjury(BodyPart part) {
        injuries.remove(part);
    }

    public void cureAll() {
        injuries.clear();
        illnessStage = IllnessStage.HEALTHY;
        illnessTimer = 0;
    }

    // =========================================================================
    // FIRST AID & TREATMENT
    // =========================================================================

    public TreatmentResult applyTreatment(BodyPart part, Item item, boolean bareHands, Player player, GameEventManager eventManager) {
        InjuryRecord record = injuries.get(part);
        if (record == null && !illnessStage.isIll()) {
            return new TreatmentResult(false, "That limb has no active injuries.", 0, 0, false);
        }

        // 1. Bare Hands Emergency Pressure (no items)
        if (bareHands) {
            if (record != null && (record.getInjuryType() == InjuryType.LACERATION_BLEEDING || record.getInjuryType() == InjuryType.PUNCTURE_WOUND)) {
                boolean stopped = random.nextFloat() < 0.60f;
                if (stopped) {
                    record.setTreated(true);
                    return new TreatmentResult(true, "Applied firm direct pressure with bare hands. Bleeding arrested temporarily.", 25, 0, false);
                } else {
                    return new TreatmentResult(false, "Direct pressure failed to stem the blood flow.", 25, 0, false);
                }
            } else {
                return new TreatmentResult(false, "Bare hands cannot mend fractures, burns, or fevers. Supplies required!", 0, 0, false);
            }
        }

        if (item == null) {
            return new TreatmentResult(false, "No treatment item selected.", 0, 0, false);
        }

        String itemName = item.getType() != null ? item.getType().name() : "";
        String friendlyName = item.getDisplayName().toLowerCase();

        // 2. High-Tier / Sterile Healing Potions
        if (itemName.startsWith("POTION_OF_HEALING") || itemName.startsWith("POTION_GREATER_HEALING")
                || itemName.startsWith("POTION_SUPERIOR_HEALING") || itemName.startsWith("POTION_SUPREME_HEALING")) {
            int heal = (itemName.contains("SUPREME")) ? 45 : (itemName.contains("SUPERIOR")) ? 30 : (itemName.contains("GREATER")) ? 20 : 12;
            if (record != null) {
                record.setTreated(true);
                record.setInfected(false);
            }
            if (illnessStage.isIll()) {
                illnessStage = IllnessStage.HEALTHY;
            }
            if (player != null) {
                player.heal(heal);
                player.getInventory().removeItem(item);
            }
            return new TreatmentResult(true, "Poured curative elixir directly over the wound! Tissue knits and purifies instantly.", 10, heal, false);
        }

        // 3. Antidote / Vitality Potions & Lichen (Curing Illness / Fever)
        if (itemName.equals("POTION_VITALITY") || itemName.equals("GLOWING_LICHEN")) {
            if (illnessStage.isIll()) {
                IllnessStage old = illnessStage;
                illnessStage = IllnessStage.HEALTHY;
                illnessTimer = 0;
                if (player != null) player.getInventory().removeItem(item);
                return new TreatmentResult(true, "Applied " + item.getDisplayName() + "! Fever and bodily infection broken.", 12, 5, false);
            } else if (record != null && record.isInfected()) {
                record.setInfected(false);
                if (player != null) player.getInventory().removeItem(item);
                return new TreatmentResult(true, "Purified infected wound on " + part.getDisplayName() + ".", 10, 4, false);
            }
        }

        // 4. Spider Silk (Sterile Suture / Dressing)
        if (itemName.equals("SPIDER_SILK") || friendlyName.contains("silk")) {
            if (record != null && (record.getInjuryType() == InjuryType.LACERATION_BLEEDING || record.getInjuryType() == InjuryType.PUNCTURE_WOUND)) {
                record.setTreated(true);
                record.setInfected(false);
                int heal = 8;
                if (player != null) {
                    player.heal(heal);
                    player.getInventory().removeItem(item);
                }
                return new TreatmentResult(true, "Sterile spider silk sutured into the wound with surgical precision. Bleed closed.", 14, heal, false);
            }
        }

        // 5. Moss Clump (Antiseptic Absorbent Dressing)
        if (itemName.equals("MOSS_CLUMP") || friendlyName.contains("moss")) {
            if (record != null) {
                record.setTreated(true);
                record.setInfected(false);
                int heal = 6;
                if (player != null) {
                    player.heal(heal);
                    player.getInventory().removeItem(item);
                }
                return new TreatmentResult(true, "Applied antiseptic moss clump to " + part.getDisplayName() + ". Cleaned and dressed.", 12, heal, false);
            }
        }

        // 6. Twisted Root (Pain Relief & Hemostatic Poultice)
        if (itemName.equals("TWISTED_ROOT") || friendlyName.contains("root")) {
            if (record != null) {
                record.setTreated(true);
                int heal = 5;
                if (player != null) {
                    player.heal(heal);
                    player.getInventory().removeItem(item);
                }
                return new TreatmentResult(true, "Chewed and applied astringent root poultice. Hemorrhage sealed.", 15, heal, false);
            }
        }

        // 7. Slime Residue (Burn & Acid Soothing Gel)
        if (itemName.equals("SLIME_RESIDUE") || friendlyName.contains("slime")) {
            if (record != null && record.getInjuryType() == InjuryType.SEVERE_BURN) {
                record.setTreated(true);
                int heal = 8;
                if (player != null) {
                    player.heal(heal);
                    player.getInventory().removeItem(item);
                }
                return new TreatmentResult(true, "Coated blistered burn with cooling biostatic slime gel.", 12, heal, false);
            }
        }

        // 8. Bones / Rotten Rope (Improvised Splint for Fractures)
        if (itemName.contains("BONE") || itemName.contains("ROPE") || friendlyName.contains("bone") || friendlyName.contains("rope")) {
            if (record != null && record.getInjuryType() == InjuryType.BONE_FRACTURE) {
                record.setTreated(true);
                int heal = 4;
                if (player != null) {
                    player.heal(heal);
                    player.getInventory().removeItem(item);
                }
                return new TreatmentResult(true, "Bound fracture with rigid improvised bone splint. Bone stabilized.", 20, heal, false);
            }
        }

        // 9. Dirty Rag / Cloth (High Infection Risk Makeshift Bandage)
        if (itemName.equals("DIRTY_CLOTH") || friendlyName.contains("rag") || friendlyName.contains("cloth")) {
            if (record != null && (record.getInjuryType() == InjuryType.LACERATION_BLEEDING || record.getInjuryType() == InjuryType.PUNCTURE_WOUND)) {
                record.setTreated(true);
                int heal = 5;
                boolean infected = random.nextFloat() < 0.35f;
                if (infected) {
                    record.setInfected(true);
                    if (illnessStage == IllnessStage.HEALTHY) {
                        illnessStage = IllnessStage.STAGE_1_INFECTED_WOUND;
                    }
                }
                if (player != null) {
                    player.heal(heal);
                    player.getInventory().removeItem(item);
                }
                String msg = infected
                        ? "Bound wound with dirty rag. Bleeding stopped, but dirt has infected the tissue! (Infection Risk Triggered)"
                        : "Bound wound with dirty rag. Bleeding stopped.";
                return new TreatmentResult(true, msg, 15, heal, infected);
            }
        }

        // 10. Clean Water Wash
        if (itemName.contains("WATER") || friendlyName.contains("water")) {
            if (record != null) {
                record.setInfected(false);
                if (player != null) player.getInventory().removeItem(item);
                return new TreatmentResult(true, "Flushed wound thoroughly with fresh water, removing grime.", 10, 2, false);
            }
        }

        return new TreatmentResult(false, item.getDisplayName() + " cannot be effectively applied to " + (record != null ? record.getInjuryType().getDisplayName() : "this condition") + ".", 0, 0, false);
    }

    // =========================================================================
    // TURN & STEP UPDATES (Bleeding, Sickness, Decals)
    // =========================================================================

    /**
     * Ticks step-based effects (bleeding on movement, blood decal drops).
     */
    public void updateStep(Player player, Maze maze, GameEventManager eventManager) {
        stepCounter++;

        // 1. Bleed Checks
        for (InjuryRecord rec : injuries.values()) {
            if (!rec.isTreated() && (rec.getInjuryType() == InjuryType.LACERATION_BLEEDING || rec.getInjuryType() == InjuryType.PUNCTURE_WOUND)) {
                int bleedDmg = Math.max(1, rec.getSeverity());
                player.takeTrueDamage(bleedDmg);

                // Spawn blood trail on floor
                if (maze != null && maze.getGoreManager() != null) {
                    Vector3 feet3d = new Vector3(player.getPosition().x, 0.02f, player.getPosition().y);
                    maze.getGoreManager().spawnSurfaceDecal(feet3d, new Color(0.65f, 0.05f, 0.05f, 0.85f), 0.35f);
                }

                if (stepCounter % 4 == 0 && eventManager != null) {
                    eventManager.addEvent(new GameEvent("Blood pours from your " + rec.getBodyPart().getDisplayName() + " as you move! (-" + bleedDmg + " HP)", 1.5f));
                }
            }
        }

        // 2. Illness / Fever Escalation
        illnessTimer++;
        if (illnessTimer > 120) {
            illnessTimer = 0;
            escalateIllness(player, eventManager);
        }
    }

    private void escalateIllness(Player player, GameEventManager eventManager) {
        boolean hasInfectedWound = false;
        for (InjuryRecord rec : injuries.values()) {
            if (rec.isInfected() || (!rec.isTreated() && rec.getTurnsUntreated() > 150)) {
                hasInfectedWound = true;
                break;
            }
        }

        if (hasInfectedWound && illnessStage == IllnessStage.HEALTHY) {
            illnessStage = IllnessStage.STAGE_1_INFECTED_WOUND;
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Your untreated wound throbs hot with infection! (Stage 1: Infected)", 2.5f));
            }
        } else if (illnessStage == IllnessStage.STAGE_1_INFECTED_WOUND) {
            illnessStage = IllnessStage.STAGE_2_ACUTE_FEVER;
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Cold chills and high fever rack your body! (Stage 2: Acute Fever)", 3.0f));
            }
        } else if (illnessStage == IllnessStage.STAGE_2_ACUTE_FEVER) {
            illnessStage = IllnessStage.STAGE_3_SEPTIC_DELIRIUM;
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Septic fever enters your blood! Delirium takes hold! (Stage 3: Septic Delirium)", 3.5f));
            }
        } else if (illnessStage == IllnessStage.STAGE_3_SEPTIC_DELIRIUM) {
            if (player != null) {
                player.takeTrueDamage(3);
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("Blood poisoning weakens your heart! (-3 HP)", 2.0f));
                }
            }
        }
    }

    /**
     * Sanctuary resting at a camp/fire accelerates healing and cures fevers.
     */
    public void restAtSanctuary(Player player, GameEventManager eventManager) {
        for (BodyPart part : BodyPart.values()) {
            InjuryRecord rec = injuries.get(part);
            if (rec != null && rec.isTreated()) {
                // Treated fractures and lacerations heal completely in deep sanctuary rest
                injuries.remove(part);
            }
        }

        if (illnessStage != IllnessStage.HEALTHY) {
            illnessStage = IllnessStage.HEALTHY;
            illnessTimer = 0;
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Warm shelter and restful sleep break your fever!", 3.0f));
            }
        }
    }

    // =========================================================================
    // STAT & GAMEPLAY DEBUFFS
    // =========================================================================

    /**
     * Speed modifier: Broken leg cuts movement speed in half; treated splinted leg gives 0.8x.
     */
    public float getEffectiveSpeedModifier() {
        InjuryRecord legInjury = injuries.get(BodyPart.LEGS);
        if (legInjury != null && legInjury.getInjuryType() == InjuryType.BONE_FRACTURE) {
            return legInjury.isTreated() ? 0.80f : 0.50f;
        }
        if (illnessStage == IllnessStage.STAGE_3_SEPTIC_DELIRIUM) {
            return 0.75f;
        }
        return 1.0f;
    }

    /**
     * Attack roll modifier: Broken arm imposes -4 to hit; treated arm imposes -1.
     */
    public int getEffectiveAttackModifier() {
        InjuryRecord armInjury = injuries.get(BodyPart.ARMS);
        if (armInjury != null && armInjury.getInjuryType() == InjuryType.BONE_FRACTURE) {
            return armInjury.isTreated() ? -1 : -4;
        }
        return 0;
    }

    /**
     * Prevents dual wielding or 2-handed weapons if an arm is fractured and untreated.
     */
    public boolean canWieldTwoHanded() {
        InjuryRecord armInjury = injuries.get(BodyPart.ARMS);
        return armInjury == null || armInjury.isTreated();
    }

    /**
     * Head concussion blurs vision and increases mana cost.
     */
    public boolean isHeadConcussed() {
        InjuryRecord headInjury = injuries.get(BodyPart.HEAD);
        return headInjury != null && !headInjury.isTreated();
    }

    /**
     * Checks if an item can be utilized in first aid triage.
     */
    public boolean isItemViableFirstAid(Item item) {
        if (item == null || item.getType() == null) return false;
        String name = item.getType().name();
        String friendly = item.getDisplayName().toLowerCase();

        return name.startsWith("POTION_OF_HEALING") || name.startsWith("POTION_GREATER_HEALING")
                || name.startsWith("POTION_SUPERIOR_HEALING") || name.startsWith("POTION_SUPREME_HEALING")
                || name.equals("POTION_VITALITY") || name.equals("GLOWING_LICHEN")
                || name.equals("SPIDER_SILK") || friendly.contains("silk")
                || name.equals("MOSS_CLUMP") || friendly.contains("moss")
                || name.equals("TWISTED_ROOT") || friendly.contains("root")
                || name.equals("SLIME_RESIDUE") || friendly.contains("slime")
                || name.contains("BONE") || name.contains("ROPE") || friendly.contains("bone") || friendly.contains("rope")
                || name.equals("DIRTY_CLOTH") || friendly.contains("rag") || friendly.contains("cloth")
                || name.contains("WATER") || friendly.contains("water");
    }

    /**
     * Provides a clear description and risk analysis for applying an item to a specific body part.
     */
    public String getItemSuitabilityPreview(BodyPart part, Item item) {
        if (item == null) {
            return "Crude Direct Pressure: 25 turns | 0% Infection | 60% chance to stem bleeding by hand (0 HP)";
        }
        String name = item.getType() != null ? item.getType().name() : "";
        String friendly = item.getDisplayName().toLowerCase();
        InjuryRecord record = (part != null) ? injuries.get(part) : null;

        if (name.startsWith("POTION_OF_HEALING") || name.startsWith("POTION_GREATER_HEALING")
                || name.startsWith("POTION_SUPERIOR_HEALING") || name.startsWith("POTION_SUPREME_HEALING")) {
            return "Curative Elixir: 10 turns | 0% Risk | Instantly knits flesh, stops bleed, and cures infection.";
        }
        if (name.equals("POTION_VITALITY") || name.equals("GLOWING_LICHEN")) {
            return "Purifying Herb/Elixir: 12 turns | 0% Risk | Breaks systemic fever and cleanses bloodstream infection.";
        }
        if (name.equals("SPIDER_SILK") || friendly.contains("silk")) {
            boolean valid = record != null && (record.getInjuryType() == InjuryType.LACERATION_BLEEDING || record.getInjuryType() == InjuryType.PUNCTURE_WOUND);
            return valid
                    ? "Sterile Suture: 14 turns | 0% Risk | Suture deep lacerations/punctures (+8 HP, stops bleed)"
                    : "Spider Silk: Effective only on open bleeding cuts or punctures.";
        }
        if (name.equals("MOSS_CLUMP") || friendly.contains("moss")) {
            return "Antiseptic Dressing: 12 turns | 0% Risk | Cleans and bandages flesh wounds (+6 HP)";
        }
        if (name.equals("TWISTED_ROOT") || friendly.contains("root")) {
            return "Astringent Poultice: 15 turns | 0% Risk | Seals bleeding tissue (+5 HP)";
        }
        if (name.equals("SLIME_RESIDUE") || friendly.contains("slime")) {
            boolean valid = record != null && record.getInjuryType() == InjuryType.SEVERE_BURN;
            return valid
                    ? "Cooling Bio-Gel: 12 turns | 0% Risk | Soothes severe burns and acid trauma (+8 HP)"
                    : "Slime Residue: Effective primarily on burns and chemical tissue scorch.";
        }
        if (name.contains("BONE") || name.contains("ROPE") || friendly.contains("bone") || friendly.contains("rope")) {
            boolean valid = record != null && record.getInjuryType() == InjuryType.BONE_FRACTURE;
            return valid
                    ? "Improvised Rigid Splint: 20 turns | 0% Risk | Binds and stabilizes bone fractures (+4 HP)"
                    : "Rigid Debris: Used to bind and splint bone fractures.";
        }
        if (name.equals("DIRTY_CLOTH") || friendly.contains("rag") || friendly.contains("cloth")) {
            return "Makeshift Rag: 15 turns | 35% INFECTION RISK! | Stems bleeding (+5 HP), high risk of wound sepsis.";
        }
        if (name.contains("WATER") || friendly.contains("water")) {
            return "Water Wash: 10 turns | 0% Risk | Flushes dirt and localized infection from wound (+2 HP)";
        }
        return item.getDisplayName() + ": Unknown medical efficacy.";
    }
}
