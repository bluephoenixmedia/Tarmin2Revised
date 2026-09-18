"""
autofit_paperdoll.py

Proposes a calibration for every paperdoll layer from the body landmarks, and scores
those proposals against the calibrations already in the repo.

The scoring is the point. Phase 4 of the plan is "calibrate one slot and judge the tool
before committing to the rest", and the honest way to judge it is to check the
auto-fitter against 335 placements that are already known good -- the hand-tuned
positions the old baker produced, preserved as seeds. If the auto-fit lands most layers
close to those, it is a usable starting point and hand-calibration becomes touch-ups.
If it does not, that is worth knowing before anyone sits down to 106 of them.

Run:
    python tools/autofit_paperdoll.py            # score against existing calibration
    python tools/autofit_paperdoll.py --apply    # write proposals for UNCALIBRATED layers
"""

import argparse
import json
import os

import numpy as np
from PIL import Image

import bake_paperdoll_layers as baker

CANVAS_W = baker.CANVAS_W
CANVAS_H = baker.CANVAS_H


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def load_json(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


# Which landmark each slot hangs off. The offset from that anchor, and the slot's
# typical footprint, are LEARNED from the calibrations already in the repo rather than
# guessed -- see learn_rules(). Hand-guessed paddings were systematically wrong by
# 80-170px per slot, because "how far a breastplate sits proud of the torso" is not
# something to estimate when 335 worked examples are sitting right there.
# Paired slots are baked as "<layer>.left" / "<layer>.right" and each limb is anchored
# to its OWN landmark. This is the whole point of splitting them: the combined artwork
# had the boots 337px apart where the feet are 502px apart, and one rigid transform
# could match spacing or size but never both.
LIMB_ANCHORS = {
    ("feet", "left"):   lambda p: (p["foot_left"][0], p["foot_left"][1]),
    ("feet", "right"):  lambda p: (p["foot_right"][0], p["foot_right"][1]),
    ("hands", "left"):  lambda p: (p["hand_left"][0], p["hand_left"][1]),
    ("hands", "right"): lambda p: (p["hand_right"][0], p["hand_right"][1]),
    ("arms", "left"):   lambda p: (p["shoulder_left"][0], p["shoulder_left"][1]),
    ("arms", "right"):  lambda p: (p["shoulder_right"][0], p["shoulder_right"][1]),
}


def limb_of(layer_name):
    """'left' / 'right' for a split half, else None."""
    if layer_name.endswith(".left"):
        return "left"
    if layer_name.endswith(".right"):
        return "right"
    return None


SLOT_ANCHORS = {
    "head":   lambda p: ((p["head_left"][0] + p["head_right"][0]) / 2.0, p["head_top"][1]),
    "chest":  lambda p: ((p["shoulder_left"][0] + p["shoulder_right"][0]) / 2.0, p["shoulder_left"][1]),
    "arms":   lambda p: ((p["shoulder_left"][0] + p["shoulder_right"][0]) / 2.0, p["shoulder_left"][1]),
    "hands":  lambda p: ((p["hand_left"][0] + p["hand_right"][0]) / 2.0, p["hand_left"][1]),
    "legs":   lambda p: (p["crotch"][0], p["crotch"][1]),
    "feet":   lambda p: ((p["foot_left"][0] + p["foot_right"][0]) / 2.0, p["foot_left"][1]),
    "shield": lambda p: (p["hand_right"][0], p["hand_right"][1]),
    "cloak":  lambda p: ((p["shoulder_left"][0] + p["shoulder_right"][0]) / 2.0, p["shoulder_left"][1]),
    "weapon": lambda p: (p["hand_left"][0], p["hand_left"][1]),
}

# Reference spans the learned footprints scale against, so a redrawn body of different
# proportions still produces sensible sizes rather than pixel constants frozen forever.
SLOT_SPANS = {
    "head":   lambda p: p["head_right"][0] - p["head_left"][0],
    "chest":  lambda p: p["shoulder_right"][0] - p["shoulder_left"][0],
    "arms":   lambda p: p["hand_right"][0] - p["hand_left"][0],
    "hands":  lambda p: p["hand_right"][0] - p["hand_left"][0],
    "legs":   lambda p: p["hip_right"][0] - p["hip_left"][0],
    "feet":   lambda p: p["foot_right"][0] - p["foot_left"][0],
    "shield": lambda p: p["shoulder_right"][0] - p["shoulder_left"][0],
    "cloak":  lambda p: p["shoulder_right"][0] - p["shoulder_left"][0],
    "weapon": lambda p: p["hand_right"][0] - p["hand_left"][0],
}


def legacy_target_box(slot, p):
    """Hand-written rules, kept only as the fallback for a slot with no worked examples.

    Returns (centre_x, centre_y, width, height) -- the footprint the artwork should
    occupy.
    """
    head_w = p["head_right"][0] - p["head_left"][0]
    head_cx = (p["head_right"][0] + p["head_left"][0]) / 2.0
    sh_l, sh_r = p["shoulder_left"][0], p["shoulder_right"][0]
    sh_span = sh_r - sh_l
    sh_cx = (sh_l + sh_r) / 2.0
    sh_y = p["shoulder_left"][1]
    waist_y = p["waist_left"][1]
    hand_l, hand_r = p["hand_left"][0], p["hand_right"][0]
    hand_y = p["hand_left"][1]
    crotch_y = p["crotch"][1]
    hip_span = p["hip_right"][0] - p["hip_left"][0]
    ankle_y = p["ankle_left"][1]
    foot_l, foot_r = p["foot_left"][0], p["foot_right"][0]
    foot_y = p["foot_left"][1]
    top_y = p["head_top"][1]

    if slot == "head":
        w = head_w * 1.25
        h = (p["chin"][1] - top_y) * 1.55
        return head_cx, top_y + h / 2.0 - 25, w, h

    if slot == "chest":
        w = sh_span * 1.14
        top = sh_y - 25
        bottom = waist_y + 110
        return sh_cx, (top + bottom) / 2.0, w, bottom - top

    if slot == "arms":
        # Both arms in one image, reaching from shoulder to about the wrists.
        w = (hand_r - hand_l) * 0.98
        top = sh_y + 10
        bottom = hand_y - 40
        return (hand_l + hand_r) / 2.0, (top + bottom) / 2.0, w, bottom - top

    if slot == "hands":
        w = (hand_r - hand_l) * 1.10
        h = w * 0.28
        return (hand_l + hand_r) / 2.0, hand_y + 25, w, h

    if slot == "legs":
        w = hip_span * 1.55
        top = crotch_y - 95
        bottom = ankle_y + 10
        return (p["hip_left"][0] + p["hip_right"][0]) / 2.0, (top + bottom) / 2.0, w, bottom - top

    if slot == "feet":
        w = (foot_r - foot_l) * 1.28
        top = ankle_y - 35
        bottom = foot_y + 10
        return (foot_l + foot_r) / 2.0, (top + bottom) / 2.0, w, bottom - top

    if slot == "shield":
        # Off hand, held beside the body.
        return hand_r - 45, hand_y + 85, 420.0, 455.0

    if slot == "cloak":
        w = sh_span * 1.75
        top = sh_y - 15
        bottom = crotch_y + 220
        return sh_cx, (top + bottom) / 2.0, w, bottom - top

    if slot == "weapon":
        # Main hand. Weapons are baked grip-down, so the artwork hangs above the hand.
        return hand_l + 18, hand_y - 105, 390.0, 775.0

    return CANVAS_W / 2.0, CANVAS_H / 2.0, 400.0, 400.0


# Where along a weapon's artwork the hand grips it, as a fraction from the top.
#
# Measured, not assumed. Across the 229 existing weapon placements the BOTTOM edge of
# the artwork clusters tightly (p10-p90 spread of 82px) while the centres spread 171px,
# so weapons are consistently hung from their bottom edge. Per-category fractions
# borrowed from tools/bake_weapon_paperdolls.py (bows at the middle, polearms near the
# butt) were tried against the same data and scored slightly worse, so they are unused.
#
# Per-item overrides belong here if a weapon ever needs one -- Q17's escape hatch.
GRIP_FRACTIONS = ()
DEFAULT_GRIP_FRACTION = 1.0


def grip_fraction(layer_name):
    n = layer_name.lower()
    for key, frac in GRIP_FRACTIONS:
        if key in n:
            return frac
    return DEFAULT_GRIP_FRACTION


def learn_rules(existing, pdir, p):
    """Derive each slot's target box from the calibrations already in the repo.

    For every calibrated layer, work out where its artwork actually renders and how big,
    express that relative to the slot's anchor landmark and reference span, and take the
    median. The median is deliberate: a handful of layers are badly placed even in the
    existing data, and a mean would let them drag the rule.

    This is what makes the auto-fit worth having. The rules are then anchored to anatomy,
    so they still mean something if the body art is redrawn, but their constants come
    from 335 worked examples rather than from estimating how far armour sits proud of
    the body it covers.
    """
    samples = {}
    for layer_id, cal in existing.items():
        slot, name = layer_id.split("/", 1)
        if slot not in SLOT_ANCHORS:
            continue
        limb = limb_of(name)
        key = (slot, limb) if limb else slot
        anchor_fn = LIMB_ANCHORS.get((slot, limb)) if limb else SLOT_ANCHORS.get(slot)
        if anchor_fn is None:
            continue
        path = os.path.join(pdir, slot, name + ".png")
        if not os.path.exists(path):
            continue
        cw, ch = content_size(Image.open(path).convert("RGBA"))
        if cw == 0 or ch == 0:
            continue

        ax, ay = anchor_fn(p)
        span = SLOT_SPANS[slot](p) or 1.0
        cx = CANVAS_W / 2.0 + cal["offsetX"]
        cy = CANVAS_H / 2.0 + cal["offsetY"]
        # Weapons are anchored by the GRIP, not the artwork centre. A dagger and a
        # longbow share a grip position but nothing else, so measuring from their
        # centres mixes "where it is held" with "how long it is".
        if slot == "weapon":
            f = grip_fraction(name)
            gy = cy + (f - 0.5) * ch * cal["scaleY"]
        else:
            gy = cy
        samples.setdefault(key, []).append((
            (cx - ax) / span,
            (gy - ay) / span,
            (cw * cal["scaleX"]) / span,
            (ch * cal["scaleY"]) / span,
            cal["scaleX"],                 # the multiplier itself
        ))

    rules = {}
    for slot, vals in samples.items():
        a = np.array(vals)
        rules[slot] = {
            "dx": float(np.median(a[:, 0])),
            "dy": float(np.median(a[:, 1])),
            "w": float(np.median(a[:, 2])),
            "h": float(np.median(a[:, 3])),
            "scale": float(np.median(a[:, 4])),
            "n": len(vals),
        }
    return rules


def target_box(slot, p, rules, limb=None):
    key = (slot, limb) if limb else slot
    r = rules.get(key)
    if r is None:
        return legacy_target_box(slot, p)
    anchor_fn = LIMB_ANCHORS.get((slot, limb)) if limb else SLOT_ANCHORS.get(slot)
    if anchor_fn is None:
        return legacy_target_box(slot, p)
    ax, ay = anchor_fn(p)
    span = SLOT_SPANS[slot](p) or 1.0
    return ax + r["dx"] * span, ay + r["dy"] * span, r["w"] * span, r["h"] * span


def propose(slot, content_w, content_h, p, rules, layer_name=""):
    """Calibration that puts a normalised layer where its slot says it belongs."""
    if content_w <= 0 or content_h <= 0:
        return None

    r = rules.get(slot)
    if slot == "weapon" and r is not None:
        # Normalisation already gave every weapon a sensible size for its own
        # proportions, so keep that and place the grip rather than forcing each piece
        # into one median footprint -- which shrank longbows and polearms to a fifth of
        # their size. The grip is the bottom of the artwork (weapons are baked
        # grip-down), and the calibration positions the artwork's centre, so the centre
        # sits half the rendered height above the grip.
        ax, ay = SLOT_ANCHORS[slot](p)
        span = SLOT_SPANS[slot](p) or 1.0
        scale = r["scale"]
        f = grip_fraction(layer_name)
        grip_x = ax + r["dx"] * span
        grip_y = ay + r["dy"] * span
        return {
            "offsetX": round(grip_x - CANVAS_W / 2.0, 2),
            "offsetY": round(grip_y - (f - 0.5) * content_h * scale - CANVAS_H / 2.0, 2),
            "scaleX": round(scale, 5),
            "scaleY": round(scale, 5),
            "rotation": 0,
        }

    cx, cy, tw, th = target_box(slot, p, rules, limb_of(layer_name))
    # Preserve the artwork's aspect: fit it inside the target box rather than squashing
    # it to fill. Squashing to the box would distort every piece whose proportions do
    # not happen to match the slot's average.
    scale = min(tw / float(content_w), th / float(content_h))
    # scaleX/scaleY multiply the whole canvas, and the artwork scales with it, so the
    # rendered footprint is content_size * scale. Equal X and Y keep the aspect.
    return {
        "offsetX": round(cx - CANVAS_W / 2.0, 2),
        "offsetY": round(cy - CANVAS_H / 2.0, 2),
        "scaleX": round(scale, 5),
        "scaleY": round(scale, 5),
        "rotation": 0,
    }


def content_size(img):
    bbox = baker.solid_bbox(img)
    if not bbox:
        return 0, 0
    return bbox[2] - bbox[0], bbox[3] - bbox[1]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true",
                    help="write proposals for layers that have no calibration yet")
    ap.add_argument("--centre-limbs", action="store_true",
                    help="align each split limb horizontally onto its own landmark")
    args = ap.parse_args()

    repo = repo_root()
    data = os.path.join(repo, "assets", "data")
    pdir = os.path.join(repo, "assets", "images", "paperdoll")

    landmarks = load_json(os.path.join(data, "paperdoll_landmarks.json"))["points"]
    cal_path = os.path.join(data, "paperdoll_calibration.json")
    existing = load_json(cal_path)["layers"]

    rules = learn_rules(existing, pdir, landmarks)
    print("rules learned from existing calibrations (offsets/sizes in anchor spans):")
    for key in sorted(rules, key=lambda k: str(k)):
        r = rules[key]
        label = "%s.%s" % key if isinstance(key, tuple) else key
        print("  %-14s n=%-4d dx=%+.2f dy=%+.2f  w=%.2f h=%.2f"
              % (label, r["n"], r["dx"], r["dy"], r["w"], r["h"]))
    print()

    # Score: for every already-calibrated layer, how far is the proposal from the
    # placement we know is right? Measured as the distance between the rendered
    # artwork's centre and the ratio of its rendered size, both in canvas pixels.
    per_slot = {}
    rows = []

    for layer_id, known in sorted(existing.items()):
        slot, name = layer_id.split("/", 1)
        path = os.path.join(pdir, slot, name + ".png")
        if not os.path.exists(path):
            continue
        img = Image.open(path).convert("RGBA")
        cw, ch = content_size(img)
        if cw == 0:
            continue

        guess = propose(slot, cw, ch, landmarks, rules, name)
        if guess is None:
            continue

        # Where each places the artwork's centre, and how big it renders.
        def placed(cal):
            return (CANVAS_W / 2.0 + cal["offsetX"],
                    CANVAS_H / 2.0 + cal["offsetY"],
                    cw * cal["scaleX"],
                    ch * cal["scaleY"])

        kx, ky, kw, kh = placed(known)
        gx, gy, gw, gh = placed(guess)

        dist = ((kx - gx) ** 2 + (ky - gy) ** 2) ** 0.5
        size_ratio = (gw * gh) / float(kw * kh) if kw * kh > 0 else 0.0
        rows.append((layer_id, dist, size_ratio))
        per_slot.setdefault(slot, []).append((dist, size_ratio))

    print("=== auto-fit scored against %d known-good calibrations ===" % len(rows))
    print()
    print("%-8s %5s  %9s %9s   %s" % ("slot", "n", "median", "p90", "within 40px"))
    for slot in sorted(per_slot):
        d = np.array([r[0] for r in per_slot[slot]])
        close = 100.0 * (d <= 40).mean()
        print("%-8s %5d  %7.0fpx %7.0fpx   %5.0f%%"
              % (slot, len(d), np.median(d), np.percentile(d, 90), close))

    alld = np.array([r[1] for r in rows])
    allr = np.array([r[2] for r in rows])
    print()
    print("overall median offset : %.0f px  (%.1f%% of canvas height)"
          % (np.median(alld), 100 * np.median(alld) / CANVAS_H))
    print("overall within 40 px  : %.0f%%" % (100.0 * (alld <= 40).mean()))
    print("overall within 80 px  : %.0f%%" % (100.0 * (alld <= 80).mean()))
    print("median size ratio     : %.2fx (1.00 = same footprint as the known placement)"
          % np.median(allr))

    worst = sorted(rows, key=lambda r: -r[1])[:8]
    print("\nfurthest off:")
    for layer_id, dist, ratio in worst:
        print("   %-34s %5.0f px   %.2fx" % (layer_id, dist, ratio))

    if args.centre_limbs:
        # Learned rules only reproduce the status quo -- they are medians OF the current
        # placements, so refitting to them changes nothing. Fixing the spacing means
        # putting each limb on its own landmark, which is the one thing the combined
        # artwork could never do: its two limbs were 337px apart where the feet are
        # 502px apart, and one rigid transform cannot change spacing without also
        # changing size. Vertical position and size are left alone; neither was wrong.
        moved = 0
        for layer_id, cal in sorted(existing.items()):
            slot, name = layer_id.split("/", 1)
            limb = limb_of(name)
            anchor_fn = LIMB_ANCHORS.get((slot, limb)) if limb else None
            if anchor_fn is None:
                continue
            path = os.path.join(pdir, slot, name + ".png")
            if not os.path.exists(path):
                continue
            cw, ch = content_size(Image.open(path).convert("RGBA"))
            if cw == 0:
                continue
            ax, _ = anchor_fn(landmarks)
            before = CANVAS_W / 2.0 + cal["offsetX"]
            cal["offsetX"] = round(ax - CANVAS_W / 2.0, 2)
            if abs(before - ax) > 1.0:
                moved += 1
        baker.write_calibration(cal_path, existing)
        print("\ncentred %d limbs on their landmarks" % moved)
        return

    if not args.apply:
        print("\nScoring only. --apply writes proposals for uncalibrated layers.")
        return

    written = 0
    for slot in baker.SLOTS:
        sdir = os.path.join(pdir, slot)
        if not os.path.isdir(sdir):
            continue
        for fn in sorted(os.listdir(sdir)):
            if not fn.endswith(".png"):
                continue
            layer_id = "%s/%s" % (slot, os.path.splitext(fn)[0])
            if layer_id in existing:
                continue
            img = Image.open(os.path.join(sdir, fn)).convert("RGBA")
            cw, ch = content_size(img)
            guess = propose(slot, cw, ch, landmarks, rules, os.path.splitext(fn)[0])
            if guess:
                guess["sourceHash"] = baker.sha1_of(os.path.join(sdir, fn))
                existing[layer_id] = guess
                written += 1

    baker.write_calibration(cal_path, existing)
    print("\nwrote %d proposals for previously uncalibrated layers" % written)


if __name__ == "__main__":
    main()
