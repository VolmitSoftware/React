package com.volmit.react.controller;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Surge;
import com.volmit.react.api.IRAI;
import com.volmit.react.api.RAI;
import com.volmit.react.util.A;
import com.volmit.react.util.Controller;
import com.volmit.react.util.TICK;
import org.bukkit.Bukkit;
import primal.json.JSONObject;

public class RAIController extends Controller {
    public boolean raiEnabled;
    private IRAI rai;

    @Override
    public void dump(JSONObject object) {
        object.put("active", raiEnabled);
    }

    @Override
    public void start() {
        Surge.register(this);
        rai = new RAI();
        raiEnabled = true;
    }

    @Override
    public void stop() {
        Surge.unregister(this);
    }

    @Override
    public void tick() {
        if (!Config.RAI) {
            return;
        }

        if (Config.MONITOR_ONLY) {
            return;
        }

        if (Gate.isLowMemory() && TICK.tick % 5 != 0) {
            return;
        }

        try {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }
        } catch (Throwable e) {

        }

        new A() {
            @Override
            public void run() {
                if (rai == null) {
                    return;
                }

                rai.tick();
            }
        };
    }

    public IRAI getRai() {
        return rai;
    }

    @Override
    public int getInterval() {
        return 1;
    }

    @Override
    public boolean isUrgent() {
        return true;
    }
}
