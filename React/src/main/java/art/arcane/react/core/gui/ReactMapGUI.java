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

package art.arcane.react.core.gui;

import art.arcane.react.React;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.util.format.C;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.react.util.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ReactMapGUI {
    private static final int PAGE_JUMP = 5;

    private ReactMapGUI() {
    }

    public static void open(Player player) {
        open(player, 0);
    }

    public static void open(Player player, int page) {
        if (player == null) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            int safePage = page;
            J.s(() -> open(player, safePage));
            return;
        }

        MapController controller = React.controller(MapController.class);
        if (controller == null || controller.getRenderers() == null) {
            player.sendMessage(C.RED + "Renderer controller is unavailable.");
            return;
        }

        List<ReactRenderer> renderers = getRenderers(controller);
        if (renderers.isEmpty()) {
            player.sendMessage(C.RED + "No map renderers are available.");
            return;
        }

        playPageTurn(player);
        PageLayout layout = pageLayout(renderers.size());
        int currentPage = clampPage(page, layout.pageCount());
        int start = currentPage * layout.itemsPerPage();
        int end = Math.min(renderers.size(), start + layout.itemsPerPage());

        UIWindow window = new UIWindow(React.instance, player);
        window.setResolution(WindowResolution.W9_H6);
        window.setViewportHeight(layout.rows());
        window.setTitle("React Maps");
        window.setDecorator(new UIStaticDecorator(new UIElement("bg")
                .setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));

        for (int row = 0; row < layout.contentRows(); row++) {
            int rowStart = start + (row * 9);
            if (rowStart >= end) {
                break;
            }

            int rowCount = Math.min(9, end - rowStart);
            for (int i = 0; i < rowCount; i++) {
                ReactRenderer renderer = renderers.get(rowStart + i);
                int w = centeredPosition(i, rowCount);
                int h = row;
                window.setElement(w, h, new UIElement("map-renderer-" + renderer.getId())
                        .setMaterial(new MaterialBlock(materialFor(renderer)))
                        .setName(C.WHITE + displayName(renderer.getId()))
                        .onLeftClick((e) -> {
                            controller.openRenderer(player, renderer);
                            player.sendMessage(C.GREEN + "Selected map: " + C.WHITE + renderer.getId());
                            player.closeInventory();
                        }));
            }
        }

        if (layout.pagination()) {
            int controlRow = layout.controlRow();
            int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
            int jumpForward = Math.min(layout.pageCount() - 1, currentPage + PAGE_JUMP);

            if (currentPage > 0) {
                window.setElement(2, controlRow, new UIElement("map-prev")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Prev")
                        .onLeftClick((e) -> open(player, currentPage - 1))
                        .onRightClick((e) -> open(player, jumpBack)));
            }

            if (currentPage < layout.pageCount() - 1) {
                window.setElement(4, controlRow, new UIElement("map-next")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Next")
                        .onLeftClick((e) -> open(player, currentPage + 1))
                        .onRightClick((e) -> open(player, jumpForward)));
            }

            int from = start + 1;
            int to = end;
            window.setElement(3, controlRow, new UIElement("map-page-info")
                    .setMaterial(new MaterialBlock(Material.PAPER))
                    .setName(C.AQUA + "Page " + (currentPage + 1) + "/" + layout.pageCount()
                            + C.GRAY + " (" + from + "-" + to + ")"));
        }

        window.open();
    }

    private static List<ReactRenderer> getRenderers(MapController controller) {
        Map<String, ReactRenderer> unique = new LinkedHashMap<>();
        for (ReactRenderer renderer : controller.getRenderers().values()) {
            if (renderer == null || renderer.getId() == null || renderer.getId().isBlank()) {
                continue;
            }
            unique.putIfAbsent(renderer.getId(), renderer);
        }

        List<ReactRenderer> renderers = new ArrayList<>(unique.values());
        renderers.sort(Comparator.comparing(i -> normalizeSortKey(i.getId())));
        return renderers;
    }

    private static Material materialFor(ReactRenderer renderer) {
        if (renderer instanceof Feature feature && feature.getIcon() != null) {
            return feature.getIcon();
        }
        if (renderer instanceof Sampler sampler && sampler.getIcon() != null) {
            return sampler.getIcon();
        }
        return Material.FILLED_MAP;
    }

    private static int clampPage(int page, int pageCount) {
        if (pageCount <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(page, pageCount - 1));
    }

    private static PageLayout pageLayout(int totalEntries) {
        int safeEntries = Math.max(0, totalEntries);
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(Math.max(1, safeEntries) / 9D)));
        int itemsPerPage = rows * 9;
        if (safeEntries <= itemsPerPage) {
            return new PageLayout(rows, rows, itemsPerPage, 1, false, -1);
        }

        int contentRows = 5;
        rows = contentRows + 1;
        itemsPerPage = contentRows * 9;
        int pageCount = Math.max(1, (int) Math.ceil(safeEntries / (double) itemsPerPage));
        return new PageLayout(rows, contentRows, itemsPerPage, pageCount, true, rows - 1);
    }

    private static int centeredPosition(int index, int rowCount) {
        int safeCount = Math.max(1, Math.min(9, rowCount));
        int safeIndex = Math.max(0, Math.min(index, safeCount - 1));
        int start = -(safeCount / 2);
        if ((safeCount & 1) == 1) {
            start = -((safeCount - 1) / 2);
        }
        return start + safeIndex;
    }

    private static String displayName(String key) {
        if (key == null || key.isBlank()) {
            return "Unknown";
        }

        String spaced = key
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        if (spaced.isBlank()) {
            return key;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String normalizeSortKey(String input) {
        return C.stripColor(Objects.toString(input, "")).toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static void playPageTurn(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
        } catch (Throwable ignored) {
        }
    }

    private record PageLayout(
            int rows,
            int contentRows,
            int itemsPerPage,
            int pageCount,
            boolean pagination,
            int controlRow
    ) {
    }
}
