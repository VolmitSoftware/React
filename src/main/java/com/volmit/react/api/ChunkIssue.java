package com.volmit.react.api;

public enum ChunkIssue
{
	ENTITY,
	HOPPER,
	TNT,
	REDSTONE,
	FLUID,
	PHYSICS;

	public double getMS()
	{
		switch(this)
		{
			case ENTITY:
				return SampledType.ENTITY_TIME.get().getValue();
			case FLUID:
				return SampledType.FLUID_TIME.get().getValue() / 1000000.0;
			case HOPPER:
				return SampledType.HOPPER_TIME.get().getValue() / 1000000.0;
			case PHYSICS:
				return SampledType.PHYSICS_TIME.get().getValue() / 1000000.0;
			case REDSTONE:
				return SampledType.REDSTONE_TIME.get().getValue() / 1000000.0;
			case TNT:
				return SampledType.EXPLOSION_TIME.get().getValue() / 1000000.0;
			default:
				break;
		}

		return 0;
	}
}
