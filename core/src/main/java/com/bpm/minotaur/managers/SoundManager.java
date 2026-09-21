package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.weather.WeatherType;
import com.bpm.minotaur.weather.WeatherIntensity;

import java.util.HashMap;
import java.util.Map;

public class SoundManager {

    private final DebugManager debugManager;
    private final Map<String, Sound> modernSounds = new HashMap<>();
    private final AudioDevice retroAudioDevice;
    private static final int SAMPLE_RATE = 44100;

    // --- Sound Layer Tracking ---
    private long currentRainId = -1;
    private long currentWindId = -1;
    private WeatherType lastWeatherType = null;
    private WeatherIntensity lastWeatherIntensity = null;

    // --- Volume Dampening & Crossfade State ---
    private boolean isDampened = false;
    private float currentDampenFactor = 1.0f;
    private float targetDampenFactor = 1.0f;
    private float currentBaseVol = 0.5f;

    private static SoundManager instance;
    private boolean swingToggle = false;
    private boolean laserToggle = false;

    public static SoundManager getInstance() {
        return instance;
    }

    public SoundManager(DebugManager debugManager) {
        instance = this;
        this.debugManager = debugManager;
        this.retroAudioDevice = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
        loadModernSounds();
    }

    // Protected constructor for Headless/Mocking
    protected SoundManager() {
        instance = this;
        this.debugManager = null;
        this.retroAudioDevice = null;
        // Do not load sounds
    }

    public void update(float delta) {
        if (Math.abs(currentDampenFactor - targetDampenFactor) > 0.001f) {
            // Smoothly interpolate over ~0.4s (speed factor 3.5)
            currentDampenFactor = MathUtils.lerp(currentDampenFactor, targetDampenFactor, Math.min(1.0f, 3.5f * delta));
            applyLoopVolumes();
        }
    }

    public float getCurrentDampenFactor() {
        return currentDampenFactor;
    }

    public float getTargetDampenFactor() {
        return targetDampenFactor;
    }

    private void applyLoopVolumes() {
        float windBase = currentBaseVol;
        if (lastWeatherType == WeatherType.STORM) {
            windBase = currentBaseVol * 0.85f;
        } else if (lastWeatherType == WeatherType.SNOW) {
            windBase = currentBaseVol * 0.45f;
        } else if (lastWeatherType == WeatherType.BLIZZARD) {
            windBase = currentBaseVol * 0.95f;
        } else if (lastWeatherType == WeatherType.TORNADO) {
            windBase = 1.0f;
        }

        float windDampen = (currentDampenFactor < 0.35f) ? currentDampenFactor * 0.60f : currentDampenFactor;

        float sfxScale = getEffectiveSfxVolume();
        if (currentRainId != -1 && modernSounds.containsKey("rain_loop")) {
            modernSounds.get("rain_loop").setVolume(currentRainId, currentBaseVol * currentDampenFactor * sfxScale);
        }
        if (currentWindId != -1 && modernSounds.containsKey("wind_loop")) {
            modernSounds.get("wind_loop").setVolume(currentWindId, windBase * windDampen * sfxScale);
        }
    }

    public float getEffectiveSfxVolume() {
        try {
            return SettingsManager.getInstance().getSfxVolume();
        } catch (Exception ignored) {
            return 0.80f;
        }
    }

    private void loadModernSounds() {
        loadSound("player_attack", "sounds/player_attack.wav");
        loadSound("player_bow_attack", "sounds/player_bow_attack.wav");
        loadSound("player_spiritual_attack", "sounds/player_spiritual_attack.wav");
        loadSound("pickup_item", "sounds/pickup_item.wav");
        loadSound("door_open", "sounds/door_open.wav");
        // Death cues. "player_death" used to point at a music stinger, which is why death
        // never landed as an event; these three are staged across the death sequence.
        loadSound("player_grunt", "sounds/hurt.mp3");
        loadSound("player_body_fall", "sounds/mountain_hurt.mp3");
        loadSound("monster_attack", "sounds/monster_attack.wav");
        loadSound("tarmin_roar", "sounds/tarmin_roar.mp3");
        loadSound("monster_roar", "sounds/monster_roar.wav");
        loadSound("player_level_up", "sounds/level_up.mp3");
        loadSound("attack", "sounds/attack.mp3");
        loadSound("tarmin_laugh", "sounds/tarmin_laugh.ogg");
        loadSound("rain_loop", "sounds/rain.ogg");
        loadSound("wind_loop", "sounds/wind.ogg");
        loadSound("thunder_1", "sounds/thunder_1.ogg");
        loadSound("thunder_2", "sounds/thunder_2.ogg");
        loadSound("thunder_3", "sounds/thunder_3.ogg");
        loadSound("thunder_4", "sounds/thunder_4.ogg");
        loadSound("thunder_5", "sounds/thunder_5.ogg");
        loadSound("lightning_crash_1", "sounds/lightning_crash_1.ogg");
        loadSound("lightning_crash_2", "sounds/lightning_crash_2.ogg");
        loadSound("lightning_crash_3", "sounds/lightning_crash_3.ogg");

        // --- NEW: Visceral Combat & Weapon Impacts ---
        loadSound("weapon_swing", "sounds/weapon_swing.ogg");
        loadSound("weapon_swing_2", "sounds/weapon_swing_2.ogg");
        loadSound("meat_hit", "sounds/meat_hit.ogg");
        loadSound("metal_hit", "sounds/metal_hit.ogg");
        loadSound("metal_hit_heavy", "sounds/metal_hit_heavy.ogg");
        loadSound("monster_grunt_light", "sounds/monster_grunt_light.wav");
        loadSound("monster_roar_heavy", "sounds/monster_roar_heavy.wav");

        // --- NEW: Tactile UI & World Audio ---
        loadSound("ui_click", "sounds/ui_click.ogg");
        loadSound("book_flip", "sounds/book_flip.ogg");
        loadSound("chest_open", "sounds/chest_open.ogg");
        loadSound("coins", "sounds/coins.ogg");
        loadSound("door_creak", "sounds/door_creak.ogg");

        // --- NEW: Void Lasers & Ambient Loops ---
        loadSound("void_laser", "sounds/void_laser.wav");
        loadSound("void_laser_alt", "sounds/void_laser_alt.wav");
        loadSound("amb_void_groan", "sounds/amb_void_groan.wav");
        loadSound("amb_doom_subbass", "sounds/amb_doom_subbass.wav");
    }

    private void loadSound(String key, String path) {
        if (Gdx.files.internal(path).exists()) {
            modernSounds.put(key, Gdx.audio.newSound(Gdx.files.internal(path)));
        } else {
            Gdx.app.error("SoundManager", "Sound file not found: " + path);
        }
    }

    public void stopAllSounds() {
        for (Sound sound : modernSounds.values()) {
            sound.stop();
        }
        currentRainId = -1;
        currentWindId = -1;
        lastWeatherType = null;
        lastWeatherIntensity = null;
    }

    public void stopWeatherEffects() {
        if (currentRainId != -1 && modernSounds.containsKey("rain_loop")) {
            modernSounds.get("rain_loop").stop(currentRainId);
            currentRainId = -1;
        }
        if (currentWindId != -1 && modernSounds.containsKey("wind_loop")) {
            modernSounds.get("wind_loop").stop(currentWindId);
            currentWindId = -1;
        }
        lastWeatherType = null;
        lastWeatherIntensity = null;
    }

    /**
     * The cry at the killing blow. Silent for attrition deaths -- starving to death without a
     * sound is more unsettling than a stock grunt, and costs nothing.
     */
    public void playDeathGrunt(boolean violent) {
        if (violent) {
            playSound("player_grunt", 1.0f);
        }
    }

    /** The groan as the body hits the floor. */
    public void playDeathImpact() {
        playSound("player_body_fall", 0.9f);
    }

    /**
     * Tarmin's laugh as the blood takes the screen.
     *
     * <p>Deliberately does not call {@link #stopAllSounds()}: the impact groan is still ringing
     * when this fires, and cutting it dead mid-breath wrecks the handover.
     */
    public void playDeathReveal() {
        MusicManager.getInstance().stop();
        stopWeatherEffects();
        playSound("tarmin_laugh");
    }

    /** One-shot death audio, for callers with no sequence to stage across. */
    public void playPlayerDeathSound() {
        stopAllSounds();
        playDeathReveal();
    }

    // --- Volume Dampening for Interiors (Smooth Crossfade) ---
    public void setDampened(boolean dampened) {
        this.isDampened = dampened;
        this.targetDampenFactor = dampened ? 0.25f : 1.0f;
    }

    public void setDampenedImmediate(boolean dampened) {
        this.isDampened = dampened;
        this.targetDampenFactor = dampened ? 0.25f : 1.0f;
        this.currentDampenFactor = this.targetDampenFactor;
        applyLoopVolumes();
    }

    public void updateWeatherAudio(WeatherType type, WeatherIntensity intensity) {
        if (DimensionalManager.getInstance().isWeatherSuppressed()) {
            stopWeatherEffects();
            return;
        }

        if (type == lastWeatherType && intensity == lastWeatherIntensity) {
            return;
        }

        boolean typeChanged = (type != lastWeatherType);
        lastWeatherType = type;
        lastWeatherIntensity = intensity;

        // Base volume scaled by intensity tier
        currentBaseVol = (intensity == WeatherIntensity.LIGHT) ? 0.35f
                : (intensity == WeatherIntensity.MEDIUM) ? 0.55f
                : (intensity == WeatherIntensity.HEAVY) ? 0.80f : 1.0f;

        float rainMod = currentDampenFactor;
        float windMod = (currentDampenFactor < 0.35f) ? currentDampenFactor * 0.60f : currentDampenFactor;

        if (typeChanged) {
            if (currentRainId != -1 && modernSounds.containsKey("rain_loop")) {
                modernSounds.get("rain_loop").stop(currentRainId);
            }
            if (currentWindId != -1 && modernSounds.containsKey("wind_loop")) {
                modernSounds.get("wind_loop").stop(currentWindId);
            }

            currentRainId = -1;
            currentWindId = -1;

            switch (type) {
                case RAIN:
                case STORM:
                    if (modernSounds.containsKey("rain_loop")) {
                        currentRainId = modernSounds.get("rain_loop").loop(currentBaseVol * rainMod);
                    }
                    if (type == WeatherType.STORM && modernSounds.containsKey("wind_loop")) {
                        float windBase = currentBaseVol * 0.85f;
                        currentWindId = modernSounds.get("wind_loop").loop(windBase * windMod, 1.0f, 0f);
                    }
                    break;
                case SNOW:
                    if (modernSounds.containsKey("wind_loop")) {
                        float snowVol = currentBaseVol * 0.45f * windMod;
                        currentWindId = modernSounds.get("wind_loop").loop(snowVol, 1.15f, 0f);
                    }
                    break;
                case BLIZZARD:
                    if (modernSounds.containsKey("wind_loop")) {
                        float blizzardVol = currentBaseVol * 0.95f * windMod;
                        currentWindId = modernSounds.get("wind_loop").loop(blizzardVol, 0.82f, 0f);
                    }
                    break;
                case TORNADO:
                    if (modernSounds.containsKey("wind_loop")) {
                        currentWindId = modernSounds.get("wind_loop").loop(1.0f * windMod, 0.65f, 0.0f);
                    }
                    break;
                default:
                    break;
            }
        } else {
            // Intensity changed within same weather type: dynamically adjust active loop volumes
            applyLoopVolumes();
        }
    }

    public void playThunder() {
        int variant = MathUtils.random(1, 5);
        float vol = Math.max(0.40f, currentDampenFactor);
        if (modernSounds.containsKey("thunder_" + variant)) {
            modernSounds.get("thunder_" + variant).play(vol);
        }
    }

    public void playRollingThunder() {
        int variant = MathUtils.random(1, 5);
        float vol = isDampened ? 0.35f : 0.75f;
        if (modernSounds.containsKey("thunder_" + variant)) {
            modernSounds.get("thunder_" + variant).play(vol, 0.8f, 0.0f);
        }
    }

    public void playLightningCrash() {
        int variant = MathUtils.random(1, 3);
        float vol = isDampened ? 0.5f : 1.0f;
        if (modernSounds.containsKey("lightning_crash_" + variant)) {
            modernSounds.get("lightning_crash_" + variant).play(vol);
        }
    }

    public void playPlayerAttackSound(Item weapon) {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            if (weapon != null) {
                if (weapon.getType() == Item.ItemType.BOW) {
                    playSound("player_bow_attack");
                } else if (weapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
                    playSound("player_spiritual_attack");
                } else {
                    playSound("player_attack");
                }
            } else {
                playSound("player_attack");
            }
        } else {
            if (weapon != null && weapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
                playRetroArpeggio(new int[] { 523, 659, 784 }, 0.04f);
            } else {
                playRetroSound(110, 0.15f, 0.8f);
            }
        }
    }

    public void playMonsterAttackSound(Monster monster) {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            playSound("monster_attack");
        } else {
            playSound("attack");
        }
    }

    public void playPickupItemSound() {
        playSound("pickup_item");
    }

    public void playPlayerLevelUpSound() {
        playSound("player_level_up");
    }

    public void playDoorOpenSound() {
        playSound("door_open");
    }

    public void playCombatStartSound() {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            playSound("monster_roar");
        } else {
            // playSound("tarmin_roar");
        }
    }

    // --- NEW: Visceral Combat Audio ---

    public void playWeaponSwing() {
        String key = swingToggle ? "weapon_swing_2" : "weapon_swing";
        swingToggle = !swingToggle;
        if (!modernSounds.containsKey(key)) {
            key = "weapon_swing";
        }
        if (modernSounds.containsKey(key)) {
            long id = modernSounds.get(key).play(0.85f);
            modernSounds.get(key).setPitch(id, MathUtils.random(0.90f, 1.10f));
        } else {
            // Fallback existing
            playSound("player_attack");
        }
    }

    public void playDimensionalWarpSound() {
        stopWeatherEffects();
        if (retroAudioDevice != null) {
            new Thread(() -> {
                try {
                    int[] freqs = new int[] { 880, 740, 587, 440, 330, 220, 165, 110, 82, 55 };
                    for (int f : freqs) {
                        playRetroSound(f, 0.08f, 0.65f);
                    }
                } catch (Exception ignored) {
                }
            }).start();
        } else {
            playSound("pickup_item");
        }
    }

    public void playWeaponImpact(boolean heavy) {
        playWeaponImpact(heavy, false);
    }

    public void playWeaponImpact(boolean heavy, boolean isMetal) {
        String sound;
        if (isMetal) {
            sound = heavy ? "metal_hit_heavy" : "metal_hit";
        } else {
            sound = "meat_hit";
        }
        if (modernSounds.containsKey(sound)) {
            long id = modernSounds.get(sound).play(heavy ? 0.9f : 0.75f);
            modernSounds.get(sound).setPitch(id, MathUtils.random(0.90f, 1.10f));
        }
    }

    public void playUiClick() {
        if (modernSounds.containsKey("ui_click")) {
            long id = modernSounds.get("ui_click").play(0.6f);
            modernSounds.get("ui_click").setPitch(id, MathUtils.random(0.95f, 1.05f));
        }
    }

    public void playBookFlip() {
        if (modernSounds.containsKey("book_flip")) {
            long id = modernSounds.get("book_flip").play(0.7f);
            modernSounds.get("book_flip").setPitch(id, MathUtils.random(0.95f, 1.05f));
        }
    }

    /**
     * The report of a firearm.
     *
     * <p>PLACEHOLDER AUDIO: no gunshot sample exists in assets/sounds. A thunderclap
     * pitched well down is a genuinely close relative of a black-powder crack, and
     * standing in lets the animation and VFX be judged in play. Replace by adding a
     * real sample as "firearm_shot" in loadModernSounds() -- this method will pick it
     * up with no other change.
     */
    public void playFirearmShot() {
        String key = modernSounds.containsKey("firearm_shot") ? "firearm_shot" : null;
        if (key == null) {
            key = modernSounds.containsKey("thunder_1") ? "thunder_1" : "metal_hit_heavy";
        }
        if (modernSounds.containsKey(key)) {
            long id = modernSounds.get(key).play(0.95f);
            // Pitched down hard: a thunderclap at source pitch reads as weather, not a gun.
            modernSounds.get(key).setPitch(id, MathUtils.random(0.55f, 0.68f));
        }
    }

    /** A shot that fizzles instead of firing. Same source, thinner and quieter. */
    public void playFirearmMisfire() {
        String key = modernSounds.containsKey("firearm_misfire") ? "firearm_misfire" : "weapon_swing_2";
        if (modernSounds.containsKey(key)) {
            long id = modernSounds.get(key).play(0.55f);
            modernSounds.get(key).setPitch(id, MathUtils.random(1.25f, 1.45f));
        }
    }

    /**
     * Loosing a bow or crossbow.
     *
     * <p>The bow sample was loaded and reachable only through a dead selector -- every
     * player attack, melee or ranged, played the generic swing whoosh instead.
     */
    public void playBowShot() {
        String key = modernSounds.containsKey("player_bow_attack") ? "player_bow_attack" : "weapon_swing";
        if (modernSounds.containsKey(key)) {
            long id = modernSounds.get(key).play(0.85f);
            modernSounds.get(key).setPitch(id, MathUtils.random(0.95f, 1.05f));
        }
    }

    public void playChestOpen() {
        if (modernSounds.containsKey("chest_open")) {
            long id = modernSounds.get("chest_open").play(0.8f);
            modernSounds.get("chest_open").setPitch(id, MathUtils.random(0.95f, 1.05f));
        }
    }

    public void playBookFlipSound() {
        if (modernSounds.containsKey("book_flip")) {
            long id = modernSounds.get("book_flip").play(0.85f);
            modernSounds.get("book_flip").setPitch(id, MathUtils.random(0.95f, 1.05f));
        } else if (retroAudioDevice != null) {
            playScrollUnfurl();
        }
    }

    /**
     * The chest that doesn't creak. Pitched well below the ordinary roar so the player
     * hears "that was not a lid" before the sprite has finished changing -- the cheapest
     * tell in the mimic encounter, and the reason world chests now creak at all.
     */
    public void playMimicRevealSound() {
        String key = modernSounds.containsKey("monster_roar_heavy") ? "monster_roar_heavy" : "monster_roar";
        if (modernSounds.containsKey(key)) {
            long id = modernSounds.get(key).play(0.9f);
            modernSounds.get(key).setPitch(id, MathUtils.random(0.6f, 0.72f));
        }
    }

    public void playCoins() {
        if (modernSounds.containsKey("coins")) {
            long id = modernSounds.get("coins").play(0.8f);
            modernSounds.get("coins").setPitch(id, MathUtils.random(0.95f, 1.05f));
        }
    }

    public void playDoorCreak() {
        if (modernSounds.containsKey("door_creak")) {
            modernSounds.get("door_creak").play(0.75f);
        } else {
            playSound("door_open");
        }
    }

    public void playVoidLaser() {
        String key = laserToggle ? "void_laser_alt" : "void_laser";
        laserToggle = !laserToggle;
        if (modernSounds.containsKey(key)) {
            long id = modernSounds.get(key).play(0.85f);
            modernSounds.get(key).setPitch(id, MathUtils.random(0.92f, 1.08f));
        }
    }

    public void playMonsterReaction(Monster monster, float damageRatio) {
        if (monster.getCurrentHP() <= 0) {
            // Death sound (handled elsewhere usually, but good to have dedicated)
            playSound("monster_roar");
            return;
        }

        if (damageRatio > 0.25f) {
            // Heavy Hit
            if (modernSounds.containsKey("monster_roar_heavy")) {
                long id = modernSounds.get("monster_roar_heavy").play();
                modernSounds.get("monster_roar_heavy").setPitch(id, MathUtils.random(0.8f, 0.95f));
            } else {
                playSound("monster_roar");
            }
        } else {
            // Light Hit
            if (modernSounds.containsKey("monster_grunt_light")) {
                long id = modernSounds.get("monster_grunt_light").play();
                modernSounds.get("monster_grunt_light").setPitch(id, MathUtils.random(0.95f, 1.1f));
            } else {
                playSound("monster_attack"); // Re-use usually short sound
            }
        }
    }

    public void playSound(String name) {
        playSound(name, 1.0f);
    }

    public void playSound(String name, float baseVol) {
        if (modernSounds.containsKey(name)) {
            modernSounds.get(name).play(baseVol * getEffectiveSfxVolume());
        }
    }

    public void playSpellSound(com.bpm.minotaur.gamedata.spells.VisualArchetype archetype) {
        if (archetype == null) return;

        // Check if modern audio asset exists
        String soundKey = archetype.getSoundKey();
        if (modernSounds.containsKey(soundKey)) {
            playSound(soundKey);
            return;
        }

        // Procedural Audio Synthesis via retroAudioDevice
        if (retroAudioDevice != null) {
            new Thread(() -> {
                try {
                    switch (archetype) {
                        case FLAME_BOLT:
                            for (int f : new int[] { 650, 520, 410, 300, 220 }) {
                                playRetroSound(f, 0.035f, 0.55f);
                            }
                            break;
                        case FROST_RAY:
                            for (int f : new int[] { 880, 1175, 1318, 1760 }) {
                                playRetroSound(f, 0.04f, 0.45f);
                            }
                            break;
                        case LIGHTNING_ARC:
                            for (int f : new int[] { 400, 1200, 250, 1400, 200, 950 }) {
                                playRetroSound(f, 0.02f, 0.65f);
                            }
                            break;
                        case FORCE_MISSILE:
                            for (int f : new int[] { 523, 659, 784, 1046 }) {
                                playRetroSound(f, 0.03f, 0.5f);
                            }
                            break;
                        case EXPLOSIVE_BURST:
                            for (int f : new int[] { 220, 160, 120, 85, 55, 40 }) {
                                playRetroSound(f, 0.06f, 0.85f);
                            }
                            break;
                        case HOLY_RADIANCE:
                            for (int f : new int[] { 440, 554, 659, 880, 1108 }) {
                                playRetroSound(f, 0.05f, 0.5f);
                            }
                            break;
                        case NECROTIC_DRAIN:
                            for (int f : new int[] { 440, 311, 260, 185, 130 }) {
                                playRetroSound(f, 0.055f, 0.6f);
                            }
                            break;
                        case TOXIC_CLOUD:
                            for (int f : new int[] { 280, 330, 260, 350, 240, 310 }) {
                                playRetroSound(f, 0.04f, 0.5f);
                            }
                            break;
                        case SPATIAL_WARP:
                            for (int f : new int[] { 880, 660, 440, 660, 990, 1320 }) {
                                playRetroSound(f, 0.04f, 0.6f);
                            }
                            break;
                        case ARCANE_WARD:
                            for (int f : new int[] { 330, 494, 659, 988 }) {
                                playRetroSound(f, 0.05f, 0.55f);
                            }
                            break;
                        case PSYCHIC_SHOCK:
                            for (int f : new int[] { 700, 850, 680, 890, 720, 920 }) {
                                playRetroSound(f, 0.025f, 0.5f);
                            }
                            break;
                        case THUNDER_CONCUSSION:
                            for (int f : new int[] { 110, 85, 65, 50, 40, 32 }) {
                                playRetroSound(f, 0.07f, 0.9f);
                            }
                            break;
                    }
                } catch (Exception ignored) {
                }
            }).start();
        } else {
            // Headless / fallback
            playSound("player_spiritual_attack");
        }
    }

    /**
     * Synthesizes high-frequency whispered flutter for unrolling an ancient parchment scroll.
     */
    public void playScrollUnfurl() {
        if (retroAudioDevice != null) {
            new Thread(() -> {
                try {
                    for (int f : new int[] { 1400, 1650, 1850, 1550, 1900, 1350 }) {
                        playRetroSound(f, 0.022f, 0.35f);
                    }
                } catch (Exception ignored) {
                }
            }).start();
        } else {
            playSound("player_spiritual_attack");
        }
    }

    /**
     * Synthesizes resonant harmonic chords for roguelike utility scrolls.
     */
    public void playScrollChime(com.bpm.minotaur.gamedata.item.ScrollEffectType effect) {
        if (effect == null) return;
        if (retroAudioDevice != null) {
            new Thread(() -> {
                try {
                    switch (effect) {
                        case IDENTIFY:
                            // Ascending Golden Triad (C5 - E5 - G5 - C6)
                            for (int f : new int[] { 523, 659, 784, 1046 }) {
                                playRetroSound(f, 0.07f, 0.65f);
                            }
                            break;
                        case MAGIC_MAPPING:
                            // Sonar Ping Echo (A5 - E6 - A6)
                            for (int f : new int[] { 880, 1318, 1760 }) {
                                playRetroSound(f, 0.08f, 0.60f);
                            }
                            break;
                        case ENCHANT_WEAPON:
                            // Steel Singing Chime (D5 - A5 - F#6)
                            for (int f : new int[] { 587, 880, 1480 }) {
                                playRetroSound(f, 0.07f, 0.70f);
                            }
                            break;
                        case ENCHANT_ARMOR:
                            // Silver Resonant Aegis (E5 - B5 - G#6)
                            for (int f : new int[] { 659, 988, 1661 }) {
                                playRetroSound(f, 0.08f, 0.65f);
                            }
                            break;
                        case TELEPORT:
                            // Phasing Warp Sweep (B5 - G5 - E5 - C5 - A4 - F4)
                            for (int f : new int[] { 988, 784, 659, 523, 440, 349 }) {
                                playRetroSound(f, 0.04f, 0.60f);
                            }
                            break;
                        case CREATE_MONSTER:
                            // Low Eldritch Summoning Growl (160 - 130 - 100 - 75 - 55)
                            for (int f : new int[] { 160, 130, 100, 75, 55 }) {
                                playRetroSound(f, 0.08f, 0.85f);
                            }
                            break;
                    }
                } catch (Exception ignored) {
                }
            }).start();
        } else {
            playSound("player_spiritual_attack");
        }
    }

    /**
     * Synthesizes crisp cloth tearing and linen wrapping friction for field dressing.
     */
    public void playBandageTearSound() {
        if (retroAudioDevice != null) {
            new Thread(() -> {
                try {
                    for (int f : new int[] { 1150, 1380, 960, 1280, 840, 1050 }) {
                        playRetroSound(f, 0.024f, 0.40f);
                    }
                } catch (Exception ignored) {
                }
            }).start();
        } else {
            playSound("pickup_item");
        }
    }

    /**
     * Synthesizes soft squelching and soothing friction of medicinal salve or moss application.
     */
    public void playSalveApplySound() {
        if (retroAudioDevice != null) {
            new Thread(() -> {
                try {
                    for (int f : new int[] { 260, 310, 350, 290, 240, 210 }) {
                        playRetroSound(f, 0.035f, 0.45f);
                    }
                } catch (Exception ignored) {
                }
            }).start();
        } else {
            playSound("pickup_item");
        }
    }

    /**
     * Synthesizes a warm, restorative resolution chord upon completing a field surgery / triage action.
     */
    public void playFirstAidSuccessSound() {
        if (retroAudioDevice != null) {
            new Thread(() -> {
                try {
                    // C4 - E4 - G4 - C5 calming tonic
                    for (int f : new int[] { 261, 329, 392, 523 }) {
                        playRetroSound(f, 0.08f, 0.55f);
                    }
                } catch (Exception ignored) {
                }
            }).start();
        } else {
            playSound("pickup_item");
        }
    }

    private void playRetroSound(int frequency, float duration, float volume) {
        int numSamples = (int) (duration * SAMPLE_RATE);
        short[] samples = new short[numSamples];
        int wavelength = Math.max(1, SAMPLE_RATE / Math.max(1, frequency));
        for (int i = 0; i < numSamples; i++) {
            samples[i] = (short) ((i % wavelength < wavelength / 2) ? (Short.MAX_VALUE * volume)
                    : (-Short.MAX_VALUE * volume));
        }
        if (retroAudioDevice != null) {
            retroAudioDevice.writeSamples(samples, 0, numSamples);
        }
    }

    private void playRetroArpeggio(int[] frequencies, float noteDuration) {
        for (int freq : frequencies) {
            playRetroSound(freq, noteDuration, 0.7f);
        }
    }

    public void dispose() {
        stopAllSounds();
        for (Sound sound : modernSounds.values()) {
            sound.dispose();
        }
        retroAudioDevice.dispose();
    }
}
