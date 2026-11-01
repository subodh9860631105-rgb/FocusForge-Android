"""
FocusForge 3D Mannequin Renderer v2
===================================
A small software rasteriser that replaces the old "stack of capsules" look with a
properly lit, depth-sorted 3D athlete.

Why the old frames looked glitchy
---------------------------------
* every limb was composited with plain "over" blending, so overlapping capsules
  left visible seams and stray edges around the joints,
* the torso was a flat polygon with a painted-on gradient,
* nothing was depth sorted, so arms could vanish behind the chest.

This renderer fixes all three:

* **z-buffer** - every pixel keeps the closest surface, so joints merge into one
  continuous silhouette and the near limb always wins over the far one,
* **muscle height field** - the torso (and every limb) is an implicit surface with
  analytic Gaussian muscle bumps (pecs, abs, lats, traps, glutes, delts, biceps,
  quads, calves) so the shading reads as anatomy instead of a tube,
* **physically flavoured shading** - key + fill + rim lights, wrapped diffuse,
  grazing-angle rim light, specular highlights, ambient occlusion and a cool
  subsurface tint in the shadows.

Units are normalised 0..1 canvas coordinates, exactly like the pose data in
`mannequin.py`, so `exercises.py` keyframes keep working unchanged.
"""

import math

import numpy as np
from PIL import Image

from mannequin import skeleton, BONES, mixp, at

# ------------------------------------------------------------------ lighting
def _unit(v):
    v = np.asarray(v, dtype=np.float32)
    return v / np.linalg.norm(v)


KEY = _unit([-0.55, -0.62, 0.56])      # soft key from the upper left
FILL = _unit([0.68, -0.18, 0.44])      # cool fill from the right
RIM_DIR = _unit([0.80, 0.10, 0.26])    # rim light grazing from behind-right
HALF = _unit(KEY + np.array([0.0, 0.0, 1.0], np.float32))


# ------------------------------------------------------------------ materials
class Material:
    def __init__(self, lit, mid, dark, spec=0.22, shine=26.0,
                 rim=(0.42, 0.78, 1.0), rim_gain=0.85, sub=(0.30, 0.40, 0.62),
                 ao_gain=0.45):
        self.lit = np.array(lit, np.float32)
        self.mid = np.array(mid, np.float32)
        self.dark = np.array(dark, np.float32)
        self.spec = spec
        self.shine = shine
        self.rim = np.array(rim, np.float32)
        self.rim_gain = rim_gain
        self.sub = np.array(sub, np.float32)
        self.ao_gain = ao_gain


SKIN = Material(
    lit=(0.968, 0.978, 0.996),
    mid=(0.712, 0.762, 0.845),
    dark=(0.150, 0.192, 0.292),
    spec=0.15, shine=18.0,
    rim=(0.46, 0.80, 1.00), rim_gain=0.80,
    sub=(0.34, 0.40, 0.66),
)
SKIN_FAR = Material(
    lit=(0.740, 0.782, 0.858),
    mid=(0.512, 0.566, 0.658),
    dark=(0.105, 0.138, 0.222),
    spec=0.08, shine=14.0,
    rim=(0.34, 0.58, 0.84), rim_gain=0.42,
    sub=(0.25, 0.31, 0.54),
)
SUIT = Material(
    lit=(0.336, 0.424, 0.600),
    mid=(0.148, 0.202, 0.325),
    dark=(0.036, 0.055, 0.100),
    spec=0.16, shine=14.0,
    rim=(0.30, 0.62, 0.95), rim_gain=0.58,
    sub=(0.09, 0.13, 0.25),
)
SHOE = Material(
    lit=(0.310, 0.372, 0.512),
    mid=(0.118, 0.152, 0.242),
    dark=(0.028, 0.042, 0.078),
    spec=0.32, shine=34.0,
    rim=(0.28, 0.55, 0.90), rim_gain=0.48,
    sub=(0.07, 0.10, 0.19),
)
HAIR = Material(
    lit=(0.210, 0.265, 0.372),
    mid=(0.086, 0.112, 0.172),
    dark=(0.016, 0.022, 0.040),
    spec=0.48, shine=48.0,
    rim=(0.34, 0.60, 0.92), rim_gain=0.72,
    sub=(0.05, 0.07, 0.15),
)
STEEL = Material(
    lit=(0.880, 0.945, 1.000),
    mid=(0.440, 0.590, 0.730),
    dark=(0.088, 0.148, 0.238),
    spec=0.95, shine=70.0,
    rim=(0.55, 0.85, 1.00), rim_gain=1.00,
    sub=(0.10, 0.18, 0.30), ao_gain=0.20,
)
IRON = Material(
    lit=(0.560, 0.612, 0.702),
    mid=(0.212, 0.252, 0.328),
    dark=(0.044, 0.060, 0.098),
    spec=0.55, shine=32.0,
    rim=(0.40, 0.62, 0.90), rim_gain=0.78,
    sub=(0.06, 0.09, 0.16), ao_gain=0.30,
)
RUBBER = Material(
    lit=(0.232, 0.272, 0.342),
    mid=(0.098, 0.118, 0.162),
    dark=(0.018, 0.024, 0.042),
    spec=0.10, shine=8.0,
    rim=(0.25, 0.45, 0.75), rim_gain=0.35,
    sub=(0.05, 0.07, 0.12),
)


def _smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0 + 1e-9), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


# ======================================================================
# Frame buffer
# ======================================================================
class Frame:
    """Premultiplied colour + coverage + depth buffer."""

    def __init__(self, size=512, ss=2):
        self.S = int(size * ss)
        self.P = np.zeros((self.S, self.S, 3), np.float32)
        self.A = np.zeros((self.S, self.S), np.float32)
        self.Z = np.full((self.S, self.S), -9.0, np.float32)

    # ---------------------------------------------------------------- core
    @staticmethod
    def _shade(nx, ny, nz, mat, ao):
        lam = np.clip(nx * KEY[0] + ny * KEY[1] + nz * KEY[2], 0.0, 1.0)
        fil = np.clip(nx * FILL[0] + ny * FILL[1] + nz * FILL[2], 0.0, 1.0)
        t = np.clip(0.13 + lam * 0.92 + fil * 0.26, 0.0, 1.25)

        col = (mat.dark[None, None, :]
               + (mat.mid[None, None, :] - mat.dark[None, None, :]) * _smoothstep(0.02, 0.78, t)[..., None])
        hi = _smoothstep(0.60, 1.12, t)[..., None]
        col = col + (mat.lit[None, None, :] - col) * (hi * 0.94)

        # cool subsurface tint in the shadows
        shadow = (1.0 - _smoothstep(0.08, 0.52, t))[..., None]
        col = col + (mat.sub[None, None, :] - col) * (shadow * 0.32)

        # grazing-angle rim light
        grazing = np.clip(1.0 - np.clip(nz, 0.0, 1.0), 0.0, 1.0) ** 2.0
        rimamt = np.clip(nx * RIM_DIR[0] + ny * RIM_DIR[1] + nz * RIM_DIR[2], 0.0, 1.0)
        col = col + (mat.rim[None, None, :] - col) * (grazing * rimamt * mat.rim_gain)[..., None]

        if mat.spec > 0.01:
            sp = np.clip(nx * HALF[0] + ny * HALF[1] + nz * HALF[2], 0.0, 1.0) ** mat.shine
            col = col + sp[..., None] * (mat.spec * 0.80)

        ao_f = 1.0 - (1.0 - np.clip(ao, 0.0, 1.0)) * mat.ao_gain
        return col * ao_f[..., None]

    def _paint(self, bx0, by0, col, cov, z, bias):
        if cov is None or not np.any(cov > 0.002):
            return
        dst = np.s_[by0:by0 + cov.shape[0], bx0:bx0 + cov.shape[1]]
        zz = z + bias
        sel = (cov > 0.002) & (zz >= self.Z[dst])
        if not sel.any():
            return
        c = np.clip(cov[sel], 0.0, 1.0)[:, None]
        view = self.P[dst]
        view[sel] = col[sel] * c + view[sel] * (1.0 - c)
        va = self.A[dst]
        va[sel] = cov[sel] + va[sel] * (1.0 - cov[sel])
        vz = self.Z[dst]
        vz[sel] = zz[sel]

    def _tile(self, x0, y0, x1, y1):
        S = self.S
        bx0 = max(0, int(math.floor(x0)))
        by0 = max(0, int(math.floor(y0)))
        bx1 = min(S, int(math.ceil(x1)))
        by1 = min(S, int(math.ceil(y1)))
        if bx1 <= bx0 or by1 <= by0:
            return None
        X, Y = np.meshgrid(np.arange(bx0, bx1, dtype=np.float32) + 0.5,
                           np.arange(by0, by1, dtype=np.float32) + 0.5)
        return bx0, by0, bx1, by1, X, Y

    # ------------------------------------------------------------ primitives
    def limb(self, p1, p2, r1, r2, mat, bias=0.0, bulge=None, squash=1.0):
        """Tapered capsule with an optional muscle bulge.

        bulge = (centre_t, width, amount) adds a smooth radial swell - that is how
        biceps / quads / calves get their shape without extra geometry.
        """
        p1 = np.asarray(p1, np.float32)
        p2 = np.asarray(p2, np.float32)
        S = self.S
        rmax = max(r1, r2) + (bulge[2] if bulge else 0.0)
        rmax *= max(1.0, squash)
        tile = self._tile((min(p1[0], p2[0]) - rmax) * S, (min(p1[1], p2[1]) - rmax) * S,
                          (max(p1[0], p2[0]) + rmax) * S, (max(p1[1], p2[1]) + rmax) * S)
        if tile is None:
            return
        bx0, by0, bx1, by1, X, Y = tile
        Xc, Yc = X / S, Y / S
        dx, dy = float(p2[0] - p1[0]), float(p2[1] - p1[1])
        L = math.hypot(dx, dy) + 1e-9
        ux, uy = dx / L, dy / L
        raw_t = (Xc - p1[0]) * ux + (Yc - p1[1]) * uy

        # centre clamped onto the segment -> round caps with correct normals
        cx = np.where(raw_t < 0, p1[0], np.where(raw_t > L, p2[0], p1[0] + ux * raw_t))
        cy = np.where(raw_t < 0, p1[1], np.where(raw_t > L, p2[1], p1[1] + uy * raw_t))
        tc = np.clip(raw_t / L, 0.0, 1.0)
        r = r1 + (r2 - r1) * tc
        if bulge:
            bc, bw, ba = bulge
            r = r + ba * np.exp(-(((tc - bc) / bw) ** 2))

        vx = (Xc - cx) / np.maximum(r, 1e-6)
        vy = (Yc - cy) / np.maximum(r * squash, 1e-6)
        q = vx * vx + vy * vy
        inside = q <= 1.0
        if not inside.any():
            return
        nz = np.sqrt(np.clip(1.0 - q, 0.0, 1.0))
        cov = np.clip((1.0 - np.sqrt(q)) * np.minimum(r, r / max(squash, 1e-6)) * S, 0.0, 1.0)
        ao = np.clip(0.92 - (1.0 - nz) * 0.30, 0.42, 1.0)
        col = self._shade(vx, vy, nz, mat, ao)
        self._paint(bx0, by0, col, cov.astype(np.float32),
                    (nz * r).astype(np.float32), bias)

    def ellipsoid(self, c, rx, ry, mat, ang=0.0, bias=0.0, ao_base=0.95,
                  hair_cut=0.0, ry_scale=1.0):
        S = self.S
        a = math.radians(ang)
        ca, sa = math.cos(a), math.sin(a)
        rr = max(rx, ry * ry_scale)
        tile = self._tile((c[0] - rr) * S, (c[1] - rr) * S,
                          (c[0] + rr) * S, (c[1] + rr) * S)
        if tile is None:
            return
        bx0, by0, bx1, by1, X, Y = tile
        dx = X / S - c[0]
        dy = Y / S - c[1]
        lx = dx * ca + dy * sa
        ly = -dx * sa + dy * ca
        vx = lx / max(rx, 1e-6)
        vy = ly / max(ry * ry_scale, 1e-6)
        q = vx * vx + vy * vy
        inside = q <= 1.0
        if not inside.any():
            return
        nz = np.sqrt(np.clip(1.0 - q, 0.0, 1.0))
        cov = np.clip((1.0 - np.sqrt(q)) * min(rx, ry * ry_scale) * S, 0.0, 1.0)
        if hair_cut > 0:
            cov = cov * (1.0 - _smoothstep(hair_cut - 0.12, hair_cut + 0.12, vy))
        nx = vx * ca - vy * sa
        ny = vx * sa + vy * ca
        ao = np.clip(ao_base - (1.0 - nz) * 0.28, 0.40, 1.0)
        col = self._shade(nx, ny, nz, mat, ao)
        self._paint(bx0, by0, col, cov.astype(np.float32),
                    (nz * min(rx, ry) * 1.8).astype(np.float32), bias)

    # ---------------------------------------------------------------- torso
    DEPTH = [(0.00, 0.058), (0.13, 0.074), (0.30, 0.062), (0.47, 0.066),
             (0.66, 0.076), (0.83, 0.080), (0.96, 0.068), (1.06, 0.046)]
    WIDTH = [(0.00, 0.080), (0.13, 0.092), (0.30, 0.074), (0.47, 0.084),
             (0.66, 0.098), (0.83, 0.110), (0.96, 0.096), (1.06, 0.050)]

    def torso(self, root, shoulder, side_view, spread=1.0, bias=0.0,
              mat=SKIN, t0=-0.05, t1=1.09, scale=1.0, relief=0.85, muscles=True):
        S = self.S
        x0, y0 = root
        x1, y1 = shoulder
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy) + 1e-9
        ux, uy = dx / L, dy / L
        px, py = -uy, ux                      # in-plane sideways axis
        half = max(max(w for _, w in self.WIDTH), max(d for _, d in self.DEPTH)) * spread * scale
        tile = self._tile((min(x0, x1) - half * 1.7) * S, (min(y0, y1) - half * 1.7) * S,
                          (max(x0, x1) + half * 1.7) * S, (max(y0, y1) + half * 1.7) * S)
        if tile is None:
            return
        bx0, by0, bx1, by1, X, Y = tile
        Xc, Yc = X / S, Y / S
        t = ((Xc - x0) * ux + (Yc - y0) * uy) / L
        d = ((Xc - x0) * px + (Yc - y0) * py) / spread

        ts = np.array([p[0] for p in self.DEPTH], np.float32)
        ds = np.array([p[1] for p in self.DEPTH], np.float32)
        ws = np.array([p[1] for p in self.WIDTH], np.float32)
        depth = np.interp(t, ts, ds) * scale
        width = np.interp(t, ts, ws) * scale
        if side_view:
            rx, rz = depth, width * 0.96
        else:
            rx, rz = width * 0.60, depth * 1.02

        q = (d / np.maximum(rx, 1e-6)) ** 2
        h = rz * np.sqrt(np.clip(1.0 - q, 0.0, 1.0))

        if muscles:
            dn = d / np.maximum(width, 1e-6)
            front_w = _smoothstep(0.10, 0.52, -dn) if side_view else np.ones_like(dn)
            back_w = _smoothstep(0.10, 0.52, dn) if side_view else np.ones_like(dn)

            def g(ct, cw, st, sw, amp):
                return amp * np.exp(-(((t - ct) / st) ** 2 + ((dn - cw) / sw) ** 2))

            if not side_view:
                bump = g(0.845, -0.46, 0.085, 0.30, 0.0135) * front_w
                bump = bump + g(0.845, 0.46, 0.085, 0.30, 0.0135) * front_w
                abs_w = np.exp(-(((t - 0.545) / 0.155) ** 2)) * np.exp(-((dn / 0.52) ** 2))
                grooves = 0.5 + 0.5 * np.cos((t - 0.40) * 58.0)
                bump = bump + abs_w * (0.0068 + 0.0014 * grooves) * front_w
                bump = bump + g(0.545, 0.0, 0.16, 0.05, -0.0050) * front_w
                bump = bump + g(0.40, -0.80, 0.10, 0.16, 0.0068) * front_w
                bump = bump + g(0.40, 0.80, 0.10, 0.16, 0.0068) * front_w
                bump = bump + g(0.70, -0.82, 0.20, 0.24, 0.0100) * back_w
                bump = bump + g(0.70, 0.82, 0.20, 0.24, 0.0100) * back_w
                bump = bump + g(0.55, 0.0, 0.20, 0.10, -0.0038) * back_w
                bump = bump + g(0.955, 0.0, 0.075, 0.62, 0.0088)
                bump = bump + g(0.955, -1.02, 0.075, 0.24, 0.0095)
                bump = bump + g(0.955, 1.02, 0.075, 0.24, 0.0095)
            else:
                bump = g(0.855, 0.55, 0.10, 0.42, 0.0112) * front_w
                bump = bump + g(0.58, -0.62, 0.20, 0.44, 0.0072) * back_w
                bump = bump + g(0.16, 0.55, 0.11, 0.48, 0.0145) * back_w
                bump = bump + g(0.45, 0.35, 0.16, 0.22, 0.0034) * back_w
            h = h + bump

        # analytic elliptical normal, perturbed by the muscle height field
        nx = (d / np.maximum(rx, 1e-6) ** 2)
        nz = (h / np.maximum(rz, 1e-6) ** 2)
        norm = np.sqrt(nx * nx + nz * nz) + 1e-9
        nx, nz = nx / norm, nz / norm
        if muscles:
            gy, gx = np.gradient(h)
            nx = nx - gx * relief * 2.2
            ny = -gy * relief * 2.2
        else:
            ny = np.zeros_like(nx)
        norm = np.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9
        nx, ny, nz = nx / norm, ny / norm, nz / norm

        cov = np.clip((1.0 - np.sqrt(np.clip(q, 0.0, 1.0))) * rx * S * 0.92, 0.0, 1.0)
        cov = cov * _smoothstep(t0 - 0.05, t0 + 0.05, t) * (1.0 - _smoothstep(t1 - 0.05, t1 + 0.05, t))

        rz_ref = float(np.mean(rz))
        ao = np.clip(0.55 + 0.48 * np.clip(h / max(rz_ref, 1e-6) + 0.15, 0.0, 1.0), 0.30, 1.0)
        col = self._shade(nx, ny, nz, mat, ao)
        self._paint(bx0, by0, col, cov.astype(np.float32),
                    h.astype(np.float32), bias)


# ======================================================================
# Anatomy
# ======================================================================
R = dict(delt=0.034, upper=0.0315, elbow=0.0245, fore=0.0265, wrist=0.0175,
         hand=0.0185, hip=0.054, thigh=0.050, knee=0.0325, calf=0.0345,
         ankle=0.019, foot=0.0245)


def _dir(a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    return math.degrees(math.atan2(dy, dx))


def draw_gear(fr, sk, pose, when, bias):
    """Render the equipment specs produced by exercises.py."""
    specs = list(pose.get("gear", ()))
    fn = pose.get("gear_fn")
    if fn:
        specs += list(fn(sk, pose))
    b = bias
    for spec in specs:
        if len(spec) >= 2 and spec[1] != when:
            continue
        b += 0.0015
        kind = spec[0]
        if kind == "floor":
            y = spec[2] if len(spec) > 2 else 0.978
            fr.limb((0.02, y), (0.98, y), 0.004, 0.004, RUBBER, bias=b)
        elif kind == "bench":
            y = spec[2]
            fr.limb((0.245, y), (0.80, y), 0.043, 0.043, RUBBER, bias=b)
            fr.ellipsoid((0.80, y), 0.043, 0.043, RUBBER, bias=b + 0.0005)
            for lx, ly in ((0.31, 0.24), (0.72, 0.24)):
                fr.limb((lx, y + 0.02), (lx, min(0.975, y + ly)), 0.015, 0.020, IRON, bias=b - 0.002)
        elif kind == "incline":
            bx, by, ang = spec[2], spec[3], spec[4]
            top = at((bx, by), 0.40, ang)
            fr.limb((bx, by), top, 0.043, 0.043, RUBBER, bias=b)
            fr.limb(top, (top[0] - 0.03, min(0.975, by + 0.20)), 0.014, 0.018, IRON, bias=b - 0.002)
        elif kind == "bar":
            p1, p2 = (spec[2], spec[3]), (spec[4], spec[5])
            fr.limb(p1, p2, 0.011, 0.011, STEEL, bias=b)
            for c in (mixp(p1, p2, 0.02), mixp(p1, p2, 0.98)):
                fr.ellipsoid(c, 0.056, 0.056, IRON, bias=b + 0.001)
                fr.ellipsoid(c, 0.032, 0.032, STEEL, bias=b + 0.002)
        elif kind == "dumbbell":
            c = (spec[2], spec[3])
            ang = spec[4] if len(spec) > 4 else 90
            a = at(c, 0.052, ang + 90)
            bb = at(c, 0.052, ang - 90)
            fr.limb(a, bb, 0.017, 0.017, IRON, bias=b)
            for p in (a, bb):
                fr.ellipsoid(p, 0.034, 0.034, IRON, bias=b + 0.001)
        elif kind == "kettlebell":
            c = (spec[2], spec[3])
            fr.ellipsoid(c, 0.046, 0.048, IRON, bias=b)
            fr.limb((c[0] - 0.024, c[1] - 0.040), (c[0] + 0.024, c[1] - 0.040),
                    0.011, 0.011, IRON, bias=b + 0.001)
        elif kind == "cable":
            x0, yt, yb, x1, y1 = spec[2], spec[3], spec[4], spec[5], spec[6]
            fr.limb((x0, yt), (x0, yb), 0.019, 0.019, IRON, bias=b - 0.003)
            fr.ellipsoid((x0, yb), 0.025, 0.025, IRON, bias=b - 0.003)
            fr.limb((x0, y1), (x1, y1), 0.004, 0.004, STEEL, bias=b)
        elif kind == "stack":
            x0, y0 = spec[2], spec[3]
            for i in range(6):
                yy = y0 + i * 0.040
                fr.limb((x0, yy), (x0 + 0.078, yy), 0.023, 0.023, IRON, bias=b - 0.004)
        elif kind == "wheel":
            c = (spec[2], spec[3])
            fr.ellipsoid(c, 0.056, 0.056, RUBBER, bias=b)
            fr.ellipsoid(c, 0.030, 0.030, STEEL, bias=b + 0.002)
            fr.limb((c[0] - 0.082, c[1]), (c[0] + 0.082, c[1]), 0.010, 0.010, STEEL, bias=b + 0.003)
        elif kind == "rope":
            p1, p2 = spec[2], spec[3]
            fr.limb(p1, p2, 0.007, 0.007, STEEL, bias=b)
        elif kind == "mat":
            y = spec[2]
            fr.limb((0.14, y), (0.88, y), 0.020, 0.020, RUBBER, bias=b)


def draw_leg(fr, sk, side, near, base_bias):
    hip = sk["hip_" + side]
    knee = sk["knee_" + side]
    ankle = sk["ankle_" + side]
    toe = sk["toe_" + side]
    b = base_bias
    m = SKIN if near else SKIN_FAR
    suit = SUIT if near else SUIT

    # thigh with a quadriceps swell, then the knee blends straight into the shin
    fr.limb(hip, knee, R["thigh"] * 1.04, R["knee"], m, bias=b,
            bulge=(0.36, 0.34, 0.009))
    fr.limb(knee, ankle, R["knee"] * 1.02, R["ankle"], m, bias=b + 0.002,
            bulge=(0.28, 0.26, 0.0105))
    fr.ellipsoid(ankle, R["ankle"] * 1.10, R["ankle"] * 1.02, m, bias=b + 0.003)

    # shorts leg: same axis, slightly fatter, hem above the knee
    fr.limb(mixp(hip, knee, 0.02), mixp(hip, knee, 0.50),
            R["thigh"] * 1.14, R["thigh"] * 1.02,
            suit, bias=b + 0.006, bulge=(0.30, 0.36, 0.004))

    # shoe: heel block + toe box, angled along the foot direction
    ang = _dir(ankle, toe)
    heel = (ankle[0] - 0.013 * math.cos(math.radians(ang)), ankle[1] + 0.006)
    fr.limb(heel, toe, R["foot"] * 1.02, R["foot"] * 0.86, SHOE, bias=b + 0.008)
    fr.ellipsoid(toe, R["foot"] * 0.90, R["foot"] * 0.74, SHOE, bias=b + 0.009)
    fr.ellipsoid(heel, R["foot"] * 1.00, R["foot"] * 0.90, SHOE, bias=b + 0.009)


def draw_arm(fr, sk, side, near, base_bias):
    sh = sk["sh_" + side]
    elbow = sk["elbow_" + side]
    wrist = sk["wrist_" + side]
    hand = sk["hand_" + side]
    b = base_bias
    m = SKIN if near else SKIN_FAR

    fr.ellipsoid(sh, R["delt"] * 1.10, R["delt"] * 1.02, m, bias=b)
    fr.limb(sh, elbow, R["delt"] * 0.86, R["elbow"], m, bias=b + 0.002,
            bulge=(0.42, 0.32, 0.0075))
    fr.limb(elbow, wrist, R["elbow"] * 0.98, R["wrist"], m, bias=b + 0.003,
            bulge=(0.26, 0.28, 0.0050))
    ang = _dir(elbow, wrist)
    fr.limb(wrist, hand, R["wrist"] * 1.05, R["hand"] * 0.92, m, bias=b + 0.004)
    fr.ellipsoid(hand, R["hand"] * 0.80, R["hand"] * 1.05, m,
                 ang=ang + 90, bias=b + 0.005)


def draw_pelvis(fr, sk, base_bias):
    """One continuous pelvis block so the shorts read as a garment, not two balls."""
    hip_n, hip_f = sk["hip_n"], sk["hip_f"]
    cx = (hip_n[0] + hip_f[0]) / 2
    cy = (hip_n[1] + hip_f[1]) / 2
    b = base_bias
    fr.limb(hip_f, hip_n, R["thigh"] * 1.12, R["thigh"] * 1.12, SUIT, bias=b + 0.006)
    fr.ellipsoid((cx, cy + 0.004), abs(hip_n[0] - hip_f[0]) / 2 + R["thigh"] * 1.02,
                 R["thigh"] * 1.30, SUIT, bias=b + 0.008)
    # crotch bridge
    fr.limb((cx, cy + 0.012), (cx, cy + 0.030), R["thigh"] * 0.86, R["thigh"] * 0.62,
            SUIT, bias=b + 0.010)


def draw_head(fr, sk, pose, base_bias):
    c = sk["head"]
    r = BONES["head_r"]
    b = base_bias
    # neck
    fr.limb(mixp(sk["shoulder"], sk["neck_top"], 0.0), sk["neck_top"],
            0.033, 0.028, SKIN, bias=b - 0.002)
    # skull, cheek/jaw mass, slight chin
    fr.ellipsoid(c, r * 0.93, r * 1.05, SKIN, bias=b)
    fr.ellipsoid((c[0], c[1] + r * 0.40), r * 0.74, r * 0.66, SKIN, bias=b + 0.001)
    # brow ridge + nose give the profile a readable face
    fr.ellipsoid((c[0] - r * 0.52, c[1] - r * 0.14), r * 0.30, r * 0.13, SKIN,
                 bias=b + 0.006, ao_base=0.80)
    fr.ellipsoid((c[0] - r * 0.70, c[1] + r * 0.20), r * 0.16, r * 0.26, SKIN,
                 bias=b + 0.004)
    # ear
    fr.ellipsoid((c[0] + r * 0.80, c[1] + r * 0.16), r * 0.18, r * 0.28, SKIN,
                 bias=b + 0.003)
    # hair cap: rides on top of the skull and wraps the back of the head
    fr.ellipsoid((c[0] + r * 0.10, c[1] - r * 0.16), r * 0.99, r * 1.04, HAIR,
                 bias=b + 0.008, hair_cut=-0.10)


# ------------------------------------------------------------------ glow
GLOW = np.array([0.95, 0.19, 0.30], np.float32)
GLOW_HOT = np.array([1.00, 0.62, 0.34], np.float32)


def draw_glow(fr, sk, pose):
    """Additive red muscle-heat overlay painted on top of the body."""
    specs = pose.get("glow", ())
    if specs and isinstance(specs[0], str):
        specs = (specs,)
    if not specs:
        return
    S = fr.S
    glow = np.zeros((S, S), np.float32)
    hot = np.zeros((S, S), np.float32)

    def stamp_seg(p1, p2, w, inten):
        x1, y1 = p1[0] * S, p1[1] * S
        x2, y2 = p2[0] * S, p2[1] * S
        ww = max(w * S, 1.0)
        pad = ww * 2.8 + 4
        bx0 = max(0, int(min(x1, x2) - pad)); bx1 = min(S, int(max(x1, x2) + pad))
        by0 = max(0, int(min(y1, y2) - pad)); by1 = min(S, int(max(y1, y2) + pad))
        if bx1 <= bx0 or by1 <= by0:
            return
        X, Y = np.meshgrid(np.arange(bx0, bx1, dtype=np.float32) + 0.5,
                           np.arange(by0, by1, dtype=np.float32) + 0.5)
        dx, dy = x2 - x1, y2 - y1
        L2 = dx * dx + dy * dy + 1e-9
        t = np.clip(((X - x1) * dx + (Y - y1) * dy) / L2, 0, 1)
        d = np.hypot(X - (x1 + dx * t), Y - (y1 + dy * t))
        core = np.clip(1 - d / (ww * 0.92), 0, 1) ** 1.15
        halo = np.exp(-(d / (ww * 1.75)) ** 2)
        sl = np.s_[by0:by1, bx0:bx1]
        glow[sl] = np.maximum(glow[sl], (halo * 0.92 * inten).astype(np.float32))
        hot[sl] = np.maximum(hot[sl], (core * inten).astype(np.float32))

    def stamp_blob(c, rx, ry, inten, ang=0.0):
        cx, cy = c[0] * S, c[1] * S
        rx, ry = rx * S, ry * S
        pad = max(rx, ry) * 2.6 + 4
        bx0 = max(0, int(cx - pad)); bx1 = min(S, int(cx + pad))
        by0 = max(0, int(cy - pad)); by1 = min(S, int(cy + pad))
        if bx1 <= bx0 or by1 <= by0:
            return
        X, Y = np.meshgrid(np.arange(bx0, bx1, dtype=np.float32) + 0.5,
                           np.arange(by0, by1, dtype=np.float32) + 0.5)
        a = math.radians(ang)
        XX = (X - cx) * math.cos(a) + (Y - cy) * math.sin(a)
        YY = -(X - cx) * math.sin(a) + (Y - cy) * math.cos(a)
        s = np.sqrt((XX / max(rx, 1e-6)) ** 2 + (YY / max(ry, 1e-6)) ** 2)
        sl = np.s_[by0:by1, bx0:bx1]
        glow[sl] = np.maximum(glow[sl], (np.exp(-(s / 0.70) ** 2) * 0.95 * inten).astype(np.float32))
        hot[sl] = np.maximum(hot[sl], (np.exp(-(s / 0.34) ** 2) * inten).astype(np.float32))

    for spec in specs:
        kind = spec[0]
        a = float(spec[1]) if len(spec) > 2 else 0.10
        bb = float(spec[2]) if len(spec) > 2 else 0.90
        inten = float(spec[3]) if len(spec) > 3 else 1.0
        if kind.startswith("arm_"):
            side = kind[-1]
            stamp_seg(mixp(sk["elbow_" + side], sk["sh_" + side], a),
                      mixp(sk["elbow_" + side], sk["sh_" + side], bb),
                      R["upper"] * 1.05, inten)
        elif kind.startswith("fore_"):
            side = kind[-1]
            stamp_seg(mixp(sk["elbow_" + side], sk["wrist_" + side], a),
                      mixp(sk["elbow_" + side], sk["wrist_" + side], bb),
                      R["fore"] * 1.0, inten)
        elif kind.startswith("thigh_"):
            side = kind[-1]
            stamp_seg(mixp(sk["knee_" + side], sk["hip_" + side], a),
                      mixp(sk["knee_" + side], sk["hip_" + side], bb),
                      R["thigh"] * 1.02, inten)
        elif kind.startswith("shin_"):
            side = kind[-1]
            stamp_seg(mixp(sk["knee_" + side], sk["ankle_" + side], a),
                      mixp(sk["knee_" + side], sk["ankle_" + side], bb),
                      R["calf"] * 1.0, inten)
        elif kind == "chest":
            c = mixp(sk["shoulder"], sk["root"], 0.28)
            if sk["side_view"]:
                stamp_blob(c, 0.078, 0.040, inten, sk["lean"])
            else:
                for side in (-1, 1):
                    stamp_blob((c[0] + side * 0.048, c[1] + 0.004), 0.044, 0.034, inten, sk["lean"])
        elif kind == "abs":
            c = mixp(sk["shoulder"], sk["root"], 0.66)
            stamp_blob(c, 0.050 if sk["side_view"] else 0.064, 0.052, inten, sk["lean"])
        elif kind == "back":
            stamp_blob(mixp(sk["shoulder"], sk["root"], 0.42), 0.088, 0.066, inten, sk["lean"])
        elif kind == "delts":
            for side in ("n", "f"):
                stamp_blob(sk["sh_" + side], 0.050, 0.036, inten, sk["lean"])
        elif kind == "glute":
            stamp_blob(mixp(sk["hip_n"], sk["hip_f"], 0.5), 0.084, 0.046, inten, sk["lean"])

    if glow.max() <= 0:
        return
    # multiply-blend the heat into the body, additive-ish on the hot core
    tint = (GLOW[None, None, :] * glow[..., None] * 0.9
            + GLOW_HOT[None, None, :] * hot[..., None] * 0.8)
    a = np.clip(glow * 0.52 + hot * 0.60, 0, 0.90) * fr.A
    fr.P += tint * a[..., None] * 0.85


# ======================================================================
# Render
# ======================================================================
def paint(pose, fr):
    """Draw one pose into an existing frame buffer (caller owns the buffer)."""
    sk = skeleton(pose)

    draw_gear(fr, sk, pose, "back", -0.60)

    near_side = pose["near_side"]
    far_side = "f" if near_side == "n" else "n"

    draw_leg(fr, sk, far_side, False, -0.34)
    draw_arm(fr, sk, far_side, False, -0.32)

    # torso, then a shorts waistband riding on the same surface
    fr.torso(sk["root"], sk["shoulder"], sk["side_view"], pose["spread"], bias=0.0)
    fr.torso(sk["root"], sk["shoulder"], sk["side_view"], pose["spread"],
             bias=0.012, mat=SUIT, t0=-0.06, t1=0.20, scale=1.035, muscles=False)

    draw_pelvis(fr, sk, 0.20)
    draw_leg(fr, sk, near_side, True, 0.30)
    draw_head(fr, sk, pose, 0.05)
    draw_arm(fr, sk, near_side, True, 0.32)

    draw_gear(fr, sk, pose, "front", 0.62)
    draw_glow(fr, sk, pose)
    return fr


def to_image(fr):
    a = np.clip(fr.A, 0.0, 1.0)
    safe = np.maximum(a, 1e-5)
    rgb = np.clip(fr.P / safe[..., None], 0.0, 1.0)
    return Image.fromarray((np.concatenate([rgb, a[..., None]], axis=2) * 255)
                           .astype(np.uint8), "RGBA")


def _norm_bbox(pose, probe=200):
    """Alpha bounding box in normalised canvas coordinates."""
    fr = Frame(probe, 1)
    paint(pose, fr)
    a = fr.A
    ys, xs = np.nonzero(a > 0.02)
    if len(xs) == 0:
        return None
    return (xs.min() / probe, ys.min() / probe, (xs.max() + 1) / probe, (ys.max() + 1) / probe)


def render_frame(pose, size=256, ss=3):
    fr = Frame(size, ss)
    paint(pose, fr)
    return to_image(fr)


def fit(img, size=512, pad=0.05):
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


def render(pose, size=512):
    return fit(render_frame(pose, size=size, ss=1), size=size)


def render_loop(poses, size=256, pad=0.045, ss=1):
    """Render a loop with one shared crop so the animation never jitters."""
    boxes = [_norm_bbox(p) for p in poses]
    boxes = [b for b in boxes if b]
    if not boxes:
        return [Image.new("RGBA", (size, size), (0, 0, 0, 0)) for _ in poses]
    box = (min(b[0] for b in boxes), min(b[1] for b in boxes),
           max(b[2] for b in boxes), max(b[3] for b in boxes))

    hi = size * 3                       # render 3x then downsample for crisp edges
    out = []
    inner = int(size * (1 - 2 * pad))
    for p in poses:
        img = render_frame(p, size=hi, ss=ss)
        px = (int(box[0] * hi) - 2, int(box[1] * hi) - 2,
              int(math.ceil(box[2] * hi)) + 2, int(math.ceil(box[3] * hi)) + 2)
        px = (max(0, px[0]), max(0, px[1]), min(hi, px[2]), min(hi, px[3]))
        crop = img.crop(px)
        w, h = crop.size
        k = min(inner / max(w, 1), inner / max(h, 1))
        crop = crop.resize((max(1, int(w * k)), max(1, int(h * k))), Image.LANCZOS)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        canvas.paste(crop, ((size - crop.width) // 2, (size - crop.height) // 2), crop)
        out.append(canvas)
    return out
