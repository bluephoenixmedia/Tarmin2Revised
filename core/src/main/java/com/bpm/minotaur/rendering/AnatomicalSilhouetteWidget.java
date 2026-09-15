package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.bpm.minotaur.gamedata.injury.BodyPart;
import com.bpm.minotaur.gamedata.injury.InjuryManager;
import com.bpm.minotaur.gamedata.injury.InjuryRecord;
import com.bpm.minotaur.gamedata.injury.InjuryType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.screens.GameScreen;

/**
 * Interactive 36x52 anatomical mini-silhouette paperdoll widget displayed on the HUD.
 * Reflects bodily trauma, lacerations, bone fractures, infections, and treatment states
 * in real-time. Hovering shows diagnostic breakdown; clicking opens the First Aid triage modal.
 */
public class AnatomicalSilhouetteWidget extends Actor {

    private final HudSkin skin;
    private final Player player;
    private GameScreen gameScreen;
    private final HudTooltip hudTooltip;
    private float pulseTimer = 0f;

    // Standardized body condition colors
    private static final Color COL_HEALTHY = Color.valueOf("3E463DE0");
    private static final Color COL_TREATED = Color.valueOf("4E707CE0");
    private static final Color COL_FRACTURE = Color.valueOf("E8A63AE0");
    private static final Color COL_INFECTED = Color.valueOf("59A835E0");
    private static final Color COL_BLEED_PEAK = Color.valueOf("FF3B30F0");
    private static final Color COL_BLEED_BASE = Color.valueOf("8B1E16F0");

    public AnatomicalSilhouetteWidget(HudSkin skin, Player player, GameScreen gameScreen, HudTooltip hudTooltip) {
        this.skin = skin;
        this.player = player;
        this.gameScreen = gameScreen;
        this.hudTooltip = hudTooltip;
        setSize(36f, 52f);

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (AnatomicalSilhouetteWidget.this.gameScreen != null) {
                    AnatomicalSilhouetteWidget.this.gameScreen.openFirstAidModal();
                }
            }
        });

        addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (hudTooltip != null && player != null) {
                    Vector2 screenPos = localToStageCoordinates(new Vector2(0, 0));
                    String tooltipText = buildDiagnosisTooltip();
                    hudTooltip.showCustomText("ANATOMICAL DIAGNOSIS", tooltipText, screenPos.x + 18f, screenPos.y + 54f, "[R] or Click to Tend");
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (hudTooltip != null) {
                    hudTooltip.hide();
                }
            }
        });
    }

    public void setGameScreen(GameScreen gameScreen) {
        this.gameScreen = gameScreen;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        pulseTimer += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        Texture pixel = skin.getWhitePixel();
        if (pixel == null) return;

        // 1. Recessed Iron Background Plate
        batch.setColor(0.06f, 0.04f, 0.03f, 0.90f * parentAlpha);
        batch.draw(pixel, x, y, w, h);

        // Border
        batch.setColor(HudSkin.COL_STONE_MID.r, HudSkin.COL_STONE_MID.g, HudSkin.COL_STONE_MID.b, 0.85f * parentAlpha);
        batch.draw(pixel, x, y, w, 1f);
        batch.draw(pixel, x, y + h - 1f, w, 1f);
        batch.draw(pixel, x, y, 1f, h);
        batch.draw(pixel, x + w - 1f, y, 1f, h);

        InjuryManager im = (player != null) ? player.getInjuryManager() : null;

        // 2. Head (Top center: 10 x 10)
        Color headCol = getPartColor(im, BodyPart.HEAD);
        batch.setColor(headCol.r, headCol.g, headCol.b, headCol.a * parentAlpha);
        batch.draw(pixel, x + 13f, y + 37f, 10f, 10f);

        // 3. Torso (Center chest: 16 x 17)
        Color torsoCol = getPartColor(im, BodyPart.TORSO);
        batch.setColor(torsoCol.r, torsoCol.g, torsoCol.b, torsoCol.a * parentAlpha);
        batch.draw(pixel, x + 10f, y + 19f, 16f, 17f);

        // 4. Arms (Flanking: Left arm 5 x 16, Right arm 5 x 16)
        Color armsCol = getPartColor(im, BodyPart.ARMS);
        batch.setColor(armsCol.r, armsCol.g, armsCol.b, armsCol.a * parentAlpha);
        batch.draw(pixel, x + 4f, y + 19f, 5f, 16f); // Left arm
        batch.draw(pixel, x + 27f, y + 19f, 5f, 16f); // Right arm

        // 5. Legs (Lower: Left leg 6 x 15, Right leg 6 x 15)
        Color legsCol = getPartColor(im, BodyPart.LEGS);
        batch.setColor(legsCol.r, legsCol.g, legsCol.b, legsCol.a * parentAlpha);
        batch.draw(pixel, x + 11f, y + 3f, 6f, 15f); // Left leg
        batch.draw(pixel, x + 19f, y + 3f, 6f, 15f); // Right leg

        // Reset batch tint
        batch.setColor(Color.WHITE);
    }

    private Color getPartColor(InjuryManager im, BodyPart part) {
        if (im == null) return COL_HEALTHY;
        InjuryRecord rec = im.getInjury(part);
        if (rec == null) return COL_HEALTHY;

        if (rec.isInfected()) {
            return COL_INFECTED;
        }
        if (rec.isTreated()) {
            return COL_TREATED;
        }

        if (rec.getInjuryType() == InjuryType.LACERATION_BLEEDING || rec.getInjuryType() == InjuryType.PUNCTURE_WOUND) {
            // Rhythmic throbbing red alert
            float t = (MathUtils.sin(pulseTimer * 6.5f) + 1f) * 0.5f;
            return new Color(
                    MathUtils.lerp(COL_BLEED_BASE.r, COL_BLEED_PEAK.r, t),
                    MathUtils.lerp(COL_BLEED_BASE.g, COL_BLEED_PEAK.g, t),
                    MathUtils.lerp(COL_BLEED_BASE.b, COL_BLEED_PEAK.b, t),
                    0.95f
            );
        }

        if (rec.getInjuryType() == InjuryType.BONE_FRACTURE) {
            return COL_FRACTURE;
        }

        return COL_FRACTURE;
    }

    private String buildDiagnosisTooltip() {
        if (player == null || player.getInjuryManager() == null) {
            return "All limbs healthy and sound.";
        }
        InjuryManager im = player.getInjuryManager();
        StringBuilder sb = new StringBuilder();

        for (BodyPart part : BodyPart.values()) {
            InjuryRecord rec = im.getInjury(part);
            sb.append(part.getDisplayName()).append(": ");
            if (rec == null) {
                sb.append("Healthy\n");
            } else {
                sb.append(rec.getInjuryType().getDisplayName());
                if (rec.isTreated()) {
                    sb.append(" [Treated]");
                } else {
                    sb.append(" [ACTIVE]");
                }
                if (rec.isInfected()) {
                    sb.append(" (Infected)");
                }
                sb.append("\n");
            }
        }

        if (im.getIllnessStage().isIll()) {
            sb.append("\nIllness: ").append(im.getIllnessStage().getDisplayName());
        }

        return sb.toString().trim();
    }
}
