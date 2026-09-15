package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.ShopkeeperNpc;
import com.bpm.minotaur.gamedata.injury.InjuryManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.laser.LaserBurst;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.telemetry.TelemetryManager;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * The traveling merchant's use of his Void chain laser, observed at the AI's
 * public seam: when he fires, at what, how often, and what happens to whoever
 * the beams find.
 */
public class ShopkeeperLaserTest {

    private Maze maze;
    private ShopkeeperNpc merchant;
    private Player player;
    private GameEventManager events;
    private ShopkeeperAiManager ai;

    @Before
    public void setUp() {
        maze = new Maze(1, new int[16][16]);
        merchant = new ShopkeeperNpc(2, 8, null);   // centre (2.5, 8.5)
        player = new Player(2, 14);                  // six tiles north, clear of any eastward lane
        events = new GameEventManager();
        ai = new ShopkeeperAiManager(new Random(4242L));
    }

    @Test
    public void testMerchantLasesAMonsterInRangeAndInSight() {
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 60, 10, 6, 8); // four tiles east
        maze.addMonster(goblin);

        ai.update(merchant, maze, player, events);

        GameEvent burst = events.findAndConsume(GameEvent.EventType.LASER_BURST);
        assertNotNull("A monster four tiles away in the open is lased", burst);
        assertTrue(burst.payload instanceof LaserBurst.BurstResult);
        assertTrue("The beams that connect are burned into the monster", goblin.getCurrentHP() < 60);
    }

    @Test
    public void testMerchantHoldsFireBeyondRangeOrBehindAWall() {
        Monster distant = new Monster(Monster.MonsterType.GOBLIN, 60, 10, 10, 8); // eight tiles east
        maze.addMonster(distant);
        ai.update(merchant, maze, player, events);
        assertNull("Eight tiles is beyond the weapon's range",
                events.findAndConsume(GameEvent.EventType.LASER_BURST));
        assertEquals(60, distant.getCurrentHP());

        int[][] walls = new int[16][16];
        for (int y = 0; y < 16; y++) {
            walls[y][4] = 1; // a solid wall line between merchant and monster
        }
        Maze walled = new Maze(1, walls);
        Monster hidden = new Monster(Monster.MonsterType.GOBLIN, 60, 10, 6, 8);
        walled.addMonster(hidden);
        merchant.getPosition().set(2.5f, 8.5f);

        ai.update(merchant, walled, player, events);
        assertNull("He does not shoot through stone", events.findAndConsume(GameEvent.EventType.LASER_BURST));
        assertEquals(60, hidden.getCurrentHP());
    }

    /**
     * Burst, then the weapon cycles for two turns before it can fire again, so a
     * firefight has a rhythm the player can read and move inside. He holds his
     * ground while it cycles rather than wandering off mid-fight.
     */
    @Test
    public void testBurstThenTwoTurnCooldown() {
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 500, 10, 6, 8); // survives many bursts
        maze.addMonster(goblin);
        boolean[] fired = new boolean[7];
        for (int turn = 0; turn < fired.length; turn++) {
            ai.update(merchant, maze, player, events);
            fired[turn] = events.findAndConsume(GameEvent.EventType.LASER_BURST) != null;
        }
        assertArrayEquals(new boolean[] { true, false, false, true, false, false, true }, fired);
        assertEquals("He holds his ground while the weapon cycles", 2.5f, merchant.getPosition().x, 0.001f);
        assertEquals(8.5f, merchant.getPosition().y, 0.001f);
    }

    /**
     * Fire discipline: he will not open a burst with the player squarely in the
     * line of fire -- between him and the target, or standing right behind it.
     * Once he is firing he does not stop, which is how strays still find people.
     */
    @Test
    public void testHeWillNotOpenABurstWithThePlayerInTheLineOfFire() {
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 60, 10, 6, 8);
        maze.addMonster(goblin);

        player.getPosition().set(4.5f, 8.5f); // squarely between him and the goblin
        ai.update(merchant, maze, player, events);
        assertNull("He holds fire with the player between him and the target",
                events.findAndConsume(GameEvent.EventType.LASER_BURST));

        player.getPosition().set(9.5f, 8.5f); // right behind the goblin, downrange
        ai.update(merchant, maze, player, events);
        assertNull("He holds fire with the player standing right behind the target",
                events.findAndConsume(GameEvent.EventType.LASER_BURST));
        assertEquals(60, goblin.getCurrentHP());

        player.getPosition().set(4.5f, 12.5f); // out of the line
        ai.update(merchant, maze, player, events);
        assertNotNull("With the line clear he opens up", events.findAndConsume(GameEvent.EventType.LASER_BURST));
    }

    /**
     * When a stray clips the player it lands in full -- ordinary armour does
     * nothing against coherent light -- and is recorded against the merchant in
     * telemetry. Then he makes it right: an apology, a restitution event (the
     * game presses a burn salve into their hands), and a discount on the next trade.
     */
    @Test
    public void testAClippedPlayerTakesTheHitAndIsCompensated() {
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 100000, 10, 5, 8); // three tiles east, unkillable
        maze.addMonster(goblin);
        player.getPosition().set(6.5f, 7.5f); // downrange, off the aim line, where the climb walks strays
        player.getStats().setMaxHP(1000);
        player.getStats().setCurrentHP(1000);
        int friendlyFireBefore = TelemetryManager.getInstance().getMerchantFriendlyFireDamage();

        boolean clipped = false;
        for (int turn = 0; turn < 900 && !clipped; turn++) {
            int hpBefore = player.getCurrentHP();
            ai.update(merchant, maze, player, events);
            GameEvent burst = events.findAndConsume(GameEvent.EventType.LASER_BURST);
            if (burst == null || !((LaserBurst.BurstResult) burst.payload).isPlayerClipped()) {
                continue;
            }
            clipped = true;
            int raw = ((LaserBurst.BurstResult) burst.payload).getDamageToPlayer();
            assertEquals("Ordinary armour does nothing against the beam", raw, hpBefore - player.getCurrentHP());
            assertNotNull("He makes restitution", events.findAndConsume(GameEvent.EventType.SHOPKEEPER_RESTITUTION));
            assertTrue("And knocks something off the next trade", merchant.getRestitutionDiscount() > 0f);
            assertTrue("And says so", apologised());
        }
        assertTrue("A player standing where the climb walks strays must eventually be clipped", clipped);
        assertTrue(TelemetryManager.getInstance().getMerchantFriendlyFireDamage() > friendlyFireBefore);
    }

    /**
     * More than one stray can clip the same bystander in a single burst -- their
     * angles are close enough near the merchant that two adjacent beams both
     * cross a tight corridor. Q15 and Q23 verified the trauma floor and the
     * hardened-armour halving against a SINGLE beam's magnitude; summing every
     * clip in the burst before checking either would let several individually
     * safe grazes launder into one over-floor burn. Injury must be rolled per
     * clip. Proven here by putting a hardened-armour player in the lane: if
     * clips were summed, enough of them landing in one burst could exceed the
     * trauma floor even through the armour; if rolled per clip (as required),
     * they never can, no matter how many strays land in the same burst.
     */
    @Test
    public void testMultipleClipsInOneBurstAreRolledForInjuryIndividuallyNotSummed() {
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 100000, 10, 6, 8); // unkillable, at max range
        maze.addMonster(goblin);
        player.getPosition().set(7.5f, 7.5f); // downrange, off-axis: reliably crossed by 2+ climbing beams
        player.getStats().setMaxHP(14);
        player.getStats().setCurrentHP(10000);
        player.getEquipment().setWornChest(hardenedArmour(Item.ItemType.FULL_PLATE, 5));
        player.getEquipment().setWornHelmet(hardenedArmour(Item.ItemType.BASINET, 2));
        player.getEquipment().setWornLegs(hardenedArmour(Item.ItemType.BRONZE_LEGGINGS, 2));

        int multiClipBurstsSeen = 0;
        for (int turn = 0; turn < 3000 && multiClipBurstsSeen < 20; turn++) {
            ai.update(merchant, maze, player, events);
            GameEvent burstEvent = events.findAndConsume(GameEvent.EventType.LASER_BURST);
            events.consumeAll(GameEvent.EventType.SHOPKEEPER_RESTITUTION);
            if (burstEvent == null) {
                continue;
            }
            LaserBurst.BurstResult result = (LaserBurst.BurstResult) burstEvent.payload;
            if (result.getPlayerClipDamages().size() >= 2) {
                multiClipBurstsSeen++;
            }
            assertFalse("A hardened-armour player must never take an anatomical wound from the merchant",
                    player.getInjuryManager().hasAnyInjuries());
        }
        assertTrue("This scenario must actually exercise multiple clips in one burst to be meaningful",
                multiClipBurstsSeen > 0);
    }

    private Item hardenedArmour(Item.ItemType type, int armorClassBonus) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = type.name();
        template.isArmor = true;
        template.armorClassBonus = armorClassBonus;
        Item item = Item.fromTemplate(type, template);
        item.setItemColor(ItemColor.PURPLE);
        return item;
    }

    /**
     * The apology has to be in his own vocabulary, not just words: slime residue
     * is the exact SEVERE_BURN treatment InjuryManager.applyTreatment already
     * recognizes, so the moment he burns someone he hands over the specific thing
     * that treats it -- on top of the discount, not instead of it.
     */
    @Test
    public void testClippingHandsOverSlimeResidueOnTopOfTheDiscount() {
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 100000, 10, 5, 8); // unkillable
        maze.addMonster(goblin);
        player.getPosition().set(6.5f, 7.5f);
        player.getStats().setMaxHP(1000);
        player.getStats().setCurrentHP(1000);
        ItemDataManager itemDataManager = new ItemDataManager() {
            @Override
            public Item createItem(ItemType type, int x, int y, ItemColor color,
                                   com.badlogic.gdx.assets.AssetManager assetManager) {
                ItemTemplate t = new ItemTemplate();
                t.friendlyName = type.name();
                return Item.fromTemplate(type, t);
            }
        };

        boolean clipped = false;
        for (int turn = 0; turn < 900 && !clipped; turn++) {
            ai.update(merchant, maze, player, events, itemDataManager, null);
            if (events.findAndConsume(GameEvent.EventType.LASER_BURST) == null) {
                continue;
            }
            clipped = player.getInventory().hasItemOfType(Item.ItemType.SLIME_RESIDUE);
        }
        assertTrue("A clip must hand over slime residue", clipped);
        assertTrue("The discount still applies alongside it", merchant.getRestitutionDiscount() > 0f);
    }

    private boolean apologised() {
        for (String line : events.getMessageHistory()) {
            for (String apology : ShopkeeperAiManager.APOLOGIES) {
                if (line.equals(apology)) {
                    return true;
                }
            }
        }
        return false;
    }
}
