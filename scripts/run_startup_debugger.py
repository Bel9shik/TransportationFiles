"""Capture bounded, function-only LLDB startup diagnostics without raw logs."""
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import time


def safe_message(value):
    """Only LLDB errors pass this function; never save the raw process log."""
    value = re.sub(r'0[xX][0-9a-fA-F]+', '<address>', value)
    value = re.sub(r'(?<![A-Za-z0-9_])(?:~?/|[A-Za-z]:\\)[^\s\'\"<>]+', '<path>', value)
    return value[:300]


def log_summary(text):
    errors = []
    for line in text.splitlines():
        line = line.strip()
        if line.startswith('error:'):
            errors.append(safe_message(line))
    stages = re.findall(r'TRANSPORT_DEBUGGER_STAGE=([a-z_]+)', text)
    return {'stages': list(dict.fromkeys(stages)), 'lldb_errors': errors[-8:]}


def main():
    out = Path('startup-diagnostics').resolve()
    out.mkdir(exist_ok=True)
    script = Path('scripts/probe_startup_lldb.py').resolve()
    version = subprocess.run(['lldb', '--version'], capture_output=True, text=True, timeout=10)
    metadata = {'version': safe_message(version.stdout.splitlines()[0]) if version.stdout else '',
                'version_exit_status': version.returncode, 'runs': []}
    report = out / 'debugger-runs.json'
    report.write_text(json.dumps(metadata, indent=2))
    total_deadline = time.monotonic() + 170
    for attempt in range(10):
        if time.monotonic() >= total_deadline:
            break
        print(f'Debug launch {attempt + 1}', flush=True)
        with tempfile.TemporaryDirectory(prefix='transport-lldb-') as directory:
            root = Path(directory)
            marker, ready = root / 'frame', root / 'ready'
            summary = out / f'lldb-{attempt + 1}.json'
            env = dict(os.environ, TRANSPORT_STARTUP_PROBE=str(marker), TRANSPORT_READY_PROBE=str(ready), PROBE_SUMMARY=str(summary))
            with (root / 'lldb.log').open('w') as log:
                debugger = subprocess.Popen([
                    'lldb', '--no-lldbinit', '--no-use-colors', '--source-quietly', '--batch',
                    '-O', "script print('TRANSPORT_DEBUGGER_STAGE=before_target', flush=True)",
                    '-O', 'settings set auto-confirm true',
                    '-O', 'settings set symbols.enable-external-lookup false',
                    '-O', 'settings set -e symbols.auto-download off',
                    '-O', f'command script import {script}',
                    '-o', "script print('TRANSPORT_DEBUGGER_STAGE=target_loaded', flush=True)",
                    '-k', 'script probe_startup_lldb.snapshot(lldb.debugger)', '-k', 'process kill', '-k', 'quit',
                    '-o', "script print('TRANSPORT_DEBUGGER_STAGE=before_run', flush=True)",
                    '-o', 'run',
                    '-o', 'script probe_startup_lldb.snapshot(lldb.debugger)', '-o', 'process kill', '-o', 'quit',
                    '--', os.environ['PROBE_EXECUTABLE']],
                    cwd=root, env=env, stdin=subprocess.DEVNULL, stdout=log, stderr=log)
                pid = None
                success = False
                started = time.monotonic()
                timed_out = False
                try:
                    deadline = min(started + 35, total_deadline)
                    while debugger.poll() is None and time.monotonic() < deadline:
                        text = (root / 'lldb.log').read_text(errors='replace')
                        match = re.search(r'Process (\d+) launched:', text)
                        if match:
                            pid = int(match[1])
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
                    timed_out = debugger.poll() is None and not success
                    try:
                        debugger.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        pass
                finally:
                    if debugger.poll() is None:
                        if pid:
                            try:
                                os.kill(pid, signal.SIGKILL)
                            except ProcessLookupError:
                                pass
                        debugger.terminate()
                        try:
                            debugger.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            debugger.kill()
                            debugger.wait(timeout=5)
                    text = (root / 'lldb.log').read_text(errors='replace')
                    result = {'attempt': attempt + 1, 'ready': success,
                              'frame_marker': marker.exists(), 'ready_marker': ready.exists(),
                              'process_launched': pid is not None, 'timed_out': timed_out,
                              'debugger_exit_status': debugger.returncode,
                              'elapsed_seconds': round(time.monotonic() - started, 3),
                              'summary_saved': summary.exists(), **log_summary(text)}
                    metadata['runs'].append(result)
                    report.write_text(json.dumps(metadata, indent=2))
                    print(result, flush=True)
            if not success:
                break
    if not metadata['runs'] or not all(item['ready'] for item in metadata['runs']):
        raise SystemExit('Native startup failed; see safe debugger summaries')


if __name__ == '__main__':
    main()
