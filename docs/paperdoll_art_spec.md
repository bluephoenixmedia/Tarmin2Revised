# Paperdoll art generation spec

Written for: whoever is generating paperdoll layer art in Gemini Nano Banana sessions.

Every piece generated without this spec is a piece likely to be generated twice. The
numbers below are not aspirational — they are the median of the 335 layers already in
the repo, so a piece that matches them lands correctly with little or no calibration.

## The one rule that matters most

**Front-facing, orthographic, light from top-centre.** The single biggest cause of a
piece reading as a sticker rather than as worn equipment is a 3/4 camera or a side
key light. A piece drawn at 3/4 cannot be fixed by any amount of moving, scaling or
rotating — warping a 3/4 view onto a front-facing body is geometrically impossible,
not merely difficult. If a generated piece comes back at 3/4, regenerate it; do not
try to calibrate it.

## Canvas and framing

Generate at **1024 x 1536**, transparent background, the item alone with nothing else
in frame.

Framing does **not** need to be precise. `tools/bake_paperdoll_layers.py` crops each
piece to its artwork and rescales it, so padding around the item is normalised away.
This is deliberate: it is what lets a regenerated piece inherit the calibration of the
piece it replaces. What matters is the item's own proportions and perspective, not
where it sits in frame.

## Per-slot targets

These are where each slot's artwork ends up on the 1024 x 1536 master canvas, as
rendered over the character. Use them to judge proportion — a helmet that is twice as
wide as it is tall is wrong regardless of how it is framed.

| Slot | Layers | Rendered size (px) | Centre on canvas | Notes |
|---|---|---|---|---|
| `head` | 20 | 330 x 426 | (512, 269) | Symmetrical about the vertical axis |
| `chest` | 24 | 471 x 609 | (512, 735) | Symmetrical; must read as covering a torso, not floating over one |
| `arms` | 13 | 791 x 277 | (470, 470) | **Both** arms in one image, side by side, equal size |
| `hands` | 10 | 765 x 218 | (493, 890) | **Both** gauntlets, side by side, equal size |
| `legs` | 17 | 459 x 665 | (512, 1053) | Symmetrical |
| `feet` | 13 | 602 x 349 | (510, 1305) | **Both** boots, side by side, equal size |
| `shield` | 8 | 411 x 450 | (822, 860) | Off-hand side only |
| `cloak` | 1 | 719 x 876 | (512, 689) | Widest slot; drapes past the shoulders |
| `weapon` | 229 | 386 x 772 | (197, 670) | Main hand; grip toward the bottom of the image |

### Paired items

Arms, hands and feet are single images containing **both** limbs. Draw them at equal
size, side by side, with no perspective difference between them. A foreground boot
drawn larger than the background boot to suggest depth will look wrong the moment it
is placed on a symmetrical front-facing body.

### Weapons

Draw the weapon with the **grip toward the bottom** of the image and the business end
toward the top. The weapon fitter aligns the grip to the character's hand, so a piece
drawn point-down will be held by the blade.

## Prompt skeleton

Reuse this rather than composing fresh each time. Consistency between sessions is the
whole point.

```
A single <ITEM NAME> for a dark fantasy RPG, pixel art.
Front-facing orthographic view, perfectly straight on, no perspective, no rotation.
Lit from directly above and in front, soft even light, no strong side shadows.
Isolated on a fully transparent background, nothing else in frame.
The item fills most of the frame.
<ITEM-SPECIFIC DETAIL: material, wear, construction>
```

For paired slots (`arms`, `hands`, `feet`), replace the first line with:

```
A matched pair of <ITEM NAME>, side by side, both drawn at exactly the same size and
angle, for a dark fantasy RPG, pixel art.
```

## After generating

1. Drop the PNG over the layer it replaces in `assets/images/paperdoll/<slot>/`.
2. Run `python tools/bake_paperdoll_layers.py --verify`, then `--apply`.
   The baker notices the file changed, re-normalises it, and **keeps the existing
   calibration** — which is the point of normalising.
3. Open the paperdoll editor and check the fit. Adjust only if needed.
4. If no adjustment makes it sit correctly, set `needsArtRedo` on that layer in
   `assets/data/paperdoll_calibration.json`. It will appear in
   `docs/paperdoll_triage.md` as a worklist entry rather than being rediscovered
   later, one equip at a time.

## What is still missing

`docs/paperdoll_triage.md` lists items that have no layer at all. Most are ammunition
and projectiles, which correctly draw nothing. Anything else in that list is a genuine
gap in the art.
