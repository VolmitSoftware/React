package art.arcane.react.util.project.world;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BundleUtilsTest {
  @Test
  void compactCombinesSimilarStacksWithoutDroppingOverflow() {
    ItemStack first = mock(ItemStack.class);
    ItemStack second = mock(ItemStack.class);
    ItemStack firstClone = mock(ItemStack.class);
    ItemStack secondClone = mock(ItemStack.class);
    AtomicInteger firstAmount = mutableAmount(firstClone, 40);
    AtomicInteger secondAmount = mutableAmount(secondClone, 40);
    when(first.getAmount()).thenReturn(40);
    when(second.getAmount()).thenReturn(40);
    when(first.clone()).thenReturn(firstClone);
    when(second.clone()).thenReturn(secondClone);
    when(firstClone.isSimilar(secondClone)).thenReturn(true);

    List<ItemStack> compacted = BundleUtils.compact(List.of(first, second));

    assertEquals(2, compacted.size());
    assertSame(firstClone, compacted.get(0));
    assertSame(secondClone, compacted.get(1));
    assertEquals(64, firstAmount.get());
    assertEquals(16, secondAmount.get());
  }

  private AtomicInteger mutableAmount(ItemStack item, int amount) {
    AtomicInteger value = new AtomicInteger(amount);
    when(item.getAmount()).thenAnswer(invocation -> value.get());
    when(item.getMaxStackSize()).thenReturn(64);
    doAnswer(invocation -> {
      value.set(invocation.getArgument(0));
      return null;
    }).when(item).setAmount(anyInt());
    return value;
  }
}
