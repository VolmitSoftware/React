package art.arcane.react.util.project.value;

import org.bukkit.Material;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class MaterialValueMemoTest {
  @Test
  void resolvesEachMaterialOnce() {
    Map<Material, Integer> calls = new EnumMap<>(Material.class);
    MaterialValueMemo memo = new MaterialValueMemo(material -> {
      calls.merge(material, 1, Integer::sum);
      return material.ordinal() * 1.5D;
    });

    for (int i = 0; i < 5; i++) {
      Assertions.assertEquals(Material.DIAMOND.ordinal() * 1.5D, memo.get(Material.DIAMOND));
      Assertions.assertEquals(Material.STICK.ordinal() * 1.5D, memo.get(Material.STICK));
    }

    Assertions.assertEquals(1, calls.get(Material.DIAMOND));
    Assertions.assertEquals(1, calls.get(Material.STICK));
    Assertions.assertEquals(2, calls.size());
  }

  @Test
  void zeroValuesAreMemoised() {
    AtomicInteger calls = new AtomicInteger();
    MaterialValueMemo memo = new MaterialValueMemo(material -> {
      calls.incrementAndGet();
      return 0D;
    });

    Assertions.assertEquals(0D, memo.get(Material.SHORT_GRASS));
    Assertions.assertEquals(0D, memo.get(Material.SHORT_GRASS));
    Assertions.assertEquals(1, calls.get());
  }

  @Test
  void failedResolutionIsRetried() {
    AtomicInteger calls = new AtomicInteger();
    MaterialValueMemo memo = new MaterialValueMemo(material -> {
      if (calls.incrementAndGet() == 1) {
        throw new IllegalStateException("recipes unavailable");
      }
      return 7D;
    });

    Assertions.assertThrows(IllegalStateException.class, () -> memo.get(Material.IRON_INGOT));
    Assertions.assertEquals(7D, memo.get(Material.IRON_INGOT));
    Assertions.assertEquals(7D, memo.get(Material.IRON_INGOT));
    Assertions.assertEquals(2, calls.get());
  }

  @Test
  void notANumberResultsAreNotCached() {
    AtomicInteger calls = new AtomicInteger();
    MaterialValueMemo memo = new MaterialValueMemo(material -> {
      calls.incrementAndGet();
      return Double.NaN;
    });

    Assertions.assertTrue(Double.isNaN(memo.get(Material.STONE)));
    Assertions.assertTrue(Double.isNaN(memo.get(Material.STONE)));
    Assertions.assertEquals(2, calls.get());
  }

  @Test
  void concurrentReadersResolveOnce() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    MaterialValueMemo memo = new MaterialValueMemo(material -> {
      calls.incrementAndGet();
      return 3D;
    });
    int threads = 8;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      List<Future<Double>> results = new ArrayList<>(threads);
      for (int i = 0; i < threads; i++) {
        results.add(executor.submit(() -> {
          start.await();
          double sum = 0;
          for (int j = 0; j < 1000; j++) {
            sum += memo.get(Material.GOLD_INGOT);
          }
          return sum;
        }));
      }
      start.countDown();
      for (Future<Double> result : results) {
        Assertions.assertEquals(3000D, result.get(10, TimeUnit.SECONDS));
      }
    } finally {
      executor.shutdownNow();
    }

    Assertions.assertEquals(1, calls.get());
  }
}
