package com.volmit.react;

import com.volmit.react.controller.*;
import com.volmit.react.util.Control;
import com.volmit.react.util.D;
import com.volmit.react.util.Plugin;

@Plugin
public class React {
    public static React instance;

    @Control
    public SampleController sampleController;

    @Control
    public PlayerController playerController;

    @Control
    public MonitorController monitorController;

    @Control
    public CommandController commandController;

    @Control
    public ActionController actionController;

    @Control
    public FastDecayController fastDecayController;

    @Control
    public EntityStackController entityStackController;

    @Control
    public GlassController glassController;

    @Control
    public EntityCullController entityCullController;

    @Control
    public SpikeController spikeController;

    @Control
    public RedstoneController redstoneController;

    @Control
    public HopperController hopperController;

    @Control
    public PhysicsController physicsController;

    @Control
    public RAIController raiController;

    @Control
    public FluidController fluidController;

    @Control
    public ChunkController chunkController;

    @Control
    public GraphController graphController;

    @Control
    public EventController eventController;

    @Control
    public SmearTickController smearTickController;

    @Control
    public FeatureController featureController;

    @Control
    public HopperOvertickController hopperPlungeController;

    @Control
    public LanguageController languageController;

    @Control
    public ExplosiveController explosiveController;

    @Control
    public WorldController worldController;

    @Control
    public MessageController messageController;

    @Control
    public MetricsController metricsController;

    @Control
    public FastGrowthController fastGrowthController;

    @Control
    public CrashController crashController;

    @Control
    public InstantDropController InstantDropController;

    @Control
    public ProtocolController protocolController;

    @Control
    public FixController fixController;

    @Control
    public CollisionController collisionController;

    @Control
    public MemoryController memoryController;

    @Control
    public TickListController tickListController;

    public React() {
        instance = this;

        if (Config.SAFE_MODE_NETWORKING) {
            D.w("WARNING: SafeMode NETWORKING is enabled.");
        }

        if (Config.SAFE_MODE_FAWE) {
            D.w("WARNING: SafeMode FAWE is enabled.");
        }

        if (Config.SAFE_MODE_NMS) {
            D.w("WARNING: SafeMode NMS is enabled.");
        }

        if (Config.SAFE_MODE_PROTOCOL) {
            D.w("WARNING: SafeMode PROTOCOL is enabled.");
        }

        if (Config.SAFE_MODE_CHUNK) {
            D.w("WARNING: SafeMode CHUNK is enabled.");
        }
    }

    public static ReactPlugin instance() {
        return ReactPlugin.i;
    }

    public void enable() {

    }

    public void disable() {

    }
}
