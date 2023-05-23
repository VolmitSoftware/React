package com.volmit.react.content.tweak;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class TweakWorldTickLimiter extends ReactTweak implements Listener {
    public static final String ID = "tweak-tick-limiter";
    private double entityTickLimit = 0.1;
    private double tileTickLimit = 0.1;
    private Class<?> reactLimiterCompiledClass;
    private Map<World, Object> oldEntityLimiters;
    private Map<World, Object> oldTileLimiters;

    public TweakWorldTickLimiter() {
        super(ID);
    }

    @Override
    public void onActivate() {
        oldEntityLimiters = new HashMap<>();
        oldTileLimiters = new HashMap<>();

        try {
            reactLimiterCompiledClass = Curse.compile("com.volmit.react.generated", """
            package com.volmit.react.generated;
                        
            import art.arcane.chrono.PrecisionStopwatch;
            import org.spigotmc.TickLimiter;
                        
            import java.util.function.Supplier;
                        
            public class ReactTickLimiter extends TickLimiter {
                private PrecisionStopwatch stopwatch;
                private Supplier<Double> limit;
                private String name;
                        
                public ReactTickLimiter(String name, Supplier<Double> limit) {
                    super(50);
                    this.name = name;
                    this.limit = limit;
                    this.stopwatch = PrecisionStopwatch.start();
                }
                        
                public void initTick() {
                    System.out.println("A tick started for " + name);
                    stopwatch.resetAndBegin();
                }
                        
                public boolean shouldContinue() {
                    return stopwatch.getMilliseconds() < limit.get();
                }
            }
                        
            """);
        }

        catch(Throwable e) {
            e.printStackTrace();
        }

        for(World i : Bukkit.getWorlds()) {
            inject(i);
        }

        React.instance.registerListener(this);
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    public void inject(World world) {
        oldTileLimiters.put(world, Curse.on(Curse.on(world).get("handle")).get("tileLimiter"));
        oldEntityLimiters.put(world, Curse.on(Curse.on(world).get("handle")).get("entityLimiter"));
        Curse.on(Curse.on(world).get("handle")).set("tileLimiter", Curse.on(reactLimiterCompiledClass)
            .construct(world.getName() + " tiles", (Supplier<Double>)() -> tileTickLimit));
        Curse.on(Curse.on(world).get("handle")).set("entityLimiter", Curse.on(reactLimiterCompiledClass)
            .construct(world.getName() + " entities", (Supplier<Double>)() -> entityTickLimit));
    }

    public void uninject(World world) {
        Curse.on(Curse.on(world).get("handle")).set("tileLimiter", oldTileLimiters.get(world));
        Curse.on(Curse.on(world).get("handle")).set("entityLimiter", oldEntityLimiters.get(world));
    }

    @EventHandler
    public void on(WorldLoadEvent e) {
        inject(e.getWorld());
    }

    @EventHandler
    public void on(WorldUnloadEvent e) {
        uninject(e.getWorld());
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
