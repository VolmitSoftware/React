package primal.bukkit.nms;

import net.minecraft.server.v1_14_R1.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_14_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_14_R1.block.CraftBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShadowChunk14 implements ShadowChunk
{
	private final Chunk actual;
	private final org.bukkit.Chunk chunk;
	private Chunk nmsCopy;
	private final boolean skylight;
	private final boolean[] modified;
	private boolean biomeModified;

	public ShadowChunk14(org.bukkit.Chunk chunk)
	{
		this.chunk = chunk;
		actual = ((CraftChunk) chunk).getHandle();
		skylight = chunk.getWorld().getEnvironment().equals(Environment.NORMAL);
		modified = new boolean[16];
		nmsCopy = copy(actual);
		dumpModifications();
	}

	@Override
	public boolean isFullModification()
	{
		return biomeModified;
	}

	@Override
	public void rebaseSection(int section)
	{
		ChunkSection sect = actual.getSections()[section];

		if(sect != null)
		{
			nmsCopy.getSections()[section] = null;

			for(int i = 0; i < 16; i++)
			{
				for(int j = 0; j < 16; j++)
				{
					for(int k = 0; k < 16; k++)
					{
						IBlockData ibd = sect.getBlocks().a(i, j, k);
						int id = Block.REGISTRY_ID.getId(ibd);

						if(id > 0)
						{
							setBlock(i, j, k, id, 0);
						}

						//setBlockLight(i, j, k, sect.getEmittedLightArray().a(i, j, k));

						if(skylight)
						{
						//	setSkyLight(i, j, k, sect.getSkyLightArray().a(i, j, k));
						}
					}
				}
			}

			modified[section] = true;
		}
	}

	@Override
	public void queueSection(int section)
	{
		if(nmsCopy.getSections()[section] != null)
		{
			modified[section] = true;
		}
	}

	@Override
	public void queueBiomes()
	{
		biomeModified = true;
	}

	@Override
	public org.bukkit.Chunk getSource()
	{
		return chunk;
	}

	@Override
	public void rebase()
	{
		biomeModified = !Arrays.equals(nmsCopy.getBiomeIndex(), actual.getBiomeIndex());
		nmsCopy = copy(actual);

		for(int i = 0; i < 16; i++)
		{
			if(nmsCopy.getSections()[i] != null)
			{
				modified[i] = true;
			}
		}
	}

	@Override
	public List<Object> flush()
	{
		List<Object> packets = new ArrayList<>();

		int chunkMask = getEntireMask();
		int modMask = getModifiedMask();

		if(biomeModified)
		{
			packets.add(new PacketPlayOutUnloadChunk(nmsCopy.getPos().x, nmsCopy.getPos().z));
			modMask = chunkMask;
		}

		packets.add(new PacketPlayOutMapChunk(nmsCopy, modMask));
		dumpModifications();

		return packets;
	}

	@Override
	public List<Object> flushSection(int section)
	{
		List<Object> packets = new ArrayList<>();

		if(!modified[section])
		{
			return packets;
		}

		int chunkMask = getEntireMask();
		int modMask = getSingularMask(section);

		if(biomeModified)
		{
			packets.add(new PacketPlayOutUnloadChunk(nmsCopy.getPos().x, nmsCopy.getPos().z));
			modMask = chunkMask;
		}

		packets.add(new PacketPlayOutMapChunk(nmsCopy, modMask));
		biomeModified = false;
		modified[section] = false;

		return packets;
	}

	@Override
	public int getEntireMask()
	{
		int bitMask = 0;

		for(int section = 0; section < nmsCopy.getSections().length; section++)
		{
			if(nmsCopy.getSections()[section] != null)
			{
				bitMask += 1 << section;
			}
		}

		return bitMask;
	}

	@Override
	public int getModifiedMask()
	{
		int bitMask = 0;

		for(int section = 0; section < modified.length; section++)
		{
			if(modified[section])
			{
				bitMask += 1 << section;
			}
		}

		return bitMask;
	}

	@Override
	public void setSkyLight(int x, int y, int z, int light)
	{
		if(!skylight)
		{
			return;
		}

		if(nmsCopy.getSections()[y >> 4] == null)
		{
		//	nmsCopy.getSections()[y >> 4] = new ChunkSection((y >> 4) << 4, skylight);
		}

		//nmsCopy.getSections()[y >> 4].getSkyLightArray().a(x, y & 15, z, light);
		modified[y >> 4] = true;
	}

	@Override
	public void setBlockLight(int x, int y, int z, int light)
	{
		if(nmsCopy.getSections()[y >> 4] == null)
		{
		//	nmsCopy.getSections()[y >> 4] = new ChunkSection((y >> 4) << 4, skylight);
		}

		//nmsCopy.getSections()[y >> 4].getEmittedLightArray().a(x, y & 15, z, light);
		modified[y >> 4] = true;
	}

	@Override
	public void setBiome(int x, int z, Biome bio)
	{
		setBiome(x, z, BiomeBase.c.getId(CraftBlock.biomeToBiomeBase(bio)));
	}

	@Override
	public void setBiome(int x, int z, int id)
	{
		nmsCopy.getBiomeIndex()[(z & 15) << 4 | x & 15] = BiomeBase.c.fromId(id);
		biomeModified = true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setBlock(int x, int y, int z, Material type, int data)
	{
		setBlock(x, y, z, type.getId(), data);
	}

	@Override
	public void setBlock(int x, int y, int z, Material type)
	{
		setBlock(x, y, z, type, 0);
	}

	@Override
	public void setBlock(Location l, Material type, int data)
	{
		setBlock(l.getBlockX() & 15, l.getBlockY(), l.getBlockZ() & 15, type, data);
	}

	@Override
	public void setBlock(Location l, Material type)
	{
		setBlock(l, type, 0);
	}

	@Override
	public void setBlock(int x, int y, int z, int type, int data)
	{
		IBlockData d = Block.REGISTRY_ID.fromId(type).getBlock().getBlockData();
		int section = y >> 4;

		if(nmsCopy.getSections()[section] == null)
		{
		//	nmsCopy.getSections()[section] = new ChunkSection(section << 4, skylight);
		}

		nmsCopy.getSections()[section].setType(x, y & 15, z, d);
		modified[section] = true;
	}

	private Chunk copy(Chunk actual)
	{
		nmsCopy = new Chunk(actual.world, actual.getPos(), actual.getBiomeIndex());

		for(int i = 0; i < 16; i++)
		{
			rebaseSection(i);
		}

		copyTileEntities(actual, nmsCopy);
		copyBiomes(actual, nmsCopy);
		dumpModifications();

		return nmsCopy;
	}

	private void copyBiomes(Chunk actual, Chunk c)
	{
		for(int i = 0; i < actual.getBiomeIndex().length; i++)
		{
			c.getBiomeIndex()[i] = actual.getBiomeIndex()[i];
		}
	}

	private void copyTileEntities(Chunk actual, Chunk c)
	{
		c.tileEntities.putAll(actual.tileEntities);
	}

	private void dumpModifications()
	{
		Arrays.fill(modified, false);
		biomeModified = false;
	}

	private int getSingularMask(int sec)
	{
		int bitMask = 0;

		for(int section = 0; section < modified.length; section++)
		{
			if(sec == section)
			{
				bitMask += 1 << section;
			}
		}

		return bitMask;
	}
}
