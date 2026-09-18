"""
measure_landmarks.py

Measures anatomical landmarks from the transparent body silhouette and writes
assets/data/paperdoll_landmarks.json.

This is the ground truth the auto-fitter needs and never had. Until base_father.png was
decomposed it was fully opaque -- frame, parchment and all -- so there was no silhouette
to measure and the old baker could only paste each piece at a hardcoded offset.

Landmarks are derived from the alpha channel rather than hand-placed, so re-running after
the body art changes re-measures instead of going stale. They are in master-canvas pixels,
image space (+y DOWN), matching paperdoll_calibration.json.

Run:
    python tools/measure_landmarks.py            # measure, write, and render a check image
    python tools/measure_landmarks.py --dry-run  # print only
"""

import argparse
import json
import os

import numpy as np
from PIL import Image, ImageDraw

CANVAS_W = 1024
CANVAS_H = 1536
SOLID = 128


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def row_runs(solid, y):
    """Contiguous horizontal spans of body at this row.

    Rows cross the figure in pieces -- torso plus two arms, or two legs -- and which
    piece matters depends on the landmark, so callers pick rather than taking the extent.
    """
    xs = np.nonzero(solid[y])[0]
    if not len(xs):
        return []
    out = []
    start = prev = xs[0]
    for x in xs[1:]:
        if x - prev > 1:
            out.append((int(start), int(prev)))
            start = x
        prev = x
    out.append((int(start), int(prev)))
    return out


def central_run(solid, y):
    """The span straddling the body's midline: the torso, or the head."""
    runs = row_runs(solid, y)
    if not runs:
        return None
    return min(runs, key=lambda r: abs((r[0] + r[1]) / 2.0 - CANVAS_W / 2.0))


def measure(solid):
    rows = np.nonzero(solid.any(axis=1))[0]
    top, bottom = int(rows.min()), int(rows.max())

    # Width of the central (head, then torso) span at every row.
    profile = {}
    for y in range(top, bottom + 1):
        c = central_run(solid, y)
        if c:
            profile[y] = c

    def width(y):
        r = profile.get(y)
        return (r[1] - r[0]) if r else 0

    # The neck is the first valley in that profile: the head widens to the cheekbones,
    # pinches at the neck, then flares into the shoulders. Searching for a global
    # minimum or a maximum over a fixed window finds the shoulders instead, because
    # they are far wider than the head.
    neck_y = None
    for y in range(top + 40, top + 400):
        w = width(y)
        if w == 0:
            continue
        # A valley: narrower than 30px above and than 30px below.
        if w < width(y - 30) and w < width(y + 30):
            neck_y = y
            break
    if neck_y is None:
        neck_y = top + 200
    neck_run = profile.get(neck_y, (CANVAS_W * 0.4, CANVAS_W * 0.6))

    # Head is everything above the neck; take its widest row.
    head_y, head_run = max(((y, profile[y]) for y in range(top, neck_y) if y in profile),
                           key=lambda t: t[1][1] - t[1][0])
    head_cx = (head_run[0] + head_run[1]) / 2.0

    # Crotch: the first row below the torso that splits cleanly into two legs. Found
    # before the waist, because it bounds where the waist can be.
    crotch_y = None
    for y in range(neck_y + 300, bottom):
        runs = [r for r in row_runs(solid, y) if r[1] - r[0] > 40]
        if len(runs) == 2 and runs[0][1] < CANVAS_W / 2 < runs[1][0]:
            crotch_y = y
            break
    if crotch_y is None:
        crotch_y = int((neck_y + bottom) / 2)

    # Below the neck the arms merge with the torso, then separate. The waist is the
    # narrowest central span in the stretch where they are separate, so it measures the
    # body rather than body-plus-arms.
    #
    # The minimum width matters: the gap between the legs throws off one- and two-pixel
    # slivers that straddle the midline, and those would win "narrowest" outright.
    MIN_TORSO_W = 120
    torso = [(y, profile[y]) for y in range(neck_y + 100, crotch_y)
             if y in profile
             and len(row_runs(solid, y)) >= 3
             and profile[y][1] - profile[y][0] >= MIN_TORSO_W]
    waist_y, waist_run = min(torso, key=lambda t: t[1][1] - t[1][0]) if torso \
        else (neck_y + 430, neck_run)

    # Hands hang clear of the body, so on rows that split into three or more pieces the
    # outermost two are the hands. Take the row where they reach furthest apart.
    hand_rows = [(y, r[0], r[-1]) for y in range(neck_y + 100, crotch_y)
                 for r in [row_runs(solid, y)] if len(r) >= 3]
    hand_y, hand_l, hand_r = max(hand_rows, key=lambda t: t[2][1] - t[1][0]) if hand_rows \
        else (waist_y, (0, 0), (CANVAS_W, CANVAS_W))

    def leg_runs(y):
        runs = [r for r in row_runs(solid, y) if r[1] - r[0] > 30]
        if len(runs) >= 2:
            return runs[0], runs[-1]
        return None

    # Hips just below the split; knees midway to the floor; ankles near the bottom.
    hip_y = crotch_y + 20
    knee_y = int(crotch_y + (bottom - crotch_y) * 0.48)
    ankle_y = int(crotch_y + (bottom - crotch_y) * 0.80)

    hips = leg_runs(hip_y)
    knees = leg_runs(knee_y)
    ankles = leg_runs(ankle_y)
    feet = leg_runs(bottom - 12)

    def pair(runs, fallback):
        if not runs:
            return fallback
        (l0, l1), (r0, r1) = runs
        return ((l0 + l1) / 2.0, (r0 + r1) / 2.0)

    hip_cx = pair(hips, (CANVAS_W * 0.35, CANVAS_W * 0.65))
    knee_cx = pair(knees, hip_cx)
    ankle_cx = pair(ankles, knee_cx)
    foot_cx = pair(feet, ankle_cx)

    # Shoulders: widest row between the neck and where the arms detach.
    shoulder_y = neck_y + 55
    shoulder_run = central_run(solid, shoulder_y) or head_run

    points = {
        "head_top": [round(head_cx, 1), float(top)],
        "head_left": [float(head_run[0]), float(head_y)],
        "head_right": [float(head_run[1]), float(head_y)],
        "chin": [round((neck_run[0] + neck_run[1]) / 2.0, 1), float(neck_y)],
        "shoulder_left": [float(shoulder_run[0]), float(shoulder_y)],
        "shoulder_right": [float(shoulder_run[1]), float(shoulder_y)],
        "waist_left": [float(waist_run[0]), float(waist_y)],
        "waist_right": [float(waist_run[1]), float(waist_y)],
        "hand_left": [round((hand_l[0] + hand_l[1]) / 2.0, 1), float(hand_y)],
        "hand_right": [round((hand_r[0] + hand_r[1]) / 2.0, 1), float(hand_y)],
        "crotch": [CANVAS_W / 2.0, float(crotch_y)],
        "hip_left": [round(hip_cx[0], 1), float(hip_y)],
        "hip_right": [round(hip_cx[1], 1), float(hip_y)],
        "knee_left": [round(knee_cx[0], 1), float(knee_y)],
        "knee_right": [round(knee_cx[1], 1), float(knee_y)],
        "ankle_left": [round(ankle_cx[0], 1), float(ankle_y)],
        "ankle_right": [round(ankle_cx[1], 1), float(ankle_y)],
        "foot_left": [round(foot_cx[0], 1), float(bottom)],
        "foot_right": [round(foot_cx[1], 1), float(bottom)],
    }
    return points


def render_check(body, points, path):
    """Draw the landmarks over the body so they can be eyeballed, not just trusted."""
    img = Image.new("RGB", (CANVAS_W, CANVAS_H), (30, 40, 30))
    img.paste(body, (0, 0), body)
    d = ImageDraw.Draw(img)
    for name, (x, y) in sorted(points.items()):
        r = 9
        d.ellipse([x - r, y - r, x + r, y + r], outline=(255, 60, 60), width=4)
        d.line([x - 22, y, x + 22, y], fill=(255, 220, 0), width=2)
    img.resize((512, 768)).save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    repo = repo_root()
    body_path = os.path.join(repo, "assets", "images", "paperdoll", "base_father.png")
    body = Image.open(body_path).convert("RGBA")

    alpha = np.asarray(body.getchannel("A"))
    if (alpha > SOLID).all():
        raise SystemExit(
            "base_father.png is fully opaque -- it has not been decomposed into a\n"
            "transparent body yet, so there is no silhouette to measure. See\n"
            "tools/check_phase0.py.")

    points = measure(alpha > SOLID)

    width = points["shoulder_right"][0] - points["shoulder_left"][0]
    print("landmarks measured from the body silhouette:")
    for name, (x, y) in sorted(points.items()):
        print("  %-15s (%7.1f, %7.1f)" % (name, x, y))
    print("\nshoulder span: %.0f px    head width: %.0f px"
          % (width, points["head_right"][0] - points["head_left"][0]))

    if args.dry_run:
        return

    out = os.path.join(repo, "assets", "data", "paperdoll_landmarks.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump({
            "_comment": "Anatomical landmarks measured from base_father.png by "
                        "tools/measure_landmarks.py. Master-canvas pixels, +y DOWN. "
                        "Re-run after changing the body art; do not hand-edit.",
            "canvas": {"width": CANVAS_W, "height": CANVAS_H},
            "points": points,
        }, fh, indent=2, sort_keys=True)
        fh.write("\n")
    print("\nwrote assets/data/paperdoll_landmarks.json")

    check = os.path.join(repo, "docs", "paperdoll_landmarks_check.png")
    render_check(body, points, check)
    print("wrote docs/paperdoll_landmarks_check.png")


if __name__ == "__main__":
    main()
