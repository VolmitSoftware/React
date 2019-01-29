package com.volmit.react.util;

import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.util.Vector;

public class RedstoneEvent
{
	private MaterialBlock block;
	private Vector vector;
	private byte from;
	private byte to;

	@SuppressWarnings("deprecation")
	public RedstoneEvent(BlockRedstoneEvent e)
	{
		block = new MaterialBlock(e.getBlock().getType(), e.getBlock().getData());
		vector = new Vector(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ());
		from = (byte) e.getOldCurrent();
		to = (byte) e.getNewCurrent();
	}

	public MaterialBlock getBlock()
	{
		return block;
	}

	public void setBlock(MaterialBlock block)
	{
		this.block = block;
	}

	public Vector getVector()
	{
		return vector;
	}

	public void setVector(Vector vector)
	{
		this.vector = vector;
	}

	public byte getFrom()
	{
		return from;
	}

	public void setFrom(byte from)
	{
		this.from = from;
	}

	public byte getTo()
	{
		return to;
	}

	public void setTo(byte to)
	{
		this.to = to;
	}
}
