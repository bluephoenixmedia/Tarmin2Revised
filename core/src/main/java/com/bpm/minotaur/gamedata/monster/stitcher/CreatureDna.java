package com.bpm.minotaur.gamedata.monster.stitcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-serialisable "DNA" descriptor for one creature type.
 *
 * Example JSON structure (assets/data/creatures/dna/troll_forest.json):
 * {
 *   "id": "troll_forest",
 *   "torsoModel": "models/creatures/torsos/torso_hulk.g3db",
 *   "attachmentPoints": [
 *     { "socket": "HEAD",  "bone": "socket_head",  "scale": 1.0, "optional": false },
 *     { "socket": "ARM_L", "bone": "socket_arm_l", "scale": 0.9, "optional": false }
 *   ],
 *   "partWeights": {
 *     "HEAD":  ["head_horned:70", "head_tusked:30"],
 *     "ARM_L": ["arm_claw:60",    "arm_tentacle:40"]
 *   },
 *   "biomeModifiers": {
 *     "cold":  { "ARM_L": ["arm_frost:80", "arm_claw:20"] },
 *     "wet":   { "ARM_L": ["arm_tentacle:90", "arm_claw:10"] }
 *   }
 * }
 */
public class CreatureDna {
    public String id = "";
    public String torsoModel = "";
    public List<AttachmentPoint> attachmentPoints = new ArrayList<>();

    /**
     * Base weight tables per socket.
     * Key = socket name, Value = list of "partId:weight" strings.
     */
    public Map<String, List<String>> partWeights = new HashMap<>();

    /**
     * Biome overrides.  Outer key = biome tag ("cold", "wet", "hot", "arid").
     * Inner map = socket → weight list (same format as partWeights).
     */
    public Map<String, Map<String, List<String>>> biomeModifiers = new HashMap<>();

    public CreatureDna() {}
}
