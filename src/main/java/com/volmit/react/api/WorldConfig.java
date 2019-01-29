package com.volmit.react.api;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;

import com.volmit.react.Config;
import com.volmit.react.Info;
import com.volmit.react.Surge;
import com.volmit.react.util.ConfigurationDataInput;
import com.volmit.react.util.ConfigurationDataOutput;
import com.volmit.react.util.Ex;
import com.volmit.react.util.IConfigurable;
import com.volmit.react.util.Key;
import com.volmit.volume.lang.collections.GList;

public class WorldConfig implements IConfigurable
{
	@Key("allow.rai")
	public boolean allowRai = true;

	@Key("allow.stacking")
	public boolean allowStacking = true;

	@Key("allow.actions")
	public boolean allowActions = true;

	@Key("allow.relighting")
	public boolean allowRelighting = true;

	@Key("allow.tile-throttling")
	public boolean allowTileThrottling = true;

	@Key("allow.entity-throttling")
	public boolean allowEntityThrottling = true;

	@Key("allow.fast-leaf-decay")
	public boolean allowFastLeafDecay = true;

	@Key("allow.chunk-purging")
	public boolean allowChunkPurging = true;

	@Key("allow.xp-handling")
	public boolean allowXPHandling = true;

	@Key("allow.drop-handling")
	public boolean allowDropHandling = true;

	@Key("entities.assume-no-side-effects")
	public List<String> assumeNoSideEffectsEntities = new ArrayList<String>(new GList<String>().qadd("ARMOR_STAND"));

	public File getConfigFile(World world)
	{
		return new File(Surge.folder(Info.WORLD_CONFIGS), world.getName() + "-" + world.getSeed() + Info.CORE_DOTYML);
	}

	public void save(World world)
	{
		if(!Config.USE_WORLD_CONFIGS)
		{
			return;
		}

		try
		{
			new ConfigurationDataOutput().write(this, getConfigFile(world));
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public void load(World world)
	{
		if(!Config.USE_WORLD_CONFIGS)
		{
			return;
		}

		try
		{
			new ConfigurationDataInput().read(this, getConfigFile(world));
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}
}
