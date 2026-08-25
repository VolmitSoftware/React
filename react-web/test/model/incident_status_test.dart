library;

import 'package:react_web/model/incident_status.dart';
import 'package:test/test.dart';

void main() {
  final Map<String, dynamic> fullJson = <String, dynamic>{
    'score': 42.5,
    'scoreAvailable': true,
    'sampledAtMs': 1234,
    'state': 'ACTIVE',
    'contributors': <dynamic>[
      <String, dynamic>{
        'id': 'tick-ms-p95',
        'label': 'Tick P95',
        'available': true,
        'weight': 0.6,
        'value': 120.0,
        'display': '120 ms',
        'pressure': 0.7,
        'scorePoints': 21.0,
        'minimum': 50.0,
        'maximum': 150.0,
      },
      <String, dynamic>{
        'id': 'gc-time-percent',
        'label': 'GC Time',
        'available': false,
        'weight': 0.4,
        'value': 0.0,
        'display': 'Unavailable',
        'pressure': 0.0,
        'scorePoints': 0.0,
        'minimum': 2.0,
        'maximum': 25.0,
      },
    ],
    'incidents': <dynamic>[
      <String, dynamic>{
        'id': 'event-id',
        'incidentId': 'incident-id',
        'kind': 'REDSTONE_CIRCUIT',
        'phase': 'THROTTLED',
        'severity': 'WARNING',
        'occurredAtMs': 1200,
        'startedAtMs': 1200,
        'source': 'circuit-manager',
        'title': 'Circuit throttled',
        'summary': 'A busy component was throttled.',
        'cause': 'Redstone event span crossed the threshold.',
        'location': <String, dynamic>{
          'worldId': 'world-id',
          'world': 'world',
          'x': 1,
          'y': 64,
          'z': 2,
        },
        'evidence': <dynamic>[],
        'actions': <dynamic>[
          <String, dynamic>{
            'id': 'circuit-throttle',
            'label': 'Circuit throttle',
            'status': 'ACTIVE',
            'detail': 'Blocked for 10 seconds.',
            'occurredAtMs': 1200,
          },
        ],
        'context': <String, dynamic>{'nodes': '8'},
      },
    ],
  };

  test('parses atomic score snapshot and explicit availability', () {
    final IncidentStatus status = IncidentStatus.fromJson(fullJson);

    expect(status.score, equals(42.5));
    expect(status.scoreAvailable, isTrue);
    expect(status.sampledAtMs, equals(1234));
    expect(status.contributors, hasLength(2));
    expect(status.contributors[1].available, isFalse);
    expect(status.primaryContributor?.id, equals('tick-ms-p95'));
  });

  test('parses structured cause, location, action, and context', () {
    final IncidentStatus status = IncidentStatus.fromJson(fullJson);
    final IncidentRecord incident = status.incidents.single;

    expect(incident.cause, contains('crossed the threshold'));
    expect(incident.location?.world, equals('world'));
    expect(incident.location?.y, equals(64));
    expect(incident.actions.single.status, equals('ACTIVE'));
    expect(incident.context['nodes'], equals('8'));
  });

  test('missing optional arrays and location default safely', () {
    final IncidentStatus status = IncidentStatus.fromJson(<String, dynamic>{
      'score': 0,
      'state': 'NORMAL',
      'incidents': <dynamic>[
        <String, dynamic>{'title': 'Minimal record'},
      ],
    });

    expect(status.scoreAvailable, isFalse);
    expect(status.contributors, isEmpty);
    expect(status.incidents.single.location, isNull);
    expect(status.incidents.single.actions, isEmpty);
  });

  test('round trips the structured incident payload', () {
    final IncidentStatus status = IncidentStatus.fromJson(fullJson);
    final IncidentStatus roundTrip = IncidentStatus.fromJson(status.toJson());

    expect(roundTrip.score, equals(42.5));
    expect(
      roundTrip.contributors
          .singleWhere((IncidentContributor item) => item.available)
          .display,
      equals('120 ms'),
    );
    expect(roundTrip.incidents.single.title, equals('Circuit throttled'));
    expect(
      roundTrip.incidents.single.actions.single.detail,
      contains('10 seconds'),
    );
  });
}
