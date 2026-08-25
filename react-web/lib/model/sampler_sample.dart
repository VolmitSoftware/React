class SamplerSample {
  final String id;
  final String name;
  final String suffix;
  final double value;
  final String display;
  final double min;
  final double max;
  final List<double> history;
  final bool available;

  const SamplerSample({
    required this.id,
    required this.name,
    required this.suffix,
    required this.value,
    required this.display,
    required this.min,
    required this.max,
    required this.history,
    this.available = true,
  });

  factory SamplerSample.fromJson(Map<String, dynamic> json) {
    final double value = (json['value'] as num).toDouble();
    return SamplerSample(
      id: json['id'] as String,
      name: json['name'] as String,
      suffix: json['suffix'] as String,
      value: value,
      display: (json['display'] as String?) ?? value.toString(),
      min: value,
      max: value,
      history: const <double>[],
      available: (json['available'] as bool?) ?? true,
    );
  }

  SamplerSample withLiveHistory(List<double> values) {
    double minimum = value;
    double maximum = value;
    for (final double sample in values) {
      if (sample < minimum) minimum = sample;
      if (sample > maximum) maximum = sample;
    }
    return SamplerSample(
      id: id,
      name: name,
      suffix: suffix,
      value: value,
      display: display,
      min: minimum,
      max: maximum,
      history: values,
      available: available,
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
    'available': available,
  };
}
