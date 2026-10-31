"""
Bakes every runtime asset the Android app needs:

  app/src/main/assets/motion/<exercise>/f00.webp ... f17.webp
       18-frame seamless demo loops (lossless WebP, transparent background)
  app/src/main/assets/motion/<exercise>/poster.webp
       single still for the exercise list thumbnails
  app/src/main/assets/body_map_<view>.webp
       shaded anatomy mannequin (front / back)
  app/src/main/assets/body_map_<view>_mask.png
       flat region mask used for tap hit-testing
  app/src/main/assets/body_map_<view>_<Region>.webp
       red heat-map overlay for one muscle group

Run:  python3 tools/motiongen/bake.py [--only id]
"""

import os
import sys
import time
import numpy as np
from multiprocessing import Pool
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import mannequin as M
import bodymap
from mannequin import render_loop, Canvas, skeleton
from exercises import EXERCISES, sample

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
MOTION = os.path.join(ASSETS, "motion")

FRAMES = 18
SIZE = 256
POSTER = 320


def bake_exercise(ex):
    out = os.path.join(MOTION, ex["id"])
    os.makedirs(out, exist_ok=True)
    poses = [sample(ex, i / FRAMES) for i in range(FRAMES)]
    ims = render_loop(poses, size=SIZE, ss=2, pad=0.035)
    for i, im in enumerate(ims):
        im.save(os.path.join(out, "f%02d.webp" % i), quality=88, alpha_quality=95, method=6)
    # poster: biggest, most readable pose of the cycle
    poster = render_loop([poses[0]], size=POSTER, ss=2, pad=0.035)[0]
    poster.save(os.path.join(out, "poster.webp"), quality=92, method=6)
    return ex["id"], len(ims)


def _work(ex_id):
    from exercises import EXERCISES
    ex = next(e for e in EXERCISES if e["id"] == ex_id)
    t = time.time()
    _, n = bake_exercise(ex)
    return "%s (%d frames, %.1fs)" % (ex_id, n, time.time() - t)


def bake_regions():
    """Red overlay per muscle group, aligned with bodymap.bake()."""
    for view in ("front", "back"):
        body_path = os.path.join(ASSETS, "body_map_%s.webp" % view)
        mask_path = os.path.join(ASSETS, "body_map_%s_mask.png" % view)
        body = Image.open(body_path).convert("RGBA")
        mask = Image.open(mask_path).convert("RGB")
        b = np.asarray(body).astype(np.float32)
        m = np.asarray(mask).astype(np.int16)
        for region, rgb in bodymap.REGION_COLORS.items():
            sel = np.all(m == np.array(rgb, dtype=np.int16), axis=2)
            if not sel.any():
                continue
            out = b.copy()
            tint = np.array([239.0, 68.0, 68.0], dtype=np.float32)
            out[..., :3] = np.where(sel[..., None],
                                    b[..., :3] * 0.25 + tint * 0.75,
                                    b[..., :3])
            out[..., 3] = np.where(sel, b[..., 3] * 0.85, 0.0)
            Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA").save(
                os.path.join(ASSETS, "body_map_%s_%s.webp" % (view, region)),
                quality=90, method=6)
    print("region overlays done")


def main():
    only = None
    if "--only" in sys.argv:
        i = sys.argv.index("--only")
        only = set(sys.argv[i + 1:])
    os.makedirs(MOTION, exist_ok=True)

    todo = [e["id"] for e in EXERCISES if not only or e["id"] in only]
    with Pool(processes=min(8, os.cpu_count() or 2)) as pool:
        for line in pool.imap_unordered(_work, todo):
            print(line)

    if not only:
        bodymap.bake("front")
        bodymap.bake("back")
        bake_regions()
    total = sum(os.path.getsize(os.path.join(dp, f))
                for dp, _, fs in os.walk(MOTION) for f in fs)
    print("motion assets: %.1f KB" % (total / 1024))


if __name__ == "__main__":
    main()
