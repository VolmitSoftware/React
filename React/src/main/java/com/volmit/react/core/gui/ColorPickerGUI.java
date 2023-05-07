package com.volmit.react.core.gui;

import com.volmit.react.util.inventorygui.CustomUIElement;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.legacyutil.MaterialBlock;
import com.volmit.react.legacyutil.UIElement;
import com.volmit.react.legacyutil.UIStaticDecorator;
import com.volmit.react.legacyutil.UIWindow;
import com.volmit.react.legacyutil.WindowResolution;
import com.volmit.react.util.data.TinyColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ColorPickerGUI {
    public static final List<Color> presetColors = List.of(
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

    public static Color pickColor(Player p) {
        return pickColor(p, null);
    }

    public static Color pickColor(Player p, Color initial) {
        if(Bukkit.isPrimaryThread()) {
            throw new RuntimeException("Cannot open color picker on main thread");
        }

        AtomicBoolean picked = new AtomicBoolean(false);
        AtomicReference<Color> result = new AtomicReference<>(initial);

        J.s(() -> {
            UIWindow window = new UIWindow(p);
            window.setTitle("Color Picker");
            window.setResolution(WindowResolution.W9_H6);
            window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));

            int rp = 0;
            for (Color i : presetColors) {
                int h = window.getRow(rp);
                int w = window.getPosition(rp);
                rp++;
                window.setElement(w, h, new CustomUIElement("color-" + i.getRGB(), Guis.generateColorIcon("#" + Integer.toHexString(i.getRGB()).substring(2), i))
                    .onLeftClick((e) -> {
                        result.set(i);
                        picked.set(true);
                        window.close();
                    })
                );
            }

            AtomicBoolean refresh = new AtomicBoolean(false);

            window.setElement(0, 2, new UIElement("custom")
                .setName("Custom Color")
                .addLore("* Left Click to pick a custom color")
                .setMaterial(new MaterialBlock(Material.WRITABLE_BOOK))
                .onLeftClick((e) ->{
                    refresh.set(true);
                    J.a(() -> {
                        pickCustomColor(p, picked, result);
                    });
                })
            );

            window.open();
            window.onClosed((w) -> {
                if(!refresh.get()) {
                    picked.set(true);
                }
            });
        });

        while (!picked.get()) {
            J.sleep(250);
        }

        return result.get();
    }

    public static void pickCustomColor(Player p, AtomicBoolean picked, AtomicReference<Color> result) {
        if(Bukkit.isPrimaryThread()) {
            throw new RuntimeException("Cannot open color picker on main thread");
        }

        AtomicBoolean refresh = new AtomicBoolean(false);
        J.s(() -> {
            if(result.get() == null) {
                result.set(new Color(0xBB6666));
            }
            UIWindow window = new UIWindow(p);
            window.setTitle("Color Picker");
            window.setResolution(WindowResolution.W3_H3);
            window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));

            window.setElement(0, 1, new CustomUIElement("buf", Guis.generateColorIcon("#" + Integer.toHexString(result.get().getRGB())
                .substring(2), result.get()))
                    .setName("Current Color")
                    .addLore("* Left Click to use this color")
                    .addLore("* You can also just hit escape to use this color")
                .onLeftClick((e) -> {
                    refresh.set(true);
                    picked.set(true);
                    window.close();
                })
            );

            window.setElement(-1, 1, new CustomUIElement("darken",
                Guis.generateColorIcon("Darken", new TinyColor(result.get()).darken(15).getColor()))
                    .setName("Darken")
                    .addLore("Brightness: " + (Math.round(new TinyColor(result.get()).getBrightness() * 100)) + "%")
                .addLore("* Left Click to darken 5%")
                .addLore("* Shift Left Click to darken 15%")
                .onLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).darken(5).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                })
                .onShiftLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).darken(15).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                }));

            window.setElement(1, 1, new CustomUIElement("brighten",
                Guis.generateColorIcon("Brighten", new TinyColor(result.get()).brighten(15).getColor()))
                .setName("Brighten")
                .addLore("Brightness: " + (Math.round(new TinyColor(result.get()).getBrightness() * 100)) + "%")
                .addLore("* Left Click to brighten 5%")
                .addLore("* Shift Left Click to brighten 15%")
                .onLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).brighten(5).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                })
                .onShiftLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).brighten(15).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                }));

            window.setElement(1, 0, new CustomUIElement("saturate",
                Guis.generateColorIcon("Saturate", new TinyColor(result.get()).saturate(15).getColor()))
                .setName("Saturate")
                .addLore("Saturation: " + (Math.round(new TinyColor(result.get()).getSaturation() * 100)) + "%")
                .addLore("* Left Click to saturate 5%")
                .addLore("* Shift Left Click to saturate 15%")
                .onLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).saturate(5).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                })
                .onShiftLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).saturate(15).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                }));

            window.setElement(-1, 0, new CustomUIElement("desaturate",
                Guis.generateColorIcon("Desaturate", new TinyColor(result.get()).desaturate(15).getColor()))
                .setName("Desaturate")
                .addLore("Saturation: " + (Math.round(new TinyColor(result.get()).getSaturation() * 100)) + "%")
                .addLore("* Left Click to desaturate 5%")
                .addLore("* Shift Left Click to desaturate 15%")
                .onLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).desaturate(5).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                })
                .onShiftLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).desaturate(15).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                }));

            window.setElement(-1, 2, new CustomUIElement("spinleft",
                Guis.generateColorIcon("Spin Left", new TinyColor(result.get()).spin(-15).getColor()))
                .setName("Spin Left")
                .addLore("* Left Click to spin left 5 deg")
                .addLore("* Shift Left Click to spin left 15 deg")
                .onLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).spin(-5).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                })
                .onShiftLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).spin(-15).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                }));

            window.setElement(1, 2, new CustomUIElement("spinright",
                Guis.generateColorIcon("Spin Right", new TinyColor(result.get()).spin(15).getColor()))
                .setName("Spin Right")
                .addLore("* Left Click to spin right 5 deg")
                .addLore("* Shift Left Click to spin right 15 deg")
                .onLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).spin(5).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                })
                .onShiftLeftClick((e) -> {
                    refresh.set(true);
                    result.set(new TinyColor(result.get()).spin(15).getColor());
                    J.a(() -> pickCustomColor(p, picked, result));
                }));

            window.setElement(0, 2, new UIElement("hexcode")
                .setName("Enter Hex Code")
                    .setMaterial(new MaterialBlock(Material.WRITABLE_BOOK))
                .addLore("* Left Click to enter a hex code")
                .onLeftClick((e) -> {
                    refresh.set(true);

                    J.a(() -> {
                        p.sendMessage("<Enter a hex code with or without the # in chat>");
                        String c = TextInputGui.captureText(p);
                        if(c != null) {
                            result.set(new TinyColor(c).getColor());
                        }
                        J.a(() -> pickCustomColor(p, picked, result));
                    });
                }));

            window.open();
            window.onClosed((w) -> {
                if(!refresh.get()) {
                    picked.set(true);
                }
            });
        });
    }
}
