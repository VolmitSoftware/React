import 'package:test/test.dart';

import 'package:react_web/chart/share_layout.dart';

void main() {
  group('shareSlices', () {
    test('keeps the biggest five and sums the rest into Other', () {
      final List<ShareSlice> slices = shareSlices(<ShareItem>[
        for (int index = 0; index < 9; index++)
          ShareItem('o$index', 'Owner $index', (index + 1).toDouble()),
      ], otherLabel: 'Other');
      expect(slices.length, 6);
      expect(slices.take(5).map((ShareSlice slice) => slice.id), <String>[
        'o8',
        'o7',
        'o6',
        'o5',
        'o4',
      ]);
      expect(slices.last.isOther, isTrue);
      expect(slices.last.value, 1 + 2 + 3 + 4);
      expect(slices.last.color, kShareColors - 1);
      expect(slices.first.start, 0);
      expect(slices.last.end, 1);
      for (int index = 1; index < slices.length; index++) {
        expect(slices[index].start, closeTo(slices[index - 1].end, 1e-9));
      }
      expect(slices.first.share, closeTo(9 / 45, 1e-9));
    });

    test('gives every named slice its own colour', () {
      final List<ShareSlice> slices = shareSlices(<ShareItem>[
        for (int index = 0; index < 5; index++)
          ShareItem('same-ish-$index', 'x', 1),
      ], otherLabel: 'Other');
      expect(slices.map((ShareSlice slice) => slice.color).toSet().length, 5);
      expect(slices.any((ShareSlice slice) => slice.isOther), isFalse);
    });

    test('keeps an owner on its colour while the ranking moves', () {
      int colorOf(List<ShareSlice> slices, String id) =>
          slices.firstWhere((ShareSlice slice) => slice.id == id).color;
      final List<ShareSlice> before = shareSlices(const <ShareItem>[
        ShareItem('Iris', 'Iris', 5),
        ShareItem('React', 'React', 1),
      ], otherLabel: 'Other');
      final List<ShareSlice> after = shareSlices(const <ShareItem>[
        ShareItem('Iris', 'Iris', 1),
        ShareItem('React', 'React', 5),
      ], otherLabel: 'Other');
      expect(colorOf(after, 'Iris'), colorOf(before, 'Iris'));
    });

    test('drops zero, negative and broken values; nothing left is empty', () {
      expect(
        shareSlices(const <ShareItem>[
          ShareItem('a', 'a', 0),
          ShareItem('b', 'b', -2),
          ShareItem('c', 'c', double.nan),
        ], otherLabel: 'Other'),
        isEmpty,
      );
    });
  });

  group('orderedSlices', () {
    test('keeps the given order and closes the turn', () {
      final List<ShareSlice> slices = orderedSlices(const <ShareItem>[
        ShareItem('event', 'Events', 1),
        ShareItem('tick', 'Tasks', 0),
        ShareItem('entity', 'Entities', 3),
      ]);
      expect(slices.map((ShareSlice slice) => slice.id), <String>[
        'event',
        'entity',
      ]);
      expect(slices.first.share, closeTo(0.25, 1e-9));
      expect(slices.last.end, 1);
    });
  });

  group('donutArcPath', () {
    test('draws a quarter from the top clockwise', () {
      final String path = donutArcPath(0, 0.25);
      expect(
        path,
        startsWith('M 50.000 2.000 A 48.0 48.0 0 0 1 98.000 50.000'),
      );
      expect(path, contains('L 80.000 50.000'));
      expect(path, endsWith('Z'));
    });

    test('flags the large arc past half a turn', () {
      expect(donutArcPath(0, 0.75), contains(' 0 1 1 '));
      expect(donutArcPath(0, 0.4), contains(' 0 0 1 '));
    });

    test('a whole turn is two halves, an empty one nothing', () {
      expect(RegExp('M ').allMatches(donutArcPath(0, 1)).length, 2);
      expect(donutArcPath(0.3, 0.3), isEmpty);
    });
  });

  group('stackSegments', () {
    test('lays kinds end to end in order on the shared scale', () {
      final List<BarSegment> segments = stackSegments(
        const <String, double>{'entity': 2, 'event': 1, 'custom': 1, 'tick': 0},
        order: const <String>['event', 'tick', 'entity'],
        scale: 8,
      );
      expect(segments.map((BarSegment segment) => segment.key), <String>[
        'event',
        'entity',
        'custom',
      ]);
      expect(segments[1].x, closeTo(0.125, 1e-9));
      expect(segments[1].width, closeTo(0.25, 1e-9));
      expect(segments.last.x + segments.last.width, closeTo(0.5, 1e-9));
    });

    test('no scale, no segments', () {
      expect(
        stackSegments(
          const <String, double>{'a': 1},
          order: const <String>[],
          scale: 0,
        ),
        isEmpty,
      );
    });
  });
}
