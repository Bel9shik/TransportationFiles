"""Snapshot only public function/module names of the stopped test process."""
import json
import os
from pathlib import Path
import re
import lldb


def safe_symbol(value):
    if value is None:
        return None
    value = re.sub(r'0[xX][0-9a-fA-F]+', '<address>', value)
    value = re.sub(r'(?<![A-Za-z0-9_])(?:~?/|[A-Za-z]:\\)[^\s\'\"<>]+', '<path>', value)
    return value[:500]


def snapshot(debugger):
    print('TRANSPORT_DEBUGGER_STAGE=snapshot_started', flush=True)
    process = debugger.GetSelectedTarget().GetProcess()
    result = {'stage': 'process_state', 'state': process.GetState(),
              'exit_status': process.GetExitStatus(), 'threads': []}
    destination = Path(os.environ['PROBE_SUMMARY'])

    def save():
        destination.write_text(json.dumps(result, indent=2))

    save()
    # Include the selected/crashing thread first, then cap the complete
    # snapshot at sixteen threads and thirty public function names per thread.
    selected = process.GetSelectedThread()
    threads = [selected] if selected.IsValid() else []
    for index in range(process.GetNumThreads()):
        thread = process.GetThreadAtIndex(index)
        if selected.IsValid() and thread.GetThreadID() == selected.GetThreadID():
            continue
        if len(threads) >= 16:
            break
        threads.append(thread)
    for thread in threads:
        reason = thread.GetStopReason()
        if os.environ.get('PROBE_CORE_CAPTURE') == '1' or reason in (lldb.eStopReasonSignal, lldb.eStopReasonException):
            stopped = {'reason': reason, 'functions': []}
            if reason == lldb.eStopReasonSignal and thread.GetStopReasonDataCount():
                stopped['signal'] = thread.GetStopReasonDataAtIndex(0)
            result['threads'].append(stopped)
            result['stage'] = 'unwinding'
            save()
            for frame_index in range(30):
                frame = thread.GetFrameAtIndex(frame_index)
                if not frame.IsValid():
                    break
                stopped['functions'].append({
                    'module': safe_symbol(frame.GetModule().GetFileSpec().GetFilename()),
                    'function': safe_symbol(frame.GetFunctionName()),
                })
                save()
    result['stage'] = 'complete'
    save()
    print('TRANSPORT_DEBUGGER_STAGE=snapshot_finished', flush=True)
