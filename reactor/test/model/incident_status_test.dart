library;

import 'package:test/test.dart';
import 'package:reactor/model/incident_status.dart';

void main() {
  final Map<String, dynamic> fullJson = <String, dynamic>{
    'score': 42.5,
    'state': 'ELEVATED',
    'timeline': <dynamic>['t0 spike', 't1 recover'],
    'contributors': <dynamic>[
      <String, dynamic>{'name': 'mspt', 'weight': 0.6, 'value': 18.2},
      <String, dynamic>{'name': 'gc', 'weight': 0.4, 'value': 9.0},
    ],
  };

  group('IncidentStatus.fromJson', () {
    test('parses score', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      expect(s.score, equals(42.5));
    });

    test('parses state', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      expect(s.state, equals('ELEVATED'));
    });

    test('parses timeline length', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      expect(s.timeline.length, equals(2));
    });

    test('parses timeline first entry', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      expect(s.timeline[0], equals('t0 spike'));
    });

    test('parses contributors length', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      expect(s.contributors.length, equals(2));
    });

    test('parses first contributor name', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      expect(s.contributors[0].name, equals('mspt'));
    });

    test('parses first contributor weight', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      expect(s.contributors[0].weight, equals(0.6));
    });

    test('parses first contributor value', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      expect(s.contributors[0].value, equals(18.2));
    });

    test('absent timeline defaults to empty list', () {
      final IncidentStatus s = IncidentStatus.fromJson(<String, dynamic>{
        'score': 0.0,
        'state': 'OK',
      });
      expect(s.timeline, isEmpty);
    });

    test('absent contributors defaults to empty list', () {
      final IncidentStatus s = IncidentStatus.fromJson(<String, dynamic>{
        'score': 0.0,
        'state': 'OK',
      });
      expect(s.contributors, isEmpty);
    });
  });

  group('IncidentStatus.toJson round-trip', () {
    test('toJson round-trips score and state', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      final Map<String, dynamic> j = s.toJson();
      expect(j['score'], equals(42.5));
      expect(j['state'], equals('ELEVATED'));
    });

    test('toJson round-trips timeline', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      final Map<String, dynamic> j = s.toJson();
      final List<dynamic> tl = j['timeline'] as List<dynamic>;
      expect(tl[0], equals('t0 spike'));
      expect(tl[1], equals('t1 recover'));
    });

    test('toJson round-trips contributors', () {
      final IncidentStatus s = IncidentStatus.fromJson(fullJson);
      final Map<String, dynamic> j = s.toJson();
      final List<dynamic> cs = j['contributors'] as List<dynamic>;
      expect(cs.length, equals(2));
      final Map<String, dynamic> c0 = cs[0] as Map<String, dynamic>;
      expect(c0['name'], equals('mspt'));
      expect(c0['weight'], equals(0.6));
      expect(c0['value'], equals(18.2));
    });
  });

  group('IncidentContributor.fromJson', () {
    test('parses name', () {
      final IncidentContributor c = IncidentContributor.fromJson(
          <String, dynamic>{'name': 'gc', 'weight': 0.4, 'value': 9.0});
      expect(c.name, equals('gc'));
    });

    test('parses weight as double', () {
      final IncidentContributor c = IncidentContributor.fromJson(
          <String, dynamic>{'name': 'gc', 'weight': 0.4, 'value': 9.0});
      expect(c.weight, equals(0.4));
    });

    test('parses value as double', () {
      final IncidentContributor c = IncidentContributor.fromJson(
          <String, dynamic>{'name': 'gc', 'weight': 0.4, 'value': 9.0});
      expect(c.value, equals(9.0));
    });

    test('toJson emits all fields', () {
      final IncidentContributor c = IncidentContributor.fromJson(
          <String, dynamic>{'name': 'gc', 'weight': 0.4, 'value': 9.0});
      final Map<String, dynamic> j = c.toJson();
      expect(j['name'], equals('gc'));
      expect(j['weight'], equals(0.4));
      expect(j['value'], equals(9.0));
    });
  });
}
