package com.volmit.react.controller;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.React;
import com.volmit.react.Surge;
import com.volmit.react.util.Average;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.M;
import com.volmit.react.util.RedstoneTracker;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.lang.collections.GSet;

public class RedstoneController extends Controller
{
	private GSet<Material> ignore;
	private GSet<Chunk> frozen;
	private GMap<Chunk, GSet<Block>> queue;
	private GMap<Chunk, Long> freezeTimes;
	private int redstonePerTick;
	private int redstonePerSecond;
	private Average aRST;
	private Average aRSS;
	private Average aRSMS;
	private boolean firstTickList;
	private long firstTick;
	private long lastTick;
	private GMap<World, RedstoneTracker> trackers;

	@Override
	public void dump(JSONObject object)
	{
		object.put("frozen", frozen.size() + " Chunks");
		object.put("queue", queue.size() + " Chunks");
	}

	@Override
	public void start()
	{
		Surge.register(this);
		queue = new GMap<Chunk, GSet<Block>>();
		freezeTimes = new GMap<Chunk, Long>();
		frozen = new GSet<Chunk>();
		ignore = new GSet<Material>();
		ignore.add(Material.REDSTONE_WIRE);
		ignore.add(Material.REDSTONE_TORCH_OFF);
		ignore.add(Material.REDSTONE_TORCH_ON);
		ignore.add(Material.REDSTONE_COMPARATOR_OFF);
		ignore.add(Material.REDSTONE_COMPARATOR_ON);
		ignore.add(Material.REDSTONE_LAMP_OFF);
		ignore.add(Material.REDSTONE_LAMP_ON);
		ignore.add(Material.REDSTONE_BLOCK);
		ignore.add(Material.DIODE_BLOCK_OFF);
		ignore.add(Material.DIODE_BLOCK_ON);
		ignore.add(Material.LEVER);
		ignore.add(Material.STONE_BUTTON);
		ignore.add(Material.WOOD_BUTTON);
		ignore.add(Material.GOLD_PLATE);
		ignore.add(Material.IRON_PLATE);
		ignore.add(Material.STONE_PLATE);
		ignore.add(Material.WOOD_PLATE);
		redstonePerTick = 0;
		redstonePerSecond = 0;
		aRST = new Average(15);
		aRSS = new Average(3);
		aRSMS = new Average(20);
		firstTickList = false;
		firstTick = M.ns();
		lastTick = M.ns();
		trackers = new GMap<World, RedstoneTracker>();

		for(World i : Bukkit.getWorlds())
		{
			trackers.put(i, new RedstoneTracker(i));
		}
	}

	@EventHandler
	public void on(WorldLoadEvent e)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		trackers.put(e.getWorld(), new RedstoneTracker(e.getWorld()));
	}

	@EventHandler
	public void on(WorldUnloadEvent e)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		trackers.get(e.getWorld()).close();
		trackers.remove(e.getWorld());
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);

		for(Chunk i : new GList<Chunk>(frozen))
		{
			releaseChunk(i);
		}
	}

	public void releaseChunk(Chunk i)
	{
		if(isFrozen(i))
		{
			unfreeze(i);
			checkChunk(i);
		}
	}

	@Override
	public void tick()
	{
		if(Gate.lowMemoryMode)
		{
			return;
		}

		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		checkChunks();
		aRST.put(redstonePerTick);
		aRSS.put(redstonePerSecond);
		redstonePerTick = 0;
		redstonePerSecond = 0;
		flushTickList();
	}

	private void checkChunks()
	{
		GSet<Chunk> cx = new GSet<Chunk>();
		cx.addAll(queue.k());
		cx.addAll(frozen);

		for(Chunk i : cx)
		{
			checkChunk(i);
		}
	}

	private void checkChunk(Chunk i)
	{
		if(!isFrozen(i))
		{
			if(queue.containsKey(i))
			{
				if(Config.UNLOCKING)
				{
					for(Block j : queue.get(i))
					{
						Gate.updateBlock(j);
					}
				}

				queue.remove(i);
			}
		}

		if(isFrozen(i) && M.ms() >= freezeTimes.get(i))
		{
			releaseChunk(i);
		}
	}

	public void queue(Block b)
	{
		if(Gate.lowMemoryMode)
		{
			return;
		}
		if(!queue.containsKey(b.getChunk()))
		{
			queue.put(b.getChunk(), new GSet<Block>());
		}

		queue.get(b.getChunk()).add(b);
	}

	public boolean isFrozen(Chunk c)
	{
		return frozen.contains(c);
	}

	public void freeze(Chunk c)
	{
		freeze(c, 10000);
	}

	private void flushTickList()
	{
		if(lastTick < firstTick)
		{
			firstTick = lastTick;
		}

		aRSMS.put(lastTick - firstTick);
		firstTickList = false;
	}

	private void tickNextTickList()
	{
		if(!firstTickList)
		{
			firstTickList = true;
			firstTick = M.ns();
		}

		else
		{
			lastTick = M.ns();
		}
	}

	public void unfreeze(Chunk c)
	{
		if(!frozen.contains(c))
		{
			return;
		}

		frozen.remove(c);
	}

	public void freeze(Chunk c, long ms)
	{
		if(isFrozen(c))
		{
			return;
		}

		frozen.add(c);
		freezeTimes.put(c, M.ms() + ms);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(ChunkUnloadEvent e)
	{
		if(Gate.lowMemoryMode)
		{
			return;
		}
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		releaseChunk(e.getChunk());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(BlockPhysicsEvent e)
	{
		if(Gate.lowMemoryMode)
		{
			return;
		}
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}
		if(ignore.contains(e.getChangedType()))
		{
			tickNextTickList();
			React.instance.physicsController.onRedstone(e.getBlock().getChunk());
			redstonePerSecond++;
			redstonePerTick++;

			if(isFrozen(e.getBlock().getChunk()))
			{
				queue(e.getBlock());
				e.setCancelled(true);
			}
		}
	}

	public GSet<Material> getIgnore()
	{
		return ignore;
	}

	public GSet<Chunk> getFrozen()
	{
		return frozen;
	}

	public GMap<Chunk, GSet<Block>> getQueue()
	{
		return queue;
	}

	public GMap<Chunk, Long> getFreezeTimes()
	{
		return freezeTimes;
	}

	public int getRedstonePerTick()
	{
		return redstonePerTick;
	}

	public int getRedstonePerSecond()
	{
		return redstonePerSecond;
	}

	public Average getaRST()
	{
		return aRST;
	}

	public Average getaRSS()
	{
		return aRSS;
	}

	public Average getaRSMS()
	{
		return aRSMS;
	}

	public boolean isFirstTickList()
	{
		return firstTickList;
	}

	public long getFirstTick()
	{
		return firstTick;
	}

	public long getLastTick()
	{
		return lastTick;
	}

	@Override
	public int getInterval()
	{
		return 1;
	}

	@Override
	public boolean isUrgent()
	{
		return true;
	}
}
