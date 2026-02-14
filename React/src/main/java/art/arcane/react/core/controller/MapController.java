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

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.rendering.RendererAdaptMetrics;
import art.arcane.react.api.rendering.RendererIrisMetrics;
import art.arcane.react.api.rendering.MapRendererPipe;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererUnknown;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.feature.FeatureUnknown;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.volmlib.util.io.JarScanner;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.scheduling.J;
import art.arcane.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class MapController extends TickedObject implements IController, Listener {
    private static final NamespacedKey nsReact = new NamespacedKey(React.instance, "react");
    private static final NamespacedKey nsRenderer = new NamespacedKey(React.instance, "react-renderer");
    private transient Map<String, ReactRenderer> renderers;
    private transient ReactRenderer irisMetricsRenderer;
    private transient ReactRenderer adaptMetricsRenderer;

    public MapController() {
        super("react", "map", 1000);
    }

    public void updateMapView(MapView view, ReactRenderer newRenderer) {
        for (MapRenderer i : view.getRenderers()) {
            view.removeRenderer(i);
        }
        view.addRenderer(new MapRendererPipe(newRenderer));
    }

    public MapView createView(World world, ReactRenderer renderer) {
        MapView view = Bukkit.createMap(world);
        for (MapRenderer i : view.getRenderers()) {
            view.removeRenderer(i);
        }
        view.addRenderer(new MapRendererPipe(renderer));
        return view;
    }

    public ReactRenderer getRenderer(ItemStack item) {
        if (renderers == null) {
            return null;
        }

        ReactRenderer unknown = renderers.get(FeatureUnknown.ID);
        if (isReactMap(item)) {
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta == null) {
                return unknown;
            }

            String rendererId = meta.getPersistentDataContainer().getOrDefault(nsRenderer, PersistentDataType.STRING, FeatureUnknown.ID);
            return renderers.getOrDefault(rendererId, unknown);
        }

        return unknown;
    }

    public void setRenderer(Player player, ReactRenderer renderer) {
        if (hasReactMap(player)) {
            setRenderer(getReactMap(player), renderer, player.getWorld());
        }
    }

    public void setRenderer(ItemStack map, ReactRenderer renderer) {
        setRenderer(map, renderer, null);
    }

    private void setRenderer(ItemStack map, ReactRenderer renderer, World worldHint) {
        if (renderer == null || !isReactMap(map)) {
            return;
        }

        MapMeta meta = (MapMeta) map.getItemMeta();
        if (meta == null) {
            return;
        }

        MapView view = meta.getMapView();
        if (view != null && view.getWorld() != null) {
            updateMapView(view, renderer);
        } else {
            World world = worldHint;
            if (world == null && !Bukkit.getWorlds().isEmpty()) {
                world = Bukkit.getWorlds().get(0);
            }
            if (world != null) {
                meta.setMapView(createView(world, renderer));
            }
        }

        applyRendererMetadata(meta, renderer);
        map.setItemMeta(meta);
    }

    public boolean hasReactMap(Player player) {
        return getReactMap(player) != null;
    }

    public ItemStack getReactMap(Player player) {
        if (isReactMap(player.getInventory().getItemInMainHand())) {
            return player.getInventory().getItemInMainHand();
        }

        if (isReactMap(player.getInventory().getItemInOffHand())) {
            return player.getInventory().getItemInOffHand();
        }

        for (ItemStack i : player.getInventory().getContents()) {
            if (isReactMap(i)) {
                return i;
            }
        }

        return null;
    }

    public void switchToMap(Player player) {
        if (hasReactMap(player)) {
            if (!isReactMap(player.getInventory().getItemInMainHand())) {
                ItemStack is = getReactMap(player);
                player.getInventory().remove(getReactMap(player));

                if (!player.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
                    ItemStack iss = player.getInventory().getItemInMainHand();
                    player.getInventory().setItemInMainHand(is);

                    for (ItemStack i : player.getInventory().addItem(iss).values()) {
                        player.getWorld().dropItem(player.getLocation(), i);
                    }
                } else {
                    player.getInventory().setItemInMainHand(is);
                }
            }
        }
    }

    public void openRenderer(Player player, ReactRenderer renderer) {
        if (hasReactMap(player)) {
            switchToMap(player);
            setRenderer(player, renderer);
        } else {
            giveMap(player, renderer);
        }
    }

    public void giveMap(Player player, ReactRenderer renderer) {
        if (!player.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
            for (ItemStack i : player.getInventory().addItem(player.getInventory().getItemInMainHand()).values()) {
                player.getWorld().dropItem(player.getLocation(), i);
            }

            player.getInventory().setItemInMainHand(null);
        }

        player.getInventory().setItemInMainHand(createMap(player.getWorld(), renderer));
    }

    public boolean isReactMap(ItemStack item) {
        if (item == null || !item.getType().equals(Material.FILLED_MAP)) {
            return false;
        }

        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().getOrDefault(nsReact, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    public void updateMapViews(Player player, boolean force) {
        updateMapViews(player, player.getWorld(), force);
    }

    public void updateMapViews(Player player, World world, boolean force) {
        ItemStack[] is = player.getInventory().getContents();
        boolean updated = false;
        for (int i = 0; i < is.length; i++) {
            ItemStack item = is[i];

            if (item == null) {
                continue;
            }

            if (isReactMap(item)) {
                MapMeta meta = (MapMeta) item.getItemMeta();
                if (meta == null) {
                    continue;
                }

                ReactRenderer renderer = getRenderer(item);
                if (renderer == null) {
                    renderer = renderers.get(FeatureUnknown.ID);
                }

                boolean mapViewChanged = force
                        || meta.getMapView() == null
                        || meta.getMapView().getWorld() == null
                        || !meta.getMapView().getWorld().equals(world);
                if (mapViewChanged) {
                    meta.setMapView(createView(world, renderer));
                }

                boolean metadataChanged = applyRendererMetadata(meta, renderer);
                if (mapViewChanged || metadataChanged) {
                    item.setItemMeta(meta);
                    updated = true;
                }
            }
        }

        if (updated) {
            player.getInventory().setContents(is);
        }
    }

    @EventHandler
    public void on(PlayerJoinEvent e) {
        join(e.getPlayer());
    }

    public void join(Player p) {
        updateMapViews(p, true);
    }

    @EventHandler
    public void on(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getFrom() == null || e.getFrom().getWorld() == null || e.getTo().getWorld() == null) {
            return;
        }

        if (!e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            updateMapViews(e.getPlayer(), e.getTo().getWorld(), false);
        }
    }

    public ItemStack createMap(World world, ReactRenderer renderer) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setMapView(createView(world, renderer));
        applyRendererMetadata(meta, renderer);
        item.setItemMeta(meta);

        return item;
    }

    @Override
    public String getName() {
        return "Map";
    }

    @Override
    public void start() {
        renderers = new ConcurrentHashMap<>();
        renderers.put(FeatureUnknown.ID, new RendererUnknown());
        irisMetricsRenderer = new RendererIrisMetrics();
        adaptMetricsRenderer = new RendererAdaptMetrics();
    }

    private void scanForRenderers(String pkg) {
        String p = React.instance.jar().getAbsolutePath();
        p = p.replaceAll("\\Q.jar.jar\\E", ".jar");
        JarScanner j = new JarScanner(new File(p), pkg);
        try {
            j.scan();
            j.getClasses().stream()
                    .filter(i -> i.isAssignableFrom(ReactRenderer.class) || ReactRenderer.class.isAssignableFrom(i))
                    .map((i) -> {
                        try {
                            return (ReactRenderer) i.getConstructor().newInstance();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }

                        return null;
                    })
                    .forEach((i) -> {
                        if (i != null) {
                            renderers.put(i.getId(), i);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {

    }

    @Override
    public void postStart() {
        for (Sampler i : React.controller(SampleController.class).getSamplers().all()) {
            renderers.put(i.getId(), i);
        }

        for (Feature i : React.controller(FeatureController.class).getFeatures().all()) {
            if (i instanceof ReactRenderer f) {
                renderers.put(i.getId(), f);
            }
        }

        syncIntegrationRenderers();

        for (Player i : Bukkit.getOnlinePlayers()) {
            J.s(() -> join(i));
        }
    }

    @Override
    public void onTick() {
        syncIntegrationRenderers();
    }

    private void syncIntegrationRenderers() {
        syncIntegrationRenderer("iris", irisMetricsRenderer);
        syncIntegrationRenderer("adapt", adaptMetricsRenderer);
        syncIntegrationCapabilityRenderers("iris");
        syncIntegrationCapabilityRenderers("adapt");
    }

    private void syncIntegrationRenderer(String capability, ReactRenderer renderer) {
        if (renderers == null || renderer == null) {
            return;
        }

        boolean available = IntegrationCapabilitySupport.hasCapability(
                React.controller(IntegrationController.class),
                capability
        );

        if (available) {
            renderers.put(renderer.getId(), renderer);
        } else {
            renderers.remove(renderer.getId());
        }
    }

    private void syncIntegrationCapabilityRenderers(String capability) {
        if (renderers == null || capability == null || capability.isBlank()) {
            return;
        }

        String prefix = capability.toLowerCase(Locale.ROOT).trim() + "-";
        boolean available = IntegrationCapabilitySupport.hasCapability(
                React.controller(IntegrationController.class),
                capability
        );

        if (!available) {
            renderers.keySet().removeIf(id -> normalizeRendererId(id).startsWith(prefix));
            return;
        }

        SampleController sampleController = React.controller(SampleController.class);
        if (sampleController != null && sampleController.getSamplers() != null) {
            for (Sampler sampler : sampleController.getSamplers().all()) {
                if (sampler == null || !normalizeRendererId(sampler.getId()).startsWith(prefix)) {
                    continue;
                }
                renderers.put(sampler.getId(), sampler);
            }
        }

        FeatureController featureController = React.controller(FeatureController.class);
        if (featureController != null && featureController.getFeatures() != null) {
            for (Feature feature : featureController.getFeatures().all()) {
                if (!(feature instanceof ReactRenderer reactRenderer) || !normalizeRendererId(feature.getId()).startsWith(prefix)) {
                    continue;
                }
                renderers.put(feature.getId(), reactRenderer);
            }
        }
    }

    private boolean applyRendererMetadata(MapMeta meta, ReactRenderer renderer) {
        if (meta == null || renderer == null) {
            return false;
        }

        String rendererId = Objects.toString(renderer.getId(), FeatureUnknown.ID);
        String normalized = normalizeRendererId(rendererId);
        String scope = rendererScope(normalized);
        String rendererName = rendererDisplayName(rendererId);
        String displayName = "React Monitor [" + scope + "]";
        List<String> lore = List.of(
                "Renderer: " + rendererName,
                "Scope: " + scope,
                "ID: " + rendererId
        );

        boolean changed = false;
        if (!Objects.equals(meta.getDisplayName(), displayName)) {
            meta.setDisplayName(displayName);
            changed = true;
        }

        if (!Objects.equals(meta.getLore(), lore)) {
            meta.setLore(lore);
            changed = true;
        }

        byte flag = meta.getPersistentDataContainer().getOrDefault(nsReact, PersistentDataType.BYTE, (byte) 0);
        if (flag != 1) {
            meta.getPersistentDataContainer().set(nsReact, PersistentDataType.BYTE, (byte) 1);
            changed = true;
        }

        String storedId = meta.getPersistentDataContainer().get(nsRenderer, PersistentDataType.STRING);
        if (!rendererId.equalsIgnoreCase(Objects.toString(storedId, ""))) {
            meta.getPersistentDataContainer().set(nsRenderer, PersistentDataType.STRING, rendererId);
            changed = true;
        }

        return changed;
    }

    private String rendererScope(String normalizedRendererId) {
        if (normalizedRendererId.startsWith("iris-")) {
            return "Iris";
        }
        if (normalizedRendererId.startsWith("adapt-")) {
            return "Adapt";
        }
        return "Core";
    }

    private String rendererDisplayName(String rendererId) {
        if (rendererId == null || rendererId.isBlank()) {
            return "Unknown";
        }

        String spaced = rendererId
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        if (spaced.isBlank()) {
            return rendererId;
        }

        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private String normalizeRendererId(String rendererId) {
        return Objects.toString(rendererId, "").toLowerCase(Locale.ROOT).trim();
    }
}
