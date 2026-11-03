"""Snapshot only public function/module names of the stopped test process."""
import json
import os
from pathlib import Path
import lldb


def snapshot(debugger):
    process = debugger.GetSelectedTarget().GetProcess()
    result = {'state': process.GetState(), 'exit_status': process.GetExitStatus(), 'threads': []}
    for thread in process:
        if thread.GetStopReason() in (lldb.eStopReasonSignal, lldb.eStopReasonException):
            frames = []
            for index in range(30):
                frame = thread.GetFrameAtIndex(index)
                if not frame.IsValid(): break
                frames.append({'module': frame.GetModule().GetFileSpec().GetFilename(), 'function': frame.GetFunctionName()})
            result['threads'].append({
                'reason': thread.GetStopReason(),
                'functions': frames,
            })
    Path(os.environ['PROBE_SUMMARY']).write_text(json.dumps(result, indent=2))
