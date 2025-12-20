package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.dice.Die;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import java.util.ArrayList;
import java.util.List;

public class CombatDiceOverlay {

    private final Player player;
    private final CombatManager combatManager;
    private final BitmapFont font;
    private final ShapeRenderer shapeRenderer;

    // UI State
    private List<Die> selectedHand = new ArrayList<>();
    private List<Rectangle> dieClickZones = new ArrayList<>();
    private Rectangle rollButtonZone;

    private static final float DIE_SIZE = 64f;
    private static final float PADDING = 20f;

    private final com.badlogic.gdx.utils.viewport.Viewport viewport;

    public CombatDiceOverlay(Player player, CombatManager combatManager,
            com.badlogic.gdx.utils.viewport.Viewport viewport) {
        this.player = player;
        this.combatManager = combatManager;
        this.viewport = viewport;
        this.font = new BitmapFont();
        this.shapeRenderer = new ShapeRenderer();
    }

    public void update(float delta) {
        // Handle input if active
        boolean clicked = Gdx.input.justTouched();
        boolean keyPressed = Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE) ||
                Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER);

        if (clicked) {
            // Correct coordinate handling: Unproject screen coordinates to world
            // coordinates
            Vector2 clickPos = new Vector2(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(clickPos); // transforms in place to world coordinates

            com.bpm.minotaur.managers.BalanceLogger.getInstance().log("UI_DEBUG", "Click detected at Screen("
                    + Gdx.input.getX() + "," + Gdx.input.getY() + ") -> World(" + clickPos.x + "," + clickPos.y + ")");
            handleClick(clickPos);
            return;
        }

        if (keyPressed) {
            com.bpm.minotaur.managers.BalanceLogger.getInstance().log("UI_DEBUG",
                    "Key SPACE/ENTER pressed. Confirming selection.");
            // Confirm Selection
            combatManager.confirmDiceSelection(selectedHand);
        }
    }

    private void handleClick(Vector2 click) {
        // 1. Check Roll Button
        if (rollButtonZone != null && rollButtonZone.contains(click)) {
            com.bpm.minotaur.managers.BalanceLogger.getInstance().log("UI_DEBUG", "Clicked ROLL BUTTON");
            combatManager.confirmDiceSelection(selectedHand);
            return;
        }

        // 2. Check Die Clicks
        for (int i = 0; i < dieClickZones.size(); i++) {
            Rectangle zone = dieClickZones.get(i);
            boolean hit = zone.contains(click);

            // Log proximity for debugging
            if (hit || click.dst(zone.x + zone.width / 2, zone.y + zone.height / 2) < 100) {
                com.bpm.minotaur.managers.BalanceLogger.getInstance().log("UI_DEBUG",
                        "Checking Zone " + i + " " + zone.toString() + " vs Click " + click + " -> " + hit);
            }

            if (hit) {
                List<Die> pool = player.getStats().getDicePool();
                if (i < pool.size()) {
                    Die toggledDie = pool.get(i);
                    if (selectedHand.contains(toggledDie)) {
                        selectedHand.remove(toggledDie);
                        com.bpm.minotaur.managers.BalanceLogger.getInstance().log("UI_DEBUG",
                                "Deselected: " + toggledDie.getName());
                    } else {
                        // Check stamina
                        if (selectedHand.size() < player.getStats().getStamina()) {
                            selectedHand.add(toggledDie);
                            com.bpm.minotaur.managers.BalanceLogger.getInstance().log("UI_DEBUG",
                                    "Selected: " + toggledDie.getName());
                        } else {
                            com.bpm.minotaur.managers.BalanceLogger.getInstance().log("UI_DEBUG",
                                    "Cannot select (Stamina Full)");
                        }
                    }
                }
                break; // Handled
            }
        }
    }

    public void render(SpriteBatch batch) {
        // Debug log removed

        if (combatManager.getCurrentState() != CombatManager.CombatState.PLAYER_SELECT_DICE) {
            return;
        }

        // For now, let's log the first time we see it or use a simple throttle.
        if (Gdx.graphics.getFrameId() % 60 == 0) {
            StringBuilder diceNames = new StringBuilder();
            List<Die> currentPool = player.getStats().getDicePool();
            for (Die d : currentPool) {
                diceNames.append(d.getName()).append(", ");
            }
            com.bpm.minotaur.managers.BalanceLogger.getInstance().log("UI_DEBUG",
                    "Dice Overlay Active. Pool: " + diceNames.toString());
        }
        // ---------------------

        // Darken background
        batch.end();
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.7f);
        shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // Debug draw button zone
        float centerX = Gdx.graphics.getWidth() / 2f;
        float centerY = Gdx.graphics.getHeight() / 2f;

        // Initialize logic on first render pass or update
        // Resetting zones every frame is cheap enough for now
        dieClickZones.clear();

        List<Die> pool = player.getStats().getDicePool();
        float startX = centerX - ((pool.size() * (DIE_SIZE + PADDING)) / 2f);

        for (int i = 0; i < pool.size(); i++) {
            float x = startX + (i * (DIE_SIZE + PADDING));
            float y = centerY;
            dieClickZones.add(new Rectangle(x, y + 20, DIE_SIZE, DIE_SIZE * 1.5f)); // Approximate text area
        }

        // Button Zone
        rollButtonZone = new Rectangle(centerX - 100, centerY - 170, 200, 50);
        shapeRenderer.setColor(0.2f, 0.5f, 0.2f, 1f);
        shapeRenderer.rect(rollButtonZone.x, rollButtonZone.y, rollButtonZone.width, rollButtonZone.height);

        shapeRenderer.end();
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        batch.begin();

        font.draw(batch, "SELECT YOUR FATE (Stamina: " + player.getStats().getStamina() + ")", centerX - 100,
                centerY + 200);

        // Draw Dice Pool
        for (int i = 0; i < pool.size(); i++) {
            Die die = pool.get(i);
            float x = startX + (i * (DIE_SIZE + PADDING));
            float y = centerY;

            // Draw Box
            // Ensure we use a texture or shape
            // Ideally: batch.draw(dieTexture, x, y, DIE_SIZE, DIE_SIZE);
            font.setColor(die.getDieColor());
            font.draw(batch, die.getName(), x, y + DIE_SIZE + 20);

            // Highlight if selected
            if (selectedHand.contains(die)) {
                font.setColor(Color.YELLOW);
                font.draw(batch, "[SELECTED]", x, y + 80);
            } else {
                font.setColor(Color.GRAY);
                font.draw(batch, "[   ]", x, y + 80);
            }
        }

        // Draw Roll Button
        String label = "ROLL (SPACE)";
        font.setColor(Color.WHITE);
        font.draw(batch, label, centerX - 40, centerY - 140);
    }
}
