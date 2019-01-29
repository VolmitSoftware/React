package com.volmit.react.nms;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import com.volmit.react.util.MaterialBlock;

public interface INMSBinding
{
	public String getPackageVersion();

	public void updateBlock(Block b);

	public void setBlock(Location l, MaterialBlock m);

	public void merge(Entity drop, Entity into);
}
