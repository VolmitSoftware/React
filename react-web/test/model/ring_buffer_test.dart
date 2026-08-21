library;

import 'package:test/test.dart';
import 'package:react_web/model/ring_buffer.dart';

void main() {
  group('RingBuffer', () {
    test('capacity 3: adding 4 elements keeps newest 3', () {
      final RingBuffer buf = RingBuffer(3);
      buf.add(1.0);
      buf.add(2.0);
      buf.add(3.0);
      buf.add(4.0);
      expect(buf.toList(), equals([2.0, 3.0, 4.0]));
    });

    test('capacity 3: fewer than capacity elements preserved in order', () {
      final RingBuffer buf = RingBuffer(3);
      buf.add(5.0);
      buf.add(6.0);
      expect(buf.toList(), equals([5.0, 6.0]));
    });

    test('empty buffer returns empty list', () {
      final RingBuffer buf = RingBuffer(3);
      expect(buf.toList(), isEmpty);
    });

    test('default capacity is 128', () {
      final RingBuffer buf = RingBuffer(128);
      for (int i = 0; i < 130; i++) {
        buf.add(i.toDouble());
      }
      final List<double> result = buf.toList();
      expect(result.length, equals(128));
      expect(result.first, equals(2.0));
      expect(result.last, equals(129.0));
    });

    test('exactly capacity elements: all preserved', () {
      final RingBuffer buf = RingBuffer(3);
      buf.add(10.0);
      buf.add(20.0);
      buf.add(30.0);
      expect(buf.toList(), equals([10.0, 20.0, 30.0]));
    });
  });
}
