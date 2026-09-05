package com.bpm.minotaur.gamedata.monster.stitcher;

/** Entry from parts_manifest.json describing one loadable limb asset. */
public class CreaturePartDef {
    /** Unique part id referenced in DNA weight tables, e.g. "arm_claw_heavy". */
    public String id = "";
    /** Path to the .g3db asset, relative to assets root. */
    public String model = "";
    /** Which socket type this part fits, e.g. "ARM_L", "HEAD". */
    public String socket = "";
    /** Uniform scale applied when instantiating this model.  Use 0.01 for
     *  FBX-sourced assets exported from Blender (cm → m conversion). */
    public float unitScale = 1.0f;

    public CreaturePartDef() {}
}
