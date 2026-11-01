"""
FocusForge 3D Mannequin Renderer v3  (signed-distance raymarcher)
=================================================================
The v1/v2 pipeline stacked 2D capsules with alpha compositing, which is why the
frames looked glitchy: limbs left seams at every joint and nothing was depth
sorted. This module renders a *real* 3D body instead.

* the pose data (2D joint angles from `mannequin.skeleton`) is promoted to 3D by
  giving the near/far limb pairs a depth offset,
* the body is a signed distance field: smooth-unioned tapered capsules and
  ellipsoids (chest, ribs, waist, pelvis, delts, biceps, quads, calves, glutes,
  pecs, abs...) so every joint melts into the next with no seams,
* each pixel is raymarched against that field, so occlusion, contact shadows and
  the silhouette are all physically consistent,
* shading is key + fill + rim + specular with SDF ambient occlusion, in the
  "clean grey mannequin with a glowing muscle" style of reference workout apps.

Output stays a transparent-background WebP loop, so nothing changes in the app.
"""

import math

import numpy as np

# ------------------------------------------------------------------ helpers
FEPS = 1e-4


def _v(x, y, z):
    return np.array([x, y, z], np.float32)


def length(p):
    return np.sqrt(np.einsum("ij,ij->i", p, p))


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b * (1.0 - h) + a * h - k * h * (1.0 - h)


def sd_ellipsoid(p, c, r):
    q = (p - c) / r
    k0 = length(q)
    k1 = length(q / r)
    return np.where(k0 < 1e-6, -float(np.min(r)), k0 * (k0 - 1.0) / np.maximum(k1, 1e-6))


def sd_capsule(p, a, b, r1, r2=None):
    if r2 is None:
        r2 = r1
    pa = p - a
    ba = b - a
    denom = float(np.dot(ba, ba)) + 1e-9
    h = np.clip((pa @ ba) / denom, 0.0, 1.0)
    d = pa - ba[None, :] * h[:, None]
    return length(d) - (r1 + (r2 - r1) * h)


def sd_box(p, c, half, round_r=0.0):
    q = np.abs(p - c) - (half - round_r)
    outside = length(np.maximum(q, 0.0))
    inside = np.minimum(np.max(q, axis=1), 0.0)
    return outside + inside - round_r


def sd_plane_cut(sd, p, axis, value, keep_below=True):
    """Intersect an SDF with a half space (used for short hems and hair)."""
    plane = (p[:, axis] - value) if keep_below else (value - p[:, axis])
    return np.maximum(sd, plane)


# ------------------------------------------------------------------ materials
class Mat:
    def __init__(self, base, shadow, highlight, spec=0.20, shine=24.0,
                 rim=(0.42, 0.78, 1.0), rim_gain=0.55, wrap=0.35):
        self.base = np.array(base, np.float32)
        self.shadow = np.array(shadow, np.float32)
        self.highlight = np.array(highlight, np.float32)
        self.spec = spec
        self.shine = shine
        self.rim = np.array(rim, np.float32)
        self.rim_gain = rim_gain
        self.wrap = wrap


SKIN = Mat(base=(0.74, 0.78, 0.86), shadow=(0.12, 0.16, 0.26),
           highlight=(0.98, 0.99, 1.00), spec=0.30, shine=26.0,
           rim=(0.48, 0.82, 1.0), rim_gain=0.85)
SUIT = Mat(base=(0.26, 0.33, 0.47), shadow=(0.07, 0.10, 0.17),
           highlight=(0.46, 0.56, 0.74), spec=0.30, shine=30.0,
           rim=(0.34, 0.64, 0.96), rim_gain=0.65)
SHOE = Mat(base=(0.13, 0.16, 0.24), shadow=(0.02, 0.03, 0.06),
           highlight=(0.34, 0.40, 0.54), spec=0.45, shine=44.0,
           rim=(0.28, 0.55, 0.90), rim_gain=0.60)
HAIR = Mat(base=(0.10, 0.13, 0.20), shadow=(0.01, 0.02, 0.04),
           highlight=(0.30, 0.36, 0.48), spec=0.55, shine=52.0,
           rim=(0.34, 0.60, 0.92), rim_gain=0.80)
STEEL = Mat(base=(0.52, 0.62, 0.75), shadow=(0.05, 0.09, 0.16),
            highlight=(0.95, 0.99, 1.00), spec=1.0, shine=80.0,
            rim=(0.55, 0.85, 1.0), rim_gain=1.0)
IRON = Mat(base=(0.22, 0.26, 0.34), shadow=(0.02, 0.03, 0.05),
           highlight=(0.56, 0.62, 0.72), spec=0.65, shine=38.0,
           rim=(0.40, 0.62, 0.90), rim_gain=0.80)
RUBBER = Mat(base=(0.11, 0.13, 0.18), shadow=(0.01, 0.02, 0.03),
             highlight=(0.26, 0.30, 0.38), spec=0.12, shine=10.0,
             rim=(0.25, 0.45, 0.75), rim_gain=0.40)

GROUPS = {"skin": SKIN, "suit": SUIT, "shoe": SHOE, "hair": HAIR,
          "steel": STEEL, "iron": IRON, "rubber": RUBBER}
GROUP_ORDER = list(GROUPS.keys())

KEY = np.array([-0.52, -0.60, -0.61], np.float32)
KEY = KEY / np.linalg.norm(KEY)
FILL = np.array([0.66, -0.20, -0.36], np.float32)
FILL = FILL / np.linalg.norm(FILL)
RIML = np.array([0.74, 0.10, -0.42], np.float32)
RIML = RIML / np.linalg.norm(RIML)


# ------------------------------------------------------------------ body field
def sd_oellipsoid(p, c, axes, radii):
    """Ellipsoid oriented along (lateral, up, forward) unit axes."""
    q = p - c
    local = np.stack([q @ axes[0], q @ axes[1], q @ axes[2]], axis=1)
    return sd_ellipsoid(local, np.zeros(3, np.float32), np.asarray(radii, np.float32))


class Body:
    """A posed 3D body: depth-sorted primitives with smooth unions.

    The athlete is built in a body frame:
      lateral  (left <-> right shoulder axis)
      up       (screen up, so the pose data stays in screen orientation)
      forward  (chest direction: towards the camera for front views, in-plane
                for side views, taken from the foot direction)
    Limb pairs are separated along the lateral axis, which is what makes the
    side view read as a real 3D body instead of two stacked silhouettes.
    """

    def __init__(self, sk, pose):
        self.sk = sk
        self.pose = pose
        self.prims = []
        self._frame()
        self._build()

    # -- frame -------------------------------------------------------
    def _frame(self):
        """Orthonormal body frame from the 2D pose.

        up      - pelvis -> head direction (screen plane)
        forward - chest direction; perpendicular to `up`. For front/back views it
                  is the camera axis, for side views it is inferred from which way
                  the athlete is lying/standing (using the toes when ambiguous).
        lateral - cross(up, forward): where the left/right limbs live.
        """
        sk = self.sk
        view = self.pose.get("view", "side")
        up2 = np.array(sk["shoulder"], np.float64) - np.array(sk["root"], np.float64)
        if np.linalg.norm(up2) < 1e-6:
            up2 = np.array([0.0, -1.0])
        up2 = up2 / np.linalg.norm(up2)
        up3 = np.array([up2[0], up2[1], 0.0], np.float32)

        if view in ("front", "back"):
            fwd = np.array([0.0, 0.0, -1.0], np.float32) if view == "front" else np.array([0.0, 0.0, 1.0], np.float32)
        else:
            perp = np.array([-up2[1], up2[0], 0.0], np.float32)
            toe2 = np.array(sk["toe_n"], np.float64) - np.array(sk["ankle_n"], np.float64)
            # prefer the perpendicular that points "up the screen" (lying poses),
            # otherwise follow the toes (standing poses)
            if abs(float(perp[1])) > 0.25:
                fwd = perp if perp[1] < 0 else -perp
            else:
                fwd = perp if float(np.dot(perp[:2], toe2)) >= 0 else -perp
        fwd = fwd / max(float(np.linalg.norm(fwd)), 1e-6)

        lat = np.cross(up3, fwd)
        if float(np.linalg.norm(lat)) < 1e-5:
            lat = np.array([1.0, 0.0, 0.0], np.float32)
        lat /= float(np.linalg.norm(lat))
        up = np.cross(fwd, lat)
        if float(np.dot(up, up3)) < 0:
            up = -up
            fwd = -fwd
            lat = np.cross(up, fwd)
        self.U = up / max(float(np.linalg.norm(up)), 1e-6)
        self.L = lat / max(float(np.linalg.norm(lat)), 1e-6)
        self.F = fwd
        self.side_view = view == "side"

    def p3(self, pt2, extra=0.0):
        return (np.array([pt2[0], pt2[1], 0.0], np.float32) + self.F * extra).astype(np.float32)

    def pair(self, key, side, hw):
        """Joint on the near (`n`) or far (`f`) side of the body."""
        a = np.array(self.sk[key + "n"], np.float32)
        b = np.array(self.sk[key + "f"], np.float32)
        mid = (a + b) / 2.0
        mid3 = np.array([mid[0], mid[1], 0.0], np.float32)
        lat_hw = min(max(float(np.linalg.norm(a - b)) / 2.0, hw * 0.42), hw)
        sign = 1.0 if side == "n" else -1.0
        return mid3 + self.L * (sign * lat_hw)

    def mid(self, key):
        a = np.array(self.sk[key + "n"], np.float32)
        b = np.array(self.sk[key + "f"], np.float32)
        m = (a + b) / 2.0
        return np.array([m[0], m[1], 0.0], np.float32)

    # -- primitive registration --------------------------------------
    def add(self, group, fn):
        self.prims.append((group, fn))

    def ellipsoid(self, group, c, r):
        c = np.asarray(c, np.float32)
        r = np.asarray(r, np.float32)
        self.add(group, lambda p, c=c, r=r: sd_ellipsoid(p, c, r))

    def oellipsoid(self, group, c, lat, up, depth):
        c = np.asarray(c, np.float32)
        axes = (self.L, self.U, self.F)
        r = np.array([lat, up, depth], np.float32)
        self.add(group, lambda p, c=c, r=r, ax=axes: sd_oellipsoid(p, c, ax, r))

    def capsule(self, group, a, b, r1, r2=None):
        a = np.asarray(a, np.float32)
        b = np.asarray(b, np.float32)
        self.add(group, lambda p, a=a, b=b, r1=r1, r2=r2: sd_capsule(p, a, b, r1, r2))

    def smooth(self, group, fn, k=0.02):
        self.add(group, fn)

    # -- body --------------------------------------------------------
    def _build(self):
        sk = self.sk
        L, U, F = self.L, self.U, self.F

        # when the athlete is horizontal, the torso is seen from an angle where the
        # lateral bulges dominate; compress them so the silhouette stays readable
        vert = abs(float(U[1]))
        lat_scale = 0.80 + 0.20 * min(1.0, vert)
        relief_scale = 0.30 + 0.70 * min(1.0, vert * 1.4)
        self._relief = relief_scale

        root = self.p3(sk["root"])
        sh = self.p3(sk["shoulder"])
        pelvis = root + U * -0.010
        waist = root + U * 0.070
        ribs = root + U * 0.150
        chest = sh + U * -0.045

        # ---------------- torso surface ----------------
        def torso_sdf(p):
            d = sd_oellipsoid(p, chest, (L, self.U, F), (0.078 * lat_scale, 0.080, 0.058))
            d = smin(d, sd_oellipsoid(p, ribs, (L, self.U, F), (0.074 * lat_scale, 0.066, 0.055)), 0.038)
            d = smin(d, sd_oellipsoid(p, waist, (L, self.U, F), (0.060 * lat_scale, 0.054, 0.048)), 0.038)
            d = smin(d, sd_oellipsoid(p, pelvis, (L, self.U, F), (0.070 * lat_scale, 0.056, 0.052)), 0.038)
            # trapezius wedge into the neck
            d = smin(d, sd_oellipsoid(p, sh + U * -0.010, (L, self.U, F), (0.070, 0.040, 0.040)), 0.032)
            return d

        def muscle_sdf(p):
            # pectorals: broad flat slabs high on the chest, facing forward
            m = sd_oellipsoid(p, chest + L * 0.036 + F * 0.036 + U * -0.008,
                              (L, self.U, self.F), (0.036, 0.024, 0.014))
            m = smin(m, sd_oellipsoid(p, chest + L * -0.036 + F * 0.036 + U * -0.008,
                                      (L, self.U, self.F), (0.036, 0.024, 0.014)), 0.014)
            # abdominals: three soft rows of two
            for row in range(3):
                yy = ribs + U * (-0.038 - row * 0.028)
                for sx in (1.0, -1.0):
                    m = smin(m, sd_ellipsoid(p, yy + L * (sx * 0.016) + F * 0.042,
                                             _v(0.016, 0.014, 0.012)), 0.012)
            # obliques
            m = smin(m, sd_ellipsoid(p, waist + L * 0.048 + F * 0.018, _v(0.014, 0.028, 0.022)), 0.018)
            m = smin(m, sd_ellipsoid(p, waist + L * -0.048 + F * 0.018, _v(0.014, 0.028, 0.022)), 0.018)
            # lats: wings on both sides of the ribcage
            m = smin(m, sd_oellipsoid(p, ribs + L * 0.062 + U * -0.010, (L, self.U, self.F),
                                      (0.018, 0.046, 0.036)), 0.022)
            m = smin(m, sd_oellipsoid(p, ribs + L * -0.062 + U * -0.010, (L, self.U, self.F),
                                      (0.018, 0.046, 0.036)), 0.022)
            # spinal ridge at the back
            m = smin(m, sd_oellipsoid(p, ribs + U * -0.030 - F * 0.040, (L, self.U, self.F),
                                      (0.016, 0.070, 0.014)), 0.020)
            return m

        self.add("skin", torso_sdf)
        if relief_scale > 0.55:
            self.add("skin", muscle_sdf)

        # ---------------- glutes ----------------
        hip_n = self.pair("hip_", "n", 0.036)
        hip_f = self.pair("hip_", "f", 0.036)
        glute_c = (hip_n + hip_f) / 2 + U * 0.016 - F * 0.040

        def glute_sdf(p):
            d = sd_ellipsoid(p, glute_c + L * 0.034, _v(0.044, 0.038, 0.032))
            d = smin(d, sd_ellipsoid(p, glute_c + L * -0.034, _v(0.044, 0.038, 0.032)), 0.018)
            return d
        self.add("skin", glute_sdf)

        # ---------------- neck + head ----------------
        neck_top = self.p3(sk["neck_top"])
        head = self.p3(sk["head"]) + U * 0.016
        self.capsule("skin", sh + U * -0.008, neck_top + U * -0.008, 0.040, 0.034)
        hr = 0.056

        def head_sdf(p):
            # smooth featureless mannequin head: skull + jaw + cheek mass
            d = sd_oellipsoid(p, head, (L, self.U, F), (hr * 0.86, hr * 1.00, hr * 0.92))
            d = smin(d, sd_oellipsoid(p, head - U * hr * 0.42 + F * hr * 0.10, (L, self.U, F),
                                      (hr * 0.66, hr * 0.52, hr * 0.70)), 0.020)
            # chin
            d = smin(d, sd_ellipsoid(p, head - U * hr * 0.86 + F * hr * 0.30,
                                     _v(hr * 0.26, hr * 0.20, hr * 0.24)), 0.014)
            # ears
            for sx in (1.0, -1.0):
                d = smin(d, sd_ellipsoid(p, head + L * (sx * hr * 0.84) - U * hr * 0.06 + F * hr * 0.04,
                                         _v(hr * 0.09, hr * 0.20, hr * 0.14)), 0.012)
            return d
        self.add("skin", head_sdf)
        self.add("hair", hair_field(head, hr, L, U, F))

        # ---------------- arms ----------------
        for side in ("n", "f"):
            s = self.pair("sh_", side, 0.070)
            e = self.pair("elbow_", side, 0.058)
            w = self.pair("wrist_", side, 0.050)
            h = self.pair("hand_", side, 0.048)
            self.ellipsoid("skin", s - U * 0.008, _v(0.034, 0.033, 0.034))
            self.capsule("skin", s, e, 0.033, 0.024)
            self.capsule("skin", e, w, 0.024, 0.017)
            # biceps / triceps
            mid = s + (e - s) * 0.52
            self.ellipsoid("skin", mid + F * 0.010, _v(0.021, 0.030, 0.022))
            # forearm belly
            fm = e + (w - e) * 0.30
            self.ellipsoid("skin", fm, _v(0.021, 0.024, 0.020))
            # hand: flattened paddle aligned with the forearm
            hd = w + (h - w) * 0.55
            self.capsule("skin", hd, h + (h - w) * 0.40, 0.020, 0.014)

        # ---------------- legs ----------------
        hips = {}
        knees = {}
        for side in ("n", "f"):
            hipj = self.pair("hip_", side, 0.036)
            knee = self.pair("knee_", side, 0.030)
            ankle = self.pair("ankle_", side, 0.028)
            toe = self.pair("toe_", side, 0.026)
            hips[side] = hipj
            knees[side] = knee
            self.capsule("skin", hipj, knee, 0.052, 0.032)
            # quadriceps mass on the front of the thigh
            qm = hipj + (knee - hipj) * 0.44
            self.ellipsoid("skin", qm + F * 0.010, _v(0.034, 0.058, 0.030))
            self.capsule("skin", knee, ankle, 0.033, 0.019)
            cm = knee + (ankle - knee) * 0.30
            self.ellipsoid("skin", cm - F * 0.010, _v(0.027, 0.040, 0.024))
            self.ellipsoid("skin", ankle, _v(0.021, 0.021, 0.021))

            # shoe aligned with the foot direction
            foot = toe - ankle
            fl = float(np.linalg.norm(foot)) + 1e-6
            self.add("shoe", shoe_field(ankle, toe, fl))

            # shorts leg
            hem = hipj + (knee - hipj) * 0.42
            self.capsule("suit", hipj + (knee - hipj) * -0.08, hem, 0.052, 0.042)

        # ---------------- shorts pelvis ----------------
        def shorts_sdf(p):
            d = sd_oellipsoid(p, pelvis + U * -0.002 - F * 0.006, (L, self.U, F), (0.072, 0.060, 0.062))
            for side in ("n", "f"):
                hj = hips[side]
                hem = hj + (knees[side] - hj) * 0.42
                d = smin(d, sd_capsule(p, hj + (knees[side] - hj) * -0.08, hem, 0.053, 0.043), 0.026)
            return d
        self.add("suit", shorts_sdf)

    # ---------------------------------------------------------------- eval
    def _grouped(self):
        """Primitives bucketed per material so each material is one smooth surface."""
        if getattr(self, "_buckets", None) is None:
            buckets = {}
            for g, fn in self.prims:
                buckets.setdefault(g, []).append(fn)
            self._buckets = [(GROUP_ORDER.index(g), fns) for g, fns in buckets.items()]
        return self._buckets

    def sdf(self, p, want_group=False):
        best = None
        best_g = None
        for gi, fns in self._grouped():
            gname = GROUP_ORDER[gi]
            k = {"skin": 0.013, "suit": 0.020, "hair": 0.008}.get(gname, 0.007)
            d = fns[0](p)
            for fn in fns[1:]:
                d = smin(d, fn(p), k)
            if best is None:
                best = d
                best_g = np.full(len(p), gi, np.int32)
            else:
                sel = d < best
                best = np.where(sel, d, best)
                best_g = np.where(sel, gi, best_g)
        return (best, best_g) if want_group else best


def shoe_field(ankle, toe, flen):
    """Rounded shoe box aligned with the foot direction."""
    d = (toe - ankle) / (flen + 1e-9)
    ang = math.atan2(float(d[1]), float(d[0]))
    ca, sa = math.cos(-ang), math.sin(-ang)
    c = ankle + (toe - ankle) * 0.46 + np.array([0.0, 0.008, 0.0], np.float32)

    def fn(p):
        q = p - c
        x = q[:, 0] * ca - q[:, 1] * sa
        y = q[:, 0] * sa + q[:, 1] * ca
        local = np.stack([x, y, q[:, 2]], axis=1)
        return sd_box(local, np.zeros(3, np.float32), _v(flen * 0.58, 0.023, 0.026), 0.019)
    return fn


def hair_field(head, r, L, U, F):
    """Short hair: a slightly larger skull shell trimmed by a tilted cap plane.

    The cap normal leans back, so the hairline sits high above the brow in front
    and wraps lower around the back of the head - the classic short-back-and-sides
    look of the reference mannequins.
    """
    c = head + U * r * 0.03
    normal = (U * 0.94 - F * 0.34)
    normal = normal / max(float(np.linalg.norm(normal)), 1e-6)

    def fn(p):
        d = sd_oellipsoid(p, c, (L, U, F), (r * 0.93, r * 1.03, r * 1.00))
        plane = (p - (head + U * r * 0.02)) @ normal     # >0 inside the cap
        return np.maximum(d, -plane)
    return fn

def _blend_all(p, fns, k):
    d = fns[0](p)
    for fn in fns[1:]:
        d = smin(d, fn(p), k)
    return d


def build_gear(body, sk, pose, when, near_side):
    """Register equipment primitives, pushed in front of / behind the athlete."""
    specs = list(pose.get("gear", ()))
    fn = pose.get("gear_fn")
    if fn:
        specs += list(fn(sk, pose))
    zoff = -0.075 if when == "front" else 0.075
    for spec in specs:
        if len(spec) >= 2 and spec[1] != when:
            continue
        kind = spec[0]
        if kind == "floor":
            y = spec[2] if len(spec) > 2 else 0.978
            body.capsule("rubber", _v(0.0, y, 0.09), _v(1.0, y, 0.09), 0.004)
        elif kind == "bench":
            y = spec[2]
            body.capsule("rubber", _v(0.245, y, 0.0), _v(0.80, y, 0.0), 0.042)
            for lx in (0.31, 0.72):
                body.capsule("iron", _v(lx, y + 0.02, 0.0), _v(lx, min(0.975, y + 0.24), 0.0), 0.014, 0.019)
        elif kind == "incline":
            bx, by, ang = spec[2], spec[3], spec[4]
            a = math.radians(ang)
            top = (bx + math.sin(a) * 0.40, by + math.cos(a) * 0.40)
            body.capsule("rubber", _v(bx, by, 0.0), _v(top[0], top[1], 0.0), 0.042)
            body.capsule("iron", _v(top[0], top[1], 0.0), _v(top[0] - 0.03, min(0.975, by + 0.20), 0.0), 0.013, 0.018)
        elif kind == "bar":
            p1 = _v(spec[2], spec[3], zoff)
            p2 = _v(spec[4], spec[5], zoff)
            body.capsule("steel", p1, p2, 0.010)
            for t in (0.02, 0.98):
                c = p1 + (p2 - p1) * t
                body.ellipsoid("iron", c, _v(0.050, 0.050, 0.016))
                body.ellipsoid("steel", c, _v(0.029, 0.029, 0.021))
        elif kind == "dumbbell":
            c = _v(spec[2], spec[3], zoff)
            ang = spec[4] if len(spec) > 4 else 90
            a = math.radians(ang + 90)
            off = _v(math.cos(a) * 0.050, math.sin(a) * 0.050, 0)
            body.capsule("iron", c - off, c + off, 0.016)
            body.ellipsoid("iron", c - off, _v(0.032, 0.032, 0.032))
            body.ellipsoid("iron", c + off, _v(0.032, 0.032, 0.032))
        elif kind == "kettlebell":
            c = _v(spec[2], spec[3], zoff)
            body.ellipsoid("iron", c, _v(0.044, 0.046, 0.044))
            body.capsule("iron", c + _v(-0.024, -0.040, 0), c + _v(0.024, -0.040, 0), 0.010)
        elif kind == "cable":
            x0, yt, yb, x1, y1 = spec[2], spec[3], spec[4], spec[5], spec[6]
            body.capsule("iron", _v(x0, yt, 0.12), _v(x0, yb, 0.12), 0.018)
            body.capsule("steel", _v(x0, y1, zoff), _v(x1, y1, zoff), 0.004)
        elif kind == "stack":
            x0, y0 = spec[2], spec[3]
            for i in range(6):
                yy = y0 + i * 0.040
                body.capsule("iron", _v(x0, yy, 0.13), _v(x0 + 0.076, yy, 0.13), 0.022)
        elif kind == "wheel":
            c = _v(spec[2], spec[3], zoff)
            body.ellipsoid("rubber", c, _v(0.054, 0.054, 0.018))
            body.ellipsoid("steel", c, _v(0.028, 0.028, 0.022))
            body.capsule("steel", c + _v(-0.080, 0, 0), c + _v(0.080, 0, 0), 0.009)
        elif kind == "rope":
            body.capsule("steel", _v(spec[2][0], spec[2][1], zoff), _v(spec[3][0], spec[3][1], zoff), 0.006)
        elif kind == "mat":
            y = spec[2]
            body.capsule("rubber", _v(0.14, y, 0.02), _v(0.88, y, 0.02), 0.018)


def _shade(n, mat, ao, glow):
    lam = np.clip(n @ KEY, 0.0, 1.0)
    fil = np.clip(n @ FILL, 0.0, 1.0)
    wrap = np.clip((n @ KEY + mat.wrap) / (1.0 + mat.wrap), 0.0, 1.0)
    t = np.clip(0.16 + wrap * 0.72 + fil * 0.18, 0.0, 1.0)

    col = mat.shadow + (mat.base - mat.shadow) * t[:, None]
    hi = np.clip((t - 0.70) / 0.42, 0.0, 1.0)[:, None]
    col = col + (mat.highlight - col) * (hi * 0.55)

    # rim light hugging the silhouette
    rim = np.clip(1.0 - np.abs(n @ np.array([0.0, 0.0, -1.0], np.float32)), 0.0, 1.0) ** 2.4
    rl = np.clip(n @ RIML, 0.0, 1.0)
    col = col + (mat.rim - col) * (rim * rl * mat.rim_gain)[:, None]

    half_v = KEY + np.array([0.0, 0.0, -1.0], np.float32)
    half_v /= np.linalg.norm(half_v)
    spec = np.clip(n @ half_v, 0.0, 1.0) ** mat.shine
    col = col + spec[:, None] * (mat.spec * 0.6)

    col = col * ao[:, None]

    if glow is not None:
        g, hot = glow
        col = col * (1.0 - 0.22 * g[:, None])
        col = col + np.array([0.95, 0.18, 0.28], np.float32)[None, :] * (g * 0.20)[:, None]
        col = col + np.array([1.0, 0.72, 0.42], np.float32)[None, :] * (hot * 0.34)[:, None]
    return np.clip(col, 0.0, 1.0)


def _glow_mask(pose, sk, pts):
    """2D-ish heat field evaluated at 3D surface points."""
    specs = pose.get("glow", ())
    if specs and isinstance(specs[0], str):
        specs = (specs,)
    if not specs:
        return None
    xy = pts[:, :2]
    g = np.zeros(len(pts), np.float32)
    hot = np.zeros(len(pts), np.float32)

    def seg(a, b, w, inten):
        a = np.asarray(a, np.float32)
        b = np.asarray(b, np.float32)
        ba = b - a
        t = np.clip(((xy - a) @ ba) / (float(ba @ ba) + 1e-9), 0.0, 1.0)
        d = np.linalg.norm(xy - (a[None, :] + ba[None, :] * t[:, None]), axis=1)
        np.maximum(g, np.exp(-((d / (w * 1.7)) ** 2)) * inten, out=g)
        np.maximum(hot, np.clip(1.0 - d / (w * 0.95), 0.0, 1.0) ** 1.2 * inten, out=hot)

    def blob(c, rx, ry, inten):
        d = np.sqrt(((xy[:, 0] - c[0]) / rx) ** 2 + ((xy[:, 1] - c[1]) / ry) ** 2)
        np.maximum(g, np.exp(-((d / 0.72) ** 2)) * inten, out=g)
        np.maximum(hot, np.exp(-((d / 0.34) ** 2)) * inten, out=hot)

    for spec in specs:
        kind = spec[0]
        a = float(spec[1]) if len(spec) > 2 else 0.10
        b = float(spec[2]) if len(spec) > 2 else 0.90
        inten = float(spec[3]) if len(spec) > 3 else 1.0
        if kind.startswith("arm_"):
            s = kind[-1]
            seg(sk["elbow_" + s] + (np.array(sk["sh_" + s]) - sk["elbow_" + s]) * a,
                sk["elbow_" + s] + (np.array(sk["sh_" + s]) - sk["elbow_" + s]) * b, 0.036, inten)
        elif kind.startswith("fore_"):
            s = kind[-1]
            seg(sk["elbow_" + s] + (np.array(sk["wrist_" + s]) - sk["elbow_" + s]) * a,
                sk["elbow_" + s] + (np.array(sk["wrist_" + s]) - sk["elbow_" + s]) * b, 0.030, inten)
        elif kind.startswith("thigh_"):
            s = kind[-1]
            seg(sk["knee_" + s] + (np.array(sk["hip_" + s]) - sk["knee_" + s]) * a,
                sk["knee_" + s] + (np.array(sk["hip_" + s]) - sk["knee_" + s]) * b, 0.052, inten)
        elif kind.startswith("shin_"):
            s = kind[-1]
            seg(sk["knee_" + s] + (np.array(sk["ankle_" + s]) - sk["knee_" + s]) * a,
                sk["knee_" + s] + (np.array(sk["ankle_" + s]) - sk["knee_" + s]) * b, 0.036, inten)
        elif kind == "chest":
            c = sk["shoulder"] + (np.array(sk["root"]) - sk["shoulder"]) * 0.28
            if sk["side_view"]:
                blob(c, 0.075, 0.040, inten)
            else:
                blob((c[0] - 0.048, c[1]), 0.044, 0.036, inten)
                blob((c[0] + 0.048, c[1]), 0.044, 0.036, inten)
        elif kind == "abs":
            c = sk["shoulder"] + (np.array(sk["root"]) - sk["shoulder"]) * 0.66
            blob(c, 0.052 if sk["side_view"] else 0.062, 0.056, inten)
        elif kind == "back":
            c = sk["shoulder"] + (np.array(sk["root"]) - sk["shoulder"]) * 0.42
            blob(c, 0.086, 0.070, inten)
        elif kind == "delts":
            for s in ("n", "f"):
                blob(sk["sh_" + s], 0.046, 0.036, inten)
        elif kind == "glute":
            c = [(sk["hip_n"][0] + sk["hip_f"][0]) / 2, (sk["hip_n"][1] + sk["hip_f"][1]) / 2]
            blob(c, 0.084, 0.048, inten)
    return g, hot


# ------------------------------------------------------------------ raymarch
def render(pose, size=256, ss=1, bounds=None, near=0.070, far_step=96):
    """Raymarch one pose against the body field.

    Returns (rgb float32 HxWx3, alpha float32 HxW). Only live rays are advanced,
    which keeps a 256px frame down to a couple of seconds.
    """
    sk = pose_skel(pose)
    body = Body(sk, pose)
    near_side = pose.get("near_side", "n")
    build_gear(body, sk, pose, "back", near_side)
    build_gear(body, sk, pose, "front", near_side)

    W = size * ss
    if bounds is None:
        bounds = default_bounds(sk)
    x0, y0, x1, y1 = bounds
    xs = np.linspace(x0, x1, W, dtype=np.float32)
    ys = np.linspace(y0, y1, W, dtype=np.float32)
    X, Y = np.meshgrid(xs, ys)
    pts = np.stack([X.ravel(), Y.ravel(), np.full(W * W, -near, np.float32)], axis=1)

    active = np.arange(len(pts))
    zhit = np.full(len(pts), np.nan, np.float32)
    for _ in range(far_step):
        if len(active) == 0:
            break
        p = pts[active]
        d = body.sdf(p)
        p[:, 2] += np.maximum(d * 0.90, 0.00040)
        hitnow = d < 0.0018
        if hitnow.any():
            zhit[active[hitnow]] = p[hitnow, 2]
        keep = (~hitnow) & (p[:, 2] < near)
        active = active[keep]
        pts[active] = p[keep]

    img = np.zeros((W * W, 3), np.float32)
    alpha = np.zeros(W * W, np.float32)
    sel = np.where(~np.isnan(zhit))[0]
    if len(sel):
        q = pts[sel].copy()
        q[:, 2] = zhit[sel]
        _, groups = body.sdf(q, want_group=True)
        eps = 0.0013
        nrm = np.stack([
            body.sdf(q + _v(eps, 0, 0)) - body.sdf(q - _v(eps, 0, 0)),
            body.sdf(q + _v(0, eps, 0)) - body.sdf(q - _v(0, eps, 0)),
            body.sdf(q + _v(0, 0, eps)) - body.sdf(q - _v(0, 0, eps)),
        ], axis=1)
        nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-6)

        # SDF ambient occlusion
        occ = np.zeros(len(sel), np.float32)
        for i, dist in enumerate((0.010, 0.022, 0.040, 0.064)):
            occ += (dist - body.sdf(q + nrm * dist)) * (1.0 / (1 << i))
        ao = np.clip(1.0 - 2.4 * np.clip(occ, 0.0, 0.45), 0.18, 1.0)

        # depth cue: the far side of the body sinks into shadow
        zc = zhit[sel]
        cue = np.clip(1.08 - (zc + near) * 2.6, 0.55, 1.08)

        glow = _glow_mask(pose, sk, q)
        rgb = np.zeros((len(sel), 3), np.float32)
        for gi, gname in enumerate(GROUP_ORDER):
            m = groups == gi
            if not m.any():
                continue
            rgb[m] = _shade(nrm[m], GROUPS[gname], (ao * cue)[m],
                            None if glow is None else (glow[0][m], glow[1][m]))
        img[sel] = rgb
        alpha[sel] = 1.0

    return img.reshape(W, W, 3), alpha.reshape(W, W)


def pose_skel(pose):
    from mannequin import skeleton
    return skeleton(pose)


def default_bounds(sk, pad=0.045):
    xs = []
    ys = []
    for key in sk:
        v = sk[key]
        if isinstance(v, tuple) and len(v) == 2 and all(isinstance(t, (int, float)) for t in v):
            xs.append(v[0])
            ys.append(v[1])
    x0, x1 = min(xs) - pad, max(xs) + pad
    y0, y1 = min(ys) - pad, max(ys) + pad
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    side = max(x1 - x0, y1 - y0)
    return (cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)


def render_loop(poses, size=256, pad=0.045, ss=1):
    """Render a loop with one shared crop so the animation never jitters."""
    from PIL import Image
    H = size * 2
    raw = [render(p, size=H, ss=ss) for p in poses]
    boxes = []
    for img, a in raw:
        ys, xs = np.nonzero(a > 0.4)
        if len(xs):
            boxes.append((int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1))
    if not boxes:
        return [Image.new("RGBA", (size, size), (0, 0, 0, 0)) for _ in poses]
    box = (min(b[0] for b in boxes), min(b[1] for b in boxes),
           max(b[2] for b in boxes), max(b[3] for b in boxes))
    inner = int(size * (1 - 2 * pad))
    bw, bh = box[2] - box[0], box[3] - box[1]
    k = min(inner / max(bw, 1), inner / max(bh, 1))
    nw, nh = max(1, int(bw * k)), max(1, int(bh * k))
    out = []
    for img, a in raw:
        rgba = np.concatenate([img, a[..., None]], axis=2)
        im = Image.fromarray((np.clip(rgba, 0, 1) * 255).astype(np.uint8), "RGBA")
        crop = im.crop(box).resize((nw, nh), Image.LANCZOS)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        canvas.paste(crop, ((size - nw) // 2, (size - nh) // 2), crop)
        out.append(canvas)
    return out
