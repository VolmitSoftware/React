library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:reactor/model/control_item.dart';
import 'package:reactor/model/knob.dart';
import 'package:reactor/model/world_settings.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/service/react_exceptions.dart';
import 'package:reactor/state/control_list_controller.dart';

ControlItem _item(String id, {bool enabled = false, List<Knob> knobs = const <Knob>[]}) =>
    ControlItem(
      id: id,
      name: id,
      category: 'test',
      enabled: enabled,
      description: '',
      knobs: knobs,
    );

Knob _knob(String key, Object? value) => Knob(
      key: key,
      label: key,
      type: KnobType.intType,
      value: value,
    );

class _FakeControlClient implements IControlClient {
  List<ControlItem> featuresResult = <ControlItem>[];
  bool throwOnFeatures = false;

  List<ControlItem> tweaksResult = <ControlItem>[];
  bool throwOnTweaks = false;

  int setFeatureEnabledCallCount = 0;
  Future<ControlItem> Function(String id, bool enabled)? onSetFeatureEnabled;

  int setTweakEnabledCallCount = 0;
  Future<ControlItem> Function(String id, bool enabled)? onSetTweakEnabled;

  Future<ControlItem> Function(String id, Map<String, Object?> knobs)? onSetFeatureConfig;
  Future<ControlItem> Function(String id, Map<String, Object?> knobs)? onSetTweakConfig;

  @override
  Future<List<ControlItem>> features() async {
    if (throwOnFeatures) throw const ReactUnavailable('forced');
    return featuresResult;
  }

  @override
  Future<ControlItem> feature(String id) async => throw UnimplementedError();

  @override
  Future<ControlItem> setFeatureEnabled(String id, bool enabled) {
    setFeatureEnabledCallCount++;
    if (onSetFeatureEnabled != null) return onSetFeatureEnabled!(id, enabled);
    throw UnimplementedError();
  }

  @override
  Future<ControlItem> setFeatureConfig(String id, Map<String, Object?> knobs) {
    if (onSetFeatureConfig != null) return onSetFeatureConfig!(id, knobs);
    throw UnimplementedError();
  }

  @override
  Future<List<ControlItem>> tweaks() async {
    if (throwOnTweaks) throw const ReactUnavailable('forced');
    return tweaksResult;
  }

  @override
  Future<ControlItem> tweak(String id) async => throw UnimplementedError();

  @override
  Future<ControlItem> setTweakEnabled(String id, bool enabled) {
    setTweakEnabledCallCount++;
    if (onSetTweakEnabled != null) return onSetTweakEnabled!(id, enabled);
    throw UnimplementedError();
  }

  @override
  Future<ControlItem> setTweakConfig(String id, Map<String, Object?> knobs) {
    if (onSetTweakConfig != null) return onSetTweakConfig!(id, knobs);
    throw UnimplementedError();
  }

  @override
  Future<List<WorldSettings>> worlds() async => <WorldSettings>[];

  @override
  Future<WorldSettings> setWorld(String name,
          {double? budgetMs, double? panicMs, double? releaseMs}) async =>
      throw UnimplementedError();
}

void main() {
  group('ControlListController.load (features)', () {
    test('populates items and clears loading on success', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.featuresResult = <ControlItem>[_item('a'), _item('b')];
      int notifyCount = 0;
      final ControlListController controller = ControlListController(
        client,
        isTweaks: false,
        onChange: () => notifyCount++,
      );

      await controller.load();

      expect(controller.items.length, equals(2));
      expect(controller.items[0].id, equals('a'));
      expect(controller.items[1].id, equals('b'));
      expect(controller.loading, isFalse);
      expect(controller.error, isNull);
      expect(notifyCount, greaterThanOrEqualTo(2));
    });

    test('sets error and calls onError on failure', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.throwOnFeatures = true;
      Object? capturedError;
      int notifyCount = 0;
      final ControlListController controller = ControlListController(
        client,
        isTweaks: false,
        onChange: () => notifyCount++,
        onError: (Object e) => capturedError = e,
      );

      await controller.load();

      expect(controller.items, isEmpty);
      expect(controller.loading, isFalse);
      expect(controller.error, isA<ReactUnavailable>());
      expect(capturedError, isA<ReactUnavailable>());
      expect(notifyCount, greaterThanOrEqualTo(2));
    });
  });

  group('ControlListController.load (tweaks)', () {
    test('uses tweaks() when isTweaks is true', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.tweaksResult = <ControlItem>[_item('t1')];
      final ControlListController controller = ControlListController(
        client,
        isTweaks: true,
      );

      await controller.load();

      expect(controller.items.length, equals(1));
      expect(controller.items[0].id, equals('t1'));
    });
  });

  group('ControlListController.toggle optimism', () {
    test('applies optimistic update before server responds', () async {
      final Completer<ControlItem> completer = Completer<ControlItem>();
      final _FakeControlClient client = _FakeControlClient();
      client.featuresResult = <ControlItem>[_item('a', enabled: false)];
      client.onSetFeatureEnabled = (String id, bool enabled) => completer.future;

      final ControlListController controller = ControlListController(
        client,
        isTweaks: false,
      );
      await controller.load();

      final Future<void> pending = controller.toggle('a', true);

      expect(
        controller.items.firstWhere((ControlItem i) => i.id == 'a').enabled,
        isTrue,
        reason: 'optimistic update must be visible synchronously before server responds',
      );

      completer.complete(_item('a', enabled: true));
      await pending;

      expect(controller.items.firstWhere((ControlItem i) => i.id == 'a').enabled, isTrue);
    });

    test('replaces item with server result after future completes', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.featuresResult = <ControlItem>[_item('a', enabled: false)];
      final ControlItem serverItem = _item('a', enabled: true);
      client.onSetFeatureEnabled =
          (String id, bool enabled) async => serverItem;

      final ControlListController controller = ControlListController(
        client,
        isTweaks: false,
      );
      await controller.load();
      await controller.toggle('a', true);

      expect(
        controller.items.firstWhere((ControlItem i) => i.id == 'a'),
        same(serverItem),
      );
    });
  });

  group('ControlListController.toggle rollback', () {
    test('restores previous value and calls onError on server failure', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.featuresResult = <ControlItem>[_item('a', enabled: false)];
      client.onSetFeatureEnabled =
          (String id, bool enabled) async => throw const ReactConflict();

      Object? capturedError;
      final ControlListController controller = ControlListController(
        client,
        isTweaks: false,
        onError: (Object e) => capturedError = e,
      );
      await controller.load();
      await controller.toggle('a', true);

      expect(
        controller.items.firstWhere((ControlItem i) => i.id == 'a').enabled,
        isFalse,
        reason: 'must roll back to previous value when server throws',
      );
      expect(capturedError, isA<ReactConflict>());
    });
  });

  group('ControlListController.setKnob optimism', () {
    test('applies optimistic knob value before server responds', () async {
      final Knob initialKnob = _knob('speed', 10);
      final _FakeControlClient client = _FakeControlClient();
      client.featuresResult = <ControlItem>[
        _item('a', knobs: <Knob>[initialKnob])
      ];
      final Completer<ControlItem> completer = Completer<ControlItem>();
      client.onSetFeatureConfig =
          (String id, Map<String, Object?> knobs) => completer.future;

      final ControlListController controller = ControlListController(
        client,
        isTweaks: false,
      );
      await controller.load();

      final Future<void> pending = controller.setKnob('a', 'speed', 99);

      final ControlItem optimisticItem =
          controller.items.firstWhere((ControlItem i) => i.id == 'a');
      expect(
        optimisticItem.knobs.firstWhere((Knob k) => k.key == 'speed').value,
        equals(99),
        reason: 'optimistic knob value must be visible before server responds',
      );

      final ControlItem serverItem =
          _item('a', knobs: <Knob>[_knob('speed', 99)]);
      completer.complete(serverItem);
      await pending;

      expect(
        controller.items
            .firstWhere((ControlItem i) => i.id == 'a')
            .knobs
            .firstWhere((Knob k) => k.key == 'speed')
            .value,
        equals(99),
      );
    });

    test('rolls back knob value on server failure', () async {
      final Knob initialKnob = _knob('speed', 10);
      final _FakeControlClient client = _FakeControlClient();
      client.featuresResult = <ControlItem>[
        _item('a', knobs: <Knob>[initialKnob])
      ];
      client.onSetFeatureConfig =
          (String id, Map<String, Object?> knobs) async =>
              throw const ReactUnavailable();

      Object? capturedError;
      final ControlListController controller = ControlListController(
        client,
        isTweaks: false,
        onError: (Object e) => capturedError = e,
      );
      await controller.load();
      await controller.setKnob('a', 'speed', 99);

      expect(
        controller.items
            .firstWhere((ControlItem i) => i.id == 'a')
            .knobs
            .firstWhere((Knob k) => k.key == 'speed')
            .value,
        equals(10),
        reason: 'must roll back to original knob value on failure',
      );
      expect(capturedError, isA<ReactUnavailable>());
    });
  });

  group('ControlListController.setAll', () {
    test('toggles only items whose enabled differs and calls setFeatureEnabled correct count', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.featuresResult = <ControlItem>[
        _item('a', enabled: false),
        _item('b', enabled: true),
        _item('c', enabled: false),
      ];
      client.onSetFeatureEnabled =
          (String id, bool enabled) async => _item(id, enabled: enabled);

      final ControlListController controller = ControlListController(
        client,
        isTweaks: false,
      );
      await controller.load();
      await controller.setAll(true);

      expect(
        controller.items.every((ControlItem i) => i.enabled),
        isTrue,
        reason: 'all items must be enabled after setAll(true)',
      );
      expect(
        client.setFeatureEnabledCallCount,
        equals(2),
        reason: 'setFeatureEnabled must be called only for items that changed',
      );
    });
  });

  group('ControlListController.enabledCount', () {
    test('counts enabled items', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.featuresResult = <ControlItem>[
        _item('a', enabled: true),
        _item('b', enabled: false),
        _item('c', enabled: true),
      ];
      final ControlListController controller = ControlListController(
        client,
        isTweaks: false,
      );
      await controller.load();

      expect(controller.enabledCount, equals(2));
    });
  });
}
