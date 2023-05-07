/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.react.util;

import com.volmit.react.React;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class VolmitPlugin extends JavaPlugin implements Listener {
    public static boolean bad = false;
    private Map<String, IController> controllers;
    private List<IController> cachedControllers;
    private Map<Class<? extends IController>, IController> cachedClassControllers;

    public void l(Object l) {
        React.info("[" + getName() + "]: " + l);
    }

    public void w(Object l) {
        React.warn("[" + getName() + "]: " + l);
    }

    public void f(Object l) {
        React.error("[" + getName() + "]: " + l);
    }

    public void v(Object l) {
        React.verbose("[" + getName() + "]: " + l);
    }

    public void onEnable() {
        registerInstance();
        registerControllers();
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::tickControllers, 0, 0);
        J.a(this::outputInfo);
        registerListener(this);
        start();
    }

    public void unregisterAll() {
        stopControllers();
        unregisterListeners();
        unregisterInstance();
    }

    private void outputInfo() {
        try {
            IO.delete(getDataFolder("info"));
            getDataFolder("info").mkdirs();
            outputPluginInfo();
        } catch (Throwable ignored) {

        }
    }

    private void outputPluginInfo() throws IOException {
        FileConfiguration fc = new YamlConfiguration();
        fc.set("version", getDescription().getVersion());
        fc.set("name", getDescription().getName());
        fc.save(getDataFile("info", "plugin.yml"));
    }

    @Override
    public void onDisable() {
        stop();
        Bukkit.getScheduler().cancelTasks(this);
        unregisterListener(this);
        unregisterAll();
    }

    private void tickControllers() {
        for (IController i : getControllers()) {
            tickController(i);
        }
    }

    private void tickController(IController i) {
        if (i.getTickInterval() < 0) {
            return;
        }

        M.tick++;
        if (M.interval(i.getTickInterval())) {
            try {
                i.tick();
            } catch (Throwable e) {
                w("Failed to tick controller " + i.getName());
                e.printStackTrace();
            }
        }
    }

    public List<IController> getControllers() {
        return cachedControllers;
    }

    private void registerControllers() {
        if (bad) {
            return;
        }
        controllers = new HashMap<>();
        cachedClassControllers = new HashMap<>();

        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Control.class)) {
                try {
                    i.setAccessible(true);
                    IController pc = (IController) i.getType().getConstructor().newInstance();
                    registerController(pc);
                    i.set(this, pc);
                    v("Registered " + pc.getName() + " (" + i.getName() + ")");
                } catch (IllegalArgumentException | IllegalAccessException | InstantiationException |
                         InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    w("Failed to register controller (field " + i.getName() + ")");
                    e.printStackTrace();
                }
            }
        }

        cachedControllers = new ArrayList<>(controllers.values());
    }

    public IController getController(Class<? extends IController> c) {
        return cachedClassControllers.get(c);
    }

    private void registerController(IController pc) {
        if (bad) {
            return;
        }
        controllers.put(pc.getName(), pc);
        cachedClassControllers.put(pc.getClass(), pc);
        registerListener(pc);

        try {
            pc.start();
            v("Started " + pc.getName());
        } catch (Throwable e) {
            w("Failed to start controller " + pc.getName());
            e.printStackTrace();
        }
    }

    private void registerInstance() {
        if (bad) {
            return;
        }
        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Instance.class)) {
                try {
                    i.setAccessible(true);
                    i.set(Modifier.isStatic(i.getModifiers()) ? null : this, this);
                    v("Registered Instance " + i.getName());
                } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                    w("Failed to register instance (field " + i.getName() + ")");
                    e.printStackTrace();
                }
            }
        }
    }

    private void unregisterInstance() {
        if (bad) {
            return;
        }
        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Instance.class)) {
                try {
                    i.setAccessible(true);
                    i.set(Modifier.isStatic(i.getModifiers()) ? null : this, null);
                    v("Unregistered Instance " + i.getName());
                } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                    w("Failed to unregister instance (field " + i.getName() + ")");
                    e.printStackTrace();
                }
            }
        }
    }

    public String getTag() {
        if (bad) {
            return "";
        }
        return getTag("");
    }

    public void registerListener(Listener l) {
        if (bad) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(l, this);
    }

    public void unregisterListener(Listener l) {
        if (bad) {
            return;
        }
        HandlerList.unregisterAll(l);
    }

    public void unregisterListeners() {
        if (bad) {
            return;
        }
        HandlerList.unregisterAll((Listener) this);
    }

    private void stopControllers() {
        for (IController i : new ArrayList<>(controllers.values())) {
            try {
                unregisterListener(i);
                i.stop();
                v("Stopped " + i.getName());
            } catch (Throwable e) {
                w("Failed to stop controller " + i.getName());
                e.printStackTrace();
            }
        }
    }

    public File getDataFile(String... strings) {
        List<String> s = new ArrayList<>(List.of(strings));
        File f = new File(getDataFolder(), String.join(File.separator, s));
        f.getParentFile().mkdirs();
        return f;
    }

    public File getDataFileList(String pre, String[] strings) {
        List<String> v = new ArrayList<>(List.of(strings));
        v.add(0, pre);
        File f = new File(getDataFolder(), String.join(File.separator, v));
        f.getParentFile().mkdirs();
        return f;
    }

    public File getDataFolder(String... strings) {
        if (strings.length == 0) {
            return super.getDataFolder();
        }

        List<String> s = new ArrayList<>(List.of(strings));
        File f = new File(getDataFolder(), String.join(File.separator, s));
        f.mkdirs();

        return f;
    }

    public File getDataFolderList(String pre, String[] strings) {
        List<String> v = new ArrayList<>(List.of(strings));
        v.add(0, pre);
        File f = new File(getDataFolder(), String.join(File.separator, v));
        f.mkdirs();

        return f;
    }

    public abstract void start();

    public abstract void stop();

    public abstract String getTag(String subTag);
}
