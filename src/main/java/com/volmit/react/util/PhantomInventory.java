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

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

public class PhantomInventory implements PhantomInventoryWrapper {
    protected Inventory i;

    public PhantomInventory(Inventory i) {
        this.i = i;
    }

    @Override
    public HashMap<Integer, ItemStack> addItem(ItemStack... arg0) throws IllegalArgumentException {
        return i.addItem(arg0);
    }

    @Override
    public HashMap<Integer, ? extends ItemStack> all(Material arg0) throws IllegalArgumentException {
        return i.all(arg0);
    }

    @Override
    public HashMap<Integer, ? extends ItemStack> all(ItemStack arg0) {
        return i.all(arg0);
    }

    @Override
    public void clear() {
        i.clear();
    }

    @Override
    public void clear(int arg0) {
        i.clear(arg0);
    }

    @Override
    public boolean contains(Material arg0) throws IllegalArgumentException {
        return i.contains(arg0);
    }

    @Override
    public boolean contains(ItemStack arg0) {
        return i.contains(arg0);
    }

    @Override
    public boolean contains(Material arg0, int arg1) throws IllegalArgumentException {
        return i.contains(arg0, arg1);
    }

    @Override
    public boolean contains(ItemStack arg0, int arg1) {
        return i.contains(arg0, arg1);
    }

    @Override
    public boolean containsAtLeast(ItemStack arg0, int arg1) {
        return i.containsAtLeast(arg0, arg1);
    }

    @Override
    public int first(Material arg0) throws IllegalArgumentException {
        return i.first(arg0);
    }

    @Override
    public int first(ItemStack arg0) {
        return i.first(arg0);
    }

    @Override
    public int firstEmpty() {
        return i.firstEmpty();
    }

    @Override
    public boolean isEmpty() {
        return i.isEmpty();
    }

    @Override
    public ItemStack[] getContents() {
        return i.getContents();
    }

    @Override
    public InventoryHolder getHolder() {
        return i.getHolder();
    }

    @Override
    public ItemStack getItem(int arg0) {
        return i.getItem(arg0);
    }

    @Override
    public int getMaxStackSize() {
        return i.getMaxStackSize();
    }

    @Override
    public int getSize() {
        return i.getSize();
    }

    @Override
    public InventoryType getType() {
        return i.getType();
    }

    @Override
    public List<HumanEntity> getViewers() {
        return i.getViewers();
    }

    @Override
    public ListIterator<ItemStack> iterator() {
        return i.iterator();
    }

    @Override
    public ListIterator<ItemStack> iterator(int arg0) {
        return i.iterator(arg0);
    }

    @Override
    public void remove(Material arg0) throws IllegalArgumentException {
        i.remove(arg0);
    }

    @Override
    public void remove(ItemStack arg0) {
        i.remove(arg0);
    }

    @Override
    public HashMap<Integer, ItemStack> removeItem(ItemStack... arg0) throws IllegalArgumentException {
        return i.removeItem(arg0);
    }

    @Override
    public void setContents(ItemStack[] arg0) throws IllegalArgumentException {
        i.setContents(arg0);
    }

    @Override
    public void setItem(int arg0, ItemStack arg1) {
        i.setItem(arg0, arg1);
    }

    @Override
    public void setMaxStackSize(int arg0) {
        i.setMaxStackSize(arg0);
    }

    @Override
    public boolean hasSpace() {
        return firstEmpty() != -1;
    }

    @Override
    public int getSlotsLeft() {
        int x = 0;

        for(ItemStack i : getContents()) {
            if(i == null || i.getType().equals(Material.AIR)) {
                x++;
            }
        }

        return x;
    }

    @Override
    public Location getLocation() {
        return i.getLocation();
    }

    @Override
    public ItemStack[] getStorageContents() {
        return i.getStorageContents();
    }

    @Override
    public void setStorageContents(ItemStack[] arg0) throws IllegalArgumentException {
        i.setStorageContents(arg0);
    }
}