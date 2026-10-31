"""Alternate packaged Java/Flutter launches; keep the first sample separate."""
import argparse
import json
from pathlib import Path
import platform
import statistics
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-app', required=True, type=Path)
parser.add_argument('--dart-app', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--runs', type=int, default=10)
args = parser.parse_args()
assert args.runs > 0
measure = Path(__file__).with_name('measure_startup.py')
commands = {
    'java': [str(args.java_app.resolve(strict=True))],
    'dart_aot': [str(args.dart_app.resolve(strict=True)), '--require-ready'],
}
samples = {name: [] for name in commands}
for run in range(args.runs + 1):
    order = list(commands) if run % 2 == 0 else list(reversed(commands))
    for name in order:
        output = subprocess.check_output(
            [sys.executable, str(measure)] + commands[name] + ['--runs', '1', '--max-seconds', '15'],
            text=True, timeout=30)
        seconds = json.loads(output)['seconds']
        assert len(seconds) == 1 and seconds[0] > 0
        samples[name].extend(seconds)
        print(f'{name}: sample {run}, {seconds[0]} seconds', file=sys.stderr, flush=True)
result = {'platform': platform.platform(),
          'metric': 'new process to first-frame marker; OS caches not cleared; '
                    'alternating order; first observation excluded from repeated-launch median',
          'first_observation_is_not_a_cold_start': True}
for name, timings in samples.items():
    measured = timings[1:]
    result[name] = {'first_observation_seconds': timings[0], 'seconds': measured,
                    'median_seconds': statistics.median(measured),
                    'min_seconds': min(measured), 'max_seconds': max(measured)}
text = json.dumps(result, indent=2)
args.output.write_text(text + '\n')
print(text)
