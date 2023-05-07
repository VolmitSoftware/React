package com.volmit.react.util;

import org.bukkit.inventory.ItemStack;

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

            return is;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }
}
