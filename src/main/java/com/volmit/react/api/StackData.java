package com.volmit.react.api;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.entity.LivingEntity;

import com.volmit.react.React;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.lang.collections.GSet;

public class StackData
{
	private GMap<UUID, Integer> emap;
	private GSet<UUID> touched;

	public StackData()
	{
		emap = new GMap<UUID, Integer>();
		touched = new GSet<UUID>();
	}

	public GMap<UUID, Integer> getEmap()
	{
		return emap;
	}

	public void setEmap(GMap<UUID, Integer> emap)
	{
		this.emap = emap;
	}

	public GSet<UUID> getTouched()
	{
		return touched;
	}

	public void setTouched(GSet<UUID> touched)
	{
		this.touched = touched;
	}

	public void clear()
	{
		for(UUID i : emap.k())
		{
			if(!touched.contains(i))
			{
				emap.remove(i);
			}
		}
	}

	public void save(File f) throws IOException
	{
		FileOutputStream fos = new FileOutputStream(f);
		GZIPOutputStream gzo = new GZIPOutputStream(fos);
		DataOutputStream dos = new DataOutputStream(gzo);
		dos.writeInt(emap.size());

		for(UUID i : emap.k())
		{
			dos.writeLong(i.getMostSignificantBits());
			dos.writeLong(i.getLeastSignificantBits());
			dos.writeShort(emap.get(i));
		}

		dos.close();
	}

	public void load(File f) throws IOException
	{
		emap.clear();
		FileInputStream fin = new FileInputStream(f);
		GZIPInputStream gzi = new GZIPInputStream(fin);
		DataInputStream din = new DataInputStream(gzi);
		int s = din.readInt();

		for(int i = 0; i < s; i++)
		{
			emap.put(new UUID(din.readLong(), din.readLong()), (int) din.readShort());
		}

		din.close();
	}

	public void put(LivingEntity e)
	{
		if(React.instance.entityStackController.isStacked(e))
		{
			StackedEntity se = React.instance.entityStackController.getStack(e);
			emap.put(e.getUniqueId(), se.getCount());
		}

		else
		{
			emap.remove(e.getUniqueId());
		}

		touched.add(e.getUniqueId());
	}

	public int get(LivingEntity e)
	{
		touched.add(e.getUniqueId());

		if(emap.containsKey(e.getUniqueId()))
		{
			return emap.get(e.getUniqueId());
		}

		return 0;
	}
}
