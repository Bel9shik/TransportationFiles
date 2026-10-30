"""Run Java and Dart peers against each other, without a UI or fixed ports."""
import os
from pathlib import Path
import subprocess
import tempfile
import time

root = Path(__file__).resolve().parent.parent
with tempfile.TemporaryDirectory(prefix='transport-interop-') as temp:
    processes = []
    handles = []
    try:
        commands = [(['dart', 'run', 'tool/interop.dart', temp], root / 'flutter_app'),
                    (['gradlew.bat' if os.name == 'nt' else './gradlew', ':core:flutterInterop', f'--args="{temp}"'], root)]
        for index, (command, cwd) in enumerate(commands):
            log = open(Path(temp) / f'{index}.log', 'w')
            handles.append(log)
            processes.append(subprocess.Popen(command, cwd=cwd, stdout=log, stderr=log))
        deadline = time.monotonic() + 240
        while any(process.poll() is None for process in processes):
            if any(process.poll() not in (None, 0) for process in processes):
                raise RuntimeError('An interoperability peer failed')
            if time.monotonic() > deadline:
                raise TimeoutError('Interoperability test timed out')
            time.sleep(0.2)
        assert all(process.returncode == 0 for process in processes)
    finally:
        for process in processes:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
        for index, log in enumerate(handles):
            log.close()
            print((Path(temp) / f'{index}.log').read_text(errors='replace'))
