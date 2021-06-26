package com.volmit.react.scaffold;

import art.arcane.quill.service.QuillService;
import art.arcane.quill.service.Service;
import com.volmit.react.React;
import lombok.Data;
import org.bukkit.Bukkit;

@Data
public class ReactServiceProvider extends QuillService
{
    @Service
    private MonitoringService monitoring;

    @Service
    private DriverService driver;

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(React.instance());
    }
}
