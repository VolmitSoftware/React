// The app's stylesheets lay out its own pages and draw the surfaces the
// library has no component for (charts, the map, the log); arcane_jaspr's
// components stay as the library draws them. Ceilings keep custom CSS from
// creeping back: the lines of web/styles/*.css (blank lines and comments
// not counted) and no rule may select a library class. Lower the ceiling
// when CSS goes; never raise it.
import 'dart:io';

import 'package:test/test.dart';

/// Non-blank, non-comment lines of web/styles/*.css.
const int cssLineBudget = 400;

/// Rules whose selector names an arcane_jaspr or shadcn class. Fix a
/// library defect in arcane_jaspr instead of restyling it here.
const int libraryRuleBudget = 0;

/// Library class names: `arcane-*` (except the theme class on the root) and
/// the shadcn sidebar's own.
final RegExp libraryClass = RegExp(
  r'\.(arcane-(?!theme-)[a-z0-9-]+|sidebar-[a-z0-9-]+)',
);

List<File> stylesheets() => <File>[
  for (final FileSystemEntity entity in Directory('web/styles').listSync())
    if (entity is File && entity.path.endsWith('.css')) entity,
]..sort((File a, File b) => a.path.compareTo(b.path));

String withoutComments(String css) =>
    css.replaceAll(RegExp(r'/\*[\s\S]*?\*/'), '');

/// Lines of [css] that hold something besides whitespace and comments.
int codeLines(String css) =>
    withoutComments(css)
        .split('\n')
        .where((String line) => line.trim().isNotEmpty)
        .length;

/// The selectors of [css], one per rule head; at-rule heads are skipped.
Iterable<String> selectors(String css) sync* {
  for (final RegExpMatch match in RegExp(
    r'([^{}]+)\{',
  ).allMatches(withoutComments(css))) {
    final String head = match[1]!.trim();
    if (head.isEmpty || head.startsWith('@')) continue;
    yield head;
  }
}

String _name(File file) => file.uri.pathSegments.last;

void main() {
  test('app CSS stays within its line budget', () {
    final Map<String, int> perFile = <String, int>{
      for (final File file in stylesheets())
        _name(file): codeLines(file.readAsStringSync()),
    };
    final int lines = perFile.values.fold(0, (int sum, int n) => sum + n);
    final String report = <String>[
      for (final MapEntry<String, int> entry in perFile.entries)
        '${entry.key} ${entry.value}',
      'total $lines, budget $cssLineBudget',
    ].join('\n');
    // ignore: avoid_print
    print(report);
    printOnFailure(report);
    expect(lines, lessThanOrEqualTo(cssLineBudget));
  });

  test('no rule selects a library class', () {
    final List<String> found = <String>[
      for (final File file in stylesheets())
        for (final String selector in selectors(file.readAsStringSync()))
          if (libraryClass.hasMatch(selector)) '${_name(file)}: $selector',
    ];
    printOnFailure(found.join('\n'));
    expect(found.length, lessThanOrEqualTo(libraryRuleBudget));
  });

  test('every stylesheet is linked from web/index.html', () {
    final String html = File('web/index.html').readAsStringSync();
    for (final File file in stylesheets()) {
      expect(html, contains('href="/styles/${_name(file)}"'));
    }
  });

  test('the scan counts and finds what it should', () {
    expect(codeLines('/* a\n b */\n\n.a {\n  color: red; /* c */\n}\n'), 3);
    expect(
      selectors('/* .arcane-button */ .x > .arcane-button { color: red; }')
          .single,
      '.x > .arcane-button',
    );
    expect(
      selectors('@media (max-width: 760px) { .a { b: c; } }').toList(),
      <String>['.a'],
    );
    expect(libraryClass.hasMatch('#arcane-root.arcane-theme-shadcn'), isFalse);
    expect(libraryClass.hasMatch('#arcane-root'), isFalse);
    expect(libraryClass.hasMatch('.nav .sidebar-link'), isTrue);
    expect(libraryClass.hasMatch(':is(.arcane-card, .x)'), isTrue);
  });
}
