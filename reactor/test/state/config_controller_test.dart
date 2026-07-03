library;

import 'package:test/test.dart';

import 'package:reactor/model/config_tree.dart';
import 'package:reactor/model/knob.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/service/react_exceptions.dart';
import 'package:reactor/state/config_controller.dart';

ConfigTree _fakeTree(List<String> keys) => ConfigTree(
      sections: <ConfigSection>[
        ConfigSection(
          name: 'Performance',
          nodes: <Knob>[
            for (final String k in keys)
              Knob(
                key: k,
                label: k,
                type: KnobType.intType,
                value: 1,
              ),
          ],
        ),
      ],
    );

class _FakeConfigClient implements IConfigClient {
  ConfigTree configResult = const ConfigTree();
  bool throwOnConfig = false;

  ConfigTree applyConfigResult = const ConfigTree();
  bool throwOnApplyConfig = false;
  Map<String, Object?>? capturedApplyConfigArgs;
  int applyConfigCallCount = 0;

  ConfigTree applyPresetResult = const ConfigTree();
  bool throwOnApplyPreset = false;
  String? capturedPresetName;

  @override
  Future<ConfigTree> config() async {
    if (throwOnConfig) throw const ReactUnavailable('forced');
    return configResult;
  }

  @override
  Future<ConfigTree> applyConfig(Map<String, Object?> changes) async {
    applyConfigCallCount++;
    capturedApplyConfigArgs = Map<String, Object?>.of(changes);
    if (throwOnApplyConfig) throw const ReactForbidden();
    return applyConfigResult;
  }

  @override
  Future<ConfigTree> applyPreset(String name) async {
    capturedPresetName = name;
    if (throwOnApplyPreset) throw const ReactForbidden();
    return applyPresetResult;
  }
}

void main() {
  group('ConfigController.load', () {
    test('sets tree and clears pending on success', () async {
      final _FakeConfigClient client = _FakeConfigClient();
      client.configResult = _fakeTree(<String>['tick-rate']);
      int notifyCount = 0;
      final ConfigController controller = ConfigController(
        client,
        onChange: () => notifyCount++,
      );

      controller.edit('stale', 99);
      await controller.load();

      expect(controller.tree.sections.length, equals(1));
      expect(controller.tree.sections[0].nodes[0].key, equals('tick-rate'));
      expect(controller.pending, isEmpty);
      expect(controller.loading, isFalse);
      expect(controller.error, isNull);
      expect(notifyCount, greaterThanOrEqualTo(2));
    });

    test('sets error and calls onError on failure', () async {
      final _FakeConfigClient client = _FakeConfigClient();
      client.throwOnConfig = true;
      Object? capturedError;
      final ConfigController controller = ConfigController(
        client,
        onError: (Object e) => capturedError = e,
      );

      await controller.load();

      expect(controller.tree.sections, isEmpty);
      expect(controller.loading, isFalse);
      expect(controller.error, isA<ReactUnavailable>());
      expect(capturedError, isA<ReactUnavailable>());
    });
  });

  group('ConfigController.edit', () {
    test('records pending change and marks dirty', () {
      final _FakeConfigClient client = _FakeConfigClient();
      final ConfigController controller = ConfigController(client);

      controller.edit('a.b', 5);

      expect(controller.dirty, isTrue);
      expect(controller.pending['a.b'], equals(5));
    });

    test('dirty is false before any edit', () {
      final ConfigController controller = ConfigController(_FakeConfigClient());
      expect(controller.dirty, isFalse);
    });
  });

  group('ConfigController.apply', () {
    test('calls applyConfig with pending map and replaces tree on success',
        () async {
      final _FakeConfigClient client = _FakeConfigClient();
      client.applyConfigResult = _fakeTree(<String>['server-tick']);
      final ConfigController controller = ConfigController(client);

      controller.edit('tick-rate', 20);
      controller.edit('max-players', 100);
      await controller.apply();

      expect(client.applyConfigCallCount, equals(1));
      expect(client.capturedApplyConfigArgs,
          equals(<String, Object?>{'tick-rate': 20, 'max-players': 100}));
      expect(controller.tree.sections[0].nodes[0].key, equals('server-tick'));
      expect(controller.pending, isEmpty);
      expect(controller.dirty, isFalse);
      expect(controller.saving, isFalse);
    });

    test('is a no-op when pending is empty', () async {
      final _FakeConfigClient client = _FakeConfigClient();
      final ConfigController controller = ConfigController(client);

      await controller.apply();

      expect(client.applyConfigCallCount, equals(0));
    });

    test('calls onError and retains pending when applyConfig throws', () async {
      final _FakeConfigClient client = _FakeConfigClient();
      client.throwOnApplyConfig = true;
      Object? capturedError;
      final ConfigController controller = ConfigController(
        client,
        onError: (Object e) => capturedError = e,
      );

      controller.edit('a.b', 5);
      await controller.apply();

      expect(capturedError, isA<ReactForbidden>());
      expect(controller.pending, equals(<String, Object?>{'a.b': 5}));
      expect(controller.dirty, isTrue);
      expect(controller.saving, isFalse);
    });
  });

  group('ConfigController.applyPreset', () {
    test('calls applyPreset with name, replaces tree, clears pending', () async {
      final _FakeConfigClient client = _FakeConfigClient();
      client.applyPresetResult = _fakeTree(<String>['preset-key']);
      final ConfigController controller = ConfigController(client);

      controller.edit('old-key', 42);
      await controller.applyPreset('balanced');

      expect(client.capturedPresetName, equals('balanced'));
      expect(controller.tree.sections[0].nodes[0].key, equals('preset-key'));
      expect(controller.pending, isEmpty);
      expect(controller.dirty, isFalse);
      expect(controller.saving, isFalse);
    });

    test('calls onError when applyPreset throws', () async {
      final _FakeConfigClient client = _FakeConfigClient();
      client.throwOnApplyPreset = true;
      Object? capturedError;
      final ConfigController controller = ConfigController(
        client,
        onError: (Object e) => capturedError = e,
      );

      await controller.applyPreset('off');

      expect(capturedError, isA<ReactForbidden>());
      expect(controller.saving, isFalse);
    });
  });
}
