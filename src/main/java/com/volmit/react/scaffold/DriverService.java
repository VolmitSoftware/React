package com.volmit.react.scaffold;

import art.arcane.quill.collections.KList;
import art.arcane.quill.execution.Looper;
import art.arcane.quill.logging.L;
import art.arcane.quill.service.QuillService;
import com.volmit.react.React;
import lombok.Data;
import org.bukkit.Bukkit;

@Data
public class DriverService extends QuillService
{
    private transient Looper asyncLooper;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(React.instance(), this::sync, 0, 0);
        asyncLooper = new Looper() {
            @Override
            protected long loop() {
                async();
                return 50;
            }
        };
        asyncLooper.start();
    }

    @Override
    public void onDisable() {
        asyncLooper.interrupt();
    }

    private void sync()
    {
        try
        {

        }

        catch(Throwable e)
        {
            L.v("React Sync Tick Driver Error");
            e.printStackTrace();
        }
    }

    private void async()
    {
        try
        {

        }

        catch(Throwable e)
        {
            L.v("React Async Tick Driver Error");
            e.printStackTrace();
        }
    }
}
