"""Build the optional client resource packs for the transceiver.

Usage:
  python tools/packs/build_packs.py classic                  # classic look
  python tools/packs/build_packs.py sounds <minecraft assets dir>
  python tools/packs/build_packs.py all <minecraft assets dir>

Both packs are made from material that this repository must not redistribute, so they are built locally
into build/resourcepacks/ and never committed or attached to a release:

- AE2WTX-Classic-Look.zip: the ExtendedAE_Plus Light Mode model and textures (C-H716, _leng, fish_旦) that
  releases up to 1.1.1 used, read from Git history at CLASSIC_COMMIT.  The mod's block renderer draws
  assets/ae2wtx/models/classic/transceiver_{off,on}.json in place of the built-in look when a pack has them.
- AE2WTX-Modern-Sounds.zip: the sound modern AE2 plays when a wrench disassembles a block
  (minecraft:entity.item_frame.remove_item, 4 variants), copied from a newer Minecraft's asset store that
  the player already has, e.g. <launcher>/assets.  It replaces the mod's synthesised fallback sound.
"""
import hashlib
import io
import json
import os
import subprocess
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "build", "resourcepacks")
CLASSIC_COMMIT = "31a0e73"
CLASSIC_SRC = "src/main/resources/assets/ae2wtx/"
CLASSIC_MODELS = {"models/blocks/lable_off.json": "models/classic/transceiver_off.json",
                  "models/blocks/lable_on.json": "models/classic/transceiver_on.json"}
CLASSIC_TEXTURES = "textures/blocks/wireless_transceiver/"
FRAME_SOUNDS = ["minecraft/sounds/entity/itemframe/remove_item%d.ogg" % i for i in range(1, 5)]


def git(*args):
    return subprocess.run(["git", "-C", ROOT] + list(args), check=True, capture_output=True).stdout


def pack_meta(description):
    return json.dumps({"pack": {"pack_format": 1, "description": description}}, ensure_ascii=False, indent=2)


def write_zip(name, files):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for arc, data in sorted(files.items()):
            info = zipfile.ZipInfo(arc, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    print("wrote", path, "sha256", hashlib.sha256(open(path, "rb").read()).hexdigest())


def build_classic():
    files = {"pack.mcmeta": pack_meta(
        "AE2WTX classic look: ExtendedAE_Plus Light Mode model by C-H716, _leng, fish_旦").encode("utf-8")}
    for src, dst in CLASSIC_MODELS.items():
        files["assets/ae2wtx/" + dst] = git("show", "%s:%s%s" % (CLASSIC_COMMIT, CLASSIC_SRC, src))
    listing = git("ls-tree", "-r", "--name-only", CLASSIC_COMMIT, "--", CLASSIC_SRC + CLASSIC_TEXTURES)
    for path in listing.decode("utf-8").split():
        files["assets/ae2wtx/" + path[len(CLASSIC_SRC):]] = git("show", "%s:%s" % (CLASSIC_COMMIT, path))
    write_zip("AE2WTX-Classic-Look.zip", files)


def build_sounds(assets):
    objects = {}
    for idx in sorted(os.listdir(os.path.join(assets, "indexes"))):
        with open(os.path.join(assets, "indexes", idx), encoding="utf-8") as f:
            entries = json.load(f).get("objects", {})
        for name in FRAME_SOUNDS:
            if name in entries:
                objects.setdefault(name, entries[name]["hash"])
    missing = [n for n in FRAME_SOUNDS if n not in objects]
    if missing:
        sys.exit("not in any asset index under %s (start Minecraft 1.9 or newer once): %s" % (assets, missing))
    files = {"pack.mcmeta": pack_meta("AE2WTX: modern AE2 wrench disassembly sound").encode("utf-8")}
    for i, name in enumerate(FRAME_SOUNDS, 1):
        h = objects[name]
        with open(os.path.join(assets, "objects", h[:2], h), "rb") as f:
            data = f.read()
        if hashlib.sha1(data).hexdigest() != h:
            sys.exit("asset object %s does not match its hash" % h)
        files["assets/ae2wtx/sounds/wrench_disassemble%d.ogg" % i] = data
    write_zip("AE2WTX-Modern-Sounds.zip", files)


def main():
    what = sys.argv[1] if len(sys.argv) > 1 else ""
    if what not in ("classic", "sounds", "all") or (what != "classic" and len(sys.argv) < 3):
        sys.exit(__doc__)
    if what in ("classic", "all"):
        build_classic()
    if what in ("sounds", "all"):
        build_sounds(sys.argv[2])


if __name__ == "__main__":
    main()
