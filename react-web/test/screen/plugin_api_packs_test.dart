library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show GlobalStateKey;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/plugin_api_pack.dart';
import 'package:react_web/screen/plugin_api_packs.dart';
import 'package:react_web/ui/kit/mode.dart';

import 'support/operate_fakes.dart';

PluginApiCatalogView _catalog() =>
    find.byType(PluginApiCatalogView).evaluate().single.component
        as PluginApiCatalogView;

PluginApiEditorSheet _editor() =>
    find.byType(PluginApiEditorSheet).evaluate().single.component
        as PluginApiEditorSheet;

void main() {
  test('the folder is shown from the server folder, not the host', () {
    expect(
      serverFolder('/Users/alex/servers/paper/plugins/React/plugin-apis'),
      'plugins/React/plugin-apis',
    );
    expect(
      serverFolder(r'C:\srv\mc\plugins\React\plugin-apis'),
      'plugins/React/plugin-apis',
    );
    expect(serverFolder('plugin-apis'), 'plugin-apis');
  });

  group('PluginApiCatalogView', () {
    testServer('one row per pack with its state', (ServerTester tester) async {
      final PluginApiCatalog base = packCatalog('Earth Mover');
      tester.pumpComponent(
        ArcaneThemeProvider(
          stylesheet: testSheet,
          child: PluginApiCatalogView(
            catalog: PluginApiCatalog(
              folder: base.folder,
              packs: base.packs,
              errors: const <PluginApiValidationError>[
                PluginApiValidationError(
                  fileName: 'broken.toml',
                  message: 'Unknown key root.legacyMode',
                ),
              ],
            ),
            onEdit: (PluginApiPack _) {},
            onDelete: (PluginApiPack _) {},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Earth Mover'));
      expect(res.body, contains('Healthy'));
      expect(res.body, contains('1/1'));
      // A file that does not load is the page's alert, not a box in here.
      expect(res.body, isNot(contains('broken.toml')));
      // The folder may break after any slash, so a long path never widens
      // the page on a phone.
      expect(res.body, contains('plugins/<wbr'));
      // From the server's folder on: the host's directories stay private.
      final String folder = res.body.replaceAll(RegExp(r'<wbr\s*/?>'), '');
      expect(folder, contains('<code>plugins/React/plugin-apis</code>'));
      // Edit is the row's button; Delete waits in its "..." menu.
      expect(res.body, contains('aria-label="Edit Earth Mover"'));
      expect(res.body, isNot(contains('aria-label="Delete Earth Mover"')));
    });

    testServer('packs state names read in sentence case', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        ArcaneThemeProvider(
          stylesheet: testSheet,
          child: packStateBadge('TARGET_MISSING'),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Target missing'));
    });
  });

  group('PluginApiPacksScreen', () {
    testComponents('a viewer sees the packs but cannot change them', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(),
          const PluginApiPacksScreen(),
          role: viewerRole,
        ),
      );
      await tester.pump();
      expect(_catalog().catalog.packs.single.name, 'Pack');
      expect(_catalog().onNew, isNull);
      expect(_catalog().onEdit, isNull);
      expect(_catalog().onDelete, isNull);
    });

    testServer('a metric\'s sampler id stays on one line, whole in its title', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        ArcaneThemeProvider(
          stylesheet: testSheet,
          child: PluginApiMetricsList(packs: packCatalog('Earth Mover').packs),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(
        body,
        contains(
          '<span class="reactor-one-line" title="plugin-api-example-ops">'
          'plugin-api-example-ops</span>',
        ),
      );
    });

    testComponents('the metric list is Advanced only', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(),
          const PluginApiPacksScreen(),
          mode: ReactorMode.simple,
        ),
      );
      await tester.pump();
      expect(find.byType(PluginApiMetricsList), findsNothing);
    });

    testComponents('a deleted pack is not removed without a yes', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer();
      tester.pumpComponent(serverScopes(server, const PluginApiPacksScreen()));
      await tester.pump();
      _catalog().onDelete!(_catalog().catalog.packs.single);
      await tester.pump();
      expect(server.client.writes('DELETE /plugin-api-packs/'), 0);
    });

    testComponents('a pack is removed after a yes', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer();
      tester.pumpComponent(
        withConfirmHost(serverScopes(server, const PluginApiPacksScreen())),
      );
      await tester.pump();
      _catalog().onDelete!(_catalog().catalog.packs.single);
      await tester.pump();
      await answerConfirm(tester, yes: true);
      expect(
        server.client.calls,
        contains('DELETE /plugin-api-packs/example.Pack'),
      );
      await unmount(tester);
    });

    testComponents('switching servers reloads and installs on the new one', (
      ComponentTester tester,
    ) async {
      final FakeServer first = FakeServer(
        client: OperateClient(packs: packCatalog('Alpha')),
      );
      final FakeServer second = FakeServer(
        client: OperateClient(packs: packCatalog('Beta')),
      );
      final GlobalStateKey<ServerSwitcherState> key =
          GlobalStateKey<ServerSwitcherState>();
      tester.pumpComponent(
        ServerSwitcher(
          key: key,
          servers: <FakeServer>[first, second],
          child: const PluginApiPacksScreen(),
        ),
      );
      await tester.pump();
      expect(_catalog().catalog.packs.single.name, 'Alpha');
      _catalog().onNew!();
      await tester.pump();
      expect(_editor().draft, isNotNull);

      key.currentState!.show(1);
      await tester.pump();
      expect(_catalog().catalog.packs.single.name, 'Beta');
      expect(_editor().draft, isNull);

      _catalog().onEdit!(_catalog().catalog.packs.single);
      await tester.pump();
      _editor().onInstall!();
      await tester.pump();
      await tester.pump();
      expect(second.client.calls, contains('PUT /plugin-api-packs/pack'));
      expect(
        first.client.calls.where(
          (String call) => call != 'GET /plugin-api-packs',
        ),
        isEmpty,
      );
    });
  });
}
