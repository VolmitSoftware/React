/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.util.project.world;

import art.arcane.react.React;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BundleUtils {
  private static final Set<String> REPORTED_BUNDLE_FAILURES = ConcurrentHashMap.newKeySet();

  public static boolean isBundle(ItemStack i) {
    return i.getType().equals(Material.BUNDLE);
  }

  public static List<ItemStack> explode(ItemStack bundle) {
    if (!isBundle(bundle)) {
      return new ArrayList<>(List.of(bundle));
    }

    return ((BundleMeta) bundle.getItemMeta()).getItems();
  }

  public static int getTotalCount(List<ItemStack> items) {
    int total = 0;

    for (ItemStack i : items) {
      total += i.getAmount();
    }

    return total;
  }

  public static List<ItemStack> compact(List<ItemStack> items) {
    List<ItemStack> compacted = new ArrayList<>(items.size());
    for (ItemStack item : items) {
      if (item == null || item.getAmount() <= 0) {
        continue;
      }

      ItemStack remaining = item.clone();
      for (ItemStack existing : compacted) {
        if (!existing.isSimilar(remaining) || existing.getAmount() >= existing.getMaxStackSize()) {
          continue;
        }

        int transferred = Math.min(remaining.getAmount(), existing.getMaxStackSize() - existing.getAmount());
        existing.setAmount(existing.getAmount() + transferred);
        remaining.setAmount(remaining.getAmount() - transferred);
        if (remaining.getAmount() == 0) {
          break;
        }
      }

      if (remaining.getAmount() > 0) {
        compacted.add(remaining);
      }
    }

    return compacted;
  }

  public static boolean isFlagged(ItemStack item) {

    return item.getItemMeta() != null
        && item.getItemMeta().getLore() != null
        && item.getItemMeta().getLore().size() == 1
        && item.getItemMeta().getLore().get(0).equals("REACT SUPER STACK");
  }

  public static ItemStack createBundle(List<ItemStack> items) {
    if (items == null || items.isEmpty()) {
      return null;
    }

    ItemStack bundle = new ItemStack(Material.BUNDLE);
    if (!(bundle.getItemMeta() instanceof BundleMeta bundleMeta)) {
      return null;
    }

    try {
      bundleMeta.setItems(compact(items));
    } catch (RuntimeException exception) {
      String failureKey = exception.getClass().getName() + ":" + exception.getMessage();
      if (REPORTED_BUNDLE_FAILURES.add(failureKey)) {
        React.warn("Could not create a React item bundle; bundle creation was skipped to preserve its contents.");
        exception.printStackTrace();
      }
      return null;
    }
    bundleMeta.setLore(List.of("REACT SUPER STACK"));
    bundle.setItemMeta(bundleMeta);
    return bundle;
  }

  public static ItemStack merge(ItemStack item, ItemStack into, int maxBundle) {
    List<ItemStack> items = new ArrayList<>();
    items.addAll(explode(item));
    items.addAll(explode(into));

    if (getTotalCount(items) > maxBundle || (items.stream().map(ItemStack::getType).distinct().count() <= 1 && getTotalCount(items) <= 64)) {
      return null;
    }

    return createBundle(items);
  }
}
