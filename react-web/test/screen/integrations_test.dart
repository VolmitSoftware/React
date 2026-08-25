library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/integrations.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(
  String id,
  double value, {
  String? name,
  String suffix = 'ms',
  double max = 100.0,
  bool available = true,
}) => SamplerSample(
  id: id,
  name: name ?? id,
  value: value,
  display: value.toString(),
  suffix: suffix,
  min: 0.0,
  max: max,
  history: <double>[value, value * 0.9, value * 1.1],
  available: available,
);

ServerSnapshot _fakeSnapshotWithIntegrations() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('adapt-ability-checks-per-tick', 14.0),
    _sample('adapt-ability-ops', 7.0),
    _sample('adapt-session-load', 0.3),
    _sample('adapt-world-policy-latency', 2.1),
    _sample('biletools-reloads', 3.0, name: 'Reloads'),
    _sample('gloss-holograms', 8.0, name: 'Holograms'),
    _sample('hiddenore-drops', 4.0, name: 'Drops'),
    _sample('iris-chunks-per-second', 8.4, name: 'Chunks Per Second'),
    _sample('iris-generation-total-ms', 12.5, name: 'Generation Total'),
    _sample('iris-pregen-queue', 12.0),
    _sample('wormholes-block-changes', 34.0),
    _sample('wormholes-packets', 220.0),
    _sample('wormholes-portals', 3.0),
    _sample('wormholes-projection-observers', 2.0),
    _sample('wormholes-projection-render-ms', 5.6),
    _sample('wormholes-projections-active', 2.0),
    _sample('wormholes-spoofed-entities', 8.0),
    _sample('wormholes-traversals', 1.0),
  ];
  final Map<String, SamplerSample> byId = <String, SamplerSample>{
    for (final SamplerSample s in samples) s.id: s,
  };
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
}

ServerSnapshot _fakeSnapshotNoIntegrations() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('ticks-per-second', 20.0, suffix: 'tps'),
  ];
  final Map<String, SamplerSample> byId = <String, SamplerSample>{
    for (final SamplerSample s in samples) s.id: s,
  };
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
}

Widget _wrapWith(Widget child, ServerSnapshot snapshot) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: ServerScope(snapshot: snapshot, state: ConnState.live, child: child),
);

void main() {
  group('IntegrationsScreen — all integrations present', () {
    test(
      'groups every available integration prefix and excludes unavailable',
      () {
        final ServerSnapshot snapshot = _fakeSnapshotWithIntegrations();
        snapshot.byId['gloss-unavailable'] = _sample(
          'gloss-unavailable',
          0.0,
          available: false,
        );

        final Map<String, List<SamplerSample>> groups =
            availableIntegrationSamples(snapshot);

        expect(
          groups.keys,
          orderedEquals(<String>[
            'Adapt',
            'BileTools',
            'Gloss',
            'HiddenOre',
            'Iris',
            'Wormholes',
          ]),
        );
        expect(
          groups['Gloss']!.any(
            (SamplerSample sample) => sample.id == 'gloss-unavailable',
          ),
          isFalse,
        );
      },
    );

    testServer('renders Adapt section when adapt-* samplers are present', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapWith(const IntegrationsScreen(), _fakeSnapshotWithIntegrations()),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Adapt'),
        isTrue,
        reason:
            'Adapt section heading must appear when adapt-* samplers are present',
      );
    });

    testServer('renders Iris section when iris-* samplers are present', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapWith(const IntegrationsScreen(), _fakeSnapshotWithIntegrations()),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Iris'),
        isTrue,
        reason:
            'Iris section heading must appear when iris-* samplers are present',
      );
    });

    testServer(
      'renders Wormholes section when wormholes-* samplers are present',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrapWith(
            const IntegrationsScreen(),
            _fakeSnapshotWithIntegrations(),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Wormholes'),
          isTrue,
          reason:
              'Wormholes section heading must appear when wormholes-* samplers are present',
        );
      },
    );

    testServer('renders BileTools, Gloss, and HiddenOre sections', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapWith(const IntegrationsScreen(), _fakeSnapshotWithIntegrations()),
      );
      final DocumentResponse res = await tester.request('/');

      expect(res.statusCode, equals(200));
      expect(res.body.contains('BileTools'), isTrue);
      expect(res.body.contains('Gloss'), isTrue);
      expect(res.body.contains('HiddenOre'), isTrue);
      expect(res.body.contains('Holograms'), isTrue);
    });

    testServer('renders at least one sampler value from the snapshot', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapWith(const IntegrationsScreen(), _fakeSnapshotWithIntegrations()),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      final bool hasValue =
          res.body.contains('14.0') ||
          res.body.contains('8.4') ||
          res.body.contains('5.6');
      expect(
        hasValue,
        isTrue,
        reason:
            'at least one integration sampler value must appear in rendered HTML',
      );
    });
  });

  group('IntegrationsScreen — no integrations present', () {
    testServer(
      'renders no-integrations-detected empty state when only non-integration samplers present',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrapWith(const IntegrationsScreen(), _fakeSnapshotNoIntegrations()),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('No integrations detected'),
          isTrue,
          reason:
              'empty-state title must appear when no integration samplers are in the snapshot',
        );
        expect(
          res.body.contains(
            'No integration metrics are being reported by this server.',
          ),
          isTrue,
          reason: 'empty-state description must match spec exactly',
        );
      },
    );

    testServer('does NOT render Adapt section when no adapt-* samplers present', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapWith(const IntegrationsScreen(), _fakeSnapshotNoIntegrations()),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      // The empty-state description contains the word "Adapt", so we check for
      // Adapt-section-specific tile content instead of the bare word.
      expect(
        res.body.contains('World Policy Latency'),
        isFalse,
        reason:
            'Adapt section tile labels must NOT appear when no adapt-* samplers are in the snapshot',
      );
      expect(
        res.body.contains('Ability Checks Per Tick'),
        isFalse,
        reason:
            'Adapt section tile labels must NOT appear when no adapt-* samplers are in the snapshot',
      );
    });
  });

  group('IntegrationsScreen route wiring', () {
    test('kRouteServerIntegrations constant has correct path', () {
      expect(kRouteServerIntegrations, equals('/server/:id/integrations'));
    });

    test('buildReactorRoutes includes server integrations route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasIntegrations = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerIntegrations,
      );
      expect(
        hasIntegrations,
        isTrue,
        reason:
            'per-server Integrations tab requires /server/:id/integrations route',
      );
    });
  });
}
