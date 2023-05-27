package com.volmit.react.content.tweak;

import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.world.FastWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.type.Snow;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;

import java.util.Map;

public class TweakTestETick extends ReactTweak implements Listener {
    public static final String ID = "test-etick";

    public TweakTestETick() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
