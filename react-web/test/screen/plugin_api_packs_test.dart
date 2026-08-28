library;

import 'package:jaspr_router/jaspr_router.dart';
import 'package:test/test.dart';

import 'package:react_web/app/reactor_app.dart';

void main() {
  group('Plugin API pack route wiring', () {
    test('uses the per-server System path', () {
      expect(kRouteServerPluginApi, equals('/server/:id/plugin-api-packs'));
    });

    test('is present in the application route table', () {
      final List<RouteBase> routes = buildReactorRoutes();
      expect(
        routes.any(
          (RouteBase route) =>
              route is Route && route.path == kRouteServerPluginApi,
        ),
        isTrue,
      );
    });
  });
}
