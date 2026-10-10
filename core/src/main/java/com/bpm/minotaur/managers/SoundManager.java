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

    private SoundBank bank = SoundBank.parse(new com.badlogic.gdx.utils.JsonReader().parse("{}"));
    private final java.util.Random bankRandom = new java.util.Random();

    private static SoundManager instance;
    private boolean swingToggle = false;
    private boolean laserToggle = false;
    private AbyssAmbienceManager abyssAmbienceManager;

    public static SoundManager getInstance() {
        return instance;
    }

    public SoundManager(DebugManager debugManager) {
        instance = this;
        this.debugManager = debugManager;
        this.retroAudioDevice = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
        this.abyssAmbienceManager = new AbyssAmbienceManager();
        loadModernSounds();
    }

    // Protected constructor for Headless/Mocking
    protected SoundManager() {
        instance = this;
        this.debugManager = null;
        this.retroAudioDevice = null;
        this.abyssAmbienceManager = null;
        // Do not load sounds
    }

    public void update(float delta) {
        if (abyssAmbienceManager != null) {
            abyssAmbienceManager.update(delta);
        }
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
        } else if (lastWeatherType == WeatherType.ASHFALL) {
            windBase = currentBaseVol * 0.30f;
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
        loadSound("dimensional_shift", "sounds/dimensional_shift.wav");
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
        // Houses of the Maze T2.9: cut from the 1984 Retro bundle's darkwave loops.
        loadSound("war_horn", "sounds/war/war_horn.ogg");
        loadSound("war_drums", "sounds/war/war_drums.ogg");
        // The Living War (W9, W11): Freesound recordings, see docs/asset-licenses.md.
        for (String war : WAR_SOUNDS) loadSound(war, "sounds/war/" + war + ".ogg");
        // Tarmin's Knell (plan K5): the toll, heard everywhere at once.
        loadSound("knell_gong", "sounds/sfx/toll.ogg");

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

        // --- Container and traversal cues ---
        // Bags used to share the chest's lid-and-latch sound because the open
        // handler branched on ItemCategory rather than type.
        loadSound("bag_open", "sounds/bag_open.wav");
        loadSound("backpack_foley", "sounds/backpack_foley.wav");
        loadSound("cloth_drop", "sounds/cloth_drop.wav");
        loadSound("armor_equip", "sounds/armor_equip.wav");
        loadSound("map_open", "sounds/map_open.wav");

        // --- Alarms & Sirens ---
        loadSound("alarm_03", "sounds/alarms/alarm_03.wav");
        loadSound("alarm_09", "sounds/alarms/alarm_09.wav");
        loadSound("alarm_10", "sounds/alarms/alarm_10.wav");
        loadSound("alarm_15", "sounds/alarms/alarm_15.wav");
        loadSound("alarm_20", "sounds/alarms/alarm_20.wav");
        loadSound("alarm_22", "sounds/alarms/alarm_22.wav");
        loadSound("alarm_29", "sounds/alarms/alarm_29.wav");

        // --- Monsters & Bosses Audio ---
        loadSound("monster_boss_cthulhu_1", "sounds/monster/486309__kp2494__cthulumonster_roar.mp3");
        loadSound("monster_boss_cthulhu_2", "sounds/monster/487177__kp2494__cthulhumonster_roar.mp3");
        loadSound("monster_boss_kong", "sounds/monster/401568__cylon8472__kong-roar_complete.wav");
        loadSound("monster_boss_sharktopus", "sounds/monster/126312__cmusounddesign__cr-sharktopusroar3.wav");
        loadSound("monster_boss_demon", "sounds/monster/469123__manim8__demon_lion_monster_growl_roar.wav");
        loadSound("monster_encounter_beast", "sounds/monster/418394__thebuilder15__beast-roar.wav");
        loadSound("monster_encounter_zombie", "sounds/monster/232289__zglar__zombie-or-monster-or-lion-roar.wav");
        loadSound("monster_encounter_short", "sounds/monster/491443__music15tree__roar.wav");
        loadSound("monster_encounter_roar8", "sounds/monster/505131__mitchanary__monster-roar_8.mp3");
        loadSound("monster_ambient_echo", "sounds/monster/340161__flechabr__ecoed-roar.wav");
        loadSound("monster_ambient_winter", "sounds/monster/500919__vanishedillusion__creature-roar-in-winter.wav");
        loadSound("monster_ambient_sea", "sounds/monster/837799__bikkit99__sea-creature-roar.wav");

        loadBank();
        // Descending used to play sounds/music/tarmin_enter_fx.ogg, which is the
        // retired player-death asset: commit 442c15d rewired the death *key* to
        // three staged cues but left call sites referencing the old file by raw
        // path, where nothing could flag them.
        loadSound("ladder_transition", "sounds/ladder_transition.wav");

        // --- Themed chunk entry stingers (contract slot g) ---
        loadSound("tarmin_roar", "sounds/tarmin_roar.mp3");
        loadSound("wind", "sounds/wind.ogg");
    }

    /** Loads every variant of every event in soundbank.json; a bad entry is logged and skipped, never fatal. */
    private void loadBank() {
        try {
            bank = SoundBank.parse(new com.badlogic.gdx.utils.JsonReader().parse(Gdx.files.internal("data/soundbank.json")));
        } catch (Exception e) {
            Gdx.app.error("SoundManager", "Cannot read data/soundbank.json; falling back to the built-in sounds", e);
            return;
        }
        for (String name : bank.eventNames()) {
            for (String path : bank.get(name).files) {
                loadSound(path, path);
            }
        }
    }

    /**
     * Plays one variant of a bank event. Returns false when the event, or every one of its files, is
     * unavailable, so a caller can fall back to the sound it used before the bank existed.
     */
    public boolean playEvent(String event) {
        // Retro is synthesized by design; the recordings belong to the modern world.
        if (debugManager != null && debugManager.getRenderMode() != DebugManager.RenderMode.MODERN) {
            return false;
        }
        String path = bank.pick(event, bankRandom);
        Sound sound = path == null ? null : modernSounds.get(path);
        if (sound == null) {
            return false;
        }
        SoundBank.Event e = bank.get(event);
        long id = sound.play(e.volume * getEffectiveSfxVolume());
        if (e.pitchJitter > 0f) {
            sound.setPitch(id, MathUtils.random(1f - e.pitchJitter, 1f + e.pitchJitter));
        }
        return true;
    }

    private void loadSound(String key, String path) {
        if (!Gdx.files.internal(path).exists()) {
            Gdx.app.error("SoundManager", "Sound file not found: " + path);
            return;
        }
        try {
            modernSounds.put(key, Gdx.audio.newSound(Gdx.files.internal(path)));
        } catch (Exception e) {
            // A file the backend cannot decode used to kill the game outright,
            // which is inconsistent with a missing file being merely logged. A
            // 24-bit PCM bag_open.wav ended every new expedition this way.
            // AudioFormatDecodabilityTest is what stops such a file landing;
            // this only keeps one bad asset from costing a player their run.
            Gdx.app.error("SoundManager", "Cannot decode sound, skipping: " + path, e);
        }
    }

    public void stopAllSounds() {
        for (Sound sound : modernSounds.values()) {
            sound.stop();
        }
        if (abyssAmbienceManager != null) {
            abyssAmbienceManager.stop();
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
        if (!playEvent("death_impact")) {
            playSound("player_body_fall", 0.9f);
        }
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
                case ASHFALL:
                    // A low, dead wind: ash does not howl.
                    if (modernSounds.containsKey("wind_loop")) {
                        currentWindId = modernSounds.get("wind_loop").loop(currentBaseVol * 0.30f * windMod, 0.70f, 0f);
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
        playCombatStartSound(null);
    }

    public void playCombatStartSound(Monster monster) {
        if (debugManager != null && debugManager.getRenderMode() != DebugManager.RenderMode.MODERN) {
            return;
        }
        if (monster != null && isBossOrMegabeast(monster)) {
            playBossEncounterSound();
        } else {
            playMonsterEncounterSound();
        }
    }

    public boolean isBossOrMegabeast(Monster monster) {
        if (monster == null) return false;
        if (monster.isBridgeBoss() || monster.isThemeChampion() || monster.holdsCourt() || monster.getMegabeastId() >= 0) {
            return true;
        }
        if (monster.getType() != null) {
            String name = monster.getType().name();
            return name.contains("MINOTAUR") || name.contains("LICH") || name.contains("VAMPIRE")
                    || name.contains("GOLEM") || name.contains("DRAGON") || name.contains("BEHOLDER")
                    || name.contains("HYDRA");
        }
        return false;
    }

    public void playBossEncounterSound() {
        String[] bosses = {
            "monster_boss_cthulhu_1", "monster_boss_cthulhu_2", "monster_boss_kong",
            "monster_boss_sharktopus", "monster_boss_demon"
        };
        String key = bosses[MathUtils.random(bosses.length - 1)];
        playSound(key, 0.95f);
    }

    public void playMonsterEncounterSound() {
        if (!playEvent("monster_roar")) {
            String[] encounters = {
                "monster_encounter_beast", "monster_encounter_zombie", "monster_encounter_short",
                "monster_encounter_roar8"
            };
            String key = encounters[MathUtils.random(encounters.length - 1)];
            playSound(key, 0.9f);
        }
    }

    // --- NEW: Visceral Combat Audio ---

    public void playWeaponSwing() {
        if (playEvent("swing")) {
            return;
        }
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

    /** The warp between the mortal realm and the Retro dimension; kept as the name existing callers use. */
    public void playDimensionalWarpSound() {
        playDimensionalShiftSound();
    }

    /**
     * The sound of shifting dimension, played whenever the world changes into (or out of) Retro.
     * Uses the recorded effect; the synthesized descending tones remain as the fallback for a build
     * where that file is missing or cannot be decoded.
     */
    public void playDimensionalShiftSound() {
        stopWeatherEffects();
        if (modernSounds.containsKey("dimensional_shift")) {
            playSound("dimensional_shift");
            return;
        }
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
        if (isMetal ? playEvent("hit_blade") : heavy && playEvent("hit_blunt")) {
            return;
        }
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

    /**
     * A gore sound from the soundbank ({@code gore_*} events), or, until an
     * event has files, {@code meat_hit} pitched to stand in for it: low and
     * loud for a burst, high and quiet for a gib landing. Silent with gore off.
     */
    public void playGore(String event, float fallbackVolume, float pitchLow, float pitchHigh) {
        if (!com.bpm.minotaur.gamedata.gore.GoreLevel.current().enabled()) return;
        if (playEvent(event)) return;
        Sound meat = modernSounds.get("meat_hit");
        if (meat == null) return;
        long id = meat.play(fallbackVolume * getEffectiveSfxVolume());
        meat.setPitch(id, MathUtils.random(pitchLow, pitchHigh));
    }

    public void playGibBurst() {
        playGore("gore_gib_burst", 1.0f, 0.62f, 0.75f);
    }

    public void playGibLand() {
        playGore("gore_gib_land", 0.35f, 1.25f, 1.5f);
    }

    public void playBoneCrack() {
        playGore("gore_bone_crack", 0.8f, 1.45f, 1.7f);
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
        if (playEvent("bow_shot")) {
            return;
        }
        String key = modernSounds.containsKey("player_bow_attack") ? "player_bow_attack" : "weapon_swing";
        if (modernSounds.containsKey(key)) {
            long id = modernSounds.get(key).play(0.85f);
            modernSounds.get(key).setPitch(id, MathUtils.random(0.95f, 1.05f));
        }
    }

    /** Soft containers: bags, packs, the bag of holding. */
    /**
     * A soft rustle for cloth containers, a hinge creak for everything else.
     */
    public void playContainerOpen(Item.ItemType type) {
        if (type == null) {
            playChestOpen();
            return;
        }
        switch (type) {
            case SMALL_BAG:
            case LARGE_BAG:
            case MEDIUM_PACK:
            case LARGE_PACK:
            case BAG_OF_HOLDING:
            case MONEY_BELT:
                playBagOpen();
                break;
            default:
                playChestOpen();
                break;
        }
    }

    public void playBagOpen() {
        if (!playEvent("bag")) {
            playSound("bag_open", 0.85f);
        }
    }

    public void playClothDrop() {
        if (!playEvent("cloth_drop")) {
            playSound("cloth_drop", 0.85f);
        }
    }

    public void playArmorEquip() {
        if (!playEvent("armor_equip")) {
            playSound("armor_equip", 0.90f);
        }
    }

    public void playInventoryFoley() {
        if (!playEvent("backpack_foley")) {
            playSound("backpack_foley", 0.85f);
        }
    }

    public void playMapOpen() {
        if (!playEvent("map_open")) {
            playSound("map_open", 0.85f);
        }
    }

    /**
     * Echo-localized, directional audio playback relative to player's position and orientation.
     */
    public void playDirectionalSound(String soundKey, float sourceX, float sourceY,
                                     float playerX, float playerY,
                                     com.bpm.minotaur.gamedata.Direction playerFacing,
                                     float maxRange, float baseVolume) {
        if (debugManager != null && debugManager.getRenderMode() != DebugManager.RenderMode.MODERN) {
            return;
        }
        Sound sound = modernSounds.get(soundKey);
        if (sound == null) return;

        float dx = sourceX - playerX;
        float dy = sourceY - playerY;
        float dist = (float) Math.hypot(dx, dy);

        // Distance volume attenuation with distant echo
        float volFactor;
        if (dist <= 0.01f) {
            volFactor = 1.0f;
        } else if (dist <= maxRange) {
            float norm = dist / maxRange;
            volFactor = Math.max(0.08f, 1.0f - norm * norm);
        } else {
            // Distant echo: barely audible due to distance
            volFactor = 0.04f;
        }

        float effectiveVol = baseVolume * volFactor * getEffectiveSfxVolume();
        if (effectiveVol < 0.005f) return;

        // Directional panning relative to player's facing direction
        float pan = 0f;
        if (dist > 0.05f && playerFacing != null) {
            switch (playerFacing) {
                case NORTH:
                    pan = dx / dist;
                    break;
                case SOUTH:
                    pan = -dx / dist;
                    break;
                case EAST:
                    pan = -dy / dist;
                    break;
                case WEST:
                    pan = dy / dist;
                    break;
            }
            pan = MathUtils.clamp(pan, -1.0f, 1.0f);
        }

        // Distance low-pass simulation: distant sounds lower slightly in pitch
        float pitch = (dist > 5.0f) ? MathUtils.random(0.88f, 0.96f) : MathUtils.random(0.97f, 1.03f);
        sound.play(effectiveVol, pitch, pan);
    }

    public void playAmbientMonsterSound(float monsterX, float monsterY, boolean isBoss,
                                       float playerX, float playerY,
                                       com.bpm.minotaur.gamedata.Direction playerFacing) {
        String key;
        if (isBoss) {
            String[] bossAmbient = {
                "monster_boss_cthulhu_1", "monster_boss_cthulhu_2", "monster_boss_kong",
                "monster_boss_sharktopus", "monster_boss_demon"
            };
            key = bossAmbient[MathUtils.random(bossAmbient.length - 1)];
        } else {
            String[] ambient = {
                "monster_ambient_echo", "monster_ambient_winter", "monster_ambient_sea",
                "monster_encounter_zombie", "monster_encounter_roar8"
            };
            key = ambient[MathUtils.random(ambient.length - 1)];
        }
        playDirectionalSound(key, monsterX, monsterY, playerX, playerY, playerFacing, 15.0f, isBoss ? 0.9f : 0.65f);
    }

    // Directional alarms
    public void playHarshKlaxon(float sx, float sy, float px, float py, com.bpm.minotaur.gamedata.Direction facing) {
        playDirectionalSound("alarm_03", sx, sy, px, py, facing, 18.0f, 0.85f);
    }

    public void playHarshKlaxon() {
        playSound("alarm_03", 0.85f);
    }

    public void playBossWarningAlarm(float sx, float sy, float px, float py, com.bpm.minotaur.gamedata.Direction facing) {
        String key = MathUtils.randomBoolean() ? "alarm_09" : "alarm_10";
        playDirectionalSound(key, sx, sy, px, py, facing, 20.0f, 0.90f);
    }

    public void playBossWarningAlarm() {
        playSound(MathUtils.randomBoolean() ? "alarm_09" : "alarm_10", 0.90f);
    }

    public void playWarZoneAlarm(float sx, float sy, float px, float py, com.bpm.minotaur.gamedata.Direction facing) {
        playDirectionalSound("alarm_15", sx, sy, px, py, facing, 22.0f, 0.85f);
    }

    public void playWarZoneAlarm() {
        playSound("alarm_15", 0.85f);
    }

    public void playGashesWarWail(float sx, float sy, float px, float py, com.bpm.minotaur.gamedata.Direction facing, boolean altFaction) {
        String key = altFaction ? "alarm_22" : "alarm_20";
        playDirectionalSound(key, sx, sy, px, py, facing, 20.0f, 0.85f);
    }

    public void playGashesWarWail(boolean altFaction) {
        playSound(altFaction ? "alarm_22" : "alarm_20", 0.85f);
    }

    public void playHouseBreachWarning(float sx, float sy, float px, float py, com.bpm.minotaur.gamedata.Direction facing) {
        playDirectionalSound("alarm_29", sx, sy, px, py, facing, 25.0f, 0.90f);
    }

    public void playHouseBreachWarning() {
        playSound("alarm_29", 0.90f);
    }

    /** Level change via a ladder. Deliberately not a death cue. */
    public void playLadderTransition() {
        playSound("ladder_transition", 0.7f);
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

    /**
     * A battle's sound (plan T2.9): the horns when a front arrives, drums and the clash of steel as
     * the lines close, the horns again over a roar when a side breaks. A crowd-of-battle bed and real
     * steel wait on the bundle's Medieval Fighting folder, which was never extracted.
     */
    /** The Living War's one-shots, loaded by their file names under sounds/war (W11). */
    public static final String[] WAR_SOUNDS = {"horn_battle", "horn_great", "volley_archers", "volley_darts",
            "clash_sword", "clash_melee", "thud_distant_1", "thud_distant_2", "siege_barrage", "siege_bursts",
            "bell_strange", "bell_cathedral"};

    /** What the player hears as an encounter reaches their chunk, or breaks (Living War W2). */
    public void playEncounterCue(EncounterDirector.Cue cue) {
        if (cue == null) return;
        switch (cue) {
            case CLASH:
                playSound("horn_battle", 0.8f);
                playSound("clash_melee", 0.9f);
                break;
            case DRUMS:
                playSound("war_drums", 0.9f);
                break;
            case FIRE:
                playSound("thud_distant_1", 0.6f);
                break;
            case ROUT:
                playSound("war_horn", 0.8f);
                break;
            default:
                break;
        }
    }

    /**
     * A war sound from a bearing: {@code pan} -1 left to 1 right, {@code volume} before the effects
     * setting. For the far-off fights of {@link WarAudio}.
     */
    public void playWarSound(String key, float volume, float pan) {
        if (debugManager != null && debugManager.getRenderMode() != DebugManager.RenderMode.MODERN) return;
        Sound sound = modernSounds.get(key);
        if (sound == null) return;
        float v = volume * getEffectiveSfxVolume();
        if (v < 0.005f) return;
        sound.play(v, MathUtils.random(0.94f, 1.04f), MathUtils.clamp(pan, -1f, 1f));
    }

    public void playWarCue(WarManager.Cue cue) {
        if (cue == null) return;
        switch (cue) {
            case HORNS:
                playSound("war_horn", 0.9f);
                break;
            case JOINED:
                playSound("war_drums", 0.9f);
                playSound("metal_hit_heavy", 0.7f);
                playSound("monster_roar", 0.6f);
                break;
            case ROUT:
                playSound("war_horn", 0.8f);
                playSound("monster_roar_heavy", 0.7f);
                break;
        }
    }

    private int knellsRung;

    /** How many times the knell has rung this session (for the play-test). */
    public int knellsRung() {
        return knellsRung;
    }

    /** Tarmin's Knell: the gong, unplaced, at its own volume, heard everywhere at once. */
    public void playKnell() {
        knellsRung++;
        if (modernSounds.containsKey("knell_gong")) {
            modernSounds.get("knell_gong").play(SettingsManager.getInstance().getKnellVolume());
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

        if (playEvent("spell_" + archetype.name().toLowerCase())) {
            return;
        }

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
                        case OBSCURING_MIST:
                            // A long, soft exhalation rather than an impact: fog arrives, it
                            // does not land. This switch has no default, so an archetype
                            // without a case here casts in silence.
                            for (int f : new int[] { 300, 260, 230, 205, 185, 170 }) {
                                playRetroSound(f, 0.075f, 0.28f);
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
        if (playEvent("bandage")) {
            return;
        }
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
        if (abyssAmbienceManager != null) {
            abyssAmbienceManager.dispose();
        }
        for (Sound sound : modernSounds.values()) {
            sound.dispose();
        }
        if (retroAudioDevice != null) {
            retroAudioDevice.dispose();
        }
    }
}
