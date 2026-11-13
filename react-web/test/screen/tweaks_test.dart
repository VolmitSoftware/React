library;

import 'package:jaspr/jaspr.dart' show GlobalStateKey;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/control_item.dart';
import 'package:react_web/screen/optimization.dart';

import 'support/operate_fakes.dart';

ControlListView _onScreen() =>
    find.byType(ControlListView).evaluate().single.component as ControlListView;

void main() {
  group('Tweaks screen', () {
    testServer('without a client it asks for a live connection', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(null, const ControlListScreen.tweaks()),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Tweaks unavailable'));
      expect(res.body, contains('requires a live connection'));
    });

    testComponents('lists the tweaks without the Governors filter', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(
          tweaks: <ControlItem>[
            controlItem('fast-snow', name: 'Fast Snow', category: 'World'),
          ],
        ),
      );
      tester.pumpComponent(
        serverScopes(server, const ControlListScreen.tweaks()),
      );
      await tester.pump();
      expect(server.client.calls, contains('GET /tweaks'));
      expect(server.client.calls, isNot(contains('GET /features')));
      expect(_onScreen().governors, isFalse);
      expect(_onScreen().itemLabel, 'Tweak');
      _onScreen().onToggle!('fast-snow', true);
      await tester.pump();
      expect(server.client.calls, contains('PUT /tweaks/fast-snow'));
    });

    testComponents('an empty list says there are no tweaks', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(FakeServer(), const ControlListScreen.tweaks()),
      );
      await tester.pump();
      expect(_onScreen().emptyMessage, 'No tweaks available');
    });

    testComponents('switching servers reloads and writes to the new server', (
      ComponentTester tester,
    ) async {
      final FakeServer first = FakeServer(
        client: OperateClient(tweaks: <ControlItem>[controlItem('fast-snow')]),
      );
      final FakeServer second = FakeServer(
        client: OperateClient(
          tweaks: <ControlItem>[controlItem('fast-fluids')],
        ),
      );
      final GlobalStateKey<ServerSwitcherState> key =
          GlobalStateKey<ServerSwitcherState>();
      tester.pumpComponent(
        ServerSwitcher(
          key: key,
          servers: <FakeServer>[first, second],
          child: const ControlListScreen.tweaks(),
        ),
      );
      await tester.pump();
      expect(_onScreen().items.single.id, 'fast-snow');

      key.currentState!.show(1);
      await tester.pump();
      expect(_onScreen().items.single.id, 'fast-fluids');

      _onScreen().onToggle!('fast-fluids', true);
      await tester.pump();
      expect(second.client.calls, contains('PUT /tweaks/fast-fluids'));
      expect(first.client.writes('PUT /tweaks/'), 0);
    });
  });
}
