package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.WorldManager;
import com.bpm.minotaur.gamedata.Maze;

/**
 * Defines the true effects of scrolls.
 */
public enum ScrollEffectType {

    IDENTIFY("Identify", "You can identify items.", true),
    TELEPORT("Teleportation", "You teleport away!", true),
    MAGIC_MAPPING("Magic Mapping", "A map appears in your mind!", true),
    ENCHANT_WEAPON("Enchant Weapon", "Your weapon glows blue.", true),
    ENCHANT_ARMOR("Enchant Armor", "Your armor glows silver.", true),
    CREATE_MONSTER("Create Monster", "A monster appears!", true);

    private final String baseName;
    private final String consumeMessage;
    private final boolean selfIdentifies;

    ScrollEffectType(String baseName, String consumeMessage, boolean selfIdentifies) {
        this.baseName = baseName;
        this.consumeMessage = consumeMessage;
        this.selfIdentifies = selfIdentifies;
    }

    public String getBaseName() {
        return baseName;
    }

    public String getConsumeMessage() {
        return consumeMessage;
    }

    public boolean doesSelfIdentify() {
        return selfIdentifies;
    }

    // Logic will be implemented in Player.read() via switch
}
