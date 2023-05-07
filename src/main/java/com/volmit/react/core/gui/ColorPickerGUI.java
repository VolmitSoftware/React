package com.volmit.react.core.gui;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.CustomUIElement;
import com.volmit.react.util.J;
import com.volmit.react.util.MaterialBlock;
import com.volmit.react.util.UIElement;
import com.volmit.react.util.UIStaticDecorator;
import com.volmit.react.util.UIWindow;
import com.volmit.react.util.WindowResolution;
import net.minecraft.world.item.DyeableArmorItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ColorPickerGUI {
    private static final List<Color> presetColors = List.of(
        new Color(0x48F06F),
        new Color(0xF0C842),
        new Color(0x4158F0),
        new Color(0xF04857),
        new Color(0xBDF041),
        new Color(0xF08839),
        new Color(0x3ADBF0),
        new Color(0xBF41F0),
        new Color(0x48F06F),

        new Color(0x8049F0),
        new Color(0x3AF0BD),
        new Color(0xF0633A),
        new Color(0xF0E641),
        new Color(0xF03AD2),
        new Color(0x48A0F0),
        new Color(0x7EF05A),
        new Color(0xF0AD41),
        new Color(0xF05F8D)
    );

    private static ItemStack generateColorIcon(Color color) {
        ItemStack is = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) is.getItemMeta();
        meta.setColor(org.bukkit.Color.fromRGB(color.getRed(), color.getGreen(), color.getBlue()));
        meta.setDisplayName("#" + Integer.toHexString(color.getRGB()).substring(2));
        is.setItemMeta(meta);

        return is;
    }

    public static Color pickColor(Player p) {
        AtomicBoolean picked = new AtomicBoolean(false);
        AtomicReference<Color> result = new AtomicReference<>();

        J.s(() -> {
            UIWindow window = new UIWindow(p);
            window.setTitle("Color Picker");
            window.setResolution(WindowResolution.W9_H6);
            window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.CYAN_STAINED_GLASS_PANE))));

            int rp = 0;
            for(Color i : presetColors) {
                int h = window.getRow(rp);
                int w = window.getPosition(rp);
                rp++;
                window.setElement(w, h, new CustomUIElement("color-" + i.getRGB(), generateColorIcon(i))
                    .onLeftClick((e) -> {
                        result.set(i);
                        picked.set(true);
                        window.close();
                    })
                );
            }

            window.open();
            window.onClosed((w) ->  picked.set(true));
        });

        while(!picked.get()) {
            J.sleep(250);
        }

        return result.get();
    }
}
