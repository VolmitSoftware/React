library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/ui/kit/action_row.dart';
import 'package:react_web/ui/kit/confirm_dialog.dart';
import 'package:react_web/ui/kit/menu.dart';

Future<String> _render(ServerTester tester, Widget child) async {
  tester.pumpComponent(
    ArcaneThemeProvider(stylesheet: const ReactorStylesheet(), child: child),
  );
  final DocumentResponse response = await tester.request('/');
  expect(response.statusCode, 200);
  return response.body;
}

void main() {
  testServer('a toolbar orders more, secondary, cancel, then primary', (
    ServerTester tester,
  ) async {
    final String body = await _render(
      tester,
      ActionRow(
        label: 'Page actions',
        more: <AppMenuEntry>[AppMenuEntry('Export', () {})],
        secondary: <RowAction>[RowAction('Refresh', () {})],
        cancel: RowAction('Cancel', () {}),
        primary: RowAction('Save', () {}),
      ),
    );
    expect(body, contains('data-action-row="toolbar"'));
    expect(body, contains('role="toolbar"'));
    final List<int> at = <int>[
      body.indexOf('data-action-slot="more"'),
      body.indexOf('data-action-slot="secondary"'),
      body.indexOf('data-action-slot="cancel"'),
      body.indexOf('data-action-slot="primary"'),
    ];
    expect(at.every((int i) => i >= 0), isTrue, reason: '$at');
    expect(at, orderedEquals(<int>[...at]..sort()));
  });

  testServer('a footer is marked for the phone stack and has no menu', (
    ServerTester tester,
  ) async {
    final String body = await _render(
      tester,
      DialogFooter(
        cancel: RowAction('Cancel', () {}),
        primary: RowAction('Delete', () {}, destructive: true),
      ),
    );
    expect(body, contains('data-action-row="footer"'));
    expect(body, isNot(contains('data-action-slot="more"')));
    expect(body, contains('data-variant="destructive"'));
  });

  testServer('a null handler disables the button', (ServerTester tester) async {
    final String body = await _render(
      tester,
      ActionRow(primary: const RowAction('Run', null)),
    );
    expect(body, contains('disabled'));
  });

  testServer('ConfirmDialog asks as an alert dialog with Cancel and the verb', (
    ServerTester tester,
  ) async {
    final String body = await _render(
      tester,
      ConfirmDialog(
        title: 'Remove Skyblock?',
        message: 'Its saved code is deleted.',
        confirmLabel: 'Remove server',
        destructive: true,
        onConfirm: () {},
        onCancel: () {},
      ),
    );
    expect(body, contains('alertdialog'));
    expect(body, contains('Remove Skyblock?'));
    expect(body, contains('Cancel'));
    expect(body, contains('Remove server'));
    expect(body, contains('data-confirm'));
  });

  testServer('a row with only leading controls has no empty end group', (
    ServerTester tester,
  ) async {
    final String body = await _render(
      tester,
      ActionRow(label: 'Search', leading: <Widget>[Component.text('field')]),
    );
    expect(body, contains('ui-actions-lead'));
    expect(body, isNot(contains('ui-actions-end')));
  });

  testServer('a row action with an address is a link', (
    ServerTester tester,
  ) async {
    final String body = await _render(
      tester,
      ActionRow(
        secondary: <RowAction>[
          const RowAction('Incidents', null, href: '/server/a/incidents'),
        ],
      ),
    );
    expect(body, contains('href="/server/a/incidents"'));
    expect(body, contains('<a'));
  });

  testServer('a question with no host is answered no and never queued', (
    ServerTester tester,
  ) async {
    final ConfirmTicket ticket = confirmTicket(
      title: 'Stale question?',
      confirmLabel: 'Go',
    );
    expect(await ticket.answer, isFalse);
    final String body = await _render(tester, const ConfirmHost());
    expect(body, isNot(contains('Stale question?')));
  });

  testServer('a host rendered on the server never takes questions', (
    ServerTester tester,
  ) async {
    await _render(tester, const ConfirmHost());
    final bool answer = await confirmTicket(
      title: 'Remove?',
      confirmLabel: 'Remove',
    ).answer.timeout(const Duration(seconds: 1), onTimeout: () => true);
    expect(answer, isFalse);
  });
}
