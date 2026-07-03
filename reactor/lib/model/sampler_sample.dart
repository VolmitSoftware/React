class SamplerSample {
  final String id;
  final String name;
  final String suffix;
  final double value;
  final String display;
  final double min;
  final double max;
  final List<double> history;

  const SamplerSample({
    required this.id,
    required this.name,
    required this.suffix,
    required this.value,
    required this.display,
    required this.min,
    required this.max,
    required this.history,
  });

  factory SamplerSample.fromJson(Map<String, dynamic> json) {
    final List<dynamic> rawHistory = json['history'] as List<dynamic>;
    final double value = (json['value'] as num).toDouble();
    return SamplerSample(
      id: json['id'] as String,
      name: json['name'] as String,
      suffix: json['suffix'] as String,
      value: value,
      display: (json['display'] as String?) ?? value.toString(),
      min: (json['min'] as num).toDouble(),
      max: (json['max'] as num).toDouble(),
      history: rawHistory.map((dynamic e) => (e as num).toDouble()).toList(),
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'name': name,
        'suffix': suffix,
        'value': value,
        'display': display,
        'min': min,
        'max': max,
        'history': history,
      };
}
