import 'sampler_sample.dart';

class ServerSnapshot {
  final Map<String, SamplerSample> byId;
  final DateTime at;
  final int seq;

  const ServerSnapshot({required this.byId, required this.at, this.seq = 0});

  factory ServerSnapshot.fromJson(Map<String, dynamic> json, {int seq = 0}) {
    final List<dynamic> data = json['data'] as List<dynamic>;
    final Map<String, SamplerSample> byId = <String, SamplerSample>{};
    for (final dynamic entry in data) {
      final SamplerSample sample =
          SamplerSample.fromJson(entry as Map<String, dynamic>);
      byId[sample.id] = sample;
    }
    return ServerSnapshot(byId: byId, at: DateTime.now(), seq: seq);
  }

  SamplerSample? sampler(String id) => byId[id];
}
