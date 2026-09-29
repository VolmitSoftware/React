package art.arcane.react.util.project.world;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class BundleUtilsFlagTest {
  @Test
  void flaggedBundleReadsItsMetaOnce() {
    ItemStack bundle = Mockito.mock(ItemStack.class);
    BundleMeta meta = Mockito.mock(BundleMeta.class);
    Mockito.when(bundle.getType()).thenReturn(Material.BUNDLE);
    Mockito.when(bundle.getItemMeta()).thenReturn(meta);
    Mockito.when(meta.getLore()).thenReturn(List.of("REACT SUPER STACK"));

    Assertions.assertTrue(BundleUtils.isFlagged(bundle));

    Mockito.verify(bundle, Mockito.times(1)).getItemMeta();
    Mockito.verify(meta, Mockito.times(1)).getLore();
  }

  @Test
  void nonBundleItemsNeverCloneTheirMeta() {
    ItemStack sword = Mockito.mock(ItemStack.class);
    Mockito.when(sword.getType()).thenReturn(Material.DIAMOND_SWORD);

    Assertions.assertFalse(BundleUtils.isFlagged(sword));

    Mockito.verify(sword, Mockito.never()).getItemMeta();
  }

  @Test
  void playerBundlesWithoutTheReactLoreStayUnflagged() {
    ItemStack bundle = Mockito.mock(ItemStack.class);
    BundleMeta meta = Mockito.mock(BundleMeta.class);
    Mockito.when(bundle.getType()).thenReturn(Material.BUNDLE);
    Mockito.when(bundle.getItemMeta()).thenReturn(meta);
    Mockito.when(meta.getLore()).thenReturn(List.of("Tools"));

    Assertions.assertFalse(BundleUtils.isFlagged(bundle));
    Mockito.verify(bundle, Mockito.times(1)).getItemMeta();
  }
}
