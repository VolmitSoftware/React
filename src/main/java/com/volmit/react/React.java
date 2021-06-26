package com.volmit.react;

import art.arcane.quill.Quill;
import com.volmit.react.scaffold.ReactServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class React extends JavaPlugin {
    private static React instance;
    private static ReactServiceProvider provider;

    public static React instance()
    {
        return instance;
    }

    public static ReactServiceProvider provider()
    {
        return provider;
    }

    public void onEnable()
    {
        instance = this;
        provider = new ReactServiceProvider();
        Quill.delegate = provider;
        Quill.delegateClass = provider.getClass();
        provider.startService();
    }

    public void onDisable()
    {
        provider.stopService();
    }
}
