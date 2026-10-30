"""Measure fresh process launch to the first painted frame (opt-in readiness marker)."""
import argparse
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys
import tempfile
import time

parser = argparse.ArgumentParser()
parser.add_argument('executable', type=Path)
parser.add_argument('--require-ready', action='store_true')
parser.add_argument('--runs', type=int, default=3)
parser.add_argument('--max-seconds', type=float, default=10)
args = parser.parse_args()
assert args.runs > 0 and args.max_seconds > 0
executable = args.executable.resolve(strict=True)
timings = []
for run in range(args.runs):
    with tempfile.TemporaryDirectory(prefix='transport startup ') as directory:
        marker = Path(directory) / 'ready'
        environment = dict(os.environ, TRANSPORT_STARTUP_PROBE=str(marker))
        ready = Path(directory) / 'network-ready'
        if args.require_ready:
            environment['TRANSPORT_READY_PROBE'] = str(ready)
        with (Path(directory) / 'log').open('w') as output:
            started = time.perf_counter()
            print(f'{executable.name}: run {run + 1}, launching', file=sys.stderr, flush=True)
            process = subprocess.Popen([str(executable)], cwd=directory, env=environment,
                                       stdout=output, stderr=output)
            print(f'{executable.name}: PID {process.pid}, waiting for frame', file=sys.stderr, flush=True)
            try:
                while not marker.exists():
                    assert process.poll() is None, 'App exited before first frame'
                    assert time.perf_counter() - started < args.max_seconds, 'Startup exceeded threshold'
                    time.sleep(0.01)
                timings.append(round(time.perf_counter() - started, 3))
                if args.require_ready:
                    while not ready.exists():
                        assert process.poll() is None, 'App exited before network initialization'
                        assert time.perf_counter() - started < args.max_seconds, 'Network initialization failed or timed out'
                        time.sleep(0.01)
            except BaseException:
                output.flush()
                print((Path(directory) / 'log').read_text(errors='replace'), file=__import__('sys').stderr)
                raise
            finally:
                print(f'{executable.name}: stopping PID {process.pid}', file=sys.stderr, flush=True)
                if os.name == 'nt':
                    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
                else:
                    process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
print(json.dumps({'seconds': timings, 'median_seconds': statistics.median(timings)}))
