library;

import 'package:test/test.dart';

import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/server_tags_store.dart';

void main() {
  group('ServerTagsStore', () {
    test('setTags deduplicates and sorts tags', () {
      final FleetStorage storage = InMemoryFleetStorage();
      final ServerTagsStore store = ServerTagsStore(storage);

      store.setTags('s1', <String>['prod', 'prod', 'eu']);

      expect(store.tagsFor('s1'), equals(<String>['eu', 'prod']));
    });

    test('tags persist across a new store instance', () {
      final FleetStorage storage = InMemoryFleetStorage();
      final ServerTagsStore store1 = ServerTagsStore(storage);
      store1.setTags('s1', <String>['prod', 'eu']);

      final ServerTagsStore store2 = ServerTagsStore(storage);
      expect(store2.tagsFor('s1'), equals(<String>['eu', 'prod']));
    });

    test('allTags unions tags across servers', () {
      final FleetStorage storage = InMemoryFleetStorage();
      final ServerTagsStore store = ServerTagsStore(storage);
      store.setTags('s1', <String>['prod', 'eu']);
      store.setTags('s2', <String>['staging', 'us']);

      expect(store.allTags(), equals(<String>['eu', 'prod', 'staging', 'us']));
    });

    test('serverIdsWithTag returns servers tagged with given tag', () {
      final FleetStorage storage = InMemoryFleetStorage();
      final ServerTagsStore store = ServerTagsStore(storage);
      store.setTags('s1', <String>['prod', 'eu']);
      store.setTags('s2', <String>['staging']);

      expect(store.serverIdsWithTag('prod'), equals(<String>['s1']));
    });

    test('setTags with empty list removes the entry', () {
      final FleetStorage storage = InMemoryFleetStorage();
      final ServerTagsStore store = ServerTagsStore(storage);
      store.setTags('s1', <String>['prod']);
      store.setTags('s1', <String>[]);

      expect(store.tagsFor('s1'), isEmpty);
      expect(store.allTags(), isEmpty);
    });

    test('tagsFor returns empty list for unknown server', () {
      final FleetStorage storage = InMemoryFleetStorage();
      final ServerTagsStore store = ServerTagsStore(storage);

      expect(store.tagsFor('unknown'), isEmpty);
    });
  });
}
