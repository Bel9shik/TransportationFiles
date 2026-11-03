import 'dart:developer';
import 'dart:io';

import 'package:path/path.dart' as p;

import 'benchmark.dart' as benchmark;

// Diagnostic wrapper only. Sampling changes timings; these are not throughput
// results. The ordinary benchmark and all transfer backends stay unchanged.
Future<void> main(List<String> args) async {
  final directory = Platform.environment['TRANSPORT_CPU_PROFILE_DIR'];
  if (args.isEmpty || args.first != 'send' || directory == null) {
    return benchmark.main(args);
  }
  final files = args.skip(3).map(File.new).toList();
  final bytes = files.fold<int>(0, (sum, file) => sum + file.lengthSync());
  final scenario = files.length == 1 && bytes == 100 * 1024 * 1024
      ? '100MiB'
      : files.length == 20 && bytes == 20 * 1024 * 1024
      ? '20x1MiB'
      : 'other';
  Directory(directory).createSync(recursive: true);
  final trace = p.join(directory, 'sender-$scenario-$pid.pftrace');
  NativeRuntime.streamTimelineTo(
    TimelineRecorder.perfetto,
    path: trace,
    enableProfiler: true,
    samplingInterval: const Duration(microseconds: 1000),
  );
  try {
    await benchmark.main(args);
  } finally {
    NativeRuntime.stopStreamingTimeline();
  }
}
