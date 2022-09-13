/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.react.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemStack & Inventory utilities
 *
 * @author cyberpwn
 */
public class Inventories {
    /**
     * Does the inventory have any space (empty slots)
     *
     * @param i
     *     the inventory
     * @return true if it has at least one slot empty
     */
    public static boolean hasSpace(Inventory i) {
        return new PhantomInventory(i).hasSpace();
    }

    /**
     * Does the inventory have a given amount of empty space (or more)
     *
     * @param i
     *     the inventory
     * @param slots
     *     the slots needed empty
     * @return true if it has more than or enough empty slots
     */
    public static boolean hasSpace(Inventory i, int slots) {
        int ex = 0;

        ItemStack[] vv = i.getContents();

        for(int v = 0; v < vv.length; v++) {
            if(vv[v] == null || vv[v].getType().equals(Material.AIR)) {
                ex++;
            }
        }

        return ex >= slots;
    }

    /**
     * Get the ACTUAL contents in this inventory. Meaning no elements in the
     * list are null, or just plain air
     *
     * @param i
     *     the inventory
     * @return the ACTUAL contents
     */
    public static List<ItemStack> getActualContents(Inventory i) {
        List<ItemStack> actualItems = new ArrayList<>();

        for(ItemStack j : i.getContents()) {
            if(Items.is(j)) {
                actualItems.add(j);
            }
        }

        return actualItems;
    }

    /**
     * Does the inventory have space for the given item
     *
     * @param i
     *     the inventory
     * @param item
     *     the item
     * @return returns true if either there is enough empty slots to fill it
     * (amt / maxStackSize) OR the item can be merged with an existing
     * item, else false.
     */
    public static boolean hasSpace(Inventory i, ItemStack item) {
        if(hasSpace(i, item.getAmount() / item.getMaxStackSize())) {
            return true;
        } else {
            for(ItemStack j : getActualContents(i)) {
                if(Items.isMergable(j, item)) {
                    return true;
                }
            }
        }

        return false;
    }
}