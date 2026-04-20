package com.bpm.minotaur.headless;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.managers.SoundManager;
import com.bpm.minotaur.weather.WeatherIntensity;
import com.bpm.minotaur.weather.WeatherType;

public class HeadlessSoundManager extends SoundManager {

    public HeadlessSoundManager() {
        super();
    }

    @Override
    public void playPlayerAttackSound(Item weapon) {
    }

    @Override
    public void playMonsterAttackSound(Monster monster) {
    }

    @Override
    public void playPickupItemSound() {
    }

    @Override
    public void playPlayerLevelUpSound() {
    }

    @Override
    public void playDoorOpenSound() {
    }

    @Override
    public void playCombatStartSound() {
    }

    @Override
    public void playPlayerDeathSound() {
    }

    @Override
    public void updateWeatherAudio(WeatherType type, WeatherIntensity intensity) {
    }

    @Override
    public void playThunder() {
    }

    @Override
    public void playLightningCrash() {
    }

    @Override
    public void stopAllSounds() {
    }
}
