import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:react_web/model/plugin_api_pack.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/service/react_client.dart';

void main() {
  const ServerCredential credential = ServerCredential(
    id: 'test',
    label: 'Test',
    host: 'localhost',
    port: 9696,
    bearer: 'token',
  );

  test('lists, validates, installs, and removes Plugin API packs', () async {
    final List<String> methods = <String>[];
    final MockClient transport = MockClient((http.Request request) async {
      methods.add('${request.method} ${request.url.path}');
      expect(request.headers['authorization'], equals('Bearer token'));
      if (request.method != 'GET') {
        expect(
          int.tryParse(request.headers['x-react-counter'] ?? ''),
          isNotNull,
        );
      }
      if (request.url.path.endsWith('/validate')) {
        expect(
          jsonDecode(request.body),
          equals(<String, dynamic>{'content': 'pack'}),
        );
        return http.Response(
          jsonEncode(<String, dynamic>{
            'data': <String, dynamic>{
              'valid': true,
              'id': 'community.example',
              'metricCount': 1,
              'message': 'valid',
            },
          }),
          200,
        );
      }
      if (request.method == 'PUT') {
        return http.Response(
          jsonEncode(<String, dynamic>{'data': _pack()}),
          200,
        );
      }
      return http.Response(
        jsonEncode(<String, dynamic>{
          'data': <String, dynamic>{
            'folder': '/plugins/React/plugin-apis',
            'packs': request.method == 'DELETE'
                ? <dynamic>[]
                : <dynamic>[_pack()],
            'errors': <dynamic>[],
          },
        }),
        200,
      );
    });
    final ReactClient client = ReactClient(credential, client: transport);

    final PluginApiCatalog initial = await client.pluginApiPacks();
    final PluginApiValidationResult validation = await client
        .validatePluginApiPack('pack');
    final PluginApiPack installed = await client.installPluginApiPack(
      'community.example',
      'pack',
    );
    final PluginApiCatalog removed = await client.removePluginApiPack(
      'community.example',
    );

    expect(initial.packs.single.id, equals('community.example'));
    expect(validation.valid, isTrue);
    expect(installed.targetPlugin, equals('Example'));
    expect(removed.packs, isEmpty);
    expect(
      methods,
      equals(<String>[
        'GET /api/v1/plugin-api-packs',
        'POST /api/v1/plugin-api-packs/validate',
        'PUT /api/v1/plugin-api-packs/community.example',
        'DELETE /api/v1/plugin-api-packs/community.example',
      ]),
    );
  });
}

Map<String, dynamic> _pack() => <String, dynamic>{
  'id': 'community.example',
  'version': '1.0.0',
  'name': 'Example',
  'authors': <String>['Tests'],
  'targetPlugin': 'Example',
  'targetVersion': '1.0.0',
  'targetVersions': <String>['*'],
  'enabled': true,
  'trusted': false,
  'state': 'HEALTHY',
  'detail': 'all-metrics-available',
  'fileName': 'community.example.toml',
  'rawContent': 'pack',
  'metrics': <dynamic>[],
};
