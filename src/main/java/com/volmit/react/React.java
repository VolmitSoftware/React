package com.volmit.react;

import art.arcane.multiburst.MultiBurst;
import com.volmit.react.core.controller.ActionController;
import com.volmit.react.core.controller.EventController;
import com.volmit.react.core.controller.PlayerController;
import com.volmit.react.core.controller.SampleController;
import com.volmit.react.util.C;
import com.volmit.react.util.Command;
import com.volmit.react.util.Control;
import com.volmit.react.util.ControllerTicker;
import com.volmit.react.util.Instance;
import com.volmit.react.util.VolmitPlugin;
import com.volmit.react.api.command.RCommand;
import com.volmit.react.util.tick.Ticker;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIConfig;
import lombok.Getter;
import lombok.SneakyThrows;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;


@Getter
public class React extends VolmitPlugin {
    private final List<RCommand> commands = new ArrayList<>();
    public static BukkitAudiences audiences;

    @Instance
    public static React instance;
    public static Thread serverThread;
    public static Ticker ticker;
    public static MultiBurst burst;
    public static BukkitAudiences adventure;

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

    @Override
    public void onLoad() {
        CommandAPI.onLoad(new CommandAPIConfig().verboseOutput(false));
        instance = this;
        if(Bukkit.isPrimaryThread()) {
            serverThread = Thread.currentThread();
        }
        super.onLoad();
    }

    @Override
    public void onEnable() {
        instance = this;
        burst = new MultiBurst("React", Thread.MIN_PRIORITY);
        if(Bukkit.isPrimaryThread()) {
            serverThread = Thread.currentThread();
        }
        ticker = new Ticker(burst);
        adventure = BukkitAudiences.create(this);
        super.onEnable();
        ticker.register(new ControllerTicker(actionController, 100));
        CommandAPI.onEnable(this);
        registerCommands();
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

    public Ticker getTicker() {
        return ticker;
    }

    public void reload() {
        stop();
        start();
    }

    @SneakyThrows
    public void registerCommands() {
        try {
            // Get the package name
            String packageName = "commands";
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = classLoader.getResources(path);
            List<File> dirs = new ArrayList<>();
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                dirs.add(new File(resource.getFile()));
            }

            for (File directory : dirs) {
                registerCommandsInDirectory(directory, packageName);
            }
        } catch (IOException | ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    private void registerCommandsInDirectory(File directory, String packageName)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        if (!directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                registerCommandsInDirectory(file, packageName + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                Class<?> clazz = Class.forName(className);
                if (RCommand.class.isAssignableFrom(clazz)) {
                    RCommand command = (RCommand) clazz.getDeclaredConstructor().newInstance();
                    registerCommand(command);
                }
            }
        }
    }

}
