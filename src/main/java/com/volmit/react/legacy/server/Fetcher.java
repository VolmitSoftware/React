package com.volmit.react.legacy.server;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class Fetcher extends Thread
{
	private String url;
	private FCCallback callback;

	public Fetcher(String s, FCCallback callback)
	{
		this.url = s;
		this.callback = callback;
	}

	@Override
	public void run()
	{
		FileConfiguration fc = new YamlConfiguration();

		try
		{
			fc.load(new InputStreamReader(new URL(url).openStream()));
			callback.run(fc);
		}

		catch(IOException e)
		{
			return;
		}

		catch(InvalidConfigurationException e)
		{
			return;
		}
	}
}
