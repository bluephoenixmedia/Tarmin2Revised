package com.bpm.minotaur.gamedata.monster.stitcher;

/** Defines a named socket on a torso where a limb ModelInstance attaches. */
public class AttachmentPoint {
    /** Logical socket name, e.g. "HEAD", "ARM_L", "LEG_R", "TAIL". */
    public String socket = "";
    /** Bone name in the torso .g3db that marks the attach origin. */
    public String bone = "";
    /** Uniform scale applied to the limb at this socket. */
    public float scale = 1.0f;
    /** If true the socket can be left empty without causing an error. */
    public boolean optional = false;

    public AttachmentPoint() {}
}
