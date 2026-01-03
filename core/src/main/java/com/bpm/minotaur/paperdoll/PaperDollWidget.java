package com.bpm.minotaur.paperdoll;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.paperdoll.data.DollFragment;
import com.bpm.minotaur.paperdoll.data.FragmentResolver;
import com.bpm.minotaur.paperdoll.data.SkeletonData;

import com.bpm.minotaur.paperdoll.physics.VerletPhysics;

import java.util.Comparator;
import java.util.List;

/**
 * The core widget that acts as the View Controller for the Paper Doll system.
 * Extends Scene2D Group to manage transformation matrices.
 * Renders a composite of DollFragment objects.
 */
public class PaperDollWidget extends Group {

    private final SkeletonData skeletonData;
    private final FragmentResolver fragmentResolver;

    // The flat list of all fragments to render
    private final Array<DollFragment> renderQueue;
    private final Comparator<DollFragment> zComparator;

    // Physics
    private final VerletPhysics physics;
    private boolean hasPhysicsItem = false;
    private String physicsSocket = "hand_main";
    private AtlasRegion chainLinkRegion;

    // Debug Assets
    private com.badlogic.gdx.graphics.Texture debugPixel;
    private com.badlogic.gdx.graphics.g2d.BitmapFont debugFont;

    public PaperDollWidget(SkeletonData skeletonData, FragmentResolver fragmentResolver) {
        this.skeletonData = skeletonData;
        this.fragmentResolver = fragmentResolver;
        this.renderQueue = new Array<>();
        this.physics = new VerletPhysics();

        this.zComparator = new Comparator<DollFragment>() {
            @Override
            public int compare(DollFragment o1, DollFragment o2) {
                return Integer.compare(o1.zIndex, o2.zIndex);
            }
        };
    }

    public void setDebugAssets(com.badlogic.gdx.graphics.Texture pixel, com.badlogic.gdx.graphics.g2d.BitmapFont font) {
        this.debugPixel = pixel;
        this.debugFont = font;
    }

    /**
     * Equips an item by resolving it into fragments and adding them to the queue.
     */
    public void equip(Item item) {
        if (item == null)
            return;

        List<DollFragment> newFragments = fragmentResolver.resolve(item);
        for (DollFragment fragment : newFragments) {
            renderQueue.add(fragment);

            // Check for Flail/Physics
            if (fragment.region instanceof AtlasRegion && item.getType().name().contains("FLAIL")) {
                initPhysics((AtlasRegion) fragment.region, fragment.socketName);
                // We might NOT want to add the flail head as a static fragment if physics
                // handles it?
                // For now, let's assume the "Weapon" fragment is the handle, and physics adds
                // the chain + ball.
                // This requires more sophisticated asset management (Teacher said "Asset
                // Requirements").
                // We will assume 'chain_link' region exists if we find a flail.
            }
        }

        sortFragments();
    }

    private void initPhysics(AtlasRegion itemRegion, String socketName) {
        this.hasPhysicsItem = true;
        this.physicsSocket = socketName != null ? socketName : "hand_main";

        // Try to find chain link from the same atlas as the item
        // This is heuristic; ideally FragmentResolver provides this.
        // Assuming the itemRegion belongs to an atlas that has "chain_link"
        // We can't easily get the atlas reference from the region object in LibGDX API
        // directly
        // without casting to AtlasRegion (which we did), but getting the atlas is not
        // always exposed.
        // However, we can just hope 'fragmentResolver' can give us access or we pass
        // atlas to widget.
        // For now, let's just create the chain blindly and we will fail to render if
        // texture is missing.

        physics.createChain(8, 0, 0); // 8 links

        // We need the texture region for the chain.
        // Since we don't have easy access to the atlas here, we might need to store it.
        // Let's rely on the fragment's region for testing or duplicate it.
        this.chainLinkRegion = itemRegion; // Placeholder: dragging the whole flail texture? No.
        // Ideally we need 'chain_link'.
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        if (hasPhysicsItem) {
            // Update Pin Position
            Vector2 socketPos = skeletonData.getSocketPosition(physicsSocket);
            if (socketPos != null) {
                // Determine World Position of the Socket
                // This must account for Parent Transformations if we want true world space,
                // but the physics solver is running in "Widget Local Space" for simplicity.
                // If the Widget moves, the gravity vector stays relative to world?
                // Actually if Widget rotates, gravity vector should probably rotate relative to
                // it?
                // For now, simple translation.

                physics.updatePin(socketPos.x, socketPos.y);
            }

            physics.step(delta);
        }
    }

    private final java.util.Set<String> loggedFragments = new java.util.HashSet<>();

    public void clearEquipment() {
        renderQueue.clear();
        hasPhysicsItem = false;
        loggedFragments.clear();
    }

    // Add a base body fragment derived from a texture region
    public void setBody(DollFragment bodyFragment) {
        renderQueue.add(bodyFragment);
        sortFragments();
    }

    private void sortFragments() {
        renderQueue.sort(zComparator);
    }

    /**
     * The core rendering loop.
     */
    @Override
    protected void drawChildren(Batch batch, float parentAlpha) {
        // We do NOT call super.drawChildren() because we process our own queue.

        // Iterate through sorted fragments
        for (DollFragment fragment : renderQueue) {
            drawFragment(batch, fragment, parentAlpha);
        }

        // Draw Physics Chain (on top/below based on Z?)
        // Ideally checking Z-index, but for now draw on top.
        if (hasPhysicsItem && chainLinkRegion != null) {
            drawPhysicsChain(batch, parentAlpha);
        }

        // --- DEBUG GRID (Overlay) ---
        if (com.bpm.minotaur.managers.DebugManager.getInstance().isDebugOverlayVisible() && debugPixel != null
                && debugFont != null) {
            drawDebugGrid(batch);
        }
    }

    private void drawDebugGrid(Batch batch) {
        float step = 50;
        float logicWidth = getWidth() / getScaleX();
        float logicHeight = getHeight() / getScaleY();
        if (getScaleX() == 0)
            logicWidth = getWidth();
        if (getScaleY() == 0)
            logicHeight = getHeight();

        batch.setColor(com.badlogic.gdx.graphics.Color.CYAN);
        float alpha = 0.3f;
        batch.setColor(0, 1, 1, alpha);

        // Grid
        for (float x = 0; x <= logicWidth; x += step) {
            batch.draw(debugPixel, x, 0, 1, logicHeight);
        }
        for (float y = 0; y <= logicHeight; y += step) {
            batch.draw(debugPixel, 0, y, logicWidth, 1);
        }

        // Draw Sockets
        batch.setColor(com.badlogic.gdx.graphics.Color.RED);
        String[] debugSockets = { "head", "torso", "hand_main", "hand_off", "feet", "hips", "back", "backpack" };

        for (String socket : debugSockets) {
            Vector2 pos = skeletonData.getSocketPosition(socket);
            if (pos != null) {
                // Draw crosshair
                float sX = pos.x;
                float sY = pos.y;
                float size = 10;
                batch.draw(debugPixel, sX - size, sY, size * 2, 2);
                batch.draw(debugPixel, sX, sY - size, 2, size * 2);
                debugFont.draw(batch, socket, sX + 5, sY + 20);
            }
        }
    }

    private void drawPhysicsChain(Batch batch, float parentAlpha) {
        Array<com.bpm.minotaur.paperdoll.physics.VerletLink> links = physics.getLinks();
        if (links.size < 2)
            return;

        batch.setColor(1, 1, 1, parentAlpha);

        for (int i = 0; i < links.size - 1; i++) {
            com.bpm.minotaur.paperdoll.physics.VerletLink l1 = links.get(i);
            com.bpm.minotaur.paperdoll.physics.VerletLink l2 = links.get(i + 1);

            float x = getX() + l1.position.x;
            float y = getY() + l1.position.y;

            // Calculate angle
            float angle = (float) Math
                    .toDegrees(Math.atan2(l2.position.y - l1.position.y, l2.position.x - l1.position.x));
            angle -= 90; // Adjust for vertical sprite orientation

            // Draw chain link
            // Assuming origin is center-top or similar
            if (chainLinkRegion != null)
                batch.draw(chainLinkRegion, x, y,
                        chainLinkRegion.getRegionWidth() / 2f, chainLinkRegion.getRegionHeight(), // Origin
                        chainLinkRegion.getRegionWidth(), 15, // Width/Height (Length from physics?)
                        1, 1, angle);
        }
    }

    private void drawFragment(Batch batch, DollFragment fragment, float parentAlpha) {
        if (fragment.region == null)
            return;

        // --- SOCKET SYSTEM LOGIC ---
        float socketX = 0;
        float socketY = 0;

        if (fragment.socketName != null) {
            Vector2 socketPos = skeletonData.getSocketPosition(fragment.socketName);
            if (socketPos != null) {
                socketX = socketPos.x;
                socketY = socketPos.y;
            }
        }

        // 2. Handle Packing Offsets (Whitespace Stripping)
        float drawX = getX() + socketX + fragment.localOffset.x;
        float drawY = getY() + socketY + fragment.localOffset.y;

        // Correct for AtlasRegion packing if necessary
        if (fragment.region instanceof AtlasRegion) {
            AtlasRegion atlasRegion = (AtlasRegion) fragment.region;
            drawX += atlasRegion.offsetX;
            drawY += atlasRegion.offsetY;
        }

        // Apply Tint
        batch.setColor(fragment.tint.r, fragment.tint.g, fragment.tint.b, fragment.tint.a * parentAlpha);

        // Draw with Scale
        float width = fragment.region.getRegionWidth();
        float height = fragment.region.getRegionHeight();

        batch.draw(fragment.region, drawX, drawY, 0, 0, width, height, fragment.scaleX, fragment.scaleY, 0);

        // --- LOGGING ---
        // Identify fragment by region name if possible, or socket+offset
        String fragId = fragment.socketName + "_" + fragment.region;
        if (fragment.region instanceof AtlasRegion) {
            fragId = ((AtlasRegion) fragment.region).name + "_" + fragment.socketName;
        }

        if (!loggedFragments.contains(fragId)) {
            com.badlogic.gdx.Gdx.app.log("PaperDoll",
                    String.format(
                            "Render %s: Pos(%.2f, %.2f) Scale(%.2f, %.2f) Socket(%s: %.2f, %.2f) LocalOff(%.2f, %.2f)",
                            fragId, drawX, drawY, fragment.scaleX, fragment.scaleY,
                            fragment.socketName, socketX, socketY,
                            fragment.localOffset.x, fragment.localOffset.y));
            loggedFragments.add(fragId);
        }
    }
}
