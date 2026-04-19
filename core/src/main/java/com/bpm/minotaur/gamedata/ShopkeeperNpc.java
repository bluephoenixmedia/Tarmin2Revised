package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;

/**
 * ShopkeeperNpc — a traveling merchant NPC that wanders the maze,
 * fights monsters it encounters, and opens a shop when the player is adjacent.
 * This is NOT a Monster subtype — it lives on Maze as a separate entity.
 */
public class ShopkeeperNpc {

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

    public Texture getTexture() {
        return texture;
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
}
