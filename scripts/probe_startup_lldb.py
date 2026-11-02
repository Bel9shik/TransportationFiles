"""Ephemeral CI diagnostic: record only native function names, never memory."""
import json
import os
from pathlib import Path
import tempfile
import time
import lldb


def run(debugger):
    debugger.SetAsync(True)
    results = []
    output = Path('startup-diagnostics/lldb-summary.json')
    output.parent.mkdir(exist_ok=True)
    for attempt in range(20):
        with tempfile.TemporaryDirectory(prefix='transport-debug-') as directory:
            marker = Path(directory) / 'frame'
            ready = Path(directory) / 'ready'
            environment = dict(os.environ, TRANSPORT_STARTUP_PROBE=str(marker), TRANSPORT_READY_PROBE=str(ready))
            target = debugger.CreateTarget(os.environ['PROBE_EXECUTABLE'])
            error = lldb.SBError()
            process = target.Launch(debugger.GetListener(), None,
                [f'{k}={v}' for k, v in environment.items()], '/dev/null', '/dev/null', '/dev/null',
                directory, 0, False, error)
            result = {'attempt': attempt + 1}
            try:
                if error.Fail():
                    result['launch_error'] = str(error)
                else:
                    deadline = time.monotonic() + 30
                    while time.monotonic() < deadline:
                        state = process.GetState()
                        if marker.exists() and ready.exists():
                            result['ready'] = True
                            break
                        if state in (lldb.eStateStopped, lldb.eStateCrashed, lldb.eStateExited):
                            result['state'] = state
                            result['exit_status'] = process.GetExitStatus()
                            break
                        time.sleep(0.02)
                    if not result.get('ready'):
                        if process.GetState() == lldb.eStateRunning:
                            process.Stop()
                            time.sleep(0.2)
                        result['threads'] = []
                        for thread in process:
                            if thread.GetStopReason() != lldb.eStopReasonNone:
                                result['threads'].append({
                                    'reason': thread.GetStopReason(),
                                    'functions': [{
                                        'module': frame.GetModule().GetFileSpec().GetFilename(),
                                        'function': frame.GetFunctionName(),
                                    } for frame in list(thread)[:30]],
                                })
            finally:
                process.Kill()
                debugger.DeleteTarget(target)
                results.append(result)
                output.write_text(json.dumps(results, indent=2))
            if not result.get('ready'):
                break
    print(json.dumps(results, indent=2))
