library;

import 'package:test/test.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/model/config_tree.dart';

void main() {
  final Map<String, dynamic> sampleJson = <String, dynamic>{
    'sections': <dynamic>[
      <String, dynamic>{
        'name': 'Load Governors',
        'nodes': <dynamic>[
          <String, dynamic>{
            'key': 'view.min',
            'label': 'Min View',
            'type': 'int',
            'value': 4,
            'options': <dynamic>[],
            'doc': 'min chunks',
          },
          <String, dynamic>{
            'key': 'view.adaptive',
            'label': 'Adaptive',
            'type': 'bool',
            'value': true,
          },
        ],
      },
      <String, dynamic>{
        'name': 'Memory',
        'nodes': <dynamic>[
          <String, dynamic>{
            'key': 'gc.threshold',
            'label': 'GC %',
            'type': 'double',
            'value': 80.0,
          },
        ],
      },
    ],
  };

  group('ConfigTree.fromJson', () {
    test('sections.length is 2', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      expect(tree.sections.length, equals(2));
    });

    test('sections[0].name is Load Governors', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      expect(tree.sections[0].name, equals('Load Governors'));
    });

    test('sections[0].nodes.length is 2', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      expect(tree.sections[0].nodes.length, equals(2));
    });

    test('sections[0].nodes[0].type is intType', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      expect(tree.sections[0].nodes[0].type, equals(KnobType.intType));
    });

    test('sections[0].nodes[0].intValue is 4', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      expect(tree.sections[0].nodes[0].intValue, equals(4));
    });

    test('sections[1].nodes[0].doubleValue is 80.0', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      expect(tree.sections[1].nodes[0].doubleValue, equals(80.0));
    });

    test('empty tree when sections absent', () {
      final ConfigTree tree = ConfigTree.fromJson(<String, dynamic>{});
      expect(tree.sections, isEmpty);
    });
  });

  group('ConfigTree.flatten', () {
    test('contains key view.min with value 4', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      final Map<String, Object?> flat = tree.flatten();
      expect(flat['view.min'], equals(4));
    });

    test('contains key view.adaptive with value true', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      final Map<String, Object?> flat = tree.flatten();
      expect(flat['view.adaptive'], equals(true));
    });

    test('contains key gc.threshold with value 80.0', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      final Map<String, Object?> flat = tree.flatten();
      expect(flat['gc.threshold'], equals(80.0));
    });
  });

  group('ConfigTree.node', () {
    test('node returns knob for gc.threshold', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      final Knob? knob = tree.node('gc.threshold');
      expect(knob, isNotNull);
      expect(knob!.key, equals('gc.threshold'));
      expect(knob.doubleValue, equals(80.0));
    });

    test('node returns null for unknown key', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      expect(tree.node('nope'), isNull);
    });
  });

  group('ConfigSection.fromJson', () {
    test('round-trip toJson preserves name', () {
      final ConfigSection section = ConfigSection.fromJson(<String, dynamic>{
        'name': 'Test',
        'nodes': <dynamic>[],
      });
      expect(section.toJson()['name'], equals('Test'));
    });

    test('missing nodes defaults to empty list', () {
      final ConfigSection section = ConfigSection.fromJson(<String, dynamic>{
        'name': 'Empty',
      });
      expect(section.nodes, isEmpty);
    });
  });

  group('ConfigTree.toJson', () {
    test('toJson sections length matches', () {
      final ConfigTree tree = ConfigTree.fromJson(sampleJson);
      final Map<String, dynamic> j = tree.toJson();
      expect((j['sections'] as List<dynamic>).length, equals(2));
    });
  });
}
