package com.volmit.react.api;

import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import com.volmit.volume.lang.collections.GList;

public class NobodySender implements CommandSender
{
	private GList<String> in;

	public NobodySender()
	{
		in = new GList<String>();
	}

	public void dump()
	{
		in.clear();
	}

	public String pump()
	{
		if(in.isEmpty())
		{
			return null;
		}

		return in.pop();
	}

	@Override
	public PermissionAttachment addAttachment(Plugin arg0)
	{
		return Bukkit.getConsoleSender().addAttachment(arg0);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin arg0, int arg1)
	{
		return Bukkit.getConsoleSender().addAttachment(arg0, arg1);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin arg0, String arg1, boolean arg2)
	{
		return Bukkit.getConsoleSender().addAttachment(arg0, arg1, arg2);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin arg0, String arg1, boolean arg2, int arg3)
	{
		return Bukkit.getConsoleSender().addAttachment(arg0, arg1, arg2, arg3);
	}

	@Override
	public Set<PermissionAttachmentInfo> getEffectivePermissions()
	{
		return Bukkit.getConsoleSender().getEffectivePermissions();
	}

	@Override
	public boolean hasPermission(String arg0)
	{
		return true;
	}

	@Override
	public boolean hasPermission(Permission arg0)
	{
		return true;
	}

	@Override
	public boolean isPermissionSet(String arg0)
	{
		return Bukkit.getConsoleSender().isPermissionSet(arg0);
	}

	@Override
	public boolean isPermissionSet(Permission arg0)
	{
		return Bukkit.getConsoleSender().isPermissionSet(arg0);
	}

	@Override
	public void recalculatePermissions()
	{
		Bukkit.getConsoleSender().recalculatePermissions();
	}

	@Override
	public void removeAttachment(PermissionAttachment arg0)
	{
		Bukkit.getConsoleSender().removeAttachment(arg0);
	}

	@Override
	public boolean isOp()
	{
		return true;
	}

	@Override
	public void setOp(boolean arg0)
	{
		// No
	}

	@Override
	public String getName()
	{
		return "Nobody";
	}

	@Override
	public Server getServer()
	{
		return Bukkit.getServer();
	}

	@Override
	public void sendMessage(String arg0)
	{
		in.add(arg0);
	}

	@Override
	public void sendMessage(String[] arg0)
	{
		for(String i : arg0)
		{
			sendMessage(i);
		}
	}

	@Override
	public Spigot spigot()
	{
		return Bukkit.getConsoleSender().spigot();
	}
}
