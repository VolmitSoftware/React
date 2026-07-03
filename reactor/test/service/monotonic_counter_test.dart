import 'package:test/test.dart';

import 'package:reactor/service/monotonic_counter.dart';
import 'package:reactor/state/fleet_manager.dart';
import 'package:reactor/state/memory_fleet_storage.dart';

void main() {
  group('MonotonicCounter', () {
    test('seeds from clock on first call', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final MonotonicCounter counter =
          MonotonicCounter(storage, clock: () => 1000);
      expect(counter.next('a'), equals(1000));
    });

    test('increments without consulting clock after first call', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final MonotonicCounter counter =
          MonotonicCounter(storage, clock: () => 1000);
      counter.next('a');
      expect(counter.next('a'), equals(1001));
    });

    test('per-credential independence', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final MonotonicCounter counter =
          MonotonicCounter(storage, clock: () => 1000);
      counter.next('a');
      counter.next('a');
      expect(counter.next('b'), equals(1000));
    });

    test('new instance resumes from stored value', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final MonotonicCounter first =
          MonotonicCounter(storage, clock: () => 1000);
      first.next('a');
      first.next('a');

      final MonotonicCounter second =
          MonotonicCounter(storage, clock: () => 5);
      expect(second.next('a'), equals(1002));
    });

    test('50 successive calls produce strictly increasing sequence', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final MonotonicCounter counter =
          MonotonicCounter(storage, clock: () => 0);
      final List<int> values =
          List<int>.generate(50, (_) => counter.next('x'));
      for (int i = 1; i < values.length; i++) {
        expect(values[i], greaterThan(values[i - 1]));
      }
    });
  });
}
