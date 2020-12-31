package com.volmit.react.controller;

import com.volmit.react.nms.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.volmit.react.Config;
import com.volmit.react.Surge;
import com.volmit.react.util.Controller;
import com.volmit.react.util.Protocol;

import primal.json.JSONObject;
import primal.bukkit.world.MaterialBlock;

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
				if(Protocol.R1_9_3.to(Protocol.R1_9_4).contains(Protocol.getProtocolVersion()))
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

				else if(Protocol.R1_13.to(Protocol.R1_13_2).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding13(getPackageVersion());
				}

				else if(Protocol.R1_14.to(Protocol.R1_14_3).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding14(getPackageVersion());
				}

				else if(Protocol.R1_15.to(Protocol.R1_15_2).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding15(getPackageVersion());
				}

				else if(Protocol.R1_16.to(Protocol.R1_16).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding16(getPackageVersion());
				}

				else if(Protocol.R1_16_2.to(Protocol.R1_16_2).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding16_R2(getPackageVersion());
				}

				else if(Protocol.R1_16_3.to(Protocol.LATEST).contains(Protocol.getProtocolVersion()))
				{
					trueBinding = new NMSBinding16_R3(getPackageVersion());
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
			System.out.println("No NMS Binder Found");
			e.printStackTrace();
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
