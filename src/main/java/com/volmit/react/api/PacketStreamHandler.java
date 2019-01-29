package com.volmit.react.api;

import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketOutputHandler;
import com.volmit.react.ReactPlugin;
import com.volmit.volume.lang.collections.GMap;

public class PacketStreamHandler implements PacketOutputHandler
{
	public static long wrote = 0;
	public static GMap<PacketType, Long> avgPacketSize = new GMap<PacketType, Long>();

	@Override
	public ListenerPriority getPriority()
	{
		return ListenerPriority.HIGHEST;
	}

	@Override
	public Plugin getPlugin()
	{
		return ReactPlugin.i;
	}

	@Override
	public byte[] handle(PacketEvent event, byte[] buffer)
	{
		avgPacketSize.put(event.getPacketType(), (long) buffer.length);
		wrote += buffer.length;
		return buffer;
	}

}
