package com.volmit.react.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

public class CustomUIElement extends UIElement{
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

            return is;
        } catch(Throwable e) {
            e.printStackTrace();
        }

        return null;
    }
}
