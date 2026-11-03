"""Export safe CPU function aggregates; never export raw trace, paths or addresses.

Official parser: https://perfetto.dev/docs/analysis/trace-processor-python
SQL: https://perfetto.dev/docs/analysis/stdlib-docs#stacks.cpu_profiling
Install the pinned official parser into a task-local venv: perfetto==0.59.1.
"""
import argparse
from collections import defaultdict
from importlib.metadata import version
import json
from pathlib import Path
import re
import sys

from perfetto.trace_processor import TraceProcessor


FUNCTIONS = """INCLUDE PERFETTO MODULE stacks.cpu_profiling;
SELECT name, mapping_name, SUM(self_count) AS self_count,
       SUM(cumulative_count) AS cumulative_count
FROM cpu_profiling_summary_tree GROUP BY name, mapping_name
"""
ATTRIBUTION = """WITH RECURSIVE ancestry(sample_id, callsite_id) AS (
  SELECT id, callsite_id FROM cpu_profiling_samples
  UNION ALL
  SELECT a.sample_id, c.parent_id FROM ancestry a
  JOIN stack_profile_callsite c ON c.id = a.callsite_id
  WHERE c.parent_id IS NOT NULL
), flags AS (
  SELECT sample_id,
    MAX(COALESCE(s.name, f.deobfuscated_name, f.name, '') LIKE '_readFiles%'
        OR COALESCE(s.name, f.deobfuscated_name, f.name, '') LIKE 'profileWorkerCpuWork%') AS worker,
    MAX(COALESCE(s.name, f.deobfuscated_name, f.name, '') LIKE 'TransferSender.%'
        OR COALESCE(s.name, f.deobfuscated_name, f.name, '') LIKE 'profileCpuWork%') AS sender
  FROM ancestry a JOIN stack_profile_callsite c ON c.id = a.callsite_id
  JOIN stack_profile_frame f ON f.id = c.frame_id
  LEFT JOIN stack_profile_symbol s ON s.symbol_set_id = f.symbol_set_id
  GROUP BY sample_id
)
SELECT COALESCE(SUM(worker), 0) AS worker,
       COALESCE(SUM(sender), 0) AS sender,
       COALESCE(SUM(worker AND sender), 0) AS overlap,
       COALESCE(SUM(NOT worker AND NOT sender), 0) AS unattributed
FROM flags
"""


def safe_function(value):
    # Whitelist symbol syntax. Reject all source paths, address-like symbols,
    # control characters and arbitrary strings instead of trying to strip them.
    if value and len(value) <= 250 and re.fullmatch(r'[A-Za-z0-9_$ .:<>,+=\[\]()!?&|^~#@-]+', value):
        if not re.search(r'0[xX][0-9a-fA-F]+|file:|[A-Za-z]:', value) and not re.fullmatch(r'[0-9a-fA-F]{8,}', value):
            return value
    return '[unknown or redacted]'


def safe_module(value):
    # Mapping names can contain absolute paths. Export only known binary names.
    base = re.split(r'[\\/]', value or '')[-1]
    if re.fullmatch(r'[A-Za-z0-9_.-]+\.(?:exe|dll|aot|so|dylib)', base, re.IGNORECASE):
        return base
    return '[unknown]'


def safe_thread(value):
    if value in ('DartWorker', 'DartMutator', 'transport-file-reader', 'main'):
        return value
    return '[other]'


def summarize(path):
    functions = defaultdict(lambda: [0, 0])
    with TraceProcessor(trace=str(path)) as processor:
        for row in processor.query(FUNCTIONS):
            counts = functions[(safe_function(row.name), safe_module(row.mapping_name))]
            counts[0] += int(row.self_count)
            counts[1] += int(row.cumulative_count)
        threads = defaultdict(int)
        for row in processor.query('SELECT thread_name, COUNT(*) AS samples FROM cpu_profiling_samples GROUP BY thread_name'):
            threads[safe_thread(row.thread_name)] += int(row.samples)
        attribution = next(iter(processor.query(ATTRIBUTION)))
    stats = [dict(function=name, module=module, self_count=counts[0],
                  cumulative_count=counts[1]) for (name, module), counts in functions.items()]
    stats.sort(key=lambda row: (-row['self_count'], -row['cumulative_count'], row['function']))
    match = re.fullmatch(r'sender-(100MiB|20x1MiB|other)-[0-9]+\.pftrace', path.name)
    return {
        'scenario': match.group(1) if match else 'smoke',
        'trace_bytes': path.stat().st_size,
        'samples': sum(threads.values()),
        'threads': [dict(name=name, samples=count) for name, count in sorted(threads.items())],
        'observed_stack_attribution': dict(worker=int(attribution.worker), sender=int(attribution.sender),
                                           overlap=int(attribution.overlap), unattributed=int(attribution.unattributed)),
        'top_functions': stats[:100],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--traces', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--verification', type=Path)
    parser.add_argument('--smoke', action='store_true')
    args = parser.parse_args()
    traces = sorted(args.traces.glob('*.pftrace'), key=lambda p: p.stat().st_mtime_ns)
    if args.smoke:
        traces += sorted(args.traces.glob('*.perfetto-trace'))
    assert traces, 'No CPU profiles generated'
    if not args.smoke:
        assert len(traces) == 4, 'Expected four Dart sender profiles'
        assert sum('sender-100MiB-' in p.name for p in traces) == 2
        assert sum('sender-20x1MiB-' in p.name for p in traces) == 2
    profiles = [summarize(path) for path in traces]
    assert all(profile['samples'] > 0 for profile in profiles), 'Empty CPU sample profile'
    result = {
        'purpose': 'CPU diagnosis only; profiler changes timings; not a throughput comparison',
        'source_commit': 'c8ad2501cf398b83aa389e8a1b5fa24f4af1cd85',
        'parser': 'perfetto==' + version('perfetto'),
        'sampling_interval_microseconds': 1000,
        'counts': 'Samples, not durations. Cumulative counts may include recursive/multiple frames.',
        'attribution': 'Observed _readFiles/TransferSender stack frames only; async boundaries leave other samples unattributed. Thread names are not isolate identities.',
        'privacy': 'Whitelisted function/module/thread names and counts only. No addresses, absolute paths, raw CPU trace, heap or core.',
        'profiles': profiles,
    }
    if args.verification:
        verification = json.loads(args.verification.read_text())
        result['verified_transfer_cases'] = [
            {key: case[key] for key in ('scenario', 'implementation', 'bytes', 'verified_files')}
            for case in verification['cases']
        ]
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'profiles': len(profiles), 'samples': sum(p['samples'] for p in profiles)}))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Never print parser diagnostics that could contain raw stack data,
        # addresses or trace paths. The runner retains raw traces locally only.
        print('CPU aggregation failed: ' + type(error).__name__, file=sys.stderr)
        raise SystemExit(1)
