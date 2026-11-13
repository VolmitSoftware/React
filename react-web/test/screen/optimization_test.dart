library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show GlobalStateKey;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/screen/optimization.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/ui/kit/mode.dart';

import 'support/operate_fakes.dart';

final List<ControlItem> _features = <ControlItem>[
  controlItem(
    'dynamic-view-distance',
    name: 'Dynamic View Distance',
    category: 'Players',
    enabled: true,
    description: 'Shrinks view distance under load.',
  ),
  const ControlItem(
    id: 'mob-stacking',
    name: 'Mob Stacking',
    category: 'Entities',
    enabled: false,
    description: 'Stacks nearby mobs.',
    knobs: <Knob>[
      Knob(key: 'radius', label: 'Radius', type: KnobType.intType, value: 4),
    ],
  ),
  controlItem('redstone-limiter', name: 'Redstone Limiter', category: 'World'),
];

Widget _view(ControlListView view) =>
    ArcaneThemeProvider(stylesheet: testSheet, child: view);

ControlListView _onScreen() =>
    find.byType(ControlListView).evaluate().single.component as ControlListView;

void main() {
  group('filterControlItems', () {
    test('matches name, id and description, ignoring case', () {
      expect(
        filterControlItems(
          _features,
          query: 'STACK',
        ).map((ControlItem i) => i.id),
        <String>['mob-stacking'],
      );
      expect(
        filterControlItems(_features, query: 'view distance').single.id,
        'dynamic-view-distance',
      );
      expect(
        filterControlItems(_features, query: 'redstone-lim').single.id,
        'redstone-limiter',
      );
    });

    test('the Governors filter picks the governor features', () {
      expect(
        filterControlItems(
          _features,
          category: governorsFilter,
        ).map((ControlItem i) => i.id),
        <String>['dynamic-view-distance'],
      );
      expect(kGovernorFeatureIds, hasLength(14));
    });

    test('a category filter keeps that category only', () {
      expect(
        filterControlItems(_features, category: 'World').single.id,
        'redstone-limiter',
      );
      expect(filterControlItems(_features), hasLength(3));
    });
  });

  group('ControlListView', () {
    testServer('one row per item with a named switch', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _view(
          ControlListView(
            items: _features,
            itemLabel: 'Feature',
            emptyMessage: 'None',
            governors: true,
            onToggle: (String _, bool _) {},
            onSetAll: (bool _, List<ControlItem> _) {},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, 200);
      expect(res.body, contains('Mob Stacking'));
      expect(res.body, contains('aria-label="Mob Stacking"'));
      expect(res.body, contains('aria-label="Redstone Limiter"'));
      expect(res.body, contains('Governors'));
      expect(res.body, contains('All categories'));
      expect(res.body, isNot(contains('Configure')));
      expect(res.body, isNot(contains('data-disabled="true"')));
    });

    testServer('Configure shows for items with settings when offered', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _view(
          ControlListView(
            items: _features,
            itemLabel: 'Feature',
            emptyMessage: 'None',
            onConfigure: (String _) {},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('aria-label="Configure Mob Stacking"'));
      expect(res.body, isNot(contains('Configure Redstone Limiter')));
      expect(res.body, isNot(contains('Governors')));
    });

    testServer('grouped, the catch-all Other group comes last', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _view(
          ControlListView(
            items: <ControlItem>[
              controlItem('a', name: 'Alpha', category: 'Other'),
              controlItem('b', name: 'Beta', category: 'World'),
              controlItem('c', name: 'Gamma', category: 'Entities'),
            ],
            itemLabel: 'Tweak',
            emptyMessage: 'None',
            grouped: true,
          ),
        ),
      );
      final String body = (await tester.request('/')).body;
      final int entities = body.indexOf('>Entities<');
      final int world = body.indexOf('>World<');
      final int other = body.indexOf('>Other<');
      expect(entities, greaterThan(0));
      expect(world, greaterThan(entities));
      expect(other, greaterThan(world));
    });

    testServer('a filter that matches nothing says so', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _view(
          ControlListView(
            items: _features,
            itemLabel: 'Feature',
            emptyMessage: 'None',
            query: 'zzz',
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Nothing matches'));
    });

    testServer('no items shows the empty message', (ServerTester tester) async {
      tester.pumpComponent(
        _view(
          const ControlListView(
            items: <ControlItem>[],
            itemLabel: 'Feature',
            emptyMessage: 'No features',
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('No features'));
    });
  });

  group('Features screen', () {
    testServer('without a client it asks for a live connection', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(null, const ControlListScreen.features()),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Features unavailable'));
      expect(res.body, contains('requires a live connection'));
    });

    testComponents('loads the features and toggles on the same server', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(features: List<ControlItem>.of(_features)),
      );
      tester.pumpComponent(
        serverScopes(server, const ControlListScreen.features()),
      );
      await tester.pump();
      expect(_onScreen().items.map((ControlItem i) => i.id), hasLength(3));
      _onScreen().onToggle!('mob-stacking', true);
      await tester.pump();
      expect(server.client.calls, contains('PUT /features/mob-stacking'));
    });

    testComponents('a viewer gets disabled switches', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(
            client: OperateClient(features: List<ControlItem>.of(_features)),
          ),
          const ControlListScreen.features(),
          role: viewerRole,
        ),
      );
      await tester.pump();
      expect(_onScreen().onToggle, isNull);
      expect(_onScreen().onSetAll, isNull);
    });

    testComponents('Configure is Advanced only', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(
            client: OperateClient(features: List<ControlItem>.of(_features)),
          ),
          const ControlListScreen.features(),
          mode: ReactorMode.simple,
        ),
      );
      await tester.pump();
      expect(_onScreen().onConfigure, isNull);
      expect(_onScreen().onToggle, isNotNull);
    });

    testComponents('Disable all asks, then turns off the shown items', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(
          features: <ControlItem>[
            controlItem('alpha', enabled: true),
            controlItem('beta', enabled: true, category: 'World'),
          ],
        ),
      );
      tester.pumpComponent(
        withConfirmHost(
          serverScopes(server, const ControlListScreen.features()),
        ),
      );
      await tester.pump();
      _onScreen().onCategory!('World');
      await tester.pump();
      final List<ControlItem> shown = filterControlItems(
        _onScreen().items,
        category: _onScreen().category,
      );
      _onScreen().onSetAll!(false, shown);
      await tester.pump();
      await answerConfirm(tester, yes: true);
      expect(server.client.calls, contains('PUT /features/beta'));
      expect(server.client.calls, isNot(contains('PUT /features/alpha')));
      await unmount(tester);
    });

    testComponents(
      'settings are staged, sent by Save, and flag server changes',
      (ComponentTester tester) async {
        final OperateClient client = OperateClient(
          features: <ControlItem>[_features[1]],
        );
        final FakeServer server = FakeServer(client: client);
        tester.pumpComponent(
          serverScopes(server, const ControlListScreen.features()),
        );
        await tester.pump();
        _onScreen().onConfigure!('mob-stacking');
        await tester.pump();
        ControlSettingsSheet sheet() =>
            find.byType(ControlSettingsSheet).evaluate().single.component
                as ControlSettingsSheet;
        expect(sheet().onSave, isNull);
        sheet().onStage!('radius', 9);
        await tester.pump();
        expect(client.writes('PUT /features/mob-stacking/config'), 0);

        client.featureList[0] = client.featureList[0].withKnobValue(
          'radius',
          6,
        );
        await server.session.features.load();
        await tester.pump();
        expect(sheet().changedOnServer!('radius'), isTrue);
        expect(_onScreen().changedOnServer!(_onScreen().items.single), isTrue);

        sheet().onSave!();
        await tester.pump();
        expect(client.writes('PUT /features/mob-stacking/config'), 1);
      },
    );

    testComponents('switching servers reloads and writes to the new server', (
      ComponentTester tester,
    ) async {
      final FakeServer first = FakeServer(
        client: OperateClient(features: <ControlItem>[controlItem('alpha')]),
      );
      final FakeServer second = FakeServer(
        client: OperateClient(features: <ControlItem>[controlItem('beta')]),
      );
      final GlobalStateKey<ServerSwitcherState> key =
          GlobalStateKey<ServerSwitcherState>();
      tester.pumpComponent(
        ServerSwitcher(
          key: key,
          servers: <FakeServer>[first, second],
          child: const ControlListScreen.features(),
        ),
      );
      await tester.pump();
      expect(_onScreen().items.single.id, 'alpha');

      key.currentState!.show(1);
      await tester.pump();
      expect(_onScreen().items.single.id, 'beta');
      expect(second.client.calls, contains('GET /features'));

      _onScreen().onToggle!('beta', true);
      await tester.pump();
      expect(second.client.calls, contains('PUT /features/beta'));
      expect(first.client.writes('PUT /features/'), 0);
    });

    testComponents('not live leaves the switches disabled', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(
            client: OperateClient(features: List<ControlItem>.of(_features)),
          ),
          const ControlListScreen.features(),
          state: ConnState.degraded,
        ),
      );
      await tester.pump();
      expect(_onScreen().onToggle, isNull);
    });
  });
}
