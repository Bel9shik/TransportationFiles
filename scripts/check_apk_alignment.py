#!/usr/bin/env python3
"""Reject 64-bit Android libraries that cannot load with 16-KB memory pages."""
import struct
import sys
import zipfile


def load_alignments(data):
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("expected little-endian ELF64")
    offset = struct.unpack_from("<Q", data, 32)[0]
    stride, count = struct.unpack_from("<HH", data, 54)
    return [
        struct.unpack_from("<Q", data, offset + i * stride + 48)[0]
        for i in range(count)
        if struct.unpack_from("<I", data, offset + i * stride)[0] == 1
    ]


def check(path):
    checked = 0
    failures = []
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if not name.startswith(("lib/arm64-v8a/", "lib/x86_64/")) or not name.endswith(".so"):
                continue
            alignments = load_alignments(apk.read(name))
            checked += 1
            if not alignments or min(alignments) < 16384:
                failures.append(f"{name}: LOAD alignments {alignments}")
    if failures or not checked:
        raise SystemExit("16-KB alignment failed:\n" + "\n".join(failures or ["no 64-bit libraries found"]))
    print(f"16-KB ELF alignment passed: {checked} libraries in {path}")


if __name__ == "__main__":
    check(sys.argv[1])
