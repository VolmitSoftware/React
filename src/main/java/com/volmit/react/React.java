package com.volmit.react;

import com.volmit.react.controller.ActionController;
import com.volmit.react.controller.ChunkController;
import com.volmit.react.controller.CollisionController;
import com.volmit.react.controller.CommandController;
import com.volmit.react.controller.CrashController;
import com.volmit.react.controller.EntityCullController;
import com.volmit.react.controller.EntityStackController;
import com.volmit.react.controller.EventController;
import com.volmit.react.controller.ExplosiveController;
import com.volmit.react.controller.FastDecayController;
import com.volmit.react.controller.FastGrowthController;
import com.volmit.react.controller.FeatureController;
import com.volmit.react.controller.FixController;
import com.volmit.react.controller.FluidController;
import com.volmit.react.controller.GlassController;
import com.volmit.react.controller.GraphController;
import com.volmit.react.controller.HopperController;
import com.volmit.react.controller.HopperOvertickController;
import com.volmit.react.controller.InstantDropController;
import com.volmit.react.controller.LanguageController;
import com.volmit.react.controller.MemoryController;
import com.volmit.react.controller.MessageController;
import com.volmit.react.controller.MetricsController;
import com.volmit.react.controller.MonitorController;
import com.volmit.react.controller.PhysicsController;
import com.volmit.react.controller.PlayerController;
import com.volmit.react.controller.ProtocolController;
import com.volmit.react.controller.RAIController;
import com.volmit.react.controller.RedstoneController;
import com.volmit.react.controller.SampleController;
import com.volmit.react.controller.SmearTickController;
import com.volmit.react.controller.SpikeController;
import com.volmit.react.controller.WorldController;
import com.volmit.react.util.Control;
import com.volmit.react.util.D;
import com.volmit.react.util.Plugin;

@Plugin
public class React
{
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

	public React()
	{
		instance = this;

		if(Config.SAFE_MODE_NETWORKING)
		{
			D.w("WARNING: SafeMode NETWORKING is enabled.");
		}

		if(Config.SAFE_MODE_FAWE)
		{
			D.w("WARNING: SafeMode FAWE is enabled.");
		}

		if(Config.SAFE_MODE_NMS)
		{
			D.w("WARNING: SafeMode NMS is enabled.");
		}

		if(Config.SAFE_MODE_PROTOCOL)
		{
			D.w("WARNING: SafeMode PROTOCOL is enabled.");
		}

		if(Config.SAFE_MODE_CHUNK)
		{
			D.w("WARNING: SafeMode CHUNK is enabled.");
		}
	}

	public static ReactPlugin instance()
	{
		return ReactPlugin.i;
	}

	public void enable()
	{

	}

	public void disable()
	{

	}
}
