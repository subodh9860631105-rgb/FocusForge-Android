"""
Bakes the interactive anatomy map used by the Focus area selector.

Produces, for the front and the back view:
  * a shaded mannequin image (WebP, transparent background)
  * a matching flat-colour region mask (PNG, lossless) so the app can hit-test a
    tap by sampling one pixel instead of hand-tuning rectangles.
"""

import os
import math
import numpy as np
from PIL import Image as PILImage, ImageDraw

import mannequin as M
from mannequin import Canvas, skeleton, P

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets")

REGION_COLORS = {
    "Chest":     (235, 45, 45),
    "Abs":       (40, 215, 95),
    "Arms":      (60, 105, 245),
    "Shoulders": (245, 170, 40),
    "Back":      (200, 70, 220),
    "Core":      (30, 200, 220),
    "Legs":      (250, 110, 180),
    "Glutes":    (150, 215, 60),
}

SIZE = 512
SS = 2


def pts_ellipse(cx, cy, rx, ry, ang=0.0, n=48):
    a0 = math.radians(ang)
    out = []
    for i in range(n):
        a = 2 * math.pi * i / n
        x, y = math.cos(a) * rx, math.sin(a) * ry
        out.append((cx + x * math.cos(a0) - y * math.sin(a0),
                    cy + x * math.sin(a0) + y * math.cos(a0)))
    return out


def seg_ellipse(p1, p2, half_w, scale=1.0):
    """Ellipse centred on a segment, rotated to follow it."""
    cx, cy = (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2
    ang = math.degrees(math.atan2(p2[1] - p1[1], p2[0] - p1[0]))
    length = math.hypot(p2[0] - p1[0], p2[1] - p1[1]) * scale / 2
    return pts_ellipse(cx, cy, length, half_w, ang)


def build_regions(sk, view):
    """Return [(muscle, polygon_points)] in normalised canvas coordinates."""
    reg = []
    root, sh = sk["root"], sk["shoulder"]
    lean = sk["lean"]

    def torso_pt(t, off=0.0):
        x = root[0] + (sh[0] - root[0]) * t
        y = root[1] + (sh[1] - root[1]) * t
        a = math.radians(lean)
        return (x - math.cos(a) * off, y - math.sin(a) * off)

    core = [torso_pt(t, o) for t, o in
            ((0.58, -0.104), (0.58, 0.104), (0.16, 0.100), (0.16, -0.100))]
    reg.append(("Core", core))
    chest = [torso_pt(t, o) for t, o in
             ((0.86, -0.085), (0.86, 0.085), (0.66, 0.095), (0.66, -0.095))]
    reg.append(("Chest", chest))
    abs_ = [torso_pt(t, o) for t, o in
            ((0.60, -0.070), (0.60, 0.070), (0.14, 0.062), (0.14, -0.062))]
    reg.append(("Abs", abs_))

    for side, key in (("n", "Shoulders"), ("f", "Shoulders")):
        shj = sk["sh_" + side]
        reg.append((key, pts_ellipse(shj[0], shj[1], 0.045, 0.040)))
    for side in ("n", "f"):
        arm = seg_ellipse(sk["sh_" + side], sk["elbow_" + side], 0.032)
        fore = seg_ellipse(sk["elbow_" + side], sk["wrist_" + side], 0.028)
        reg.append(("Arms", arm))
        reg.append(("Arms", fore))
    for side in ("n", "f"):
        thigh = seg_ellipse(sk["hip_" + side], sk["knee_" + side], 0.058)
        shin = seg_ellipse(sk["knee_" + side], sk["ankle_" + side], 0.045)
        reg.append(("Legs", thigh))
        reg.append(("Legs", shin))
    if view == "back":
        glutes = []
        lats = [torso_pt(t, o) for t, o in
                ((0.90, -0.110), (0.90, 0.110), (0.40, 0.085), (0.40, -0.085))]
        reg.append(("Back", lats))
        for side in ("n", "f"):
            glutes.append(("Glutes", pts_ellipse(
                (sk["hip_" + side][0] * 0.75 + root[0] * 0.25),
                sk["hip_" + side][1] + 0.012, 0.052, 0.048)))
        reg.extend(glutes)
    return reg


def bake(view):
    # arms slightly away from the body, feet apart
    if view == "front":
        pose = P(view="front", root=(0.5, 0.50), spread=1.0,
                 arm_n=(24, 20), arm_f=(-24, -20),
                 leg_n=(5, 2), leg_f=(-5, -2), foot_n=20, foot_f=-20,
                 ankle_n=(0.573, 0.918), ankle_f=(0.427, 0.918))
    else:
        pose = P(view="back", root=(0.5, 0.50), spread=1.0,
                 arm_n=(26, 22), arm_f=(-26, -22),
                 leg_n=(5, 2), leg_f=(-5, -2), foot_n=-20, foot_f=20,
                 ankle_n=(0.573, 0.918), ankle_f=(0.427, 0.918))

    sk = skeleton(pose)
    cv = Canvas(SIZE, SS)
    M.draw_leg(cv, sk, "f", near=False)
    M.draw_arm(cv, sk, "f", near=False)
    M.draw_leg(cv, sk, "n", near=True)
    cv.torso(sk["root"], sk["shoulder"], sk["lean"], sk["side_view"], pose["spread"])
    cv.torso(sk["root"], sk["shoulder"], sk["lean"], sk["side_view"], pose["spread"],
             tint=(M.SUIT_LIT, M.SUIT, M.SUIT_DK), t0=-0.26, t1=0.135, rim_gain=0.32)
    cv.capsule(M.mixp(sk["shoulder"], sk["neck_top"], 0.02), sk["neck_top"],
               0.034, 0.029, near=True)
    M.draw_arm(cv, sk, "n", near=True)
    M.draw_head(cv, sk, pose)

    arr = np.clip(np.concatenate([cv.rgb, cv.a[..., None]], axis=2), 0, 1)
    body = PILImage.fromarray((arr * 255).astype(np.uint8), "RGBA").resize(
        (SIZE, SIZE), PILImage.LANCZOS)

    # region mask in the same space
    mask = PILImage.new("RGB", (SIZE * SS, SIZE * SS), (0, 0, 0))
    md = ImageDraw.Draw(mask)
    for name, poly in build_regions(sk, view):
        md.polygon([(x * SIZE * SS, y * SIZE * SS) for x, y in poly],
                   fill=REGION_COLORS[name])
    mask = mask.resize((SIZE, SIZE), PILImage.NEAREST)

    # keep regions inside the silhouette
    keep = np.asarray(body.split()[3]) > 40
    m = np.asarray(mask).copy()
    m[~keep] = 0
    mask = PILImage.fromarray(m, "RGB")

    os.makedirs(OUT, exist_ok=True)
    body.save(os.path.join(OUT, "body_map_%s.webp" % view), quality=92, method=6)
    mask.save(os.path.join(OUT, "body_map_%s_mask.png" % view), optimize=True)


if __name__ == "__main__":
    for v in ("front", "back"):
        bake(v)
        print("baked", v)
