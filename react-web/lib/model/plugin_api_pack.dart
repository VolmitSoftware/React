class PluginApiCatalog {
  final String folder;
  final List<PluginApiPack> packs;
  final List<PluginApiValidationError> errors;

  const PluginApiCatalog({
    required this.folder,
    required this.packs,
    required this.errors,
  });

  factory PluginApiCatalog.fromJson(Map<String, dynamic> json) {
    final List<dynamic> rawPacks =
        json['packs'] as List<dynamic>? ?? <dynamic>[];
    final List<dynamic> rawErrors =
        json['errors'] as List<dynamic>? ?? <dynamic>[];
    return PluginApiCatalog(
      folder: json['folder'] as String? ?? '',
      packs: rawPacks
          .map(
            (dynamic value) =>
                PluginApiPack.fromJson(value as Map<String, dynamic>),
          )
          .toList(growable: false),
      errors: rawErrors
          .map(
            (dynamic value) => PluginApiValidationError.fromJson(
              value as Map<String, dynamic>,
            ),
          )
          .toList(growable: false),
    );
  }
}

class PluginApiPack {
  final String id;
  final String version;
  final String name;
  final List<String> authors;
  final String targetPlugin;
  final String targetVersion;
  final List<String> targetVersions;
  final bool enabled;
  final bool trusted;
  final String state;
  final String detail;
  final String fileName;
  final String rawContent;
  final List<PluginApiMetric> metrics;

  const PluginApiPack({
    required this.id,
    required this.version,
    required this.name,
    required this.authors,
    required this.targetPlugin,
    required this.targetVersion,
    required this.targetVersions,
    required this.enabled,
    required this.trusted,
    required this.state,
    required this.detail,
    required this.fileName,
    required this.rawContent,
    required this.metrics,
  });

  factory PluginApiPack.fromJson(Map<String, dynamic> json) {
    final List<dynamic> rawAuthors =
        json['authors'] as List<dynamic>? ?? <dynamic>[];
    final List<dynamic> rawVersions =
        json['targetVersions'] as List<dynamic>? ?? <dynamic>[];
    final List<dynamic> rawMetrics =
        json['metrics'] as List<dynamic>? ?? <dynamic>[];
    return PluginApiPack(
      id: json['id'] as String? ?? '',
      version: json['version'] as String? ?? '',
      name: json['name'] as String? ?? '',
      authors: rawAuthors
          .map((dynamic value) => value as String)
          .toList(growable: false),
      targetPlugin: json['targetPlugin'] as String? ?? '',
      targetVersion: json['targetVersion'] as String? ?? '',
      targetVersions: rawVersions
          .map((dynamic value) => value as String)
          .toList(growable: false),
      enabled: json['enabled'] as bool? ?? false,
      trusted: json['trusted'] as bool? ?? false,
      state: json['state'] as String? ?? 'UNKNOWN',
      detail: json['detail'] as String? ?? '',
      fileName: json['fileName'] as String? ?? '',
      rawContent: json['rawContent'] as String? ?? '',
      metrics: rawMetrics
          .map(
            (dynamic value) =>
                PluginApiMetric.fromJson(value as Map<String, dynamic>),
          )
          .toList(growable: false),
    );
  }
}

class PluginApiMetric {
  final String id;
  final String samplerId;
  final String displayName;
  final String sourceType;
  final bool available;
  final String availabilityReason;
  final int sampledAtMs;
  final int sampleDurationMs;
  final int acceptedSamples;
  final int failedSamples;
  final bool quarantined;

  const PluginApiMetric({
    required this.id,
    required this.samplerId,
    required this.displayName,
    required this.sourceType,
    required this.available,
    required this.availabilityReason,
    required this.sampledAtMs,
    required this.sampleDurationMs,
    required this.acceptedSamples,
    required this.failedSamples,
    required this.quarantined,
  });

  factory PluginApiMetric.fromJson(Map<String, dynamic> json) =>
      PluginApiMetric(
        id: json['id'] as String? ?? '',
        samplerId: json['samplerId'] as String? ?? '',
        displayName: json['displayName'] as String? ?? '',
        sourceType: json['sourceType'] as String? ?? '',
        available: json['available'] as bool? ?? false,
        availabilityReason: json['availabilityReason'] as String? ?? '',
        sampledAtMs: (json['sampledAtMs'] as num?)?.toInt() ?? 0,
        sampleDurationMs: (json['sampleDurationMs'] as num?)?.toInt() ?? 0,
        acceptedSamples: (json['acceptedSamples'] as num?)?.toInt() ?? 0,
        failedSamples: (json['failedSamples'] as num?)?.toInt() ?? 0,
        quarantined: json['quarantined'] as bool? ?? false,
      );
}

class PluginApiValidationError {
  final String fileName;
  final String message;

  const PluginApiValidationError({
    required this.fileName,
    required this.message,
  });

  factory PluginApiValidationError.fromJson(Map<String, dynamic> json) =>
      PluginApiValidationError(
        fileName: json['fileName'] as String? ?? '',
        message: json['message'] as String? ?? '',
      );
}

class PluginApiValidationResult {
  final bool valid;
  final String id;
  final int metricCount;
  final String message;

  const PluginApiValidationResult({
    required this.valid,
    required this.id,
    required this.metricCount,
    required this.message,
  });

  factory PluginApiValidationResult.fromJson(Map<String, dynamic> json) =>
      PluginApiValidationResult(
        valid: json['valid'] as bool? ?? false,
        id: json['id'] as String? ?? '',
        metricCount: (json['metricCount'] as num?)?.toInt() ?? 0,
        message: json['message'] as String? ?? '',
      );
}
