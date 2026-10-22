package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.bpm.minotaur.gamedata.history.NemesisHoard;

/** The active slot's {@link NemesisHoard}, kept in {@code nemesis_hoards.json} (#284). */
public final class NemesisHoardManager implements SlotScopedState {

    private static NemesisHoardManager instance;

    private NemesisHoard hoard;

    private NemesisHoardManager() {
        SaveManager.register(this);
    }

    public static NemesisHoardManager getInstance() {
        if (instance == null) {
            instance = new NemesisHoardManager();
        }
        return instance;
    }

    public NemesisHoard hoard() {
        if (hoard == null) load();
        return hoard;
    }

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("nemesis_hoards.json");
    }

    public void save() {
        if (hoard == null) return;
        try {
            Json json = new Json();
            json.setOutputType(OutputType.json);
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (!file.parent().exists()) {
                file.parent().mkdirs();
            }
            file.writeString(json.prettyPrint(hoard), false);
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("NemesisHoardManager", "Failed to save the Nemesis hoards", e);
            }
        }
    }

    private void load() {
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (file.exists()) {
                hoard = new Json().fromJson(NemesisHoard.class, file);
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("NemesisHoardManager", "Failed to load the Nemesis hoards", e);
            }
        }
        if (hoard == null) hoard = new NemesisHoard();
    }

    @Override
    public void reloadForActiveSlot() {
        hoard = null;
    }

    @Override
    public void resetForNewGame() {
        hoard = new NemesisHoard();
        save();
    }
}
