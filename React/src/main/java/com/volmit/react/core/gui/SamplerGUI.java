package com.volmit.react.core.gui;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.data.MaterialBlock;
import com.volmit.react.util.inventorygui.UIElement;
import com.volmit.react.util.inventorygui.UIStaticDecorator;
import com.volmit.react.util.inventorygui.UIWindow;
import com.volmit.react.util.inventorygui.WindowResolution;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SamplerGUI {
    public static Sampler pickSampler(Player p) {
        return pickSampler(p, null);
    }

    public static Sampler pickSampler(Player p, List<String> without) {
        if(Bukkit.isPrimaryThread()) {
            throw new RuntimeException("Cannot open gui on main thread");
        }

        AtomicBoolean picked = new AtomicBoolean(false);
        AtomicReference<Sampler> result = new AtomicReference<>();

        J.s(() -> {
            UIWindow window = new UIWindow(p);
            window.setTitle("React Samplers");
            window.setResolution(WindowResolution.W9_H6);
            window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));
            //Sorted Samplers
            List<Sampler> samplers = React.instance.getSampleController().getSamplers().values().stream().sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId())).toList();

            int rp = 0;
            for (Sampler i : samplers) {
                if(without != null && without.contains(i.getId())) {
                    continue;
                }

                int h = window.getRow(rp);
                int w = window.getPosition(rp);
                rp++;
                window.setElement(w, h, new UIElement("sample-" + i.getId())
                        .setMaterial(new MaterialBlock(i.getIcon()))
                        .setName(i.getName())
                        .addLore(i.format(i.sample()))
                        .onLeftClick((e) -> {
                            result.set(i);
                            picked.set(true);
                            window.close();
                        })
                );
            }

            window.open();
            window.onClosed((w) -> picked.set(true));
        });

        while (!picked.get()) {
            J.sleep(250);
        }

        return result.get();
    }
}
