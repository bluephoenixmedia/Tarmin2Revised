package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.bones.BonesData;
import com.bpm.minotaur.gamedata.monster.GhostPlayerMonster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.SoundManager;

/**
 * Modal dialog presented when examining the decomposing remains of a fallen player character.
 * Gives the player tactical agency to challenge the spirit or leave in peace.
 */
public class BonesAwakenModal extends Table {

    private final HudSkin hudSkin;
    private final Label titleLabel;
    private final Label bodyLabel;
    private final Label epitaphLabel;
    private final TextButton challengeBtn;
    private final TextButton leaveBtn;

    private Scenery activeCorpse;
    private BonesData activeBones;
    private Maze activeMaze;
    private Player activePlayer;
    private GameEventManager activeEventManager;
    private SoundManager activeSoundManager;
    private com.badlogic.gdx.assets.AssetManager activeAssetManager;

    private Texture whitePixel;

    public BonesAwakenModal(HudSkin hudSkin) {
        this.hudSkin = hudSkin;

        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(Color.WHITE);
        pix.fill();
        this.whitePixel = new Texture(pix);
        pix.dispose();

        this.setBackground(new TextureRegionDrawable(whitePixel).tint(new Color(0.06f, 0.08f, 0.10f, 0.95f)));
        this.pad(30);

        titleLabel = new Label("RESTLESS SPIRIT", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        titleLabel.setFontScale(1.2f);
        titleLabel.setAlignment(Align.center);
        this.add(titleLabel).growX().padBottom(12).row();

        bodyLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
        bodyLabel.setWrap(true);
        bodyLabel.setAlignment(Align.center);
        this.add(bodyLabel).width(580).padBottom(10).row();

        epitaphLabel = new Label("", new Label.LabelStyle(hudSkin.getFontSmall(), new Color(0.35f, 0.85f, 0.75f, 1f)));
        epitaphLabel.setWrap(true);
        epitaphLabel.setAlignment(Align.center);
        this.add(epitaphLabel).width(580).padBottom(24).row();

        Table btnTable = new Table();
        TextButton.TextButtonStyle challengeStyle = new TextButton.TextButtonStyle();
        challengeStyle.font = hudSkin.getFontMain();
        challengeStyle.fontColor = Color.WHITE;
        challengeStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.65f, 0.18f, 0.18f, 0.95f));
        challengeStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.85f, 0.25f, 0.25f, 1f));
        challengeBtn = new TextButton("CHALLENGE SPIRIT", challengeStyle);
        challengeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                challengeSpirit();
            }
        });
        btnTable.add(challengeBtn).width(220).height(48).padRight(20);

        TextButton.TextButtonStyle leaveStyle = new TextButton.TextButtonStyle();
        leaveStyle.font = hudSkin.getFontMain();
        leaveStyle.fontColor = Color.WHITE;
        leaveStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.25f, 0.30f, 0.35f, 0.95f));
        leaveStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.35f, 0.42f, 0.50f, 1f));
        leaveBtn = new TextButton("LEAVE IN PEACE", leaveStyle);
        leaveBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                leaveInPeace();
            }
        });
        btnTable.add(leaveBtn).width(200).height(48);

        this.add(btnTable).center().row();

        this.setSize(660, 360);
        this.setVisible(false);
    }

    public void configureAndShow(Scenery corpse, BonesData bones, Maze maze, Player player,
                                 GameEventManager eventManager, SoundManager soundManager,
                                 com.badlogic.gdx.assets.AssetManager assetManager) {
        this.activeCorpse = corpse;
        this.activeBones = bones;
        this.activeMaze = maze;
        this.activePlayer = player;
        this.activeEventManager = eventManager;
        this.activeSoundManager = soundManager;
        this.activeAssetManager = assetManager;

        String heroName = (bones != null && bones.playerName != null) ? bones.playerName : "Fallen Hero";
        int floor = (bones != null) ? bones.floorLevel : 1;
        int strata = (bones != null) ? bones.strataDepth : 1;
        String epitaph = (bones != null && bones.epitaph != null) ? bones.epitaph : "Fell in battle";

        bodyLabel.setText("You discover the decomposing remains of " + heroName + ",\nfallen on Floor " + floor + " (Strata " + strata + ").\n\nA lingering phantom hovers watchfully over the gear.\nDo you dare disturb the remains?");
        epitaphLabel.setText("\"" + epitaph + "\"");

        if (getStage() != null) {
            this.setPosition((getStage().getWidth() - getWidth()) / 2f,
                    (getStage().getHeight() - getHeight()) / 2f + 40f);
            getStage().setKeyboardFocus(this);
        }

        this.setVisible(true);
        this.toFront();
    }

    private void challengeSpirit() {
        if (activeBones == null || activeMaze == null) {
            close();
            return;
        }

        activeBones.awakened = true;

        // Spawn Ghost on adjacent traversable tile or corpse position
        Vector2 corpsePos = activeCorpse != null ? activeCorpse.getPosition() : new Vector2(activeBones.tileX + 0.5f, activeBones.tileY + 0.5f);
        GridPoint2 spawnTile = findAdjacentSpawnTile((int) corpsePos.x, (int) corpsePos.y);

        GhostPlayerMonster ghost = new GhostPlayerMonster(activeBones, spawnTile.x, spawnTile.y, activeAssetManager);
        activeMaze.addMonster(ghost);

        if (activeSoundManager != null) {
            activeSoundManager.playThunder();
        }

        if (activeEventManager != null) {
            activeEventManager.addEvent(new GameEvent("A chilling gale howls! The ghost of " + activeBones.playerName + " awakens to battle!", 3.5f));
        }

        close();
    }

    private GridPoint2 findAdjacentSpawnTile(int cx, int cy) {
        int[][] deltas = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] d : deltas) {
            int tx = cx + d[0];
            int ty = cy + d[1];
            if (tx >= 0 && tx < activeMaze.getWidth() && ty >= 0 && ty < activeMaze.getHeight()) {
                if (!activeMaze.isWall(tx, ty) && !activeMaze.getMonsters().containsKey(new GridPoint2(tx, ty))) {
                    return new GridPoint2(tx, ty);
                }
            }
        }
        return new GridPoint2(cx, cy);
    }

    private void leaveInPeace() {
        if (activeEventManager != null) {
            activeEventManager.addEvent(new GameEvent("You step away, leaving the restless spirit undisturbed.", 2f));
        }
        close();
    }

    public void close() {
        this.setVisible(false);
        if (getStage() != null) {
            getStage().setKeyboardFocus(null);
        }
    }

    public boolean handleInput(int keycode) {
        if (!isVisible()) return false;
        if (keycode == Input.Keys.ESCAPE) {
            leaveInPeace();
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE || keycode == Input.Keys.E) {
            challengeSpirit();
            return true;
        }
        return false;
    }
}
