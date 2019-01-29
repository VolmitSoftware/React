package com.volmit.react.controller;

import org.bukkit.command.CommandSender;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Surge;
import com.volmit.react.api.IFix;
import com.volmit.react.fix.FixEntityAI;
import com.volmit.react.fix.FixInvisibleChunk;
import com.volmit.react.fix.FixLowMM;
import com.volmit.react.util.C;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.volume.lang.collections.GList;

public class FixController extends Controller
{
	private GList<IFix> fixes;

	@Override
	public void dump(JSONObject object)
	{
		object.put("fixes-loaded", fixes.size());
	}

	@Override
	public void start()
	{
		fixes = new GList<IFix>();

		fixes.add(new FixEntityAI());
		fixes.add(new FixLowMM());
		fixes.add(new FixInvisibleChunk());

		Surge.register(this);
	}

	public GList<IFix> getFixes()
	{
		return fixes;
	}

	public void runFix(CommandSender sender, String name, String[] args)
	{
		if(Config.SAFE_MODE_NMS)
		{
			Gate.msgError(sender, "SAFE MODE NMS ENABLED.");
			return;
		}

		for(IFix i : fixes)
		{
			if(i.getId().equalsIgnoreCase(name))
			{
				i.run(sender, args);
				return;
			}
		}

		for(IFix i : fixes)
		{
			for(String j : i.getAliases())
			{
				if(j.equalsIgnoreCase(name))
				{
					i.run(sender, args);
					return;
				}
			}
		}

		Gate.msgError(sender, "Unknown fix '" + C.RED + name + C.GRAY + "'");
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{

	}

	@Override
	public int getInterval()
	{
		return 1612;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
