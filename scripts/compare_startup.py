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
for name, path in zip(('java', 'flutter'), paths):
    output = subprocess.check_output([sys.executable, str(root / 'scripts/measure_startup.py'),
                                      str(root / path), '--runs', '5', '--max-seconds', '15'] + (['--require-ready'] if name == 'flutter' else []), text=True)
    results[name] = json.loads(output)
results['median_ratio_flutter_to_java'] = round(results['flutter']['median_seconds'] / results['java']['median_seconds'], 3)
text = json.dumps(results, ensure_ascii=False, indent=2)
(root / 'startup-results.json').write_text(text + '\n')
print(text)
if 'GITHUB_STEP_SUMMARY' in os.environ:
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
        summary.write('## Packaged startup comparison\n```json\n' + text + '\n```\n')
