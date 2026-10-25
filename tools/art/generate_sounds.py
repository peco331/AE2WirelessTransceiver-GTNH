"""Synthesise the fallback wrench-disassembly sound.

Usage: python tools/art/generate_sounds.py [ffmpeg]

Writes mono 44.1 kHz Ogg Vorbis files to assets/ae2wtx/sounds/ (positional sounds must be mono). Every
sound is generated from code, so the repository holds no third-party audio; the real modern AE2 sound comes
from a local resource pack built by tools/packs/build_packs.py. ffmpeg (with libvorbis) does the
encoding; pass its path when it is not on PATH.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "ae2wtx", "sounds")
SR = 44100


def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR


def band(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], "bandpass", fs=SR, output="sos"), x)


def fade(x, a=0.005, b=0.02):
    na, nb = int(SR * a), int(SR * b)
    x[:na] *= np.linspace(0, 1, na)
    x[-nb:] *= np.linspace(1, 0, nb)
    return x


def norm(x, peak):
    return x / np.abs(x).max() * peak


def disassemble(seed):
    """Wrench disassembly: a short hollow pop, modelled on the sound modern AE2 plays
    (a ~40 ms knock: low body near 170-350 Hz, a hollow resonance near 700-950 Hz, a noisy attack)."""
    rng = np.random.default_rng(seed)
    t = t_axis(0.11)
    onset = 0.012 + rng.uniform(0, 0.008)
    tt = np.clip(t - onset, 0, None)
    on = t >= onset
    body_f, res_f = rng.uniform(170, 330), rng.uniform(700, 950)
    body = np.sin(2 * np.pi * body_f * tt) * np.exp(-tt / 0.016)
    res = np.sin(2 * np.pi * res_f * tt + rng.uniform(0, 3)) * np.exp(-tt / 0.009) * 0.7
    click = band(rng.standard_normal(len(t)), 1200, 6000) * np.exp(-tt / 0.004) * 0.6
    return fade(norm((body + res + click) * on, 0.85), 0.002, 0.01)


SOUNDS = {
    **{"wrench_disassemble%d" % i: (lambda i=i: disassemble(10 + i)) for i in range(1, 5)},
}


def encode(name, x, ffmpeg):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        wavfile.write(wav, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
        subprocess.run([ffmpeg, "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "4",
                        "-map_metadata", "-1", "-fflags", "+bitexact", os.path.join(OUT, name + ".ogg")],
                       check=True)


def main():
    ffmpeg = sys.argv[1] if len(sys.argv) > 1 else "ffmpeg"
    os.makedirs(OUT, exist_ok=True)
    for name, make in SOUNDS.items():
        encode(name, make(), ffmpeg)
        print("wrote", name + ".ogg")


if __name__ == "__main__":
    main()
