package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * ShopkeeperNpc — a traveling merchant NPC that wanders the maze,
 * fights monsters it encounters, and opens a shop when the player is adjacent.
 * This is NOT a Monster subtype — it lives on Maze as a separate entity.
 */
public class ShopkeeperNpc implements Renderable {

    public enum ShopkeeperState {
        WANDERING,
        FLEEING,
        TRADING // Currently in dialogue with player (frozen)
    }

    // --- Position & Core Stats ---
    private final Vector2 position;
    private int currentHP;
    private final int maxHP = 80;

    // --- Rendering ---
    private Texture texture;
    public static final String TEXTURE_PATH = "images/monsters/shopkeeper.png";
    public final Vector2 scale = new Vector2(1.0f, 1.0f);

    // --- Inventory ---
    private final Inventory inventory;

    // --- AI State ---
    private ShopkeeperState state = ShopkeeperState.WANDERING;
    private float energy = 0f;
    private static final int BASE_SPEED = 8; // slower than most monsters (12)

    // --- Interaction cooldown: prevent re-triggering shop every step ---
    private int tradingCooldown = 0;

    // --- Void chain laser ---
    // Loot gate on the weapon is meant to stack two halves: (1) he turns the laser on
    // an attacker in self-defense, so killing him early is meant to be suicidal, and
    // (2) restoring the always-dropped spent cell needs rare debris plus a Void-only
    // component (see CraftingManager's restoration recipe). Only half (2) actually
    // exists in code -- CombatManager is Monster-typed and this NPC is deliberately
    // not a Monster (see class doc), so there is currently NO path anywhere for the
    // player to attack the merchant at all. Half (1) is fiction only until that gap
    // is closed; killing him today means a monster does it, not the player.
    /** Turns the weapon spends cycling after a burst before it can fire again. */
    public static final int LASER_COOLDOWN_TURNS = 2;
    private int laserCooldown = 0;

    /** Fraction knocked off the next trade after one of his strays clips the player. */
    public static final float RESTITUTION_DISCOUNT = 0.15f;
    private float restitutionDiscount = 0f;

    public ShopkeeperNpc(float startX, float startY, AssetManager assetManager) {
        this.position = new Vector2(startX + 0.5f, startY + 0.5f);
        this.currentHP = maxHP;
        this.inventory = new Inventory();

        if (assetManager != null && assetManager.isLoaded(TEXTURE_PATH, Texture.class)) {
            this.texture = assetManager.get(TEXTURE_PATH, Texture.class);
        }
    }

    // --- Combat ---
    public int takeDamage(int amount) {
        this.currentHP -= amount;
        if (this.currentHP < 0)
            this.currentHP = 0;
        return amount;
    }

    public boolean isAlive() {
        return currentHP > 0;
    }

    public boolean isLowHP() {
        return (float) currentHP / maxHP < 0.3f;
    }

    // --- Getters / Setters ---
    public Vector2 getPosition() {
        return position;
    }

    public GridPoint2 getGridPosition() {
        return new GridPoint2((int) position.x, (int) position.y);
    }

    public int getCurrentHP() {
        return currentHP;
    }

    public int getMaxHP() {
        return maxHP;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public ShopkeeperState getState() {
        return state;
    }

    public void setState(ShopkeeperState state) {
        this.state = state;
    }

    public float getEnergy() {
        return energy;
    }

    public void setEnergy(float energy) {
        this.energy = energy;
    }

    public void addEnergy(float amount) {
        this.energy += amount;
    }

    public int getEffectiveSpeed() {
        return BASE_SPEED;
    }

    public int getTradingCooldown() {
        return tradingCooldown;
    }

    public void setTradingCooldown(int turns) {
        this.tradingCooldown = turns;
    }

    /** He clipped the player: the next trade is on better terms. */
    public void offerRestitution() {
        restitutionDiscount = RESTITUTION_DISCOUNT;
    }

    public float getRestitutionDiscount() {
        return restitutionDiscount;
    }

    /** The apology has been traded against; the next clip earns a fresh one. */
    public void consumeRestitutionDiscount() {
        restitutionDiscount = 0f;
    }

    /** True when the cell is charged and a burst can go out this turn. */
    public boolean isLaserCharged() {
        return laserCooldown == 0;
    }

    /** Called the turn a burst goes out. */
    public void startLaserCooldown() {
        laserCooldown = LASER_COOLDOWN_TURNS;
    }

    /** Advances the weapon cycle by one turn. */
    public void tickLaserCooldown() {
        if (laserCooldown > 0) {
            laserCooldown--;
        }
    }

    public void tickTradingCooldown() {
        if (tradingCooldown > 0)
            tradingCooldown--;
    }

    /** Called after the asset manager finishes loading to bind the texture late. */
    public void bindTexture(AssetManager assetManager) {
        if (texture == null && assetManager != null && assetManager.isLoaded(TEXTURE_PATH, Texture.class)) {
            this.texture = assetManager.get(TEXTURE_PATH, Texture.class);
        }
    }

    public String getDisplayName() {
        return "Traveling Merchant";
    }

    @Override
    public Color getColor() {
        return Color.WHITE;
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }

    public List<com.bpm.minotaur.gamedata.item.Item> createDeathDrops(
            com.bpm.minotaur.gamedata.item.ItemDataManager idm, AssetManager am) {
        List<com.bpm.minotaur.gamedata.item.Item> drops = new ArrayList<>();
        if (idm != null) {
            try {
                com.bpm.minotaur.gamedata.item.Item goldPouch = idm.createItem(
                        com.bpm.minotaur.gamedata.item.Item.ItemType.COINS,
                        (int) position.x, (int) position.y, null, am);
                if (goldPouch != null) drops.add(goldPouch);
            } catch (Exception ignored) {}

            // The Void chain laser always drops, but spent -- the cell burns out the
            // instant he falls. Restoring it (see CraftingManager's Void salvage
            // recipe) is the "careful planning" gate; surviving him at all to get
            // here is the "extreme high levels" gate.
            try {
                com.bpm.minotaur.gamedata.item.Item spentLaser = idm.createItem(
                        com.bpm.minotaur.gamedata.item.Item.ItemType.VOID_CHAIN_LASER_SPENT,
                        (int) position.x, (int) position.y, null, am);
                if (spentLaser != null) drops.add(spentLaser);
            } catch (Exception ignored) {}
        }
        List<com.bpm.minotaur.gamedata.item.Item> stock = inventory.getAllItems();
        if (!stock.isEmpty()) {
            List<com.bpm.minotaur.gamedata.item.Item> copy = new ArrayList<>(stock);
            java.util.Collections.shuffle(copy);
            int count = Math.min(3, copy.size());
            for (int i = 0; i < count; i++) {
                drops.add(copy.get(i));
            }
        }
        return drops;
    }
}
