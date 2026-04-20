package com.bpm.minotaur.paperdoll.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Manages the "Socket" definitions for the paper doll.
 * Loads socket coordinates from a JSON file.
 */
public class SkeletonData {

    private final ObjectMap<String, Vector2> sockets;
    private final ObjectMap<String, Integer> socketDepths; // Optional Z-ordering hint

    public SkeletonData() {
        this.sockets = new ObjectMap<>();
        this.socketDepths = new ObjectMap<>();
    }

    public void load(FileHandle file) {
        if (!file.exists()) {
            Gdx.app.error("SkeletonData", "Skeleton definition file not found: " + file.path());
            return;
        }

        JsonValue root = new JsonReader().parse(file);

        JsonValue socketsJson = root.get("sockets");
        if (socketsJson != null) {
            for (JsonValue socketEntry = socketsJson.child; socketEntry != null; socketEntry = socketEntry.next) {
                String name = socketEntry.name;

                // Handle nested structure: "x": { "value": 123 }
                float x = 0;
                float y = 0;
                int z = 0;

                if (socketEntry.has("x") && socketEntry.get("x").has("value")) {
                    x = socketEntry.get("x").getFloat("value");
                } else {
                    x = socketEntry.getFloat("x", 0); // Fallback for simple format
                }

                if (socketEntry.has("y") && socketEntry.get("y").has("value")) {
                    y = socketEntry.get("y").getFloat("value");
                } else {
                    y = socketEntry.getFloat("y", 0);
                }

                if (socketEntry.has("z") && socketEntry.get("z").has("value")) {
                    z = socketEntry.get("z").getInt("value");
                } else {
                    z = socketEntry.getInt("z", 0);
                }

                sockets.put(name, new Vector2(x, y));
                socketDepths.put(name, z);
            }
        }

        Gdx.app.log("SkeletonData", "Loaded " + sockets.size + " sockets from " + file.path());
    }

    public Vector2 getSocketPosition(String socketName) {
        return sockets.get(socketName, new Vector2(0, 0)); // Return 0,0 if not found, or maybe null?
    }

    public int getSocketDepth(String socketName) {
        return socketDepths.get(socketName, 0);
    }

    public void setSocketPosition(String name, float x, float y) {
        sockets.put(name, new Vector2(x, y));
    }
}
