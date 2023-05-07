package com.volmit.react.core.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.awt.Color;

public class Guis {
    public static ItemStack generateColorIcon(String title, Color color) {
        ItemStack is = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) is.getItemMeta();
        meta.setColor(org.bukkit.Color.fromRGB(color.getRed(), color.getGreen(), color.getBlue()));
        meta.setDisplayName(title);
        is.setItemMeta(meta);

        return is;
    }
}
