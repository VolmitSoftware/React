library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/world_overrides_controller.dart';

WorldSettings _world(
  String name, {
  double budgetMs = 50.0,
  double panicMs = 80.0,
  double releaseMs = 40.0,
}) => WorldSettings(
  key: 'minecraft:$name',
  name: name,
  pressureMode: PressureMode.normal,
  budgetMs: budgetMs,
  panicMs: panicMs,
  releaseMs: releaseMs,
);

class _FakeControlClient implements IControlClient {
  List<WorldSettings> worldsResult = <WorldSettings>[];
  bool throwOnWorlds = false;

  Future<WorldSettings> Function(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  })?
  onSetWorld;

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
  Future<List<WorldSettings>> worlds() async {
    if (throwOnWorlds) throw const ReactUnavailable('forced');
    return worldsResult;
  }

  @override
  Future<WorldSettings> setWorld(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) {
    if (onSetWorld != null) {
      return onSetWorld!(
        name,
        budgetMs: budgetMs,
        panicMs: panicMs,
        releaseMs: releaseMs,
      );
    }
    throw UnimplementedError();
  }
}

void main() {
  group('WorldOverridesController.load', () {
    test('populates worlds and clears loading on success', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.worldsResult = <WorldSettings>[
        _world('overworld'),
        _world('nether'),
      ];
      int notifyCount = 0;
      final WorldOverridesController controller = WorldOverridesController(
        client,
        onChange: () => notifyCount++,
      );

      await controller.load();

      expect(controller.worlds.length, equals(2));
      expect(controller.worlds[0].name, equals('overworld'));
      expect(controller.worlds[1].name, equals('nether'));
      expect(controller.loading, isFalse);
      expect(controller.error, isNull);
      expect(notifyCount, greaterThanOrEqualTo(2));
    });

    test('sets error and calls onError on failure', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.throwOnWorlds = true;
      Object? capturedError;
      int notifyCount = 0;
      final WorldOverridesController controller = WorldOverridesController(
        client,
        onChange: () => notifyCount++,
        onError: (Object e) => capturedError = e,
      );

      await controller.load();

      expect(controller.worlds, isEmpty);
      expect(controller.loading, isFalse);
      expect(controller.error, isA<ReactUnavailable>());
      expect(capturedError, isA<ReactUnavailable>());
      expect(notifyCount, greaterThanOrEqualTo(2));
    });
  });

  group('WorldOverridesController.setBudget optimism', () {
    test('applies optimistic budgetMs before server responds', () async {
      final Completer<WorldSettings> completer = Completer<WorldSettings>();
      final _FakeControlClient client = _FakeControlClient();
      client.worldsResult = <WorldSettings>[
        _world('overworld', budgetMs: 50.0),
      ];
      client.onSetWorld =
          (
            String name, {
            double? budgetMs,
            double? panicMs,
            double? releaseMs,
          }) => completer.future;

      final WorldOverridesController controller = WorldOverridesController(
        client,
      );
      await controller.load();

      final Future<void> pending = controller.setBudget(
        'overworld',
        budgetMs: 75.0,
      );

      expect(
        controller.worlds
            .firstWhere((WorldSettings w) => w.name == 'overworld')
            .budgetMs,
        equals(75.0),
        reason: 'optimistic budgetMs must be visible before server responds',
      );

      final WorldSettings serverResult = _world('overworld', budgetMs: 75.0);
      completer.complete(serverResult);
      await pending;

      expect(
        controller.worlds
            .firstWhere((WorldSettings w) => w.name == 'overworld')
            .budgetMs,
        equals(75.0),
      );
    });

    test('replaces world with server result on success', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.worldsResult = <WorldSettings>[
        _world('overworld', budgetMs: 50.0),
      ];
      final WorldSettings serverResult = _world('overworld', budgetMs: 75.0);
      client.onSetWorld =
          (
            String name, {
            double? budgetMs,
            double? panicMs,
            double? releaseMs,
          }) async => serverResult;

      final WorldOverridesController controller = WorldOverridesController(
        client,
      );
      await controller.load();
      await controller.setBudget('overworld', budgetMs: 75.0);

      expect(
        controller.worlds.firstWhere(
          (WorldSettings w) => w.name == 'overworld',
        ),
        same(serverResult),
      );
    });
  });

  group('WorldOverridesController.setBudget rollback', () {
    test(
      'restores previous budgetMs and calls onError on server failure',
      () async {
        final _FakeControlClient client = _FakeControlClient();
        client.worldsResult = <WorldSettings>[
          _world('overworld', budgetMs: 50.0),
        ];
        client.onSetWorld =
            (
              String name, {
              double? budgetMs,
              double? panicMs,
              double? releaseMs,
            }) async => throw const ReactConflict();

        Object? capturedError;
        final WorldOverridesController controller = WorldOverridesController(
          client,
          onError: (Object e) => capturedError = e,
        );
        await controller.load();
        await controller.setBudget('overworld', budgetMs: 75.0);

        expect(
          controller.worlds
              .firstWhere((WorldSettings w) => w.name == 'overworld')
              .budgetMs,
          equals(50.0),
          reason: 'must roll back to original budgetMs when server throws',
        );
        expect(capturedError, isA<ReactConflict>());
      },
    );
  });

  group('WorldOverridesController.setBudget partial fields', () {
    test('only updates provided fields optimistically', () async {
      final _FakeControlClient client = _FakeControlClient();
      client.worldsResult = <WorldSettings>[
        _world('overworld', budgetMs: 50.0, panicMs: 80.0, releaseMs: 40.0),
      ];
      client.onSetWorld =
          (
            String name, {
            double? budgetMs,
            double? panicMs,
            double? releaseMs,
          }) async => _world(
            name,
            budgetMs: budgetMs ?? 50.0,
            panicMs: panicMs ?? 80.0,
            releaseMs: releaseMs ?? 40.0,
          );

      final WorldOverridesController controller = WorldOverridesController(
        client,
      );
      await controller.load();
      await controller.setBudget('overworld', panicMs: 90.0);

      final WorldSettings result = controller.worlds.firstWhere(
        (WorldSettings w) => w.name == 'overworld',
      );
      expect(result.budgetMs, equals(50.0));
      expect(result.panicMs, equals(90.0));
      expect(result.releaseMs, equals(40.0));
    });
  });
}
