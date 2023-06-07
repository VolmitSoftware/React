package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.api.feature.Feature;
import com.volmit.react.api.rendering.MapRendererPipe;
import com.volmit.react.api.rendering.ReactRenderer;
import com.volmit.react.api.rendering.RendererUnknown;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.content.feature.FeatureUnknown;
import com.volmit.react.util.io.JarScanner;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.TickedObject;
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
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class MapController extends TickedObject implements IController, Listener {
    private static final NamespacedKey nsReact = new NamespacedKey(React.instance, "react");
    private static final NamespacedKey nsRenderer = new NamespacedKey(React.instance, "react-renderer");
    private transient Map<String, ReactRenderer> renderers;

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
        if (isReactMap(item)) {
            return renderers.getOrDefault(item.getItemMeta().getPersistentDataContainer().getOrDefault(nsRenderer, PersistentDataType.STRING, "unknown"), renderers.get(FeatureUnknown.ID));
        }

        return renderers.get(FeatureUnknown.ID);
    }

    public void setRenderer(Player player, ReactRenderer renderer) {
        if (hasReactMap(player)) {
            setRenderer(getReactMap(player), renderer);
        }
    }

    public void setRenderer(ItemStack map, ReactRenderer renderer) {
        if (isReactMap(map)) {
            MapMeta meta = (MapMeta) map.getItemMeta();
            updateMapView(meta.getMapView(), renderer);
        }
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
        return item != null && item.getType().equals(Material.FILLED_MAP)
                && item.getItemMeta().getPersistentDataContainer().getOrDefault(nsReact, PersistentDataType.BYTE, (byte) 0) == 1;
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
                if (force || meta.getMapView() == null || meta.getMapView().getWorld() == null || !meta.getMapView().getWorld().equals(world)) {
                    meta.setMapView(createView(world, getRenderer(item)));
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
        if (e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            updateMapViews(e.getPlayer(), e.getTo().getWorld(), false);
        }
    }

    public ItemStack createMap(World world, ReactRenderer renderer) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(createView(world, renderer));
        meta.setDisplayName("React Monitor");
        meta.getPersistentDataContainer().set(nsReact, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(nsRenderer, PersistentDataType.STRING, renderer.getId());
        item.setItemMeta(meta);

        return item;
    }

    @Override
    public String getName() {
        return "Map";
    }

    @Override
    public void start() {
        renderers = new HashMap<>();
        renderers.put(FeatureUnknown.ID, new RendererUnknown());
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

        for (Player i : Bukkit.getOnlinePlayers()) {
            J.s(() -> join(i));
        }
    }

    @Override
    public void onTick() {

    }
}
