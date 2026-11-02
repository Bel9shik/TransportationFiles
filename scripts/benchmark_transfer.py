"""Compare real Java and Dart AOT transfer cores over loopback in separate processes."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import platform
import random
import shutil
import statistics
import subprocess
import tempfile
import time


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default=shutil.which('java'), type=Path)
    parser.add_argument('--classpath', required=True, type=Path)
    parser.add_argument('--dart-aot', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--warmups', type=int, default=3)
    parser.add_argument('--runs', type=int, default=7)
    parser.add_argument('--max-dart-ratio', type=float,
                        help='Fail if Dart median / Java median exceeds this ratio for either workload')
    args = parser.parse_args()
    assert args.warmups >= 0 and args.runs > 0
    assert args.java is not None, 'Java executable not found; supply --java'
    assert args.max_dart_ratio is None or args.max_dart_ratio > 0
    total_runs = args.warmups + args.runs
    peers = {
        'java': [str(args.java.resolve(strict=True)), '-cp', args.classpath.read_text().strip(),
                 'kardash.project.transfer.TransferBenchmark'],
        'dart_aot': [str(args.dart_aot.resolve(strict=True))],
    }
    result = {'platform': platform.platform(), 'warmups': args.warmups, 'runs': args.runs,
              'metric': 'send call through receiver acknowledgement, seconds; separate processes; '
                        'loopback; SHA256 and disk writes included; OS caches not cleared', 'cases': []}
    with tempfile.TemporaryDirectory(prefix='transport benchmark ') as directory:
        root = Path(directory)
        source = root / 'source'
        source.mkdir()
        rng = random.Random(20261031)
        # Deterministic non-sparse data, generated before timing; no compression in either core.
        for name, size in [('large.bin', 100)] + [(f'small-{i:02}.bin', 1) for i in range(20)]:
            with (source / name).open('wb') as output:
                for _ in range(size):
                    output.write(rng.randbytes(1024 * 1024))
        scenarios = [('100MiB', [source / 'large.bin']),
                     ('20x1MiB', sorted(source.glob('small-*.bin')))]
        # Reverse the order for the second workload to reduce a fixed order bias.
        for scenario_index, (scenario, files) in enumerate(scenarios):
            expected = Counter((f.stat().st_size, digest(f)) for f in files)
            expected = Counter({key: count * total_runs for key, count in expected.items()})
            total_bytes = sum(f.stat().st_size for f in files)
            order = list(peers) if scenario_index == 0 else list(reversed(peers))
            for name in order:
                print(f'{scenario}: {name}, {args.warmups} warmups + {args.runs} samples', flush=True)
                with tempfile.TemporaryDirectory(dir=root) as destination:
                    destination = Path(destination)
                    with (root / 'receiver.log').open('w') as log:
                        receiver = subprocess.Popen(peers[name] + ['serve', str(destination)],
                                                    stdin=subprocess.PIPE, stdout=log, stderr=log)
                        try:
                            deadline = time.monotonic() + 30
                            port = destination / 'port'
                            while not port.exists() or not port.read_text():
                                assert receiver.poll() is None, 'Receiver exited'
                                assert time.monotonic() < deadline, 'Receiver did not start'
                                time.sleep(0.01)
                            output = subprocess.check_output(
                                peers[name] + ['send', port.read_text(), str(total_runs)] + list(map(str, files)),
                                text=True, timeout=600)
                            samples = [float(line) for line in output.splitlines()]
                            assert len(samples) == total_runs and all(s > 0 for s in samples), output
                            actual = Counter((f.stat().st_size, digest(f))
                                             for f in destination.rglob('*') if f.is_file() and f != port)
                            assert actual == expected, 'Received data, file count or SHA256 differs'
                            measured = samples[args.warmups:]
                            median = statistics.median(measured)
                            case = {'implementation': name, 'scenario': scenario, 'bytes': total_bytes,
                                    'warmup_seconds': samples[:args.warmups], 'seconds': measured,
                                    'median_seconds': median, 'min_seconds': min(measured),
                                    'max_seconds': max(measured), 'MiB_per_second': total_bytes / 1024**2 / median,
                                    'verified_files': sum(actual.values())}
                            result['cases'].append(case)
                            print(json.dumps(case), flush=True)
                        except BaseException:
                            log.flush()
                            print((root / 'receiver.log').read_text())
                            raise
                        finally:
                            receiver.terminate()
                            try:
                                receiver.wait(timeout=5)
                            except subprocess.TimeoutExpired:
                                receiver.kill()
                                receiver.wait(timeout=5)
                            receiver.stdin.close()
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    if args.max_dart_ratio is not None:
        for scenario, _ in scenarios:
            cases = {c['implementation']: c['median_seconds']
                     for c in result['cases'] if c['scenario'] == scenario}
            ratio = cases['dart_aot'] / cases['java']
            assert ratio <= args.max_dart_ratio, f'{scenario}: Dart/Java {ratio:.3f} exceeds {args.max_dart_ratio}'


if __name__ == '__main__':
    main()
