import 'sampler_sample.dart';

/// One loaded plugin or mod from `GET /owners`. Its cost is the live value
/// of the sampler [samplerId] names; [includedIn] is set when that sampler
/// belongs to another owner (a mod nested in or bundled with it).
class OwnerInfo {
  const OwnerInfo({
    required this.id,
    required this.name,
    required this.version,
    required this.enabled,
    this.samplerId,
    this.includedIn,
  });

  final String id;
  final String name;
  final String version;
  final bool enabled;
  final String? samplerId;
  final String? includedIn;

  factory OwnerInfo.fromJson(Map<String, dynamic> json) {
    final String id = json['id'] as String? ?? '';
    final String name = json['name'] as String? ?? '';
    return OwnerInfo(
      id: id,
      name: name.isEmpty ? id : name,
      version: json['version'] as String? ?? '',
      enabled: json['enabled'] as bool? ?? true,
      samplerId: json['samplerId'] as String?,
      includedIn: json['includedIn'] as String?,
    );
  }
}

/// An owner with its live cost: [msPerSecond] is null when the server has no
/// cost sampler for it (React itself, a nested mod), and [share] is its part
/// of the cost of every listed owner.
class OwnerCost {
  const OwnerCost(this.owner, this.msPerSecond, this.share);

  final OwnerInfo owner;
  final double? msPerSecond;
  final double share;
}

/// Joins [owners] with the cost samplers in [samples] (by id), costliest
/// first, then by name. A sampler shared by several owners counts once, on
/// the owner it belongs to.
List<OwnerCost> ownerCosts(
  List<OwnerInfo> owners,
  Map<String, SamplerSample> samples,
) {
  final List<(OwnerInfo, double?)> joined = <(OwnerInfo, double?)>[
    for (final OwnerInfo owner in owners)
      (owner, owner.includedIn == null ? _cost(owner, samples) : null),
  ];
  double total = 0;
  for (final (OwnerInfo _, double? cost) in joined) {
    total += cost ?? 0;
  }
  final List<OwnerCost> costs = <OwnerCost>[
    for (final (OwnerInfo owner, double? cost) in joined)
      OwnerCost(owner, cost, total > 0 && cost != null ? cost / total : 0),
  ];
  costs.sort((OwnerCost left, OwnerCost right) {
    final int byCost = (right.msPerSecond ?? -1).compareTo(
      left.msPerSecond ?? -1,
    );
    return byCost != 0
        ? byCost
        : left.owner.name.toLowerCase().compareTo(
            right.owner.name.toLowerCase(),
          );
  });
  return costs;
}

double? _cost(OwnerInfo owner, Map<String, SamplerSample> samples) {
  final String? id = owner.samplerId;
  final SamplerSample? sample = id == null ? null : samples[id];
  if (sample == null || !sample.available || !sample.value.isFinite) {
    return null;
  }
  return sample.value < 0 ? 0 : sample.value;
}
