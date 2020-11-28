package primal.bukkit.nms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ShadowQueue
{
	public final Map<Chunk, ShadowChunk> chunks;
	public final List<Vector> modifiedQueue;
	private final Player p;
	private final World world;

	public ShadowQueue(Player p, World world)
	{
		this.p = p;
		this.world = world;
		chunks = new HashMap<>();
		modifiedQueue = new ArrayList<>();
	}

	public ShadowChunk getChunk(int x, int z)
	{
		Chunk c = world.getChunkAt(x, z);

		if(!chunks.containsKey(c))
		{
			chunks.put(c, NMP.CHUNK.shadow(c));
		}

		return chunks.get(c);
	}

	public PacketBuffer dump()
	{
		PacketBuffer buffer = new PacketBuffer();
		Set<Vector> fullChunks = new HashSet<>();
		Set<Vector> fullChunksPopped = new HashSet<>();
		List<Vector> order = new ArrayList<>();
		Map<Vector, Integer> priority = new HashMap<>();
		Vector section = getPlayerSection();

		for(Vector i : modifiedQueue)
		{
			ShadowChunk c = getChunk(i.getBlockX(), i.getBlockZ());

			if(c.isFullModification())
			{
				fullChunks.add(new Vector(i.getBlockX(), 0, i.getBlockZ()));
			}

			priority.put(i, (int) (3 * i.distanceSquared(section)));
		}

		List<Integer> values = new ArrayList<>(priority.values());
		List<Vector> keys = new ArrayList<>(priority.keySet());
		Collections.sort(values);

		for(int i : values)
		{
			for(Vector j : keys)
			{
				if(priority.get(j) == i)
				{
					order.add(j);
				}
			}
		}

		for(Vector i : order)
		{
			Vector full = new Vector(i.getBlockX(), 0, i.getBlockZ());

			if(fullChunks.contains(i))
			{
				if(!fullChunksPopped.contains(i))
				{
					fullChunksPopped.add(i);
					buffer.q(getChunk(full.getBlockX(), full.getBlockZ()).flush());
				}

				continue;
			}

			buffer.q(getChunk(i.getBlockX(), i.getBlockZ()).flushSection(i.getBlockY()));
		}

		return buffer;
	}

	public void modify(int x, int y, int z)
	{
		modifySection(x >> 4, y >> 4, z >> 4);
	}

	public void modifySection(int x, int y, int z)
	{
		modifiedQueue.add(new Vector(x, y, z));
	}

	public void rebaseSection(int x, int y, int z)
	{
		getChunk(x, z).rebaseSection(y);
		modifySection(x, y, z);
	}

	public void setBlock(int x, int y, int z, int id, int data)
	{
		getChunk(x >> 4, z >> 4).setBlock(x & 15, y, z & 15, id, data);
		modify(x, y, z);
	}

	public void setSkyLight(int x, int y, int z, int level)
	{
		getChunk(x >> 4, z >> 4).setSkyLight(x & 15, y, z & 15, level);
		modify(x, y, z);
	}

	public void setBlockLight(int x, int y, int z, int level)
	{
		getChunk(x >> 4, z >> 4).setBlockLight(x & 15, y, z & 15, level);
		modify(x, y, z);
	}

	public void setBiome(int x, int z, Biome biome)
	{
		getChunk(x >> 4, z >> 4).setBiome(x & 15, z & 15, biome);
	}

	public Vector getPlayerSection()
	{
		return new Vector(p.getLocation().getBlockX() >> 4, p.getLocation().getBlockY() >> 4, p.getLocation().getBlockZ() >> 4);
	}
}
