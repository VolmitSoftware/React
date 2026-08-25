import 'sampler_sample.dart';

class ServerSnapshot {
  final Map<String, SamplerSample> byId;
  final DateTime at;
  final int seq;

  const ServerSnapshot({required this.byId, required this.at, this.seq = 0});

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
    );
  }

  SamplerSample? sampler(String id) => byId[id];
}
