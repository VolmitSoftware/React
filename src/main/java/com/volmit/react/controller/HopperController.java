package com.volmit.react.controller;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.React;
import com.volmit.react.Surge;
import com.volmit.react.util.Average;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.M;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.lang.collections.GSet;

public class HopperController extends Controller
{
	private GSet<Material> ignore;
	private int transfersPerTick;
	private int transfersPerSecond;
	private Average aHST;
	private Average aHSS;
	private Average aHSMS;
	private boolean firstTickList;
	private long firstTick;
	private long lastTick;
	private GSet<Chunk> frozen;
	private GMap<Chunk, GSet<Block>> queue;
	private GMap<Chunk, Long> freezeTimes;

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
		transfersPerTick = 0;
		transfersPerSecond = 0;
		aHST = new Average(15);
		aHSS = new Average(3);
		aHSMS = new Average(20);
		firstTickList = false;
		firstTick = M.ns();
		lastTick = M.ns();
		ignore = new GSet<Material>();
		ignore.add(Material.HOPPER);
	}

	public void releaseChunk(Chunk i)
	{
		if(isFrozen(i))
		{
			unfreeze(i);
			checkChunk(i);
		}
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

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		checkChunks();
		aHST.put(transfersPerTick);
		aHSS.put(transfersPerSecond);
		transfersPerTick = 0;
		transfersPerSecond = 0;
		flushTickList();
	}

	private void flushTickList()
	{
		if(lastTick < firstTick)
		{
			firstTick = lastTick;
		}

		aHSMS.put(lastTick - firstTick);
		lastTick = M.ms();
		firstTick = lastTick;
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

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(InventoryMoveItemEvent e)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		if(e.getDestination().getHolder() instanceof BlockState)
		{
			if(ignore.contains(((BlockState) e.getDestination().getHolder()).getBlock().getType()))
			{
				if(isFrozen(((BlockState) e.getDestination().getHolder()).getBlock().getChunk()))
				{
					queue(((BlockState) e.getDestination().getHolder()).getBlock());
					e.setCancelled(true);
				}

				else
				{
					tickNextTickList();
					transfersPerSecond++;
					transfersPerTick++;
					React.instance.physicsController.onHopper(((BlockState) e.getDestination().getHolder()).getBlock().getChunk());
				}
			}
		}
	}

	public GSet<Material> getIgnore()
	{
		return ignore;
	}

	public int getTransfersPerTick()
	{
		return transfersPerTick;
	}

	public int getTransfersPerSecond()
	{
		return transfersPerSecond;
	}

	public Average getaHST()
	{
		return aHST;
	}

	public Average getaHSS()
	{
		return aHSS;
	}

	public Average getaHSMS()
	{
		return aHSMS;
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
