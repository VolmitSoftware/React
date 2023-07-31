/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.react.core.gui;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.core.controller.SampleController;
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
import java.util.function.Consumer;

public class SamplerGUI {
    public static void pickSampler(Player p, Consumer<Sampler> onPicked, int page) {
        pickSampler(p, onPicked, null, page);
    }

    public static void pickSampler(Player p, Consumer<Sampler> onPicked, List<String> without) {
        pickSampler(p, onPicked, without, 0);
    }

    public static void pickSampler(Player p, Consumer<Sampler> onPicked) {
        pickSampler(p, onPicked, null, 0);
    }

    public static void pickSampler(Player p, Consumer<Sampler> onPicked, List<String> without, int page) {
        if (Bukkit.isPrimaryThread()) {
            throw new RuntimeException("Cannot open gui on main thread");
        }

        J.s(() -> {
            UIWindow window = new UIWindow(p);
            window.setTitle("React Samplers");
            window.setResolution(WindowResolution.W9_H6);
            window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));
            //Sorted Samplers
            List<Sampler> samplers = React.controller(SampleController.class)
                    .getSamplers()
                    .all()
                    .stream()
                    .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                    .toList();
            boolean hasMore = samplers.size() > (9 * 2) * (page + 1);
            int pge = page;
            if (samplers.size() <= pge * (9 * 2)) {
                pge = 0;
                React.warn("Pagination Issue!");
            }

            samplers = samplers.subList(pge * (9 * 2), Math.min(samplers.size(), (pge + 1) * (9 * 2)));

            int rp = 0;
            for (Sampler i : samplers) {
                if (without != null && without.contains(i.getId())) {
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
                            onPicked.accept(i);
                            window.close();
                        })
                );
            }

            int pg = pge;

            if (pg > 0) {
                window.setElement(-4, 2, new UIElement("page-back")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName("Previous Page")
                        .onLeftClick((e) -> {
                            window.close();
                            J.a(() -> pickSampler(p, onPicked, without, pg - 1));
                        })
                );
            }

            if (hasMore) {
                window.setElement(4, 2, new UIElement("page-forward")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName("Next Page")
                        .onLeftClick((e) -> {
                            window.close();
                            J.a(() -> pickSampler(p, onPicked, without, pg + 1));
                        })
                );
            }

            window.open();
        });
    }
}
