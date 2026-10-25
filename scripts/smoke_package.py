"""Check that the packaged native launcher starts with its bundled Java runtime."""
import pathlib
import subprocess
import sys
import tempfile

root = pathlib.Path(__file__).resolve().parents[1] / "app/build/package"
executables = {
    "darwin": "TransportationFiles.app/Contents/MacOS/TransportationFiles",
    "win32": "TransportationFiles/TransportationFiles.exe",
    "linux": "TransportationFiles/bin/TransportationFiles",
}
executable = root / executables[sys.platform]
assert executable.is_file(), f"Missing packaged launcher: {executable}"
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
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
