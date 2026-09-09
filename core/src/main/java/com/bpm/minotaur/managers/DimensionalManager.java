package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Direction;

/**
 * Manages the Ancient Void of the Tarmin Gods (The Retro Dimension).
 *
 * Coordinates:
 * - Dimensional state (Mortal 3D vs Retro Void).
 * - "Hollow Shade" purgatory state when slain by spiritual/undead foes.
 * - Combat Inversion: -60% Physical damage, +250% Spiritual/Magical damage in the Void.
 * - Weather suppression (the Void is an airless, eerie vacuum).
 * - Ethereal Ghost Walls passable only in the Void.
 * - Ancient Lore Glyphs tied to the Tarmin-Zul story cycle.
 */
public class DimensionalManager {
    private static final DimensionalManager INSTANCE = new DimensionalManager();

    private boolean inVoid = false;
    private boolean hollowShade = false;

    // Anchor points to return the player to the mortal realm
    private final GridPoint2 mortalReturnPos = new GridPoint2(0, 0);
    private int mortalReturnLevel = 1;
    private final GridPoint2 mortalReturnChunk = new GridPoint2(0, 0);

    // Soul Husk left behind when entering via Death Inversion
    private GridPoint2 soulHuskPos = null;
    private int soulHuskLevel = 1;
    private GridPoint2 soulHuskChunk = null;

    // Lore entries corresponding to the story art cycle
    public static final String[] VOID_LORE_GLYPHS = new String[] {
        "Rune of the Valley: Castle Tarmin casts its crimson shadow upon the mortal realm. We rang the bells, but none could avert the twilight. (Story I)",
        "Rune of the Scribe: In the sacred codex, the scholar inscribed the name of the Antlered Lord, sealing the rites in flame. (Story II)",
        "Rune of the Horned King: From the obsidian rift strode the Minotaur, leading legions of titans and creeping carapaces. (Story III)",
        "Rune of Ash: The village stones turned to embers. Fire consumed the hearth of man beneath a blood-red sun. (Story IV)",
        "Rune of the Cages: Chained within iron bars beneath a thunderous sky, our kin were ferried to the Maw of Tarmin. (Story V)",
        "Rune of Tarmin-Zul: The Antlered Nether Lord extracted the mortal spirit, tearing open the primordial Raycast Void. (Story VI)",
        "Rune of the Refuge: He who rests at the green hearth may yet take up the notched blade and reclaim the light. (Story VII)"
    };

    private DimensionalManager() {
    }

    public static DimensionalManager getInstance() {
        return INSTANCE;
    }

    /**
     * Enters the Ancient Void dimension.
     *
     * @param asHollowShade true if ripped into the Void via Death Inversion by spiritual foes.
     * @param playerPos current position in mortal realm.
     * @param level current dungeon level.
     * @param chunk current chunk coordinates.
     */
    public void enterVoid(boolean asHollowShade, Vector2 playerPos, int level, GridPoint2 chunk) {
        this.inVoid = true;
        this.hollowShade = asHollowShade;

        if (playerPos != null) {
            this.mortalReturnPos.set((int) playerPos.x, (int) playerPos.y);
        }
        this.mortalReturnLevel = level;
        if (chunk != null) {
            this.mortalReturnChunk.set(chunk);
        }

        if (asHollowShade && playerPos != null && chunk != null) {
            this.soulHuskPos = new GridPoint2((int) playerPos.x, (int) playerPos.y);
            this.soulHuskLevel = level;
            this.soulHuskChunk = new GridPoint2(chunk);
        }

        // Lock render mode and engine to retro raycaster
        DebugManager.getInstance().setRenderEngine(DebugManager.RenderEngine.RAYCASTER);
        DebugManager.getInstance().setRenderModeDirect(DebugManager.RenderMode.RETRO);

        if (Gdx.app != null) {
            Gdx.app.log("DimensionalManager", "Entered Ancient Void of Tarmin-Zul. HollowShade=" + asHollowShade);
        }
    }

    /**
     * Exits the Ancient Void and returns to the Mortal Modern Realm.
     *
     * @param recoveredSoul true if the player's mortal husk was recovered/resurrected.
     */
    public void exitVoid(boolean recoveredSoul) {
        this.inVoid = false;
        if (recoveredSoul || !hollowShade) {
            this.hollowShade = false;
            this.soulHuskPos = null;
            this.soulHuskChunk = null;
        }

        // Return render mode and engine to modern 3D
        DebugManager.getInstance().setRenderEngine(DebugManager.RenderEngine.PLANAR_3D);
        DebugManager.getInstance().setRenderModeDirect(DebugManager.RenderMode.MODERN);

        if (Gdx.app != null) {
            Gdx.app.log("DimensionalManager", "Exited Ancient Void. Returned to Mortal Realm.");
        }
    }

    public boolean isInVoid() {
        return inVoid;
    }

    public void setInVoid(boolean inVoid) {
        this.inVoid = inVoid;
    }

    public boolean isHollowShade() {
        return hollowShade;
    }

    public void setHollowShade(boolean hollowShade) {
        this.hollowShade = hollowShade;
    }

    public GridPoint2 getMortalReturnPos() {
        return mortalReturnPos;
    }

    public int getMortalReturnLevel() {
        return mortalReturnLevel;
    }

    public GridPoint2 getMortalReturnChunk() {
        return mortalReturnChunk;
    }

    public GridPoint2 getSoulHuskPos() {
        return soulHuskPos;
    }

    public int getSoulHuskLevel() {
        return soulHuskLevel;
    }

    public GridPoint2 getSoulHuskChunk() {
        return soulHuskChunk;
    }

    /**
     * In the Void, mundane physical weapons deal 60% less damage (0.4x multiplier).
     */
    public float getPhysicalDamageMultiplier() {
        return inVoid ? 0.40f : 1.0f;
    }

    /**
     * In the Void, Spiritual and Magical energies resonate fiercely (+250% / 2.5x multiplier).
     */
    public float getSpiritualDamageMultiplier() {
        return inVoid ? 2.50f : 1.0f;
    }

    /**
     * Weather is completely suppressed in the Ancient Void vacuum.
     */
    public boolean isWeatherSuppressed() {
        return inVoid;
    }

    /**
     * Checks if a given wall cell acts as an ethereal Ghost Wall.
     * Ghost walls only phase open when the player is inside the Void.
     */
    public boolean isGhostWall(int chunkX, int chunkY, int x, int y, Direction dir) {
        if (!inVoid) return false;
        // Deterministic hash to place ethereal ghost seams in the ancient geometry
        int hash = (chunkX * 73856093) ^ (chunkY * 19349663) ^ (x * 83492791) ^ (y * 23492813) ^ (dir.ordinal() * 1013904223);
        hash = Math.abs(hash);
        // Approximately 1 in 14 internal wall segments is an ethereal Ghost Wall
        return (hash % 14) == 0;
    }

    /**
     * Retrieves the ancient lore inscription at a given cell, if any.
     */
    public String getVoidLoreAt(int chunkX, int chunkY, int x, int y) {
        if (!inVoid) return null;
        int hash = Math.abs((chunkX * 31337) ^ (chunkY * 7919) ^ (x * 1013) ^ (y * 6971));
        if ((hash % 19) == 0) {
            int index = (hash / 19) % VOID_LORE_GLYPHS.length;
            return VOID_LORE_GLYPHS[index];
        }
        return null;
    }

    /**
     * Resets dimensional state (e.g. on new game or full wipe).
     */
    public void reset() {
        this.inVoid = false;
        this.hollowShade = false;
        this.soulHuskPos = null;
        this.soulHuskChunk = null;
        this.mortalReturnPos.set(0, 0);
        this.mortalReturnLevel = 1;
        this.mortalReturnChunk.set(0, 0);
    }
}
