package com.bpm.minotaur.paperdoll.calibration;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Resolves editable data files so that reading and writing hit the SAME file.
 *
 * lwjgl3/build.gradle puts assets/ on the resource path, so a copy of every data file
 * also lives in lwjgl3/build/resources/main/, and Gdx.files.internal finds that copy
 * first via the classpath. An editor that saves to the real asset but reloads through
 * internal therefore saves correctly and then silently reverts the whole session on the
 * next read -- and never sees a hand-edit of the tracked file at all. Both calibration
 * editors go through here for exactly that reason.
 *
 * A packaged build has no local assets/ directory, so reads fall through to internal and
 * behave as they always did, and writes report that there is nowhere to write.
 */
public final class AssetDataFiles {

    private AssetDataFiles() {
    }

    /** The real on-disk asset when there is one, otherwise whatever internal resolves. */
    public static FileHandle readable(String internalPath) {
        FileHandle onDisk = Gdx.files.local("assets/" + internalPath);
        if (onDisk.exists()) {
            return onDisk;
        }
        FileHandle internal = Gdx.files.internal(internalPath);
        if (internal.exists()) {
            return internal;
        }
        FileHandle direct = Gdx.files.local(internalPath);
        return direct.exists() ? direct : null;
    }

    /** Where an editor's save should land, or null when there is no writable asset tree. */
    public static FileHandle writable(String internalPath) {
        FileHandle underAssets = Gdx.files.local("assets/" + internalPath);
        if (underAssets.exists() || underAssets.parent().exists()) {
            return underAssets;
        }
        FileHandle direct = Gdx.files.local(internalPath);
        if (direct.exists() || direct.parent().exists()) {
            return direct;
        }
        return null;
    }
}
