"""Capture bounded, function-only LLDB startup diagnostics without raw logs."""
import json
import os
from pathlib import Path
import re
import signal
import sys
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


def core_main():
    """Run only the ephemeral CI app normally; inspect its own crash offline."""
    import resource

    out = Path('startup-diagnostics').resolve()
    out.mkdir(exist_ok=True)
    report = out / 'core-runs.json'
    metadata = {'mode': 'untraced_owned_process_core', 'runs': []}

    def save():
        report.write_text(json.dumps(metadata, indent=2))

    save()
    core_directory = Path('/cores')
    soft, hard = resource.getrlimit(resource.RLIMIT_CORE)
    metadata['core_directory_writable'] = os.access(core_directory, os.W_OK)
    metadata['core_hard_limit_unlimited'] = hard == resource.RLIM_INFINITY
    if not metadata['core_directory_writable'] or hard == 0:
        metadata['constraint'] = 'core_directory_not_writable' if not metadata['core_directory_writable'] else 'core_hard_limit_zero'
        save()
        raise SystemExit('Core capture unavailable; see safe status summary')
    try:
        resource.setrlimit(resource.RLIMIT_CORE, (hard, hard))
    except (OSError, ValueError):
        metadata['constraint'] = 'process_core_limit_could_not_be_enabled'
        save()
        raise SystemExit('Core capture unavailable; see safe status summary')
    metadata['process_core_limit_enabled'] = True
    save()
    deadline = time.monotonic() + 150
    executable = str(Path(os.environ['PROBE_EXECUTABLE']).resolve(strict=True))
    snapshot_script = Path('scripts/probe_startup_lldb.py').resolve()
    for attempt in range(20):
        if time.monotonic() >= deadline:
            metadata['constraint'] = 'overall_launch_budget_exhausted'
            break
        with tempfile.TemporaryDirectory(prefix='transport-core-startup-') as directory:
            root = Path(directory)
            marker, ready = root / 'frame', root / 'ready'
            environment = dict(os.environ, TRANSPORT_STARTUP_PROBE=str(marker), TRANSPORT_READY_PROBE=str(ready))
            result = {'attempt': attempt + 1, 'ready': False, 'core_owned': False}
            core = None
            core_owned = False
            started = time.monotonic()
            with (root / 'app.log').open('w') as log:
                process = subprocess.Popen([executable], cwd=root, env=environment,
                                           stdin=subprocess.DEVNULL, stdout=log, stderr=log)
                # Ownership is established only when this exact PID's core is absent.
                # Never enumerate, read, modify or delete any preexisting core.
                core = core_directory / f'core.{process.pid}'
                core_owned = not core.exists()
                result['core_owned'] = core_owned
                try:
                    launch_deadline = min(started + 15, deadline)
                    while process.poll() is None and time.monotonic() < launch_deadline:
                        if marker.exists() and ready.exists():
                            result['ready'] = True
                            break
                        time.sleep(0.01)
                    # Kernel core writing may delay process reaping. This grants
                    # only the already-owned core a brief bounded completion wait.
                    if not result['ready'] and core_owned and core.exists() and process.poll() is None:
                        try:
                            process.wait(timeout=15)
                        except subprocess.TimeoutExpired:
                            result['constraint'] = 'core_write_did_not_complete_in_budget'
                    result['app_exit_status'] = process.poll()
                    result['frame_marker'] = marker.exists()
                    result['ready_marker'] = ready.exists()
                    result['elapsed_seconds'] = round(time.monotonic() - started, 3)
                    metadata['runs'].append(result)
                    save()
                    print(result, flush=True)
                    if result['ready']:
                        continue
                    if not core_owned:
                        result['constraint'] = 'core_ownership_not_established'
                    elif process.poll() is None:
                        result.setdefault('constraint', 'startup_deadline_without_process_exit')
                    elif process.returncode >= 0:
                        result['constraint'] = 'process_exited_without_crash_signal'
                    elif not core.exists():
                        result['constraint'] = 'own_process_exited_but_no_core_was_created'
                    else:
                        result['core_created'] = True
                        summary = out / 'offline-core.json'
                        debugger_environment = dict(os.environ, PROBE_SUMMARY=str(summary), PROBE_CORE_CAPTURE='1')
                        with (root / 'offline-lldb.log').open('w') as debugger_log:
                            command = ['lldb', '--no-lldbinit', '--no-use-colors', '--source-quietly', '--batch',
                                '-O', 'settings set symbols.enable-external-lookup false',
                                '-O', 'settings set -e symbols.auto-download off',
                                '-O', f'command script import {snapshot_script}',
                                '-o', 'script probe_startup_lldb.snapshot(lldb.debugger)',
                                '--core', str(core), '--', executable]
                            try:
                                debugger = subprocess.run(command, cwd=root, env=debugger_environment,
                                    stdin=subprocess.DEVNULL, stdout=debugger_log, stderr=debugger_log, timeout=40)
                                result['offline_debugger_exit_status'] = debugger.returncode
                            except subprocess.TimeoutExpired:
                                result['constraint'] = 'offline_debugger_deadline'
                        result.update(log_summary((root / 'offline-lldb.log').read_text(errors='replace')))
                        result['summary_saved'] = summary.exists()
                    save()
                    break
                finally:
                    if process.poll() is None:
                        process.terminate()
                        try:
                            process.wait(timeout=3)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            try:
                                process.wait(timeout=3)
                            except subprocess.TimeoutExpired:
                                result['constraint'] = 'own_process_did_not_reap_after_kill'
                    if core_owned and core is not None and core.exists():
                        # This core belongs only to the Popen child from this
                        # attempt. It never leaves the disposable runner.
                        core.unlink()
                        result['own_core_removed'] = True
                    save()
    save()
    if not metadata['runs'] or not all(item['ready'] for item in metadata['runs']):
        raise SystemExit('Untraced startup failed; see safe status/function summaries')


if __name__ == '__main__':
    core_main() if '--core' in sys.argv else main()
