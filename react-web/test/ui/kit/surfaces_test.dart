library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/role_scope.dart';
import 'package:react_web/state/server_scope.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/ui/kit/action_row.dart';
import 'package:react_web/ui/kit/alert.dart';
import 'package:react_web/ui/kit/card.dart';
import 'package:react_web/ui/kit/data_list.dart';
import 'package:react_web/ui/kit/badge.dart';
import 'package:react_web/ui/kit/metric_tile.dart';
import 'package:react_web/ui/kit/page.dart';
import 'package:react_web/ui/kit/section.dart';
import 'package:react_web/ui/kit/states.dart';

Future<String> _render(ServerTester tester, Widget child) async {
  tester.pumpComponent(
    ArcaneThemeProvider(stylesheet: const ReactorStylesheet(), child: child),
  );
  final DocumentResponse response = await tester.request('/');
  expect(response.statusCode, 200);
  return response.body;
}

int _count(String body, String needle) => needle.allMatches(body).length;

typedef _Row = ({String name, int players});

DataList<_Row> _list() => DataList<_Row>(
  label: 'Servers',
  items: const <_Row>[(name: 'Skyblock', players: 40)],
  href: (_Row row) => '/server/${row.name}',
  columns: <ListColumn<_Row>>[
    ListColumn<_Row>('Server', (_Row row) => Text(row.name)),
    ListColumn<_Row>(
      'Players',
      (_Row row) => Text('${row.players}'),
      numeric: true,
    ),
  ],
);

void main() {
  group('card and list shape', () {
    testServer('a card that is only a heading draws no empty body', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        const AppCard(title: 'Presets', children: <Widget>[]),
      );
      expect(body, contains('ui-card-head'));
      expect(body, isNot(contains('ui-card-body')));
    });

    testServer('row buttons share one column track with the header', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        DataList<_Row>(
          label: 'Servers',
          items: const <_Row>[(name: 'Skyblock', players: 40)],
          columns: <ListColumn<_Row>>[
            ListColumn<_Row>('Server', (_Row row) => Text(row.name)),
          ],
          actions: (_Row row) => <RowAction>[RowAction('Remove', () {})],
        ),
      );
      expect(body, contains('--ui-list-cols: 1fr auto'));
      expect(_count(body, 'role="columnheader"'), 2);
    });
  });

  group('one surface level', () {
    testServer('an alert is a box on the page and a line in a card', (
      ServerTester tester,
    ) async {
      final String page = await _render(
        tester,
        const AppAlert(message: 'Down', tone: AlertTone.error, live: true),
      );
      expect(page, contains('ui-alert'));
      expect(page, contains('role="alert"'));
      expect(page, contains('data-alert="error"'));
      final String card = await _render(
        tester,
        const AppCard(
          title: 'Status',
          children: <Widget>[AppAlert(message: 'Down', tone: AlertTone.error)],
        ),
      );
      expect(card, contains('ui-note'));
      expect(card, isNot(contains('ui-alert')));
    });

    testServer('a metric tile is a stat card on the page, flat in a card', (
      ServerTester tester,
    ) async {
      final String page = await _render(
        tester,
        const MetricGrid(
          children: <Widget>[
            MetricTile(label: 'TPS', value: '19.9', history: <double>[1, 2]),
          ],
        ),
      );
      expect(page, contains('arcane-stat-card'));
      expect(page, contains('reactor-sparkline'));
      final String card = await _render(
        tester,
        const AppCard(
          children: <Widget>[
            MetricTile(label: 'Memory', value: '4096', unit: 'MB', meter: 0.5),
          ],
        ),
      );
      expect(card, isNot(contains('arcane-stat-card')));
      expect(card, contains('ui-stat'));
      expect(card, contains('4096 MB'));
      expect(card, contains('role="meter"'));
      expect(card, contains('aria-valuenow="50"'));
    });

    testServer('a tile says its state as a badge and never repeats its unit', (
      ServerTester tester,
    ) async {
      final String page = await _render(
        tester,
        const MetricGrid(
          children: <Widget>[
            MetricTile(
              label: 'TPS',
              value: '9.3',
              unit: 'TPS',
              caption: 'Critical',
              tone: BadgeTone.error,
              history: <double>[9.3, 9.3],
            ),
            MetricTile(label: 'Tick time', value: '61.6', unit: 'ms'),
          ],
        ),
      );
      expect(page, isNot(contains('>TPS</span><span')));
      expect(_count(page, '>TPS<'), 1, reason: 'the label only');
      expect(page, contains('ui-badge'));
      expect(page, contains('>Critical<'));
      expect(page, contains('>ms<'));
      expect(
        page,
        isNot(contains('reactor-sparkline')),
        reason: 'flat history',
      );
    });

    test('a data list stacks where its columns would not fit', () {
      DataList<String> list(int columns, {bool actions = false}) =>
          DataList<String>(
            label: 'Rows',
            items: const <String>[],
            columns: <ListColumn<String>>[
              for (int index = 0; index < columns; index++)
                ListColumn<String>.text('C$index', (String row) => row),
            ],
            actions: actions ? (String _) => const <RowAction>[] : null,
          );
      expect(list(2).stackBelow, lessThan(list(6).stackBelow));
      expect(list(8, actions: true).stackBelow, kListStackWidths.last);
      expect(kListStackWidths, contains(list(3).stackBelow));
    });

    testServer('a data list is one card on the page with stacking labels', (
      ServerTester tester,
    ) async {
      final String page = await _render(tester, _list());
      expect(_count(page, 'arcane-card'), greaterThanOrEqualTo(1));
      expect(page, contains('role="table"'));
      expect(page, contains('role="columnheader"'));
      expect(page, contains('data-label="Players"'));
      expect(page, contains('href="/server/Skyblock"'));
      final String nested = await _render(
        tester,
        AppCard(title: 'Servers', children: <Widget>[_list()]),
      );
      expect(
        _count(nested, 'class="arcane-card'),
        _count(page, 'class="arcane-card'),
      );
    });

    testServer('an empty data list shows its message', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        DataList<int>(
          label: 'Servers',
          items: const <int>[],
          columns: <ListColumn<int>>[
            ListColumn<int>('Server', (int i) => Text('$i')),
          ],
          emptyMessage: 'No servers yet',
        ),
      );
      expect(body, contains('No servers yet'));
      expect(body, contains('<p class="ui-caption">'));
    });
  });

  group('page', () {
    testServer('AppPage shows the title, tip, connection and role', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        const ServerScope(
          snapshot: null,
          state: ConnState.live,
          child: RoleScope(
            role: RoleInfo(role: 'admin', scopes: <String>['admin']),
            child: AppPage(
              title: 'Tuning',
              tip: 'Features and budgets.',
              children: <Widget>[Text('body')],
            ),
          ),
        ),
      );
      expect(body, contains('id="reactor-page-heading"'));
      expect(body, contains('Tuning'));
      expect(body, contains('Features and budgets.'));
      expect(body, contains('Online'));
      expect(body, contains('Admin'));
    });

    testServer('an ended pairing keeps the title only: no role, no data', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        const ServerScope(
          snapshot: null,
          state: ConnState.live,
          pairingEnded: true,
          child: RoleScope(
            role: RoleInfo(role: 'admin', scopes: <String>['admin']),
            child: AppPage(
              title: 'Overview',
              actions: ActionRow(
                secondary: <RowAction>[RowAction('Refresh', null)],
              ),
              children: <Widget>[Text('Healthy tiles')],
            ),
          ),
        ),
      );
      expect(body, contains('Overview'));
      expect(body, contains('Pairing ended'));
      expect(body, isNot(contains('Admin')));
      expect(body, isNot(contains('Healthy tiles')));
      expect(body, isNot(contains('Refresh')));
    });

    testServer('PageSection and ChartCard carry their headings', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        const PageSection(
          title: 'Vitals',
          children: <Widget>[
            ChartCard(title: 'Tick time', child: Text('chart')),
          ],
        ),
      );
      expect(body, contains('Vitals'));
      expect(body, contains('data-chart-card'));
      expect(body, contains('Tick time'));
    });
  });

  group('states', () {
    testServer('loading, empty and error read out', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        PageStack(
          children: <Widget>[
            const LoadingState(label: 'Loading worlds'),
            const EmptyState(message: 'No worlds'),
            ErrorState(message: 'Failed', onRetry: () {}),
          ],
        ),
      );
      expect(body, contains('aria-label="Loading worlds"'));
      expect(body, contains('No worlds'));
      expect(body, contains('role="alert"'));
      expect(body, contains('Retry'));
    });
  });
}
