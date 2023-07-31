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

package com.volmit.react.util.inventorygui;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CustomUIElement extends UIElement {
    private final ItemStack itemStack;

    public CustomUIElement(String id, ItemStack itemStack) {
        super(id);
        this.itemStack = itemStack;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ItemStack computeItemStack() {
        try {
            ItemStack is = itemStack.clone();
            is.setAmount(getCount());
            ItemMeta meta = is.getItemMeta();
            meta.setDisplayName(getName());
            meta.setLore(getLore());
            is.setItemMeta(meta);

            return is;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }
}
