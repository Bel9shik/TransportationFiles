"""Measure packaged Java and Flutter builds on the same runner, outside checkout."""
import json
import os
from pathlib import Path
import platform
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
system = platform.system()
paths = {
    'Darwin': ('app/build/package/TransportationFiles.app/Contents/MacOS/TransportationFiles',
               'flutter_app/build/macos/Build/Products/Release/TransportationFiles.app/Contents/MacOS/TransportationFiles'),
    'Windows': ('app/build/package/TransportationFiles/TransportationFiles.exe',
                'flutter_app/build/windows/x64/runner/Release/transportfiles.exe'),
    'Linux': ('app/build/package/TransportationFiles/bin/TransportationFiles',
              'flutter_app/build/linux/x64/release/bundle/transportfiles'),
}[system]
results = {'platform': system, 'architecture': platform.machine(), 'metric': 'process to first painted frame; OS caches are not cleared'}
diagnostics = root / 'startup-diagnostics'
diagnostics.mkdir(exist_ok=True)


def save_results():
    (root / 'startup-results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')


for name, path in zip(('java', 'flutter'), paths):
    print(f'Measuring {name} startup', file=sys.stderr, flush=True)
    results[name] = {'status': 'running'}
    save_results()
    output_path = diagnostics / f'{name}.stdout.log'
    error_path = diagnostics / f'{name}.stderr.log'
    try:
        # Persist each peer's output independently of the runner's job log. A
        # failed Java baseline must not prevent testing the native application.
        with output_path.open('w') as output, error_path.open('w') as errors:
            completed = subprocess.run(
                [sys.executable, str(root / 'scripts/measure_startup.py'),
                 str(root / path), '--runs', '5', '--max-seconds', '15']
                + (['--require-ready'] if name == 'flutter' else []),
                stdout=output, stderr=errors, text=True, timeout=120,
            )
        if completed.returncode != 0:
            raise RuntimeError(f'Measurement exited with code {completed.returncode}')
        results[name] = json.loads(output_path.read_text())
    except (subprocess.TimeoutExpired, RuntimeError, json.JSONDecodeError) as error:
        results[name] = {'error': str(error), 'stderr_log': str(error_path.relative_to(root))}
    finally:
        save_results()
        print(error_path.read_text(errors='replace'), file=sys.stderr, flush=True)
failed = any('error' in results[name] for name in ('java', 'flutter'))
if not failed:
    results['median_ratio_flutter_to_java'] = round(results['flutter']['median_seconds'] / results['java']['median_seconds'], 3)
text = json.dumps(results, ensure_ascii=False, indent=2)
save_results()
print(text)
if 'GITHUB_STEP_SUMMARY' in os.environ:
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
        summary.write('## Packaged startup comparison\n```json\n' + text + '\n```\n')
if failed:
    raise SystemExit('Startup comparison failed; see startup-diagnostics and startup-results.json')
