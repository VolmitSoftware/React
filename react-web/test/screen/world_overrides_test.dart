library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show GlobalStateKey;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/world_settings.dart';
import 'package:react_web/screen/world_overrides.dart';
import 'package:react_web/ui/kit/mode.dart';

import 'support/operate_fakes.dart';

final List<WorldSettings> _worlds = <WorldSettings>[
  worldSettings('world', budget: 50),
  const WorldSettings(
    key: 'minecraft:world_nether',
    name: 'world_nether',
    pressureMode: PressureMode.panic,
    budgetMs: 40,
    panicMs: 45.5,
    releaseMs: 35,
  ),
];

WorldOverridesView _view() =>
    find.byType(WorldOverridesView).evaluate().single.component
        as WorldOverridesView;

WorldBudgetSheet _sheet() =>
    find.byType(WorldBudgetSheet).evaluate().single.component
        as WorldBudgetSheet;

void main() {
  group('WorldOverridesView', () {
    testServer('one row per world with pressure and times', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        ArcaneThemeProvider(
          stylesheet: testSheet,
          child: WorldOverridesView(worlds: _worlds),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('world_nether'));
      expect(res.body, contains('Panic'));
      expect(res.body, contains('Budget (ms)'));
      expect(res.body, contains('45.5'));
      expect(res.body, isNot(contains('Edit world')));
    });

    testServer('Edit shows per world when offered', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        ArcaneThemeProvider(
          stylesheet: testSheet,
          child: WorldOverridesView(worlds: _worlds, onEdit: (String _) {}),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('aria-label="Edit world_nether"'));
    });
  });

  group('WorldOverridesScreen', () {
    testServer('without a client it asks for a live connection', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(serverScopes(null, const WorldOverridesScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('World overrides unavailable'));
    });

    testComponents('editing is Advanced only', (ComponentTester tester) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(
            client: OperateClient(worlds: List<WorldSettings>.of(_worlds)),
          ),
          const WorldOverridesScreen(),
          mode: ReactorMode.simple,
        ),
      );
      await tester.pump();
      expect(_view().worlds, hasLength(2));
      expect(_view().onEdit, isNull);
    });

    testComponents('a viewer may open a world but not change it', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(
            client: OperateClient(worlds: List<WorldSettings>.of(_worlds)),
          ),
          const WorldOverridesScreen(),
          role: viewerRole,
        ),
      );
      await tester.pump();
      _view().onEdit!('world');
      await tester.pump();
      expect(_sheet().world?.name, 'world');
      expect(_sheet().onStage, isNull);
      expect(_sheet().onSave, isNull);
    });

    testComponents('switching servers reloads and writes to the new server', (
      ComponentTester tester,
    ) async {
      final FakeServer first = FakeServer(
        client: OperateClient(worlds: <WorldSettings>[worldSettings('alpha')]),
      );
      final FakeServer second = FakeServer(
        client: OperateClient(worlds: <WorldSettings>[worldSettings('beta')]),
      );
      final GlobalStateKey<ServerSwitcherState> key =
          GlobalStateKey<ServerSwitcherState>();
      tester.pumpComponent(
        ServerSwitcher(
          key: key,
          servers: <FakeServer>[first, second],
          child: const WorldOverridesScreen(),
        ),
      );
      await tester.pump();
      expect(_view().worlds.single.name, 'alpha');

      key.currentState!.show(1);
      await tester.pump();
      expect(_view().worlds.single.name, 'beta');

      _view().onEdit!('beta');
      await tester.pump();
      expect(_sheet().onSave, isNull);
      _sheet().onStage!('beta', WorldBudget.budget, 30);
      await tester.pump();
      expect(second.client.writes('PUT /worlds/'), 0);
      _sheet().onSave!();
      await tester.pump();
      expect(second.client.calls, contains('PUT /worlds/beta'));
      expect(second.client.worldList.single.budgetMs, 30);
      expect(first.client.writes('PUT /worlds/'), 0);
    });
  });
}
