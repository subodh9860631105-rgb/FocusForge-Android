"""
Exercise motion library: every demo shown in the app is authored here as a short
keyframed loop and baked to WebP frames by `bake.py`.

Coordinates are absolute canvas positions (0..1). Hands/ankles are IK targets, so
poses read like real coaching cues ("hands travel from the chest to lockout")
instead of joint angles.
"""

from mannequin import P, BONES

T = BONES["torso"]


def E(id, view="side", frames=None, base=None, gear=None, gear_fn=None,
      glow=(), tags=None, mirror=False):
    b = P(view=view)
    if base:
        b.update(base)
    return dict(id=id, view=view, base=b, frames=frames or [], gear=gear or [],
                gear_fn=gear_fn, glow=glow, tags=tags or [], mirror=mirror)


# --------------------------------------------------------------------------
# helpers that build repeated gear from skeleton joints
# --------------------------------------------------------------------------
def bar_at_hands(length=0.46, lift=0.0):
    def fn(sk, pose):
        h1 = sk["hand_n"]
        h2 = sk["hand_f"]
        cx = (h1[0] + h2[0]) / 2
        cy = min(h1[1], h2[1]) + lift
        return [("bar", "front", cx - length / 2, cy, cx + length / 2, cy)]
    return fn


def db_at_hands(size=1.0):
    def fn(sk, pose):
        out = []
        for side in ("n", "f"):
            h = sk["hand_" + side]
            out.append(("dumbbell", "front", h[0], h[1], 90))
        return out
    return fn


def bar_on_back(lift=-0.012):
    def fn(sk, pose):
        lean = sk["lean"]
        sh = sk["shoulder"]
        cx = sh[0]
        cy = sh[1] + lift
        return [("bar", "front", cx - 0.25, cy, cx + 0.25, cy)]
    return fn


# ==========================================================================
# SIDE-VIEW STRENGTH MOVEMENTS
# ==========================================================================
EXERCISES = []


def add(e):
    EXERCISES.append(e)
    return e


# 1 -------------------------------------------------------- bench press
add(E("bench_bb", "side",
      base=dict(root=(0.575, 0.605), lean=88.0, spread=1.0,
                ankle_n=(0.815, 0.905), ankle_f=(0.805, 0.895),
                foot_n=95, foot_f=95,
                hand_n=(0.315, 0.395), hand_f=(0.345, 0.385),
                bend_n=-1.0, bend_f=-1.0),
      frames=[
          (0.00, dict(hand_n=(0.315, 0.395), hand_f=(0.345, 0.385))),
          (0.42, dict(hand_n=(0.315, 0.300), hand_f=(0.345, 0.292))),
          (0.55, dict(hand_n=(0.315, 0.296), hand_f=(0.345, 0.288))),
          (1.00, dict(hand_n=(0.315, 0.395), hand_f=(0.345, 0.385))),
      ],
      gear=[("bench", "back", 0.635)],
      gear_fn=bar_at_hands(0.34),
      glow=[("chest",), ]))

# 2 -------------------------------------------------------- bent-over row
add(E("row_bb", "side",
      base=dict(root=(0.545, 0.565), lean=-58.0,
                ankle_n=(0.585, 0.915), ankle_f=(0.565, 0.905),
                hand_n=(0.365, 0.640), hand_f=(0.395, 0.632),
                bend_n=-1.0, bend_f=-1.0),
      frames=[
          (0.00, dict(hand_n=(0.365, 0.640), hand_f=(0.395, 0.632))),
          (0.40, dict(hand_n=(0.400, 0.520), hand_f=(0.428, 0.514))),
          (0.52, dict(hand_n=(0.402, 0.514), hand_f=(0.430, 0.508))),
          (1.00, dict(hand_n=(0.365, 0.640), hand_f=(0.395, 0.632))),
      ],
      gear=[("floor", "back", 0.955)],
      gear_fn=bar_at_hands(0.36),
      glow=[("back",), ("arm_n", 0.2, 0.8)]))

# 3 -------------------------------------------------------- dumbbell curl
add(E("curl_db", "side",
      base=dict(root=(0.520, 0.500), lean=2.0,
                ankle_n=(0.545, 0.930), ankle_f=(0.525, 0.920),
                hand_n=(0.545, 0.640), hand_f=(0.575, 0.632),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(hand_n=(0.545, 0.640), hand_f=(0.575, 0.632))),
          (0.40, dict(hand_n=(0.630, 0.470), hand_f=(0.660, 0.462))),
          (0.52, dict(hand_n=(0.632, 0.462), hand_f=(0.662, 0.454))),
          (1.00, dict(hand_n=(0.545, 0.640), hand_f=(0.575, 0.632))),
      ],
      gear=[("floor", "back", 0.955)],
      gear_fn=db_at_hands(),
      glow=[("arm_n", 0.15, 0.85), ("arm_f", 0.15, 0.85, 0.6)]))

# 4 -------------------------------------------------------- barbell squat
add(E("squat_smith", "side",
      base=dict(root=(0.500, 0.500), lean=-4.0,
                ankle_n=(0.545, 0.925), foot_n=90,
                hand_n=(0.430, 0.215), hand_f=(0.455, 0.225),
                bend_n=-1.0, bend_f=-1.0, kbend_n=1.0),
      frames=[
          (0.00, dict(root=(0.500, 0.500), lean=-4.0, ankle_n=(0.545, 0.925),
                      hand_n=(0.430, 0.215), hand_f=(0.455, 0.225))),
          (0.40, dict(root=(0.465, 0.680), lean=-26.0, ankle_n=(0.560, 0.925),
                      hand_n=(0.395, 0.395), hand_f=(0.420, 0.405))),
          (0.52, dict(root=(0.465, 0.690), lean=-28.0, ankle_n=(0.560, 0.925),
                      hand_n=(0.395, 0.405), hand_f=(0.420, 0.415))),
          (1.00, dict(root=(0.500, 0.500), lean=-4.0, ankle_n=(0.545, 0.925),
                      hand_n=(0.430, 0.215), hand_f=(0.455, 0.225))),
      ],
      gear=[("floor", "back", 0.955)],
      gear_fn=bar_on_back(0.010),
      glow=[("thigh_n", 0.05, 0.75), ("glute",)]))

# 5 -------------------------------------------------------- lat pulldown
add(E("lat_cable", "side",
      base=dict(root=(0.520, 0.585), lean=8.0,
                ankle_n=(0.610, 0.930), ankle_f=(0.600, 0.920),
                hand_n=(0.480, 0.230), hand_f=(0.510, 0.238),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(hand_n=(0.480, 0.215), hand_f=(0.510, 0.223))),
          (0.42, dict(hand_n=(0.520, 0.400), hand_f=(0.548, 0.408))),
          (0.54, dict(hand_n=(0.524, 0.412), hand_f=(0.552, 0.420))),
          (1.00, dict(hand_n=(0.480, 0.215), hand_f=(0.510, 0.223))),
      ],
      gear=[("stack", "back", 0.100, 0.560),
            ("cable", "back", 0.180, 0.120, 0.180, 0.470, 0.180)],
      gear_fn=bar_at_hands(0.30),
      glow=[("back",), ("arm_n", 0.3, 0.9)]))

# 6 -------------------------------------------------------- back extension
add(E("back_ext", "side",
      base=dict(root=(0.520, 0.700), lean=88.0,
                ankle_n=(0.760, 0.780), ankle_f=(0.750, 0.770), foot_n=80, foot_f=80,
                hand_n=(0.330, 0.620), hand_f=(0.360, 0.615),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(root=(0.520, 0.700), lean=74.0,
                      hand_n=(0.360, 0.700), hand_f=(0.390, 0.695))),
          (0.45, dict(root=(0.520, 0.700), lean=100.0,
                      hand_n=(0.300, 0.530), hand_f=(0.330, 0.525))),
          (0.58, dict(root=(0.520, 0.700), lean=101.0,
                      hand_n=(0.298, 0.526), hand_f=(0.328, 0.521))),
          (1.00, dict(root=(0.520, 0.700), lean=74.0,
                      hand_n=(0.360, 0.700), hand_f=(0.390, 0.695))),
      ],
      gear=[("bench", "back", 0.720)],
      glow=[("back",), ("glute", 1.0)]))

# 7 -------------------------------------------------------- incline press
add(E("incline_db", "side",
      base=dict(root=(0.585, 0.640), lean=52.0,
                ankle_n=(0.700, 0.930), ankle_f=(0.690, 0.920), foot_n=92, foot_f=92,
                hand_n=(0.415, 0.400), hand_f=(0.445, 0.392),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(hand_n=(0.415, 0.400), hand_f=(0.445, 0.392))),
          (0.42, dict(hand_n=(0.430, 0.270), hand_f=(0.460, 0.262))),
          (0.55, dict(hand_n=(0.432, 0.264), hand_f=(0.462, 0.256))),
          (1.00, dict(hand_n=(0.415, 0.400), hand_f=(0.445, 0.392))),
      ],
      gear=[("incline", "back", 0.760, 0.930, -62.0)],
      gear_fn=db_at_hands(),
      glow=[("chest",), ("delts", 1.0)]))

# 8 -------------------------------------------------------- push-up
add(E("pushup_bw", "side",
      base=dict(root=(0.585, 0.615), lean=-72.0,
                ankle_n=(0.790, 0.700), ankle_f=(0.780, 0.692), foot_n=88, foot_f=88,
                hand_n=(0.330, 0.900), hand_f=(0.360, 0.894),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(root=(0.585, 0.640), lean=-70.0,
                      ankle_n=(0.790, 0.720), hand_n=(0.330, 0.900), hand_f=(0.360, 0.894))),
          (0.42, dict(root=(0.585, 0.560), lean=-72.0,
                      ankle_n=(0.790, 0.650), hand_n=(0.330, 0.900), hand_f=(0.360, 0.894))),
          (0.55, dict(root=(0.585, 0.556), lean=-72.0,
                      ankle_n=(0.790, 0.648), hand_n=(0.330, 0.900), hand_f=(0.360, 0.894))),
          (1.00, dict(root=(0.585, 0.640), lean=-70.0,
                      ankle_n=(0.790, 0.720), hand_n=(0.330, 0.900), hand_f=(0.360, 0.894))),
      ],
      gear=[("floor", "back", 0.930)],
      glow=[("chest",), ("abs", 0.9)]))

# 9 -------------------------------------------------------- cable fly
add(E("cable_fly", "side",
      base=dict(root=(0.520, 0.510), lean=-6.0,
                ankle_n=(0.545, 0.925), ankle_f=(0.525, 0.915),
                hand_n=(0.330, 0.470), hand_f=(0.360, 0.462),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(hand_n=(0.300, 0.450), hand_f=(0.330, 0.442))),
          (0.45, dict(hand_n=(0.500, 0.400), hand_f=(0.528, 0.394))),
          (0.57, dict(hand_n=(0.505, 0.398), hand_f=(0.533, 0.392))),
          (1.00, dict(hand_n=(0.300, 0.450), hand_f=(0.330, 0.442))),
      ],
      gear=[("cable", "back", 0.120, 0.240, 0.120, 0.470, 0.120),
            ("stack", "back", 0.090, 0.560)],
      glow=[("chest",), ("delts", 0.8)]))

# 10 ------------------------------------------------------- tricep pushdown
add(E("tricep_cable", "side",
      base=dict(root=(0.520, 0.505), lean=-8.0,
                ankle_n=(0.545, 0.925), ankle_f=(0.525, 0.915),
                hand_n=(0.560, 0.470), hand_f=(0.588, 0.462),
                bend_n=-1.0, bend_f=-1.0),
      frames=[
          (0.00, dict(hand_n=(0.500, 0.470), hand_f=(0.528, 0.462))),
          (0.40, dict(hand_n=(0.600, 0.640), hand_f=(0.626, 0.634))),
          (0.52, dict(hand_n=(0.604, 0.646), hand_f=(0.630, 0.640))),
          (1.00, dict(hand_n=(0.500, 0.470), hand_f=(0.528, 0.462))),
      ],
      gear=[("cable", "back", 0.150, 0.140, 0.150, 0.300, 0.150),
            ("stack", "back", 0.080, 0.560)],
      gear_fn=db_at_hands(),
      glow=[("fore_n", 0.1, 0.9), ("fore_f", 0.1, 0.9, 0.6)]))

# 11 ------------------------------------------------------- deadlift
add(E("deadlift_bb", "side",
      base=dict(root=(0.545, 0.560), lean=-60.0,
                ankle_n=(0.590, 0.915), ankle_f=(0.570, 0.905),
                hand_n=(0.400, 0.690), hand_f=(0.430, 0.684),
                bend_n=-1.0, bend_f=-1.0),
      frames=[
          (0.00, dict(root=(0.560, 0.635), lean=-64.0, ankle_n=(0.600, 0.915),
                      hand_n=(0.390, 0.700), hand_f=(0.420, 0.694))),
          (0.38, dict(root=(0.510, 0.520), lean=-16.0, ankle_n=(0.560, 0.915),
                      hand_n=(0.520, 0.560), hand_f=(0.548, 0.554))),
          (0.50, dict(root=(0.510, 0.505), lean=-6.0, ankle_n=(0.560, 0.915),
                      hand_n=(0.540, 0.548), hand_f=(0.568, 0.542))),
          (0.70, dict(root=(0.545, 0.560), lean=-60.0, ankle_n=(0.590, 0.915),
                      hand_n=(0.400, 0.690), hand_f=(0.430, 0.684))),
          (1.00, dict(root=(0.560, 0.635), lean=-64.0, ankle_n=(0.600, 0.915),
                      hand_n=(0.390, 0.700), hand_f=(0.420, 0.694))),
      ],
      gear=[("floor", "back", 0.955)],
      gear_fn=bar_at_hands(0.42),
      glow=[("back",), ("glute",), ("thigh_n", 0.0, 0.6, 0.7)]))

# 12 ------------------------------------------------------- lunge
add(E("lunge_db", "side",
      base=dict(root=(0.520, 0.500), lean=-2.0,
                ankle_n=(0.575, 0.925), ankle_f=(0.430, 0.905),
                hand_n=(0.545, 0.640), hand_f=(0.575, 0.632),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(root=(0.520, 0.500), lean=-2.0,
                      ankle_n=(0.575, 0.925), ankle_f=(0.430, 0.905),
                      hand_n=(0.545, 0.640), hand_f=(0.575, 0.632))),
          (0.42, dict(root=(0.520, 0.660), lean=-6.0,
                      ankle_n=(0.570, 0.925), ankle_f=(0.370, 0.900),
                      hand_n=(0.545, 0.790), hand_f=(0.575, 0.782))),
          (0.54, dict(root=(0.520, 0.672), lean=-7.0,
                      ankle_n=(0.570, 0.925), ankle_f=(0.365, 0.898),
                      hand_n=(0.545, 0.800), hand_f=(0.575, 0.792))),
          (1.00, dict(root=(0.520, 0.500), lean=-2.0,
                      ankle_n=(0.575, 0.925), ankle_f=(0.430, 0.905),
                      hand_n=(0.545, 0.640), hand_f=(0.575, 0.632))),
      ],
      gear=[("floor", "back", 0.955)],
      gear_fn=db_at_hands(),
      glow=[("thigh_n", 0.0, 0.8), ("glute",)]))

# 13 ------------------------------------------------------- ab wheel
add(E("ab_wheel", "side",
      base=dict(root=(0.560, 0.560), lean=-46.0,
                ankle_n=(0.700, 0.870), ankle_f=(0.690, 0.862), foot_n=120, foot_f=120,
                hand_n=(0.390, 0.780), hand_f=(0.420, 0.774),
                bend_n=1.0, bend_f=1.0, kbend_n=-1.0),
      frames=[
          (0.00, dict(root=(0.585, 0.545), lean=-20.0,
                      hand_n=(0.415, 0.700), hand_f=(0.445, 0.694))),
          (0.42, dict(root=(0.520, 0.590), lean=-78.0,
                      hand_n=(0.300, 0.880), hand_f=(0.330, 0.874))),
          (0.54, dict(root=(0.516, 0.594), lean=-80.0,
                      hand_n=(0.295, 0.884), hand_f=(0.325, 0.878))),
          (1.00, dict(root=(0.585, 0.545), lean=-20.0,
                      hand_n=(0.415, 0.700), hand_f=(0.445, 0.694))),
      ],
      gear=[("mat", "back", 0.930)],
      gear_fn=lambda sk, pose: [("wheel", "front", sk["hand_n"][0], sk["hand_n"][1])]
      if False else [("wheel", "front", (sk["hand_n"][0] + sk["hand_f"][0]) / 2,
                      (sk["hand_n"][1] + sk["hand_f"][1]) / 2)],
      glow=[("abs",)]))

# 14 ------------------------------------------------------- sit-up + twist
add(E("situp_twist", "side",
      base=dict(root=(0.545, 0.760), lean=86.0,
                ankle_n=(0.700, 0.900), ankle_f=(0.690, 0.890), foot_n=80, foot_f=80,
                hand_n=(0.330, 0.700), hand_f=(0.360, 0.695),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(root=(0.545, 0.760), lean=86.0,
                      hand_n=(0.330, 0.700), hand_f=(0.360, 0.695))),
          (0.45, dict(root=(0.545, 0.760), lean=140.0,
                      hand_n=(0.400, 0.600), hand_f=(0.430, 0.596))),
          (0.58, dict(root=(0.545, 0.760), lean=142.0,
                      hand_n=(0.404, 0.598), hand_f=(0.434, 0.594))),
          (1.00, dict(root=(0.545, 0.760), lean=86.0,
                      hand_n=(0.330, 0.700), hand_f=(0.360, 0.695))),
      ],
      gear=[("mat", "back", 0.930)],
      glow=[("abs",)]))

# 15 ------------------------------------------------------- side plank
add(E("side_plank", "front",
      base=dict(root=(0.590, 0.640), lean=58.0, spread=0.95,
                ankle_n=(0.840, 0.850), ankle_f=(0.815, 0.845), foot_n=58, foot_f=58,
                hand_n=(0.215, 0.420), hand_f=(0.330, 0.885)),
      frames=[
          (0.00, dict(root=(0.590, 0.640), lean=58.0,
                      ankle_n=(0.840, 0.850), ankle_f=(0.815, 0.845),
                      hand_n=(0.215, 0.420), hand_f=(0.330, 0.885))),
          (0.45, dict(root=(0.592, 0.628), lean=58.0,
                      ankle_n=(0.825, 0.815), ankle_f=(0.800, 0.810),
                      hand_n=(0.360, 0.560), hand_f=(0.330, 0.885))),
          (0.57, dict(root=(0.592, 0.626), lean=58.0,
                      ankle_n=(0.823, 0.812), ankle_f=(0.798, 0.807),
                      hand_n=(0.365, 0.566), hand_f=(0.330, 0.885))),
          (1.00, dict(root=(0.590, 0.640), lean=58.0,
                      ankle_n=(0.840, 0.850), ankle_f=(0.815, 0.845),
                      hand_n=(0.215, 0.420), hand_f=(0.330, 0.885))),
      ],
      gear=[("mat", "back", 0.905)],
      glow=[("abs",), ("shin_n", 0.1, 0.7, 0.6)]))

# 16 ------------------------------------------------------- pull-up
add(E("pullup_bw", "side",
      base=dict(root=(0.525, 0.640), lean=2.0,
                ankle_n=(0.560, 0.900), ankle_f=(0.540, 0.890), foot_n=110, foot_f=110,
                hand_n=(0.470, 0.230), hand_f=(0.500, 0.238),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(root=(0.525, 0.700), hand_n=(0.470, 0.230), hand_f=(0.500, 0.238),
                      ankle_n=(0.560, 0.955), ankle_f=(0.540, 0.945))),
          (0.42, dict(root=(0.525, 0.545), hand_n=(0.470, 0.220), hand_f=(0.500, 0.228),
                      ankle_n=(0.560, 0.800), ankle_f=(0.540, 0.790))),
          (0.54, dict(root=(0.525, 0.535), hand_n=(0.470, 0.218), hand_f=(0.500, 0.226),
                      ankle_n=(0.560, 0.790), ankle_f=(0.540, 0.780))),
          (1.00, dict(root=(0.525, 0.700), hand_n=(0.470, 0.230), hand_f=(0.500, 0.238),
                      ankle_n=(0.560, 0.955), ankle_f=(0.540, 0.945))),
      ],
      gear_fn=lambda sk, pose: [("bar", "back", 0.220, 0.150, 0.780, 0.150)],
      glow=[("back",), ("arm_n", 0.2, 0.9)]))

# 17 ------------------------------------------------------- glute bridge
add(E("glute_bridge", "side",
      base=dict(root=(0.560, 0.760), lean=84.0,
                ankle_n=(0.720, 0.905), ankle_f=(0.710, 0.895), foot_n=84, foot_f=84,
                hand_n=(0.330, 0.880), hand_f=(0.360, 0.876),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(root=(0.560, 0.800), lean=84.0,
                      hand_n=(0.330, 0.905), hand_f=(0.360, 0.900))),
          (0.42, dict(root=(0.560, 0.700), lean=88.0,
                      hand_n=(0.330, 0.905), hand_f=(0.360, 0.900))),
          (0.55, dict(root=(0.560, 0.696), lean=88.0,
                      hand_n=(0.330, 0.905), hand_f=(0.360, 0.900))),
          (1.00, dict(root=(0.560, 0.800), lean=84.0,
                      hand_n=(0.330, 0.905), hand_f=(0.360, 0.900))),
      ],
      gear=[("mat", "back", 0.920)],
      glow=[("glute",), ("thigh_n", 0.2, 0.9, 0.7)]))

# 18 ------------------------------------------------------- calf raise
add(E("calf_raise", "side",
      base=dict(root=(0.520, 0.500), lean=0.0,
                ankle_n=(0.545, 0.925), foot_n=90,
                hand_n=(0.545, 0.640), hand_f=(0.575, 0.632),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(root=(0.520, 0.510), ankle_n=(0.545, 0.930), foot_n=96,
                      hand_n=(0.545, 0.648), hand_f=(0.575, 0.640))),
          (0.42, dict(root=(0.520, 0.430), ankle_n=(0.545, 0.920), foot_n=128,
                      hand_n=(0.545, 0.570), hand_f=(0.575, 0.562))),
          (0.54, dict(root=(0.520, 0.426), ankle_n=(0.545, 0.918), foot_n=132,
                      hand_n=(0.545, 0.566), hand_f=(0.575, 0.558))),
          (1.00, dict(root=(0.520, 0.510), ankle_n=(0.545, 0.930), foot_n=96,
                      hand_n=(0.545, 0.648), hand_f=(0.575, 0.640))),
      ],
      gear=[("floor", "back", 0.955)],
      glow=[("shin_n", 0.1, 0.9)]))

# 19 ------------------------------------------------------- plank hold
add(E("plank_hold", "side",
      base=dict(root=(0.585, 0.620), lean=-70.0,
                ankle_n=(0.800, 0.740), foot_n=88,
                hand_n=(0.360, 0.900), hand_f=(0.390, 0.894),
                bend_n=1.0, bend_f=1.0),
      frames=[
          (0.00, dict(root=(0.585, 0.622), lean=-70.0, hand_n=(0.360, 0.900))),
          (0.50, dict(root=(0.585, 0.612), lean=-71.0, hand_n=(0.360, 0.898))),
          (1.00, dict(root=(0.585, 0.622), lean=-70.0, hand_n=(0.360, 0.900))),
      ],
      gear=[("mat", "back", 0.925)],
      glow=[("abs",), ("back", 0.6)]))

# 20 ------------------------------------------------------- hammer curl
add(E("hammer_curl", "side",
      base=dict(root=(0.520, 0.500), lean=1.0,
                ankle_n=(0.545, 0.930), ankle_f=(0.525, 0.920),
                hand_n=(0.560, 0.640), hand_f=(0.590, 0.632)),
      frames=[
          (0.00, dict(hand_n=(0.560, 0.640), hand_f=(0.590, 0.632))),
          (0.42, dict(hand_n=(0.640, 0.500), hand_f=(0.670, 0.492))),
          (0.54, dict(hand_n=(0.642, 0.492), hand_f=(0.672, 0.484))),
          (1.00, dict(hand_n=(0.560, 0.640), hand_f=(0.590, 0.632))),
      ],
      gear=[("floor", "back", 0.955)],
      gear_fn=db_at_hands(),
      glow=[("arm_n", 0.15, 0.85), ("arm_f", 0.15, 0.85)]))

# 21 ------------------------------------------------------- overhead press
add(E("shoulder_db", "front",
      base=dict(root=(0.500, 0.500), spread=1.0,
                ankle_n=(0.575, 0.920), ankle_f=(0.425, 0.920), foot_n=22, foot_f=-22,
                hand_n=(0.630, 0.330), hand_f=(0.370, 0.330)),
      frames=[
          (0.00, dict(hand_n=(0.640, 0.300), hand_f=(0.360, 0.300))),
          (0.40, dict(hand_n=(0.620, 0.170), hand_f=(0.380, 0.170))),
          (0.52, dict(hand_n=(0.618, 0.164), hand_f=(0.382, 0.164))),
          (1.00, dict(hand_n=(0.640, 0.300), hand_f=(0.360, 0.300))),
      ],
      gear=[("floor", "back", 0.955)],
      gear_fn=db_at_hands(),
      glow=[("delts",), ("arm_n", 0.2, 0.8, 0.5)]))

# 22 ------------------------------------------------------- lateral raise
add(E("lat_raise_db", "front",
      base=dict(root=(0.500, 0.500), spread=1.0,
                ankle_n=(0.575, 0.920), ankle_f=(0.425, 0.920), foot_n=22, foot_f=-22,
                hand_n=(0.600, 0.640), hand_f=(0.400, 0.640)),
      frames=[
          (0.00, dict(hand_n=(0.600, 0.650), hand_f=(0.400, 0.650))),
          (0.42, dict(hand_n=(0.770, 0.440), hand_f=(0.230, 0.440))),
          (0.54, dict(hand_n=(0.774, 0.434), hand_f=(0.226, 0.434))),
          (1.00, dict(hand_n=(0.600, 0.650), hand_f=(0.400, 0.650))),
      ],
      gear=[("floor", "back", 0.955)],
      gear_fn=db_at_hands(),
      glow=[("delts",)]))


# ==========================================================================
def pose_keyframes(ex):
    """Return [(t, merged pose dict)] with view/base baked in."""
    out = []
    for (t, ov) in ex["frames"]:
        p = dict(ex["base"])
        p.update(ov)
        p["view"] = ex["view"]
        if ex["gear"]:
            p["gear"] = ex["gear"]
        if ex["gear_fn"]:
            p["gear_fn"] = ex["gear_fn"]
        if ex["glow"]:
            p["glow"] = ex["glow"]
        out.append((t, p))
    return out


def sample(ex, u):
    """Interpolate the pose at loop position u in [0,1)."""
    kfs = pose_keyframes(ex)
    u = u % 1.0
    for i in range(len(kfs)):
        t0, p0 = kfs[i]
        t1, p1 = kfs[(i + 1) % len(kfs)]
        if i == len(kfs) - 1:
            t1 = 1.0
        if t0 <= u <= t1 or (i == len(kfs) - 1 and u >= t0):
            span = max(t1 - t0, 1e-6)
            k = (u - t0) / span
            # smooth ease in/out
            k = k * k * (3 - 2 * k)
            return lerp_pose(p0, p1, k)
    return kfs[0][1]


def lerp_pose(a, b, k):
    out = dict(a)
    for key, bv in b.items():
        av = a.get(key)
        if isinstance(av, (int, float)) and isinstance(bv, (int, float)):
            out[key] = av + (bv - av) * k
        elif (isinstance(av, tuple) and isinstance(bv, tuple)
              and len(av) == len(bv) == 2
              and all(isinstance(x, (int, float)) for x in av + bv)):
            out[key] = (av[0] + (bv[0] - av[0]) * k, av[1] + (bv[1] - av[1]) * k)
        else:
            out[key] = av if k < 0.5 else bv
    return out
