package com.bpm.minotaur.gamedata.events;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EventResolverTest {

    private Player player;
    private GameEventManager events;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
        ShelterAltar.getInstance().reset();
        player = new Player(2, 2);
        events = new GameEventManager();
    }

    @After
    public void tearDown() {
        Gdx.app = null;
        ShelterAltar.getInstance().reset();
    }

    // --- Costs ---------------------------------------------------------------------------------

    @Test
    public void aMaxHpCostIsAffordableOnlyWhileSomeMaxHpWouldRemain() {
        EventChoice choice = choice(cost(EventOutcome.Type.MAX_HP, -8));

        player.getStats().setMaxHP(10);
        assertTrue(resolver(0.5f).isAvailable(choice));

        player.getStats().setMaxHP(8);
        assertFalse(resolver(0.5f).isAvailable(choice));
    }

    @Test
    public void payingAMaxHpCostLowersTheMaximumAndClampsCurrentHp() {
        player.getStats().setMaxHP(20);
        player.setCurrentHP(20);

        resolver(0.5f).resolve(choice(cost(EventOutcome.Type.MAX_HP, -8)));

        assertEquals(12, player.getStats().getBaseMaxHP());
        assertTrue(player.getCurrentHP() <= player.getMaxHP());
    }

    @Test
    public void aGoldCostNeedsTheGoldAndSpendsIt() {
        EventChoice choice = choice(cost(EventOutcome.Type.GOLD, -1));

        player.getStats().setTreasureScore(0);
        assertFalse(resolver(0.5f).isAvailable(choice));

        player.getStats().setTreasureScore(5);
        assertTrue(resolver(0.5f).isAvailable(choice));
        resolver(0.5f).resolve(choice);
        assertEquals(4, player.getTreasureScore());
    }

    @Test
    public void takingFoodNeedsEnoughAndConsumesFromTheStack() {
        EventOutcome take = cost(EventOutcome.Type.TAKE_ITEM, 0);
        take.itemKind = "FOOD";
        take.count = 2;
        EventChoice choice = choice(take);

        assertFalse(resolver(0.5f).isAvailable(choice));

        Item rations = item(ItemType.FOOD, t -> t.isFood = true);
        rations.setStackCount(3);
        player.getInventory().pickupToBackpack(rations);
        assertTrue(resolver(0.5f).isAvailable(choice));

        resolver(0.5f).resolve(choice);
        assertEquals(1, rations.getStackCount());
    }

    @Test
    public void takingACursedItemRemovesIt() {
        EventOutcome take = cost(EventOutcome.Type.TAKE_ITEM, 0);
        take.itemKind = "CURSED";
        EventChoice choice = choice(take);

        Item clean = item(ItemType.RING, t -> t.isRing = true);
        player.getInventory().pickupToBackpack(clean);
        assertFalse(resolver(0.5f).isAvailable(choice));

        Item cursed = item(ItemType.SMALL_RING, t -> t.isRing = true);
        cursed.setBeatitude(Item.Beatitude.CURSED);
        player.getInventory().pickupToBackpack(cursed);
        assertTrue(resolver(0.5f).isAvailable(choice));

        resolver(0.5f).resolve(choice);
        assertFalse(player.getInventory().contains(cursed));
        assertTrue(player.getInventory().contains(clean));
    }

    // --- Gates ---------------------------------------------------------------------------------

    @Test
    public void aRangedWeaponGateOpensOnceOneIsCarried() {
        EventGate gate = new EventGate();
        gate.type = EventGate.Type.HAS_ITEM;
        gate.itemKind = "RANGED_WEAPON";
        gate.label = "Needs a ranged weapon";
        EventChoice choice = choice();
        choice.requires.add(gate);

        assertEquals("Needs a ranged weapon", resolver(0.5f).unmetLabel(choice));

        player.getInventory().pickupToBackpack(item(ItemType.BOW, t -> {
            t.isWeapon = true;
            t.isRanged = true;
        }));
        assertNull(resolver(0.5f).unmetLabel(choice));
    }

    @Test
    public void anAttributeGateReadsTheEffectiveAttribute() {
        EventGate gate = new EventGate();
        gate.type = EventGate.Type.ATTRIBUTE_MIN;
        gate.attribute = "WIS";
        gate.value = 14;
        EventChoice choice = choice();
        choice.requires.add(gate);

        player.getStats().setWisdom(10);
        assertFalse(resolver(0.5f).isAvailable(choice));
        player.getStats().setWisdom(20);
        assertTrue(resolver(0.5f).isAvailable(choice));
    }

    // --- Checks --------------------------------------------------------------------------------

    @Test
    public void aChoiceWithoutACheckIsCertain() {
        assertEquals(1f, resolver(0.5f).chance(choice()), 0.0001f);
    }

    @Test
    public void aCheckUsesTheEffectiveAttributeAndLuck() {
        EventChoice choice = choice();
        choice.check = check("STR", 0.5f);
        int str = player.getEffectiveStrength();
        int luck = player.getLuck();
        assertEquals(EventOdds.chance(0.5f, str, luck), resolver(0.5f).chance(choice), 0.0001f);
    }

    @Test
    public void aPassedCheckAppliesSuccessAndAFailedOneAppliesFailure() {
        EventChoice choice = choice();
        choice.check = check(null, 0.5f);
        choice.success.add(attribute("STR", 1));
        EventOutcome poison = new EventOutcome();
        poison.type = EventOutcome.Type.ADD_STATUS;
        poison.status = "POISONED";
        poison.duration = 5;
        choice.failure.add(poison);

        int strBefore = player.getStats().getStrength();

        EventResolver.Result pass = resolver(0.01f).resolve(choice);
        assertTrue(pass.succeeded);
        assertEquals(strBefore + 1, player.getStats().getStrength());
        assertFalse(player.getStatusManager().hasEffect(StatusEffectType.POISONED));

        EventResolver.Result fail = resolver(0.99f).resolve(choice);
        assertFalse(fail.succeeded);
        assertEquals(strBefore + 1, player.getStats().getStrength());
        assertTrue(player.getStatusManager().hasEffect(StatusEffectType.POISONED));
    }

    // --- Outcomes ------------------------------------------------------------------------------

    @Test
    public void outcomeTextIsReportedAndAChainIsHandedBack() {
        EventOutcome luck = new EventOutcome();
        luck.type = EventOutcome.Type.LUCK;
        luck.amount = 1;
        luck.text = "Your step feels lighter.";
        EventOutcome chain = new EventOutcome();
        chain.type = EventOutcome.Type.CHAIN_EVENT;
        chain.eventId = "EVENT_NEXT";
        EventChoice choice = choice();
        choice.success.add(luck);
        choice.success.add(chain);

        int luckBefore = player.getStats().getLuck();
        EventResolver.Result result = resolver(0.5f).resolve(choice);

        assertEquals(luckBefore + 1, player.getStats().getLuck());
        assertTrue(result.messages.contains("Your step feels lighter."));
        assertEquals("EVENT_NEXT", result.chainedEventId);
    }

    @Test
    public void givingAnItemPutsItInThePack() {
        EventOutcome give = new EventOutcome();
        give.type = EventOutcome.Type.GIVE_ITEM;
        give.itemId = "SCROLL";
        give.count = 2;
        EventChoice choice = choice();
        choice.success.add(give);

        int before = player.getInventory().getAllItems().size();
        resolver(0.5f).resolve(choice);
        assertEquals(before + 2, player.getInventory().getAllItems().size());
    }

    @Test
    public void blessingAnItemBlessesOneThatWasNotAlready() {
        Item ring = item(ItemType.RING, t -> t.isRing = true);
        player.getInventory().pickupToBackpack(ring);
        EventOutcome bless = new EventOutcome();
        bless.type = EventOutcome.Type.BLESS_ITEM;
        bless.itemKind = "UNBLESSED";
        EventChoice choice = choice();
        choice.success.add(bless);

        resolver(0.5f).resolve(choice);
        assertEquals(Item.Beatitude.BLESSED, ring.getBeatitude());
    }

    @Test
    public void maxMpAndHealAndDamageMoveThePools() {
        player.getStats().setMaxMP(10);
        EventChoice choice = choice();
        EventOutcome mp = new EventOutcome();
        mp.type = EventOutcome.Type.MAX_MP;
        mp.amount = 5;
        choice.success.add(mp);

        resolver(0.5f).resolve(choice);
        assertEquals(15, player.getMaxMP());
    }

    // --- Helpers -------------------------------------------------------------------------------

    /** A resolver whose every roll comes up {@code roll}. */
    private EventResolver resolver(float roll) {
        Random fixed = new Random() {
            @Override
            public float nextFloat() {
                return roll;
            }
        };
        return new EventResolver(player, null, events, itemDataManager(), null, null, fixed);
    }

    private static ItemDataManager itemDataManager() {
        return new ItemDataManager() {
            @Override
            public Item createItem(ItemType type, int x, int y, ItemColor color,
                                   com.badlogic.gdx.assets.AssetManager assetManager) {
                return item(type, t -> { });
            }
        };
    }

    private static Item item(ItemType type, Consumer<ItemTemplate> setup) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = type.name();
        setup.accept(t);
        return Item.fromTemplate(type, t);
    }

    private static EventChoice choice(EventOutcome... costs) {
        EventChoice c = new EventChoice();
        c.text = "Choose.";
        for (EventOutcome cost : costs) {
            c.costs.add(cost);
        }
        return c;
    }

    private static EventOutcome cost(EventOutcome.Type type, int amount) {
        EventOutcome o = new EventOutcome();
        o.type = type;
        o.amount = amount;
        return o;
    }

    private static EventOutcome attribute(String attr, int amount) {
        EventOutcome o = new EventOutcome();
        o.type = EventOutcome.Type.ATTRIBUTE;
        o.attribute = attr;
        o.amount = amount;
        return o;
    }

    private static EventCheck check(String attribute, float base) {
        EventCheck c = new EventCheck();
        c.attribute = attribute;
        c.base = base;
        return c;
    }
}
