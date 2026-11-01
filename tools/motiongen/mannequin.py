"""
FocusForge 3D Mannequin Renderer
================================
Procedurally bakes the animated exercise demonstrations that ship inside the app.

Bodies are shaded as if lit by a soft key light from the upper left plus a cool
rim light from the right, using cylindrical normals. Limbs are lofted tubes with
per-segment radius profiles, so the result reads as a lit 3D athlete mannequin
rather than a stick figure.

Units are normalised 0..1 canvas coordinates. Angles are screen-space degrees
where 0 = straight down and positive = clockwise (i.e. 90 = to the right).
"""

import math
import numpy as np
from PIL import Image, ImageDraw

# ------------------------------------------------------------------ palette
BODY_LIT = (0.880, 0.912, 0.962)
BODY_MID = (0.596, 0.672, 0.784)
BODY_DK = (0.145, 0.200, 0.310)
RIM = (0.400, 0.760, 1.000)
FAR = 0.66                     # brightness multiplier for the far side
HAIR = (0.090, 0.118, 0.180)
GLOW = (0.960, 0.230, 0.330)
GLOW_HOT = (1.000, 0.650, 0.330)
STEEL_LIT = (0.780, 0.910, 1.000)
STEEL = (0.235, 0.545, 0.780)
STEEL_DK = (0.070, 0.165, 0.275)
IRON_LIT = (0.520, 0.590, 0.680)
IRON = (0.155, 0.205, 0.290)
IRON_DK = (0.035, 0.055, 0.095)
MAT_LIT = (0.285, 0.370, 0.510)
MAT = (0.105, 0.150, 0.235)
SUIT_LIT = (0.330, 0.400, 0.540)
SUIT = (0.150, 0.195, 0.300)
SUIT_DK = (0.040, 0.060, 0.105)

LIGHT = np.array([-0.46, -0.66, 0.60])
LIGHT /= np.linalg.norm(LIGHT)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mixp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


# ------------------------------------------------------------------ skeleton
BONES = dict(
    torso=0.300,
    neck=0.052,
    head_r=0.060,
    upper_arm=0.172,
    fore_arm=0.158,
    hand=0.050,
    thigh=0.216,
    shin=0.206,
    foot=0.090,
)

DEFAULTS = dict(
    root=(0.50, 0.512),
    lean=0.0,
    head=0.0,
    gaze=0.0,
    view="side",
    arm_n=(6.0, 10.0),
    arm_f=(2.0, 6.0),
    leg_n=(2.0, 1.0),
    leg_f=(-2.0, -1.0),
    foot_n=90.0,
    foot_f=90.0,
    hand_n=0.0,
    hand_f=0.0,
    spread=1.0,
    glow=(),
    gear=(),
    near_side="n",
    ground=None,
)


def P(**kw):
    p = dict(DEFAULTS)
    p.update(kw)
    return p


def at(origin, length, angle_deg):
    a = math.radians(angle_deg)
    return (origin[0] + math.sin(a) * length, origin[1] + math.cos(a) * length)


def ik2(origin, target, l1, l2, bend=1.0):
    """Two-bone IK. Returns (joint, angle1, angle2). bend=+1 bulges clockwise."""
    dx, dy = target[0] - origin[0], target[1] - origin[1]
    d = math.hypot(dx, dy)
    dmax = (l1 + l2) * 0.999
    if d > dmax:
        dx *= dmax / d
        dy *= dmax / d
        d = dmax
    d = max(d, abs(l1 - l2) + 1e-4)
    base = math.degrees(math.atan2(dx, dy))
    cosA = (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)
    A = math.degrees(math.acos(max(-1.0, min(1.0, cosA))))
    a1 = base + bend * A
    joint = at(origin, l1, a1)
    end = (origin[0] + dx, origin[1] + dy)
    a2 = math.degrees(math.atan2(end[0] - joint[0], end[1] - joint[1]))
    return joint, a1, a2


def skeleton(pose):
    lean = pose["lean"]
    root = pose["root"]
    up = (root[0] - math.sin(math.radians(lean)) * BONES["torso"],
          root[1] - math.cos(math.radians(lean)) * BONES["torso"])
    neck_ang = 180.0 - (lean + pose["head"])
    neck_top = at(up, BONES["neck"], neck_ang)
    head = at(neck_top, BONES["head_r"] * 0.82, neck_ang + pose["gaze"])

    side_view = pose["view"] == "side"
    sh_off = (0.012 if side_view else 0.076) * pose["spread"]
    hip_off = (0.008 if side_view else 0.048) * pose["spread"]
    # arms hang from the deltoids, slightly out from the spine
    sign = {"n": 1.0, "f": -1.0}

    sk = dict(root=root, shoulder=up, neck_top=neck_top, head=head, lean=lean,
              side_view=side_view, sh_off=sh_off, hip_off=hip_off)
    for side in ("n", "f"):
        sg = sign[side]
        sh = (up[0] + sg * sh_off, up[1])
        hip = (root[0] + sg * hip_off, root[1])

        tgt = pose.get("hand_" + side)
        if sk["side_view"]:
            vbend = -1.0
        else:
            vbend = -1.0 if side == "n" else 1.0
        if isinstance(tgt, (tuple, list)):
            elbow, a1, a2 = ik2(sh, tgt, BONES["upper_arm"], BONES["fore_arm"],
                                pose.get("bend_" + side, vbend))
        else:
            a1, a2 = pose["arm_" + side]
            elbow = at(sh, BONES["upper_arm"], a1)
        wrist = at(elbow, BONES["fore_arm"], a2)
        hand = at(wrist, BONES["hand"] * 0.92, a2 + (0 if isinstance(tgt, (tuple, list))
                                                    else pose["hand_" + side]))
        if isinstance(tgt, (tuple, list)):
            hand_tgt = (tgt[0], tgt[1])
            hand = hand_tgt if math.hypot(wrist[0]-tgt[0], wrist[1]-tgt[1]) < 0.02 else hand

        tgt = pose.get("ankle_" + side)
        if isinstance(tgt, (tuple, list)):
            if sk["side_view"]:
                kb = 1.0
            else:
                kb = 1.0 if side == "n" else -1.0
            knee, t1, t2 = ik2(hip, tgt, BONES["thigh"], BONES["shin"],
                               pose.get("kbend_" + side, kb))
            ankle = (tgt[0], tgt[1])
        else:
            t1, t2 = pose["leg_" + side]
            knee = at(hip, BONES["thigh"], t1)
            ankle = at(knee, BONES["shin"], t2)
        toe = at(ankle, BONES["foot"], pose["foot_" + side])

        sk["sh_" + side] = sh
        sk["hip_" + side] = hip
        sk["elbow_" + side] = elbow
        sk["wrist_" + side] = wrist
        sk["hand_" + side] = hand
        sk["knee_" + side] = knee
        sk["ankle_" + side] = ankle
        sk["toe_" + side] = toe
    return sk


# ------------------------------------------------------------------ canvas
class Canvas:
    def __init__(self, size=512, ss=2):
        self.S = size * ss
        self.rgb = np.zeros((self.S, self.S, 3), dtype=np.float32)
        self.a = np.zeros((self.S, self.S), dtype=np.float32)

    def grid(self, x0, y0, x1, y1):
        X, Y = np.meshgrid(np.arange(x0, x1, dtype=np.float32) + 0.5,
                           np.arange(y0, y1, dtype=np.float32) + 0.5)
        return X, Y

    def blend(self, x0, y0, rgb, alpha):
        H, W = self.a.shape
        h, w = alpha.shape
        x1, y1 = x0 + w, y0 + h
        cx0, cy0 = max(0, x0), max(0, y0)
        cx1, cy1 = min(W, x1), min(H, y1)
        if cx1 <= cx0 or cy1 <= cy0:
            return
        ax0, ay0 = cx0 - x0, cy0 - y0
        a = alpha[ay0:ay0 + (cy1 - cy0), ax0:ax0 + (cx1 - cx0)]
        c = rgb
        if isinstance(rgb, np.ndarray) and rgb.ndim == 3:
            c = rgb[ay0:ay0 + (cy1 - cy0), ax0:ax0 + (cx1 - cx0)]
        dst = np.s_[cy0:cy1, cx0:cx1]
        self.rgb[dst] = self.rgb[dst] * (1 - a[..., None]) + np.asarray(c, np.float32) * a[..., None]
        self.a[dst] = self.a[dst] * (1 - a) + a

    # -------------------------------------------------------- shading core
    @staticmethod
    def shade(diff, s, near=True, tint=None, rim=True, out=None):
        lit = np.array(BODY_LIT); base = np.array(BODY_MID); dark = np.array(BODY_DK)
        if not near:
            lit, base, dark = lit * FAR, base * FAR, dark * 0.82
        if tint is not None:
            lit, base, dark = (np.array(t, dtype=np.float32) for t in tint)
        sd = smoothstep(0.0, 1.0, np.clip(diff, 0, 1) ** 0.85)
        col = dark[None, None, :] + (base[None, None, :] - dark[None, None, :]) * sd[..., None]
        hi = smoothstep(0.66, 1.0, np.clip(diff, 0, 1))
        col = col + (lit[None, None, :] - col) * (hi[..., None] * 0.72)
        if rim and out is not None:
            rw = smoothstep(0.74, 1.0, s) * smoothstep(0.05, 0.45, diff) * out
            col = col + (np.array(RIM)[None, None, :] - col) * rw[..., None]
        return col

    # -------------------------------------------------------- primitives
    def capsule(self, p1, p2, r1, r2, near=True, tint=None, rim=True, alpha_mul=1.0,
                rim_gain=0.55):
        """Lofted tube segment with rounded caps."""
        S = self.S
        x1, y1 = p1[0] * S, p1[1] * S
        x2, y2 = p2[0] * S, p2[1] * S
        r1 = max(r1, 1e-4) * S
        r2 = max(r2, 1e-4) * S
        pad = max(r1, r2) + 2
        bx0 = max(0, int(math.floor(min(x1, x2) - pad)))
        bx1 = min(S, int(math.ceil(max(x1, x2) + pad)))
        by0 = max(0, int(math.floor(min(y1, y2) - pad)))
        by1 = min(S, int(math.ceil(max(y1, y2) + pad)))
        if bx1 <= bx0 or by1 <= by0:
            return
        X, Y = self.grid(bx0, by0, bx1, by1)
        dx, dy = x2 - x1, y2 - y1
        L = math.hypot(dx, dy) + 1e-9
        ux, uy = dx / L, dy / L
        t = (X - x1) * ux + (Y - y1) * uy
        d = np.abs(-(X - x1) * uy + (Y - y1) * ux)
        tn = np.clip(t / L, 0.0, 1.0)
        rr = r1 + (r2 - r1) * tn
        s = np.clip(d / np.maximum(rr, 1e-6), 0, 1)
        beyond = np.maximum(0.0, np.maximum(-t, t - L))
        sd = np.where((t >= 0) & (t <= L), d - rr, np.sqrt(beyond ** 2 + d ** 2) - rr)
        cov = np.clip(0.5 - sd, 0.0, 1.0)
        z = np.sqrt(np.clip(1 - s ** 2, 0, 1))
        nx, ny = s * (-uy), s * (ux)
        diff = np.clip(nx * LIGHT[0] + ny * LIGHT[1] + z * LIGHT[2], 0, 1)
        col = self.shade(diff, s, near, tint, rim, rim_gain)
        self.blend(bx0, by0, col.astype(np.float32), (cov * alpha_mul).astype(np.float32))

    def tube(self, joints, radii, near=True, tint=None, rim_gain=0.55):
        """Chain of capsules through joints with per-joint radii (radius = half width)."""
        for i in range(len(joints) - 1):
            self.capsule(joints[i], joints[i + 1], radii[i], radii[i + 1],
                         near=near, tint=tint, rim_gain=rim_gain)

    def sphere(self, c, r, near=True, tint=None, squash=1.0, rim=True, alpha_mul=1.0):
        S = self.S
        cx, cy = c[0] * S, c[1] * S
        rx = max(r, 1e-4) * S
        ry = rx * squash
        pad = 2
        bx0 = max(0, int(math.floor(cx - rx - pad))); bx1 = min(S, int(math.ceil(cx + rx + pad)))
        by0 = max(0, int(math.floor(cy - ry - pad))); by1 = min(S, int(math.ceil(cy + ry + pad)))
        if bx1 <= bx0 or by1 <= by0:
            return
        X, Y = self.grid(bx0, by0, bx1, by1)
        nx = (X - cx) / rx
        ny = (Y - cy) / ry
        s = np.sqrt(nx ** 2 + ny ** 2)
        cov = np.clip((1.0 - s) * min(rx, ry) * 0.98, 0, 1)
        z = np.sqrt(np.clip(1 - s ** 2, 0, 1))
        diff = np.clip(nx * LIGHT[0] + ny * LIGHT[1] + z * LIGHT[2], 0, 1)
        col = self.shade(diff, s, near, tint, rim, 0.45)
        self.blend(bx0, by0, col.astype(np.float32), (cov * alpha_mul).astype(np.float32))

    SIDE_PROF = [
        # (t along spine, half-depth behind, half-depth in front)
        (0.00, 0.074, 0.058),
        (0.12, 0.081, 0.062),
        (0.28, 0.070, 0.059),
        (0.46, 0.070, 0.066),
        (0.62, 0.078, 0.076),
        (0.80, 0.086, 0.072),
        (0.94, 0.082, 0.055),
        (1.03, 0.058, 0.040),
    ]
    FRONT_PROF = [
        (0.00, 0.088, 0.088),
        (0.12, 0.100, 0.100),
        (0.28, 0.086, 0.086),
        (0.46, 0.092, 0.092),
        (0.62, 0.106, 0.106),
        (0.80, 0.122, 0.122),
        (0.94, 0.116, 0.116),
        (1.03, 0.070, 0.070),
    ]

    def torso(self, root, shoulder, lean, side_view, spread=1.0, near=True,
              tint=None, t0=-9.0, t1=9.0, rim_gain=0.42):
        S = self.S
        x0, y0 = root[0] * S, root[1] * S
        x1, y1 = shoulder[0] * S, shoulder[1] * S
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy) + 1e-9
        ux, uy = dx / L, dy / L
        px, py = -uy, ux

        prof = self.SIDE_PROF if side_view else self.FRONT_PROF
        back, front = [], []
        for (t, b, f) in prof:
            cx, cy = x0 + ux * t * L, y0 + uy * t * L
            back.append((cx + px * b * spread * S, cy + py * b * spread * S))
            front.append((cx - px * f * spread * S, cy - py * f * spread * S))
        poly = np.array(back + front[::-1], dtype=np.float32)

        bx0 = max(0, int(poly[:, 0].min()) - 2); bx1 = min(S, int(poly[:, 0].max()) + 2)
        by0 = max(0, int(poly[:, 1].min()) - 2); by1 = min(S, int(poly[:, 1].max()) + 2)
        if bx1 <= bx0 or by1 <= by0:
            return
        mask = polygon_mask(poly, bx0, by0, bx1, by1)

        X, Y = self.grid(bx0, by0, bx1, by1)
        t = ((X - x0) * ux + (Y - y0) * uy) / L
        if t0 > -8 or t1 < 8:
            mask = mask * smoothstep(t0 - 0.04, t0 + 0.04, t) * (1 - smoothstep(t1 - 0.04, t1 + 0.04, t))
        if mask.max() <= 0.002:
            return
        d = (-(X - x0) * uy + (Y - y0) * ux) / S
        tc = np.clip(t, 0.0, 1.03)
        ts = np.array([p[0] for p in prof])
        bt = np.array([p[1] for p in prof]) * spread
        ft = np.array([p[2] for p in prof]) * spread
        half = np.where(d < 0, np.interp(tc, ts, ft), np.interp(tc, ts, bt))
        s = np.clip(np.abs(d) / np.maximum(half, 1e-6), 0, 1)
        z = np.sqrt(np.clip(1 - s ** 2, 0, 1))
        nx = s * np.sign(d) * px
        ny = s * np.sign(d) * py
        diff = np.clip(nx * LIGHT[0] + ny * LIGHT[1] + z * LIGHT[2], 0, 1)
        pec = np.exp(-((t - 0.82) / 0.10) ** 2) * 0.07
        ab = (0.5 + 0.5 * np.cos(t * 46)) * smoothstep(0.28, 0.44, t) * (1 - smoothstep(0.58, 0.80, t)) * 0.085
        diff = np.clip(diff - pec - ab, 0, 1)
        col = self.shade(diff, s, near, tint, True, rim_gain)
        self.blend(bx0, by0, col.astype(np.float32), mask.astype(np.float32))

def polygon_mask(poly, bx0, by0, bx1, by1, ss=2):
    w, h = bx1 - bx0, by1 - by0
    img = Image.new("L", (w * ss, h * ss), 0)
    ImageDraw.Draw(img).polygon([((p[0] - bx0) * ss, (p[1] - by0) * ss) for p in poly], fill=255)
    img = img.resize((w, h), Image.LANCZOS)
    return np.asarray(img, dtype=np.float32) / 255.0


# ------------------------------------------------------------------ anatomy
RADII = dict(
    deltoid=0.040,
    upper_arm=0.032,
    bicep=0.036,
    elbow=0.026,
    fore_arm=0.031,
    wrist=0.019,
    hand=0.023,
    hip=0.062,
    thigh=0.070,
    thigh_lo=0.044,
    knee=0.036,
    calf=0.041,
    shin_lo=0.024,
    ankle=0.021,
    foot=0.024,
)


def draw_leg(cv, sk, side, near):
    hip, knee, ankle, toe = sk["hip_" + side], sk["knee_" + side], sk["ankle_" + side], sk["toe_" + side]
    R = RADII
    m = 1.0 if near else 0.94
    quad = mixp(hip, knee, 0.42)
    calf = mixp(knee, ankle, 0.30)
    cv.tube([hip, quad, knee], [R["hip"] * m, R["thigh"] * m, R["knee"] * m], near=near)
    cv.tube([knee, calf, ankle], [R["knee"] * m, R["calf"] * m, R["ankle"] * m], near=near)
    # shorts leg opening
    cv.tube([mixp(hip, knee, 0.02), mixp(hip, knee, 0.30)],
            [R["hip"] * m * 1.05, R["thigh"] * m * 1.10], near=near,
            tint=(SUIT_LIT, SUIT, SUIT_DK), rim_gain=0.35)
    # shoe (foreshortened when the athlete faces the camera)
    if sk["side_view"]:
        heel = (ankle[0] - 0.014, ankle[1] - 0.004)
        shoe_toe = (toe[0] + 0.008, toe[1] + 0.004)
        cv.tube([heel, shoe_toe], [R["foot"] * 1.00, R["foot"] * 0.66], near=near,
                tint=(SUIT_LIT, SUIT, SUIT_DK), rim_gain=0.22)
        cv.sphere(shoe_toe, R["foot"] * 0.66, near=near, tint=(SUIT_LIT, SUIT, SUIT_DK))
    else:
        inward = 0.010 if side == "f" else -0.010
        cv.tube([ankle, (ankle[0] + inward, ankle[1] + 0.026)],
                [R["ankle"] * 1.30, R["foot"] * 0.90], near=near,
                tint=(SUIT_LIT, SUIT, SUIT_DK), rim_gain=0.20)
    cv.capsule(ankle, (ankle[0], ankle[1] + 0.026), R["ankle"] * 1.18, R["ankle"] * 1.05,
               near=near, tint=(SUIT_LIT, SUIT, SUIT_DK), rim_gain=0.22)


def draw_arm(cv, sk, side, near):
    sh, elbow, wrist, hand = (sk["sh_" + side], sk["elbow_" + side],
                              sk["wrist_" + side], sk["hand_" + side])
    R = RADII
    m = 1.0 if near else 0.92
    cv.tube([sh, mixp(sh, elbow, 0.34), elbow],
            [R["deltoid"] * m, R["bicep"] * m, R["elbow"] * m], near=near)
    cv.tube([elbow, mixp(elbow, wrist, 0.28), wrist],
            [R["elbow"] * m, R["fore_arm"] * m, R["wrist"] * m], near=near)
    cv.sphere(hand, R["hand"] * m, near=near)


def draw_head(cv, sk, pose):
    c = sk["head"]
    r = BONES["head_r"]
    cv.sphere(c, r, squash=1.07, near=True)
    # face plane, brighter, set slightly toward the facing direction
    fwd = 0.0 if sk["side_view"] else 0.0
    cv.sphere((c[0] + fwd, c[1] + r * 0.20), r * 0.74, squash=1.0,
              tint=((0.930, 0.950, 0.985), (0.690, 0.760, 0.865), (0.250, 0.320, 0.430)))
    # hair cap
    S = cv.S
    cx, cy = c[0] * S, (c[1] - r * 0.26) * S
    rr = r * 0.98 * S
    pad = 2
    bx0 = max(0, int(cx - rr - pad)); bx1 = min(S, int(cx + rr + pad))
    by0 = max(0, int(cy - rr - pad)); by1 = min(S, int(cy + rr * 0.62 + pad))
    if bx1 > bx0 and by1 > by0:
        X, Y = cv.grid(bx0, by0, bx1, by1)
        nx, ny = (X - cx) / rr, (Y - cy) / rr
        s = np.sqrt(nx ** 2 + ny ** 2)
        cov = np.clip((1.0 - s) * rr * 0.98, 0, 1)
        z = np.sqrt(np.clip(1 - s ** 2, 0, 1))
        diff = np.clip(nx * LIGHT[0] + ny * LIGHT[1] + z * LIGHT[2], 0, 1)
        col = np.array(HAIR, dtype=np.float32)[None, None, :] * (0.55 + 0.90 * diff[..., None])
        cut = smoothstep(0.10, 0.72, ny) if pose["view"] == "back" else smoothstep(-0.05, 0.55, ny)
        cv.blend(bx0, by0, col.astype(np.float32), (cov * (1 - cut) * 0.97).astype(np.float32))


def draw_glow(cv, sk, pose):
    specs = pose.get("glow", ())
    if specs and isinstance(specs[0], str):
        specs = (specs,)
    if not specs:
        return
    glow = np.zeros((cv.S, cv.S), dtype=np.float32)
    hot = np.zeros((cv.S, cv.S), dtype=np.float32)

    def stamp_seg(p1, p2, w, inten):
        S = cv.S
        x1, y1 = p1[0] * S, p1[1] * S
        x2, y2 = p2[0] * S, p2[1] * S
        ww = max(w * S, 1.0)
        pad = ww * 2.6 + 4
        bx0 = max(0, int(min(x1, x2) - pad)); bx1 = min(S, int(max(x1, x2) + pad))
        by0 = max(0, int(min(y1, y2) - pad)); by1 = min(S, int(max(y1, y2) + pad))
        if bx1 <= bx0 or by1 <= by0:
            return
        X, Y = cv.grid(bx0, by0, bx1, by1)
        dx, dy = x2 - x1, y2 - y1
        L2 = dx * dx + dy * dy + 1e-9
        t = np.clip(((X - x1) * dx + (Y - y1) * dy) / L2, 0, 1)
        d = np.hypot(X - (x1 + dx * t), Y - (y1 + dy * t))
        core = np.clip(1 - d / (ww * 0.85), 0, 1) ** 1.2
        halo = np.exp(-(d / (ww * 1.7)) ** 2)
        sl = np.s_[by0:by1, bx0:bx1]
        glow[sl] = np.maximum(glow[sl], (halo * 0.9 * inten).astype(np.float32))
        hot[sl] = np.maximum(hot[sl], (core * inten).astype(np.float32))

    def stamp_blob(c, rx, ry, inten, ang=0.0):
        S = cv.S
        cx, cy = c[0] * S, c[1] * S
        rx, ry = rx * S, ry * S
        pad = max(rx, ry) * 2.4 + 4
        bx0 = max(0, int(cx - pad)); bx1 = min(S, int(cx + pad))
        by0 = max(0, int(cy - pad)); by1 = min(S, int(cy + pad))
        if bx1 <= bx0 or by1 <= by0:
            return
        X, Y = cv.grid(bx0, by0, bx1, by1)
        a = math.radians(ang)
        XX = (X - cx) * math.cos(a) + (Y - cy) * math.sin(a)
        YY = -(X - cx) * math.sin(a) + (Y - cy) * math.cos(a)
        s = np.sqrt((XX / rx) ** 2 + (YY / ry) ** 2)
        sl = np.s_[by0:by1, bx0:bx1]
        glow[sl] = np.maximum(glow[sl], (np.exp(-(s / 0.66) ** 2) * 0.95 * inten).astype(np.float32))
        hot[sl] = np.maximum(hot[sl], (np.exp(-(s / 0.32) ** 2) * inten).astype(np.float32))

    for spec in specs:
        kind = spec[0]
        a = float(spec[1]) if len(spec) > 2 else 0.10
        b = float(spec[2]) if len(spec) > 2 else 0.90
        inten = float(spec[3]) if len(spec) > 3 else 1.0
        if kind.startswith("arm_"):
            side = kind[-1]
            stamp_seg(mixp(sk["elbow_" + side], sk["sh_" + side], a),
                      mixp(sk["elbow_" + side], sk["sh_" + side], b), RADII["bicep"] * 0.95, inten)
        elif kind.startswith("fore_"):
            side = kind[-1]
            stamp_seg(mixp(sk["elbow_" + side], sk["wrist_" + side], a),
                      mixp(sk["elbow_" + side], sk["wrist_" + side], b), RADII["fore_arm"] * 0.85, inten)
        elif kind.startswith("thigh_"):
            side = kind[-1]
            stamp_seg(mixp(sk["knee_" + side], sk["hip_" + side], a),
                      mixp(sk["knee_" + side], sk["hip_" + side], b), RADII["thigh"] * 1.0, inten)
        elif kind.startswith("shin_"):
            side = kind[-1]
            stamp_seg(mixp(sk["knee_" + side], sk["ankle_" + side], a),
                      mixp(sk["knee_" + side], sk["ankle_" + side], b), RADII["calf"] * 0.85, inten)
        elif kind == "chest":
            c = mixp(sk["shoulder"], sk["root"], 0.30)
            if sk["side_view"]:
                stamp_blob(c, 0.080, 0.038, inten, sk["lean"])
            else:
                for side in (-1, 1):
                    stamp_blob((c[0] + side * 0.052, c[1] + 0.004), 0.048, 0.036, inten, sk["lean"])
        elif kind == "abs":
            c = mixp(sk["shoulder"], sk["root"], 0.68)
            stamp_blob(c, 0.055 if sk["side_view"] else 0.072, 0.055, inten, sk["lean"])
        elif kind == "back":
            stamp_blob(mixp(sk["shoulder"], sk["root"], 0.42), 0.093, 0.070, inten, sk["lean"])
        elif kind == "delts":
            for side in ("n", "f"):
                stamp_blob(sk["sh_" + side], 0.055, 0.038, inten, sk["lean"])
        elif kind == "glute":
            stamp_blob(mixp(sk["hip_n"], sk["hip_f"], 0.5), 0.090, 0.048, inten, sk["lean"])

    if glow.max() <= 0:
        return
    rgb = (np.array(GLOW, np.float32)[None, None, :] * glow[..., None] * 0.85 +
           np.array(GLOW_HOT, np.float32)[None, None, :] * hot[..., None] * 0.75)
    alpha = np.clip(glow * 0.55 + hot * 0.65, 0, 0.92)
    cv.blend(0, 0, rgb.astype(np.float32), alpha.astype(np.float32))


# ------------------------------------------------------------------ gear
def draw_gear(cv, sk, pose, when):
    specs = list(pose.get("gear", ()))
    fn = pose.get("gear_fn")
    if fn:
        specs += list(fn(sk, pose))
    for spec in specs:
        if len(spec) >= 2 and spec[1] != when:
            continue
        kind = spec[0]
        if kind == "floor":
            y = spec[2] if len(spec) > 2 else 0.978
            cv.capsule((0.05, y), (0.95, y), 0.006, 0.006, near=False,
                       tint=(MAT_LIT, MAT, IRON_DK), rim=False, alpha_mul=0.45)
        elif kind == "bench":
            y = spec[2]
            cv.capsule((0.24, y), (0.80, y), 0.046, 0.046, tint=(MAT_LIT, MAT, STEEL_DK))
            for lx, ly in ((0.31, 0.24), (0.72, 0.24)):
                cv.capsule((lx, y + 0.02), (lx, min(0.975, y + ly)), 0.017, 0.022,
                           tint=(IRON_LIT, IRON, IRON_DK))
        elif kind == "incline":
            bx, by, ang = spec[2], spec[3], spec[4]
            top = at((bx, by), 0.40, ang)
            cv.capsule((bx, by), top, 0.046, 0.046, tint=(MAT_LIT, MAT, STEEL_DK))
            cv.capsule(top, (top[0] - 0.03, min(0.975, by + 0.20)), 0.015, 0.019,
                       tint=(IRON_LIT, IRON, IRON_DK))
        elif kind == "bar":
            p1, p2 = (spec[2], spec[3]), (spec[4], spec[5])
            cv.capsule(p1, p2, 0.012, 0.012, tint=(STEEL_LIT, STEEL, STEEL_DK))
            for c in (mixp(p1, p2, 0.03), mixp(p1, p2, 0.97)):
                cv.sphere(c, 0.058, tint=(IRON_LIT, IRON, IRON_DK))
                cv.sphere(c, 0.038, tint=(STEEL_LIT, STEEL, STEEL_DK))
        elif kind == "dumbbell":
            c = (spec[2], spec[3])
            ang = spec[4] if len(spec) > 4 else 90
            a = at(c, 0.055, ang + 90)
            b = at(c, 0.055, ang - 90)
            cv.capsule(a, b, 0.019, 0.019, tint=(IRON_LIT, IRON, IRON_DK))
            for p in (a, b):
                cv.sphere(p, 0.036, tint=(IRON_LIT, IRON, IRON_DK))
        elif kind == "kettlebell":
            c = (spec[2], spec[3])
            cv.sphere(c, 0.048, tint=(IRON_LIT, IRON, IRON_DK))
            cv.capsule((c[0] - 0.026, c[1] - 0.042), (c[0] + 0.026, c[1] - 0.042), 0.012, 0.012,
                       tint=(IRON_LIT, IRON, IRON_DK))
        elif kind == "cable":
            x0, yt, yb, x1, y1 = spec[2], spec[3], spec[4], spec[5], spec[6]
            cv.capsule((x0, yt), (x0, yb), 0.020, 0.020, tint=(IRON_LIT, IRON, IRON_DK))
            cv.sphere((x0, yb), 0.026, tint=(IRON_LIT, IRON, IRON_DK))
            cv.capsule((x0, y1), (x1, y1), 0.005, 0.005, tint=(STEEL_LIT, STEEL, STEEL_DK), rim=False)
        elif kind == "stack":
            x0, y0 = spec[2], spec[3]
            for i in range(6):
                yy = y0 + i * 0.040
                cv.capsule((x0, yy), (x0 + 0.080, yy), 0.024, 0.024,
                           tint=(MAT_LIT, MAT, IRON_DK), rim=False)
        elif kind == "wheel":
            c = (spec[2], spec[3])
            cv.sphere(c, 0.058, tint=(IRON_LIT, IRON, IRON_DK))
            cv.sphere(c, 0.032, tint=(STEEL_LIT, STEEL, STEEL_DK))
            cv.capsule((c[0] - 0.085, c[1]), (c[0] + 0.085, c[1]), 0.011, 0.011,
                       tint=(STEEL_LIT, STEEL, STEEL_DK))
        elif kind == "rope":
            p1, p2 = spec[2], spec[3]
            cv.capsule(p1, p2, 0.008, 0.008, tint=(STEEL_LIT, STEEL, STEEL_DK), rim=False)
        elif kind == "mat":
            y = spec[2]
            cv.capsule((0.16, y), (0.86, y), 0.022, 0.022,
                       tint=(MAT_LIT, MAT, IRON_DK), rim=False)


# ------------------------------------------------------------------ render
def render(pose, size=512, ss=2):
    cv = Canvas(size, ss)
    sk = skeleton(pose)

    ground = pose.get("ground")
    if ground is None:
        ground = min(0.985, max(sk["ankle_n"][1], sk["ankle_f"][1]) + 0.012)
    cv.sphere((sk["root"][0], min(0.99, ground + 0.006)), 0.155, squash=0.13,
              tint=((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (0.0, 0.0, 0.0)), rim=False)
    cv.a *= 0.26

    draw_gear(cv, sk, pose, "back")

    near_side = pose["near_side"]
    far_side = "f" if near_side == "n" else "n"
    draw_leg(cv, sk, far_side, near=False)
    draw_arm(cv, sk, far_side, near=False)
    draw_leg(cv, sk, near_side, near=True)
    cv.torso(sk["root"], sk["shoulder"], sk["lean"], sk["side_view"], pose["spread"])
    cv.torso(sk["root"], sk["shoulder"], sk["lean"], sk["side_view"], pose["spread"] * 1.05,
             tint=(SUIT_LIT, SUIT, SUIT_DK), t0=-0.26, t1=0.135, rim_gain=0.32)
    # neck
    cv.capsule(mixp(sk["shoulder"], sk["neck_top"], 0.02), sk["neck_top"],
               0.034, 0.029, near=True)
    draw_arm(cv, sk, near_side, near=True)
    draw_head(cv, sk, pose)
    draw_gear(cv, sk, pose, "front")
    draw_glow(cv, sk, pose)

    arr = np.clip(np.concatenate([cv.rgb, cv.a[..., None]], axis=2), 0, 1)
    img = Image.fromarray((arr * 255).astype(np.uint8), "RGBA")
    return fit(img, size)


def render_loop(ex_poses, size=512, ss=2, pad=0.055):
    """Render a list of poses keeping one shared crop so the loop doesn't jitter."""
    raw = [render(p, size=1024, ss=1) for p in ex_poses]
    boxes = []
    for im in raw:
        b = im.split()[3].point(lambda v: 255 if v > 6 else 0).getbbox()
        if b:
            boxes.append(b)
    if not boxes:
        return raw
    box = (min(b[0] for b in boxes), min(b[1] for b in boxes),
           max(b[2] for b in boxes), max(b[3] for b in boxes))
    inner = int(size * (1 - 2 * pad))
    w, h = box[2] - box[0], box[3] - box[1]
    k = min(inner / w, inner / h)
    nw, nh = max(1, int(w * k)), max(1, int(h * k))
    out = []
    for im in raw:
        crop = im.crop(box).resize((nw, nh), Image.LANCZOS)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        canvas.paste(crop, ((size - nw) // 2, (size - nh) // 2), crop)
        out.append(canvas)
    return out


def fit(img, size=512, pad=0.055):
    """Crop to content and fit into a square with padding (keeps aspect)."""
    bbox = img.split()[3].point(lambda v: 255 if v > 6 else 0).getbbox()
    if not bbox:
        return img.resize((size, size), Image.LANCZOS)
    img = img.crop(bbox)
    inner = int(size * (1 - 2 * pad))
    w, h = img.size
    k = min(inner / w, inner / h)
    img = img.resize((max(1, int(w * k)), max(1, int(h * k))), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, ((size - img.width) // 2, (size - img.height) // 2), img)
    return out
