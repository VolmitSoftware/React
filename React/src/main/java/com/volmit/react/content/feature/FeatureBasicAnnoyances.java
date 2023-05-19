package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.math.RNG;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FeatureBasicAnnoyances extends ReactFeature implements Listener {
    public static final String ID = "basic-annoyances";
    private boolean disableWeatherWhenOnlyCreativePlayersOnline = true;

    public FeatureBasicAnnoyances() {
        super(ID);
    }

    @Override
    public void onActivate() {
        React.instance.registerListener(this);
    }

    @EventHandler
    public void on(WeatherChangeEvent e) {
        if(e.toWeatherState() && disableWeatherWhenOnlyCreativePlayersOnline && Bukkit.getOnlinePlayers().stream().allMatch(i -> i.getGameMode().equals(GameMode.CREATIVE))) {
            e.setCancelled(true);
            React.verbose("Cancelled weather change because all players are in creative mode");
        }
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
