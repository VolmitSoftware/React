package com.volmit.react.controller;

import com.volmit.react.Config;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.SampledType;
import com.volmit.react.api.Unused;
import com.volmit.react.util.Controller;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import primal.json.JSONObject;

public class MetricsController extends Controller {

    @Override
    public void dump(JSONObject object) {

    }

    @Override
    public void start() {
        if (Config.SAFE_MODE_NETWORKING) {
            return;
        }

        Metrics stats = new Metrics(ReactPlugin.i, 1650);

        stats.addCustomChart(new SimplePie("max_memory", () -> SampledType.MAXMEM.get().get()));
        stats.addCustomChart(new SimplePie("language", () -> Config.LANGUAGE));
        stats.addCustomChart(new SimplePie("view_distance", () -> Bukkit.getServer().getViewDistance() + ""));
        stats.addCustomChart(new SimplePie("using_protocollib", () -> Bukkit.getPluginManager().getPlugin("ProtocolLib") != null ? "Yes" : "No"));
        stats.addCustomChart(new SimplePie("using_fawe", () -> Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null ? "Yes" : "No"));
    }

    @Override
    public void stop() {

    }

    @Unused
    @Override
    public void tick() {

    }

    @Override
    public int getInterval() {
        return 2016;
    }

    @Override
    public boolean isUrgent() {
        return false;
    }
}
