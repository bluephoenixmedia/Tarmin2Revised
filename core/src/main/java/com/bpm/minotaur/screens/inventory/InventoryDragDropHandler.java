package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;
import com.bpm.minotaur.managers.SoundManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralises drag-and-drop for the entire modern inventory.
 *
 * A single {@link DragAndDrop} instance is shared so that payloads survive
 * crossing panel boundaries.  Every slot — backpack, quick-slot, equipment, or
 * alchemy — is registered here as both a source and a target.
 */
public class InventoryDragDropHandler {

    private final DragAndDrop    dnd;
    private final Player         player;
    private final Maze           maze;
    private final InventoryEventBus eventBus;
    private final InventorySkin  skin;
    private final ItemDataManager idm;
    private final List<InventorySlot> allSlots = new ArrayList<>();

    public InventoryDragDropHandler(Player player, Maze maze,
                                    InventoryEventBus eventBus,
                                    InventorySkin skin,
                                    ItemDataManager idm) {
        this.player   = player;
        this.maze     = maze;
        this.eventBus = eventBus;
        this.skin     = skin;
        this.idm      = idm;

        dnd = new DragAndDrop();
        dnd.setTapSquareSize(5);
        dnd.setDragTime(0);
    }

    // ── Slot registration ─────────────────────────────────────────────

    public void register(final InventorySlot slot) {
        allSlots.add(slot);

        // Right-click to drop; double-click (left button) to quick-equip/unequip.
        ClickListener actionListener = new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (event.getButton() == Input.Buttons.RIGHT) {
                    dropItem(slot);
                    return;
                }
                if (event.getButton() == Input.Buttons.LEFT && getTapCount() == 2) {
                    smartEquipToggle(slot);
                }
            }
        };
        actionListener.setButton(-1); // accept any button; branch on it above
        slot.addListener(actionListener);

        dnd.addSource(new Source(slot) {
            @Override
            public Payload dragStart(InputEvent event, float x, float y, int pointer) {
                if (slot.isEmpty()) return null;

                Payload p = new Payload();
                p.setObject(slot);

                // Ghost actor shown under the cursor while dragging
                InventorySlot ghost = new InventorySlot(
                        slot.category, slot.index, null, slot.restrictType, skin, idm);
                ghost.setItem(slot.getItem());
                ghost.setSize(56f, 56f);
                p.setDragActor(ghost);
                dnd.setDragActorPosition(28f, -28f);
                return p;
            }
        });

        dnd.addTarget(new Target(slot) {
            @Override
            public boolean drag(Source src, Payload p, float x, float y, int pointer) {
                InventorySlot origin = (InventorySlot) p.getObject();
                if (origin == slot) { slot.setHighlight(false, false); return false; }
                boolean valid = slot.accepts(origin.getItem());
                slot.setHighlight(true, valid);
                return valid;
            }

            @Override
            public void reset(Source src, Payload p) {
                slot.setHighlight(false, false);
            }

            @Override
            public void drop(Source src, Payload p, float x, float y, int pointer) {
                InventorySlot origin = (InventorySlot) p.getObject();
                moveItem(origin, slot);
            }
        });
    }

    // ── Public actions (also called by click listeners) ──────────────

    /**
     * Swaps items between two slots, updating both visuals and data model.
     *
     * The displaced item (if any) is written back at the SAME grid index/slot
     * it came from rather than appended elsewhere -- this preserves backpack
     * ordering across quick-slot <-> backpack and backpack <-> backpack drags
     * (previously a remove-then-append strategy would silently relocate the
     * displaced item to the end of the backpack list).
     */
    public void moveItem(InventorySlot source, InventorySlot target) {
        Item srcItem = source.getItem();
        if (srcItem == null) return;

        Item dstItem = target.getItem();
        // Ensure the displaced item can also go back into the source slot.
        if (dstItem != null && !source.accepts(dstItem)) return;

        setSlotModel(target, srcItem);
        setSlotModel(source, dstItem);

        source.setItem(dstItem);
        target.setItem(srcItem);

        if (SoundManager.getInstance() != null) {
            SoundManager.getInstance().playUiClick();
        }

        eventBus.fireItemMoved(source, target, srcItem);
        eventBus.fireStatsChanged();
    }

    /**
     * Double-click / smart-activate: moves an equipped item back to the first
     * empty backpack slot, or a backpack/quick-slot item into the first
     * equipment slot that accepts it.
     */
    public void smartEquipToggle(InventorySlot slot) {
        Item item = slot.getItem();
        if (item == null) return;

        if (slot.category == InventorySlot.SlotCategory.EQUIPMENT) {
            for (InventorySlot candidate : allSlots) {
                if (candidate.category == InventorySlot.SlotCategory.BACKPACK && candidate.isEmpty()) {
                    moveItem(slot, candidate);
                    return;
                }
            }
        } else {
            for (InventorySlot candidate : allSlots) {
                if (candidate.category == InventorySlot.SlotCategory.EQUIPMENT && candidate.accepts(item)) {
                    moveItem(slot, candidate);
                    return;
                }
            }
        }
    }

    /** Drops an item from a slot onto the maze floor. */
    public boolean dropItem(InventorySlot slot) {
        Item item = slot.getItem();
        if (item == null) return false;

        boolean ok = player.dropItem(maze, item);
        if (ok) {
            if (SoundManager.getInstance() != null) {
                SoundManager.getInstance().playUiClick();
            }
            setSlotModel(slot, null);
            slot.setItem(null);
            eventBus.fireItemDropped(item);
            eventBus.fireStatsChanged();
            if (player.getEventManager() != null) {
                player.getEventManager().addEvent(
                        new com.bpm.minotaur.gamedata.GameEvent("Dropped " + item.getDisplayName(), 2f));
            }
        }
        return ok;
    }

    // ── Data-model sync ───────────────────────────────────────────────

    /**
     * Writes {@code item} (possibly null, to clear) into the exact slot given,
     * without disturbing any other slot's position. For BACKPACK this is an
     * in-place {@code List.set}/{@code remove} at {@code slot.index} rather than
     * a value-based remove + end-of-list append, so swaps preserve grid order.
     */
    private void setSlotModel(InventorySlot slot, Item item) {
        switch (slot.category) {
            case BACKPACK: {
                List<Item> list = player.getInventory().getMainInventory();
                if (slot.index < list.size()) {
                    if (item == null) {
                        list.remove(slot.index);
                    } else {
                        list.set(slot.index, item);
                    }
                } else if (item != null) {
                    list.add(item);
                }
                break;
            }
            case QUICK_SLOT:
                player.getInventory().getQuickSlots()[slot.index] = item;
                break;
            case EQUIPMENT:
                if (item == null) {
                    unequipFromModel(slot.slotName);
                } else {
                    equipToModel(slot.slotName, item);
                }
                break;
            default:
                break;
        }
    }

    private void unequipFromModel(String name) {
        if (name == null) return;
        PlayerEquipment eq  = player.getEquipment();
        Inventory        inv = player.getInventory();
        switch (name) {
            case "Head":   eq.setWornHelmet(null);    break;
            case "Eyes":   eq.setWornEyes(null);      break;
            case "Neck":   eq.setWornNeck(null);      break;
            case "Chest":  eq.setWornChest(null);     break;
            case "Back":   eq.setWornBack(null);      break;
            case "Arms":   eq.setWornArms(null);      break;
            case "Hands":  eq.setWornGauntlets(null); break;
            case "Legs":   eq.setWornLegs(null);      break;
            case "Feet":   eq.setWornBoots(null);     break;
            case "Ring":   eq.setWornRing(null);      break;
            case "Ring 2": eq.setWornRing2(null);     break;
            case "L.Hand": inv.setLeftHand(null);  eq.setWornShield(null); break;
            case "R.Hand": inv.setRightHand(null); break;
        }
    }

    private void equipToModel(String name, Item item) {
        if (name == null || item == null) return;
        PlayerEquipment eq  = player.getEquipment();
        Inventory        inv = player.getInventory();
        switch (name) {
            case "Head":   eq.setWornHelmet(item);    break;
            case "Eyes":   eq.setWornEyes(item);      break;
            case "Neck":   eq.setWornNeck(item);      break;
            case "Chest":  eq.setWornChest(item);     break;
            case "Back":   eq.setWornBack(item);      break;
            case "Arms":   eq.setWornArms(item);      break;
            case "Hands":  eq.setWornGauntlets(item); break;
            case "Legs":   eq.setWornLegs(item);      break;
            case "Feet":   eq.setWornBoots(item);     break;
            case "Ring":   eq.setWornRing(item);      break;
            case "Ring 2": eq.setWornRing2(item);     break;
            case "L.Hand": {
                inv.setLeftHand(item);
                boolean shield = item.isShield();
                if (!shield) {
                    try {
                        var t = idm.getTemplate(item.getType());
                        if (t != null && t.isShield) shield = true;
                    } catch (Exception ignored) {}
                }
                eq.setWornShield(shield ? item : null);
                break;
            }
            case "R.Hand": inv.setRightHand(item); break;
        }
    }
}
