"""Let LLDB handle loader events; stop successful launches from outside it."""
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import time

out = Path('startup-diagnostics').resolve()
out.mkdir(exist_ok=True)
script = Path('scripts/probe_startup_lldb.py').resolve()
results = []
for attempt in range(10):
    print(f'Debug launch {attempt + 1}', flush=True)
    with tempfile.TemporaryDirectory(prefix='transport-lldb-') as directory:
        root = Path(directory)
        marker, ready = root / 'frame', root / 'ready'
        summary = out / f'lldb-{attempt + 1}.json'
        env = dict(os.environ, TRANSPORT_STARTUP_PROBE=str(marker), TRANSPORT_READY_PROBE=str(ready), PROBE_SUMMARY=str(summary))
        with (root / 'lldb.log').open('w') as log:
            debugger = subprocess.Popen(['lldb', '--batch', '-o', 'settings set auto-confirm true',
                '-o', f'command script import {script}',
                '-k', 'script probe_startup_lldb.snapshot(lldb.debugger)', '-k', 'process kill', '-k', 'quit',
                '-o', 'run',
                '-o', 'script probe_startup_lldb.snapshot(lldb.debugger)', '-o', 'process kill', '-o', 'quit',
                '--', os.environ['PROBE_EXECUTABLE']], cwd=root, env=env, stdin=subprocess.DEVNULL, stdout=log, stderr=log)
            pid = None
            success = False
            try:
                deadline = time.monotonic() + 30
                while debugger.poll() is None and time.monotonic() < deadline:
                    text = (root / 'lldb.log').read_text(errors='replace')
                    match = re.search(r'Process (\d+) launched:', text)
                    if match: pid = int(match[1])
                    if marker.exists() and ready.exists() and not pid:
                        processes = subprocess.check_output(['ps', '-axo', 'pid=,comm='], text=True)
                        for row in processes.splitlines():
                            parts = row.strip().split(None, 1)
                            if len(parts) == 2 and Path(parts[1]).name == 'TransportationFiles':
                                pid = int(parts[0])
                                break
                    if marker.exists() and ready.exists() and pid:
                        success = True
                        os.kill(pid, signal.SIGTERM)
                        break
                    time.sleep(0.05)
                try: debugger.wait(timeout=5)
                except subprocess.TimeoutExpired: pass
            finally:
                if debugger.poll() is None:
                    if pid:
                        try: os.kill(pid, signal.SIGKILL)
                        except ProcessLookupError: pass
                    debugger.terminate()
                    try: debugger.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        debugger.kill(); debugger.wait(timeout=5)
                results.append({'attempt': attempt + 1, 'ready': success, 'summary_saved': summary.exists()})
                (out / 'debugger-runs.json').write_text(json.dumps(results, indent=2))
                print(results[-1], flush=True)
        if not success: break

if not results or not all(item['ready'] for item in results):
    raise SystemExit('Native startup failed; see function-only summaries')
