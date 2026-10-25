"""Generate the transceiver's block textures, JSON models and GUI texture.

All art is drawn here pixel by pixel in the AE2 palette, so the PNGs and
model JSON under src/main/resources are build outputs of this script.
Run from anywhere:  python tools/art/generate_assets.py
Requires Pillow.
"""
import json
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "ae2wtx")
BLOCK_TEX = os.path.join(ASSETS, "textures", "blocks", "transceiver")
GUI_TEX = os.path.join(ASSETS, "textures", "gui")
MODELS = os.path.join(ASSETS, "models", "blocks")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")
TEX_REF = "ae2wtx:blocks/transceiver/"


def rgb(h, a=255):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), a)


CLEAR = (0, 0, 0, 0)
# AE2 casing: dark brown frame and four greys
FRAME_D = rgb("#2b261f")
FRAME = rgb("#373127")
G0 = rgb("#404040")
G1 = rgb("#646464")
G2 = rgb("#808080")
G3 = rgb("#a8a8a8")
G4 = rgb("#d8d8d8")
# fluix
F_DEEP = rgb("#4d27a3")
F_D = rgb("#6a1fc0")
F = rgb("#9500fb")
F_L = rgb("#be82fb")
F_W = rgb("#f0e4ff")
# unlit fluix
DIM0 = rgb("#1c1624")
DIM1 = rgb("#2e2438")
DIM2 = rgb("#43355a")


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


# ---------------------------------------------------------------- textures
#
# Model faces use 1:1 UVs (one texel per model unit), so the side texture is
# literally the block's side elevation and the top texture its plan view.
# Rows in the side texture: row = 15 - y.  Plinth y0-2, body y2-9, deck y9-10.

def side_texture():
    c = Canvas()
    # deck (row 6)
    c.hline(0, 15, 6, G3)
    c.set(0, 6, FRAME)
    c.set(15, 6, FRAME)
    c.hline(6, 9, 6, G4)
    # body (rows 7-13, cols 1-14)
    c.rect(1, 7, 14, 13, G2)
    c.hline(1, 14, 7, G1)  # shadow under the deck
    c.hline(2, 13, 8, G3)  # panel bevel
    c.vline(1, 7, 13, G1)
    c.vline(14, 7, 13, G1)
    c.hline(1, 14, 13, G1)
    # window with the signal slot
    c.frame(4, 9, 11, 12, FRAME)
    c.rect(5, 10, 10, 11, DIM1)
    c.hline(5, 10, 10, DIM2)
    for x, y in ((2, 10), (13, 10), (2, 12), (13, 12)):
        c.set(x, y, G0)
    # plinth (rows 14-15)
    c.hline(0, 15, 14, G3)
    c.set(0, 14, FRAME)
    c.set(15, 14, FRAME)
    c.hline(6, 9, 14, G0)
    c.hline(7, 8, 14, FRAME_D)
    c.hline(0, 15, 15, FRAME)
    return c


def top_texture():
    c = Canvas(fill=G2)
    c.frame(0, 0, 15, 15, FRAME)
    c.hline(1, 14, 1, G3)
    c.vline(1, 1, 14, G3)
    c.hline(1, 14, 14, G1)
    c.vline(14, 1, 14, G1)
    # emitter grooves around the socket
    c.frame(3, 3, 12, 12, G1)
    for x, y in ((7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)):
        c.set(x, y, F_D)
    # mast mounts (the 1x1 mast stands on the inner corner pixel)
    for mx, my in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(mx, my, FRAME_D)
    return c


def bottom_texture():
    c = Canvas(fill=G1)
    c.frame(0, 0, 15, 15, FRAME)
    c.frame(5, 5, 10, 10, FRAME)
    c.rect(6, 6, 9, 9, G0)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, G0)
    return c


def socket_texture():
    """Top of the socket at cols/rows 5-10; its 1-unit sides on row 15."""
    c = Canvas()
    c.rect(5, 5, 10, 10, G0)
    c.frame(5, 5, 10, 10, FRAME)
    c.frame(6, 6, 9, 9, F_DEEP)
    c.hline(0, 5, 15, G1)
    c.set(0, 15, FRAME)
    c.set(5, 15, FRAME)
    return c


# crystal: shaft sides uv [0,0,3,4], shaft top uv [0,4,3,7],
#          tip sides uv [4,0,6,2] (2x2 region, used 1.5x1), tip top uv [4,2,6,4]
def crystal_frame(light, mid, dark, glint):
    c = Canvas()
    for y in range(0, 4):
        c.set(0, y, light)
        c.set(1, y, mid)
        c.set(2, y, dark)
    c.set(0, 0, glint)
    c.set(1, 3, dark)
    c.rect(0, 4, 2, 6, mid)
    c.set(1, 5, light)
    c.rect(4, 0, 5, 1, light)
    c.set(4, 0, glint)
    c.rect(4, 2, 5, 3, glint)
    return c


def crystal_off():
    return crystal_frame(DIM2, DIM1, DIM0, DIM2)


def crystal_on():
    """Vertical animation strip: a slow fluix pulse (12 frames)."""
    frames = 12
    strip = Image.new("RGBA", (16, 16 * frames), CLEAR)
    for i in range(frames):
        # 0 -> 1 -> 0 triangle wave
        t = 1.0 - abs((i / frames) * 2.0 - 1.0)
        light = lerp(F_L, F_W, t * 0.6)
        mid = lerp(F, F_L, t * 0.5)
        dark = lerp(F_D, F, t * 0.5)
        strip.paste(crystal_frame(light, mid, dark, F_W).im, (0, 16 * i))
    return strip


def mast_texture():
    c = Canvas()
    rows = [G4, G4, G3, G2, G0, G2, G2, G1] + [G2] * 8
    for y, col in enumerate(rows):
        c.hline(0, 15, y, col)
    return c


def light_on():
    c = Canvas(fill=F_L)
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                c.set(x, y, lerp(F_L, F_W, 0.5))
    return c


def light_off():
    c = Canvas(fill=DIM1)
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                c.set(x, y, DIM2)
    return c


def fallback_icon(on):
    """Full-cube face for vanilla rendering, particles and the GTNHLib-less item."""
    c = Canvas(fill=G2)
    c.frame(0, 0, 15, 15, FRAME)
    c.hline(1, 14, 1, G3)
    c.vline(1, 1, 14, G3)
    c.hline(1, 14, 14, G1)
    c.vline(14, 1, 14, G1)
    c.frame(3, 3, 12, 12, FRAME)
    c.rect(4, 4, 11, 11, G0 if on else DIM0)
    for y in range(4, 12):
        for x in range(4, 12):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d <= 3.0:
                if on:
                    col = F_W if d <= 1.0 else (F_L if x + y < 15 else F)
                else:
                    col = DIM2 if x + y < 15 else DIM1
                c.set(x, y, col)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, G0)
    return c


# ------------------------------------------------------------------ models

FACES = ("north", "south", "west", "east", "up", "down")


def auto_uv(face, fr, to):
    x1, y1, z1 = fr
    x2, y2, z2 = to
    if face == "north":
        return [16 - x2, 16 - y2, 16 - x1, 16 - y1]
    if face == "south":
        return [x1, 16 - y2, x2, 16 - y1]
    if face == "west":
        return [z1, 16 - y2, z2, 16 - y1]
    if face == "east":
        return [16 - z2, 16 - y2, 16 - z1, 16 - y1]
    if face == "up":
        return [x1, z1, x2, z2]
    return [x1, 16 - z2, x2, 16 - z1]


def element(fr, to, tex, uv=None, faces=FACES, rotate=None, glow=False, name=None):
    """Box with 1:1 UVs unless uv (face -> [u1,v1,u2,v2] or one list for all) is given."""
    el = {}
    if name:
        el["name"] = name
    el["from"] = list(fr)
    el["to"] = list(to)
    if rotate is not None:
        axis, angle, origin = rotate
        el["rotation"] = {"angle": angle, "axis": axis, "origin": list(origin)}
    if glow:
        el["light_emission"] = 15
    out = {}
    for f in faces:
        t = tex[f] if isinstance(tex, dict) else tex
        if uv is None:
            u = auto_uv(f, fr, to)
        elif isinstance(uv, dict):
            u = uv.get(f) or auto_uv(f, fr, to)
        else:
            u = uv
        out[f] = {"uv": [round(v, 4) for v in u], "texture": "#" + t}
    el["faces"] = out
    return el


SIDES = ("north", "south", "west", "east")


def build_model(on):
    els = []
    casing = {"north": "side", "south": "side", "west": "side", "east": "side", "up": "top", "down": "bottom"}
    els.append(element((0, 0, 0), (16, 2, 16), casing, name="plinth"))
    els.append(element((1, 2, 1), (15, 9, 15), "side", faces=SIDES, name="body"))
    els.append(element((0, 9, 0), (16, 10, 16), casing, name="deck"))
    els.append(element((5, 10, 5), (11, 11, 11), "socket",
                       uv={f: [0, 15, 6, 16] for f in SIDES}, faces=SIDES + ("up",), name="socket"))
    crystal = "crystal"
    up = SIDES + ("up",)
    els.append(element((6.75, 11, 6.75), (9.25, 15, 9.25), crystal,
                       uv={**{f: [0, 0, 2.5, 4] for f in SIDES}, "up": [0, 4, 2.5, 6.5]},
                       faces=up, rotate=("y", 45, (8, 11, 8)), glow=on, name="crystal"))
    els.append(element((7.4, 15, 7.4), (8.6, 16, 8.6), crystal,
                       uv={**{f: [4, 0, 5.2, 1] for f in SIDES}, "up": [4, 2, 5.2, 3.2]},
                       faces=up, rotate=("y", 45, (8, 15, 8)), glow=on, name="crystal_tip"))
    # two small shards leaning out of the socket
    els.append(element((5.6, 10.5, 7.4), (6.8, 13, 8.6), crystal,
                       uv={**{f: [0, 0, 1.2, 2.5] for f in SIDES}, "up": [0, 4, 1.2, 5.2]},
                       faces=up, rotate=("z", 22.5, (6.2, 10.5, 8)), glow=on, name="shard"))
    els.append(element((7.4, 10.5, 9.2), (8.6, 12.5, 10.4), crystal,
                       uv={**{f: [0, 0, 1.2, 2] for f in SIDES}, "up": [0, 4, 1.2, 5.2]},
                       faces=up, rotate=("x", 22.5, (8, 10.5, 9.8)), glow=on, name="shard"))
    for mx, mz in ((1, 1), (14, 1), (1, 14), (14, 14)):
        els.append(element((mx, 10, mz), (mx + 1, 14, mz + 1), "mast", faces=SIDES, name="mast"))
        els.append(element((mx, 14, mz), (mx + 1, 15, mz + 1), "light",
                           faces=SIDES + ("up",), glow=on, name="mast_light"))
    if on:
        # lit signal slots, a hair in front of each body face (rows 10-11 = y4-6, cols 5-10)
        e = 0.1
        els.append(element((5, 4, 1 - e), (11, 6, 1), "light", faces=("north",), glow=True, name="slot"))
        els.append(element((5, 4, 15), (11, 6, 15 + e), "light", faces=("south",), glow=True, name="slot"))
        els.append(element((1 - e, 4, 5), (1, 6, 11), "light", faces=("west",), glow=True, name="slot"))
        els.append(element((15, 4, 5), (15 + e, 6, 11), "light", faces=("east",), glow=True, name="slot"))
    state = "on" if on else "off"
    return {
        "credit": "ae2wtx original art, generated by tools/art/generate_assets.py",
        "ambientocclusion": True,
        "textures": {
            "particle": TEX_REF + "fallback_" + state,
            "side": TEX_REF + "side",
            "top": TEX_REF + "top",
            "bottom": TEX_REF + "bottom",
            "socket": TEX_REF + "socket",
            "crystal": TEX_REF + "crystal_" + state,
            "mast": TEX_REF + "mast",
            "light": TEX_REF + "light_" + state,
        },
        "elements": els,
    }


# --------------------------------------------------------------------- GUI
#
# 256x178 panel in vanilla/AE2 style.  The coordinates here must match the
# constants in LabeledTransceiverGui.

GUI_W, GUI_H = 256, 178
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
LIST = (8, 37, 120, 132)        # 12 rows of 11
SCROLL = (131, 37, 6, 132)
INFO = (143, 21, 105, 72)
SCREEN_BOX = (143, 135, 105, 34)
BTN_SIZE = (52, 16)
BUTTONS = ((143, 97), (196, 97), (143, 115), (196, 115))
BTN_SPRITES_Y = 184             # normal, hover, disabled, armed; 16 px apart


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


def button_sprite(c, x, y, fill, hi, sh):
    w, h = BTN_SIZE
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
    c.hline(ix + 3, ix + iw - 4, iy + 24, G3)
    inset(c, SCREEN_BOX, SCREEN)
    sprites = (
        (rgb("#8b8b8b"), rgb("#b4b4b4"), rgb("#5a5a5a")),   # normal
        (rgb("#8a6cc8"), rgb("#b9a0ec"), rgb("#55389a")),   # hover (fluix)
        (rgb("#6e6e6e"), rgb("#7e7e7e"), rgb("#5e5e5e")),   # disabled
        (rgb("#a84040"), rgb("#d77272"), rgb("#6a2222")),   # armed delete
    )
    for i, (fill, hi, sh) in enumerate(sprites):
        button_sprite(c, 0, BTN_SPRITES_Y + i * 16, fill, hi, sh)
    return c


# -------------------------------------------------------------------- main

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    t = BLOCK_TEX
    side_texture().save(os.path.join(t, "side.png"))
    top_texture().save(os.path.join(t, "top.png"))
    bottom_texture().save(os.path.join(t, "bottom.png"))
    socket_texture().save(os.path.join(t, "socket.png"))
    crystal_off().save(os.path.join(t, "crystal_off.png"))
    crystal_on().save(os.path.join(t, "crystal_on.png"))
    write_json(os.path.join(t, "crystal_on.png.mcmeta"), {"animation": {"frametime": 3}})
    mast_texture().save(os.path.join(t, "mast.png"))
    light_on().save(os.path.join(t, "light_on.png"))
    light_off().save(os.path.join(t, "light_off.png"))
    fallback_icon(False).save(os.path.join(t, "fallback_off.png"))
    fallback_icon(True).save(os.path.join(t, "fallback_on.png"))
    write_json(os.path.join(MODELS, "transceiver_off.json"), build_model(False))
    write_json(os.path.join(MODELS, "transceiver_on.json"), build_model(True))
    write_json(os.path.join(BLOCKSTATES, "labeled_wireless_transceiver.json"), {
        "variants": {
            "meta=0": {"model": "ae2wtx:blocks/transceiver_off"},
            "meta=1": {"model": "ae2wtx:blocks/transceiver_on"},
        }
    })
    gui_texture().save(os.path.join(GUI_TEX, "transceiver.png"))
    print("assets written under", ASSETS)


if __name__ == "__main__":
    main()
