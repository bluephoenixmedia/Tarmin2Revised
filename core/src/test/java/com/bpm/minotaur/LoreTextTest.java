package com.bpm.minotaur;

import com.bpm.minotaur.managers.DimensionalManager;
import com.bpm.minotaur.rendering.Hud;
import com.bpm.minotaur.screens.PlayerDeathScreen;
import com.bpm.minotaur.ui.HudReadouts;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * "Lore text: the ritual becomes the Bridge, and Tarmin is no Minotaur" (#273): the player-facing
 * lore tells the myth of the Bridge of Souls (#267). The daughter guard stays in HintManagerTest.
 */
public class LoreTextTest {

    private static List<String> playerFacingLore() throws IOException {
        List<String> lore = new ArrayList<>();
        for (float pressure : new float[]{0f, 0.3f, 0.6f, 0.95f}) lore.add(PlayerDeathScreen.hungerLine(pressure));
        HudReadouts.Tip loose = HudReadouts.hungerTip(50, 50, true);
        HudReadouts.Tip near = HudReadouts.hungerTip(45, 50, false);
        lore.add(loose.details + " " + loose.remedy);
        lore.add(near.details + " " + near.remedy);
        lore.add(Hud.bridgeBarText(100f, true));
        for (String glyph : DimensionalManager.VOID_LORE_GLYPHS) lore.add(glyph);
        lore.add(readAsset("data/chronicle_grammar.json"));
        return lore;
    }

    private static String readAsset(String relative) throws IOException {
        for (String root : new String[]{"assets/", "../assets/"}) {
            Path p = Paths.get(root + relative);
            if (Files.exists(p)) return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        }
        throw new IOException("missing " + relative);
    }

    @Test
    public void theRitualIsTheBridgeAndNothingIsSacrificed() throws IOException {
        for (String text : playerFacingLore()) {
            String lower = text.toLowerCase();
            assertFalse(text, lower.contains("ritual"));
            assertFalse(text, lower.contains("sacrifice"));
        }
        assertTrue(PlayerDeathScreen.hungerLine(0.95f).contains("Bridge"));
    }

    @Test
    public void tarminIsNoMinotaur() throws IOException {
        for (String text : playerFacingLore()) {
            assertFalse(text, text.contains("strode the Minotaur"));
            assertFalse(text, text.contains("The Minotaur stirs"));
            assertFalse(text, text.contains("Minotaur King Tarmin"));
        }
    }

    @Test
    public void theWardenGuardsTheLastStoneNotTheBringerOfDeath() {
        HudReadouts.Tip loose = HudReadouts.hungerTip(50, 50, true);
        HudReadouts.Tip near = HudReadouts.hungerTip(45, 50, false);
        for (String text : new String[]{loose.details, loose.remedy, near.details, Hud.bridgeBarText(100f, true)}) {
            assertFalse(text, text.toLowerCase().contains("bringer of death"));
        }
        assertTrue(loose.details, loose.details.contains("Warden"));
        assertTrue(Hud.bridgeBarText(100f, true).contains("WARDEN"));
    }

    @Test
    public void theSeekerWasTakenNotAVolunteer() throws IOException {
        assertFalse(readAsset("data/chronicle_grammar.json").contains("came to the maze for vengeance"));
    }
}
