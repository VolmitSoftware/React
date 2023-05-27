package com.volmit.react.content.decreecommand;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.benchmark.Hastebin;
import com.volmit.react.api.rendering.ReactRenderer;
import com.volmit.react.core.controller.MapController;
import com.volmit.react.core.controller.PlayerController;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.decree.annotations.Param;
import com.volmit.react.util.decree.handlers.ReactRendererHandler;
import com.volmit.react.util.format.C;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.RNG;
import com.volmit.react.util.reflect.Platform;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Decree(
        name = "react",
        aliases = {"re"},
        origin = DecreeOrigin.BOTH,
        description = "The root react command"
)
public class CommandReact implements DecreeExecutor {
    private CommandConfig config;
    private CommandAction action;
    private CommandChunk chunk;
    private CommandEnvironment environment;

    @Decree(
        name = "monitor",
        aliases = {"m", "mon"},
        description = "Monitor the server via action bar",
        origin = DecreeOrigin.PLAYER
    )
    public void monitor() {
        React.controller(PlayerController.class).getPlayer(player()).toggleActionBar();
        sender().sendMessage(C.REACT + "Action bar monitor " + (React.controller(PlayerController.class).getPlayer(player()).isActionBarMonitoring() ? "enabled" : "disabled"));
    }

    @Decree(
        name = "allaytest",
        aliases = {"at"},
        description = "Monitor the server via action bar",
        origin = DecreeOrigin.PLAYER
    )
    public void allaytest() {
        J.s(() -> {
            for(int i = 0; i < 100; i++) {
                for(Player j : Bukkit.getOnlinePlayers()) {
                    Allay a = (Allay) j.getWorld().spawnEntity(j.getEyeLocation().clone().add(RNG.r.d(-10, 10), RNG.r.d(-10, 10), RNG.r.d(-10, 10)), EntityType.ALLAY);
                    a.getInventory().setItem(0, new ItemStack(Material.KELP));
                    a.setCanDuplicate(true);
                    a.setLeashHolder(j);
                }
            }
        });
    }

    @Decree(
            name = "info",
            aliases = {"info", "i"},
            description = "Print the environment details!"
    )
    public void enviornment() {
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == React Info == -- ");
        sender().sendMessage(C.GOLD + "React Version Version: " + React.instance.getDescription().getVersion());
        sender().sendMessage(C.GOLD + "Server Type: " + Bukkit.getVersion());
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Platform Overview == -- ");
        sender().sendMessage(C.GOLD + "Version: " + Platform.getVersion() + " - Platform: " + Platform.getName());
        sender().sendMessage(C.GOLD + "Java Vendor: " + Platform.ENVIRONMENT.getJavaVendor() + " - Java Version: " + Platform.ENVIRONMENT.getJavaVersion());
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Storage Information == -- ");
        sender().sendMessage(C.GOLD + "Total Space: " + Form.memSize(Platform.STORAGE.getTotalSpace()));
        sender().sendMessage(C.GOLD + "Free Space: " + Form.memSize(Platform.STORAGE.getFreeSpace()));
        sender().sendMessage(C.GOLD + "Used Space: " + Form.memSize(Platform.STORAGE.getUsedSpace()));
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Memory Information == -- ");
        sender().sendMessage(C.GOLD + "Physical Memory - Total: " + Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory()));
        sender().sendMessage(C.GOLD + "Virtual Memory - Total: " + Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory()));
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == CPU Overview == -- ");
        sender().sendMessage(C.GOLD + "CPU Architecture: " + Platform.CPU.getArchitecture() + " Available Processors: " + Platform.CPU.getAvailableProcessors());
        sender().sendMessage(C.GOLD + "CPU Load: " + Form.pc(Platform.CPU.getCPULoad()) + " CPU Live Process Load: " + Form.pc(Platform.CPU.getLiveProcessCPULoad()));
        Hastebin.enviornment(sender());

    }

    @Decree(
            name = "set-player-view-distance",
            aliases = {"vd", "view-distance"},
            description = "Visualize the via glow blocks",
            origin = DecreeOrigin.PLAYER
    )
    public void vd(
            @Param(name = "distance")
            int d) {
        Curse.on(player().getWorld()).method("setViewDistance", int.class).invoke(d);
        Curse.on(player().getWorld()).method("setSimulationDistance", int.class).invoke(d);
    }

    @Decree(
            name = "map",
            description = "Visualize the via glow blocks",
            origin = DecreeOrigin.PLAYER
    )
    public void map(
            @Param(
                    name = "renderer",
                    defaultValue = "unknown",
                    customHandler = ReactRendererHandler.class
            ) ReactRenderer renderer
    ) {
        React.controller(MapController.class).openRenderer(player(), renderer);
    }
}
