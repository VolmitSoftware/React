library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/tweaks.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/control_scope.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

final List<ControlItem> _fakeTweaks = <ControlItem>[
  const ControlItem(
    id: 'fast-fluids',
    name: 'Fast Fluids',
    category: 'World',
    enabled: true,
    description: 'Speeds up fluid simulation.',
    knobs: <Knob>[
      Knob(key: 'rate', label: 'Rate', type: KnobType.intType, value: 4),
    ],
  ),
  const ControlItem(
    id: 'fast-snow',
    name: 'Fast Snow',
    category: 'World',
    enabled: false,
    description: 'Accelerates snow accumulation.',
    knobs: <Knob>[],
  ),
];

Widget _wrapView(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

Widget _wrapScreen(Widget child) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: ControlScope(
    client: null,
    child: ServerScope(
      snapshot: ServerSnapshot(
        byId: const <String, SamplerSample>{},
        at: DateTime(2026),
        seq: 1,
      ),
      state: ConnState.live,
      child: child,
    ),
  ),
);

void main() {
  group('TweaksScreen route wiring', () {
    test('kRouteServerTweaks constant is correct', () {
      expect(kRouteServerTweaks, equals('/server/:id/tweaks'));
    });

    test('buildReactorRoutes includes server tweaks route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasTweaks = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerTweaks,
      );
      expect(
        hasTweaks,
        isTrue,
        reason: 'per-server Tweaks screen requires /server/:id/tweaks route',
      );
    });
  });

  group('TweaksScreen sidebar', () {
    testServer('ReactorShell with a server renders Tweaks sidebar item', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          ReactorShell(
            currentPath: '/server/srv1/tweaks',
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
        res.body.contains('Tweaks'),
        isTrue,
        reason: 'Tweaks sidebar item must appear in per-server sidebar group',
      );
    });
  });

  group('TweaksListView render', () {
    testServer('renders tweak names Fast Fluids and Fast Snow', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          TweaksListView(
            items: _fakeTweaks,
            onToggle: null,
            onKnobChanged: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Fast Fluids'),
        isTrue,
        reason: 'Fast Fluids tweak name must appear',
      );
      expect(
        res.body.contains('Fast Snow'),
        isTrue,
        reason: 'Fast Snow tweak name must appear',
      );
    });

    testServer('renders knob label Rate in accordion customContent', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          TweaksListView(
            items: _fakeTweaks,
            onToggle: null,
            onKnobChanged: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Rate'),
        isTrue,
        reason: 'knob label Rate must appear in accordion customContent',
      );
    });

    testServer('renders Configure (1) accordion title for tweaks with knobs', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          TweaksListView(
            items: _fakeTweaks,
            onToggle: null,
            onKnobChanged: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Configure (1)'),
        isTrue,
        reason:
            'accordion title Configure (1) must appear for tweaks with 1 knob',
      );
    });
  });

  group('TweaksScreen no-connection', () {
    testServer('renders requires a live connection note when client is null', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapScreen(const TweaksScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('requires a live connection'),
        isTrue,
        reason: 'connection note must appear when ControlScope has no client',
      );
    });
  });
}
