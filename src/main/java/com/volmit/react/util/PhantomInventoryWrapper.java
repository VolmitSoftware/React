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

import org.bukkit.inventory.Inventory;

/**
 * Wrapper on top of the bukkit inventory api
 *
 * @author cyberpwn
 */
public interface PhantomInventoryWrapper extends Inventory {
    /**
     * Does the given inventory have any space?
     *
     * @return true if it has space for at least one more slot.
     */
    boolean hasSpace();

    /**
     * Get how many air slots exist (empty slots)
     *
     * @return the number of slots empty (0 if full)
     */
    int getSlotsLeft();
}