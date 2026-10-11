package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the living war campaign, faction army seedings, and sector actor clusters
 * for the 3D Main Menu Flyover.
 */
public class AttractWarDirector {

    public static class AttractHouse {
        private final int id;
        private final String name;
        private final String doctrineId;
        private final Color primaryColor;
        private final Color secondaryColor;
        private final String tabardPath;

        public AttractHouse(int id, String name, String doctrineId, Color primaryColor, Color secondaryColor) {
            this.id = id;
            this.name = name;
            this.doctrineId = doctrineId;
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
            this.tabardPath = "images/paperdoll/chest/tabard_" + doctrineId + ".png";
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getDoctrineId() { return doctrineId; }
        public Color getPrimaryColor() { return primaryColor; }
        public Color getSecondaryColor() { return secondaryColor; }
        public String getTabardPath() { return tabardPath; }
    }

    private final AttractHouse defendingHouse;
    private final AttractHouse invadingHouse;

    // 5 Spline Sectors
    private final List<List<AttractActor>> sectorActors = new ArrayList<>(5);
    private final List<AttractActor> allActors = new ArrayList<>();
    private int nextActorId = 1;

    public AttractWarDirector() {
        // Canonical rivalry: The Chainwrights (Fortress defenders) vs. The Ashen Penitents (Invading siege zealots)
        this.defendingHouse = new AttractHouse(
                1, "The Chainwrights", "chainwrights",
                new Color(0.29f, 0.31f, 0.34f, 1f), new Color(0.72f, 0.45f, 0.20f, 1f)
        );
        this.invadingHouse = new AttractHouse(
                2, "The Ashen Penitents", "ashen_penitents",
                new Color(0.55f, 0.15f, 0.15f, 1f), new Color(0.85f, 0.70f, 0.30f, 1f)
        );

        for (int i = 0; i < 5; i++) {
            sectorActors.add(new ArrayList<>());
        }

        seedActors();
    }

    private void seedActors() {
        // --- SECTOR 0: LAKELANDS (Dawn) ---
        // Knot 0: (275, 1.6, -165) to Knot 1: (245, 1.8, -220)
        addActor(0, AttractActor.ActorType.WILD_PREDATOR, 270f, 0.1f, -170f, 1.1f, 1.1f, "neutral", Color.WHITE, "GIANT_CENTIPEDE");
        addActor(0, AttractActor.ActorType.SOLDIER_MELEE, 260f, 0.2f, -185f, 0.9f, 0.9f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "HOBGOBLIN");
        addActor(0, AttractActor.ActorType.SOLDIER_ARCHER, 262f, 0.2f, -188f, 0.85f, 0.85f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "HOBGOBLIN");
        addActor(0, AttractActor.ActorType.DISCARDED_SHIELD, 258f, 0.05f, -182f, 0.4f, 0.4f, defendingHouse.getDoctrineId(), Color.LIGHT_GRAY, null);
        addActor(0, AttractActor.ActorType.LOOT_CHEST, 248f, 0.1f, -215f, 0.6f, 0.6f, "neutral", Color.GOLD, null);
        addActor(0, AttractActor.ActorType.WILD_PREDATOR, 240f, 0.1f, -225f, 1.0f, 1.0f, "neutral", Color.WHITE, "LIZARD");

        // --- SECTOR 1: ANCIENT FOREST (High Noon) ---
        // Knot 2: (220, 2.0, -260) to Knot 4: (125, 2.2, -260)
        // Defending Woodland War Camp
        addActor(1, AttractActor.ActorType.CAMPFIRE, 185f, 0.1f, -280f, 0.7f, 0.7f, "neutral", Color.WHITE, null);
        addActor(1, AttractActor.ActorType.SMOKE_PLUME, 185f, 0.8f, -280f, 1.8f, 6.0f, "neutral", new Color(0.8f, 0.8f, 0.8f, 0.6f), null);
        addActor(1, AttractActor.ActorType.TENT, 188f, 0.1f, -283f, 1.6f, 1.4f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), null);
        addActor(1, AttractActor.ActorType.TENT, 182f, 0.1f, -284f, 1.6f, 1.4f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), null);
        addActor(1, AttractActor.ActorType.SOLDIER_OFFICER, 184f, 0.1f, -278f, 1.0f, 1.0f, defendingHouse.getDoctrineId(), defendingHouse.getSecondaryColor(), "OGRE");
        // Marching Column
        addActor(1, AttractActor.ActorType.SOLDIER_MELEE, 175f, 0.1f, -286f, 0.9f, 0.9f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "IRON_GOLEM");
        addActor(1, AttractActor.ActorType.SOLDIER_SHIELD, 172f, 0.1f, -285f, 0.9f, 0.9f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "IRON_GOLEM");
        addActor(1, AttractActor.ActorType.SOLDIER_ARCHER, 169f, 0.1f, -284f, 0.85f, 0.85f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "HOBGOBLIN");
        addActor(1, AttractActor.ActorType.WILD_PREDATOR, 135f, 0.1f, -265f, 1.2f, 1.2f, "neutral", Color.WHITE, "BEAR");

        // --- SECTOR 2: DESERT CANYONS (Golden Hour / Dusk) ---
        // Knot 7: (115, 2.4, -100) to Knot 8: (155, 1.8, -85)
        // Advance Skirmish Vanguard
        addActor(2, AttractActor.ActorType.TORCH, 120f, 0.1f, -98f, 0.5f, 0.9f, "neutral", Color.WHITE, null);
        addActor(2, AttractActor.ActorType.SOLDIER_MELEE, 130f, 0.1f, -92f, 0.9f, 0.9f, invadingHouse.getDoctrineId(), invadingHouse.getPrimaryColor(), "GHOUL").setInCombat(true);
        addActor(2, AttractActor.ActorType.SOLDIER_MELEE, 132f, 0.1f, -91f, 0.9f, 0.9f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "IRON_GOLEM").setInCombat(true);
        addActor(2, AttractActor.ActorType.DISCARDED_SHIELD, 131f, 0.05f, -93f, 0.45f, 0.45f, invadingHouse.getDoctrineId(), Color.LIGHT_GRAY, null);
        addActor(2, AttractActor.ActorType.CAMPFIRE, 150f, 0.1f, -86f, 0.8f, 0.8f, "neutral", Color.WHITE, null);
        addActor(2, AttractActor.ActorType.SMOKE_PLUME, 150f, 0.8f, -86f, 1.6f, 5.5f, "neutral", new Color(0.7f, 0.65f, 0.6f, 0.5f), null);
        addActor(2, AttractActor.ActorType.SOLDIER_ARCHER, 153f, 0.1f, -84f, 0.85f, 0.85f, invadingHouse.getDoctrineId(), invadingHouse.getPrimaryColor(), "WRAITH");
        addActor(2, AttractActor.ActorType.LOOT_CHEST, 156f, 0.1f, -88f, 0.6f, 0.6f, "neutral", Color.GOLD, null);

        // --- SECTOR 3: MOUNTAIN GORGE PASS (Twilight) ---
        // Knot 5: (95, 3.8, -215) to Knot 6: (88, 4.2, -155)
        // Choke-Point Barricades & Heavy Defenders
        addActor(3, AttractActor.ActorType.BARRICADE, 92f, 0.1f, -195f, 1.8f, 1.0f, defendingHouse.getDoctrineId(), Color.BROWN, null);
        addActor(3, AttractActor.ActorType.SOLDIER_SHIELD, 90f, 0.1f, -192f, 1.0f, 1.0f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "IRON_GOLEM");
        addActor(3, AttractActor.ActorType.SOLDIER_ARCHER, 89f, 0.1f, -188f, 0.9f, 0.9f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "HOBGOBLIN");
        addActor(3, AttractActor.ActorType.TORCH, 91f, 0.1f, -185f, 0.5f, 0.9f, "neutral", Color.WHITE, null);
        // The Mountain Megabeast
        addActor(3, AttractActor.ActorType.MEGABEAST, 84f, 0.2f, -150f, 2.8f, 2.8f, "neutral", Color.WHITE, "MINOTAUR");
        addActor(3, AttractActor.ActorType.WILD_PREDATOR, 87f, 0.1f, -145f, 1.1f, 1.1f, "neutral", Color.WHITE, "GARGOYLE");

        // --- SECTOR 4: CASTLE TARMIN MIDNIGHT SIEGE ---
        // Knot 9: (180, 2.2, -120) to Knot 12: (200, 2.6, -195)
        // Invading Army Siege Lines along Southern Grand Avenue
        addActor(4, AttractActor.ActorType.CAMPFIRE, 175f, 0.1f, -115f, 0.9f, 0.9f, "neutral", Color.WHITE, null);
        addActor(4, AttractActor.ActorType.SMOKE_PLUME, 175f, 0.8f, -115f, 2.2f, 7.0f, "neutral", new Color(0.6f, 0.6f, 0.65f, 0.7f), null);
        addActor(4, AttractActor.ActorType.BARRICADE, 178f, 0.1f, -122f, 2.0f, 1.1f, invadingHouse.getDoctrineId(), Color.DARK_GRAY, null);
        addActor(4, AttractActor.ActorType.SOLDIER_ARCHER, 176f, 0.1f, -125f, 0.9f, 0.9f, invadingHouse.getDoctrineId(), invadingHouse.getPrimaryColor(), "GHAST");
        addActor(4, AttractActor.ActorType.SOLDIER_ARCHER, 184f, 0.1f, -125f, 0.9f, 0.9f, invadingHouse.getDoctrineId(), invadingHouse.getPrimaryColor(), "GHAST");
        addActor(4, AttractActor.ActorType.SOLDIER_OFFICER, 180f, 0.1f, -128f, 1.1f, 1.1f, invadingHouse.getDoctrineId(), invadingHouse.getSecondaryColor(), "MIND_FLAYER");
        addActor(4, AttractActor.ActorType.SOLDIER_MELEE, 179f, 0.1f, -138f, 0.95f, 0.95f, invadingHouse.getDoctrineId(), invadingHouse.getPrimaryColor(), "GHOUL").setInCombat(true);
        addActor(4, AttractActor.ActorType.SOLDIER_MELEE, 181f, 0.1f, -139f, 0.95f, 0.95f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "IRON_GOLEM").setInCombat(true);
        addActor(4, AttractActor.ActorType.DISCARDED_SHIELD, 177f, 0.05f, -140f, 0.45f, 0.45f, defendingHouse.getDoctrineId(), Color.LIGHT_GRAY, null);

        // Moat & Rampart Castle Defenders
        addActor(4, AttractActor.ActorType.TORCH, 177f, 2.2f, -148f, 0.5f, 0.9f, "neutral", Color.WHITE, null);
        addActor(4, AttractActor.ActorType.TORCH, 183f, 2.2f, -148f, 0.5f, 0.9f, "neutral", Color.WHITE, null);
        addActor(4, AttractActor.ActorType.SOLDIER_ARCHER, 175f, 2.4f, -150f, 0.9f, 0.9f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "HOBGOBLIN");
        addActor(4, AttractActor.ActorType.SOLDIER_ARCHER, 185f, 2.4f, -150f, 0.9f, 0.9f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "HOBGOBLIN");
        addActor(4, AttractActor.ActorType.SOLDIER_SHIELD, 180f, 0.1f, -153f, 1.0f, 1.0f, defendingHouse.getDoctrineId(), defendingHouse.getPrimaryColor(), "IRON_GOLEM");
        addActor(4, AttractActor.ActorType.BLIGHTED_HORROR, 168f, 0.1f, -158f, 1.2f, 1.2f, "neutral", Color.WHITE, "SPECTER");
        addActor(4, AttractActor.ActorType.LOOT_CHEST, 180f, 0.1f, -178f, 0.7f, 0.7f, "neutral", Color.GOLD, null);
    }

    private AttractActor addActor(int sector, AttractActor.ActorType type, float x, float y, float z,
                                  float w, float h, String houseId, Color tint, String spriteId) {
        AttractActor actor = new AttractActor(nextActorId++, type, x, y, z);
        actor.setScale(w, h);
        actor.setHouseId(houseId);
        actor.setTint(tint);
        actor.setSpriteId(spriteId);

        sectorActors.get(sector).add(actor);
        allActors.add(actor);
        return actor;
    }

    public List<AttractActor> getActorsInSector(int sector) {
        if (sector < 0 || sector >= sectorActors.size()) return Collections.emptyList();
        return sectorActors.get(sector);
    }

    public List<AttractActor> getActorsNear(Vector3 center, float radius) {
        List<AttractActor> result = new ArrayList<>();
        float r2 = radius * radius;
        for (AttractActor a : allActors) {
            if (center.dst2(a.getPosition()) <= r2) {
                result.add(a);
            }
        }
        return result;
    }

    public void update(float delta, float flightTime) {
        for (AttractActor a : allActors) {
            a.update(delta);
        }
    }

    public AttractHouse getDefendingHouse() {
        return defendingHouse;
    }

    public AttractHouse getInvadingHouse() {
        return invadingHouse;
    }

    public List<AttractActor> getAllActors() {
        return allActors;
    }
}
