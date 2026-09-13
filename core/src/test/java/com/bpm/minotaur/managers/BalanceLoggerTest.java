package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies the Player Power Score heuristic accounts for a weapon's full
 * effective damage (STR/ring bonus and rarity-tier multiplier), not just a
 * raw roll of its base damage dice -- the gap identified while grilling the
 * "DEADLY" combat-start prediction against real telemetry.
 */
public class BalanceLoggerTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @Test
    public void testWeaponScoreIncludesRarityMultiplier() {
        Player player = new Player(2, 2);
        player.getStats().setStrength(10); // +0 STR damage bonus, isolates the rarity effect

        Item plainSword = weapon("1d1", ItemColor.TAN); // 1 guaranteed base damage
        player.getInventory().setRightHand(plainSword);
        int plainScore = BalanceLogger.calculatePlayerPowerScore(player);

        Item legendarySword = weapon("1d1", ItemColor.WHITE); // same dice, top War rarity (x2.0)
        player.getInventory().setRightHand(legendarySword);
        int legendaryScore = BalanceLogger.calculatePlayerPowerScore(player);

        assertTrue("A top-rarity weapon must score higher than a plain one with identical dice",
                legendaryScore > plainScore);
    }

    @Test
    public void testWeaponScoreIncludesStrengthDamageBonus() {
        Player player = new Player(2, 2);
        Item sword = weapon("1d1", ItemColor.TAN);
        player.getInventory().setRightHand(sword);

        player.getStats().setStrength(10); // +0 bonus
        int baseScore = BalanceLogger.calculatePlayerPowerScore(player);

        player.getStats().setStrength(20); // +5 bonus, meaningfully stronger
        int strongScore = BalanceLogger.calculatePlayerPowerScore(player);

        assertTrue("Higher STR (more damage bonus) must raise the power score",
                strongScore > baseScore);
    }

    private Item weapon(String damageDice, ItemColor color) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = "Test Weapon";
        template.isWeapon = true;
        template.damageDice = damageDice;
        Item item = Item.fromTemplate(Item.ItemType.RUSTY_SWORD, template);
        item.setItemColor(color);
        return item;
    }
}
