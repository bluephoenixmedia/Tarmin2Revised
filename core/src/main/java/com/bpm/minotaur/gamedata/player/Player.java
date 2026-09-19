package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.monster.HostileSight;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.gamedata.spells.Tome;
import com.bpm.minotaur.gamedata.spells.TomeChoice;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.*;
import com.bpm.minotaur.managers.*;

import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.bpm.minotaur.gamedata.monster.Monster; // NEW
import com.bpm.minotaur.gamedata.monster.MonsterDataManager; // NEW
import com.bpm.minotaur.gamedata.item.ItemModifier; // NEW
import com.bpm.minotaur.gamedata.ModifierType; // NEW
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.gamedata.injury.BodyPart;
import com.bpm.minotaur.gamedata.injury.InjuryManager;
import com.bpm.minotaur.gamedata.injury.InjuryType;
import com.bpm.minotaur.lighting.LightSource;
import com.bpm.minotaur.lighting.LightingManager;

public class Player {

    // --- Stats ---
    // --- Stats ---
    private final PlayerStats stats;
    private int moveSpeed = 12; // Base speed, similar to Monster

    // --- Equipment ---
    private final PlayerEquipment equipment = new PlayerEquipment();
    private final InjuryManager injuryManager = new InjuryManager();
    private final com.bpm.minotaur.gamedata.gore.PlayerBlood blood = new com.bpm.minotaur.gamedata.gore.PlayerBlood();

    public InjuryManager getInjuryManager() {
        return injuryManager;
    }

    // =========================================================================
    // FIELD REST (H) -- reintroduced after the "way off again" balance
    // investigation. The old R-bound rest was deleted wholesale when R was
    // repurposed to open the First Aid modal, leaving no field-accessible HP
    // recovery besides slow passive regen, rare potions, and shelter-only rest.
    // Restored deliberately smaller and cooldown-gated: the original was
    // abusable (camp a cleared room, spend a sliver of the hunger meter,
    // repeat indefinitely for a free top-up), so this version cannot be spammed
    // regardless of how much food is banked.
    // =========================================================================

    /** War Strength restored per successful field rest. */
    public static final int FIELD_REST_HEAL_AMOUNT = 5;

    private int fieldRestCooldownTurns = 0;

    /** Turns remaining before {@link #attemptFieldRest} can succeed again. */
    public int getFieldRestCooldownTurns() {
        return fieldRestCooldownTurns;
    }

    /** Advances the cooldown by one turn. Called once per player turn regardless of what action was taken. */
    public void tickFieldRestCooldown() {
        if (fieldRestCooldownTurns > 0) {
            fieldRestCooldownTurns--;
        }
    }

    /**
     * Compensates the cooldown for turns consumed by the SAME rest that just set
     * it. GameScreen advances several world-ticks per rest (so a monster can
     * close in mid-rest) through the same per-turn hook that decrements this
     * cooldown for every other cause of turn advancement -- without this, a
     * rest's own ticks would immediately erode the cooldown it just started,
     * undermining the anti-spam cooldown's entire purpose.
     */
    public void restoreFieldRestCooldown(int ticks) {
        if (ticks > 0) {
            fieldRestCooldownTurns += ticks;
        }
    }

    /** Outcome of one {@link #attemptFieldRest} call. */
    public static final class FieldRestResult {
        public final boolean success;
        public final String message;

        FieldRestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Spends one point of satiety to heal {@link #FIELD_REST_HEAL_AMOUNT} War
     * Strength, refusing (at no cost) when already at full strength, out of
     * food, or still on cooldown from the last use. On success, the cooldown is
     * set to {@link PlayerStats#getRegenIntervalTurns()} -- the same
     * Constitution-scaled interval natural passive regen already uses, so this
     * reads as one coherent healing model (a food-fueled acceleration of it)
     * rather than a second, disconnected system. Every outcome, including every
     * refusal, is queued onto {@code eventManager} -- a refusal is still
     * something the player pressed a key for and needs a reason for, not a
     * silent no-op.
     */
    public FieldRestResult attemptFieldRest(GameEventManager eventManager) {
        if (fieldRestCooldownTurns > 0) {
            return refuseFieldRest(eventManager,
                    "You are still catching your breath. (" + fieldRestCooldownTurns + " turns)");
        }
        if (stats.getCurrentHP() >= stats.getMaxHP()) {
            return refuseFieldRest(eventManager, "You are already at full strength.");
        }
        if (stats.getFood() <= 0) {
            return refuseFieldRest(eventManager, "You are too famished to rest and recover.");
        }

        stats.setFood(stats.getFood() - 1);
        heal(FIELD_REST_HEAL_AMOUNT);
        fieldRestCooldownTurns = stats.getRegenIntervalTurns();
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordFieldRestUsed();

        String msg = "You catch your breath and recover. WS: " + stats.getCurrentHP() + "/" + stats.getMaxHP() + ".";
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent(msg, 2f));
        }
        return new FieldRestResult(true, msg);
    }

    private FieldRestResult refuseFieldRest(GameEventManager eventManager, String message) {
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent(message, 2f));
        }
        return new FieldRestResult(false, message);
    }

    public boolean canWieldTwoHanded() {
        return injuryManager == null || injuryManager.canWieldTwoHanded();
    }

    public int getEffectiveSpeed() {
        int speed = moveSpeed; // 12
        // AGI modifier shifts base speed
        speed += (stats.getAgility() - 10) / 2;
        // Check for HASTE / SLOW effects
        if (statusManager.hasEffect(StatusEffectType.SLOWED) || statusManager.hasEffect(StatusEffectType.SLOW)) {
            speed /= 2;
        }
        if (statusManager.hasEffect(StatusEffectType.TEMP_SPEED)
                || statusManager.hasEffect(StatusEffectType.SUPER_SPEED)) {
            speed *= 2;
        }
        // Weather Survival Exposure: CHILLED saps 20% of move/attack speed
        if (statusManager.hasEffect(StatusEffectType.CHILLED)) {
            speed = (int) (speed * 0.8f);
        }
        // Anatomical Injury Debuff: Broken legs reduce speed by 50%
        if (injuryManager != null) {
            speed = (int) (speed * injuryManager.getEffectiveSpeedModifier());
        }
        return Math.max(1, speed);
    }

    /** Effective luck: base stat + equipment BONUS_LUCK, clamped to ±13. */
    public int getLuck() {
        int effective = stats.getLuck() + equipment.getEquippedModifierSum(ModifierType.BONUS_LUCK);
        return Math.max(-13, Math.min(13, effective));
    }

    private final StatusManager statusManager;
    private GameEventManager eventManager;

    public void setEventManager(GameEventManager eventManager) {
        this.eventManager = eventManager;
    }

    public GameEventManager getEventManager() {
        return eventManager;
    }

    private final List<StatusEffectType> activeMealEffects = new ArrayList<>();

    // --- Position and Movement ---
    private final Vector2 position;
    private Direction facing;
    private final Vector2 directionVector;
    private final Vector2 cameraPlane;

    // --- Inventory ---
    private final Inventory inventory = new Inventory();

    // --- Spells ---
    private final List<String> knownSpellIds = new ArrayList<>();
    private final String[] preparedSpells = new String[5];
    // Slot 1 (cantrips) is available from the start; Tomes of the Initiate/Elements/
    // Arcane/Tarmin progressively unlock slots 2-5 (see Player#useItem).
    private int unlockedSpellSlots = 1;

    public int getUnlockedSpellSlots() {
        return unlockedSpellSlots;
    }

    public void setUnlockedSpellSlots(int slots) {
        this.unlockedSpellSlots = Math.max(1, Math.min(5, slots));
    }

    public List<String> getKnownSpellIds() {
        return knownSpellIds;
    }

    public void learnSpellId(String spellId) {
        if (spellId != null && !knownSpellIds.contains(spellId.toUpperCase())) {
            knownSpellIds.add(spellId.toUpperCase());
        }
    }

    /**
     * Replaces the whole spellbook with saved state: Known Spells, the prepared
     * slots, and how many Spell Slots are unlocked. Prepared entries beyond the
     * unlocked slots are dropped.
     */
    public void restoreSpellbook(List<String> knownIds, List<String> prepared, int unlockedSlots) {
        knownSpellIds.clear();
        if (knownIds != null) {
            for (String id : knownIds) {
                learnSpellId(id);
            }
        }
        setUnlockedSpellSlots(unlockedSlots);
        for (int i = 0; i < preparedSpells.length; i++) {
            String id = (prepared != null && i < prepared.size()) ? prepared.get(i) : null;
            preparedSpells[i] = null;
            prepareSpell(i, id);
        }
    }

    public String[] getPreparedSpells() {
        return preparedSpells;
    }

    public void prepareSpell(int slot, String spellId) {
        if (slot >= 0 && slot < unlockedSpellSlots && slot < preparedSpells.length) {
            preparedSpells[slot] = spellId != null ? spellId.toUpperCase() : null;
        }
    }

    /** Outcome of a Spellbook request to change a Spell Slot. */
    public enum SlotChange { OK, HOSTILE_IN_VIEW, SLOT_LOCKED, UNKNOWN_SPELL }

    /**
     * The Spellbook's way to change a Spell Slot: puts a Known Spell into an unlocked
     * slot, or clears it when {@code spellId} is null. A spell holds at most one slot,
     * so moving it swaps with whatever the target slot held. Refused while a hostile
     * is in view.
     */
    public SlotChange assignSpellSlot(int slot, String spellId, Maze maze) {
        if (slot < 0 || slot >= unlockedSpellSlots || slot >= preparedSpells.length) {
            return SlotChange.SLOT_LOCKED;
        }
        String id = spellId != null ? spellId.toUpperCase() : null;
        if (id != null && !knownSpellIds.contains(id)) {
            return SlotChange.UNKNOWN_SPELL;
        }
        if (com.bpm.minotaur.gamedata.monster.HostileSight.anyInView(maze, position)) {
            return SlotChange.HOSTILE_IN_VIEW;
        }
        if (id != null) {
            for (int i = 0; i < preparedSpells.length; i++) {
                if (i != slot && id.equals(preparedSpells[i])) {
                    preparedSpells[i] = preparedSpells[slot];
                }
            }
        }
        preparedSpells[slot] = id;
        return SlotChange.OK;
    }

    public String getPreparedSpell(int slot) {
        if (slot >= 0 && slot < preparedSpells.length) {
            return preparedSpells[slot];
        }
        return null;
    }

    public boolean castPreparedSpell(int slot, Maze maze, GameEventManager eventManager, CombatManager combatManager) {
        if (slot >= unlockedSpellSlots) {
            eventManager.addEvent(new GameEvent("Spell slot " + (slot + 1) + " is locked! Study a Tome to unlock it.", 1.5f));
            return false;
        }
        String id = getPreparedSpell(slot);
        if (id == null || id.isEmpty()) {
            eventManager.addEvent(new GameEvent("Spell slot " + (slot + 1) + " is empty! Assign a spell in the Spellbook ["
                    + com.badlogic.gdx.Input.Keys.toString(com.bpm.minotaur.managers.SettingsManager.getInstance().getKey("SPELLBOOK")) + "].", 1.5f));
            return false;
        }
        return com.bpm.minotaur.gamedata.spells.SpellExecutionEngine.castSpell(id, this, maze, eventManager, combatManager);
    }

    /**
     * Resolves the spell a dedicated spell-scroll item (SCROLL_FIREBALL, SCROLL_MAGIC_MISSILE,
     * etc.) represents, independent of the unidentified-appearance ScrollEffectType system.
     * Shared by {@link #scribeScroll} (learn permanently) and {@link #read} (cast immediately).
     */
    private String resolveSpellIdFromScroll(Item scrollItem) {
        if (scrollItem.getSpellId() != null && !scrollItem.getSpellId().isEmpty()) {
            return scrollItem.getSpellId().toUpperCase();
        }
        if (scrollItem.getType() == Item.ItemType.SCROLL_FIREBALL) return "FIREBALL";
        if (scrollItem.getType() == Item.ItemType.SCROLL_MISTY_STEP) return "MISTY_STEP";
        if (scrollItem.getType() == Item.ItemType.SCROLL_MAGIC_MISSILE) return "MAGIC_MISSILE";
        if (scrollItem.getType() == Item.ItemType.SCROLL_LIGHTNING_BOLT) return "LIGHTNING_BOLT";
        String name = scrollItem.getFriendlyName();
        if (name != null && name.contains("(") && name.contains(")")) {
            return name.substring(name.indexOf('(') + 1, name.indexOf(')')).replace(' ', '_').toUpperCase();
        }
        return null;
    }

    /** Whether this item is a scroll carrying a resolvable, not-yet-known spell. */
    public boolean canInscribeScroll(Item item) {
        if (item == null || !item.isScroll()) return false;
        String spellId = resolveSpellIdFromScroll(item);
        return spellId != null && !knownSpellIds.contains(spellId);
    }

    /** Public accessor for UI panels that need to preview a scroll's underlying spell. */
    public String resolveScrollSpellId(Item item) {
        if (item == null) return null;
        return resolveSpellIdFromScroll(item);
    }

    /**
     * Permanently learns a spell scroll into Known Spells, at the cost of 100% of the
     * spell's MP (as opposed to {@link #read} which is a free single emergency cast).
     * The spell also fills the first empty unlocked Spell Slot, if there is one;
     * otherwise the player assigns it from the Spellbook.
     */
    public boolean inscribeScroll(Item scrollItem, GameEventManager eventManager, DiscoveryManager discoveryManager) {
        if (scrollItem == null) return false;
        String spellId = resolveSpellIdFromScroll(scrollItem);

        if (spellId == null) {
            eventManager.addEvent(new GameEvent("This scroll cannot be inscribed.", 1.5f));
            return false;
        }

        if (knownSpellIds.contains(spellId)) {
            eventManager.addEvent(new GameEvent("You already know " + spellId + "!", 1.5f));
            return false;
        }

        com.bpm.minotaur.gamedata.spells.SpellTemplate spellTemplate = com.bpm.minotaur.gamedata.spells.SpellDataManager.getSpell(spellId);
        int mpCost = (spellTemplate != null) ? spellTemplate.getMpCost() : 0;
        if (!hasEnoughMana(mpCost)) {
            eventManager.addEvent(new GameEvent("Not enough MP to inscribe (need " + mpCost + ")!", 2.0f));
            return false;
        }

        deductMana(mpCost);
        int slot = learnAndPrepareIfSlotFree(spellId);
        inventory.removeItem(scrollItem);
        if (discoveryManager != null) {
            discoveryManager.identifyDedicatedScroll(scrollItem.getType(), spellId);
        }
        String name = spellTemplate != null ? spellTemplate.getName() : spellId;
        eventManager.addEvent(new GameEvent(slot >= 0
                ? "Inscribed " + name + " into spell slot " + (slot + 1) + "! (-" + mpCost + " MP)"
                : "Inscribed " + name + " into your Spellbook! (-" + mpCost + " MP)", 2.5f));
        if (soundManager != null) soundManager.playPickupItemSound();
        return true;
    }

    /**
     * Adds a spell to Known Spells and puts it in the first empty unlocked Spell Slot.
     *
     * @return the slot index it was prepared in, or -1 if every unlocked slot is full
     */
    public int learnAndPrepareIfSlotFree(String spellId) {
        learnSpellId(spellId);
        for (int i = 0; i < unlockedSpellSlots && i < preparedSpells.length; i++) {
            if (preparedSpells[i] == null) {
                prepareSpell(i, spellId);
                return i;
            }
        }
        return -1;
    }

    // --- Tome Study & Tome Choice ---

    private final java.util.Random tomeRng = new java.util.Random();
    private TomeStudy activeTomeStudy;
    private TomeChoice pendingTomeChoice;

    /** The field study being channelled, or null. */
    public TomeStudy getActiveTomeStudy() {
        return activeTomeStudy;
    }

    /** The Tome Choice waiting for the player to pick a spell, or null. */
    public TomeChoice getPendingTomeChoice() {
        return pendingTomeChoice;
    }

    /**
     * Opens a Tome. In the Shelter the study completes at once; in the field it
     * becomes a channelled study the caller advances turn by turn with
     * {@link #advanceTomeStudy}. Refused while a hostile is in view, and for a
     * repeat Tome with nothing left to teach.
     *
     * @return true if the study completed or began
     */
    public boolean beginTomeStudy(Item tome, Maze maze, GameEventManager eventManager) {
        Tome kind = tome != null ? Tome.of(tome.getType()) : null;
        if (kind == null || pendingTomeChoice != null) {
            return false;
        }
        if (unlockedSpellSlots >= kind.getSlotNumber()
                && TomeChoice.candidates(kind, knownSpellIds, tomeChoicePerks()).isEmpty()) {
            eventManager.addEvent(new GameEvent("You've learned all this Tome can teach.", 2.5f));
            return false;
        }
        if (maze != null && maze.isHomeTile((int) position.x, (int) position.y)) {
            finishTomeStudy(tome, eventManager);
            return true;
        }
        if (HostileSight.anyInView(maze, position)) {
            eventManager.addEvent(new GameEvent("A hostile is in view -- you cannot study now.", 2.0f));
            return false;
        }
        activeTomeStudy = new TomeStudy(tome, getCurrentHP());
        int left = activeTomeStudy.getTurnsRequired() - activeTomeStudy.getTurnsDone();
        eventManager.addEvent(new GameEvent("You open the " + kind.getDisplayName() + " and begin to study... ("
                + left + " turns). Any action breaks your concentration.", 3.0f));
        return true;
    }

    /** Resolves the world turn that just passed for the active study. */
    public TomeStudy.Step advanceTomeStudy(Maze maze, GameEventManager eventManager) {
        if (activeTomeStudy == null) {
            return TomeStudy.Step.COMPLETE;
        }
        TomeStudy study = activeTomeStudy;
        TomeStudy.Step step = study.afterTurn(getCurrentHP(), HostileSight.anyInView(maze, position));
        switch (step) {
            case COMPLETE:
                activeTomeStudy = null;
                finishTomeStudy(study.getTome(), eventManager);
                break;
            case INTERRUPTED_BY_DAMAGE:
                activeTomeStudy = null;
                eventManager.addEvent(new GameEvent("Pain breaks your concentration! " + studyProgressText(study), 2.5f));
                break;
            case INTERRUPTED_BY_HOSTILE:
                activeTomeStudy = null;
                eventManager.addEvent(new GameEvent("A hostile appears! You snap the Tome shut. " + studyProgressText(study), 2.5f));
                break;
            default:
                break;
        }
        return step;
    }

    /** Stops the active study on the player's own action; progress is kept. */
    public void cancelTomeStudy(GameEventManager eventManager) {
        if (activeTomeStudy == null) {
            return;
        }
        TomeStudy study = activeTomeStudy;
        activeTomeStudy = null;
        eventManager.addEvent(new GameEvent("You close the Tome. " + studyProgressText(study), 2.0f));
    }

    private static String studyProgressText(TomeStudy study) {
        return "(" + study.getTurnsDone() + "/" + study.getTurnsRequired() + " turns studied)";
    }

    /**
     * A finished study opens the Tome Choice. Nothing is granted until a spell is
     * chosen, so the Tome is never lost to an unanswered choice. A first-time Tome
     * with nothing left to offer still unlocks its slot.
     */
    private void finishTomeStudy(Item tome, GameEventManager eventManager) {
        if (!inventory.contains(tome)) {
            return;
        }
        Tome kind = Tome.of(tome.getType());
        pendingTomeChoice = TomeChoice.offer(kind, tome, knownSpellIds, tomeChoicePerks(), tomeRng);
        if (pendingTomeChoice == null) {
            grantTome(tome, kind, eventManager);
            return;
        }
        eventManager.addEvent(new GameEvent("The " + kind.getDisplayName() + " reveals its secrets. Choose a spell to learn.", 3.0f));
    }

    /**
     * Learns the chosen spell from the pending Tome Choice, unlocks the Tome's
     * slot if it is new (preparing the spell there), and uses up the Tome.
     *
     * @return false if the spell was not one of the options
     */
    public boolean chooseTomeSpell(String spellId, GameEventManager eventManager) {
        if (pendingTomeChoice == null || spellId == null
                || !pendingTomeChoice.getOptions().contains(spellId.toUpperCase())) {
            return false;
        }
        TomeChoice choice = pendingTomeChoice;
        pendingTomeChoice = null;
        String id = spellId.toUpperCase();
        if (grantTome(choice.getTomeItem(), choice.getTome(), eventManager)) {
            learnSpellId(id);
            prepareSpell(choice.getTome().getSlotNumber() - 1, id);
        } else {
            learnAndPrepareIfSlotFree(id);
        }
        com.bpm.minotaur.gamedata.spells.SpellTemplate spell = com.bpm.minotaur.gamedata.spells.SpellDataManager.getSpell(id);
        eventManager.addEvent(new GameEvent("Learned " + (spell != null ? spell.getName() : id) + "!", 3.0f));
        return true;
    }

    /** Spends one of the Tome Choice's rerolls on fresh options. */
    public boolean rerollTomeChoice() {
        return pendingTomeChoice != null && pendingTomeChoice.reroll(knownSpellIds, tomeRng);
    }

    /** Uses up the Tome and unlocks its slot; returns whether the slot was newly unlocked. */
    private boolean grantTome(Item tome, Tome kind, GameEventManager eventManager) {
        inventory.removeItem(tome);
        boolean newSlot = kind.getSlotNumber() > unlockedSpellSlots;
        setUnlockedSpellSlots(Math.max(unlockedSpellSlots, kind.getSlotNumber()));
        eventManager.addEvent(new GameEvent(newSlot
                ? "Studied the " + kind.getDisplayName() + "! Spell Slot " + kind.getSlotNumber() + " unlocked!"
                : "Studied the " + kind.getDisplayName() + ".", 3.0f));
        return newSlot;
    }

    private static TomeChoice.Perks tomeChoicePerks() {
        return ShelterAltar.getInstance().getTomeChoicePerks();
    }

    public boolean hasEnoughMana(int cost) {
        return stats.getCurrentMP() >= cost;
    }

    public void deductMana(int amount) {
        stats.setCurrentMP(Math.max(0, stats.getCurrentMP() - amount));
    }

    // --- Managers ---
    private final SoundManager soundManager;
    private final ItemDataManager itemDataManager;
    private final MonsterDataManager monsterDataManager; // NEW
    private final AssetManager assetManager;

    public Player(float startX, float startY) {
        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        this.facing = Direction.NORTH;
        this.directionVector = new Vector2(0, 1);
        this.cameraPlane = new Vector2(0.66f, 0);
        this.soundManager = null;
        this.itemDataManager = null;
        this.monsterDataManager = null;
        this.assetManager = null;
        this.stats = new PlayerStats(Difficulty.MEDIUM);
        this.statusManager = new StatusManager();
        this.statusManager.initialize(null, this);
        initStartingSpells();
    }

    private void initStartingSpells() {
        knownSpellIds.clear();
        learnSpellId("MOTE_OF_LIGHT");
        for (int i = 0; i < preparedSpells.length; i++) {
            preparedSpells[i] = null;
        }
        preparedSpells[0] = "MOTE_OF_LIGHT";
    }

    public Player(float startX, float startY, Difficulty difficulty,
            ItemDataManager itemDataManager, MonsterDataManager monsterDataManager, AssetManager assetManager) {

        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        this.facing = Direction.NORTH;
        this.directionVector = new Vector2();
        this.cameraPlane = new Vector2();
        this.soundManager = new SoundManager(null);
        this.itemDataManager = itemDataManager;
        this.monsterDataManager = monsterDataManager; // NEW
        this.assetManager = assetManager;

        Gdx.app.log("Player [DEBUG]", "Constructor: Creating starting items...");
        updateVectors();

        this.stats = new PlayerStats(difficulty);
        this.statusManager = new StatusManager();
        this.statusManager.initialize(null, this);

        Item knife = itemDataManager.createItem(Item.ItemType.RUSTY_SWORD, 0, 0, ItemColor.GRAY, assetManager);
        inventory.setRightHand(knife);
        if (knife.getGrantedDie() != null) {
            stats.getDicePool().add(knife.getGrantedDie());
            BalanceLogger.getInstance().log("DICE_DEBUG", "Added initial die: " + knife.getGrantedDie().getName());
        }

        // Starter protective layer: Padded Armor (+1 AC) provides baseline defense against early vermin
        Item paddedArmor = itemDataManager.createItem(Item.ItemType.PADDED_ARMOR, 0, 0, ItemColor.TAN, assetManager);
        if (paddedArmor != null) {
            equipment.setWornChest(paddedArmor);
        }

        Item ration = itemDataManager.createItem(Item.ItemType.FOOD, 0, 0, ItemColor.TAN, assetManager);
        inventory.pickupToBackpack(ration);

        Item waterskin = itemDataManager.createItem(Item.ItemType.POTION_BLUE, 0, 0, ItemColor.BLUE, assetManager);
        inventory.pickupToBackpack(waterskin);

        Gdx.app.log("Player [DEBUG]", "Constructor: Finished creating items.");

        // Initialize Spells
        initStartingSpells();
    }

    public interface ItemPickupListener {
        void onItemPickedUp(Item item);
    }
    private ItemPickupListener itemPickupListener;

    public void setItemPickupListener(ItemPickupListener listener) {
        this.itemPickupListener = listener;
    }

    public void setPosition(GridPoint2 newPos) {
        this.position.set(newPos.x + 0.5f, newPos.y + 0.5f);
    }

    public void setPosition(Vector2 pos) {
        this.position.set(pos);
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }

    public boolean pickupItem(Item item) {
        // Food is now picked up normally
        boolean pickedUp = inventory.pickup(item);
        if (pickedUp) {
            if (item.getGrantedDie() != null) {
                stats.getDicePool().add(item.getGrantedDie());
                BalanceLogger.getInstance().log("DICE_DEBUG",
                        "Picked up " + item.getFriendlyName() + " -> Added " + item.getGrantedDie().getName());
            }
            if (itemPickupListener != null) {
                itemPickupListener.onItemPickedUp(item);
            }
        }
        return pickedUp;
    }

    public boolean useQuickSlot(int slotIndex, GameEventManager eventManager, DiscoveryManager discoveryManager, Maze maze) {
        return useQuickSlot(slotIndex, eventManager, discoveryManager, maze, null);
    }

    public boolean useQuickSlot(int slotIndex, GameEventManager eventManager, DiscoveryManager discoveryManager, Maze maze, CombatManager combatManager) {
        if (slotIndex < 0 || slotIndex >= inventory.getQuickSlots().length) return false;
        Item item = inventory.getQuickSlots()[slotIndex];
        if (item == null) {
            eventManager.addEvent(new GameEvent("Quick slot " + (slotIndex + 1) + " is empty.", 1.5f));
            return false;
        }

        if (item.isWeapon()) {
            Item oldWeapon = inventory.getRightHand();
            if (oldWeapon != null && oldWeapon.getGrantedDie() != null) {
                stats.getDicePool().remove(oldWeapon.getGrantedDie());
            }
            if (item.getGrantedDie() != null) {
                stats.getDicePool().add(item.getGrantedDie());
            }
            inventory.setRightHand(item);
            inventory.getQuickSlots()[slotIndex] = oldWeapon;
            eventManager.addEvent(new GameEvent("Swapped to " + item.getDisplayName() + " in Right Hand.", 2.0f));
            soundManager.playPickupItemSound();
            return true;
        }

        if (item.isShield()) {
            Item oldShield = inventory.getLeftHand();
            if (oldShield != null && oldShield.getGrantedDie() != null) {
                stats.getDicePool().remove(oldShield.getGrantedDie());
            }
            if (item.getGrantedDie() != null) {
                stats.getDicePool().add(item.getGrantedDie());
            }
            inventory.setLeftHand(item);
            inventory.getQuickSlots()[slotIndex] = oldShield;
            eventManager.addEvent(new GameEvent("Swapped to " + item.getDisplayName() + " in Left Hand.", 2.0f));
            soundManager.playPickupItemSound();
            return true;
        }

        useItem(item, eventManager, discoveryManager, maze, combatManager);
        return true;
    }

    public boolean quickEquipOrConsumeGroundItem(Maze maze, GameEventManager eventManager, DiscoveryManager discoveryManager, SoundManager soundManager) {
        int px = (int) position.x;
        int py = (int) position.y;
        GridPoint2 feetPos = new GridPoint2(px, py);
        GridPoint2 targetTile = feetPos;
        Item item = maze.getItems().get(feetPos);

        if (item == null) {
            GridPoint2 frontPos = new GridPoint2(
                (int) (position.x + facing.getVector().x),
                (int) (position.y + facing.getVector().y));
            item = maze.getItems().get(frontPos);
            targetTile = frontPos;
        }

        if (item == null || item.isImpassable()) return false;

        if (item.isWeapon() || (item.getType() != null && item.getType().name().endsWith("_BOOK"))) {
            maze.getItems().remove(targetTile);
            Item oldWeapon = inventory.getRightHand();
            if (oldWeapon != null && oldWeapon.getGrantedDie() != null) {
                stats.getDicePool().remove(oldWeapon.getGrantedDie());
            }
            if (item.getGrantedDie() != null) {
                stats.getDicePool().add(item.getGrantedDie());
            }
            inventory.setRightHand(item);
            if (oldWeapon != null) {
                inventory.pickup(oldWeapon);
            }
            eventManager.addEvent(new GameEvent("Equipped " + item.getDisplayName() + " in Right Hand.", 2.0f));
            soundManager.playPickupItemSound();
            return true;
        }

        if (item.isShield()) {
            maze.getItems().remove(targetTile);
            Item oldShield = inventory.getLeftHand();
            if (oldShield != null && oldShield.getGrantedDie() != null) {
                stats.getDicePool().remove(oldShield.getGrantedDie());
            }
            if (item.getGrantedDie() != null) {
                stats.getDicePool().add(item.getGrantedDie());
            }
            inventory.setLeftHand(item);
            if (oldShield != null) {
                inventory.pickup(oldShield);
            }
            eventManager.addEvent(new GameEvent("Equipped " + item.getDisplayName() + " in Left Hand.", 2.0f));
            soundManager.playPickupItemSound();
            return true;
        }

        if (item.isArmor()) {
            maze.getItems().remove(targetTile);
            Item oldArmor = null;
            if (item.isHelmet()) {
                oldArmor = equipment.getWornHelmet();
                equipment.setWornHelmet(item);
            } else if (item.isGauntlets()) {
                oldArmor = equipment.getWornGauntlets();
                equipment.setWornGauntlets(item);
            } else if (item.isBoots()) {
                oldArmor = equipment.getWornBoots();
                equipment.setWornBoots(item);
            } else if (item.isLegs()) {
                oldArmor = equipment.getWornLegs();
                equipment.setWornLegs(item);
            } else if (item.isArms()) {
                oldArmor = equipment.getWornArms();
                equipment.setWornArms(item);
            } else if (item.isCloak()) {
                oldArmor = equipment.getWornBack();
                equipment.setWornBack(item);
            } else if (item.isTorso()) {
                oldArmor = equipment.getWornChest();
                equipment.setWornChest(item);
            }
            if (oldArmor != null) {
                inventory.pickup(oldArmor);
            }
            eventManager.addEvent(new GameEvent("Equipped " + item.getDisplayName() + ".", 2.0f));
            soundManager.playPickupItemSound();
            return true;
        }

        if (item.isConsumableOrTool()) {
            if (!item.isUsable() && !item.isPotion() && !item.isFood()
                    && (item.getType() == null || (!item.getType().name().contains("SCROLL") && !item.getType().name().startsWith("WAND_")))) {
                interactWithItem(maze, eventManager, soundManager, discoveryManager);
                return true;
            }
            maze.getItems().remove(targetTile);
            useItem(item, eventManager, discoveryManager, maze);
            return true;
        }

        interactWithItem(maze, eventManager, soundManager, discoveryManager);
        return true;
    }

    public void interactWithItem(Maze maze, GameEventManager eventManager, SoundManager soundManager,
            DiscoveryManager discoveryManager) {
        int playerGridX = (int) position.x;
        int playerGridY = (int) position.y;
        GridPoint2 playerTile2 = new GridPoint2(playerGridX, playerGridY);

        // 1. Check item at feet first
        Item itemAtFeet = maze.getItems().get(playerTile2);

        if (itemAtFeet != null) {
            // Check Impassable (e.g., Cooking Fire, Crafting Bench)
            if (itemAtFeet.isImpassable()) {
                eventManager.addEvent(new GameEvent("You cannot pick that up.", 2f));
                return;
            }

            if (itemAtFeet.getType() == Item.ItemType.QUIVER || itemAtFeet.isAmmunition()) {
                soundManager.playPickupItemSound();
                int arrowsFound = new Random().nextInt(7) + 8;
                stats.addArrows(arrowsFound);
                maze.getItems().remove(playerTile2);
                eventManager.addEvent(new GameEvent("Collected " + arrowsFound + " " + itemAtFeet.getDisplayName() + "! Total: " + stats.getArrows(), 2.5f));
                BalanceLogger.getInstance().logEconomy("RES_GAIN", "Arrows", arrowsFound);
                return;
            }

            if (itemAtFeet.getCategory() == ItemCategory.TREASURE) {
                soundManager.playPickupItemSound();
                stats.incrementTreasureScore(itemAtFeet.getBaseValue());
                maze.getItems().remove(playerTile2);
                eventManager.addEvent(new GameEvent("You found " + itemAtFeet.getDisplayName() + "!", 2f));
                BalanceLogger.getInstance().logEconomy("TREASURE", itemAtFeet.getDisplayName(), itemAtFeet.getBaseValue());
                return;
            }

            if (itemAtFeet.getType() == Item.ItemType.FLOUR_SACK) {
                soundManager.playPickupItemSound();
                int foodFound = new Random().nextInt(4) + 6;
                stats.addFood(foodFound);
                maze.getItems().remove(playerTile2);
                eventManager.addEvent(new GameEvent("You found " + foodFound + " food.", 2f));
                BalanceLogger.getInstance().logEconomy("RES_GAIN", "Food", foodFound);
                return;
            }

            if (pickupItem(itemAtFeet)) {
                maze.getItems().remove(playerTile2);
                soundManager.playPickupItemSound();
                String pickedName = discoveryManager != null
                        ? discoveryManager.getGroundItemDisplayName(itemAtFeet)
                        : itemAtFeet.getDisplayName();
                eventManager.addEvent(new GameEvent("Picked up " + pickedName, 2f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("PICKUP", itemAtFeet.getDisplayName(),
                        itemAtFeet.getBaseValue());
                // ---------------
            } else {
                eventManager.addEvent(new GameEvent("Inventory is full.", 2f));
            }
            return;
        }

        // 2. Check item in front
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 targetTile = new GridPoint2(targetX, targetY);
        Item itemInFront = maze.getItems().get(targetTile);

        if (itemInFront != null) {

            if (itemInFront.getCategory() == ItemCategory.TREASURE) {
                soundManager.playPickupItemSound();
                stats.incrementTreasureScore(itemInFront.getBaseValue());
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found " + itemInFront.getDisplayName() + "!", 2f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("TREASURE", itemInFront.getDisplayName(),
                        itemInFront.getBaseValue());
                // ---------------
                return;
            }
            if (itemInFront.getType() == Item.ItemType.QUIVER || itemInFront.isAmmunition()) {
                soundManager.playPickupItemSound();
                int arrowsFound = new Random().nextInt(7) + 8;
                stats.addArrows(arrowsFound);
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("Collected " + arrowsFound + " " + itemInFront.getDisplayName() + "! Total: " + stats.getArrows(), 2.5f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("RES_GAIN", "Arrows", arrowsFound);
                // ---------------
                return;
            }
            if (itemInFront.getType() == Item.ItemType.FLOUR_SACK) {
                soundManager.playPickupItemSound();
                int foodFound = new Random().nextInt(4) + 6;
                stats.addFood(foodFound);
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You found " + foodFound + " food.", 2f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("RES_GAIN", "Food", foodFound);
                // ---------------
                return;
            }
            if (itemInFront.getType() == Item.ItemType.MEAT) {
                soundManager.playPickupItemSound();
                int foodFound = new Random().nextInt(6) + 10; // 10-15 food value
                stats.addFood(foodFound);
                maze.getItems().remove(targetTile);
                eventManager.addEvent(new GameEvent("You ate the meat. (" + foodFound + " food)", 2f));

                // --- LOGGING ---
                BalanceLogger.getInstance().logEconomy("RES_GAIN", "Meat", foodFound);
                // ---------------
                return;
            }

            // --- NEW: Portal Interaction ---
            if (itemInFront.getType() == Item.ItemType.MYSTERIOUS_PORTAL) {
                useMysteriousPortal(maze, eventManager);
                return;
            }
            // -------------------------------

            if (pickupItem(itemInFront)) {
                maze.getItems().remove(targetTile);
                if (itemInFront.getType() == ItemType.BRASS_LANTERN) {
                    maze.removeLightAt(targetTile.x + 0.5f, targetTile.y + 0.5f);
                }
                soundManager.playPickupItemSound();
                String pickedName = discoveryManager != null
                        ? discoveryManager.getGroundItemDisplayName(itemInFront)
                        : itemInFront.getDisplayName();
                eventManager.addEvent(new GameEvent("Picked up " + pickedName, 2f));

                // --- LOGGING ---
                Gdx.app.log("Player",
                        "Attempting pickup at " + targetTile + ". Items before: " + maze.getItems().size());
                maze.getItems().remove(targetTile);
                Gdx.app.log("Player", "Items after: " + maze.getItems().size());

                BalanceLogger.getInstance().logEconomy("PICKUP", itemInFront.getDisplayName(),
                        itemInFront.getBaseValue());
                // ---------------
            } else {
                eventManager.addEvent(new GameEvent("Inventory is full.", 2f));
            }
        } else if (inventory.getRightHand() != null) {
            GridPoint2 playerTile = new GridPoint2((int) position.x, (int) position.y);
            if (!maze.getItems().containsKey(playerTile)) {
                Item itemInHand = inventory.getRightHand();
                itemInHand.getPosition().set(playerTile.x + 0.5f, playerTile.y + 0.5f);
                maze.addItem(itemInHand);
                if (itemInHand.getType() == ItemType.BRASS_LANTERN) {
                    maze.addLight(new LightSource("shelter_lantern_" + playerTile.x + "_" + playerTile.y,
                            playerTile.x + 0.5f, playerTile.y + 0.5f,
                            LightingManager.COLOR_LANTERN, 5.0f, LightingManager.MOUNTED_LANTERN_INTENSITY,
                            LightSource.FlickerProfile.LANTERN_BREATH));
                }
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("Dropped " + itemInHand.getDisplayName(), 2f));
            } else {
                eventManager.addEvent(new GameEvent("No space to drop here.", 2f));
            }
        } else {
            eventManager.addEvent(new GameEvent("Nothing to interact with.", 2f));
        }
    }

    public void useItem(Item item, GameEventManager eventManager, DiscoveryManager discoveryManager, Maze maze) {
        useItem(item, eventManager, discoveryManager, maze, null);
    }

    public void useItem(Item item, GameEventManager eventManager, DiscoveryManager discoveryManager, Maze maze, CombatManager combatManager) {
        if (item == null) {
            eventManager.addEvent(new GameEvent("You have nothing to use.", 2f));
            return;
        }

        // --- Tarmin Milestone Tomes: studied (instant in the Shelter, channelled in the field) ---
        if (Tome.of(item.getType()) != null) {
            beginTomeStudy(item, maze, eventManager);
            return;
        }

        // --- NEW: Handle Food & Meal Eating ---
        if (item.isFood()) {
            if (stats.getSatiationState() == com.bpm.minotaur.gamedata.player.PlayerStats.SatiationState.CHOKING) {
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("You are too full to swallow! You choke violently!", 2.5f));
                }
                takeTrueDamage(3);
                inventory.removeItem(item);
                return;
            }

            if (item.getType() == Item.ItemType.MEAL || (item.getMealEffects() != null && !item.getMealEffects().isEmpty())) {
                feastOnMeal(item, eventManager);
                inventory.removeItem(item);
                return;
            }

            com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordFoodConsumed();
            if (item.getHydrationValue() > 0) {
                com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordWaterConsumed();
            }

            // Basic food value
            stats.addFood(item.getNutrition() > 0 ? item.getNutrition() : 8);
            stats.addHydration(item.getHydrationValue());

            // Weather Survival Exposure: hot meals chase away cold; cold drinks cut through heat
            String foodName = item.getDisplayName().toLowerCase();
            float bodyTemp = stats.getBodyTemperature();
            boolean isHotMeal = foodName.contains("cooked") || foodName.contains("roast")
                    || foodName.contains("soup") || foodName.contains("stew") || foodName.contains("meal");
            boolean isColdDrink = item.getHydrationValue() > 0
                    && (foodName.contains("waterskin") || foodName.contains("water") || foodName.contains("snow"));
            if (isHotMeal && bodyTemp < 37.0f) {
                stats.setBodyTemperature(Math.min(37.0f, bodyTemp + 1.5f));
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("The warm meal chases away the chill.", 1.5f));
                }
            } else if (isColdDrink && bodyTemp > 37.0f) {
                stats.setBodyTemperature(Math.max(37.0f, bodyTemp - 1.0f));
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("The cool water helps you shed some heat.", 1.5f));
                }
            }

            // NetHack-style Monster Flesh Intrinsics & Poison System
            String dName = item.getDisplayName().toLowerCase();
            if (dName.contains("scorpion") || dName.contains("spider") || dName.contains("ghoul")
                    || dName.contains("zombie") || dName.contains("kobold") || dName.contains("bile")
                    || dName.contains("snake") || dName.contains("flesh")) {
                if (dName.contains("scorpion") || dName.contains("spider") || dName.contains("ghoul")
                        || dName.contains("zombie") || dName.contains("kobold") || dName.contains("bile")
                        || dName.contains("snake")) {
                    statusManager.addEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.POISONED, 15, 1, false);
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("Tainted raw flesh! You feel poisoned and nauseated.", 2.5f));
                    }
                    // NetHack Intrinsic: 25% chance to permanently gain Poison Resistance
                    if (Math.random() < 0.25f && !statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_POISON)) {
                        statusManager.addEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_POISON, -1, 1, false);
                        if (eventManager != null) {
                            eventManager.addEvent(new GameEvent("Your constitution hardens against venoms! (Gained Poison Resistance)", 3.5f));
                        }
                    }
                }
                if (dName.contains("gelatinous") && Math.random() < 0.30f
                        && !statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_LIGHTNING)) {
                    statusManager.addEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_LIGHTNING, -1, 1, false);
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("A static tingling numbs your skin! (Gained Shock Resistance)", 3.5f));
                    }
                }
                // 20% chance to grant Fire Resistance from elemental beast flesh (e.g. Fire Beetle / Fire Giant)
                if (dName.contains("fire") && Math.random() < 0.20f
                        && !statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_FIRE)) {
                    statusManager.addEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_FIRE, -1, 1, false);
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("A warm barrier settles within you! (Gained Fire Resistance)", 3.5f));
                    }
                }
                if ((dName.contains("frost") || dName.contains("ice") || dName.contains("white dragon")) && Math.random() < 0.30f
                        && !statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_COLD)) {
                    statusManager.addEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_COLD, -1, 1, false);
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("A chill settles comfortably in your bones! (Gained Cold Resistance)", 3.5f));
                    }
                }
            }

            // Apply Random Effect if present (e.g. Cooked Meat)
            if (item.getTrueEffect() != null) {
                item.getTrueEffect().applyEffect(this, statusManager);

                // Show effect message
                String msg = item.getTrueEffect().getConsumeMessage();
                if (msg != null && eventManager != null) {
                    eventManager.addEvent(new GameEvent(msg, 2f));
                }
                // Identification check
                if (!discoveryManager.isPotionIdentified(item.getTrueEffect())
                        && item.getTrueEffect().doesSelfIdentify()) {
                    discoveryManager.identifyPotion(this, item.getTrueEffect());
                    if (eventManager != null) {
                        eventManager.addEvent(
                                new GameEvent("You discovered it was " + item.getTrueEffect().getBaseName() + "!", 2.0f));
                    }
                }
            }

            // Apply Intrinsic
            if (item.getGrantedIntrinsic() != null) {
                com.bpm.minotaur.gamedata.effects.StatusEffectType intrinsic = item.getGrantedIntrinsic();
                if (!statusManager.hasEffect(intrinsic)) {
                    statusManager.addEffect(intrinsic, -1, 1, false);
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent("You feel a change in your nature.", 2.5f));
                        if (intrinsic == com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_FIRE)
                            eventManager.addEvent(new GameEvent("You feel cool.", 2f));
                        if (intrinsic == com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_COLD)
                            eventManager.addEvent(new GameEvent("You feel warm.", 2f));
                        if (intrinsic == com.bpm.minotaur.gamedata.effects.StatusEffectType.TELEPATHY)
                            eventManager.addEvent(new GameEvent("You feel mental waves.", 2f));
                    }
                } else if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("You feel nothing new.", 1.5f));
                }
            }

            inventory.removeItem(item); // Consume it
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You ate the " + item.getDisplayName() + ".", 2f));
            }
            if (soundManager != null) {
                soundManager.playPickupItemSound();
            }

            // Log the meal
            BalanceLogger.getInstance().logEconomy("RES_GAIN", "Food (Item)", 5);
            return;
        }
        // -------------------------------

        // --- SCROLL HANDLING (Moved before isUsable check and widened) ---
        if (item.getType().name().contains("SCROLL")) {
            read(item, discoveryManager, eventManager, maze, combatManager);
            return;
        }
        // ----------------------------------------------------------------

        if (item.getType() == Item.ItemType.MYSTERIOUS_PORTAL) {
            useMysteriousPortal(maze, eventManager);
            return;
        }

        if (!item.isUsable() && !item.isPotion() && !item.isArmor() && !item.isRing()) {
            eventManager.addEvent(new GameEvent("You can't use that.", 2f));
            return;
        }

        if (item.isPotion()) {
            quaff(item, discoveryManager, eventManager);
            return;
        }

        if (item.getType().name().startsWith("WAND_")) {
            // Wands usually require direction, handled by Z key.
            // If Used directly, maybe default direction?
            // For now, allow Use to trigger Zap in facing direction.
            zap(item, facing, discoveryManager, eventManager, maze);
            return;
        }

        switch (item.getCategory()) {
            case ARMOR:
                wear(item, eventManager);
                break;
            case RING:
                equipRing(item, eventManager); // TODO: Refactor to 'putOn'
                break;
            case USEFUL:
                useConsumable(item, eventManager);
                break;
            default:
                eventManager.addEvent(new GameEvent("Cannot use this item.", 2f));
                break;
        }
    }

    public boolean hasIntrinsic(com.bpm.minotaur.gamedata.effects.StatusEffectType type) {
        // Check Status Effects (Eating/Potions)
        if (statusManager.hasEffect(type))
            return true;

        // Check Equipment
        if (equipment != null) {
            for (com.bpm.minotaur.gamedata.item.Item item : equipment.getAllEquipped()) {
                if (item == null)
                    continue;
                for (com.bpm.minotaur.gamedata.item.ItemModifier mod : item.getModifiers()) {
                    if (mod.type == com.bpm.minotaur.gamedata.ModifierType.GRANT_INTRINSIC) {
                        if (mod.value == type.ordinal())
                            return true;
                    }
                }
            }
        }

        // Check Ring of Free Action
        if (equipment != null && equipment.hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.FREE_ACTION)) {
            if (type == com.bpm.minotaur.gamedata.effects.StatusEffectType.SLOWED ||
                type == com.bpm.minotaur.gamedata.effects.StatusEffectType.SLOW ||
                type == com.bpm.minotaur.gamedata.effects.StatusEffectType.PARALYZED) {
                return true;
            }
        }

        return false;
    }

    // --- NEW INTERACTION METHODS ---

    private void useMysteriousPortal(Maze maze, GameEventManager eventManager) {
        boolean toVoid = !com.bpm.minotaur.managers.DimensionalManager.getInstance().isInVoid();
        if (toVoid) {
            eventManager.addEvent(new GameEvent("You step into the swirling Tear of Tarmin...", 2.0f));
            eventManager.addEvent(new GameEvent("Reality shears! Entering the Ancient Raycast Void...", 3.0f));
        } else {
            eventManager.addEvent(new GameEvent("You step through the Resonating Rift...", 2.0f));
            eventManager.addEvent(new GameEvent("The Void dissolves! Returning to the Mortal Realm...", 3.0f));
        }

        // Trigger System Event
        eventManager.addEvent(new GameEvent(GameEvent.EventType.PORTAL_ACTIVATED, null));

        // --- BALANCE LOGGING ---
        BalanceLogger.getInstance().log("PORTAL_USE", "Player triggered Mysterious Portal. toVoid=" + toVoid);
    }

    /**
     * Consumes a cooked meal, applying Caves of Qud-style metabolic boons.
     * Clears previous meal metabolic buffs, resets metabolizing timer, restores satiety, heals HP, and warms temperature.
     */
    public void feastOnMeal(Item meal) {
        feastOnMeal(meal, null);
    }

    public void feastOnMeal(Item meal, GameEventManager eventManager) {
        if (meal == null) return;

        // Choking check if already over-satiated
        if (stats.getSatiationState() == com.bpm.minotaur.gamedata.player.PlayerStats.SatiationState.CHOKING) {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You are too full to swallow! You choke violently!", 2.5f));
            }
            takeTrueDamage(3);
            return;
        }

        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordFoodConsumed();
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordWaterConsumed();

        // 1. Satiety, Food, Hydration & Temperature (NetHack model: 0 direct HP heal)
        stats.modifySatiety(45f);
        stats.addFood(meal.getNutrition() > 0 ? meal.getNutrition() : 25);
        stats.addHydration(meal.getHydrationValue() > 0 ? meal.getHydrationValue() : 15);

        // 2. Warm body temperature toward cozy normal (37°C)
        float temp = stats.getBodyTemperature();
        if (temp < com.bpm.minotaur.gamedata.player.PlayerStats.BODY_TEMP_NORMAL) {
            stats.setBodyTemperature(Math.min(com.bpm.minotaur.gamedata.player.PlayerStats.BODY_TEMP_NORMAL, temp + 2.5f));
        }

        // 3. Caves of Qud Style Metabolic Overwrite
        if (statusManager != null) {
            for (StatusEffectType oldEff : activeMealEffects) {
                statusManager.removeEffect(oldEff);
            }
            activeMealEffects.clear();
            statusManager.removeEffect(StatusEffectType.METABOLIZING);

            int duration = meal.getMealEffectDuration() > 0 ? meal.getMealEffectDuration() : 150;
            statusManager.addEffect(StatusEffectType.METABOLIZING, duration, 1, false);

            List<StatusEffectType> effs = meal.getMealEffects();
            if (effs != null && !effs.isEmpty()) {
                for (StatusEffectType eff : effs) {
                    statusManager.addEffect(eff, duration, 1, false);
                    activeMealEffects.add(eff);
                }
            }

            StringBuilder effList = new StringBuilder();
            if (effs != null && !effs.isEmpty()) {
                for (int i = 0; i < effs.size(); i++) {
                    if (i > 0) effList.append(", ");
                    effList.append(effs.get(i).name().replace('_', ' '));
                }
            } else {
                effList.append("Well Fed");
            }

            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You feast upon " + meal.getDisplayName() + "! Metabolizing: " + effList + " (" + duration + " turns).", 3.5f));
            }
        }

        if (soundManager != null) {
            soundManager.playPickupItemSound();
        }
        BalanceLogger.getInstance().logEconomy("RES_GAIN", "Cooked Meal", 25);
    }

    public List<StatusEffectType> getActiveMealEffects() {
        return java.util.Collections.unmodifiableList(activeMealEffects);
    }

    private void drinkToxicConcoction(Item potion, int damage, int strBonus, int maxHpPenalty, int toxicityAdd,
            String msg, GameEventManager eventManager) {
        // 1. Damage (The Ordeal)
        stats.setCurrentHP(stats.getCurrentHP() - damage);

        // 2. Apply Stats
        if (strBonus > 0) {
            stats.setStrength(stats.getStrength() + strBonus);
        }

        if (maxHpPenalty > 0) {
            stats.modifyBaseHP(-maxHpPenalty);
        }

        // 3. Increase Toxicity
        stats.modifyToxicity(toxicityAdd);

        eventManager.addEvent(new GameEvent("You take " + damage + " poison damage!", 2f));
        eventManager.addEvent(new GameEvent(msg, 3f));

        // 4. Check for Death
        if (stats.getCurrentHP() <= 0) {
            eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
        }
    }

    public void quaff(Item potion, DiscoveryManager discoveryManager, GameEventManager eventManager) {
        if (potion == null) {
            eventManager.addEvent(new GameEvent("Quaff what?", 1.0f));
            return;
        }

        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordPotionQuaffed();

        // NEW: Toxic Alchemy Potions
        if (potion.getType() == ItemType.POTION_FERAL_DRAUGHT) {
            drinkToxicConcoction(potion, 30, 1, 0, 15, "Strength surges through you, but it burns!", eventManager);
            inventory.removeItem(potion);
            return;
        }
        if (potion.getType() == ItemType.POTION_TITAN_SLUDGE) {
            drinkToxicConcoction(potion, 50, 5, 5, 40, "You feel heavy and powerful... and sick.", eventManager);
            inventory.removeItem(potion);
            return;
        }

        if (!potion.isPotion()) {
            eventManager.addEvent(new GameEvent("You can't drink that!", 1.0f));
            return;
        }

        // Remove from inventory FIRST? Or after effect?
        // NetHack consumes usually.
        // We need to handle stack splitting if we ever have stacks, but currently
        // unique items.
        // Assuming single items for now or handle removal logic.
        // ... existing logic ...

        if (potion.getTrueEffect() == null) {
            eventManager.addEvent(new GameEvent("It tastes like water.", 1.5f));
            // Consume
            inventory.removeItem(potion);
            return;
        }

        PotionEffectType effect = potion.getTrueEffect();
        if (effect != null) {
            effect.applyEffect(this, statusManager);
        }

        // Potions usually hydrate
        stats.addHydration(potion.getHydrationValue() > 0 ? potion.getHydrationValue() : 5);

        // Message
        String msg = effect.getConsumeMessage();
        if (msg == null)
            msg = "You feel strange.";
        if (effect == PotionEffectType.OIL_OF_SHARPNESS
                && (inventory.getRightHand() == null || !inventory.getRightHand().isWeapon())) {
            msg = "The oil has nothing to coat and drips uselessly.";
        }
        eventManager.addEvent(new GameEvent(msg, 2.0f));

        // Identification check
        if (!discoveryManager.isPotionIdentified(effect) && effect.doesSelfIdentify()) {
            discoveryManager.identifyPotion(this, effect);
            eventManager.addEvent(new GameEvent("You discovered it was " + effect.getBaseName() + "!", 2.0f));
        }

        BalanceLogger.getInstance().logEconomy("RES_USED", "Potion", 1);

        // Consume item
        inventory.removeItem(potion);
    }

    public void read(Item scroll, DiscoveryManager discoveryManager, GameEventManager eventManager, Maze maze) {
        read(scroll, discoveryManager, eventManager, maze, null);
    }

    public void read(Item scroll, DiscoveryManager discoveryManager, GameEventManager eventManager, Maze maze, CombatManager combatManager) {
        if (scroll == null) {
            eventManager.addEvent(new GameEvent("Read what?", 1.0f));
            return;
        }

        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordScrollRead();

        ScrollEffectType effect = scroll.getScrollEffect();
        if (effect == null) {
            // Dedicated spell scrolls (Fireball, Magic Missile, etc.) carry a spellId
            // instead of a ScrollEffectType -- cast that spell directly rather than
            // reporting the scroll as blank.
            String spellId = resolveSpellIdFromScroll(scroll);
            if (spellId != null) {
                if (discoveryManager != null) {
                    discoveryManager.identifyDedicatedScroll(scroll.getType(), spellId);
                }

                GameScreen gs = (combatManager != null) ? combatManager.getGameScreen() : null;
                if (gs != null && gs.getSpellCastOverlay() != null) {
                    gs.getSpellCastOverlay().triggerScrollRead(0.6f, new Color(1f, 0.7f, 0.2f, 1f), "EVOCATION");
                }
                if (soundManager != null) {
                    soundManager.playScrollUnfurl();
                }

                com.bpm.minotaur.gamedata.spells.SpellExecutionEngine.castSpell(spellId, this, maze, eventManager, combatManager);
                inventory.removeItem(scroll);
                return;
            }
            eventManager.addEvent(new GameEvent("The scroll is blank.", 1.5f));
            inventory.removeItem(scroll);
            return;
        }

        GameScreen gs = (combatManager != null) ? combatManager.getGameScreen() : null;
        if (soundManager != null) {
            soundManager.playScrollUnfurl();
            soundManager.playScrollChime(effect);
        }

        // Apply Effect
        switch (effect) {
            case IDENTIFY:
                if (gs != null) {
                    if (gs.getSpellCastOverlay() != null) {
                        gs.getSpellCastOverlay().triggerScrollRead(0.65f, Color.GOLD, "DIVINATION");
                    }
                    if (gs.getSpellPostProcessor() != null) {
                        gs.getSpellPostProcessor().triggerWisdomIris(0.70f);
                    }
                }
                // Identify all items in inventory for now (simplification)
                for (Item i : inventory.getAllItems()) {
                    if (!i.isIdentified()) {
                        i.setIdentified(true);
                        // If it's a potion/scroll/wand, we should also identify the TYPE in discovery
                        // manager
                        if (i.isPotion())
                            discoveryManager.identifyPotion(this, i.getTrueEffect());
                        if (i.getType().name().startsWith("SCROLL_") && i.getScrollEffect() != null)
                            discoveryManager.identifyScroll(this, i.getScrollEffect());
                        if (i.getType().name().startsWith("WAND_") && i.getWandEffect() != null)
                            discoveryManager.identifyWand(this, i.getWandEffect());
                    }
                }
                eventManager.addEvent(new GameEvent("Your possessions glow with understanding!", 2.0f));
                break;
            case TELEPORT: {
                if (gs != null) {
                    if (gs.getSpellCastOverlay() != null) {
                        gs.getSpellCastOverlay().triggerScrollRead(0.60f, Color.CYAN, "TRANSMUTATION");
                    }
                    if (gs.getSpellPostProcessor() != null) {
                        gs.getSpellPostProcessor().triggerGlitch(0.85f, 0.40f);
                        gs.getSpellPostProcessor().triggerChromaticAberration(0.80f, 0.45f);
                    }
                }
                GridPoint2 startTile = new GridPoint2((int) this.position.x, (int) this.position.y);
                boolean teleported = false;
                int tries = 0;
                while (tries < 20) {
                    int tx = (int) (Math.random() * maze.getWidth());
                    int ty = (int) (Math.random() * maze.getHeight());
                    if (maze.isPassable(tx, ty) && maze.getGameObjectAt(tx, ty) == null) {
                        GridPoint2 candidate = new GridPoint2(tx, ty);
                        if (candidate.equals(startTile)
                                || !com.bpm.minotaur.gamedata.Pathfinder.findPath(maze, this, startTile, candidate).isEmpty()) {
                            if (combatManager != null && combatManager.getAnimationManager() != null) {
                                Vector3 depart3d = new Vector3(startTile.x + 0.5f, 0.5f, startTile.y + 0.5f);
                                Vector3 arrive3d = new Vector3(tx + 0.5f, 0.5f, ty + 0.5f);
                                combatManager.getAnimationManager().spawnExplosion(ExplosionType.VOID, depart3d, 1.4f, 0.45f);
                                combatManager.getAnimationManager().spawnExplosion(ExplosionType.WIND, arrive3d, 1.5f, 0.50f);
                            }
                            this.position.set(tx + 0.5f, ty + 0.5f);
                            eventManager.addEvent(new GameEvent("You teleport to a new location!", 2.0f));
                            teleported = true;
                            break;
                        }
                    }
                    tries++;
                }
                if (!teleported)
                    eventManager.addEvent(new GameEvent("The chaotic energies fizzle...", 2.0f));
                break;
            }
            case MAGIC_MAPPING:
                if (gs != null) {
                    if (gs.getSpellCastOverlay() != null) {
                        gs.getSpellCastOverlay().triggerScrollRead(0.65f, Color.GREEN, "DIVINATION");
                    }
                    if (gs.getSpellPostProcessor() != null) {
                        gs.getSpellPostProcessor().triggerSonarWave(0.85f);
                    }
                }
                for (int mx = 0; mx < maze.getWidth(); mx++) {
                    for (int my = 0; my < maze.getHeight(); my++) {
                        maze.markVisited(mx, my);
                    }
                }
                eventManager.addEvent(new GameEvent("A map is etched in your mind!", 2.0f));
                break;
            case ENCHANT_WEAPON:
                if (gs != null) {
                    if (gs.getSpellCastOverlay() != null) {
                        gs.getSpellCastOverlay().triggerScrollRead(0.60f, Color.CYAN, "TRANSMUTATION");
                    }
                    if (gs.getSpellPostProcessor() != null) {
                        gs.getSpellPostProcessor().triggerArcaneBladeGleam(0.75f);
                    }
                }
                Item weapon = inventory.getRightHand();
                if (weapon != null && weapon.isWeapon()) {
                    weapon.addModifier(new ItemModifier(ModifierType.BONUS_DAMAGE, 1, "Enchanted"));
                    eventManager.addEvent(new GameEvent("Your weapon glows with power!", 2.0f));
                } else {
                    eventManager.addEvent(new GameEvent("You need to hold a weapon.", 1.5f));
                }
                break;
            case ENCHANT_ARMOR:
                if (gs != null) {
                    if (gs.getSpellCastOverlay() != null) {
                        gs.getSpellCastOverlay().triggerScrollRead(0.60f, Color.WHITE, "ABJURATION");
                    }
                    if (gs.getSpellPostProcessor() != null) {
                        gs.getSpellPostProcessor().triggerAegisFlash(0.70f);
                    }
                }
                Item armor = getRandomWornArmorHelper();
                if (armor != null) {
                    armor.addModifier(new ItemModifier(ModifierType.BONUS_AC, 1, "Blessed"));
                    eventManager.addEvent(new GameEvent("Your " + armor.getDisplayName() + " glows silver!", 2.0f));
                } else {
                    eventManager.addEvent(new GameEvent("You are not wearing any armor to enchant.", 2.0f));
                }
                break;
            case CREATE_MONSTER:
                if (gs != null && gs.getSpellCastOverlay() != null) {
                    gs.getSpellCastOverlay().triggerScrollRead(0.65f, Color.PURPLE, "CONJURATION");
                }
                int[][] dirs = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
                boolean spawned = false;
                for (int[] d : dirs) {
                    int mx = (int) position.x + d[0];
                    int my = (int) position.y + d[1];
                    if (maze.getWallDataAt(mx, my) == 0 && maze.getGameObjectAt(mx, my) == null) {
                        if (this.monsterDataManager == null) {
                            eventManager.addEvent(new GameEvent("The spell fizzles (No Data).", 2.0f));
                            break;
                        }
                        Monster.MonsterType mType = Monster.MonsterType.SKELETON; // Summon Skeleton
                        try {
                            com.bpm.minotaur.gamedata.monster.MonsterVariant variant = monsterDataManager
                                    .getRandomVariantForMonster(mType, getLevel());
                            if (variant != null) {
                                Monster m = new Monster(mType, mx, my, variant.color, monsterDataManager, assetManager);
                                m.scaleStats(getLevel());
                                maze.getMonsters().put(new GridPoint2(mx, my), m);
                                if (combatManager != null && combatManager.getAnimationManager() != null) {
                                    Vector3 spawn3d = new Vector3(mx + 0.5f, 0.5f, my + 0.5f);
                                    combatManager.getAnimationManager().spawnExplosion(ExplosionType.VOID, spawn3d, 1.5f, 0.55f);
                                }
                                eventManager.addEvent(new GameEvent("A monster appears from the void!", 2.0f));
                                spawned = true;
                            }
                            break;
                        } catch (Exception e) {
                            Gdx.app.error("Player", "Summon failed", e);
                        }
                    }
                }
                if (!spawned)
                    eventManager.addEvent(new GameEvent("The summoning fails due to lack of space.", 2.0f));
                break;
        }

        // Identification
        if (effect.doesSelfIdentify() && !discoveryManager.isScrollIdentified(effect)) {
            discoveryManager.identifyScroll(this, effect);
        }

        // Consume
        inventory.removeItem(scroll);
    }

    public void zap(Item wand, Direction dir, DiscoveryManager discoveryManager, GameEventManager eventManager,
            Maze maze) {
        if (wand == null) {
            eventManager.addEvent(new GameEvent("Zap what?", 1.0f));
            return;
        }

        if (wand.getCharges() <= 0) {
            eventManager.addEvent(new GameEvent("Nothing happens.", 1.5f));
            return;
        }

        // Decrement charge
        wand.decrementCharges();

        WandEffectType effect = wand.getWandEffect();
        if (effect == null)
            return; // Should not happen for wands

        String msg = effect.getZapMessage();
        if (msg != null)
            eventManager.addEvent(new GameEvent(msg, 2.0f));

        // Logic
        int tx = (int) position.x + (int) dir.getVector().x;
        int ty = (int) position.y + (int) dir.getVector().y;

        switch (effect) {
            case DIGGING:
                // Check if wall
                if (maze.getWallDataAt(tx, ty) == 1) { // 1 is wall
                    maze.setTile(tx, ty, 0); // 0 is floor
                    eventManager.addEvent(new GameEvent("The rock crumbles!", 2.0f));
                    // Check identification: If player sees wall gone
                    discoveryManager.identifyWand(this, effect);
                } else {
                    eventManager.addEvent(new GameEvent("The beam dissipates.", 1.0f));
                }
                break;
            case FIRE:
            case COLD:
            case MAGIC_MISSILE:
            case LIGHT:
            case TELEPORTATION:
                // Affect monster at tx, ty
                // We don't have direct access to list of monsters here easily without
                // iteration?
                // Maze might have getMonsterAt(x,y)?
                // Checking code... Maze has 'monsters' list?
                // Need to verify Maze methods.
                // For now stub.
                eventManager.addEvent(new GameEvent("The beam strikes at (" + tx + "," + ty + ")!", 1.0f));
                discoveryManager.identifyWand(this, effect);
                break;
        }
    }

    public void apply(Item tool, GameEventManager eventManager) {
        if (tool == null) {
            eventManager.addEvent(new GameEvent("Apply what?", 1.0f));
            return;
        }
        eventManager.addEvent(new GameEvent("You apply the " + tool.getDisplayName() + ".", 2.0f));
    }

    public void wield(Item weapon, GameEventManager eventManager) {
        if (weapon == null) {
            // Wielding nothing = holster
            if (inventory.getRightHand() != null) {
                eventManager.addEvent(new GameEvent("You put away your weapon.", 1.0f));
                // This logic depends on where it goes. For now, swap hands ensures it's in
                // hand.
                // If we want to holster to pack, that's different.
                // 'w - -' (wield nothing) usually implies fighting with hands.
            }
            return;
        }
        // If weapon is in pack, move to hand.
        if (inventory.getRightHand() == weapon) {
            eventManager.addEvent(new GameEvent("You are already wielding that.", 1.0f));
            return;
        }

        // Swap logic
        inventory.setRightHand(weapon);
        // We need to find where 'weapon' came from and put 'currentHand' there.
        // This is complex with the current Inventory structure.
        // For now, let InventoryScreen handle the complex swapping.
        // This method might just be for "Action: Wield" from a list.
        eventManager.addEvent(new GameEvent("You wield the " + weapon.getDisplayName() + ".", 1.0f));
    }

    public void wear(Item armor, GameEventManager eventManager) {
        if (armor == null)
            return;
        equipArmor(armor, eventManager); // Reuse existing logic
    }

    public void takeOff(Item armor, GameEventManager eventManager) {
        if (armor == null) {
            eventManager.addEvent(new GameEvent("Take off what?", 1.0f));
            return;
        }
        if (equipment.getWornRing() == armor) {
            equipment.setWornRing(null);
            inventory.addItem(armor);
            eventManager.addEvent(new GameEvent("You remove the " + armor.getDisplayName() + " from your left hand.", 1.0f));
            return;
        }
        if (equipment.getWornRing2() == armor) {
            equipment.setWornRing2(null);
            inventory.addItem(armor);
            eventManager.addEvent(new GameEvent("You remove the " + armor.getDisplayName() + " from your right hand.", 1.0f));
            return;
        }
        eventManager.addEvent(new GameEvent("You remove the " + armor.getDisplayName() + ".", 1.0f));
    }

    private void equipRing(Item ring, GameEventManager eventManager) {
        if (ring.getCategory() != ItemCategory.RING)
            return;

        if (this.equipment.getWornRing() == null) {
            this.equipment.setWornRing(ring);
            if (inventory.getRightHand() == ring) inventory.setRightHand(null);
            else if (inventory.getLeftHand() == ring) inventory.setLeftHand(null);
            else inventory.removeItem(ring);
            eventManager.addEvent(new GameEvent("Equipped " + ring.getDisplayName() + " (Left Hand).", 2f));
        } else if (this.equipment.getWornRing2() == null) {
            this.equipment.setWornRing2(ring);
            if (inventory.getRightHand() == ring) inventory.setRightHand(null);
            else if (inventory.getLeftHand() == ring) inventory.setLeftHand(null);
            else inventory.removeItem(ring);
            eventManager.addEvent(new GameEvent("Equipped " + ring.getDisplayName() + " (Right Hand).", 2f));
        } else {
            Item previouslyWornRing = this.equipment.getWornRing();
            this.equipment.setWornRing(ring);
            if (inventory.getRightHand() == ring) {
                inventory.setRightHand(previouslyWornRing);
            } else if (inventory.getLeftHand() == ring) {
                inventory.setLeftHand(previouslyWornRing);
            } else {
                inventory.removeItem(ring);
                inventory.addItem(previouslyWornRing);
            }
            eventManager.addEvent(new GameEvent("Replaced Left Ring with " + ring.getDisplayName() + ".", 2f));
        }
    }

    // --- REPLACED: Flexible Equip Logic (Allows Swapping) ---
    private void equipArmor(Item armor, GameEventManager eventManager) {
        Item previousItem = null;
        String slotName = "";

        if (armor.isHelmet()) {
            previousItem = equipment.getWornHelmet();
            equipment.setWornHelmet(armor);
            slotName = "Head";
        } else if (armor.isShield()) {
            previousItem = equipment.getWornShield();
            equipment.setWornShield(armor);
            slotName = "Off-hand";
        } else if (armor.getType() == ItemType.GAUNTLETS) {
            previousItem = equipment.getWornGauntlets();
            equipment.setWornGauntlets(armor);
            slotName = "Hands";
        } else if (armor.getType() == ItemType.HAUBERK || armor.getType() == ItemType.BREASTPLATE || armor.isArmor()) {
            // Broad fallback for generic body armor if not one of the above special slots
            // But we should be careful about other slots like Leg/Boots if we can detect
            // them.
            // Currently check specific types first?

            if (armor.getType() == ItemType.BOOTS) {
                previousItem = equipment.getWornBoots();
                equipment.setWornBoots(armor);
                slotName = "Feet";
            } else if (armor.getType() == ItemType.LEGS) {
                previousItem = equipment.getWornLegs();
                equipment.setWornLegs(armor);
                slotName = "Legs";
            } else if (armor.getType() == ItemType.ARMS) {
                previousItem = equipment.getWornArms();
                equipment.setWornArms(armor);
                slotName = "Arms";
            } else if (armor.getType() == ItemType.CLOAK) {
                previousItem = equipment.getWornBack();
                equipment.setWornBack(armor);
                slotName = "Back";
            } else if (armor.getType() == ItemType.AMULET) {
                previousItem = equipment.getWornNeck();
                equipment.setWornNeck(armor);
                slotName = "Neck";
            } else if (armor.getType() == ItemType.EYES) {
                previousItem = equipment.getWornEyes();
                equipment.setWornEyes(armor);
                slotName = "Eyes";
            } else {
                // Default Body Slot
                previousItem = equipment.getWornChest();
                equipment.setWornChest(armor);
                slotName = "Chest";
            }
        } else {
            eventManager.addEvent(new GameEvent("Cannot equip this.", 2f));
            return;
        }

        // Put the new item in the slot, and put the old item (if any) in the hand
        inventory.setRightHand(previousItem);

        eventManager.addEvent(new GameEvent("Equipped " + armor.getDisplayName() + " to " + slotName, 2f));

        // --- NEW: Recalculate Stats Immediately ---
        // This ensures that if you equip "of Brawn", your Max HP updates.
        // Optional: If you want "Brawn" to heal you for the difference, do this:
        // Note: getEffectiveMax... calculates total with gear.

        // Log the change
        int newDefense = equipment.getArmorDefense();
        int newMaxHP = getEffectiveMaxWarStrength();

        BalanceLogger.getInstance().log("EQUIPMENT",
                "Changed " + slotName + ". AC: " + newDefense + " MaxHP: " + newMaxHP);
        // ------------------------------------------
    }

    public void heal(int amount) {
        stats.heal(amount);
        if (com.badlogic.gdx.Gdx.app != null) {
            com.badlogic.gdx.Gdx.app.log("Player", "Healed for " + amount + ". New HP: " + stats.getCurrentHP());
        }
    }

    private void useConsumable(Item item, GameEventManager eventManager) {
        switch (item.getType()) {
            case WAR_BOOK:
                stats.modifyBaseHP(10);
                this.heal(10); // Heal by amount gained? or full heal? ModifyBaseHP already heals.
                // Re-sync logic if needed. modifyBaseHP calls heal.
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("Your knowledge of war grows.", 2f));
                break;
            case SPIRITUAL_BOOK:
                stats.setMaxMP(stats.getMaxMP() + 10);
                stats.setCurrentMP(stats.getMaxMP());
                inventory.setRightHand(null);
                eventManager.addEvent(new GameEvent("Your spiritual knowledge grows.", 2f));
                break;
            default:
                eventManager.addEvent(new GameEvent("Cannot use this item.", 2f));
                break;
        }
    }

    private void updateVectors() {
        directionVector.set(facing.getVector());
        cameraPlane.set(-directionVector.y, directionVector.x).scl(0.66f);
    }

    // --- Stats Getters for New System ---
    public int getCurrentHP() {
        return stats.getCurrentHP();
    }

    public void setCurrentHP(int hp) {
        stats.setCurrentHP(hp);
    }

    public int getMaxHP() {
        return stats.getMaxHP();
    }

    public int getCurrentMP() {
        return stats.getCurrentMP();
    }

    public void setCurrentMP(int mp) {
        stats.setCurrentMP(mp);
    }

    public void restoreMP(int amount) {
        stats.setCurrentMP(Math.min(getEffectiveMaxMP(), stats.getCurrentMP() + amount));
    }

    public int getMaxMP() {
        return stats.getMaxMP();
    }

    public int getEffectiveMaxHP() {
        return stats.getMaxHP() + equipment.getEquippedModifierSum(ModifierType.BONUS_MAX_HP);
    }

    public int getEffectiveMaxMP() {
        return stats.getMaxMP() + equipment.getEquippedModifierSum(ModifierType.BONUS_MAX_MP);
    }

    public int getEffectiveDexterityModifier() {
        return (getEffectiveDexterity() - 10) / 2;
    }

    public int getEffectiveWisdomModifier() {
        return (getEffectiveWisdom() - 10) / 2;
    }

    public int getWisdomModifier() {
        return getEffectiveWisdomModifier();
    }

    public int getArmorClass() { // 5e Base 10 + Dex (capped by Armor tier) + Equipment AC
        int base = 10;
        int dexMod = getEffectiveDexterityModifier();
        int maxDex = (equipment != null) ? equipment.getMaxDexBonus() : 99;
        int appliedDex = Math.min(maxDex, dexMod);
        if (appliedDex < 0) appliedDex = 0;

        int ac = base + appliedDex;
        if (equipment != null) {
            ac += equipment.getACBonus();
        }
        if (statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.HARDENED)) {
            ac += 4;
        }
        return ac;
    }

    public int getArmorDefense() {
        return getArmorClass();
    }

    public int getTotalDamageReduction() {
        int dr = (equipment != null) ? equipment.getTotalDamageReduction() : 0;
        if (inventory != null && inventory.getLeftHand() != null) {
            Item offhand = inventory.getLeftHand();
            if (offhand.isShield() && (equipment == null || equipment.getWornShield() != offhand)) {
                dr += offhand.getDamageReduction();
            }
        }
        return dr;
    }

    // Deprecated Aliases
    public int getWarStrength() {
        return getCurrentHP();
    }

    public void setWarStrength(int val) {
        setCurrentHP(val);
    }

    public int getSpiritualStrength() {
        return getCurrentMP();
    }

    public int getEffectiveMaxWarStrength() {
        return getEffectiveMaxHP();
    }

    public int getEffectiveMaxSpiritualStrength() {
        return getEffectiveMaxMP();
    }

    public void rest(GameEventManager eventManager) {
        if (stats.getFood() > 0) {
            stats.setFood(stats.getFood() - 1);

            int warStrengthGained = 5;
            int spiritualStrengthGained = 5;

            // Check for Level Up
            if (stats.canLevelUp()) {
                performLevelUp(eventManager); // Call the private helper that calls stats.performLevelUp()
                // Don't consume food if leveling up? Or maybe require food TO level up?
                // Let's require food to level up as well.
            } else {
                // Standard Rest (Heal)
                this.setWarStrength(
                        Math.min(this.getEffectiveMaxWarStrength(), this.getWarStrength() + warStrengthGained));
                stats.setSpiritualStrength(Math.min(this.getEffectiveMaxSpiritualStrength(),
                        stats.getSpiritualStrength() + spiritualStrengthGained));
                equipment.fullyRechargeRings();

                eventManager.addEvent(new GameEvent(
                        ("WS restored to " + stats.getWarStrength() + ", SS restored to "
                                + stats.getSpiritualStrength() + ". Magic rings recharged."),
                        2f));
            }

            // --- LOGGING ---
            BalanceLogger.getInstance().logEconomy("RES_USED", "Food", 1);
            // ---------------
        } else {
            eventManager.addEvent(new GameEvent("You have no food to rest.", 2f));
        }
    }

    public void restAtSanctuary(GameEventManager eventManager) {
        if (injuryManager != null) {
            injuryManager.restAtSanctuary(this, eventManager);
        }
        this.setWarStrength(this.getEffectiveMaxWarStrength());
        stats.setSpiritualStrength(this.getEffectiveMaxSpiritualStrength());
        equipment.fullyRechargeRings();
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent("Deep sanctuary sleep restores your body, mind, and spirit!", 2.5f));
        }
    }

    public void setFacing(Direction facing) {
        this.facing = facing;
        updateVectors();
    }

    public int takeDamage(int amount, DamageType type) {
        // Dodge check: AGI-based chance to avoid a connected hit entirely.
        float dodgeChance = getDodgeChance();
        if (dodgeChance > 0f && new java.util.Random().nextFloat() < dodgeChance) {
            if (com.badlogic.gdx.Gdx.app != null) {
                com.badlogic.gdx.Gdx.app.log("Player", "Dodged! (dodge chance: " + dodgeChance + ")");
            }
            return 0;
        }

        // 5e Elemental Resistances (50% reduction)
        boolean hasResist = false;
        if (statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.INVULNERABILITY)) {
            hasResist = true;
        } else if (type == DamageType.FIRE && ((statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_FIRE)) || equipment.hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.RESISTANCE_FIRE))) {
            hasResist = true;
        } else if (type == DamageType.ICE && ((statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_COLD)) || equipment.hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.RESISTANCE_COLD) || equipment.hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.WARMTH))) {
            hasResist = true;
        } else if (type == DamageType.LIGHT && ((statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_LIGHTNING)) || equipment.hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.RESISTANCE_LIGHTNING))) {
            hasResist = true;
        } else if (type == DamageType.POISON && ((statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_ACID)) || equipment.hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.RESISTANCE_ACID))) {
            hasResist = true;
        } else if (type == DamageType.DARK && ((statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.RESIST_NECROTIC)) || equipment.hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.RESISTANCE_NECROTIC))) {
            hasResist = true;
        }

        if (hasResist) {
            amount = Math.max(1, amount / 2);
        }

        if (type == DamageType.PHYSICAL) {
            // AC bonus = evasion threshold used in the to-hit roll, not a damage absorber.
            // Flat Armor Damage Reduction (DR) + BONUS_ABSORB mitigates connected hits
            int dr = getTotalDamageReduction();
            int absorb = equipment.getEquippedModifierSum(ModifierType.BONUS_ABSORB);
            int totalMitigation = dr + absorb;
            if (totalMitigation > 0 && amount > 0) {
                int mitigated = Math.min(totalMitigation, amount - 1);
                if (mitigated > 0) {
                    amount -= mitigated;
                    com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordDamageMitigated(mitigated);
                }
            }
        }

        // Chip damage: always take at least 1 on a connected, non-dodged hit.
        if (amount < 1) amount = 1;

        int finalDamage = Math.max(1, (int) (amount * stats.getVulnerabilityMultiplier()));

        // Cheat-Death check: Ring of Evasion (Charged)
        if (finalDamage >= (stats.getCurrentHP() + stats.getTemporaryHP()) && equipment.getRingCharges(com.bpm.minotaur.gamedata.item.RingEffectType.EVASION_CHARGED) > 0) {
            equipment.expendRingCharge(com.bpm.minotaur.gamedata.item.RingEffectType.EVASION_CHARGED);
            if (com.badlogic.gdx.Gdx.app != null) {
                com.badlogic.gdx.Gdx.app.log("Player", "Ring of Evasion triggered! Negated fatal blow.");
            }
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Your Ring of Evasion flashes brilliantly, negating the fatal blow!", 3.0f));
            }
            return 0;
        }

        stats.setWarStrength(stats.getWarStrength() - finalDamage);

        if (com.badlogic.gdx.Gdx.app != null) {
            com.badlogic.gdx.Gdx.app.log("Player", "Taken Damage: " + finalDamage + " (Adj. Amount: " + amount + ")");
        }

        return finalDamage;
    }

    public void takeSpiritualDamage(int amount, DamageType type) {
        int damageReduction = equipment.getRingDefense();
        int resistance = getResistance(type);
        // WIS modifier reduces spiritual damage taken (positive WIS only).
        int wisReduction = Math.max(0, (getEffectiveWisdom() - 10) / 2);

        int finalDamage = (int) (amount * stats.getVulnerabilityMultiplier());
        finalDamage = Math.max(0, finalDamage - damageReduction - resistance - wisReduction);

        stats.setSpiritualStrength(Math.max(0, stats.getSpiritualStrength() - finalDamage));
    }

    public Item getWornRing() {
        return equipment.getWornRing();
    }

    private int getResistance(DamageType type) {
        if (type == DamageType.PHYSICAL || type == DamageType.SPIRITUAL) {
            return 0;
        }

        ModifierType modTypeToFind;
        switch (type) {
            case FIRE:
                modTypeToFind = ModifierType.RESIST_FIRE;
                break;
            case ICE:
                modTypeToFind = ModifierType.RESIST_ICE;
                break;
            case POISON:
                modTypeToFind = ModifierType.RESIST_POISON;
                break;
            case BLEED:
                modTypeToFind = ModifierType.RESIST_BLEED;
                break;
            case DISEASE:
                modTypeToFind = ModifierType.RESIST_DISEASE;
                break;
            case DARK:
                modTypeToFind = ModifierType.RESIST_DARK;
                break;
            case LIGHT:
                modTypeToFind = ModifierType.RESIST_LIGHT;
                break;
            case SORCERY:
                modTypeToFind = ModifierType.RESIST_SORCERY;
                break;
            default:
                return 0;
        }

        return equipment.getEquippedModifierSum(modTypeToFind);
    }

    public void moveForward(Maze maze, GameEventManager eventManager, GameMode gameMode) {
        moveForward(maze, eventManager, gameMode, null);
    }

    public void moveForward(Maze maze, GameEventManager eventManager, GameMode gameMode, SoundManager soundManager) {
        move(facing, maze, eventManager, gameMode, soundManager);
    }

    public void moveBackward(Maze maze, GameEventManager eventManager, GameMode gameMode) {
        move(facing.getOpposite(), maze, eventManager, gameMode, null);
    }

    public void moveBackward(Maze maze, GameEventManager eventManager, GameMode gameMode, SoundManager soundManager) {
        move(facing.getOpposite(), maze, eventManager, gameMode, soundManager);
    }

    public void move(Direction direction, Maze maze, GameEventManager eventManager, GameMode gameMode) {
        move(direction, maze, eventManager, gameMode, null);
    }

    public void move(Direction direction, Maze maze, GameEventManager eventManager, GameMode gameMode,
            SoundManager soundManager) {
        int currentX = (int) position.x;
        int currentY = (int) position.y;

        int nextX = currentX + (int) direction.getVector().x;
        int nextY = currentY + (int) direction.getVector().y;
        GridPoint2 nextTile = new GridPoint2(nextX, nextY);

        Object nextObject = maze.getGameObjectAt(nextX, nextY);

        if (nextObject instanceof Gate gate && gameMode == GameMode.ADVANCED) {
            if (gate.isChunkTransitionGate() && gate.getState() == Gate.GateState.OPEN) {
                eventManager.addEvent(new GameEvent(GameEvent.EventType.CHUNK_TRANSITION, gate));
                return;
            }
        }

        // --- BUMP TO OPEN DOOR ---
        if (direction == facing && nextObject instanceof Door door) {
            if (door.getState() == Door.DoorState.CLOSED || door.getState() == Door.DoorState.CLOSING) {
                door.startOpening();
                eventManager.addEvent(new GameEvent("You open the door.", 1f));
                UnlockManager.getInstance().incrementStat("doors", 1);
                if (soundManager != null) {
                    soundManager.playDoorOpenSound();
                }
                return;
            }
        }

        // --- GHOST WALL & MOVEMENT HANDLING ---
        boolean isGhostWall = com.bpm.minotaur.managers.DimensionalManager.getInstance().isGhostWall(0, 0, currentX, currentY, direction);
        if (!isGhostWall) {
            if (!maze.isPassable(nextX, nextY)) {
                // Check specific reasons for blockage
                if (Gdx.app != null && maze.getWallDataAt(nextX, nextY) == 1) {
                    Gdx.app.log("Player [DEBUG]", "Move blocked by WALL data at (" + nextX + "," + nextY + ")");
                }
                Item item = maze.getItems().get(nextTile);
                if (Gdx.app != null && item != null && item.isImpassable()) {
                    Gdx.app.log("Player [DEBUG]", "Move blocked by IMPASSABLE ITEM: " + item.getDisplayName() + " at ("
                            + nextX + "," + nextY + ")");
                }
                // Check for doors/gates
                Object obj = maze.getGameObjectAt(nextX, nextY);
                if (Gdx.app != null && obj instanceof Door && ((Door) obj).getState() != Door.DoorState.OPEN
                        && ((Door) obj).getState() != Door.DoorState.OPENING) {
                    Gdx.app.log("Player [DEBUG]", "Move blocked by CLOSED DOOR at (" + nextX + "," + nextY + ")");
                }
                return;
            }

            if (maze.isWallBlocking(currentX, currentY, direction)) {
                if (Gdx.app != null) {
                    Gdx.app.log("Player [DEBUG]",
                            "Move blocked by WALL MASK from (" + currentX + "," + currentY + ") facing " + direction);
                }
                return;
            }
        } else {
            eventManager.addEvent(new GameEvent("You phase through the ethereal Ghost Wall...", 1.2f));
        }

        boolean bumpedDormantStatue = false;
        if (maze.getScenery().containsKey(nextTile)) {
            Scenery s = maze.getScenery().get(nextTile);
            if (s.isImpassable()) {
                if (Gdx.app != null) {
                    Gdx.app.log("Player [DEBUG]", "Move blocked by SCENERY at (" + nextX + "," + nextY + ")");
                }
                return;
            }
            if (s.getType() == Scenery.SceneryType.STATUE && maze.getEventAt(nextX, nextY) == null) {
                // Depleted statue: its encounter has already been resolved
                bumpedDormantStatue = true;
            }
        }

        position.set(nextX + 0.5f, nextY + 0.5f);
        UnlockManager.getInstance().incrementStat("steps", 1);

        if (maze.getLiquidManager() != null) {
            maze.getLiquidManager().onPlayerStep(nextX, nextY, this, eventManager);
        }

        // --- Auto-pickup Ammunition (Quiver / Arrows / Bolts) on step ---
        Item steppedItem = maze.getItems().get(nextTile);
        if (steppedItem != null && (steppedItem.getType() == Item.ItemType.QUIVER || steppedItem.isAmmunition())) {
            if (soundManager != null) {
                soundManager.playPickupItemSound();
            }
            int arrowsFound = new Random().nextInt(7) + 8;
            stats.addArrows(arrowsFound);
            maze.getItems().remove(nextTile);
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Collected " + arrowsFound + " " + steppedItem.getDisplayName() + "! Total: " + stats.getArrows(), 2.5f));
            }
            BalanceLogger.getInstance().logEconomy("RES_GAIN", "Arrows", arrowsFound);
        }

        // --- VOID SIGHT LORE INSCRIPTIONS ---
        if (com.bpm.minotaur.managers.DimensionalManager.getInstance().isInVoid()) {
            String voidLore = com.bpm.minotaur.managers.DimensionalManager.getInstance().getVoidLoreAt(0, 0, nextX, nextY);
            if (voidLore != null) {
                eventManager.addEvent(new GameEvent(voidLore, 4.0f));
            }
        }

        String eventId = maze.getEventAt(nextX, nextY);
        if (eventId != null) {
            eventManager.addEvent(new GameEvent(GameEvent.EventType.ENCOUNTER_TRIGGERED, eventId));
            maze.removeEvent(nextX, nextY);
        } else if (bumpedDormantStatue) {
            eventManager.addEvent(new GameEvent(
                    "An ancient carved monument. The residual magic has gone dormant.", 2.5f));
        }
    }

    public void turnLeft() {
        facing = facing.getLeft();
        updateVectors();
    }

    public void turnRight() {
        facing = facing.getRight();
        updateVectors();
    }

    public void interact(Maze maze, GameEventManager eventManager, SoundManager soundManager, GameMode gameMode,
            WorldManager worldManager) {
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 targetTile = new GridPoint2(targetX, targetY);

        // 1. Handle Gates (Keep existing logic)
        Gate gateObj = maze.getGates().get(targetTile);
        if (gateObj != null) {
            if (gameMode == GameMode.ADVANCED && gateObj.isChunkTransitionGate()) {
                if (gateObj.getState() == Gate.GateState.CLOSED) {
                    gateObj.startOpening(worldManager);
                    eventManager.addEvent(new GameEvent("The gate rumbles and opens...", 2f));
                    if (soundManager != null)
                        soundManager.playDoorOpenSound();
                }
            } else {
                useGate(maze, eventManager, gateObj);
            }
            return;
        }

        // 2. Handle Barred Shelter Window
        Object obj = maze.getGameObjectAt(targetX, targetY);
        if (obj instanceof Window) {
            String weatherStr = "clear";
            String intensityStr = "";
            if (worldManager != null && worldManager.getWeatherManager() != null) {
                weatherStr = worldManager.getWeatherManager().getCurrentWeather().name().toLowerCase();
                intensityStr = worldManager.getWeatherManager().getCurrentIntensity().name().toLowerCase() + " ";
            }
            String phaseStr = "day";
            if (worldManager != null && worldManager.getDayNightManager() != null) {
                phaseStr = worldManager.getDayNightManager().getPhase().name().toLowerCase();
            }
            float bridgeIntegrity = DoomManager.getInstance() != null ? DoomManager.getInstance().getBridgeIntegrity() : 0f;

            String msg = String.format("You peer through the iron bars into the %s wild: %s%s skies overhead. Doom Bridge integrity: %.0f%%.",
                    phaseStr, intensityStr, weatherStr, bridgeIntegrity);
            eventManager.addEvent(new GameEvent(msg, 4f));
            if (soundManager != null) {
                soundManager.playDoorOpenSound();
            }
            return;
        }

        // 3. Handle Doors (UPDATED: Toggle Logic with Occupancy Lock)
        if (obj instanceof Door) {
            Door door = (Door) obj;
            if (door.getState() == Door.DoorState.OPEN || door.getState() == Door.DoorState.OPENING) {
                int px = (int) position.x;
                int py = (int) position.y;
                if ((px == targetX && py == targetY) || maze.getMonsters().containsKey(targetTile)) {
                    eventManager.addEvent(new GameEvent("The doorway is blocked!", 1.5f));
                    return;
                }
            }

            // Use the toggle method
            maze.toggleDoorAt(targetX, targetY);

            // Check state AFTER toggle to determine event/sound
            if (door.getState() == Door.DoorState.OPENING) {
                eventManager.addEvent(new GameEvent("You open the door.", 1f));
                UnlockManager.getInstance().incrementStat("doors", 1);
                if (soundManager != null)
                    soundManager.playDoorOpenSound();
            } else if (door.getState() == Door.DoorState.CLOSING) {
                eventManager.addEvent(new GameEvent("You close the door.", 1f));
                if (soundManager != null)
                    soundManager.playDoorOpenSound();
            }
            return;
        }

        // 3. Handle Containers (Keep existing logic)
        Item itemInFront = maze.getItems().get(targetTile);
        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_CHEST) {
            // Home chest is persistent and handled by GameScreen / ShelterChestScreen
            return;
        }

        if (itemInFront != null && itemInFront.getCategory() == ItemCategory.CONTAINER) {
            String containerName = itemInFront.getDisplayName();

            if (itemInFront.isLocked()) {
                Item key = findKey();
                if (key != null && itemInFront.unlocks(key)) {
                    itemInFront.unlock();
                    consumeKey(key);
                    eventManager.addEvent(new GameEvent("You unlocked the " + containerName + "!", 2f));
                } else {
                    eventManager.addEvent(new GameEvent("The " + containerName + " is locked.", 2f));
                    return;
                }
            }

            List<Item> contents = new ArrayList<>(itemInFront.getContents());
            maze.getItems().remove(targetTile);

            if (contents.isEmpty()) {
                eventManager.addEvent(new GameEvent("The " + containerName + " is empty.", 2f));
            } else {
                eventManager.addEvent(new GameEvent("You open the " + containerName + ".", 2f));
                for (Item contentItem : contents) {
                    GridPoint2 dropTile = targetTile;
                    if (maze.getItems().containsKey(dropTile)) {
                        dropTile = new GridPoint2((int) position.x, (int) position.y);
                    }
                    if (!maze.getItems().containsKey(dropTile)) {
                        contentItem.getPosition().set(dropTile.x + 0.5f, dropTile.y + 0.5f);
                        maze.addItem(contentItem);
                    } else {
                        eventManager
                                .addEvent(new GameEvent("No space to drop " + contentItem.getDisplayName() + ".", 2f));
                    }
                }
            }
            return;
        }

        // 4. Handle Corpses (Recovery or Butchering)
        if (itemInFront != null && itemInFront.getType() == Item.ItemType.CORPSE) {
            // Check if this corpse holds stored player items (death marker)
            if (itemInFront.getContents() != null && !itemInFront.getContents().isEmpty()) {
                eventManager.addEvent(new GameEvent("You search your remains and recover your lost gear!", 3f));
                List<Item> stored = new ArrayList<>(itemInFront.getContents());
                List<Item> unrecovered = new ArrayList<>();
                int recoveredCount = 0;
                for (Item it : stored) {
                    if (inventory.pickup(it)) {
                        recoveredCount++;
                    } else {
                        unrecovered.add(it);
                    }
                }
                itemInFront.getContents().clear();
                if (unrecovered.isEmpty()) {
                    maze.getItems().remove(targetTile);
                    eventManager.addEvent(new GameEvent("Recovered all " + recoveredCount + " items from remains.", 2f));
                } else {
                    itemInFront.getContents().addAll(unrecovered);
                    eventManager.addEvent(new GameEvent("Pack full! " + unrecovered.size() + " items remain in your corpse.", 2f));
                }
                return;
            }

            if (hasButcheringTool()) {
                eventManager.addEvent(new GameEvent("You butcher the corpse.", 2f));
                maze.getItems().remove(targetTile);

                // Probabilistic Loot Generation scaled by Player Luck
                int luck = stats != null ? stats.getLuck() : 0;
                int meatChance = Math.min(75, 35 + (luck * 2));
                int boneChance = Math.min(70, 30 + (luck * 2));
                boolean harvestedAny = false;

                if (Math.random() * 100 < meatChance) {
                    Item meat = itemDataManager.createItem(Item.ItemType.MEAT, targetX, targetY, ItemColor.RED,
                            assetManager);
                    meat.setCorpseSource(itemInFront.getCorpseSource());
                    harvestedAny = true;
                    if (inventory.pickupToBackpack(meat)) {
                        eventManager.addEvent(new GameEvent("You harvest some " + meat.getDisplayName() + ".", 2f));
                    } else {
                        maze.addItem(meat);
                        eventManager.addEvent(new GameEvent("Inventory full! " + meat.getDisplayName() + " dropped.", 2f));
                    }
                }

                if (Math.random() * 100 < boneChance) {
                    Item bone = itemDataManager.createItem(Item.ItemType.BONE, targetX, targetY, ItemColor.WHITE,
                            assetManager);
                    bone.setCorpseSource(itemInFront.getCorpseSource());
                    harvestedAny = true;
                    if (inventory.pickupToBackpack(bone)) {
                        eventManager.addEvent(new GameEvent("You harvest a " + bone.getDisplayName() + ".", 2f));
                    } else {
                        maze.addItem(bone);
                        eventManager.addEvent(new GameEvent("Inventory full! " + bone.getDisplayName() + " dropped.", 2f));
                    }
                }

                if (!harvestedAny) {
                    eventManager.addEvent(new GameEvent("You butcher the corpse, but harvest nothing of value.", 2f));
                }
            } else {
                eventManager.addEvent(new GameEvent("You need a sharp tool (Axe/Knife) to butcher this.", 2f));
            }
            return;
        }

        // 5. Handle Encounter / Statues in front of the player or under feet
        String targetEventId = maze.getEventAt(targetX, targetY);
        if (targetEventId != null) {
            eventManager.addEvent(new GameEvent(GameEvent.EventType.ENCOUNTER_TRIGGERED, targetEventId));
            maze.removeEvent(targetX, targetY);
            return;
        }

        int px = (int) position.x;
        int py = (int) position.y;
        String standingEventId = maze.getEventAt(px, py);
        if (standingEventId != null) {
            eventManager.addEvent(new GameEvent(GameEvent.EventType.ENCOUNTER_TRIGGERED, standingEventId));
            maze.removeEvent(px, py);
            return;
        }

        eventManager.addEvent(new GameEvent("Nothing to interact with here.", 2f));
    }

    private Item findKey() {
        Inventory inv = getInventory();
        if (inv.getRightHand() != null && inv.getRightHand().getType() == Item.ItemType.KEY) {
            return inv.getRightHand();
        }
        if (inv.getLeftHand() != null && inv.getLeftHand().getType() == Item.ItemType.KEY) {
            return inv.getLeftHand();
        }
        for (Item item : inv.getBackpack()) {
            if (item != null && item.getType() == Item.ItemType.KEY) {
                return item;
            }
        }
        for (Item item : inv.getMainInventory()) {
            if (item.getType() == Item.ItemType.KEY) {
                return item;
            }
        }
        return null;
    }

    public int getDexterity() {
        return stats.getDexterity();
    }

    private void consumeKey(Item keyToRemove) {
        Inventory inv = getInventory();
        if (inv.getRightHand() == keyToRemove) {
            inv.setRightHand(null);
            return;
        }
        if (inv.getLeftHand() == keyToRemove) {
            inv.setLeftHand(null);
            return;
        }
        for (int i = 0; i < inv.getBackpack().length; i++) {
            if (inv.getBackpack()[i] == keyToRemove) {
                inv.getBackpack()[i] = null;
                return;
            }
        }
        inv.getMainInventory().remove(keyToRemove);
    }

    private void useGate(Maze maze, GameEventManager eventManager, Gate gate) {
        eventManager.addEvent(new GameEvent("You touch the strange mural...", 2f));

        int outcome = new Random().nextInt(4);
        switch (outcome) {
            case 1:
                int temp = stats.getWarStrength();
                setWarStrength(stats.getSpiritualStrength());
                stats.setSpiritualStrength(Math.min(temp, stats.getMaxMP()));
                eventManager.addEvent(new GameEvent("Your strengths feel reversed!", 2f));
                break;
            case 2:
                setWarStrength((int) (stats.getWarStrength() * 0.75f));
                eventManager.addEvent(new GameEvent("You feel weaker!", 2f));
                break;
            case 3:
                stats.setSpiritualStrength(
                        Math.min((int) (stats.getSpiritualStrength() * 0.75f), stats.getMaxMP()));
                eventManager.addEvent(new GameEvent("Your spirit feels drained!", 2f));
                break;
            default:
                eventManager.addEvent(new GameEvent("Nothing seems to happen.", 2f));
                break;
        }

        List<GridPoint2> emptyTiles = new ArrayList<>();
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                if (maze.getWallDataAt(x, y) == 0
                        && maze.getGameObjectAt(x, y) == null
                        && !maze.getItems().containsKey(new GridPoint2(x, y))
                        && !maze.getMonsters().containsKey(new GridPoint2(x, y))) {
                    emptyTiles.add(new GridPoint2(x, y));
                }
            }
        }

        if (!emptyTiles.isEmpty()) {
            GridPoint2 currentPos = new GridPoint2((int) position.x, (int) position.y);
            GridPoint2 newPos;
            int attempts = 0;
            do {
                newPos = emptyTiles.get(new Random().nextInt(emptyTiles.size()));
                attempts++;
            } while (newPos.equals(currentPos) && attempts < 10 && emptyTiles.size() > 1);

            position.set(newPos.x + 0.5f, newPos.y + 0.5f);
            eventManager.addEvent(new GameEvent("Space warps around you!", 2f));
        } else {
            eventManager.addEvent(new GameEvent("The mural shimmers weakly.", 2f));
        }
    }

    // In interactWithItem, we need to detect the portal.
    // The Portal is an ITEM on the ground? Or a Tile?
    // User requested "encounter a mysterious portal".
    // I implemented it as an ItemType.MYSTERIOUS_PORTAL.

    // ... inside interactWithItem ...

    public void addArrows(int amount) {
        stats.addArrows(amount);
    }

    public void addFood(int amount) {
        stats.addFood(amount);
    }

    public void decrementArrow() {
        stats.decrementArrow();
        // --- LOGGING ---
        BalanceLogger.getInstance().logEconomy("RES_USED", "Arrow", 1);
        // ---------------
    }

    public void addExperience(int amount, GameEventManager eventManager) {
        if (amount <= 0)
            return;

        boolean readyToLevel = stats.addExperience(amount);

        eventManager.addEvent(new GameEvent("You gained " + amount + " experience!", 2f));

        if (readyToLevel) {
            eventManager
                    .addEvent(new GameEvent("You have enough experience to level up! Sleep in a bed to advance.", 3f));
        }
    }

    private void performLevelUp(GameEventManager eventManager) {
        stats.performLevelUp();
        soundManager.playPlayerLevelUpSound();
        eventManager.addEvent(new GameEvent("You reached level " + stats.getLevel() + "!", 3f));
        eventManager.addEvent(new GameEvent("Attack Bonus increased to +" + stats.getAttackModifier() + "!", 2f));
    }

    public void takeStatusEffectDamage(int amount, DamageType type) {
        int resistance = getResistance(type);

        int finalDamage = (int) (amount * stats.getVulnerabilityMultiplier());
        finalDamage = Math.max(0, finalDamage - resistance);

        stats.setWarStrength(stats.getWarStrength() - finalDamage);

        if (stats.getWarStrength() < 0) {
            stats.setWarStrength(0);
        }
    }

    /** Dodge chance as float (0.0–1.0), same formula as takeDamage(), exposed for UI display. */
    public float getDodgeChance() {
        int agiMod = Math.max(0, (stats.getAgility() - 10) / 2);
        float chance = agiMod * 0.04f + equipment.getEquippedModifierSum(ModifierType.BONUS_DODGE) / 100f;
        chance += 0.12f * equipment.countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.EVASION);
        if (statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.DIMINUTIVE)) {
            chance += 0.15f;
        }
        return chance;
    }

    /** Flat physical damage absorbed on a connected hit (BONUS_ABSORB from equipment). */
    public int getAbsorb() {
        return equipment.getEquippedModifierSum(ModifierType.BONUS_ABSORB);
    }

    /** Total spiritual damage reduction: ring defense + positive WIS modifier. */
    public int getSpiritualDefense() {
        return equipment.getRingDefense() + Math.max(0, (getEffectiveWisdom() - 10) / 2);
    }

    /** Elemental resistance for a given damage type, for UI display. */
    public int getElementalResistance(DamageType type) {
        return getResistance(type);
    }

    public void takeTrueDamage(int amount) {
        if (amount <= 0)
            return;
        stats.setCurrentHP(Math.max(0, stats.getCurrentHP() - amount));
    }

    /** To-hit bonus: quarter level + DEX modifier, delegated to PlayerStats, plus equipment and Heroism. */
    public int getToHitBonus() {
        int bonus = stats.getToHitBonus() + equipment.getEquippedModifierSum(ModifierType.BONUS_TO_HIT);
        if (statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.HEROISM)) {
            bonus += 2;
        }
        return bonus;
    }

    // --- Equipment-adjusted primary attributes ---

    /** Strength including toxicity bonus (threshold-shifted by Fortitude), plus equipment BONUS_STRENGTH, stacked rings, and Giant Strength override. */
    public int getEffectiveStrength() {
        int baseStr = stats.getEffectiveStrength(getToxicityThresholdShift())
                + equipment.getEquippedModifierSum(ModifierType.BONUS_STRENGTH)
                + equipment.countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.STRENGTH) * 5;
        if (statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.GIANT_STRENGTH)) {
            com.bpm.minotaur.gamedata.effects.ActiveStatusEffect eff = statusManager.getEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.GIANT_STRENGTH);
            int giantStr = (eff != null && eff.getPotency() > 0) ? eff.getPotency() : 21;
            return Math.max(baseStr, giantStr);
        }
        return baseStr;
    }

    /** Dexterity including equipment BONUS_DEXTERITY and stacked Rings of Dexterity. */
    public int getEffectiveDexterity() {
        int bonus = equipment.countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.DEXTERITY) * 5;
        return stats.getDexterity() + equipment.getEquippedModifierSum(ModifierType.BONUS_DEXTERITY) + bonus;
    }

    /** Constitution including equipment BONUS_CONSTITUTION and stacked Rings of Constitution. */
    public int getEffectiveConstitution() {
        int bonus = equipment.countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.CONSTITUTION) * 5;
        return stats.getConstitution() + equipment.getEquippedModifierSum(ModifierType.BONUS_CONSTITUTION) + bonus;
    }

    /** Intelligence including equipment BONUS_INTELLIGENCE and stacked Rings of Intelligence. */
    public int getEffectiveIntelligence() {
        int bonus = equipment.countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.INTELLIGENCE) * 5;
        return stats.getIntelligence() + equipment.getEquippedModifierSum(ModifierType.BONUS_INTELLIGENCE) + bonus;
    }

    /** Wisdom including equipment BONUS_WISDOM and stacked Rings of Wisdom. */
    public int getEffectiveWisdom() {
        int bonus = equipment.countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.WISDOM) * 5;
        return stats.getWisdom() + equipment.getEquippedModifierSum(ModifierType.BONUS_WISDOM) + bonus;
    }

    /** Agility including equipment BONUS_AGILITY and stacked Rings of Agility. */
    public int getEffectiveAgility() {
        int bonus = equipment.countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.AGILITY) * 5;
        return stats.getAgility() + equipment.getEquippedModifierSum(ModifierType.BONUS_AGILITY) + bonus;
    }

    public int getEffectiveCharisma() {
        return stats.getCharisma() + equipment.getEquippedModifierSum(ModifierType.BONUS_CHARISMA);
    }

    /**
     * How many points to shift toxicity tier thresholds upward.
     * Sources: BONUS_TOXICITY_THRESHOLD from equipment + stacked Rings of Fortitude (+15 each).
     * A shift of 15 means medium tier activates at 41+ instead of 26+.
     */
    public int getToxicityThresholdShift() {
        int shift = equipment.getEquippedModifierSum(ModifierType.BONUS_TOXICITY_THRESHOLD);
        shift += equipment.countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.FORTITUDE) * 15;
        return shift;
    }

    /**
     * Damage bonus: (effectiveStr - 10) / 2 plus flat BONUS_DAMAGE from equipment, plus Growth bonus.
     * Uses player-level effective strength so BONUS_STRENGTH items flow through.
     */
    public int getDamageBonus() {
        int strDmg = Math.max(0, (getEffectiveStrength() - 10) / 2);
        int bonus = strDmg + equipment.getEquippedModifierSum(ModifierType.BONUS_DAMAGE);
        if (statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.ENLARGED)) {
            bonus += 2;
        }
        return bonus;
    }

    public int getEffectiveStrengthModifier() {
        return (getEffectiveStrength() - 10) / 2;
    }

    public int getFinesseDamageBonus() {
        int stat = Math.max(getEffectiveStrength(), getEffectiveDexterity());
        int statMod = Math.max(0, (stat - 10) / 2);
        int bonus = statMod + equipment.getEquippedModifierSum(ModifierType.BONUS_DAMAGE);
        if (statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.ENLARGED)) {
            bonus += 2;
        }
        return bonus;
    }

    public int getFinesseToHitBonus() {
        int baseBonus = 2 + (stats.getLevel() / 2);
        int stat = Math.max(getEffectiveStrength(), getEffectiveDexterity());
        int bonus = baseBonus + (stat - 10) / 2 + equipment.getEquippedModifierSum(ModifierType.BONUS_TO_HIT);
        if (statusManager != null && statusManager.hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.HEROISM)) {
            bonus += 2;
        }
        return bonus;
    }

    /** Flat spell damage bonus: INT modifier + equipment BONUS_SPELL_POWER + stacked Rings of Spell Mastery. */
    public int getSpellPower() {
        int intMod = (getEffectiveIntelligence() - 10) / 2;
        int ringBonus = equipment.countRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.SPELL_MASTERY) * 6;
        return intMod + equipment.getEquippedModifierSum(ModifierType.BONUS_SPELL_POWER) + ringBonus;
    }

    public boolean imbueRingOfSpellStoring(Item ring, String spellId) {
        if (ring == null || ring.getRingEffect() != com.bpm.minotaur.gamedata.item.RingEffectType.SPELL_STORING) {
            return false;
        }
        if (spellId == null || !knownSpellIds.contains(spellId.toUpperCase())) {
            return false;
        }
        if (ring.getMaxCharges() < 1) {
            ring.setMaxCharges(1);
        }
        ring.setStoredSpellId(spellId.toUpperCase());
        ring.setCurrentCharges(1);
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent("Inscribed " + spellId.toUpperCase() + " into Ring of Spell Storing!", 2.0f));
        }
        return true;
    }

    /**
     * Effective stamina (max dice selectable): base + CON bonus + equipment BONUS_STAMINA.
     * Minimum 1.
     */
    public int getEffectiveStamina() {
        int conBonus = Math.max(0, (getEffectiveConstitution() - 10) / 2);
        int equipBonus = equipment.getEquippedModifierSum(ModifierType.BONUS_STAMINA);
        return Math.max(1, stats.getBaseStamina() + conBonus + equipBonus);
    }

    /**
     * Crit chance as a float (0.0–1.0).
     * Base 5% + DEX + luck + BONUS_CRIT_CHANCE + Ring of Critical Edge (+10%).
     */
    public float getCritChance() {
        float chance = stats.getBaseCritChance(); // 5% base
        chance += Math.max(0, (getEffectiveDexterity() - 10) / 2) * 0.02f; // effective DEX
        chance += getLuck() * 0.005f;                                        // effective luck (incl. equipment)
        chance += equipment.getEquippedModifierSum(ModifierType.BONUS_CRIT_CHANCE) / 100f;
        if (equipment.hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.CRITICAL_EDGE)) chance += 0.10f;
        if (inventory.getLeftHand() != null && inventory.getLeftHand().getType() == ItemType.BRASS_LANTERN) chance += 0.10f;
        return Math.min(0.95f, chance);
    }

    public Item getEquippedLeft() {
        return inventory.getLeftHand();
    }

    /**
     * Crit damage multiplier. Base 2.0×, each BONUS_CRIT_MULTIPLIER point adds 0.1×.
     */
    public float getCritMultiplier() {
        return 2.0f + equipment.getEquippedModifierSum(ModifierType.BONUS_CRIT_MULTIPLIER) * 0.1f;
    }

    /**
     * Legacy: returns full level for UI display. Do NOT use in combat math.
     * Use getToHitBonus() and getDamageBonus() separately.
     */
    public int getAttackModifier() {
        return stats.getAttackModifier() + equipment.getEquippedModifierSum(ModifierType.BONUS_DAMAGE);
    }

    public int getLevel() {
        return stats.getLevel();
    }

    public int getExperience() {
        return stats.getExperience();
    }

    public int getExperienceToNextLevel() {
        return stats.getExperienceToNextLevel();
    }

    public int getTreasureScore() {
        return stats.getTreasureScore();
    }

    public Vector2 getPosition() {
        return position;
    }

    public Direction getFacing() {
        return facing;
    }

    public Vector2 getDirectionVector() {
        return directionVector;
    }

    public Vector2 getCameraPlane() {
        return cameraPlane;
    }

    public int getFood() {
        return stats.getFood();
    }

    public int getArrows() {
        return stats.getArrows();
    }

    public Inventory getInventory() {
        return inventory;
    }

    public PlayerEquipment getEquipment() {
        return equipment;
    }

    /** Blood on the father himself, and splashes not yet settled onto the paperdoll. */
    public com.bpm.minotaur.gamedata.gore.PlayerBlood getBlood() {
        return blood;
    }

    public void restoreBlood(com.bpm.minotaur.gamedata.gore.PlayerBlood saved) {
        blood.clear();
        if (saved != null) {
            blood.body = saved.body != null ? saved.body : new com.bpm.minotaur.gamedata.gore.BloodCoat();
            if (saved.pending != null) {
                blood.pending.addAll(saved.pending);
            }
        }
    }

    /** Everything drawn on the paperdoll that can carry blood. */
    private java.util.List<Item> bloodiedGear() {
        java.util.List<Item> gear = new java.util.ArrayList<>(equipment.getAllEquipped());
        if (inventory.getLeftHand() != null && !gear.contains(inventory.getLeftHand())) {
            gear.add(inventory.getLeftHand());
        }
        return gear;
    }

    /** Dries the blood on him and on everything he is wearing by a number of turns. */
    public void ageBlood(int turns) {
        blood.age(turns);
        for (Item item : bloodiedGear()) {
            if (item.getBloodCoats() != null) {
                for (com.bpm.minotaur.gamedata.gore.BloodCoat coat : item.getBloodCoats().values()) {
                    coat.age(turns);
                }
            }
        }
    }

    /** Washes him and everything he is wearing clean. */
    public void washBlood() {
        blood.clear();
        for (Item item : bloodiedGear()) {
            if (item.getBloodCoats() != null) {
                for (com.bpm.minotaur.gamedata.gore.BloodCoat coat : item.getBloodCoats().values()) {
                    coat.clear();
                }
            }
        }
    }

    public StatusManager getStatusManager() {
        return statusManager;
    }

    public void setMaze(Maze newMaze) {
    }

    public PlayerStats getStats() {
        return this.stats;
    }

    /**
     * Drops an item. Tries Feet -> Front -> Adjacent tiles.
     * 
     * @return true if successfully dropped, false if no space.
     */
    public boolean dropItem(Maze maze, Item item) {
        if (item == null)
            return false;

        GridPoint2 playerTile = new GridPoint2((int) position.x, (int) position.y);

        // 1. Try Feet
        if (!maze.getItems().containsKey(playerTile)) {
            item.getPosition().set(playerTile.x + 0.5f, playerTile.y + 0.5f);
            maze.addItem(item);
            if (item.getType() == ItemType.BRASS_LANTERN) {
                maze.addLight(new LightSource("shelter_lantern_" + playerTile.x + "_" + playerTile.y,
                        playerTile.x + 0.5f, playerTile.y + 0.5f,
                        LightingManager.COLOR_LANTERN, 5.0f, LightingManager.MOUNTED_LANTERN_INTENSITY,
                        LightSource.FlickerProfile.LANTERN_BREATH));
            }
            return true;
        }

        // 2. Try Front
        int targetX = (int) (position.x + facing.getVector().x);
        int targetY = (int) (position.y + facing.getVector().y);
        GridPoint2 frontTile = new GridPoint2(targetX, targetY);

        // Ensure we don't drop through a wall
        if (!maze.isWallBlocking((int) position.x, (int) position.y, facing)
                && !maze.getItems().containsKey(frontTile)) {
            item.getPosition().set(frontTile.x + 0.5f, frontTile.y + 0.5f);
            maze.addItem(item);
            if (item.getType() == ItemType.BRASS_LANTERN) {
                maze.addLight(new LightSource("shelter_lantern_" + frontTile.x + "_" + frontTile.y,
                        frontTile.x + 0.5f, frontTile.y + 0.5f,
                        LightingManager.COLOR_LANTERN, 5.0f, LightingManager.MOUNTED_LANTERN_INTENSITY,
                        LightSource.FlickerProfile.LANTERN_BREATH));
            }
            return true;
        }

        // 3. Try Other Directions (Back, Left, Right)
        for (Direction d : Direction.values()) {
            if (d == facing)
                continue;

            if (!maze.isWallBlocking((int) position.x, (int) position.y, d)) {
                int nx = (int) (position.x + d.getVector().x);
                int ny = (int) (position.y + d.getVector().y);
                GridPoint2 neighborTile = new GridPoint2(nx, ny);

                if (!maze.getItems().containsKey(neighborTile)) {
                    item.getPosition().set(neighborTile.x + 0.5f, neighborTile.y + 0.5f);
                    maze.addItem(item);
                    if (item.getType() == ItemType.BRASS_LANTERN) {
                        maze.addLight(new LightSource("shelter_lantern_" + neighborTile.x + "_" + neighborTile.y,
                                neighborTile.x + 0.5f, neighborTile.y + 0.5f,
                                LightingManager.COLOR_LANTERN, 5.0f, LightingManager.MOUNTED_LANTERN_INTENSITY,
                                LightSource.FlickerProfile.LANTERN_BREATH));
                    }
                    return true;
                }
            }
        }

        // No space found
        return false;
    }

    private boolean hasButcheringTool() {
        if (checkTool(inventory.getRightHand()))
            return true;
        if (checkTool(inventory.getLeftHand()))
            return true;
        for (Item i : inventory.getQuickSlots()) {
            if (checkTool(i))
                return true;
        }
        for (Item i : inventory.getMainInventory()) {
            if (checkTool(i))
                return true;
        }
        return false;
    }

    private boolean checkTool(Item item) {
        if (item == null)
            return false;
        String name = item.getDisplayName().toLowerCase();
        return name.contains("axe") || name.contains("knife") || name.contains("shiv")
                || name.contains("sword") || name.contains("dagger");
    }

    // --- Helper for Scrolls ---
    private Item getRandomWornArmorHelper() {
        List<Item> worn = new ArrayList<>();
        PlayerEquipment eq = getEquipment();
        if (eq.getWornHelmet() != null)
            worn.add(eq.getWornHelmet());
        if (eq.getWornChest() != null)
            worn.add(eq.getWornChest());
        if (eq.getWornLegs() != null)
            worn.add(eq.getWornLegs());
        if (eq.getWornBoots() != null)
            worn.add(eq.getWornBoots());
        if (eq.getWornGauntlets() != null)
            worn.add(eq.getWornGauntlets());
        if (eq.getWornShield() != null)
            worn.add(eq.getWornShield());
        // Add other slots if relevant

        if (worn.isEmpty())
            return null;
        return worn.get(new Random().nextInt(worn.size()));
    }
}
