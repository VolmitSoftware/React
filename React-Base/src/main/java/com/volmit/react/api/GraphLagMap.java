package com.volmit.react.api;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.controller.EventController;
import com.volmit.react.util.M;

public class GraphLagMap extends Graph2D
{
	private int grid;
	private Player viewer;
	private Vector lastVector;
	private BufferedFrame last = null;
	private long lastf = M.ms();

	public GraphLagMap(Player viewer, int gridSize)
	{
		super("graph-" + gridSize, 10);
		lastVector = viewer.getLocation().getDirection();
		this.viewer = viewer;
		grid = gridSize;
		zoomIn();
		zoomOut();
	}

	public void zoomIn()
	{
		grid++;

		if(grid > 8)
		{
			grid = 8;
		}
	}

	public void zoomOut()
	{
		grid--;

		if(grid < 1)
		{
			grid = 1;
		}
	}

	@Override
	public void onRender(BufferedFrame aframe)
	{
		if(M.ms() - lastf < 1000.0 / (double) (Gate.isLowMemory() ? Config.GRAPH_FPS / 7 : Config.GRAPH_FPS))
		{
			if(last != null)
			{
				aframe.write(last);
				return;
			}
		}

		lastf = M.ms();
		BufferedFrame frame = new BufferedFrame(aframe.getWidth() * 2, aframe.getHeight() * 2);
		frame.write(FrameColor.WHITE);
		int cx = viewer.getLocation().getChunk().getX();
		int cz = viewer.getLocation().getChunk().getZ();

		for(int i = 0; i < frame.getWidth(); i++)
		{
			for(int j = 0; j < frame.getHeight(); j++)
			{
				int posx = cx - (i / grid) + (frame.getWidth() / (grid * 2));
				int posz = cz - (j / grid) + (frame.getHeight() / (grid * 2));

				if(viewer.getWorld().isChunkLoaded(posx, posz))
				{
					Chunk c = viewer.getWorld().getChunkAt(posx, posz);

					if(EventController.map.getChunks().containsKey(c))
					{
						LagMapChunk lc = EventController.map.getChunks().get(c);
						double max = -1;
						ChunkIssue iss = null;

						if(lc.getHits().isEmpty())
						{
							frame.write(i, j, FrameColor.LIGHT_GREEN);
							continue;
						}

						else if(lc.getHits().size() == 1)
						{
							iss = lc.getHits().k().get(0);
						}

						else
						{
							for(ChunkIssue k : lc.getHits().k())
							{
								if(lc.getHits().get(k) > max)
								{
									max = lc.getHits().get(k);
									iss = k;
								}
							}
						}

						if(iss != null)
						{
							switch(iss)
							{
								case ENTITY:
									frame.write(i, j, FrameColor.matchColor(Color.CYAN));
									break;
								case FLUID:
									frame.write(i, j, FrameColor.BLUE);
									break;
								case HOPPER:
									frame.write(i, j, FrameColor.matchColor(Color.ORANGE));
									break;
								case PHYSICS:
									frame.write(i, j, FrameColor.matchColor(Color.MAGENTA));
									break;
								case REDSTONE:
									frame.write(i, j, FrameColor.RED);
									break;
								case TNT:
									frame.write(i, j, FrameColor.matchColor(Color.YELLOW));
									break;
								default:
									break;
							}
						}

						else
						{
							frame.write(i, j, FrameColor.LIGHT_GREEN);
						}
					}

					else
					{
						frame.write(i, j, FrameColor.LIGHT_GREEN);
					}
				}

				else if(i % grid == 0 || j % grid == 0)
				{
					frame.write(i, j, FrameColor.matchColor(Color.GRAY));
				}
			}
		}

		BufferedImage bu = aframe.toBufferedImage();
		BufferedImage buf = frame.toBufferedImage();
		Graphics g = bu.createGraphics();
		AffineTransform at = new AffineTransform();
		at.translate(bu.getWidth() / 2, bu.getHeight() / 2);
		Vector vvx = viewer.getLocation().getDirection();
		lastVector = lastVector.clone().add(lastVector.clone().multiply(1.7)).add(vvx).normalize();
		at.rotate(lastVector.getZ(), lastVector.getX());
		at.translate(-bu.getWidth(), -bu.getHeight());
		Graphics2D g2d = (Graphics2D) g;
		g2d.drawImage(buf, at, null);
		g2d.dispose();
		aframe.fromBufferedImage(bu);
		last = aframe;
	}
}
