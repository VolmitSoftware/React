library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/control_scope.dart';

const String _kMarker = 'control-client-present';

class _FakeControlClient implements IControlClient {
  @override
  Future<List<ControlItem>> features() async => <ControlItem>[];

  @override
  Future<ControlItem> feature(String id) async => throw UnimplementedError();

  @override
  Future<ControlItem> setFeatureEnabled(String id, bool enabled) async =>
      throw UnimplementedError();

  @override
  Future<ControlItem> setFeatureConfig(
    String id,
    Map<String, Object?> knobs,
  ) async => throw UnimplementedError();

  @override
  Future<List<ControlItem>> tweaks() async => <ControlItem>[];

  @override
  Future<ControlItem> tweak(String id) async => throw UnimplementedError();

  @override
  Future<ControlItem> setTweakEnabled(String id, bool enabled) async =>
      throw UnimplementedError();

  @override
  Future<ControlItem> setTweakConfig(
    String id,
    Map<String, Object?> knobs,
  ) async => throw UnimplementedError();

  @override
  Future<List<WorldSettings>> worlds() async => <WorldSettings>[];

  @override
  Future<WorldSettings> setWorld(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) async => throw UnimplementedError();
}

class _Probe extends StatelessWidget {
  const _Probe();

  @override
  Widget build(BuildContext context) {
    final ControlScope? scope = ControlScope.of(context);
    if (scope?.client != null) {
      return Component.text(_kMarker);
    }
    return Component.fragment(const <Widget>[]);
  }
}

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('ControlScope', () {
    testServer('probe renders marker when client is non-null', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ControlScope(client: _FakeControlClient(), child: const _Probe()),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(_kMarker),
        isTrue,
        reason:
            'marker must appear when ControlScope carries a non-null client',
      );
    });

    testServer('probe does not render marker when client is null', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(ControlScope(client: null, child: const _Probe())),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(_kMarker),
        isFalse,
        reason:
            'marker must not appear when ControlScope carries a null client',
      );
    });

    test('updateShouldNotify returns true when client changes', () {
      final _FakeControlClient c1 = _FakeControlClient();
      final _FakeControlClient c2 = _FakeControlClient();
      final ControlScope s1 = ControlScope(
        client: c1,
        child: Component.fragment(const <Widget>[]),
      );
      final ControlScope s2 = ControlScope(
        client: c2,
        child: Component.fragment(const <Widget>[]),
      );
      expect(s1.updateShouldNotify(s2), isTrue);
    });

    test('updateShouldNotify returns false when client is same instance', () {
      final _FakeControlClient c = _FakeControlClient();
      final ControlScope s1 = ControlScope(
        client: c,
        child: Component.fragment(const <Widget>[]),
      );
      final ControlScope s2 = ControlScope(
        client: c,
        child: Component.fragment(const <Widget>[]),
      );
      expect(s1.updateShouldNotify(s2), isFalse);
    });
  });
}
