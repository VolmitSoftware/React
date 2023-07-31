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

package com.volmit.react.util.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;

public class BundleUtils {
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
        if (items.stream().map(ItemStack::getType).distinct().count() <= items.size()) {
            Inventory inv = Bukkit.createInventory(null, 54);

            for (ItemStack i : items) {
                inv.addItem(i);
            }

            List<ItemStack> l = new ArrayList<>();

            for (ItemStack i : inv.getContents()) {
                if (i != null) {
                    l.add(i);
                }
            }
        }

        return items;
    }

    public static boolean isFlagged(ItemStack item) {
        return item.getItemMeta().getLore() != null
                && item.getItemMeta().getLore().size() == 1
                && item.getItemMeta().getLore().get(0).equals("REACT SUPER STACK");
    }

    public static ItemStack merge(ItemStack item, ItemStack into, int maxBundle) {
        List<ItemStack> items = new ArrayList<>();
        items.addAll(explode(item));
        items.addAll(explode(into));

        if (getTotalCount(items) > maxBundle || (items.stream().map(ItemStack::getType).distinct().count() <= 1 && getTotalCount(items) <= 64)) {
            return null;
        }

        ItemStack is = new ItemStack(Material.BUNDLE);
        BundleMeta bm = (BundleMeta) is.getItemMeta();
        try {
            bm.setItems(compact(items));
        } catch (Throwable e) {
            return null;
        }
        bm.setLore(List.of("REACT SUPER STACK"));
        is.setItemMeta(bm);
        return is;
    }
}
