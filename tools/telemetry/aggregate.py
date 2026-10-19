#!/usr/bin/env python3
"""Aggregate Expedition telemetry into the numbers the balance tickets read.

Reads logs/telemetry/expedition_*.json (schema 2+), keeps the files that match the filter
(by default: MEDIUM difficulty, no debug cheats), and prints:

  * expedition totals: outcomes, turns, depth, regen, XP, gold, items
  * hit rate per attack mode (melee, ranged, thrown, firearm, spell, book)
  * fights per depth: turns, player and monster hit rates, damage, outcomes
  * fights per monster: turns-to-kill and hit rates

It also writes expeditions.csv and fights.csv for spreadsheets.

    python tools/telemetry/aggregate.py
    python tools/telemetry/aggregate.py --difficulty ANY --include-cheats --out report/

Abandoned expeditions (quit to menu) are counted everywhere, by decision on the 0.0.2 map.
"""
import argparse
import csv
import glob
import json
import os
import statistics
import sys

MODES = ["melee", "ranged", "thrown", "firearm", "spell", "book"]
EXPEDITION_FIELDS = ["turns", "maxDepth", "kills", "damageDealt", "damageTaken", "hpRegenerated",
                     "mpRegenerated", "xpGained", "goldGained", "goldSpent", "itemsPickedUp",
                     "divinitiesEarned", "divinitiesSpent", "turnsHungry", "turnsThirsty",
                     "turnsCold", "turnsPoisoned", "hpVengeance", "unlocksEarned"]


def load(directory, difficulty, include_cheats):
    kept, skipped = [], {"old schema": 0, "unreadable": 0, "cheats": 0, "difficulty": 0}
    for path in sorted(glob.glob(os.path.join(directory, "expedition_*.json"))):
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except (OSError, ValueError):
            skipped["unreadable"] += 1
            continue
        if data.get("schemaVersion", 1) < 2:
            skipped["old schema"] += 1
        elif data.get("cheatsUsed") and not include_cheats:
            skipped["cheats"] += 1
        elif difficulty != "ANY" and data.get("difficulty") != difficulty:
            skipped["difficulty"] += 1
        else:
            data["_file"] = os.path.basename(path)
            kept.append(data)
    return kept, skipped


def spread(values):
    """median [p25-p75] (n)"""
    values = sorted(v for v in values if v is not None)
    if not values:
        return "-"
    if len(values) == 1:
        return "%g (n=1)" % values[0]
    q = statistics.quantiles(values, n=4)
    return "%g [%g-%g] (n=%d)" % (statistics.median(values), q[0], q[2], len(values))


def rate(hits, total):
    return "%.0f%% (%d/%d)" % (100.0 * hits / total, hits, total) if total else "-"


def report(exps, skipped, out):
    p = lambda *a: print(*a, file=out)
    p("EXPEDITIONS: %d kept; skipped %s" % (len(exps), ", ".join("%s %d" % kv for kv in skipped.items() if kv[1])
                                               or "none"))
    if not exps:
        return
    outcomes = {}
    for e in exps:
        outcomes[e.get("outcome")] = outcomes.get(e.get("outcome"), 0) + 1
    p("outcomes: " + ", ".join("%s %d" % kv for kv in sorted(outcomes.items())))
    p("")
    p("PER EXPEDITION (median [p25-p75])")
    for field in EXPEDITION_FIELDS:
        p("  %-16s %s" % (field, spread([e.get(field) for e in exps])))

    p("")
    p("HIT RATE BY ATTACK MODE (hit + glancing / attacks)")
    for mode in MODES:
        hit = glance = miss = 0
        for e in exps:
            m = e.get("attacksByMode", {}).get(mode)
            if m:
                hit += m.get("hit", 0)
                glance += m.get("glancing", 0)
                miss += m.get("miss", 0)
        total = hit + glance + miss
        if total:
            p("  %-8s %s   glancing %d" % (mode, rate(hit + glance, total), glance))

    # Schema 3 (#234): Weapon Families, Mastery and spells. Older files simply lack them.
    families = {}
    for e in exps:
        for fam, v in e.get("attacksByFamily", {}).items():
            acc = families.setdefault(fam, [0, 0, 0, 0])
            for i, k in enumerate(("hit", "glancing", "miss", "qualifying")):
                acc[i] += v.get(k, 0)
    if families:
        p("")
        p("BY WEAPON FAMILY (full hits / attacks; qualifying = trained Mastery)")
        for fam in sorted(families):
            hit, glance, miss, qual = families[fam]
            p("  %-8s %s   glancing %d   qualifying %d" % (fam, rate(hit, hit + glance + miss), glance, qual))
    rank_ups = [(e["_file"], r) for e in exps for r in e.get("masteryRankUps", [])]
    if rank_ups:
        p("")
        p("MASTERY RANK-UPS")
        for f, r in rank_ups:
            p("  %-8s %-8s turn %s   %s" % (r.get("family"), r.get("rank"), r.get("turn"), f))
    casts, healing = {}, {}
    for e in exps:
        for s, n in e.get("spellsCast", {}).items():
            casts[s] = casts.get(s, 0) + n
        for s, n in e.get("spellHealing", {}).items():
            healing[s] = healing.get(s, 0) + n
    if casts:
        p("")
        p("SPELLS CAST (HP healed)")
        for s in sorted(casts):
            p("  %-20s %d%s" % (s, casts[s], ("   healed %d" % healing[s]) if s in healing else ""))

    fights =[dict(f, expedition=e["_file"]) for e in exps for f in e.get("fights", [])]
    p("")
    p("FIGHTS BY DEPTH")
    for depth in sorted({f["depth"] for f in fights}):
        fs = [f for f in fights if f["depth"] == depth]
        p("  depth %d: %d fights" % (depth, len(fs)))
        p("    turns            %s" % spread([f["turns"] for f in fs]))
        p("    player hit rate  %s" % rate(sum(f["playerHits"] for f in fs), sum(f["playerAttacks"] for f in fs)))
        p("    monster hit rate %s" % rate(sum(f["monsterHits"] for f in fs), sum(f["monsterAttacks"] for f in fs)))
        p("    damage dealt     %s" % spread([f["damageDealt"] for f in fs]))
        p("    damage taken     %s" % spread([f["damageTaken"] for f in fs]))
        oc = {}
        for f in fs:
            oc[f["outcome"]] = oc.get(f["outcome"], 0) + 1
        p("    outcomes         " + ", ".join("%s %d" % kv for kv in sorted(oc.items())))

    p("")
    p("FIGHTS BY MONSTER (turns-to-kill over killed fights only)")
    for monster in sorted({f["monster"] for f in fights}):
        fs = [f for f in fights if f["monster"] == monster]
        killed = [f for f in fs if f["outcome"] == "killed"]
        p("  %-20s fights %3d  killed %3d  ttk %-22s  player hit %-16s  monster hit %s" % (
            monster, len(fs), len(killed), spread([f["turns"] for f in killed]),
            rate(sum(f["playerHits"] for f in fs), sum(f["playerAttacks"] for f in fs)),
            rate(sum(f["monsterHits"] for f in fs), sum(f["monsterAttacks"] for f in fs))))
    return fights


def write_csv(exps, fights, directory):
    os.makedirs(directory, exist_ok=True)
    head = ["file", "outcome", "difficulty", "lifeId"] + EXPEDITION_FIELDS
    with open(os.path.join(directory, "expeditions.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(head)
        for e in exps:
            w.writerow([e["_file"], e.get("outcome"), e.get("difficulty"), e.get("lifeId")]
                       + [e.get(k) for k in EXPEDITION_FIELDS])
    cols = ["expedition", "monster", "level", "depth", "openTurn", "turns", "playerAttacks", "playerHits",
            "monsterAttacks", "monsterHits", "damageDealt", "damageTaken", "modes", "outcome"]
    with open(os.path.join(directory, "fights.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(cols)
        for fight in fights:
            w.writerow([";".join(fight[c]) if c == "modes" else fight.get(c) for c in cols])


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dir", default="logs/telemetry", help="folder holding expedition_*.json")
    ap.add_argument("--difficulty", default="MEDIUM", help="difficulty to keep, or ANY")
    ap.add_argument("--include-cheats", action="store_true", help="keep expeditions where debug cheats were used")
    ap.add_argument("--out", default=None, help="folder for the CSVs (default: <dir>/report)")
    args = ap.parse_args(argv)

    exps, skipped = load(args.dir, args.difficulty, args.include_cheats)
    fights = report(exps, skipped, sys.stdout) or []
    out = args.out or os.path.join(args.dir, "report")
    if exps:
        write_csv(exps, fights, out)
        print("\nCSV written to %s" % out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
