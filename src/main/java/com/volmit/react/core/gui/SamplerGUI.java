package com.volmit.react.core.gui;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.J;
import com.volmit.react.util.MaterialBlock;
import com.volmit.react.util.UIElement;
import com.volmit.react.util.UIStaticDecorator;
import com.volmit.react.util.UIWindow;
import com.volmit.react.util.WindowResolution;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SamplerGUI {
    public static Sampler pickSampler(Player p) {
        AtomicBoolean picked = new AtomicBoolean(false);
        AtomicReference<Sampler> result = new AtomicReference<>();

        J.s(() -> {
            UIWindow window = new UIWindow(p);
            window.setTitle("React Samplers");
            window.setResolution(WindowResolution.W9_H6);
            window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.CYAN_STAINED_GLASS_PANE))));

            int rp = 0;
            for(Sampler i : React.instance.getSampleController().getSamplers().values()) {
                int h = window.getRow(rp);
                int w = window.getPosition(rp);
                rp++;
                window.setElement(w, h, new UIElement("sample-" + i.getId())
                    .setMaterial(new MaterialBlock(i.getIcon()))
                    .setName(i.getId())
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

        while(!picked.get()) {
            J.sleep(250);
        }

        return result.get();
    }
}
