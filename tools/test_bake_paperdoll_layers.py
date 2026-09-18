"""Tests for the normalising baker.

Run with: python tools/test_bake_paperdoll_layers.py

These cover the properties that make a calibration a one-time cost. The idempotence
test in particular guards a bug that already bit once: running --apply a second time
measured the already-centred layers, concluded no offset was needed, and flattened
317 of 335 calibrations to identity.
"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw

import bake_paperdoll_layers as baker


def make_layer(cx, cy, w, h, canvas=(baker.CANVAS_W, baker.CANVAS_H)):
    """A solid rectangle of artwork at a known place on an otherwise empty canvas."""
    img = Image.new("RGBA", canvas, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([cx - w // 2, cy - h // 2, cx + w // 2, cy + h // 2], fill=(200, 120, 60, 255))
    return img


class NormalisationTest(unittest.TestCase):

    def test_normalisation_centres_the_artwork(self):
        # Artwork off to one side must come back centred, because that is what makes
        # the seed calibration a pure offset from canvas centre.
        img = make_layer(300, 400, 200, 260)
        norm, nw, nh = baker.normalise(img, "chest")
        bbox = baker.solid_bbox(norm)
        centre_x = (bbox[0] + bbox[2]) / 2.0
        centre_y = (bbox[1] + bbox[3]) / 2.0
        self.assertAlmostEqual(baker.CANVAS_CX, centre_x, delta=1.5)
        self.assertAlmostEqual(baker.CANVAS_CY, centre_y, delta=1.5)

    def test_normalisation_fits_the_slot_box_preserving_aspect(self):
        img = make_layer(512, 768, 200, 400)
        norm, nw, nh = baker.normalise(img, "chest")
        box_w, box_h = baker.SLOT_BOXES["chest"]
        self.assertLessEqual(nw, box_w + 1)
        self.assertLessEqual(nh, box_h + 1)
        self.assertAlmostEqual(200 / 400.0, nw / float(nh), delta=0.02)

    def test_differently_framed_art_normalises_identically(self):
        # The core promise. The same artwork regenerated with more padding around it
        # must normalise to the same thing, so an existing calibration still applies.
        tight = make_layer(512, 768, 240, 300)
        shifted = make_layer(300, 500, 240, 300)

        a, _, _ = baker.normalise(tight, "chest")
        b, _, _ = baker.normalise(shifted, "chest")
        self.assertEqual(baker.solid_bbox(a), baker.solid_bbox(b))

    def test_seed_calibration_restores_the_original_placement(self):
        img = make_layer(300, 1000, 180, 240)
        before = baker.alpha_moments(img)

        norm, nw, nh = baker.normalise(img, "legs")
        cal = baker.seed_calibration(img, nw, nh)
        rebuilt = baker.apply_calibration(norm, cal)
        after = baker.alpha_moments(rebuilt)

        for b, a in zip(before, after):
            self.assertAlmostEqual(b, a, delta=baker.VERIFY_TOL_PX)

    def test_alpha_floor_stops_resampling_fringe_accumulating(self):
        # Without the clamp, LANCZOS ringing widens the measured bbox a little on every
        # pass, so repeated bakes would creep the piece larger each time.
        img = make_layer(512, 768, 300, 300)
        first, _, _ = baker.normalise(img, "chest")
        second, _, _ = baker.normalise(first, "chest")
        self.assertEqual(baker.solid_bbox(first), baker.solid_bbox(second))


class IdempotenceTest(unittest.TestCase):
    """Re-running the baker must never silently discard placement."""

    def test_unchanged_layer_is_skipped_entirely(self):
        img = make_layer(300, 1000, 180, 240)
        norm, nw, nh = baker.normalise(img, "legs")
        cal = baker.seed_calibration(img, nw, nh)
        cal["sourceHash"] = "abc123"

        existing = {"legs/greaves": cal}

        # This is the guard the loop applies: same hash means already normalised.
        prior = existing.get("legs/greaves")
        self.assertIsNotNone(prior)
        self.assertEqual("abc123", prior.get("sourceHash"))

        # Re-seeding from the normalised image is exactly the destructive path, and
        # demonstrably collapses to identity -- which is why the guard has to exist.
        reseeded = baker.seed_calibration(norm, nw, nh)
        self.assertAlmostEqual(0.0, reseeded["offsetX"], delta=1.0)
        self.assertAlmostEqual(0.0, reseeded["offsetY"], delta=1.0)
        self.assertAlmostEqual(1.0, reseeded["scaleX"], delta=0.02)
        self.assertNotAlmostEqual(cal["offsetY"], reseeded["offsetY"], delta=1.0)

    def test_load_existing_calibration_handles_a_first_run(self):
        with tempfile.TemporaryDirectory() as d:
            missing = os.path.join(d, "nope.json")
            self.assertEqual({}, baker.load_existing_calibration(missing))

    def test_load_existing_calibration_round_trips_what_was_written(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "cal.json")
            baker.write_calibration(path, {
                "chest/breastplate": {"offsetX": -12.5, "offsetY": 40.0, "scaleX": 1.1,
                                      "scaleY": 1.1, "rotation": 0, "sourceHash": "ff00"},
            })
            loaded = baker.load_existing_calibration(path)
            self.assertIn("chest/breastplate", loaded)
            self.assertEqual(-12.5, loaded["chest/breastplate"]["offsetX"])
            self.assertEqual("ff00", loaded["chest/breastplate"]["sourceHash"])




class PairSplittingTest(unittest.TestCase):
    """Paired slots draw both limbs in one image.

    A single offset+scale moves that pair as a rigid unit, so its internal spacing is
    frozen into the artwork. Measured against the body: the boots sit 337-413px apart
    where the feet are 502px apart, so spreading them would need a 1.49x scale that
    makes each boot half again too big. Correct spacing or correct size, never both --
    which is why they could not be calibrated no matter how much they were nudged.

    Splitting the pair gives each limb its own placement and decouples the two.
    """

    def pair(self, left_cx, right_cx, y=760, w=120, h=200):
        img = Image.new("RGBA", (baker.CANVAS_W, baker.CANVAS_H), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        for cx in (left_cx, right_cx):
            d.rectangle([cx - w // 2, y - h // 2, cx + w // 2, y + h // 2], fill=(180, 140, 90, 255))
        return img

    def test_splits_a_clean_pair_into_two_halves(self):
        img = self.pair(300, 720)
        left, right = baker.split_pair(img)
        self.assertIsNotNone(left)
        self.assertIsNotNone(right)

        lb = baker.solid_bbox(left)
        rb = baker.solid_bbox(right)
        self.assertLess(lb[2], rb[0], "halves must not overlap")
        self.assertAlmostEqual(300, (lb[0] + lb[2]) / 2.0, delta=3)
        self.assertAlmostEqual(720, (rb[0] + rb[2]) / 2.0, delta=3)

    def test_each_half_keeps_its_own_position_on_the_canvas(self):
        # The split is a mask, not a crop: each half stays where it was so its seed
        # calibration still describes where that limb actually sits.
        img = self.pair(300, 720)
        left, right = baker.split_pair(img)
        self.assertEqual(img.size, left.size)
        self.assertEqual(img.size, right.size)

    def test_stray_blobs_are_grouped_by_which_side_they_fall_on(self):
        # great_boots.png separates into three pieces, not two -- a strap or buckle
        # detached from its boot. Grouping by side keeps it with the limb it belongs to
        # instead of being mistaken for a third limb.
        img = self.pair(300, 720)
        d = ImageDraw.Draw(img)
        d.rectangle([250, 900, 290, 940], fill=(180, 140, 90, 255))  # stray, left side

        left, right = baker.split_pair(img)
        lb = baker.solid_bbox(left)
        self.assertGreater(lb[3], 880, "the stray piece should travel with the left half")

    def test_refuses_to_split_a_single_blob(self):
        # One connected mass has no honest midline; guessing one would slice a boot in
        # half down the middle.
        img = Image.new("RGBA", (baker.CANVAS_W, baker.CANVAS_H), (0, 0, 0, 0))
        ImageDraw.Draw(img).rectangle([400, 700, 620, 900], fill=(180, 140, 90, 255))
        self.assertEqual((None, None), baker.split_pair(img))

    def test_refuses_an_empty_image(self):
        blank = Image.new("RGBA", (baker.CANVAS_W, baker.CANVAS_H), (0, 0, 0, 0))
        self.assertEqual((None, None), baker.split_pair(blank))

    def test_split_halves_can_be_placed_independently(self):
        # The property the whole change exists for: after splitting, spacing and size
        # are no longer locked together.
        img = self.pair(300, 720)
        left, right = baker.split_pair(img)

        lnorm, lw, lh = baker.normalise(left, "feet")
        rnorm, rw, rh = baker.normalise(right, "feet")
        lcal = baker.seed_calibration(left, lw, lh)
        rcal = baker.seed_calibration(right, rw, rh)

        # Move only the right limb outward; the left must not follow.
        rcal["offsetX"] += 120
        lplaced = baker.solid_bbox(baker.apply_calibration(lnorm, lcal))
        rplaced = baker.solid_bbox(baker.apply_calibration(rnorm, rcal))
        self.assertAlmostEqual(300, (lplaced[0] + lplaced[2]) / 2.0, delta=4)
        self.assertAlmostEqual(840, (rplaced[0] + rplaced[2]) / 2.0, delta=4)


if __name__ == "__main__":
    unittest.main(verbosity=2)
