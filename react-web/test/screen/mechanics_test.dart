library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/screen/mechanics.dart';
import 'package:react_web/state/connection_manager.dart';

import 'monitor_support.dart';

void main() {
  group('MechanicsScreen', () {
    testServer('redstone, hoppers, physics and savings in sections', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const MechanicsScreen(),
          snapshot: snapshotOf(<SamplerSample>[
            sample('redstone', 310),
            sample('redstone-event-span', 0.6, suffix: 'ms'),
            sample('hopper', 120),
            sample('physics', 40),
            sample('fluid', 15),
            sample('crop-fast-forward', 8),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;

      // The span is a tile; a chart of it would repeat the tile.
      expect(occurrences(body, 'data-chart-card'), 0);
      expect(occurrences(body, 'class="ui-section"'), 4);
      for (final String label in <String>[
        ReactorText.commonRedstone.english,
        ReactorText.mechanicsBurstRate.english,
        ReactorText.mechanicsRedstoneEventSpan.english,
        ReactorText.commonHoppers.english,
        ReactorText.mechanicsPhysicsFluids.english,
        ReactorText.commonOptimizations.english,
        ReactorText.mechanicsCropFastForward.english,
      ]) {
        expect(body, contains(label));
      }
      expect(body, contains('310.0'));
    });

    testServer('loads while connecting', (ServerTester tester) async {
      tester.pumpComponent(
        onServer(const MechanicsScreen(), state: ConnState.connecting),
      );
      expect((await tester.request('/')).body, contains('ui-loading'));
    });
  });
}
