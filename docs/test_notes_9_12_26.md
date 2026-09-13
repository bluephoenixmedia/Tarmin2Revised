# Session Test Notes (9/12/26)

- [x] The Spells Quick Slots UX element needs to be 75% smaller and moved to the far left of the screen so as to not block other UX elements. (Resolved: Hud.java shrunk hotbar to 200x34 docked at x=16f, y=204f)
- [x] We need a better sequence for the death of the player and better transition to the shelter and next run. (Resolved: PlayerDeathScreen.java implemented with Doom clock progression, casualties accounting, and smooth awakening transition)
- [x] Upon death and return to the shelter, the maze is not being regenerated from a new seed. (Resolved: WorldManager.wipeExploredWorldOnDeath() now wipes all chunk files including chunk_1_0_0.json, re-seeding the entire world and starting shelter maze)
- [x] The starting sword is pointing at too low of an angle in the first person idle view, it needs to be more vertically aligned. (Resolved: FirstPersonWeaponOverlay.java idle rotation aligned from -22f to -5f)
- [x] The game crashes when opening the chest in the shelter. (Resolved: ShelterChest.java serialization migrated to ItemSaveData DTOs with asset rehydration; Item.java and ShelterChestScreen.java hardened against null type/display names)
- [x] We need a hotkey to turn off / on the player's lantern. (Resolved: LightingManager.java toggleLantern(), SettingsManager.java TOGGLE_LANTERN default [L], GameScreen.java)
- [x] Crash when clicking Bone Ossuary menu items with empty slots. (Resolved: OssuaryManager.java null check and bone validation; CraftingScreen.java Touchable.disabled on action buttons and click guard checks)

- The monster flesh items that can be picked up should be using an appropriate GIB texture, not the food texture. 
- Teleportation spells / scrolls should never teleport the player into an enlosed non navigable part of the maze. 
- When using a special book as a weapon, the attack animation should be spell based, not weapon strike. 
- When you have buffs due to any potion, item, spell, enhanced weapon etc, weshould show a color glow on the weapon while it's in idel first person mode
- Scrolls seem to be auto identified when I first see them but then when I use them they don't have the effect that is being displayed initially
- Drag and drop on the inventory screen seems broken when moving between quick slots and inventory slots
- We need to revisit MP and Health regeneration.
- When find and using tomes, are they actually doing anything in regards to spell slots?
- Inventory reported as full but it wasn't according to the inventory screen available slots.
- Right clicking on items in inventory are NOT dropping them
- We need a telemetry system that captures all pertinent data about individual runs that can be later analzyed to see how balance, item distribution, difficult and performance are working out.
- In subterranean forest levels, there is a thick white fog, is this on purpose?
- We need to overhaul and rebuild a comprehensive progression, achievement, unlock model which unlocks weapons, armor, items, spells, events, statues and anything else which could be gated by player progression or actions and which adds the unlocked items to future mazes, including full UX treatment for the notifications. They should be displayed in the death screen along with a summary of how the player died in the last run.
- Battles in later levels often are just composed of long drawn out matches whcih 80% of strikes missing and it is boring.
-When clicking the return to shelter button on the death screen, multiple deaths seem to be registered.