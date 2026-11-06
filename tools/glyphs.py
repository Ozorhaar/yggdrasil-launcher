#!/usr/bin/env python3
"""Yggdrasil glyphs the launcher carves on the shields of apps that bring no themed (monochrome) icon.

Each glyph is a white silhouette in a 108x108 viewport (like an adaptive icon's monochrome layer);
the launcher tints it dark and scales it 1.22x onto the shield, exactly as it does monochrome layers.
icons/Glyphs.kt says which app gets which glyph and which shield face.

    python3 tools/glyphs.py      # from the repo root: writes app/src/main/res/drawable/glyph_*.xml,
                                 # plus shield previews in /tmp/ygg-glyphs/
"""
import math
import os
import re


def circ(cx, cy, r): return f"M{cx-r:.2f},{cy:.2f} A{r},{r} 0 1 0 {cx+r:.2f},{cy:.2f} A{r},{r} 0 1 0 {cx-r:.2f},{cy:.2f} Z"
def rect(x0, y0, x1, y1): return f"M{x0},{y0} L{x1},{y0} L{x1},{y1} L{x0},{y1} Z"


G = {}  # name -> [(pathData, evenOdd)]

# Calendar: a runestone with three rows of carved day-marks and a sun ring
stone = "M38,76 L38,44 C38,32 46,26 54,26 C62,26 70,32 70,44 L70,76 Z"
holes = [circ(54, 41, 4.2), circ(54, 41, 2.0)]
for y in (53, 61, 69):
    for i in range(5):
        x = 42.6 + i * 5.7
        holes.append(rect(x, y - 2.4, x + 2.2, y + 2.4))
G["calendar"] = [(stone + " " + " ".join(holes), True)]


# Voicemail: Gjallarhorn, a curved cone from mouthpiece (bottom left) to bell (top right), two bands, sounding
def _horn():
    P0, P1, P2, P3 = (32, 74), (44, 74), (52, 50), (66, 38)

    def bez(t):
        x = (1-t)**3*P0[0] + 3*(1-t)**2*t*P1[0] + 3*(1-t)*t**2*P2[0] + t**3*P3[0]
        y = (1-t)**3*P0[1] + 3*(1-t)**2*t*P1[1] + 3*(1-t)*t**2*P2[1] + t**3*P3[1]
        return x, y
    N = 40; up = []; dn = []
    for i in range(N + 1):
        t = i / N; x, y = bez(t)
        if t == 1:
            x1, y1 = bez(t - 0.01); dx, dy = x - x1, y - y1
        else:
            x2, y2 = bez(t + 0.01); dx, dy = x2 - x, y2 - y
        L = math.hypot(dx, dy); nx, ny = -dy / L, dx / L
        w = 0.9 + 7.6 * t ** 1.5
        up.append((x + nx * w, y + ny * w)); dn.append((x - nx * w, y - ny * w))
    d = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in up + dn[::-1]) + " Z"
    for t in (0.45, 0.7):  # bands cut through the horn
        i = int(t * N); (ax, ay), (bx, by) = up[i], dn[i]; (cx_, cy_), (ex, ey) = up[i + 2], dn[i + 2]
        d += f" M{ax:.2f},{ay:.2f} L{cx_:.2f},{cy_:.2f} L{ex:.2f},{ey:.2f} L{bx:.2f},{by:.2f} Z"
    return d


def arc(r, w):
    a0, a1 = -80, 0; cx, cy = 69, 40
    p = lambda r, a: (cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
    x0, y0 = p(r, a0); x1, y1 = p(r, a1); x2, y2 = p(r + w, a1); x3, y3 = p(r + w, a0)
    return f"M{x0:.2f},{y0:.2f} A{r},{r} 0 0 1 {x1:.2f},{y1:.2f} L{x2:.2f},{y2:.2f} A{r+w},{r+w} 0 0 0 {x3:.2f},{y3:.2f} Z"


G["voicemail"] = [(_horn(), True), (arc(9.5, 2.2), False), (arc(14, 2.2), False)]


# Calculator: crossed tally sticks with counting notches
def stick(cx, cy, L, W, ang, notches):
    a = math.radians(ang); ux, uy = math.cos(a), math.sin(a); vx, vy = -uy, ux
    P = lambda s, t: (cx + ux * s + vx * t, cy + uy * s + vy * t)
    pts = [P(-L / 2, -W / 2)]
    for i in range(notches):
        s = -L / 2 + L * (i + 1) / (notches + 1)
        pts += [P(s - 1.6, -W / 2), P(s, -W / 2 + 2.6), P(s + 1.6, -W / 2)]
    pts += [P(L / 2, -W / 2), P(L / 2 + 2, 0), P(L / 2, W / 2), P(-L / 2, W / 2), P(-L / 2 - 2, 0)]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"


G["calculator"] = [(stick(54, 50, 40, 7, -18, 6), False), (stick(54, 63, 30, 5.4, -18, 4), False)]

# Mail (Nine): a folded letter under a wax seal
G["mail"] = [("M34,40 L74,40 L74,70 L34,70 Z M37.5,43 L54,56.2 L70.5,43 Z M37,67 L37,45.5 L50.5,56.4 Z "
              "M71,67 L71,45.5 L57.5,56.4 Z M39.5,67 L52,57.6 L54,59.2 L56,57.6 L68.5,67 Z", True),
             (circ(54, 59.5, 5.2), False)]


# Game (Monopoly Go): rolling the bones, two bone dice carved with ring-and-dot pips like Viking ones
BONE_SHAPE = ("M -13,-3.8 L 13,-3.8 C 14,-8 18.6,-10 21.4,-7.4 C 23.4,-5.4 23,-2.4 21.2,-1 C 23,0.4 23.4,3.4 21.4,5.4 "
              "C 18.6,8 14,6 13,3.8 L -13,3.8 C -14,6 -18.6,8 -21.4,5.4 C -23.4,3.4 -23,0.4 -21.2,-1 C -23,-2.4 -23.4,-5.4 -21.4,-7.4 "
              "C -18.6,-10 -14,-8 -13,-3.8 Z")


def placed(d, cx, cy, s, ang):
    a = math.radians(ang); c, si = math.cos(a), math.sin(a); out = []; nums = []
    for t in re.findall(r'[A-Za-z]|-?\d+\.?\d*', d):
        if t.isalpha():
            out.append(t)
        else:
            nums.append(float(t))
            if len(nums) == 2:
                x, y = nums; out.append(f"{cx + s * (x * c - y * si):.2f},{cy + s * (x * si + y * c):.2f}"); nums = []
    return " ".join(out)


def bone(cx, cy, s, ang, n):
    d = placed(BONE_SHAPE, cx, cy, s, ang); a = math.radians(ang)
    for i in range(n):  # a ring cut into the bone, its centre left standing
        x = (i - (n - 1) / 2) * 7.2
        px, py = cx + s * x * math.cos(a), cy + s * x * math.sin(a)
        d += " " + circ(px, py, s * 2.3) + " " + circ(px, py, s * 0.85)
    return d


G["dice"] = [(bone(51, 63, 0.92, -14, 3), True), (bone(57, 44, 0.82, 12, 2), True)]

# 3D printer (Bambu Handy): the smith's anvil and hammer
G["anvil"] = [("M30,52 L66,52 C70,52 76,50 80,47 C78,53 72,57 66,57.5 L61,58 C59,60 58.5,63 59.5,66 L64,66 L64,71 "
               "L42,71 L42,66 L46.5,66 C47.5,63 47,60 45,58 L38,57.5 C34,57.2 31,55 30,52 Z", False),
              ("M44,36 L58,36 L58,44 L44,44 Z M57,38.6 L74,35.8 L75,39.4 L57.6,41.6 Z", False)]

# Gallery: a Gotland picture stone with a longship under sail and waves carved through it
G["gallery"] = [("M40,76 L40,46 C40,34 46,27 54,27 C62,27 68,34 68,46 L68,76 Z "
                 "M43,61.6 L46.2,61.6 C48.6,63.4 59.4,63.4 61.8,61.6 L65,61.6 C63.4,66.6 44.6,66.6 43,61.6 Z "
                 "M43,61.6 L42,57.4 L43.6,57.2 L45,61.6 Z M65,61.6 L66,57.4 L64.4,57.2 L63,61.6 Z "
                 "M53.3,44 L54.7,44 L54.7,61.2 L53.3,61.2 Z M46.8,46 L61.2,46 L60.2,58.6 L47.8,58.6 Z "
                 "M42.6,69.6 C45,68.2 47,68.2 49,69.6 C51,71 53,71 55,69.6 C57,68.2 59,68.2 61,69.6 C62.6,70.6 64,70.8 65.4,70.2 "
                 "L65.4,71.8 C63.6,72.6 62,72.4 60.4,71.2 C58.6,69.9 57.4,69.9 55.6,71.2 C53.6,72.6 50.4,72.6 48.4,71.2 "
                 "C46.6,69.9 45.4,69.9 43.6,71.2 L42.6,71.8 Z " + circ(54, 37.4, 3), True)]

# Brave: a spangenhelm with eye guards and nasal (no horns)
G["helm"] = [("M36,58 C36,42 44,32 54,32 C64,32 72,42 72,58 L72,66 C72,69 70,71 67,71 L62,71 C60,71 59,70 59,68 "
              "L59,62 C59,60.4 57.6,59.4 56,60 L55.6,72 L52.4,72 L52,60 C50.4,59.4 49,60.4 49,62 L49,68 C49,70 48,71 46,71 "
              "L41,71 C38,71 36,69 36,66 Z "
              "M40.6,57 C42,54 46,53.4 50,54.6 L50.6,57.6 C47,57 43.4,57.4 40.6,59.4 Z "
              "M67.4,57 C66,54 62,53.4 58,54.6 L57.4,57.6 C61,57 64.6,57.4 67.4,59.4 Z "
              "M36.4,50.6 C46,47.6 62,47.6 71.6,50.6 L71.8,53 C62,50.2 46,50.2 36.2,53 Z "
              "M52.8,32.4 L55.2,32.4 L55.2,48.6 L52.8,48.6 Z", True)]


def arc2(cx, cy, r, w, a0, a1):
    p = lambda r, a: (cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
    x0, y0 = p(r, a0); x1, y1 = p(r, a1); x2, y2 = p(r + w, a1); x3, y3 = p(r + w, a0)
    return f"M{x0:.2f},{y0:.2f} A{r},{r} 0 0 1 {x1:.2f},{y1:.2f} L{x2:.2f},{y2:.2f} A{r+w},{r+w} 0 0 0 {x3:.2f},{y3:.2f} Z"


# Assist (Home Assistant's voice): ansuz, the rune of Odin's speech, sounding
G["assist"] = [("M47,30 L51.4,30 L51.4,78 L47,78 Z M51.4,33.4 L64,41.4 L61.8,45 L51.4,38.4 Z "
                "M51.4,45.4 L64,53.4 L61.8,57 L51.4,50.4 Z", False),
               (arc2(62, 60, 6, 2.4, -50, 50), False), (arc2(62, 60, 11, 2.4, -50, 50), False)]


def board(x0, y0, x1, y1, w):
    dx, dy = x1 - x0, y1 - y0; L = math.hypot(dx, dy); nx, ny = -dy / L * w / 2, dx / L * w / 2
    return f"M{x0+nx:.2f},{y0+ny:.2f} L{x1+nx:.2f},{y1+ny:.2f} L{x1-nx:.2f},{y1-ny:.2f} L{x0-nx:.2f},{y0-ny:.2f} Z"


# Home Assistant: a Norse longhouse gable, steep roof with crossed bargeboards, door
G["longhouse"] = [("M29,60.6 L54,38.4 L79,60.6 L74,60.6 L54,43.2 L34,60.6 Z", False),
                  ("M37,60.6 L54,45.4 L71,60.6 L71,75 L37,75 Z "
                   "M50.2,75 L50.2,66.6 C50.2,63.6 57.8,63.6 57.8,66.6 L57.8,75 Z", True),
                  (board(46, 44, 62.6, 28.6, 2.6) + " " + board(62, 44, 45.4, 28.6, 2.6), False)]

# Sense (home energy): Mjolnir with a lightning bolt on its head and a bound grip
G["mjolnir"] = [("M38,36 L70,36 C71.6,36 72.6,37 72.6,38.6 L72.6,46.4 C72.6,48 71.6,49 70,49 L58,49 L58,70 "
                 "C58,71.4 59.6,72 59.6,73.6 C59.6,75.4 58,77 54,77 C50,77 48.4,75.4 48.4,73.6 C48.4,72 50,71.4 50,70 "
                 "L50,49 L38,49 C36.4,49 35.4,48 35.4,46.4 L35.4,38.6 C35.4,37 36.4,36 38,36 Z "
                 "M56.4,38.4 L49.6,43.2 L53.4,43.6 L51.2,47.4 L58.6,41.8 L54.6,41.4 Z "
                 "M50,56 L58,54.6 L58,56.2 L50,57.6 Z M50,61 L58,59.6 L58,61.2 L50,62.6 Z M50,66 L58,64.6 L58,66.2 L50,67.6 Z", True)]

FACES = {n: "#C8A25A" for n in G}  # preview only: every shield is gold; Glyphs.kt holds each accent ring
DESC = {"calendar": "a runestone carved with day-marks and a sun ring", "voicemail": "Gjallarhorn sounding",
        "calculator": "crossed tally sticks", "mail": "a folded letter under a wax seal", "dice": "two bone dice, rolling the bones",
        "anvil": "the smith's anvil and hammer", "gallery": "a Gotland picture stone carved with a longship", "helm": "a spangenhelm, for Brave", "assist": "ansuz, the rune of speech, sounding", "longhouse": "a longhouse gable with crossed bargeboards", "mjolnir": "Mjolnir with a lightning bolt"}


def svg_shield(name, face):
    b = 108 * 1.22; off = (108 - b) / 2
    paths = "".join(f'<path d="{d}" fill="#141A26"{" fill-rule=" + chr(34) + "evenodd" + chr(34) if eo else ""}/>' for d, eo in G[name])
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
            f'<circle cx="54" cy="54" r="49.14" fill="{face}" stroke="#0B1220" stroke-width="4.86"/>'
            f'<circle cx="54" cy="54" r="40.5" fill="none" stroke="#000" stroke-opacity="0.18" stroke-width="2.4"/>'
            f'<g transform="translate({off},{off}) scale(1.22)">{paths}</g></svg>\n')


def vector(name):
    body = "".join(f'    <path android:fillColor="#FFFFFFFF"{" android:fillType=" + chr(34) + "evenOdd" + chr(34) if eo else ""} '
                   f'android:pathData="{d}"/>\n' for d, eo in G[name])
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Yggdrasil glyph: {DESC[name]} (tools/glyphs.py) -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n' + body + '</vector>\n')


if __name__ == "__main__":
    dr = os.path.join("app", "src", "main", "res", "drawable")
    os.makedirs("/tmp/ygg-glyphs", exist_ok=True)
    for n in G:
        open(os.path.join(dr, f"glyph_{n}.xml"), "w").write(vector(n))
        open(f"/tmp/ygg-glyphs/{n}.svg", "w").write(svg_shield(n, FACES[n]))
    print(len(G), "glyphs written")
