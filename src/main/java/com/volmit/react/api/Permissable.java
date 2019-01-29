package com.volmit.react.api;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.util.Ex;
import com.volmit.volume.lang.collections.GList;

public enum Permissable
{
	ACCESS(Info.PERM_ACCESS),
	TPS(Info.PERM_TPS),
	SYSTEMINFO(Info.PERM_SYSTEMINFO),
	TELEPORT(Info.PERM_TELEPORT),
	MONITOR(Info.PERM_MONITOR),
	MONITOR_TITLE(Info.PERM_MONITOR_TITLE),
	MONITOR_ACTIONLOG(Info.PERM_MONITOR_TITLE),
	MONITOR_MAP(Info.PERM_MONITOR_MAP),
	MONITOR_ENVIRONMENT(Info.PERM_MONITOR_ENVIRONMENT),
	MONITOR_GLASSES(Info.PERM_MONITOR_GLASSES),
	MONITOR_CHUNK_BLAME(Info.PERM_MONITOR_CHUNK_BLAME),
	RAI_MONITOR(Info.PERM_RAI_MONITOR),
	RAI_CONTROL(Info.PERM_RAI_CONTROL),
	RAI_ACCESS(Info.PERM_RAI_ACCESS),
	PING(Info.PERM_PING),
	PING_OTHERS(Info.PERM_PING_OTHERS),
	RAI(Info.PERM_RAI),
	ACT(Info.PERM_ACT),
	RELOAD(Info.PERM_RELOAD);

	private String node;
	public static GList<TemporaryAccessor> accessors = new GList<TemporaryAccessor>();

	private Permissable(String s)
	{
		try
		{
			node = Info.CORE_REACT_DOT + s;
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public static void addAccessor(TemporaryAccessor t)
	{
		if(!isAccessor(t.getPlayer()))
		{
			accessors.add(t);
			Gate.msgSuccess(t.getPlayer(), "You have been granted temporary access to React");
		}
	}

	public static void removeAccesssor(Player p)
	{
		if(isAccessor(p))
		{
			for(TemporaryAccessor i : accessors.copy())
			{
				if(i.getPlayer().equals(p))
				{
					accessors.remove(i);
					Gate.msgActing(i.getPlayer(), "Temporary access expired.");
				}
			}
		}
	}

	public static boolean isAccessor(Player p)
	{
		for(TemporaryAccessor i : accessors)
		{
			if(i.getPlayer().equals(p))
			{
				return true;
			}
		}

		return false;
	}

	public boolean has(CommandSender p)
	{
		if(!p.hasPermission(getNode()))
		{
			if(p instanceof Player)
			{
				for(TemporaryAccessor i : accessors)
				{
					if(i.getPlayer().equals(p) && i.getPermissions().contains(this))
					{
						return true;
					}
				}
			}

			return false;
		}

		return true;
	}

	public String getNode()
	{
		return node;
	}
}
