library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/widget/section_card.dart';
import 'package:reactor/widget/stat_tile.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

SamplerSample _fakeSample({
  double value = 10.0,
  String suffix = '',
  String? display,
}) =>
    SamplerSample(
      id: 'test',
      name: 'Test',
      value: value,
      display: display ?? value.toString(),
      suffix: suffix,
      min: 0.0,
      max: 100.0,
      history: <double>[],
    );

void main() {
  group('statGrid', () {
    testServer(
      'renders a grid div containing all tile labels',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(statGrid(<Widget>[
            StatTile(label: 'Alpha', sample: _fakeSample()),
            StatTile(label: 'Beta', sample: null),
          ])),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Alpha'),
          isTrue,
          reason: 'statGrid must render the first tile label',
        );
        expect(
          res.body.contains('Beta'),
          isTrue,
          reason: 'statGrid must render the second tile label',
        );
        expect(
          res.body.contains('display:grid') ||
              res.body.contains('grid-template-columns'),
          isTrue,
          reason: 'statGrid must render a CSS grid container',
        );
      },
    );
  });
}
