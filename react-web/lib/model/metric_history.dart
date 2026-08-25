class MetricHistoryDescriptor {
  final String id;
  final String name;
  final String suffix;
  final DateTime firstAt;
  final DateTime lastAt;
  final bool active;

  const MetricHistoryDescriptor({
    required this.id,
    required this.name,
    required this.suffix,
    required this.firstAt,
    required this.lastAt,
    required this.active,
  });

  factory MetricHistoryDescriptor.fromJson(Map<String, dynamic> json) =>
      MetricHistoryDescriptor(
        id: json['id'] as String,
        name: json['name'] as String,
        suffix: json['suffix'] as String,
        firstAt: DateTime.fromMillisecondsSinceEpoch(
          (json['firstTimestampMs'] as num).toInt(),
        ),
        lastAt: DateTime.fromMillisecondsSinceEpoch(
          (json['lastTimestampMs'] as num).toInt(),
        ),
        active: json['active'] as bool,
      );
}

class MetricHistoryPoint {
  final DateTime at;
  final double average;
  final double minimum;
  final double maximum;
  final double last;
  final int count;

  const MetricHistoryPoint({
    required this.at,
    required this.average,
    required this.minimum,
    required this.maximum,
    required this.last,
    required this.count,
  });

  factory MetricHistoryPoint.fromTuple(List<dynamic> tuple) =>
      MetricHistoryPoint(
        at: DateTime.fromMillisecondsSinceEpoch((tuple[0] as num).toInt()),
        average: (tuple[1] as num).toDouble(),
        minimum: (tuple[2] as num).toDouble(),
        maximum: (tuple[3] as num).toDouble(),
        last: (tuple[4] as num).toDouble(),
        count: (tuple[5] as num).toInt(),
      );
}

class MetricHistorySeries {
  final String id;
  final String name;
  final String suffix;
  final List<MetricHistoryPoint> points;

  const MetricHistorySeries({
    required this.id,
    required this.name,
    required this.suffix,
    required this.points,
  });

  factory MetricHistorySeries.fromJson(Map<String, dynamic> json) {
    final List<dynamic> rawPoints = json['points'] as List<dynamic>;
    return MetricHistorySeries(
      id: json['id'] as String,
      name: json['name'] as String,
      suffix: json['suffix'] as String,
      points: rawPoints
          .map(
            (dynamic point) =>
                MetricHistoryPoint.fromTuple(point as List<dynamic>),
          )
          .toList(),
    );
  }
}

class MetricHistoryPage {
  final DateTime requestedFrom;
  final DateTime requestedTo;
  final DateTime pageFrom;
  final DateTime pageTo;
  final Duration resolution;
  final int throughSequence;
  final DateTime throughAt;
  final String? nextCursor;
  final List<MetricHistorySeries> series;

  const MetricHistoryPage({
    required this.requestedFrom,
    required this.requestedTo,
    required this.pageFrom,
    required this.pageTo,
    required this.resolution,
    required this.throughSequence,
    required this.throughAt,
    required this.nextCursor,
    required this.series,
  });

  factory MetricHistoryPage.fromJson(Map<String, dynamic> json) {
    final List<dynamic> rawSeries = json['series'] as List<dynamic>;
    return MetricHistoryPage(
      requestedFrom: _at(json, 'requestedFromMs'),
      requestedTo: _at(json, 'requestedToMs'),
      pageFrom: _at(json, 'pageFromMs'),
      pageTo: _at(json, 'pageToMs'),
      resolution: Duration(
        milliseconds: (json['actualResolutionMs'] as num).toInt(),
      ),
      throughSequence: (json['throughSequence'] as num).toInt(),
      throughAt: _at(json, 'throughMs'),
      nextCursor: json['nextCursor'] as String?,
      series: rawSeries
          .map(
            (dynamic value) =>
                MetricHistorySeries.fromJson(value as Map<String, dynamic>),
          )
          .toList(),
    );
  }

  static DateTime _at(Map<String, dynamic> json, String key) =>
      DateTime.fromMillisecondsSinceEpoch((json[key] as num).toInt());
}
