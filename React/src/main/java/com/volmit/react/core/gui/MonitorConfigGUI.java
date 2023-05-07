package com.volmit.react.core.gui;

import com.volmit.react.React;
import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.api.monitor.configuration.MonitorGroup;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.legacyutil.CustomUIElement;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.legacyutil.MaterialBlock;
import com.volmit.react.legacyutil.UIElement;
import com.volmit.react.legacyutil.UIStaticDecorator;
import com.volmit.react.legacyutil.UIWindow;
import com.volmit.react.legacyutil.WindowResolution;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MonitorConfigGUI {
    public static void editMonitorConfigurationGroup(Player p, MonitorConfiguration configuration, MonitorGroup group, Consumer<MonitorConfiguration> saver) {
        if(Bukkit.isPrimaryThread()) {
            throw new RuntimeException("Cannot open gui on main thread");
        }

        J.s(() -> {
            group.setSamplers(new ArrayList<>(group.getSamplers()));
            UIWindow window = new UIWindow(p);
            window.setTitle(group.getName() + " Group");
            window.setResolution(WindowResolution.W9_H6);
            window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));
            AtomicBoolean refresh = new AtomicBoolean(false);
            int rp = 0;
            String head = group.getHeadOrSomething();

            for (String ii : group.getSamplers()) {
                int h = window.getRow(rp);
                int w = window.getPosition(rp);
                rp++;
                Sampler i = React.instance.getSampleController().getSampler(ii);
                window.setElement(w, h, new UIElement("sample-" + i.getId())
                    .setMaterial(new MaterialBlock(i.getIcon()))
                    .setName(i.getId())
                    .addLore(i.format(i.sample()))
                    .setEnchanted(head.equals(ii))
                    .addLore("* Left Click to set as header")
                    .addLore("* Shift + Right Click to remove")
                    .onLeftClick((e) -> {
                        group.setHeadSampler(i.getId());
                        saver.accept(configuration);
                        refresh.set(true);
                        J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
                    })
                    .onShiftRightClick((e) -> {
                        group.getSamplers().remove(i.getId());

                        if(head.equals(ii)) {
                            group.setHeadSampler(null);
                        }

                        refresh.set(true);
                        saver.accept(configuration);
                        J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
                    })
                );
            }

            window.setElement(0, 2, new UIElement("addnew")
                .setName("Add Sampler")
                .addLore("* Left Click to add a new sampler")
                .setMaterial(new MaterialBlock(Material.EMERALD))
                .onLeftClick((e) ->{
                    refresh.set(true);
                    J.a(() -> {
                        Sampler s = SamplerGUI.pickSampler(p, new ArrayList<>(group.getSamplers()));

                        if(s != null) {
                            group.getSamplers().add(s.getId());
                            saver.accept(configuration);
                            refresh.set(true);
                            J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
                        }
                    });
                })
            );

            window.setElement(-1, 2, new UIElement("renamegroup")
                .setName("Rename Group")
                .addLore("* Left Click to rename the group. Simply send a chat message of the new name!")
                .setMaterial(new MaterialBlock(Material.WRITABLE_BOOK))
                .onLeftClick((e) ->{
                    refresh.set(true);
                    J.s(() -> {
                        window.close();

                        J.a(() -> {
                            p.sendMessage("<Enter the new name for the group in chat>");
                            String n = TextInputGui.captureText(p);

                            if(n != null) {
                                group.setName(n);
                                saver.accept(configuration);
                                J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
                            }
                        });
                    });
                })
            );

            window.setElement(-2, 2, new CustomUIElement("recolorgroup", Guis.generateColorIcon("Group Color", new Color(group.getColorValue())))
                .setName("Group Color")
                .addLore("* Left Click to set the group color")
                .onLeftClick((e) ->{
                    refresh.set(true);
                    J.a(() -> {
                        Color s = ColorPickerGUI.pickColor(p, new Color(group.getColorValue()));

                        if(s != null) {
                            group.setColor("#" + Integer.toHexString(s.getRGB()).substring(2));
                            saver.accept(configuration);
                            refresh.set(true);
                            J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
                        }
                    });
                })
            );

            window.setElement(1, 2, new UIElement("deletegroup")
                .setName("Delete Group")
                .addLore("* Shift Left Click to delete")
                .setMaterial(new MaterialBlock(Material.BARRIER))
                .onShiftLeftClick((e) ->{
                    refresh.set(true);
                    configuration.getGroups().remove(group);
                    saver.accept(configuration);
                    J.a(() -> editMonitorConfiguration(p, configuration, saver));
                })
            );

            window.open();
            window.onClosed((w) -> {
                saver.accept(configuration);
                if(!refresh.get()) {
                    J.a(() -> editMonitorConfiguration(p, configuration, saver));
                }
            });
        });
    }

    public static void editMonitorConfiguration(Player p, MonitorConfiguration configuration, Consumer<MonitorConfiguration> saver) {
        if(Bukkit.isPrimaryThread()) {
            throw new RuntimeException("Cannot open gui on main thread");
        }

        J.s(() -> {
            UIWindow window = new UIWindow(p);
            window.setTitle("Monitor Configuration");
            window.setResolution(WindowResolution.W9_H6);
            window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));

            int rp = 0;
            for (MonitorGroup i : configuration.getGroups()) {
                int h = window.getRow(rp);
                int w = window.getPosition(rp);
                rp++;
                UIElement ee = new CustomUIElement("group-" + i.getName(), Guis.generateColorIcon(i.getName(), new Color(i.getColorValue())))
                    .setName(i.getName());

                ee.addLore("[" + i.getHeadOrSomething() + "]");

                for(String j : i.getSamplers()) {
                    ee.addLore(j);
                }

                    window.setElement(w, h, ee
                    .onLeftClick((e) -> J.a(() -> editMonitorConfigurationGroup(p, configuration, i, saver)))
                );
            }

            window.setElement(0, 2, new UIElement("creategroup")
                .setName("Create Group")
                .addLore("* Left Click to create a group")
                .setMaterial(new MaterialBlock(Material.EMERALD))
                .onLeftClick((e) ->{
                    J.a(() -> {
                        J.s(window::close);
                        p.sendMessage("<Enter a name for this monitor group in chat>");
                        String name = TextInputGui.captureText(p);
                        Color c = ColorPickerGUI.presetColors.get(new Random().nextInt(ColorPickerGUI.presetColors.size()));
                        if(name != null){
                            MonitorGroup g = MonitorGroup.builder()
                                .name(name)
                                .color("#" + Integer.toHexString(c.getRGB()).substring(2))
                                .build();
                            configuration.getGroups().add(g);
                            saver.accept(configuration);
                            J.a(() -> editMonitorConfigurationGroup(p, configuration, g, saver));
                        }

                        else {
                            p.sendMessage("Invalid Name?");
                            J.a(() -> editMonitorConfiguration(p, configuration, saver));
                        }
                    });
                })
            );


            window.open();
            window.onClosed((w) -> {
                saver.accept(configuration);
            });
        });
    }
}
