package com.volmit.react.controller;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Surge;
import com.volmit.react.nms.INMSBinding;
import com.volmit.react.nms.NMSBinding10;
import com.volmit.react.nms.NMSBinding11;
import com.volmit.react.nms.NMSBinding12;
import com.volmit.react.nms.NMSBinding92;
import com.volmit.react.nms.NMSBinding94;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.MaterialBlock;
import com.volmit.react.util.Protocol;

public class FeatureController extends Controller implements INMSBinding
{
	private INMSBinding trueBinding;

	@Override
	public void dump(JSONObject object)
	{
		if(trueBinding == null)
		{
			object.put("nms-binding", "NO BINDING!");
			return;
		}

		object.put("nms-binding", trueBinding.getClass().getSimpleName());
	}

	@Override
	public void start()
	{
		Surge.register(this);
		try
		{
			if(Protocol.R1_9.to(Protocol.LATEST).contains(Protocol.getProtocolVersion()))
			{
				if(Protocol.R1_9.to(Protocol.R1_9_2).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding92(getPackageVersion());
				}

				else if(Protocol.R1_9_3.to(Protocol.R1_9_4).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding94(getPackageVersion());
				}

				else if(Protocol.R1_10.to(Protocol.R1_10_2).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding10(getPackageVersion());
				}

				else if(Protocol.R1_11.to(Protocol.R1_11_2).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding11(getPackageVersion());
				}

				else if(Protocol.R1_12.to(Protocol.R1_12_2).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding12(getPackageVersion());
				}

				else if(Protocol.R1_13.to(Protocol.LATEST).contains(Protocol.getProtocolVersion()))
				{
					// trueBinding = new NMSBinding13(getPackageVersion());
					// TODO Needs a bind
					Gate.safe = true;
					Config.SAFE_MODE_NMS = true;
					trueBinding = null;
				}
				else
				{
					trueBinding = null;
				}
			}

			else
			{
				trueBinding = null;
			}
		}
		catch(Throwable e)
		{
			System.out.println("No NMS Binder Found i cri 1.13");
		}
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{

	}

	public static String findPackageVersion()
	{
		return Bukkit.getServer().getClass().toString().split("\\.")[3];
	}

	@Override
	public String getPackageVersion()
	{
		return findPackageVersion();
	}

	public boolean hasBinding()
	{
		if(Config.SAFE_MODE_NMS)
		{
			return false;
		}

		return trueBinding != null;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setBlock(Location l, MaterialBlock m)
	{
		if(hasBinding())
		{
			trueBinding.setBlock(l, m);

			for(Player i : l.getWorld().getPlayers())
			{
				if(i.getLocation().distanceSquared(l) <= Math.pow(Bukkit.getViewDistance() * 16, 2))
				{
					i.sendBlockChange(l, m.getMaterial(), m.getData());
				}
			}
		}

		else
		{
			l.getBlock().setType(m.getMaterial());
			l.getBlock().setData(m.getData());
		}
	}

	@Override
	public void updateBlock(Block b)
	{
		if(hasBinding())
		{
			trueBinding.updateBlock(b);
		}
	}

	@Override
	public void merge(Entity drop, Entity into)
	{
		if(hasBinding())
		{
			trueBinding.merge(drop, into);
		}
	}

	@Override
	public int getInterval()
	{
		return 2212;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
