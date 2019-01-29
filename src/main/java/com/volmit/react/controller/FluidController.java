package com.volmit.react.controller;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

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

public class FluidController extends Controller
{
	private GSet<Material> ignore;
	private int flowsPerTick;
	private int flowsPerSecond;
	private Average aFST;
	private Average aFSS;
	private Average aFSMS;
	private boolean firstTickList;
	private long firstTick;
	private long lastTick;
	private GSet<Chunk> frozen;
	private GMap<Chunk, GSet<Block>> queue;
	private GMap<Chunk, Long> freezeTimes;

	@Override
	public void dump(JSONObject object)
	{
		object.put("queue", queue.size() + " Chunks");
		object.put("frozen", frozen.size() + " Chunks");
	}

	@Override
	public void start()
	{
		Surge.register(this);
		queue = new GMap<Chunk, GSet<Block>>();
		freezeTimes = new GMap<Chunk, Long>();
		frozen = new GSet<Chunk>();
		flowsPerTick = 0;
		flowsPerSecond = 0;
		aFST = new Average(15);
		aFSS = new Average(3);
		aFSMS = new Average(20);
		firstTickList = false;
		firstTick = M.ns();
		lastTick = M.ns();
		ignore = new GSet<Material>();
		ignore.add(Material.LAVA);
		ignore.add(Material.WATER);
		ignore.add(Material.STATIONARY_WATER);
		ignore.add(Material.STATIONARY_LAVA);
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
		checkChunks();
		aFST.put(flowsPerTick);
		aFSS.put(flowsPerSecond);
		flowsPerTick = 0;
		flowsPerSecond = 0;
		flushTickList();
	}

	private void flushTickList()
	{
		if(lastTick < firstTick)
		{
			firstTick = lastTick;
		}

		aFSMS.put(lastTick - firstTick);
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
	public void on(BlockPhysicsEvent e)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		if(ignore.contains(e.getBlock().getType()))
		{
			if(isFrozen(e.getBlock().getChunk()))
			{
				e.setCancelled(true);
				queue(e.getBlock());
			}

			else
			{

				tickNextTickList();
				flowsPerSecond++;
				flowsPerTick++;
				React.instance.physicsController.onFluid(e.getBlock().getChunk());
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(BlockFromToEvent e)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		if(ignore.contains(e.getToBlock().getType()))
		{
			if(isFrozen(e.getToBlock().getChunk()))
			{
				e.setCancelled(true);
				queue(e.getToBlock());
			}

			else
			{

				tickNextTickList();
				flowsPerSecond++;
				flowsPerTick++;
				React.instance.physicsController.onFluid(e.getToBlock().getChunk());
			}
		}

		if(ignore.contains(e.getBlock().getType()))
		{
			if(isFrozen(e.getBlock().getChunk()))
			{
				e.setCancelled(true);
				queue(e.getBlock());
			}

			else
			{

				tickNextTickList();
				flowsPerSecond++;
				flowsPerTick++;
				React.instance.physicsController.onFluid(e.getBlock().getChunk());
			}
		}
	}

	public GSet<Material> getIgnore()
	{
		return ignore;
	}

	public int getTransfersPerTick()
	{
		return flowsPerTick;
	}

	public int getTransfersPerSecond()
	{
		return flowsPerSecond;
	}

	public Average getaFST()
	{
		return aFST;
	}

	public Average getaFSS()
	{
		return aFSS;
	}

	public Average getaFSMS()
	{
		return aFSMS;
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
