package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.generation.Biome;

/**
 * Manages spatial altitude wind rush, biome water sounds, and the expedition gate dive SFX
 * during the Main Menu Attract Mode flyover.
 */
public class AttractSoundManager implements Disposable {

    private static final String TAG = "AttractSoundManager";

    private Music windLoop;
    private Music rainLoop;
    private Sound gateDiveSound;
    private Sound[] arrowSounds;
    private Sound warHornSound;
    private Sound thunderSound;

    private boolean initialized = false;

    public AttractSoundManager() {
        try {
            if (Gdx.files.internal("sounds/wind.ogg").exists()) {
                windLoop = Gdx.audio.newMusic(Gdx.files.internal("sounds/wind.ogg"));
                windLoop.setLooping(true);
                windLoop.setVolume(0f);
                windLoop.play();
            }

            if (Gdx.files.internal("sounds/rain.ogg").exists()) {
                rainLoop = Gdx.audio.newMusic(Gdx.files.internal("sounds/rain.ogg"));
                rainLoop.setLooping(true);
                rainLoop.setVolume(0f);
                rainLoop.play();
            }

            if (Gdx.files.internal("sounds/music/tarmin_enter_fx.ogg").exists()) {
                gateDiveSound = Gdx.audio.newSound(Gdx.files.internal("sounds/music/tarmin_enter_fx.ogg"));
            } else if (Gdx.files.internal("sounds/tarmin_enter_fx.ogg").exists()) {
                gateDiveSound = Gdx.audio.newSound(Gdx.files.internal("sounds/tarmin_enter_fx.ogg"));
            }

            // Arrow whoosh sounds
            java.util.List<Sound> arrows = new java.util.ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                if (Gdx.files.internal("sounds/sfx/arrow_" + i + ".wav").exists()) {
                    arrows.add(Gdx.audio.newSound(Gdx.files.internal("sounds/sfx/arrow_" + i + ".wav")));
                }
            }
            if (!arrows.isEmpty()) {
                arrowSounds = arrows.toArray(new Sound[0]);
            }

            if (Gdx.files.internal("sounds/war/war_horn.ogg").exists()) {
                warHornSound = Gdx.audio.newSound(Gdx.files.internal("sounds/war/war_horn.ogg"));
            } else if (Gdx.files.internal("sounds/war/horn_battle.ogg").exists()) {
                warHornSound = Gdx.audio.newSound(Gdx.files.internal("sounds/war/horn_battle.ogg"));
            }

            if (Gdx.files.internal("sounds/thunder_1.ogg").exists()) {
                thunderSound = Gdx.audio.newSound(Gdx.files.internal("sounds/thunder_1.ogg"));
            }

            initialized = true;
        } catch (Throwable t) {
            if (Gdx.app != null) {
                Gdx.app.log(TAG, "Audio device not available or sounds missing: " + t.getMessage());
            }
        }
    }

    /**
     * Updates spatial ambient volume based on camera altitude and biome proximity.
     */
    public void update(Vector3 cameraPos, Biome currentBiome) {
        if (!initialized) return;

        try {
            // Wind volume increases as camera climbs above tree level towards High Spire (y > 6.0)
            if (windLoop != null) {
                float altitudeFactor = MathUtils.clamp((cameraPos.y - 5.0f) / 15.0f, 0.0f, 1.0f);
                float targetWindVol = altitudeFactor * 0.35f;
                windLoop.setVolume(targetWindVol);
            }

            // Lakelands rain / water ambience
            if (rainLoop != null) {
                float targetRainVol = (currentBiome == Biome.LAKELANDS) ? 0.25f : 0.0f;
                rainLoop.setVolume(targetRainVol);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Plays the dramatic gate rumble / whoosh when the player launches an expedition.
     */
    public void playGateDive() {
        if (!initialized || gateDiveSound == null) return;
        try {
            gateDiveSound.play(0.85f);
        } catch (Throwable ignored) {
        }
    }

    public void playArrowWhistle() {
        if (!initialized || arrowSounds == null || arrowSounds.length == 0) return;
        try {
            int idx = (int) (Math.random() * arrowSounds.length);
            arrowSounds[idx].play(0.35f);
        } catch (Throwable ignored) {
        }
    }

    public void playWarHorn() {
        if (!initialized || warHornSound == null) return;
        try {
            warHornSound.play(0.40f);
        } catch (Throwable ignored) {
        }
    }

    public void playThunder() {
        if (!initialized || thunderSound == null) return;
        try {
            thunderSound.play(0.50f);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void dispose() {
        try {
            if (windLoop != null) {
                windLoop.stop();
                windLoop.dispose();
                windLoop = null;
            }
            if (rainLoop != null) {
                rainLoop.stop();
                rainLoop.dispose();
                rainLoop = null;
            }
            if (gateDiveSound != null) {
                gateDiveSound.dispose();
                gateDiveSound = null;
            }
            if (arrowSounds != null) {
                for (Sound s : arrowSounds) {
                    if (s != null) s.dispose();
                }
                arrowSounds = null;
            }
            if (warHornSound != null) {
                warHornSound.dispose();
                warHornSound = null;
            }
            if (thunderSound != null) {
                thunderSound.dispose();
                thunderSound = null;
            }
        } catch (Throwable ignored) {
        }
        initialized = false;
    }
}
