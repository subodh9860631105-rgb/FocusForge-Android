# FocusForge Motion Generator

Bakes the **3D mannequin exercise demonstrations** and the **anatomy map** that ship inside the APK.

Everything the app plays is pre-rendered here — the Android side only decodes WebP frames, so the
animation stays smooth on cheap phones with zero runtime 3D work.

```
tools/motiongen/
├── mannequin.py    # shaded 3D-looking body renderer (capsule lofting + cylindrical lighting)
├── exercises.py    # the 22 exercise motion clips, authored as IK keyframes
├── bodymap.py      # front/back anatomy mannequin + tappable region masks
├── bake.py         # writes everything into app/src/main/assets/
├── sheet.py        # contact-sheet previews for reviewing a clip
└── web_preview/    # browser preview of the exact shipped assets
```

## Regenerate the assets

```bash
python3 -m pip install pillow numpy
python3 tools/motiongen/bake.py              # everything (~3 min on 2 cores)
python3 tools/motiongen/bake.py --only squat_smith curl_db   # just a couple of clips
```

## What lands in `app/src/main/assets`

| Path | Used by |
| --- | --- |
| `motion/<exercise>/f00..f17.webp` | `MotionFigureView` — the looping demo (18 frames, 256 px, transparent) |
| `motion/<exercise>/poster.webp` | static fallback frame |
| `body_map_front.webp` / `body_map_back.webp` | `AnatomyMapView` shaded mannequin |
| `body_map_<view>_mask.png` | pixel hit-testing: tap → muscle group |
| `body_map_<view>_<Region>.webp` | pre-baked red heat-map overlay |

Total ≈ 3.4 MB for 22 clips.

## Authoring a new exercise

Poses are absolute canvas coordinates, with hands/ankles written as IK targets so a keyframe reads
like a coaching cue:

```python
add(E("squat_smith", "side",
      base=dict(root=(0.500, 0.500), lean=-4.0, ankle_n=(0.545, 0.925),
                hand_n=(0.430, 0.215), hand_f=(0.455, 0.225)),
      frames=[
          (0.00, dict(root=(0.500, 0.500), hand_n=(0.430, 0.215))),   # lockout
          (0.40, dict(root=(0.465, 0.680), hand_n=(0.395, 0.395))),   # bottom
          (0.52, dict(root=(0.465, 0.690), hand_n=(0.395, 0.405))),
          (1.00, dict(root=(0.500, 0.500), hand_n=(0.430, 0.215))),   # loop back
      ],
      gear=[("floor", "back", 0.955)],
      gear_fn=bar_on_back(0.010),
      glow=[("thigh_n", 0.05, 0.75), ("glute",)]))
```

* `0.00 → 1.00` is one full rep and loops seamlessly (frame 17 is one step before 0).
* `glow` entries take optional `(start, end, intensity)` along the muscle, so the working muscle
  lights up even when the limb is partly hidden.
* `gear` / `gear_fn` add benches, bars, cables, mats and wheels; `gear_fn` can follow the skeleton
  (e.g. a barbell that tracks the hands).

Then run `python3 tools/motiongen/bake.py --only <id>` and refresh the browser preview.

## Browser preview

```bash
python3 -m http.server 8000 --bind 0.0.0.0     # from the repo root
# open /tools/motiongen/web_preview/index.html
```

It plays the same WebP frames the APK uses plus the interactive anatomy map.
