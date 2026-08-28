import 'package:test/test.dart';

import 'package:react_web/model/plugin_api_pack.dart';

void main() {
  test('decodes pack provenance, metric health, and validation errors', () {
    final PluginApiCatalog catalog = PluginApiCatalog.fromJson(
      <String, dynamic>{
        'folder': '/plugins/React/plugin-apis',
        'packs': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 'community.adapt',
            'version': '1.0.0',
            'name': 'Adapt Metrics',
            'authors': <String>['Community'],
            'targetPlugin': 'Adapt',
            'targetVersion': '2.4.0',
            'targetVersions': <String>['2.*'],
            'enabled': true,
            'trusted': false,
            'state': 'HEALTHY',
            'detail': 'all-metrics-available',
            'fileName': 'community.adapt.toml',
            'rawContent': 'schema = "react.plugin-api/v1"',
            'metrics': <Map<String, dynamic>>[
              <String, dynamic>{
                'id': 'ops',
                'samplerId': 'plugin-api-community-adapt-ops',
                'displayName': 'Ability Operations',
                'sourceType': 'integration',
                'available': true,
                'availabilityReason': '',
                'sampledAtMs': 123,
                'sampleDurationMs': 1,
                'acceptedSamples': 5,
                'failedSamples': 0,
                'quarantined': false,
              },
            ],
          },
        ],
        'errors': <Map<String, dynamic>>[
          <String, dynamic>{'fileName': 'broken.toml', 'message': 'bad schema'},
        ],
      },
    );

    expect(catalog.folder, equals('/plugins/React/plugin-apis'));
    expect(catalog.packs.single.targetPlugin, equals('Adapt'));
    expect(catalog.packs.single.metrics.single.available, isTrue);
    expect(catalog.errors.single.fileName, equals('broken.toml'));
  });
}
