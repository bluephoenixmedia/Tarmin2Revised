package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
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
/**
 * COORDINATE SYSTEM — read this before editing any position numbers.
 *
 * Stage size is fixed at 1920 × 1080 virtual pixels (FitViewport).
 * X : 0 = left screen edge, 1920 = right screen edge
 * Y : 0 = BOTTOM screen edge, 1080 = TOP screen edge ← LibGDX is Y-up!
 *
 * Left page occupies roughly X 0 – 960
 * Right page occupies roughly X 960 – 1920
 *
 * HOW TO MOVE AN EQUIPMENT SLOT
 * Each slot is placed by equip(label, type, centerX, bottomY).
 * • centerX – horizontal centre of the slot icon
 * • bottomY – bottom edge of the slot icon (= visualCentreY − SLOT_SZ/2)
 * Increase bottomY → slot moves UP. Decrease → moves DOWN.
 * Increase centerX → slot moves RIGHT. Decrease → moves LEFT.
 *
 * HOW TO MOVE THE PORTRAIT / RUNE CIRCLE / FRAME
 * Edit the PORTRAIT_* constants below.
 * PORTRAIT_X / PORTRAIT_Y are the left and bottom edges of the portrait image.
 * Everything else (rune circle, frame overlay, paper-doll widget) is derived
 * from those two values automatically.
 */
public class PaperDollPanel extends WidgetGroup implements InventoryEventBus.Listener {

    /** Size of each equipment slot icon in stage pixels. */
    private static final float SLOT_SZ = 64f;

    // The panel is sized to the full stage so that slot coordinates below
    // are identical to absolute stage coordinates — no local-to-stage transform
    // needed.
    private static final float PW = 1920f;
    private static final float PH = 1080f;

    // ── Portrait geometry ─────────────────────────────────────────────
    // All four values are in stage pixels. Change PORTRAIT_X / PORTRAIT_Y
    // to shift the portrait (and the frame / rune circle) together.
    private static final float PORTRAIT_W = 380f; // portrait image width
    private static final float PORTRAIT_H = 500f; // portrait image height
    private static final float PORTRAIT_X = 300f; // left edge of portrait
    private static final float PORTRAIT_Y = 426f; // bottom edge of portrait
    // Derived centre — used to position the rune circle and frame overlay.
    // private static final float PORTRAIT_CX = PORTRAIT_X + PORTRAIT_W / 2f; // 490
    // private static final float PORTRAIT_CY = PORTRAIT_Y + PORTRAIT_H / 2f; // 676

    private final Player player;
    private final InventorySkin skin;
    private final InventoryDragDropHandler dnd;
    private final ItemDataManager idm;

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

    // Frame overlay — stored so attachPaperDollWidget can insert before it
    // private Image frameOverlayImage;

    public PaperDollPanel(Player player, InventorySkin skin,
            InventoryDragDropHandler dnd,
            ItemDataManager idm, Texture dollTexture) {
        this.player = player;
        this.skin = skin;
        this.dnd = dnd;
        this.idm = idm;

        setSize(PW, PH);

        // Rune circle — behind everything, centred on portrait
        // float runeSize = 580f;
        // Image runeCircle = new Image(skin.getRuneCircleDrawable());
        // runeCircle.setSize(runeSize, runeSize);
        // runeCircle.setPosition(PORTRAIT_CX - runeSize / 2f, PORTRAIT_CY - runeSize /
        // 2f);
        // addActor(runeCircle);

        // Portrait base image
        // Image portrait = new Image(new TextureRegionDrawable(dollTexture));
        // portrait.setSize(PORTRAIT_W, PORTRAIT_H);
        // portrait.setPosition(PORTRAIT_X, PORTRAIT_Y);
        // addActor(portrait);

        // ── Equipment slot positions ──────────────────────────────────
        // Signature: equip(label, acceptedItemType, centerX, bottomY)
        // centerX — horizontal centre of the slot in stage pixels
        // bottomY — bottom edge of the slot in stage pixels
        // To find bottomY from a visual centre: visualCentreY − (SLOT_SZ / 2)
        // i.e. bottomY = centreY − 32
        //
        // Positions were derived by scanning new_inventory.png (2676×1568) then
        // scaling to stage space: stageX = imgX × (1920/2676)
        // stageY = (1568 − imgY) × (1080/1568)
        // Tweak any number here to nudge a slot; re-run to see the result.

        // ── Left column (armour, runs top to bottom on the left side) ─
        slotHead = equip("Head", null, 515f, 937f); // top-centre, above portrait
        slotNeck = equip("Neck", ItemType.AMULET, 222f, 846f); // far-left, upper row
        slotBack = equip("Back", ItemType.CLOAK, 425f, 795f); // centre-left, below neck
        slotChest = equip("Chest", ItemType.HAUBERK, 221f, 729f); // far-left, mid row
        slotArms = equip("Arms", ItemType.ARMS, 167f, 616f); // far-left, lower-mid row (left of pair)
        slotHands = equip("Hands", ItemType.GAUNTLETS, 280f, 616f); // lower-mid row (right of pair)
        slotLegs = equip("Legs", ItemType.LEGS, 221f, 502f); // far-left, lower row
        slotFeetL = equip("Feet", ItemType.BOOTS, 165f, 382f); // bottom row (left boot)
        slotFeetR = equip("Feet", ItemType.BOOTS, 280f, 382f); // bottom row (right boot)

        // ── Right column (weapons / rings, run top to bottom on the right side) ─
        slotWeaponMain = equip("R.Hand", ItemType.SWORD, 810f, 844f); // right side, upper row
        slotWeaponOff = equip("L.Hand", ItemType.SHIELD, 758f, 733f); // right side, mid row
        slotRingL = equip("Ring", ItemType.RING, 759f, 616f); // right side, lower-mid (left ring)
        slotRingR = equip("Ring 2", ItemType.RING, 870f, 615f); // right side, lower-mid (right ring)

        // Ornate frame overlay (on top of paper doll widget, behind slots)
        // float frameW = PORTRAIT_W + 18f;
        // float frameH = PORTRAIT_H + 18f;
        // frameOverlayImage = new Image(skin.getPortraitFrameDrawable());
        // frameOverlayImage.setSize(frameW, frameH);
        // frameOverlayImage.setPosition(PORTRAIT_X - 9f, PORTRAIT_Y - 9f);
        // addActor(frameOverlayImage);

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

    /**
     * Creates and places one equipment slot.
     *
     * @param name         Label shown inside the slot when empty.
     * @param restrictType Only items of this type may be dropped here; null = any
     *                     item.
     * @param x            Horizontal CENTRE of the slot in stage pixels.
     * @param y            BOTTOM EDGE of the slot in stage pixels.
     *                     Formula: y = visualCentreY − (SLOT_SZ / 2) i.e. centreY −
     *                     32
     */
    private InventorySlot equip(String name, ItemType restrictType, float x, float y) {
        InventorySlot slot = new InventorySlot(
                InventorySlot.SlotCategory.EQUIPMENT, -1, name, restrictType, skin, idm);
        slot.setSize(SLOT_SZ, SLOT_SZ);
        // setPosition takes the bottom-left corner, so shift x left by half the slot
        // width
        slot.setPosition(x - SLOT_SZ / 2f, y);
        dnd.register(slot);
        allEquip.add(slot);
        addActor(slot);
        return slot;
    }

    private void wireSlotInteractions(final InventorySlot slot) {
        slot.addListener(new ClickListener() {
            {
                setButton(-1);
            } // listen to all mouse buttons

            @Override
            public void enter(InputEvent e, float x, float y, int ptr, com.badlogic.gdx.scenes.scene2d.Actor from) {
                if (slot.getItem() != null)
                    tooltipLabel.setText(tooltipFor(slot.getItem()));
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
        // Constrain widget to portrait bounds so equipped-item renders don't bleed onto
        // the right page
        widget.setPosition(PORTRAIT_X - 20f, PORTRAIT_Y - 20f);
        widget.setSize(PORTRAIT_W + 40f, PORTRAIT_H + 40f);
        // Insert between portrait and frame overlay so the frame renders on top of the
        // doll
        // addActorBefore(frameOverlayImage, widget);
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

    @Override
    public float getPrefWidth() {
        return PW;
    }

    @Override
    public float getPrefHeight() {
        return PH;
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void smartUnequip(InventorySlot slot) {
        if (slot.isEmpty())
            return;
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
        if (paperDollWidget == null)
            return;
        paperDollWidget.clearEquipment();
        if (eq.getWornHelmet() != null)
            paperDollWidget.equip(eq.getWornHelmet());
        if (eq.getWornChest() != null)
            paperDollWidget.equip(eq.getWornChest());
        if (eq.getWornArms() != null)
            paperDollWidget.equip(eq.getWornArms());
        if (eq.getWornGauntlets() != null)
            paperDollWidget.equip(eq.getWornGauntlets());
        if (eq.getWornLegs() != null)
            paperDollWidget.equip(eq.getWornLegs());
        if (eq.getWornBoots() != null)
            paperDollWidget.equip(eq.getWornBoots());
        if (eq.getWornBack() != null)
            paperDollWidget.equip(eq.getWornBack());
    }

    private String tooltipFor(Item item) {
        StringBuilder sb = new StringBuilder(item.getDisplayName());
        if (item.isWeapon())
            sb.append("  Dmg: ").append(item.getDamageDice());
        if (item.getArmorClassBonus() > 0)
            sb.append("  AC: +").append(item.getArmorClassBonus());
        return sb.toString();
    }

    // ── EventBus ─────────────────────────────────────────────────────

    @Override
    public void onStatsChanged() {
        refresh();
    }

    @Override
    public void onItemMoved(InventorySlot f, InventorySlot t, Item i) {
        refresh();
    }

    @Override
    public void onItemDropped(Item i) {
        refresh();
    }
}
