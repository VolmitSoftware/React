library;

import 'package:arcane_jaspr/arcane_jaspr.dart' show BreadcrumbItem;
import 'package:react_web/app/fleet_observer.dart';
import 'package:react_web/app/nav.dart';
import 'package:react_web/app/paths.dart';
import 'package:react_web/app/shell_nav.dart';
import 'package:react_web/state/connection_manager.dart';
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
        'network',
        'performance',
        'activity',
        'map',
        'profiler',
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

    test('only Network is proxy only', () {
      expect(
        <String>[
          for (final ServerPage page in serverPages)
            if (page.proxyOnly) page.slug,
        ],
        <String>['network'],
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
        kRouteServerNetwork,
        kRouteServerProfiler,
      ];
      for (final ServerPage page in serverPages) {
        expect(routes, contains(page.pathFor(':id')), reason: page.slug);
      }
    });

    test('a page below a page links back to it in the breadcrumbs', () {
      const List<ServerEntry> servers = <ServerEntry>[
        ServerEntry(id: 'a', name: 'Survival', state: ConnState.live),
      ];
      final List<BreadcrumbItem> owner = breadcrumbsFor(
        '/server/a/plugins/Iris',
        servers,
      );
      expect(owner.map((BreadcrumbItem i) => i.href), <String?>[
        '/server/a',
        '/server/a/plugins',
      ]);
      expect(breadcrumbsFor('/server/a/profiler', servers).last.href, isNull);
    });

    test('names the page at an address', () {
      expect(serverPageAt('/server/a')?.slug, '');
      expect(serverPageAt('/server/a/map')?.slug, 'map');
      expect(serverPageAt('/server/a/governors'), isNull);
      expect(serverPageAt('/server/a/plugins/Iris')?.slug, 'plugins');
      expect(serverPageAt('/server/a/profiler/snapshot/x')?.slug, 'profiler');
      expect(pageTitleAt('/alerts'), ReactorText.alertsTitle);
      expect(pageTitleAt('/pair'), ReactorText.pairTitle);
      expect(pageTitleAt('/'), ReactorText.pageHomeTitle);
      expect(pageTitleAt('/server/a/console'), ReactorText.pageConsoleTitle);
      expect(pageTitleAt('/server/a/plugins'), ReactorText.pagePluginsTitle);
      expect(
        pageTitleAt('/server/a/plugins', modded: true),
        ReactorText.pageModsTitle,
      );
    });
  });
}
