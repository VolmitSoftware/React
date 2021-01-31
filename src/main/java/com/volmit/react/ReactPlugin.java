package com.volmit.react;

import com.volmit.react.api.Capability;
import com.volmit.react.api.Permissable;
import com.volmit.react.legacy.server.ReactServer;
import com.volmit.react.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import primal.bukkit.nms.Catalyst;
import primal.bukkit.nms.FrameType;
import primal.bukkit.nms.NMP;
import primal.bukkit.nms.NMSVersion;
import primal.bukkit.plugin.PrimalPlugin;
import primal.bukkit.sched.J;
import primal.lang.collection.GList;
import primal.util.text.C;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;

public class ReactPlugin extends PrimalPlugin {
    public static ReactPlugin i;
    private GList<IController> controllers;
    private React react;
    private ParallelPoolManager pool;
    private HotloadManager mgr;
    private ReactServer srv;

    public static void reload() {
        React.instance().stopRLServer();
        new TaskLater("", 5) {
            @Override
            public void run() {
                i.onDisable();
                Bukkit.getScheduler().cancelTasks(i);
                HandlerList.unregisterAll(i);
                i.onEnable();
            }
        };
    }

    public static ReactPlugin getI() {
        return i;
    }

    @Override
    public void start() {
        if (this.getDescription().getVersion().contains("-dev"))
            this.warn();

        controllers = new GList<>();
        Surge.m();
        i = this;
        react = new React();
        React.instance = react;
        initNMS();

        pool = new ParallelPoolManager(1) {
            @Override
            public long getNanoGate() {
                return 0;
            }
        };

        S.mgr = pool;
        Config.onRead(i);

        for (Field i : React.class.getFields()) {
            if (i.isAnnotationPresent(Control.class)) {
                try {
                    IController controller = (IController) i.getType().getConstructor().newInstance();
                    i.set(react, controller);
                    controllers.add(controller);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        for (IController i : controllers) {
            try {
                i.start();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        mgr = new HotloadManager();
        Runnable rl = () -> {
            for (Player i : P.onlinePlayers()) {
                if (Permissable.ACCESS.has(i) || i.isOp()) {
                    Gate.msgSuccess(i, "Injecting Configuration Changes");
                    NMP.MESSAGE.advance(i, new ItemStack(Material.EMPTY_MAP), C.GRAY + "React Config Hotloaded", FrameType.GOAL);
                }
            }

            J.s(ReactPlugin::reload, 10);
        };

        mgr.track(new File(getDataFolder(), "config.yml"), rl);
        mgr.track(new File(getDataFolder(), "config-experimental.yml"), rl);

        new Task("controller-main.tick", 0) {
            @Override
            public void run() {
                TICK.tick++;
                doTick();
            }
        };

        if (Capability.PLACEHOLDERS.isCapable()) {
            PlaceholderHandler h = new PlaceholderHandler();
            h.register();

            new TaskLater("waiter", 5) {
                @Override
                public void run() {
                    new A() {
                        @Override
                        public void run() {
                            try {
                                h.writeToFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                }
            };
        }

        if (Config.LEGACY_SERVER) {
            new TaskLater("waiter-srv", 12) {
                @Override
                public void run() {
                    startRLServer();
                }
            };
        }

    }

    @Override
    public void stop() {
        for (IController i : controllers) {
            try {
                i.stop();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        mgr.untrackall();
        controllers.clear();
        pool.shutdown();
        stopRLServer();
        stopNMS();
    }

    @Override
    public String getTag(String subTag) {
        if (subTag == null || subTag.trim().isEmpty()) {
            return C.AQUA + "[" + C.DARK_GRAY + "React" + C.AQUA + "]" + C.GRAY + ": ";
        }

        return C.AQUA + "[" + C.DARK_GRAY + "React" + C.GRAY + " - " + C.WHITE + subTag.trim() + C.AQUA + "]" + C.GRAY + ": ";
    }

    public void warn() {
        System.out.println("==============================================================");
        System.out.println("THIS IS AN EARLY REACT BETA RELEASE. THIS VERSION WAS PROVIDED");
        System.out.println("TO SPECIFIC AND TRUSTED USERS. THIS BUILD MAY BE UNSTABLE.");
        System.out.println("PLEASE USE COMMON SENSE WHEN USING DEVELOPMENT BUILDS, AND HAVE");
        System.out.println("TESTED BACKUPS. PLEASE DO NOT REDISTRIBUTE THIS EARLY ACCESS BUILD.");
        System.out.println("==============================================================");

        try {
            Thread.sleep(1000 * 3);
        } catch (InterruptedException ignored) {
        }
    }

    private void stopNMS() {
        assert Catalyst.host != null;
        Catalyst.host.stop();
    }

    private void initNMS() {
        assert Catalyst.host != null;
        Catalyst.host.start();
        NMP.host = Catalyst.host;
        NMSVersion v = NMSVersion.current();

        if (v != null) {
            getLogger().info("Selected " + Objects.requireNonNull(NMSVersion.current()).name());
        } else {
            getLogger().info("Could not find a suitable binder for this server version!");
        }
    }

    private void doTick() {
        if (TICK.tick % 20 == 0) {
            try {
                mgr.onTick();
            } catch (Throwable e) {
                Ex.t(e);
            }
        }

        for (IController i : controllers.copy()) {
            doTick(i);
        }

        try {
            pool.tickSyncQueue();
        } catch (Throwable e) {
            Ex.t(e);
        }
    }

    private void doTick(IController i) {
        if (TICK.tick % (Math.max(i.getInterval(), 1)) != 0) {
            return;
        }

        if (i.isUrgent()) {
            handle(i);
        } else {
            new S("tick-task-" + i.getClass().getSimpleName()) {
                @Override
                public void run() {
                    handle(i);
                }
            };
        }
    }

    private void handle(IController i) {
        try {
            Profiler p = new Profiler();
            p.begin();
            i.tick();
            p.end();
            i.setTime(p.getMilliseconds());
        } catch (Throwable e) {
            Ex.t(e);
        }
    }

    public void startRLServer() {
        new TaskLater("", 12) {
            @Override
            public void run() {
                new A() {
                    @Override
                    public void run() {
                        try {
                            srv = new ReactServer(Config.LEGACY_SERVER_PORT);
                            srv.start();
                        } catch (IOException e) {
                            System.out.println("COULD NOT START REACT REMOTE SERVER");
                            e.printStackTrace();
                        }
                    }
                };
            }
        };
    }

    @SuppressWarnings("deprecation")
    public void stopRLServer() {
        new Thread("React Remote Killer") {
            @Override
            public void run() {
                if (srv != null) {
                    if (srv.isAlive()) {
                        srv.interrupt();

                        try {
                            srv.join(1000);

                            if (srv.isAlive()) {
                                try {
                                    srv.serverSocket.close();
                                } catch (IOException ignored) {

                                }

                                try {
                                    srv.stop();
                                } catch (Throwable ignored) {

                                }

                                try {
                                    srv.destroy();
                                } catch (Throwable ignored) {

                                }
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
    }

    public GList<IController> getControllers() {
        return controllers;
    }

    public React getReact() {
        return react;
    }

    public ParallelPoolManager getPool() {
        return pool;
    }

    public HotloadManager getMgr() {
        return mgr;
    }

    public ReactServer getSrv() {
        return srv;
    }

    public int startTask(int delay, Runnable r) {
        return Bukkit.getScheduler().scheduleSyncDelayedTask(this, r, delay);
    }

    public int startRepeatingTask(int delay, int interval, Runnable r) {
        return Bukkit.getScheduler().scheduleSyncRepeatingTask(this, r, delay, interval);
    }

    public void stopTask(int id) {
        Bukkit.getScheduler().cancelTask(id);
    }
}
