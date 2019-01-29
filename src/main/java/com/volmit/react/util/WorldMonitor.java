package com.volmit.react.util;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import com.volmit.react.Surge;
import com.volmit.volume.lang.collections.GList;

@SuppressWarnings("deprecation")
public abstract class WorldMonitor implements Listener
{
	private boolean chunksChanged = true;
	private boolean dropChanged = true;
	private boolean tileChanged = true;
	private boolean livingChanged = true;
	private boolean totalChanged = true;
	private boolean updated = false;
	private int totalChunks = 0;
	private int totalDrops = 0;
	private int totalTiles = 0;
	private int totalLiving = 0;
	private int totalEntities = 0;
	private int chunksLoaded = 0;
	private int chunksUnloaded = 0;
	private int rollingTileCount = 0;
	private long ms = M.ms();
	private GList<Chunk> pending;

	public WorldMonitor()
	{
		Surge.register(this);
		pending = new GList<Chunk>();
	}

	public void run()
	{
		if(TICK.tick < 20)
		{
			return;
		}

		try
		{
			sample();
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public abstract void updated(int totalChunks, int totalDrops, int totalTiles, int totalLiving, int totalEntities, int chunksLoaded, int chunksUnloaded);

	@EventHandler
	public void on(ChunkLoadEvent e)
	{
		chunksChanged = true;
		tileChanged = true;
		livingChanged = true;
		dropChanged = true;
		chunksLoaded++;
	}

	@EventHandler
	public void on(ChunkUnloadEvent e)
	{
		chunksChanged = true;
		tileChanged = true;
		livingChanged = true;
		dropChanged = true;
		chunksUnloaded++;
	}

	@EventHandler
	public void on(EntitySpawnEvent e)
	{
		livingChanged = true;
	}

	@EventHandler
	public void on(EntityDeathEvent e)
	{
		livingChanged = true;
	}

	@EventHandler
	public void on(PlayerDropItemEvent e)
	{
		dropChanged = true;
	}

	@EventHandler
	public void on(PlayerPickupItemEvent e)
	{
		dropChanged = true;
	}

	@EventHandler
	public void on(BlockPlaceEvent e)
	{
		tileChanged = true;
	}

	@EventHandler
	public void on(BlockBreakEvent e)
	{
		tileChanged = true;
	}

	private void doUpdate()
	{
		updated = true;
	}

	private void sample()
	{
		if(chunksChanged || dropChanged || tileChanged || livingChanged)
		{
			totalChanged = true;
		}

		try
		{
			if(chunksChanged)
			{
				sampleChunkCount();
				chunksChanged = false;
				doUpdate();
			}

		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		try
		{
			if(dropChanged)
			{
				sampleDropCount();
				dropChanged = false;
				doUpdate();
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		try
		{
			if(tileChanged)
			{
				sampleTileCount();
				doUpdate();
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		try
		{
			if(livingChanged)
			{
				sampleLivingCount();
				livingChanged = false;
				doUpdate();
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		try
		{
			if(totalChanged)
			{
				sampleTotalCount();
				totalChanged = false;
				doUpdate();
			}

		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		try
		{
			if(updated || M.ms() - ms > 1000)
			{
				updated(totalChunks, totalDrops, totalTiles, totalLiving, totalEntities, chunksLoaded, chunksUnloaded);

				if(M.ms() - ms > 1000)
				{
					ms = M.ms();
					chunksLoaded = 0;
					chunksLoaded = 0;
				}
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	private void sampleTotalCount()
	{
		totalEntities = 0;

		for(World i : Bukkit.getWorlds())
		{
			try
			{
				totalEntities += i.getEntities().size();
			}

			catch(Throwable e)
			{

			}
		}
	}

	private void sampleLivingCount()
	{
		totalLiving = 0;

		for(World i : Bukkit.getWorlds())
		{
			try
			{
				totalLiving += i.getLivingEntities().size();
			}

			catch(Throwable e)
			{

			}
		}
	}

	private void sampleTileCount()
	{
		if(pending.isEmpty())
		{
			totalTiles = rollingTileCount;
			rollingTileCount = 0;

			for(World i : Bukkit.getWorlds())
			{
				try
				{
					for(Chunk j : i.getLoadedChunks())
					{
						pending.add(j);
					}
				}

				catch(Throwable e)
				{
					Ex.t(e);
				}
			}
		}

		if(!pending.isEmpty())
		{
			new S("tile-count-interval")
			{
				@Override
				public void run()
				{
					long ns = M.ns();

					while(!pending.isEmpty() && M.ns() - ns < 250000)
					{
						Chunk c = pending.pop();

						if(!c.isLoaded())
						{
							continue;
						}

						rollingTileCount += c.getTileEntities().length;
					}

					if(pending.isEmpty())
					{
						tileChanged = false;
					}
				}
			};

			return;
		}
	}

	private void sampleDropCount()
	{
		totalDrops = 0;

		for(World i : Bukkit.getWorlds())
		{
			try
			{
				totalDrops += i.getEntitiesByClass(Item.class).size();
			}

			catch(Throwable e)
			{

			}
		}
	}

	private void sampleChunkCount()
	{
		totalChunks = 0;

		for(World i : Bukkit.getWorlds())
		{
			totalChunks += i.getLoadedChunks().length;
		}
	}
}
