"""
bake_paperdoll_layers.py

Non-destructive normalising baker for the 2D paperdoll.

Supersedes bake_all_armor_paperdolls.py, which scaled each piece to a hardcoded
per-slot width and pasted it at a hardcoded offset -- constants that were eyeballed
once and applied to 106 different silhouettes. Placement now lives in
assets/data/paperdoll_calibration.json instead of being burnt into pixels.

What this does
--------------
1. NORMALISE   Every layer is cropped to its alpha bounding box, scaled (preserving
               aspect) to fit a fixed per-slot box, and centred on the 1024x1536
               master canvas. Because framing is normalised away, re-generating a
               piece with different padding lands it in the same place -- which is
               what makes a calibration a one-time cost rather than a recurring one.

2. SEED        Each layer's CURRENT baked placement is measured and written back as
               its seed calibration. Normalisation plus seed therefore reproduces
               today's composite; nothing shifts on the first run. Existing
               hand-tuning is preserved, just relocated into data.

3. MAP         Emits assets/data/paperdoll_layers.json mapping item keys to layers,
               replacing the three-way filename guessing in getCandidateNames() and
               the duplicate alias PNGs that guessing required.

4. HASH        Records a digest of each layer, so re-running is safe. A layer whose
               hash still matches is already normalised and is left completely alone.
               A layer whose hash changed -- because the art was regenerated -- is
               re-normalised while KEEPING its existing calibration, which is the
               whole point of normalising: new art with different framing lands where
               the old art was calibrated to sit.

               Without this the baker is destructive on its second run: it would
               measure the already-centred layers, conclude no offset is needed, and
               silently flatten every calibration to identity.

Run with --verify to confirm the round trip before writing anything.
"""

import argparse
import hashlib
import json
import os
import shutil
from PIL import Image

CANVAS_W = 1024
CANVAS_H = 1536
CANVAS_CX = CANVAS_W / 2.0
CANVAS_CY = CANVAS_H / 2.0

# Per-slot normalisation boxes. The seed calibration restores each layer's exact
# original size regardless of these, so the box only decides resampling quality and
# what "scale 1.0" means to the landmark auto-fitter later. Values are sized near the
# typical final footprint of the slot to keep both sensible.
SLOT_BOXES = {
    "head":   (340, 440),
    "chest":  (480, 620),
    "arms":   (800, 280),
    "hands":  (700, 200),
    "legs":   (470, 680),
    "feet":   (620, 360),
    "shield": (430, 470),
    "cloak":  (740, 900),
    "weapon": (400, 800),
}

SLOTS = list(SLOT_BOXES.keys())

# Tolerance for the round-trip check, in canvas pixels. Normalisation resamples twice
# (down to the box, back up via calibration), so exact equality is not achievable.
# The portrait renders 1024 canvas pixels into ~388 stage pixels, so this bound is
# about 1.1 rendered pixels -- below what anyone can see, and well inside what LANCZOS
# shifts the alpha centroid by on thin diagonal artwork.
VERIFY_TOL_PX = 3.0

# Alpha at or below this is treated as empty. LANCZOS rings around hard edges and
# leaves a fringe of alpha 1-2 well outside the visible artwork; left alone it would
# widen the measured bounding box on every re-bake, so each pass would drift the piece
# slightly larger than the last. Kept deliberately low: several pieces carry a genuine
# soft glow, and a high floor would eat it.
ALPHA_FLOOR = 2


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def clamp_alpha(img):
    """Zero out sub-threshold alpha so resampling fringe cannot accumulate."""
    alpha = img.getchannel("A").point(lambda a: 0 if a <= ALPHA_FLOOR else a)
    img.putalpha(alpha)
    return img


def solid_bbox(img):
    """Bounding box of visible content, ignoring resampling fringe."""
    alpha = img.getchannel("A").point(lambda a: 255 if a > ALPHA_FLOOR else 0)
    return alpha.getbbox()


def alpha_moments(img):
    """Alpha centroid and weighted spread, in canvas pixels.

    The round trip is verified on these rather than on a bounding box. A bbox is
    decided by the single faintest surviving pixel, so on art with a soft glow it
    shrinks under downscaling even when the piece is placed perfectly -- it measures
    the resampler, not the placement. Centroid and spread are mass-weighted, so they
    answer the question that actually matters: is the artwork in the same place, at
    the same size, as before.
    """
    import numpy as np

    a = np.asarray(img.getchannel("A"), dtype=np.float64)
    total = a.sum()
    if total <= 0:
        return None

    h, w = a.shape
    xs = np.arange(w, dtype=np.float64)
    ys = np.arange(h, dtype=np.float64)

    col_mass = a.sum(axis=0)
    row_mass = a.sum(axis=1)

    cx = float((col_mass * xs).sum() / total)
    cy = float((row_mass * ys).sum() / total)
    var_x = max(0.0, float((col_mass * xs * xs).sum() / total) - cx * cx)
    var_y = max(0.0, float((row_mass * ys * ys).sum() / total) - cy * cy)

    return cx, cy, var_x ** 0.5, var_y ** 0.5


def sha1_of(path):
    h = hashlib.sha1()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()[:16]


def normalise(img, slot):
    """Crop to alpha bbox, fit into the slot box preserving aspect, centre on canvas.

    Returns (normalised_image, content_w, content_h). The content dimensions feed the
    seed calibration, which needs them to work out the multiplier that restores the
    layer's original footprint.
    """
    bbox = solid_bbox(img)
    if not bbox:
        return None, 0, 0

    content = img.crop(bbox)
    cw, ch = content.size
    box_w, box_h = SLOT_BOXES[slot]

    scale = min(box_w / float(cw), box_h / float(ch))
    nw = max(1, int(round(cw * scale)))
    nh = max(1, int(round(ch * scale)))

    scaled = content.resize((nw, nh), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    canvas.paste(scaled,
                 (int(round(CANVAS_CX - nw / 2.0)), int(round(CANVAS_CY - nh / 2.0))),
                 scaled)
    return clamp_alpha(canvas), nw, nh


def seed_calibration(baked_img, nw, nh):
    """Work out the calibration that puts a normalised layer back where it is today.

    The renderer scales the whole canvas about its centre, and the normalised content
    sits at that centre, so restoring the original footprint is a pure ratio; restoring
    the original position is the offset from canvas centre to the old bbox centre.
    """
    bbox = solid_bbox(baked_img)
    if not bbox or nw == 0 or nh == 0:
        return None

    x0, y0, x1, y1 = bbox
    w0 = x1 - x0
    h0 = y1 - y0
    cx0 = (x0 + x1) / 2.0
    cy0 = (y0 + y1) / 2.0

    return {
        "offsetX": round(cx0 - CANVAS_CX, 2),
        "offsetY": round(cy0 - CANVAS_CY, 2),   # +y is DOWN, matching image space
        "scaleX": round(w0 / float(nw), 5),
        "scaleY": round(h0 / float(nh), 5),
        "rotation": 0,
    }


def apply_calibration(norm_img, cal):
    """Reproduce what the renderer will draw, for the round-trip check."""
    sx, sy = cal["scaleX"], cal["scaleY"]
    out_w = max(1, int(round(CANVAS_W * sx)))
    out_h = max(1, int(round(CANVAS_H * sy)))
    scaled = norm_img.resize((out_w, out_h), Image.Resampling.LANCZOS)

    canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    px = int(round(CANVAS_CX + cal["offsetX"] - out_w / 2.0))
    py = int(round(CANVAS_CY + cal["offsetY"] - out_h / 2.0))
    canvas.paste(scaled, (px, py), scaled)
    return canvas


def discover_layers(paperdoll_dir):
    """Group baked layers by content so alias copies collapse onto one canonical layer."""
    by_slot = {}
    for slot in SLOTS:
        slot_dir = os.path.join(paperdoll_dir, slot)
        if not os.path.isdir(slot_dir):
            continue
        digests = {}
        for fname in sorted(os.listdir(slot_dir)):
            if not fname.endswith(".png"):
                continue
            path = os.path.join(slot_dir, fname)
            digests.setdefault(sha1_of(path), []).append(fname)
        by_slot[slot] = digests
    return by_slot


def choose_canonical(names, preferred):
    """Prefer the name items actually reference; otherwise the shortest, then alphabetical."""
    for name in names:
        if os.path.splitext(name)[0] in preferred:
            return name
    return sorted(names, key=lambda n: (len(n), n))[0]


def slot_for_item(item):
    if item.get("isAmulet") or item.get("isRing"):
        return None
    if item.get("isHelmet"):
        return "head"
    if item.get("isShield"):
        return "shield"
    if item.get("isGauntlets"):
        return "hands"
    if item.get("isBoots"):
        return "feet"
    if item.get("isLegs"):
        return "legs"
    if item.get("isArms"):
        return "arms"
    if item.get("isCloak"):
        return "cloak"
    if item.get("isTorso"):
        return "chest"
    return None


def basename_no_ext(path):
    if not path:
        return None
    return os.path.splitext(os.path.basename(path))[0].lower()


def build_layer_map(repo, by_slot, canonical_by_digest):
    """item key -> {slot, layer}, replacing runtime name guessing with explicit data."""
    mapping = {}
    unresolved = []

    def resolve(slot, candidates):
        digests = by_slot.get(slot, {})
        for digest, names in digests.items():
            stems = set(os.path.splitext(n)[0].lower() for n in names)
            for cand in candidates:
                if cand in stems:
                    return canonical_by_digest[(slot, digest)]
        return None

    armor_path = os.path.join(repo, "assets", "data", "armor.json")
    with open(armor_path, "r", encoding="utf-8") as fh:
        armor = json.load(fh)

    for key, item in armor.items():
        slot = slot_for_item(item)
        if not slot:
            continue
        candidates = [c for c in (basename_no_ext(item.get("texturePath")), key.lower()) if c]
        layer = resolve(slot, candidates)
        if layer:
            mapping[key] = {"slot": slot, "layer": os.path.splitext(layer)[0]}
        else:
            unresolved.append((key, slot, candidates))

    weapons_path = os.path.join(repo, "assets", "data", "weapons.json")
    with open(weapons_path, "r", encoding="utf-8") as fh:
        weapons = json.load(fh)

    for key, item in weapons.items():
        candidates = [c for c in (basename_no_ext(item.get("texturePath")), key.lower()) if c]
        layer = resolve("weapon", candidates)
        if layer:
            mapping[key] = {"slot": "weapon", "layer": os.path.splitext(layer)[0]}
        else:
            unresolved.append((key, "weapon", candidates))

    return mapping, unresolved


def merge_layer_map(repo, fresh, unresolved):
    """Keep prior mappings that still point at a real layer.

    Name matching can only resolve an item while a file bearing one of its candidate
    names exists. Once the alias copies are removed, a handful of items stop resolving
    by name alone even though the mapping recorded for them is still perfectly valid --
    so a re-run would quietly orphan them. The previous map is authoritative for those.
    """
    path = os.path.join(repo, "assets", "data", "paperdoll_layers.json")
    if not os.path.exists(path):
        return fresh, unresolved

    with open(path, "r", encoding="utf-8") as fh:
        prior = json.load(fh).get("items", {})

    recovered = set()
    for key, ref in prior.items():
        if key in fresh:
            continue
        layer_png = os.path.join(repo, "assets", "images", "paperdoll",
                                 ref.get("slot", ""), ref.get("layer", "") + ".png")
        if os.path.exists(layer_png):
            fresh[key] = ref
            recovered.add(key)

    if recovered:
        unresolved = [u for u in unresolved if u[0] not in recovered]
    return fresh, unresolved


def write_calibration(path, calibration):
    lines = [
        "{",
        '  "_comment": "Paperdoll layer calibration. Offsets are master-canvas pixels '
        '(1024x1536); +y is DOWN. Safe to hand-edit; the in-game editor reloads on F5.",',
        '  "layers": {',
    ]
    keys = sorted(calibration)
    for i, k in enumerate(keys):
        c = calibration[k]
        line = ('    "%s": {"offsetX": %s, "offsetY": %s, "scaleX": %s, "scaleY": %s, '
                '"rotation": %s, "sourceHash": "%s"}'
                % (k, c["offsetX"], c["offsetY"], c["scaleX"], c["scaleY"],
                   c["rotation"], c["sourceHash"]))
        lines.append(line + ("," if i < len(keys) - 1 else ""))
    lines += ["  }", "}", ""]
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))


def load_existing_calibration(path):
    """Previous calibration, keyed by layer id. Absent file means a first run."""
    if not os.path.exists(path):
        return {}
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh).get("layers", {})


def write_triage(path, unresolved, calibration):
    """The art worklist: what has no layer, and what has been flagged for redrawing.

    Without this the same problems get rediscovered one equip at a time. A concrete
    list is what turns "many pieces need redoing" into a finite queue.
    """
    by_slot = {}
    for key, slot, tried in unresolved:
        by_slot.setdefault(slot, []).append((key, tried))

    redo = sorted(k for k, c in calibration.items() if c.get("needsArtRedo"))

    lines = [
        "# Paperdoll art triage",
        "",
        "Generated by `tools/bake_paperdoll_layers.py`. Do not hand-edit; re-run the baker.",
        "",
        "## Items with no paperdoll layer",
        "",
        "These equip fine but draw nothing on the doll. Ammunition and projectiles are",
        "expected here. Anything else is a missing piece of art.",
        "",
    ]
    if not by_slot:
        lines.append("_None._")
    for slot in sorted(by_slot):
        lines.append("### %s (%d)" % (slot, len(by_slot[slot])))
        lines.append("")
        for key, tried in sorted(by_slot[slot]):
            lines.append("- `%s` — looked for %s" % (key, ", ".join("`%s.png`" % t for t in tried)))
        lines.append("")

    lines += [
        "## Layers flagged for regeneration",
        "",
        "Set `needsArtRedo` on a layer in `assets/data/paperdoll_calibration.json` when",
        "no calibration makes it sit correctly — that means the art is at the wrong",
        "perspective and needs redrawing, not nudging.",
        "",
    ]
    lines.append("_None flagged yet._" if not redo else "")
    for layer_id in redo:
        lines.append("- `%s`" % layer_id)
    lines.append("")

    if not os.path.isdir(os.path.dirname(path)):
        os.makedirs(os.path.dirname(path))
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--verify", action="store_true",
                    help="check the normalise/calibrate round trip without writing anything")
    ap.add_argument("--apply", action="store_true",
                    help="write normalised layers, calibration seeds and the layer map")
    args = ap.parse_args()

    repo = repo_root()
    paperdoll_dir = os.path.join(repo, "assets", "images", "paperdoll")
    data_dir = os.path.join(repo, "assets", "data")

    by_slot = discover_layers(paperdoll_dir)

    preferred = set()
    for name in ("armor.json", "weapons.json"):
        with open(os.path.join(data_dir, name), "r", encoding="utf-8") as fh:
            for item in json.load(fh).values():
                b = basename_no_ext(item.get("texturePath"))
                if b:
                    preferred.add(b)

    canonical_by_digest = {}
    aliases_to_drop = []
    for slot, digests in by_slot.items():
        for digest, names in digests.items():
            canon = choose_canonical(names, preferred)
            canonical_by_digest[(slot, digest)] = canon
            for n in names:
                if n != canon:
                    aliases_to_drop.append(os.path.join(slot, n))

    # Existing calibration decides what this run is allowed to touch.
    existing = load_existing_calibration(os.path.join(data_dir, "paperdoll_calibration.json"))

    calibration = {}
    normalised = {}
    failures = []
    untouched = 0
    recalibrated = 0
    reframed = 0
    worst = 0.0

    for slot, digests in by_slot.items():
        for digest, names in digests.items():
            canon = canonical_by_digest[(slot, digest)]
            src = os.path.join(paperdoll_dir, slot, canon)
            layer_id = "%s/%s" % (slot, os.path.splitext(canon)[0])
            prior = existing.get(layer_id)

            # Already normalised and unchanged: nothing to do. Re-normalising would
            # resample the art again for no gain, and re-seeding from the centred
            # result would erase the placement.
            if prior is not None and prior.get("sourceHash") == digest:
                calibration[layer_id] = prior
                untouched += 1
                continue

            baked = Image.open(src).convert("RGBA")

            norm, nw, nh = normalise(baked, slot)
            if norm is None:
                failures.append((slot, canon, "empty alpha"))
                continue

            cal = seed_calibration(baked, nw, nh)
            if cal is None:
                failures.append((slot, canon, "no seed"))
                continue

            if prior is not None:
                # The art changed under an existing calibration. Normalisation is
                # exactly what makes the old numbers still apply to the new pixels,
                # so keep them and just record that we have seen the new art.
                cal = dict(prior)
                reframed += 1
            else:
                recalibrated += 1
            cal["sourceHash"] = digest

            calibration[layer_id] = cal
            normalised[layer_id] = (norm, src)

            # Round trip: does normalise + calibrate put the artwork back where it is
            # today, at the size it is today?
            rebuilt = apply_calibration(norm, cal)
            ob, rb = alpha_moments(baked), alpha_moments(rebuilt)
            if ob and rb:
                drift = max(abs(a - b) for a, b in zip(ob, rb))
                worst = max(worst, drift)
                if drift > VERIFY_TOL_PX:
                    failures.append((slot, canon, "drift %.2fpx" % drift))

    print("=== normalising baker ===")
    print("canonical layers : %d" % len(calibration))
    print("alias copies     : %d (removable once the layer map lands)" % len(aliases_to_drop))
    print("already normalised     : %d (left untouched)" % untouched)
    print("newly seeded           : %d" % recalibrated)
    print("re-normalised, calibration kept : %d" % reframed)
    print("worst round-trip drift : %.2f px (tolerance %.1f)" % (worst, VERIFY_TOL_PX))
    if failures:
        print("FAILURES (%d):" % len(failures))
        for f in failures[:25]:
            print("   %s/%s -> %s" % f)
    else:
        print("round trip clean: normalisation preserves every layer's current placement")

    mapping, unresolved = build_layer_map(repo, by_slot, canonical_by_digest)
    mapping, unresolved = merge_layer_map(repo, mapping, unresolved)
    print("item -> layer mappings : %d" % len(mapping))
    print("unmapped items         : %d (see the triage list)" % len(unresolved))

    if not args.apply:
        print("\nNothing written. Pass --apply to write.")
        return

    if failures:
        print("\nRefusing to write: the round trip failed. Fix the failures above first.")
        return

    backup = os.path.join(repo, "tools", "paperdoll_prebake_backup")
    if not os.path.exists(backup):
        shutil.copytree(paperdoll_dir, backup)
        print("\nbacked up pre-normalisation layers -> tools/paperdoll_prebake_backup")

    for layer_id, (norm, src) in normalised.items():
        norm.save(src)
        # Hash what we just wrote, not what we read. The stored digest has to describe
        # the file that will be on disk next run, otherwise every re-run sees a
        # mismatch and resamples the artwork again for nothing.
        calibration[layer_id]["sourceHash"] = sha1_of(src)
    print("wrote %d normalised layers" % len(normalised))

    for rel in aliases_to_drop:
        p = os.path.join(paperdoll_dir, rel)
        if os.path.exists(p):
            os.remove(p)
    print("removed %d alias copies" % len(aliases_to_drop))

    write_calibration(os.path.join(data_dir, "paperdoll_calibration.json"), calibration)
    print("wrote assets/data/paperdoll_calibration.json (%d entries)" % len(calibration))

    write_triage(os.path.join(repo, "docs", "paperdoll_triage.md"), unresolved, calibration)
    print("wrote docs/paperdoll_triage.md")

    map_path = os.path.join(data_dir, "paperdoll_layers.json")
    with open(map_path, "w", encoding="utf-8") as fh:
        json.dump({"_comment": "item key -> paperdoll layer. Replaces filename guessing; "
                               "several items may share a layer.",
                   "items": mapping}, fh, indent=2, sort_keys=True)
        fh.write("\n")
    print("wrote assets/data/paperdoll_layers.json (%d entries)" % len(mapping))


if __name__ == "__main__":
    main()
