library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/widget/gauge.dart';
import 'package:react_web/widget/stat_tile.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

SamplerSample _fakeSample({
  double value = 42.5,
  String suffix = '%',
  String? display,
  double min = 0.0,
  double max = 100.0,
  bool available = true,
}) => SamplerSample(
  id: 'test-id',
  name: 'CPU Usage',
  value: value,
  display: display ?? value.toString(),
  suffix: suffix,
  min: min,
  max: max,
  history: <double>[10.0, 20.0, 30.0, 40.0, 42.5],
  available: available,
);

void main() {
  group('StatTile.formatValue', () {
    test('null sample returns "--"', () {
      expect(StatTile.formatValue(null), equals('--'));
    });

    test('non-null sample returns the formatted display string', () {
      final SamplerSample sample = _fakeSample(value: 42.5, display: '42.5');
      expect(StatTile.formatValue(sample), equals('42.5'));
    });

    test('unavailable sample returns "--"', () {
      final SamplerSample sample = _fakeSample(
        value: 0.0,
        display: '0',
        available: false,
      );
      expect(StatTile.formatValue(sample), equals('--'));
    });

    test('uses the human display, not the raw value', () {
      final SamplerSample sample = _fakeSample(
        value: 1339115552.0,
        suffix: 'GB',
        display: '1.3',
      );
      expect(StatTile.formatValue(sample), equals('1.3'));
      expect(StatTile.formatValue(sample), isNot(contains('1339115552')));
    });

    test('suffix is exposed from sample', () {
      final SamplerSample sample = _fakeSample(suffix: 'ms');
      expect(sample.suffix, equals('ms'));
    });
  });

  group('Gauge.statusFor', () {
    test('value below warn threshold returns success', () {
      expect(Gauge.statusFor(30.0, (70.0, 90.0)), equals(GaugeStatus.success));
    });

    test('value at warn threshold returns warning', () {
      expect(Gauge.statusFor(70.0, (70.0, 90.0)), equals(GaugeStatus.warning));
    });

    test('value between warn and error thresholds returns warning', () {
      expect(Gauge.statusFor(80.0, (70.0, 90.0)), equals(GaugeStatus.warning));
    });

    test('value at error threshold returns error', () {
      expect(Gauge.statusFor(90.0, (70.0, 90.0)), equals(GaugeStatus.error));
    });

    test('value above error threshold returns error', () {
      expect(Gauge.statusFor(95.0, (70.0, 90.0)), equals(GaugeStatus.error));
    });
  });

  group('StatTile rendering', () {
    testServer('renders label text when sample is provided', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(StatTile(label: 'CPU Usage', sample: _fakeSample())),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('CPU Usage'),
        isTrue,
        reason: 'label must appear in rendered HTML',
      );
    });

    testServer('renders "--" when sample is null', (ServerTester tester) async {
      tester.pumpComponent(_wrap(StatTile(label: 'Memory', sample: null)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('--'),
        isTrue,
        reason: 'null sample must render "--" as value placeholder',
      );
    });

    testServer(
      'renders the human display value and suffix, not the raw value',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            StatTile(
              label: 'Memory Used',
              sample: _fakeSample(
                value: 1339115552.0,
                suffix: 'GB',
                display: '1.3',
              ),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('1.3'),
          isTrue,
          reason: 'formatted display value must appear in rendered HTML',
        );
        expect(
          res.body.contains('GB'),
          isTrue,
          reason: 'suffix must appear in rendered HTML',
        );
        expect(
          res.body.contains('1339115552'),
          isFalse,
          reason: 'raw unscaled value must not be rendered for the big number',
        );
      },
    );
  });

  group('Gauge rendering', () {
    testServer('renders label text', (ServerTester tester) async {
      tester.pumpComponent(
        _wrap(
          Gauge(
            label: 'CPU',
            value: 50.0,
            max: 100.0,
            thresholds: (70.0, 90.0),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('CPU'),
        isTrue,
        reason: 'Gauge must render its label',
      );
    });

    testServer('renders an accessible error meter above the error threshold', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          Gauge(
            label: 'CPU',
            value: 95.0,
            max: 100.0,
            thresholds: (70.0, 90.0),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('var(--destructive)'),
        isTrue,
        reason: 'The meter fill must use the destructive state color',
      );
      expect(
        res.body.contains('role="meter"'),
        isTrue,
        reason: 'Gauge values must expose meter semantics',
      );
      expect(
        res.body.contains('aria-valuenow="95.0"'),
        isTrue,
        reason: 'The meter must expose its current value',
      );
    });

    testServer('renders the provided display string as the meter value', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          Gauge(
            label: 'TPS',
            value: 19.5,
            display: '19.5',
            max: 20.0,
            thresholds: (5.0, 10.0),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('19.5'),
        isTrue,
        reason: 'gauge center must show the display string',
      );
    });
  });
}
