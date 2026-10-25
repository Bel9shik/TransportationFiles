"""Check that the packaged native launcher starts with its bundled Java runtime."""
import pathlib
import plistlib
import struct
import subprocess
import sys
import tempfile

root = pathlib.Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parents[1] / "app/build/package"
executables = {
    "darwin": "TransportationFiles.app/Contents/MacOS/TransportationFiles",
    "win32": "TransportationFiles/TransportationFiles.exe",
    "linux": "TransportationFiles/bin/TransportationFiles",
}
executable = root / executables[sys.platform]
assert executable.is_file(), f"Missing packaged launcher: {executable}"
sources = pathlib.Path(__file__).resolve().parents[1] / "app/src/main"
if sys.platform == "darwin":
    contents = root / "TransportationFiles.app/Contents"
    with (contents / "Info.plist").open("rb") as stream:
        icon_name = plistlib.load(stream)["CFBundleIconFile"]
    assert (contents / "Resources" / icon_name).read_bytes() == (sources / "packaging/app-icon.icns").read_bytes()
elif sys.platform == "win32":
    assert (root / "TransportationFiles/runtime/bin/server/jvm.dll").is_file(), "Bundled Java missing"
    icon = (sources / "packaging/app-icon.ico").read_bytes()
    count = struct.unpack_from("<H", icon, 4)[0]
    size, offset = struct.unpack_from("<II", icon, 6 + (count - 1) * 16 + 8)
    assert icon[offset:offset + size] in executable.read_bytes(), "Custom icon missing from Windows launcher"
else:
    expected = (sources / "resources/icons/app-icon.png").read_bytes()
    assert any(icon.read_bytes() == expected for icon in root.rglob("*.png")), "Custom icon missing from Linux package"
# Launch outside the checkout, including a space in the working directory.
with tempfile.TemporaryDirectory(prefix="transport smoke ") as directory:
    log = pathlib.Path(directory) / "startup.log"
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen([str(executable)], cwd=directory, stdout=output, stderr=output)
        try:
            try:
                code = process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                code = None
            diagnostics = log.read_text(encoding="utf-8", errors="replace")
            assert code is None, f"Launcher exited early ({code}):\n{diagnostics}"
            assert "Exception" not in diagnostics and "Error:" not in diagnostics, diagnostics
            print(f"PASS: {sys.platform} packaged launcher remained running with no startup errors")
        finally:
            if sys.platform == "win32":
                # jpackage may spawn a child which inherits the log handle.
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
            else:
                process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
