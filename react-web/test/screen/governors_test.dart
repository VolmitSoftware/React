library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/governors.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/control_scope.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(String id, double value) => SamplerSample(
  id: id,
  name: id,
  value: value,
  display: value.toStringAsFixed(0),
  suffix: '',
  min: 0.0,
  max: 100.0,
  history: <double>[value],
);

ServerSnapshot _fakeSnapshot() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('incident-score', 55.0),
    _sample('scheduler-backlog', 3.0),
    _sample('backlog-growth-rate', 0.5),
  ];
  return ServerSnapshot(
    byId: <String, SamplerSample>{
      for (final SamplerSample s in samples) s.id: s,
    },
    at: DateTime.now(),
    seq: 1,
  );
}

final List<ControlItem> _fakeGovernors = <ControlItem>[
  const ControlItem(
    id: 'incident-mode',
    name: 'Incident Mode',
    category: 'Governor',
    enabled: true,
    description: '',
  ),
  const ControlItem(
    id: 'pathfinder-budget',
    name: 'Pathfinder Budget',
    category: 'Governor',
    enabled: false,
    description: '',
  ),
];

Widget _wrapView(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

Widget _wrapScreen({required Widget child, ServerSnapshot? snapshot}) =>
    ArcaneThemeProvider(
      stylesheet: _sheet,
      child: ControlScope(
        client: null,
        child: ServerScope(
          snapshot: snapshot,
          state: ConnState.live,
          child: child,
        ),
      ),
    );

void main() {
  group('GovernorsScreen route', () {
    test('kRouteServerGovernors constant is correct', () {
      expect(kRouteServerGovernors, equals('/server/:id/governors'));
    });

    test('buildReactorRoutes includes governors route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasGovernors = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerGovernors,
      );
      expect(
        hasGovernors,
        isTrue,
        reason:
            'per-server Governors screen requires /server/:id/governors route',
      );
    });
  });

  group('GovernorsScreen sidebar', () {
    testServer('ReactorShell with a server renders Governors sidebar item', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          ReactorShell(
            servers: const <ServerEntry>[
              ServerEntry(
                id: 'srv1',
                name: 'Test Server',
                state: ConnState.live,
              ),
            ],
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Governors'),
        isTrue,
        reason:
            'Governors sidebar item must appear in per-server sidebar group',
      );
    });
  });

  group('GovernorDashboardView render', () {
    testServer(
      'renders governor names, enabled/disabled badges, gauge value, and heading',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrapView(
            GovernorDashboardView(
              governors: _fakeGovernors,
              incidentScore: _sample('incident-score', 55.0),
              schedulerBacklog: _sample('scheduler-backlog', 3.0),
              backlogGrowthRate: null,
              onToggle: null,
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Incident Mode'),
          isTrue,
          reason: 'governor name Incident Mode must appear in rendered HTML',
        );
        expect(
          res.body.contains('Pathfinder Budget'),
          isTrue,
          reason:
              'governor name Pathfinder Budget must appear in rendered HTML',
        );
        expect(
          res.body.contains('Enabled'),
          isTrue,
          reason: 'Enabled badge must appear for incident-mode governor',
        );
        expect(
          res.body.contains('Disabled'),
          isTrue,
          reason: 'Disabled badge must appear for pathfinder-budget governor',
        );
        expect(
          res.body.contains('55'),
          isTrue,
          reason: 'incident score gauge value 55 must appear in rendered HTML',
        );
        expect(
          res.body.contains('Governors'),
          isTrue,
          reason: 'Governors section heading must appear in rendered HTML',
        );
      },
    );
  });

  group('GovernorsScreen no-connection', () {
    testServer(
      'renders requires a live connection note and still shows incident gauge',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrapScreen(
            snapshot: _fakeSnapshot(),
            child: const GovernorsScreen(),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('requires a live connection'),
          isTrue,
          reason:
              'connection note must appear when ControlScope client is null',
        );
        expect(
          res.body.contains('55'),
          isTrue,
          reason: 'incident score gauge must still render when client is null',
        );
      },
    );
  });
}
