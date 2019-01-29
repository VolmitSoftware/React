package com.volmit.react.fix;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;

import com.volmit.react.Gate;
import com.volmit.react.api.Capability;
import com.volmit.react.util.C;
import com.volmit.react.util.F;
import com.volmit.react.util.Worlds;

public class FixEntityAI extends Fix
{
	public FixEntityAI()
	{
		setName("Fix Entity AI");
		setId("entity-ai");
		setAliases(new String[] {"eai", "ai"});
		setDescription("Re-activates entity AI for all entities.");
		setUsage("");
	}

	@Override
	public void run(CommandSender sender, String[] args)
	{
		if(!Capability.ENTITY_AI.isCapable())
		{
			Capability.ENTITY_AI.sendNotCapable(sender);
			return;
		}

		int fixed = 0;
		int itr = 0;

		for(World i : Worlds.getWorlds())
		{
			for(LivingEntity j : i.getLivingEntities())
			{
				itr++;

				if(!j.hasAI())
				{
					fixed++;
					j.setAI(true);
				}
			}
		}

		Gate.msgSuccess(sender, "Activated AI on " + C.WHITE + F.f(fixed) + C.GRAY + " of " + C.WHITE + F.f(itr) + C.GRAY + " entities.");
	}
}
