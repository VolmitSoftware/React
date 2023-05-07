package com.volmit.react.legacyutil;

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
