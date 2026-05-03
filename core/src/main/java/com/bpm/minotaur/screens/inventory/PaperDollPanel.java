package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;
import com.bpm.minotaur.paperdoll.PaperDollWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Left-page paper doll: character portrait surrounded by equipment slots.
 *
 * Equipment slots are positioned absolutely inside a {@link Group} so they
 * can be placed freely around the portrait image.  The layout mirrors the
 * open-book mockup:
 *
 * <pre>
 *             [HEAD]
 *  [NECK][BACK]  portrait  [WEAPON_MAIN]
 *  [CHEST]       portrait  [WEAPON_OFF]
 *  [ARMS][HANDS] portrait
 *                [RING_L][RING_R]
 *             [LEGS]
 *          [FEET_L][FEET_R]
 * </pre>
 */
public class PaperDollPanel extends WidgetGroup implements InventoryEventBus.Listener {

    private static final float SLOT_SZ = 64f;

    // Panel dimensions — sized to fill the left-page vertical space
    private static final float PW = 560f;   // panel width
    private static final float PH = 720f;   // panel height

    // Portrait centre x / top y in panel coordinates
    private static final float CX  = PW * 0.5f;
    private static final float PT  = PH * 0.88f; // portrait top y
    private static final float PB  = PH * 0.24f; // portrait bottom y

    // Column offsets from portrait centre
    private static final float LEFT_CLOSE  = -160f;
    private static final float LEFT_FAR    = -230f;
    private static final float RIGHT_CLOSE =  100f;
    private static final float RIGHT_FAR   =  170f;

    private final Player              player;
    private final InventorySkin       skin;
    private final InventoryDragDropHandler dnd;
    private final ItemDataManager     idm;

    // All equipment slots — kept so we can sync from model on refresh()
    private InventorySlot slotHead, slotNeck, slotBack;
    private InventorySlot slotChest, slotArms, slotHands;
    private InventorySlot slotLegs, slotFeetL, slotFeetR;
    private InventorySlot slotWeaponMain, slotWeaponOff;
    private InventorySlot slotRingL, slotRingR;

    private final List<InventorySlot> allEquip = new ArrayList<>();

    // Tooltip label shown in the panel footer
    private final Label tooltipLabel;

    // Optional paper doll widget reference (set from ModernInventoryUI)
    private PaperDollWidget paperDollWidget;

    public PaperDollPanel(Player player, InventorySkin skin,
                          InventoryDragDropHandler dnd,
                          ItemDataManager idm, Texture dollTexture) {
        this.player = player;
        this.skin   = skin;
        this.dnd    = dnd;
        this.idm    = idm;

        setSize(PW, PH);

        // Portrait background image
        Image portrait = new Image(new TextureRegionDrawable(dollTexture));
        portrait.setSize(PW * 0.44f, PH * 0.62f);
        portrait.setPosition(CX - portrait.getWidth() / 2f, PB);
        addActor(portrait);

        // Equipment slots
        float midY  = (PT + PB) / 2f;
        float topY  = PT - SLOT_SZ - 10f;
        float botY  = PB - SLOT_SZ - 10f;
        float botY2 = PB - SLOT_SZ * 2f - 20f;

        slotHead      = equip("Head",    null,         CX + LEFT_CLOSE / 4f, topY + SLOT_SZ * 1.3f);
        slotNeck      = equip("Neck",    ItemType.AMULET, CX + LEFT_FAR,  topY);
        slotBack      = equip("Back",    ItemType.CLOAK,  CX + LEFT_CLOSE, topY - SLOT_SZ - 4f);
        slotChest     = equip("Chest",   ItemType.HAUBERK,CX + LEFT_FAR,  midY + SLOT_SZ * 0.5f);
        slotArms      = equip("Arms",    ItemType.ARMS,   CX + LEFT_FAR,  midY - SLOT_SZ * 0.5f);
        slotHands     = equip("Hands",   ItemType.GAUNTLETS, CX + LEFT_FAR, midY - SLOT_SZ * 1.6f);
        slotLegs      = equip("Legs",    ItemType.LEGS,   CX + LEFT_CLOSE, botY);
        slotFeetL     = equip("Feet",    ItemType.BOOTS,  CX - SLOT_SZ,    botY2);
        slotFeetR     = equip("Feet",    ItemType.BOOTS,  CX,              botY2);
        slotWeaponMain= equip("R.Hand",  ItemType.SWORD,  CX + RIGHT_CLOSE, topY);
        slotWeaponOff = equip("L.Hand",  ItemType.SHIELD, CX + RIGHT_CLOSE, topY - SLOT_SZ - 4f);
        slotRingL     = equip("Ring",    ItemType.RING,   CX + RIGHT_CLOSE, botY);
        slotRingR     = equip("Ring 2",  ItemType.RING,   CX + RIGHT_FAR,  botY);

        // Tooltip area
        tooltipLabel = new Label("",
                new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_MUTED));
        tooltipLabel.setPosition(10f, 4f);
        tooltipLabel.setWidth(PW - 20f);
        addActor(tooltipLabel);

        // Wire right-click → drop and hover → tooltip for every equipment slot
        for (InventorySlot s : allEquip) {
            wireSlotInteractions(s);
        }
    }

    // ── Slot factory ─────────────────────────────────────────────────

    private InventorySlot equip(String name, ItemType restrictType, float x, float y) {
        InventorySlot slot = new InventorySlot(
                InventorySlot.SlotCategory.EQUIPMENT, -1, name, restrictType, skin, idm);
        slot.setSize(SLOT_SZ, SLOT_SZ);
        slot.setPosition(x - SLOT_SZ / 2f, y);
        dnd.register(slot);
        allEquip.add(slot);
        addActor(slot);
        return slot;
    }

    private void wireSlotInteractions(final InventorySlot slot) {
        slot.addListener(new ClickListener() {
            { setButton(-1); } // listen to all mouse buttons

            @Override
            public void enter(InputEvent e, float x, float y, int ptr, com.badlogic.gdx.scenes.scene2d.Actor from) {
                if (slot.getItem() != null) tooltipLabel.setText(tooltipFor(slot.getItem()));
            }

            @Override
            public void exit(InputEvent e, float x, float y, int ptr, com.badlogic.gdx.scenes.scene2d.Actor to) {
                tooltipLabel.setText("");
            }

            @Override
            public void clicked(InputEvent e, float x, float y) {
                if (e.getButton() == Input.Buttons.RIGHT) {
                    dnd.dropItem(slot);
                } else if (getTapCount() == 2 && e.getButton() == Input.Buttons.LEFT) {
                    smartUnequip(slot);
                }
            }
        });
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Attaches an existing {@link PaperDollWidget} so it renders inside this panel.
     * Call from ModernInventoryUI after constructing the widget.
     */
    public void attachPaperDollWidget(PaperDollWidget widget) {
        paperDollWidget = widget;
        widget.setPosition(0, 0);
        widget.setSize(PW, PH);
        // Insert behind slots by adding before them (slots were added after portrait)
        addActorBefore(getChildren().get(1), widget); // index 1 = after portrait image
    }

    public void refresh() {
        PlayerEquipment eq = player.getEquipment();
        slotHead.setItem(eq.getWornHelmet());
        slotNeck.setItem(eq.getWornNeck());
        slotBack.setItem(eq.getWornBack());
        slotChest.setItem(eq.getWornChest());
        slotArms.setItem(eq.getWornArms());
        slotHands.setItem(eq.getWornGauntlets());
        slotLegs.setItem(eq.getWornLegs());
        slotFeetL.setItem(eq.getWornBoots());
        slotFeetR.setItem(null); // second boot slot is cosmetic only
        slotWeaponMain.setItem(player.getInventory().getRightHand());
        slotWeaponOff.setItem(player.getInventory().getLeftHand());
        slotRingL.setItem(eq.getWornRing());
        slotRingR.setItem(eq.getWornRing2());

        syncPaperDoll(eq);
    }

    // ── WidgetGroup preferred size ────────────────────────────────────

    @Override public float getPrefWidth()  { return PW; }
    @Override public float getPrefHeight() { return PH; }

    // ── Private helpers ───────────────────────────────────────────────

    private void smartUnequip(InventorySlot slot) {
        if (slot.isEmpty()) return;
        // Find the first free backpack slot and move the item there.
        // ModernInventoryUI registers backpack slots; since we don't hold a reference,
        // we delegate via the drag handler's moveItem using a synthetic event fired
        // on the bus so BackpackPanel can handle it directly via its own slots array.
        // For simplicity we call player.getInventory() directly:
        if (player.getInventory().pickupToBackpack(slot.getItem())) {
            dnd.dropItem(slot); // removes from model; item was just added to backpack above
        }
    }

    private void syncPaperDoll(PlayerEquipment eq) {
        if (paperDollWidget == null) return;
        paperDollWidget.clearEquipment();
        if (eq.getWornHelmet() != null) paperDollWidget.equip(eq.getWornHelmet());
        if (eq.getWornChest()  != null) paperDollWidget.equip(eq.getWornChest());
        if (eq.getWornArms()   != null) paperDollWidget.equip(eq.getWornArms());
        if (eq.getWornGauntlets() != null) paperDollWidget.equip(eq.getWornGauntlets());
        if (eq.getWornLegs()   != null) paperDollWidget.equip(eq.getWornLegs());
        if (eq.getWornBoots()  != null) paperDollWidget.equip(eq.getWornBoots());
        if (eq.getWornBack()   != null) paperDollWidget.equip(eq.getWornBack());
    }

    private String tooltipFor(Item item) {
        StringBuilder sb = new StringBuilder(item.getDisplayName());
        if (item.isWeapon())           sb.append("  Dmg: ").append(item.getDamageDice());
        if (item.getArmorClassBonus() > 0) sb.append("  AC: +").append(item.getArmorClassBonus());
        return sb.toString();
    }

    // ── EventBus ─────────────────────────────────────────────────────

    @Override public void onStatsChanged()                                        { refresh(); }
    @Override public void onItemMoved(InventorySlot f, InventorySlot t, Item i)   { refresh(); }
    @Override public void onItemDropped(Item i)                                    { refresh(); }
}
