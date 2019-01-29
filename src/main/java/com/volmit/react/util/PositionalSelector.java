package com.volmit.react.util;

import org.bukkit.Chunk;
import org.bukkit.World;

import com.volmit.volume.lang.collections.GSet;

public class PositionalSelector
{
	private GSet<Chunk> allowedChunks;

	public PositionalSelector()
	{
		allowedChunks = new GSet<Chunk>();
	}

	public PositionalSelector(Chunk c)
	{
		this();
		addChunk(c);
	}

	public PositionalSelector(World w)
	{
		addWorld(w);
	}

	public void addWorld(World w)
	{
		allowedChunks.addAll(Chunks.getLoadedChunks(w));
	}

	public void addAllWorlds()
	{
		allowedChunks.addAll(Chunks.getLoadedChunks());
	}

	public void addChunk(Chunk c)
	{
		allowedChunks.add(c);
	}

	public GSet<Chunk> getAllowedChunks()
	{
		return allowedChunks;
	}
}
