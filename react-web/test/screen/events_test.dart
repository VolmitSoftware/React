library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/screen/events.dart';
import 'package:react_web/state/connection_manager.dart';

import 'monitor_support.dart';

void main() {
  group('EventsScreen', () {
    testServer('time, handles and listeners as tiles, no repeat chart', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const EventsScreen(),
          snapshot: snapshotOf(<SamplerSample>[
            sample('event-time', 1.5, suffix: 'ms'),
            sample('event-handles-per-tick', 420),
            sample('events-listeners', 96),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(occurrences(body, 'class="ui-metric"'), 3);
      expect(occurrences(body, 'data-chart-card'), 0);
      expect(body, contains(ReactorText.commonEventTime.english));
      expect(body, contains(ReactorText.eventsHandlesPerTick.english));
      expect(body, contains(ReactorText.eventsListeners.english));
      expect(body, contains('420.0'));
    });

    testServer('loads while connecting', (ServerTester tester) async {
      tester.pumpComponent(
        onServer(const EventsScreen(), state: ConnState.connecting),
      );
      expect((await tester.request('/')).body, contains('ui-loading'));
    });
  });
}
