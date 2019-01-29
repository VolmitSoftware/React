package com.volmit.react.api;

import com.volmit.react.Info;

public enum ActionType
{
	FIX_LIGHTING(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_FIX_LIGHTING_NAME, Info.ACTION_FIX_LIGHTING_DESCRIPTION),
	CPU_SCORE(ActionHandle.MUTEX, ActionTargetType.SYSTEM, "CPU Score", "Tempts the processor to turbo and catches the score."),
	DUMP(ActionHandle.MUTEX, ActionTargetType.SYSTEM, "Dump", "Dumps debugging and performance information into paste."),
	FILE_SIZE(ActionHandle.MUTEX, ActionTargetType.SYSTEM, "File Size", "Checks the server files for a size report."),
	TIMINGS(ActionHandle.MUTEX, ActionTargetType.SYSTEM, "Timings Report", "Pulls a timings report for some time."),
	CHUNK_TEST(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_CHUNK_TEST_NAME, Info.ACTION_CHUNK_TEST_DESCRIPTION),
	UNLOCK_FLUID(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_UNLOCK_FLUID_NAME, Info.ACTION_UNLOCK_FLUID_DESCRIPTION),
	LOCK_FLUID(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_LOCK_FLUID_NAME, Info.ACTION_LOCK_FLUID_DESCRIPTION),
	UNLOCK_HOPPER(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_UNLOCK_HOPPER_NAME, Info.ACTION_UNLOCK_HOPPER_DESCRIPTION),
	LOCK_HOPPER(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_LOCK_HOPPER_NAME, Info.ACTION_LOCK_HOPPER_DESCRIPTION),
	UNLOCK_REDSTONE(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_UNLOCK_REDSTONE_NAME, Info.ACTION_UNLOCK_REDSTONE_DESCRIPTION),
	LOCK_REDSTONE(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_LOCK_REDSTONE_NAME, Info.ACTION_LOCK_REDSTONE_DESCRIPTION),
	CULL_ENTITIES(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_CULL_ENTITIES_NAME, Info.ACTION_CULL_ENTITIES_DESCRIPTION),
	COLLECT_GARBAGE(ActionHandle.MUTEX, ActionTargetType.SYSTEM, Info.ACTION_COLLECT_GARBAGE_NAME, Info.ACTION_COLLECT_GARBAGE_DESCRIPTION),
	PURGE_ENTITIES(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_PURGE_ENTITIES_NAME, Info.ACTION_PURGE_ENTITIES_DESCRIPTION),
	PURGE_CHUNKS(ActionHandle.MUTEX, ActionTargetType.POSITIONAL, Info.ACTION_PURGE_CHUNKS_NAME, Info.ACTION_PURGE_CHUNKS_DESCRIPTION);

	private String name;
	private String description;
	private ActionHandle handle;
	private ActionTargetType target;

	private ActionType(ActionHandle handle, ActionTargetType target, String name, String description)
	{
		this.name = name;
		this.description = description;
		this.handle = handle;
		this.target = target;
	}

	public String getName()
	{
		return name;
	}

	public String getDescription()
	{
		return description;
	}

	public ActionHandle getHandle()
	{
		return handle;
	}

	public ActionTargetType getTarget()
	{
		return target;
	}
}
