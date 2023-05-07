package com.volmit.react;

import art.arcane.multiburst.MultiBurst;
import com.volmit.react.api.command.RCommand;
import com.volmit.react.core.command.*;
import com.volmit.react.core.controller.ActionController;
import com.volmit.react.core.controller.EventController;
import com.volmit.react.core.controller.PlayerController;
import com.volmit.react.core.controller.SampleController;
import com.volmit.react.util.*;
import com.volmit.react.util.tick.Ticker;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIConfig;
import lombok.Getter;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


@Getter
public class React extends VolmitPlugin {
    public static BukkitAudiences audiences;
    @Instance
    public static React instance;
    public static Thread serverThread;
    public static Ticker ticker;
    public static MultiBurst burst;
    public static BukkitAudiences adventure;
    private final List<RCommand> commands = new ArrayList<>();
    @Control
    private SampleController sampleController;
    @Control
    private PlayerController playerController;
    @Control
    private EventController eventController;
    @Control
    private ActionController actionController;

    public React() {
        instance = this;
    }

    public static void warn(String string) {
        msg(C.YELLOW + string);
    }

    public static void error(String string) {
        msg(C.RED + string);
    }

    public static void verbose(String string) {
        msg(C.LIGHT_PURPLE + string);
    }

    public static void msg(String string) {
        try {
            if (instance == null) {
                System.out.println("[React]: " + string);
                return;
            }

            String msg = C.GRAY + "[" + C.AQUA + "React" + C.GRAY + "]: " + string;
            Bukkit.getConsoleSender().sendMessage(msg);
        } catch (Throwable e) {
            System.out.println("[React]: " + string);
        }
    }

    public static void success(String string) {
        msg(C.GREEN + string);
    }

    public static void info(String string) {
        msg(C.WHITE + string);
    }

    public static void debug(String string) {
        msg(C.DARK_PURPLE + string);
    }

    @Override
    public void onLoad() {
        CommandAPI.onLoad(new CommandAPIConfig().verboseOutput(false));
        instance = this;
        if (Bukkit.isPrimaryThread()) {
            serverThread = Thread.currentThread();
        }
        super.onLoad();
    }

    @Override
    public void onEnable() {
        instance = this;
        CommandAPI.onEnable(this);
        burst = new MultiBurst("React", Thread.MIN_PRIORITY);
        if (Bukkit.isPrimaryThread()) {
            serverThread = Thread.currentThread();
        }
        ticker = new Ticker(burst);
        adventure = BukkitAudiences.create(this);
        super.onEnable();
        ticker.register(new ControllerTicker(actionController, 100));
        registerCommand(new CommandReact());
        registerCommand(new CommandColor());
        registerCommand(new CommandReload());
        registerCommand(new CommandMonitor());
        registerCommand(new CommandAction());

    }

    @Override
    public void onDisable() {
        stop();
        super.onDisable();
        CommandAPI.onDisable();
    }

    public File jar() {
        return getFile();
    }

    @Override
    public void start() {
        sampleController.postStart();
    }

    @Override
    public void stop() {
        ticker.close();
        burst.close();
    }

    private void registerCommand(RCommand command) {
        commands.add(command);
        CommandAPI.registerCommand(command.getClass());
    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.AQUA + "React" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }

    public Ticker getTicker() {
        return ticker;
    }

    public void reload() {
        stop();
        start();
    }


}
