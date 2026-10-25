"""Generate the transceiver's block textures and GUI texture.

All art is drawn here pixel by pixel, so the PNGs under src/main/resources are
build outputs of this script.  Run from anywhere:  python tools/art/generate_assets.py
Requires Pillow and numpy.
"""
import json
import os

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "ae2wtx")
BLOCK_TEX = os.path.join(ASSETS, "textures", "blocks", "transceiver")
GUI_TEX = os.path.join(ASSETS, "textures", "gui")


def rgb(h, a=255):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), a)


CLEAR = (0, 0, 0, 0)


class Canvas:

    def __init__(self, w=16, h=16, fill=CLEAR):
        self.im = Image.new("RGBA", (w, h), fill)
        self.px = self.im.load()

    def set(self, x, y, c):
        self.px[x, y] = c

    def rect(self, x0, y0, x1, y1, c):
        """Filled rectangle, corners inclusive."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px[x, y] = c

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def frame(self, x0, y0, x1, y1, c):
        self.hline(x0, x1, y0, c)
        self.hline(x0, x1, y1, c)
        self.vline(x0, y0, y1, c)
        self.vline(x1, y0, y1, c)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.im.save(path)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


# ---------------------------------------------------------------- block textures
#
# Design 7: a 2-wide frame on every edge, a shallow 45-degree bevel (1 wide, 1 deep)
# from the frame down to a glass pane spanning 3..13, and a 6x6 wiring panel
# (5..11) in the middle of each face reaching from the surface to the glass.
# The block is drawn by TransceiverBlockRenderer, the black hole inside by
# TransceiverEffectsRenderer; both take these numbers from TransceiverShape.
# Texture coordinates below are in pixels; the renderer maps model units
# 0..16 onto the whole icon.
#
# Two themes, picked per block.  Dark is the default (config: client.defaultTheme).

THEMES = {
    "dark": dict(frame=rgb("#1e2129"), frame_hi=rgb("#4a5164"), frame_lo=rgb("#15171d"),
                 glow=rgb("#b98aff"), glow_hi=rgb("#f1e6ff"), dim=rgb("#3b3550"), core=rgb("#121419"),
                 glass=dict(tint=(118, 132, 168), a=0.12, rim=(170, 182, 214)),
                 arcs=((242, 228, 255), (190, 150, 255))),
    # L1: pearl white with AE cyan; the arcs use deeper tones or they vanish into the pale pane
    "light": dict(frame=rgb("#d4d9e1"), frame_hi=rgb("#f4f6f9"), frame_lo=rgb("#9aa2b0"),
                  glow=rgb("#1fc6ee"), glow_hi=rgb("#c9f6ff"), dim=rgb("#8e98a8"), core=rgb("#2b313d"),
                  glass=dict(tint=(232, 240, 250), a=0.12, rim=(255, 255, 255)),
                  arcs=((120, 235, 255), (0, 170, 235))),
}
G_LO, G_HI = 3, 13    # glass pane span (model units)
PANEL_LO, PANEL_HI = 5, 11
GLASS_N = 64          # glass and arc textures: 4 px per model unit


def beam_texture(p):
    """Frame beams, mapped 1:1 onto the block faces: the ring's outer rows land on the block edges."""
    c = Canvas(32, 32, p["frame"])
    c.frame(0, 0, 31, 31, p["frame_lo"])
    c.frame(1, 1, 30, 30, p["frame_hi"])
    return c


def bevel_texture(p, on):
    """Across the strip (rows, outer edge first): lit fold at the frame, shaded slope, groove and a light seam
    at the glass.  Columns run along the edge."""
    c = Canvas(32, 32, p["frame"])
    for y in range(32):
        c.hline(0, 31, y, lerp(p["frame_hi"], p["frame_lo"], (y / 31.0) ** 0.8))
    c.hline(0, 31, 0, p["frame_hi"])
    c.hline(0, 31, 29, p["frame_lo"])
    c.hline(0, 31, 30, p["frame_lo"])
    c.hline(0, 31, 31, p["glow"] if on else p["dim"])
    return c


def port_texture(p, on):
    """Wiring panel: face at uv [0,0,6,6], back (seen through the glass) at [6,0,12,6], sides on rows 30-31."""
    c = Canvas(32, 32)
    c.rect(0, 0, 11, 11, p["frame"])
    c.frame(0, 0, 11, 11, p["frame_lo"])
    c.frame(1, 1, 10, 10, p["frame_hi"])
    c.rect(3, 3, 8, 8, p["glow"] if on else p["dim"])
    c.rect(4, 4, 7, 7, p["core"])
    c.rect(5, 5, 6, 6, p["glow_hi"] if on else p["dim"])
    for x0 in (3, 8):
        for y0 in (3, 8):
            c.set(x0, y0, p["frame"])
    c.rect(12, 0, 23, 11, p["frame"])
    c.frame(12, 0, 23, 11, p["frame_lo"])
    c.rect(0, 30, 31, 31, p["frame_hi"])
    return c


# See-through panels (client.panelOpacity) need the alpha in the texture: Angelica's
# terrain renderer reads vertex alpha as a shade factor, so a faded vertex colour
# only darkens the panel. One copy per 10 % step, picked by TransceiverIcons.
PANEL_FADE_STEPS = range(10, 100, 10)


def faded(im, pct):
    out = im.copy()
    out.putalpha(im.getchannel("A").point(lambda a: int(round(a * pct / 100.0))))
    return out


def glass_texture(p):
    """Cool tinted pane with a frosted rim; only px 12..51 (units 3..13) are used."""
    g = p["glass"]
    tx = np.zeros((GLASS_N, GLASS_N, 4), np.float32)
    yy, xx = np.mgrid[0:GLASS_N, 0:GLASS_N].astype(np.float32)
    tx[..., :3] = g["tint"]
    tx[..., 3] = 255 * g["a"]
    lo, hi = G_LO * 4, G_HI * 4 - 1
    d = np.minimum(np.minimum(xx - lo, hi - xx), np.minimum(yy - lo, hi - yy))
    rim = np.clip(1 - d / 2.5, 0, 1) ** 1.5
    tx[..., :3] = tx[..., :3] * (1 - rim[..., None] * 0.6) + np.array(g["rim"], np.float32) * rim[..., None] * 0.6
    tx[..., 3] = np.maximum(tx[..., 3], 255 * (g["a"] + 0.2 * rim))
    return to_image(tx)


# Modern AE2 quantum ring light (quantum_ring_light.png: 32 frames, frametime 2): short electric arcs crackle
# across the pane -- they appear as 1-2 cells, stretch into an L, fade to the secondary tone and vanish.
# One cell = 2 px.  The overlay is drawn full-bright on top of the glass while the transceiver is online.
ARC_FRAMES = 32
ARC_CELL = 2


def _arc_cell_visible(cx, cy):
    lo, hi = G_LO * 4 // ARC_CELL, G_HI * 4 // ARC_CELL - 1
    blo, bhi = PANEL_LO * 4 // ARC_CELL, PANEL_HI * 4 // ARC_CELL - 1     # hidden behind the wiring panel
    return lo <= cx <= hi and lo <= cy <= hi and not (blo <= cx <= bhi and blo <= cy <= bhi)


def _make_arcs(seed=11, per_frame=2):
    rng = np.random.default_rng(seed)
    lo, hi = G_LO * 4 // ARC_CELL, G_HI * 4 // ARC_CELL - 1
    arcs = []
    for f0 in range(ARC_FRAMES):
        for _ in range(per_frame):
            while True:
                cx, cy = rng.integers(lo, hi + 1, 2)
                if _arc_cell_visible(cx, cy):
                    break
            d = [(1, 0), (-1, 0), (0, 1), (0, -1)][rng.integers(4)]
            cells = [(int(cx), int(cy))]
            for _ in range(int(rng.integers(2, 6))):
                if rng.random() < 0.35:
                    d = (d[1], d[0]) if rng.random() < 0.5 else (-d[1], -d[0])
                nx, ny = cells[-1][0] + d[0], cells[-1][1] + d[1]
                if not _arc_cell_visible(nx, ny):
                    break
                cells.append((nx, ny))
            arcs.append((f0, cells))
    return arcs


def _arc_cells(arcs, frame):
    """(cell, tone) lit in this frame; tone 0 = core, 1 = secondary."""
    out = []
    for f0, cells in arcs:
        age = (frame - f0) % ARC_FRAMES
        if age == 0:
            out += [(c, 0) for c in cells[:2]]
        elif age == 1:
            out += [(c, 0) for c in cells]
        elif age == 2:
            out += [(c, 0 if i == len(cells) - 1 else 1) for i, c in enumerate(cells[1:], 1)]
        elif age == 3:
            out += [(c, 1) for c in cells[-2:]]
    return out


def arc_texture(p):
    """Vertical strip of ARC_FRAMES 64x64 frames; a faint 1 px halo stands in for the emissive bloom."""
    arcs = _make_arcs()
    core, second = (np.array(c, np.float32) for c in p["arcs"])
    strip = np.zeros((GLASS_N * ARC_FRAMES, GLASS_N, 4), np.float32)
    for f in range(ARC_FRAMES):
        fr = strip[f * GLASS_N:(f + 1) * GLASS_N]
        lit = np.zeros((GLASS_N, GLASS_N), bool)
        for (cx, cy), tone in _arc_cells(arcs, f):
            ys, xs = slice(cy * ARC_CELL, (cy + 1) * ARC_CELL), slice(cx * ARC_CELL, (cx + 1) * ARC_CELL)
            fr[ys, xs, :3] = core if tone == 0 else second
            fr[ys, xs, 3] = 255
            lit[ys, xs] = True
        halo = np.zeros_like(lit)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            halo |= np.roll(np.roll(lit, dy, 0), dx, 1)
        halo &= ~lit
        fr[halo, :3] = second
        fr[halo, 3] = 70
    return to_image(strip)


def fallback_icon(p, on):
    """Flat face for the simple-cube fallback (client.simpleBlockModel), particles and breaking."""
    c = Canvas(32, 32, p["core"])
    glass = p["glass"]["tint"] + (255,)
    pane = lerp(p["core"], glass, 0.18)
    for y in range(6, 26):
        for x in range(6, 26):
            r = ((x - 15.5) ** 2 + (y - 15.5) ** 2) ** 0.5
            c.set(x, y, lerp(pane, p["glow"], (1 - r / 7) ** 1.5) if on and r < 7 else pane)
    c.frame(6, 6, 25, 25, lerp(p["core"], glass, 0.45))
    c.frame(4, 4, 27, 27, p["frame_hi"])
    c.frame(5, 5, 26, 26, p["frame_lo"])
    c.frame(0, 0, 31, 31, p["frame_lo"])
    c.frame(1, 1, 30, 30, p["frame_hi"])
    c.frame(2, 2, 29, 29, p["frame"])
    c.frame(3, 3, 28, 28, p["frame"])
    c.rect(10, 10, 21, 21, p["frame"])
    c.frame(10, 10, 21, 21, p["frame_lo"])
    c.frame(11, 11, 20, 20, p["frame_hi"])
    c.rect(13, 13, 18, 18, p["glow"] if on else p["dim"])
    c.rect(14, 14, 17, 17, p["core"])
    c.rect(15, 15, 16, 16, p["glow_hi"] if on else p["dim"])
    return c


def to_image(a):
    return Image.fromarray(np.clip(np.round(a), 0, 255).astype(np.uint8), "RGBA")


def write_block_textures():
    for name, p in THEMES.items():
        d = os.path.join(BLOCK_TEX, name)
        os.makedirs(d, exist_ok=True)
        beam_texture(p).save(os.path.join(d, "beam.png"))
        for on, suffix in ((False, "off"), (True, "on")):
            bevel_texture(p, on).save(os.path.join(d, "bevel_%s.png" % suffix))
            port = port_texture(p, on)
            port.save(os.path.join(d, "port_%s.png" % suffix))
            for pct in PANEL_FADE_STEPS:
                faded(port.im, pct).save(os.path.join(d, "port_%s_a%d.png" % (suffix, pct)))
            fallback_icon(p, on).save(os.path.join(d, "fallback_%s.png" % suffix))
        glass_texture(p).save(os.path.join(d, "glass.png"))
        arc_texture(p).save(os.path.join(d, "arcs.png"))
        write_json(os.path.join(d, "arcs.png.mcmeta"), {"animation": {"frametime": 2}})



# --------------------------------------------------------------------- GUI
#
# 256x198 panel in vanilla/AE2 style.  The coordinates here must match the
# constants in LabeledTransceiverGui.

GUI_W, GUI_H = 256, 198
P_BG = rgb("#c6c6c6")
P_HI = rgb("#ffffff")
P_SH = rgb("#555555")
P_OUT = rgb("#000000")
IN_DARK = rgb("#373737")
IN_CORNER = rgb("#8b8b8b")
LIST_A = rgb("#d4d4d4")
LIST_B = rgb("#cbcbcb")
TRACK = rgb("#8b8b8b")
INFO_BG = rgb("#d4d4d4")
SCREEN = rgb("#1b1b20")
FIELD = rgb("#202020")

SEARCH = (8, 21, 129, 12)       # x, y, w, h (inner)
LIST = (8, 37, 120, 154)        # 14 rows of 11
SCROLL = (131, 37, 6, 154)
INFO = (143, 21, 105, 72)
SCREEN_BOX = (143, 154, 105, 37)
BTN_SIZE = (52, 16)
BUTTONS = ((143, 97), (196, 97), (143, 115), (196, 115), (143, 133), (196, 133))
BTN_SPRITES = ((0, 200), (0, 216), (0, 232), (56, 200))   # normal, hover, disabled, armed
# 12x12 title-bar buttons (theme, rename): backgrounds, then 8x8 glyphs drawn 2 px in
ICON_BTN = 12
ICON_BTN_SPRITES = ((112, 200), (124, 200))                  # normal, hover
GLYPH_Y = 212
GLYPHS = {   # x in the texture -> bitmap
    112: ("...###..", "..##....", ".##.....", ".##.....", ".##.....", ".##.....", "..##....", "...###.."),  # dark
    120: ("...#....", ".#.#.#..", "..###...", "#######.", "..###...", ".#.#.#..", "...#....", "........"),  # light
    128: ("..###...", ".#..##..", "#...###.", "#...###.", "#...###.", ".#..##..", "..###...", "........"),  # default
    136: ("......##", ".....###", "....###.", "...###..", "..###...", ".###....", "##......", "#......."),  # rename
}


def panel(c, x0, y0, w, h):
    x1, y1 = x0 + w - 1, y0 + h - 1
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, P_BG)
    c.hline(x0 + 2, x1 - 2, y0, P_OUT)
    c.hline(x0 + 2, x1 - 2, y1, P_OUT)
    c.vline(x0, y0 + 2, y1 - 2, P_OUT)
    c.vline(x1, y0 + 2, y1 - 2, P_OUT)
    c.set(x0 + 1, y0 + 1, P_OUT)
    c.set(x1 - 1, y1 - 1, P_OUT)
    c.set(x1 - 1, y0 + 1, P_OUT)
    c.set(x0 + 1, y1 - 1, P_OUT)
    c.hline(x0 + 2, x1 - 3, y0 + 1, P_HI)
    c.hline(x0 + 1, x1 - 3, y0 + 2, P_HI)
    c.vline(x0 + 1, y0 + 2, y1 - 3, P_HI)
    c.vline(x0 + 2, y0 + 1, y1 - 3, P_HI)
    c.hline(x0 + 3, x1 - 1, y1 - 1, P_SH)
    c.hline(x0 + 3, x1 - 2, y1 - 2, P_SH)
    c.vline(x1 - 1, y0 + 3, y1 - 2, P_SH)
    c.vline(x1 - 2, y0 + 3, y1 - 2, P_SH)
    c.set(x1 - 1, y1 - 1, P_OUT)
    c.set(x1 - 2, y0 + 2, P_BG)
    c.set(x0 + 2, y1 - 2, P_BG)


def inset(c, box, fill):
    x, y, w, h = box
    c.rect(x, y, x + w - 1, y + h - 1, fill)
    c.hline(x - 1, x + w - 1, y - 1, IN_DARK)
    c.vline(x - 1, y - 1, y + h - 1, IN_DARK)
    c.hline(x, x + w, y + h, P_HI)
    c.vline(x + w, y, y + h, P_HI)
    c.set(x - 1, y + h, IN_CORNER)
    c.set(x + w, y - 1, IN_CORNER)


def button_sprite(c, x, y, fill, hi, sh, size=BTN_SIZE):
    w, h = size
    x1, y1 = x + w - 1, y + h - 1
    c.rect(x, y, x1, y1, fill)
    c.hline(x + 1, x1 - 1, y, P_OUT)
    c.hline(x + 1, x1 - 1, y1, P_OUT)
    c.vline(x, y + 1, y1 - 1, P_OUT)
    c.vline(x1, y + 1, y1 - 1, P_OUT)
    c.set(x, y, CLEAR)
    c.set(x1, y, CLEAR)
    c.set(x, y1, CLEAR)
    c.set(x1, y1, CLEAR)
    c.hline(x + 1, x1 - 1, y + 1, hi)
    c.vline(x + 1, y + 1, y1 - 2, hi)
    c.hline(x + 1, x1 - 1, y1 - 1, sh)
    c.vline(x1 - 1, y + 2, y1 - 1, sh)


def gui_texture():
    c = Canvas(256, 256)
    panel(c, 0, 0, GUI_W, GUI_H)
    inset(c, SEARCH, FIELD)
    lx, ly, lw, lh = LIST
    inset(c, LIST, LIST_A)
    for row in range(lh // 11):
        if row % 2:
            c.rect(lx, ly + row * 11, lx + lw - 1, ly + row * 11 + 10, LIST_B)
    inset(c, SCROLL, TRACK)
    inset(c, INFO, INFO_BG)
    ix, iy, iw, _ = INFO
    c.hline(ix + 3, ix + iw - 4, iy + 24, rgb("#a8a8a8"))
    inset(c, SCREEN_BOX, SCREEN)
    sprites = (
        (rgb("#8b8b8b"), rgb("#b4b4b4"), rgb("#5a5a5a")),   # normal
        (rgb("#8a6cc8"), rgb("#b9a0ec"), rgb("#55389a")),   # hover (fluix)
        (rgb("#6e6e6e"), rgb("#7e7e7e"), rgb("#5e5e5e")),   # disabled
        (rgb("#a84040"), rgb("#d77272"), rgb("#6a2222")),   # armed delete
    )
    for (x, y), (fill, hi, sh) in zip(BTN_SPRITES, sprites):
        button_sprite(c, x, y, fill, hi, sh)
    for (x, y), (fill, hi, sh) in zip(ICON_BTN_SPRITES, sprites):
        button_sprite(c, x, y, fill, hi, sh, (ICON_BTN, ICON_BTN))
    for gx, rows in GLYPHS.items():
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    c.set(gx + x, GLYPH_Y + y, P_HI)
    return c


# -------------------------------------------------------------------- main

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    write_block_textures()
    gui_texture().save(os.path.join(GUI_TEX, "transceiver.png"))
    print("assets written under", ASSETS)


if __name__ == "__main__":
    main()
