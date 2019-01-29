package com.volmit.react.legacy.server;

import org.bukkit.configuration.file.FileConfiguration;

public class FCCallback implements Runnable
{
	private FileConfiguration fc;
	
	public void run(FileConfiguration fc)
	{
		this.fc = fc;
		run();
	}
	
	@Override
	public void run()
	{
		
	}
	
	public FileConfiguration fc()
	{
		return fc;
	}
}
