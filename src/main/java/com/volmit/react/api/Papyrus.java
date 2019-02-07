package com.volmit.react.api;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import com.volmit.react.util.A;
import com.volmit.react.util.Ex;
import com.volmit.react.util.Protocol;

import primal.lang.collection.GList;

public class Papyrus extends MapRenderer implements IPapyrus
{
	private MapView map;
	private BufferedFrame frameBuffer;
	private BufferedFrame lastFrameBuffer;
	private GList<IRenderer> renderers;

	public Papyrus(World world)
	{
		map = createMap(world);
		frameBuffer = new BufferedFrame();
		lastFrameBuffer = new BufferedFrame();
		renderers = new GList<IRenderer>();

		for(MapRenderer i : map.getRenderers())
		{
			map.removeRenderer(i);
		}

		map.addRenderer(this);
	}

	private MapView createMap(World world)
	{
		return Bukkit.createMap(world);
	}

	@Override
	public MapView getView()
	{
		return map;
	}

	@Override
	public BufferedFrame getFrameBuffer()
	{
		return frameBuffer;
	}

	@Override
	public void addRenderer(IRenderer renderer)
	{
		renderers.add(renderer);
	}

	@Override
	public void clearRenderers()
	{
		renderers.clear();
	}

	@Override
	public GList<IRenderer> getRenderers()
	{
		return renderers;
	}

	@Override
	public void removeRenderer(IRenderer renderer)
	{
		renderers.remove(renderer);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void render(MapView v, MapCanvas c, Player p)
	{
		if(v.getId() == map.getId() && v.getWorld().equals(map.getWorld()))
		{
			new A()
			{
				@Override
				public void run()
				{
					frameBuffer.write(FrameColor.TRANSPARENT);

					for(IRenderer i : renderers)
					{
						i.draw(frameBuffer, c, v);
					}

					int i;
					int j;
					byte[][] raw = frameBuffer.getRawFrame();
					byte[][] lastRaw = lastFrameBuffer.getRawFrame();

					for(i = 0; i < 128; i++)
					{
						for(j = 0; j < 128; j++)
						{
							if(raw[i][j] != lastRaw[i][j])
							{
								c.setPixel(i, j, raw[i][j]);
							}
						}
					}
				}
			};
		}
	}

	@Override
	public void destroy()
	{

	}

	@SuppressWarnings("deprecation")
	@Override
	public ItemStack makeMapItem()
	{
		ItemStack is = new ItemStack(Material.MAP);

		if(Protocol.isNewAPI())
		{
			MapMeta mm = (MapMeta) is.getItemMeta();

			try
			{
				Method m = mm.getClass().getMethod("setMapId", int.class);
				m.setAccessible(true);
				m.invoke(mm, (int) map.getId());
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}

			is.setItemMeta(mm);
		}

		else
		{
			is.setDurability(map.getId());
		}

		return is;
	}
}
