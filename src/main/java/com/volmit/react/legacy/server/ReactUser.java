package com.volmit.react.legacy.server;

import java.io.File;

import org.apache.commons.lang3.StringUtils;

import com.volmit.react.React;
import com.volmit.react.util.DataCluster;
import com.volmit.react.util.YamlDataInput;
import com.volmit.react.util.YamlDataOutput;

public class ReactUser
{
	private String username;
	private String password;
	private boolean canViewConsole;
	private boolean canUseConsole;
	private boolean canUseActions;
	private boolean enabled;
	private DataCluster cc;

	public ReactUser(String username)
	{
		this.username = username;
		password = "unknown";
		canViewConsole = true;
		canUseActions = false;
		canUseConsole = false;
		enabled = false;
		cc = new DataCluster();
	}

	public void onNewConfig(DataCluster cc)
	{
		cc.set("username", username);
		cc.set("password", password);
		cc.set("permissions.console.view", canViewConsole);
		cc.set("permissions.console.use", canUseConsole);
		cc.set("permissions.actions.use", canUseActions);
		cc.set("enabled", enabled);
		cc.comment("enabled", "Copy this file and make new users.\nMake sure the file name matches the username.\nSet enabled to true to turn on users");
	}

	public void onReadConfig()
	{
		username = cc.getString("username");
		password = cc.getString("password");
		canViewConsole = cc.getBoolean("permissions.console.view");
		canUseConsole = cc.getBoolean("permissions.console.use");
		canUseActions = cc.getBoolean("permissions.actions.use");
		enabled = cc.getBoolean("enabled");
	}

	public DataCluster getConfiguration()
	{
		return cc;
	}

	public String getCodeName()
	{
		return username;
	}

	public void reload()
	{
		File ff = new File(new File(React.instance().getDataFolder(), "remote-users"), getCodeName() + ".yml");
		DataCluster cc = new DataCluster();
		onNewConfig(cc);

		if(ff.exists())
		{
			DataCluster cv = new YamlDataInput().read(ff);

			for(String i : cv.keys())
			{
				cc.trySet(i, cv.get(i));
			}
		}

		new YamlDataOutput().write(cc, ff);
		this.cc = cc;
		onReadConfig();
	}

	public String getUsername()
	{
		return username;
	}

	public String getPassword()
	{
		return password;
	}

	public boolean canViewConsole()
	{
		return canViewConsole;
	}

	public boolean canUseConsole()
	{
		return canUseConsole;
	}

	public boolean canUseActions()
	{
		return canUseActions;
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	@Override
	public String toString()
	{
		cc.set("password", StringUtils.repeat("*", cc.getString("password").length()));
		return cc.toFileConfiguration().saveToString();
	}
}
