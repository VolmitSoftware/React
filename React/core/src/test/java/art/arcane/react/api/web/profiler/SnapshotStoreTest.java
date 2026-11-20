package art.arcane.react.api.web.profiler;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

class SnapshotStoreTest {
  @TempDir
  File folder;

  private final AtomicLong clock = new AtomicLong(1_763_640_000_000L);

  private SnapshotStore store(int retention) {
    return new SnapshotStore(folder, JsonMapper.builder().build(), () -> retention, () -> clock.addAndGet(1000L), ZoneOffset.UTC);
  }

  @Test
  void retentionKeepsTheNewest() throws IOException {
    SnapshotStore store = store(2);
    store.save(SnapshotStore.PROFILE, "one", "Lobby", "", Map.of("n", 1));
    SnapshotStore.Meta second = store.save(SnapshotStore.HEATMAP, "two", "Lobby", "", Map.of("n", 2));
    SnapshotStore.Meta third = store.save(SnapshotStore.PROFILE, "three", "Lobby", "", Map.of("n", 3));
    List<SnapshotStore.Meta> kept = store.list(null);
    Assertions.assertEquals(List.of(third.id(), second.id()), kept.stream().map(SnapshotStore.Meta::id).toList());
    Assertions.assertEquals(List.of(third.id()), store.list(SnapshotStore.PROFILE).stream().map(SnapshotStore.Meta::id).toList());
  }

  @Test
  void survivesANewStoreAndPrunesOnRequest() throws IOException {
    store(5).save(SnapshotStore.PROFILE, "a", "Lobby", "", Map.of());
    store(5).save(SnapshotStore.PROFILE, "b", "Lobby", "", Map.of());
    SnapshotStore reopened = store(1);
    Assertions.assertEquals(2, reopened.list(null).size());
    reopened.prune();
    Assertions.assertEquals("b", reopened.list(null).get(0).label());
    Assertions.assertEquals(1, reopened.list(null).size());
  }

  @Test
  void documentAndExportCarryMetaAndData() throws IOException {
    SnapshotStore store = store(5);
    SnapshotStore.Meta meta = store.save(SnapshotStore.PROFILE, "", "Lobby", "3 samples", Map.of("samples", 3));
    Assertions.assertTrue(meta.bytes() > 0);
    Assertions.assertFalse(meta.label().isBlank());
    String document = new String(store.document(meta.id()).orElseThrow(), StandardCharsets.UTF_8);
    Map<?, ?> parsed = JsonMapper.builder().build().readValue(document, Map.class);
    Assertions.assertEquals(Map.of("samples", 3), parsed.get("data"));
    Assertions.assertEquals(meta.id(), ((Map<?, ?>) parsed.get("meta")).get("id"));
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(store.export(meta.id()).orElseThrow()))) {
      Assertions.assertEquals(document, new String(gzip.readAllBytes(), StandardCharsets.UTF_8));
    }
    Assertions.assertTrue(store.delete(meta.id()));
    Assertions.assertTrue(store.document(meta.id()).isEmpty());
    Assertions.assertFalse(store.delete(meta.id()));
  }

  @Test
  void refusesIdsOutsideTheStore() throws IOException {
    SnapshotStore store = store(5);
    Assertions.assertTrue(store.meta("../web/tokens").isEmpty());
    Assertions.assertFalse(store.delete("../../react.toml"));
    Assertions.assertTrue(store.document("..").isEmpty());
  }

  @Test
  void exportNameUsesKindServerAndTime() {
    SnapshotStore.Meta meta = new SnapshotStore.Meta("x-000000", "profile", "", 1_763_640_000_000L, "My Lobby #1", 1, "");
    Assertions.assertEquals("react-profile-My-Lobby-1-20251120-120000.json.gz", SnapshotStore.exportName(meta, ZoneOffset.UTC));
    SnapshotStore.Meta blank = new SnapshotStore.Meta("x-000000", "heatmap", "", 0L, "///", 1, "");
    Assertions.assertEquals("react-heatmap-server-19700101-000000.json.gz", SnapshotStore.exportName(blank, ZoneOffset.UTC));
  }

  @Test
  void labelsAreTrimmedToTheLimit() throws IOException {
    SnapshotStore.Meta meta = store(5).save(SnapshotStore.PROFILE, "x".repeat(80), "Lobby", "", Map.of());
    Assertions.assertEquals(SnapshotStore.MAX_LABEL, meta.label().length());
  }
}
