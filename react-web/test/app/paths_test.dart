library;

import 'package:react_web/app/nav.dart';
import 'package:react_web/app/paths.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:test/test.dart';

void main() {
  group('server paths', () {
    test('serverPath leaves Overview bare', () {
      expect(serverPath('abc'), '/server/abc');
      expect(serverPath('abc', 'tuning'), '/server/abc/tuning');
    });

    test('serverIdOf and serverPageOf read the address', () {
      expect(serverIdOf('/server/abc/console?x=1'), 'abc');
      expect(serverIdOf('/fleet'), isNull);
      expect(serverPageOf('/server/abc'), '');
      expect(serverPageOf('/server/abc/performance?tab=memory'), 'performance');
    });

    test('switching servers keeps the page and its tab', () {
      expect(
        switchServerPath('/server/a/performance?tab=memory', 'b'),
        '/server/b/performance?tab=memory',
      );
      expect(switchServerPath('/fleet', 'b'), '/server/b');
    });
  });

  group('nav', () {
    test('groups the server pages as Monitor, Operate, Configure', () {
      List<String> slugs(NavGroup group) => <String>[
        for (final ServerPage page in serverPages)
          if (page.group == group) page.slug,
      ];
      expect(slugs(NavGroup.monitor), <String>[
        '',
        'performance',
        'activity',
        'map',
        'incidents',
        'metrics',
      ]);
      expect(slugs(NavGroup.operate), <String>['console', 'actions']);
      expect(slugs(NavGroup.configure), <String>['tuning', 'plugins']);
    });

    test('only Metrics is Advanced only', () {
      expect(
        <String>[
          for (final ServerPage page in serverPages)
            if (page.advanced) page.slug,
        ],
        <String>['metrics'],
      );
    });

    test('every server page has a route', () {
      const List<String> routes = <String>[
        kRouteServer,
        kRouteServerPerformance,
        kRouteServerActivity,
        kRouteServerMap,
        kRouteServerIncidents,
        kRouteServerConsole,
        kRouteServerActions,
        kRouteServerTuning,
        kRouteServerPlugins,
        kRouteServerMetrics,
      ];
      for (final ServerPage page in serverPages) {
        expect(routes, contains(page.pathFor(':id')), reason: page.slug);
      }
    });

    test('names the page at an address', () {
      expect(serverPageAt('/server/a')?.slug, '');
      expect(serverPageAt('/server/a/map')?.slug, 'map');
      expect(serverPageAt('/server/a/governors'), isNull);
      expect(pageTitleAt('/alerts'), ReactorText.alertsTitle);
      expect(pageTitleAt('/pair'), ReactorText.pairTitle);
      expect(pageTitleAt('/'), ReactorText.pageHomeTitle);
      expect(pageTitleAt('/server/a/console'), ReactorText.pageConsoleTitle);
    });
  });
}
