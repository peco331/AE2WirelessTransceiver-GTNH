"""Render a resource pack's transceiver JSON models offline, without Minecraft.

Usage:  python tools/art/preview.py <pack.zip or pack folder> [out.png]
Draws models/classic/transceiver_off.json and _on.json (the optional classic look, see
tools/packs/build_packs.py) from two angles with Minecraft's face shading, plus a small 1:1-ish view.
The built-in look is drawn in code by TransceiverBlockRenderer and is not previewed here.
Requires Pillow and numpy.
"""
import json
import math
import os
import sys
import tempfile
import zipfile

import numpy as np
from PIL import Image, ImageDraw


SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}


def face_vertices(face, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {
        "north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }[face]


def load_texture(ref, base):
    ns, path = ref.split(":")
    im = Image.open(os.path.join(base, ns, "textures", path + ".png"))
    im = im.convert("RGBA")
    w = im.size[0]
    return np.asarray(im.crop((0, 0, w, w)), dtype=np.float32)


def quads(model, base):
    tex = {k: load_texture(v, base) for k, v in model["textures"].items()}
    out = []
    for el in model["elements"]:
        rot = el.get("rotation")
        for face, spec in el["faces"].items():
            verts = np.array(face_vertices(face, el["from"], el["to"]), dtype=np.float64)
            if rot:
                ang = math.radians(rot["angle"])
                o = np.array(rot["origin"], dtype=np.float64)
                c, s = math.cos(ang), math.sin(ang)
                d = verts - o
                if rot["axis"] == "y":
                    verts = o + np.stack([d[:, 0] * c + d[:, 2] * s, d[:, 1], -d[:, 0] * s + d[:, 2] * c], 1)
                elif rot["axis"] == "x":
                    verts = o + np.stack([d[:, 0], d[:, 1] * c - d[:, 2] * s, d[:, 1] * s + d[:, 2] * c], 1)
                else:
                    verts = o + np.stack([d[:, 0] * c - d[:, 1] * s, d[:, 0] * s + d[:, 1] * c, d[:, 2]], 1)
            u1, v1, u2, v2 = spec["uv"]
            uv = np.array([(u1, v1), (u2, v1), (u2, v2), (u1, v2)], dtype=np.float64)
            out.append((verts, uv, tex[spec["texture"][1:]], SHADE[face]))
    return out


def render(model, yaw, pitch, scale, size, base):
    img = np.zeros((size, size, 4), dtype=np.float32)
    zbuf = np.full((size, size), -1e9)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p[:, 0] - 8, p[:, 1] - 8, p[:, 2] - 8
        rx = x * cy - z * sy
        rz = x * sy + z * cy
        ry = y * cp - rz * sp
        depth = y * sp + rz * cp
        return np.stack([size / 2 + rx * scale, size / 2 - ry * scale, depth], 1)

    for verts, uv, tex, shade in quads(model, base):
        p = project(verts)
        th, tw = tex.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = p[list(tri)]
            ua, ub, uc = uv[list(tri)]
            x0 = max(int(math.floor(min(a[0], b[0], c[0]))), 0)
            x1 = min(int(math.ceil(max(a[0], b[0], c[0]))), size - 1)
            y0 = max(int(math.floor(min(a[1], b[1], c[1]))), 0)
            y1 = min(int(math.ceil(max(a[1], b[1], c[1]))), size - 1)
            if x0 > x1 or y0 > y1:
                continue
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / den
            w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / den
            w2 = 1 - w0 - w1
            m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not m.any():
                continue
            depth = w0 * a[2] + w1 * b[2] + w2 * c[2]
            u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
            v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
            tx = np.clip((u / 16.0 * tw).astype(int), 0, tw - 1)
            ty = np.clip((v / 16.0 * th).astype(int), 0, th - 1)
            col = tex[ty, tx]
            region = zbuf[y0:y1 + 1, x0:x1 + 1]
            m &= (col[..., 3] >= 128) & (depth > region)
            region[m] = depth[m]
            out = img[y0:y1 + 1, x0:x1 + 1]
            shaded = col.copy()
            shaded[..., :3] *= shade
            out[m] = shaded[m]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    pack = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else "preview.png"
    with tempfile.TemporaryDirectory() as tmp:
        if zipfile.is_zipfile(pack):
            zipfile.ZipFile(pack).extractall(tmp)
            pack = tmp
        draw(os.path.join(pack, "assets"), out_path)


def draw(base, out_path):
    models = []
    for state in ("off", "on"):
        path = os.path.join(base, "ae2wtx", "models", "classic", "transceiver_%s.json" % state)
        with open(path, encoding="utf-8") as f:
            models.append((state, json.load(f)))
    big, small = 360, 64
    sheet = Image.new("RGBA", (big * 2 + small + 40, big * 2 + 40), (58, 62, 70, 255))
    d = ImageDraw.Draw(sheet)
    for row, (state, model) in enumerate(models):
        y = 20 + row * big
        for col, (yaw, pitch) in enumerate(((-35, 28), (120, 12))):
            im = render(model, yaw, pitch, 13.5, big, base)
            sheet.alpha_composite(im, (10 + col * big, y))
        tiny = render(model, -35, 28, 2.4, small // 2 + 8, base).resize((small + 16, small + 16), Image.NEAREST)
        sheet.alpha_composite(tiny, (big * 2 + 18, y + big // 2 - small // 2))
        d.text((12, y - 14), state, fill=(230, 230, 230, 255))
    sheet.save(out_path)
    print("preview written to", out_path)


if __name__ == "__main__":
    main()
