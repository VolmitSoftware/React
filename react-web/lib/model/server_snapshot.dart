import 'sampler_sample.dart';
import 'server_revisions.dart';

class ServerSnapshot {
  final Map<String, SamplerSample> byId;
  final DateTime at;
  final int seq;
  final ServerRevisions? revisions;

  /// When each of the recent snapshots was captured (epoch ms, oldest
  /// first): the time axis of the samplers' live histories, which end with
  /// this snapshot.
  final List<double> times;

  const ServerSnapshot({
    required this.byId,
    required this.at,
    this.seq = 0,
    this.revisions,
    this.times = const <double>[],
  });

  factory ServerSnapshot.fromJson(Map<String, dynamic> json) {
    final Map<String, dynamic> data = json['data'] as Map<String, dynamic>;
    final List<dynamic> samplers = data['samplers'] as List<dynamic>;
    final Map<String, SamplerSample> byId = <String, SamplerSample>{};
    for (final dynamic entry in samplers) {
      final SamplerSample sample = SamplerSample.fromJson(
        entry as Map<String, dynamic>,
      );
      byId[sample.id] = sample;
    }
    return ServerSnapshot(
      byId: byId,
      at: DateTime.fromMillisecondsSinceEpoch(
        (data['capturedAtMs'] as num).toInt(),
      ),
      seq: (data['sequence'] as num).toInt(),
      revisions: ServerRevisions.fromEnvelope(json),
    );
  }

  SamplerSample? sampler(String id) {
    final SamplerSample? sample = byId[id];
    return sample != null && sample.available ? sample : null;
  }
}
