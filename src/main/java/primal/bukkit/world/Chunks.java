package primal.bukkit.world;

import org.bukkit.Chunk;
import org.bukkit.World;

import primal.lang.collection.GList;

/**
 * Chunk utils
 *
 * @author cyberpwn
 */
public class Chunks
{
	/**
	 * Get all loaded chunks in the world
	 *
	 * @param world
	 *            the given world
	 * @return the chunks
	 */
	public static GList<Chunk> getLoadedChunks(World world)
	{
		return new GList<Chunk>(world.getLoadedChunks());
	}

	/**
	 * Get all loaded chunks in all worlds
	 *
	 * @return all loaded chunks
	 */
	public static GList<Chunk> getLoadedChunks()
	{
		GList<Chunk> chunks = new GList<Chunk>();

		for(World i : Worlds.getWorlds())
		{
			chunks.add(getLoadedChunks(i));
		}

		return chunks;
	}

	/**
	 * Get chunks in a radius
	 *
	 * @param center
	 *            the center chunk
	 * @param radius
	 *            the radius
	 * @return the chunks
	 */
	public static GList<Chunk> getRadius(Chunk center, int radius)
	{
		return W.chunkRadius(center, radius);
	}

	/**
	 * Unload the given chunk
	 *
	 * @param chunk
	 *            the chunk
	 * @param save
	 *            should this chunk be saved
	 * @param safe
	 *            if set to true, the chunk will not be unloaded if players are
	 *            nearby
	 * @return true if the chunk unloaded
	 */
	@SuppressWarnings("deprecation")
	public static boolean unload(Chunk chunk, boolean save, boolean safe)
	{
		return chunk.unload(save, safe);
	}

	/**
	 * Unload the given chunk
	 *
	 * @param chunk
	 *            the chunk
	 * @param save
	 *            should this chunk be saved?
	 * @return returns true if the chunk unloaded
	 */
	public static boolean unload(Chunk chunk, boolean save)
	{
		return unload(chunk, save, true);
	}

	/**
	 * Unload the given chunk
	 *
	 * @param chunk
	 *            the chunk
	 * @return returns true if the chunk unloaded
	 */
	public static boolean unload(Chunk chunk)
	{
		return unload(chunk, true, true);
	}

	/**
	 * Unload all given chunks
	 *
	 * @param chunks
	 *            the chunks to unload
	 * @param save
	 *            should these chunks be saved?
	 * @param safe
	 *            should we ignore chunks with players nearby?
	 * @return the amount of chunks unloaded
	 */
	public static int unload(GList<Chunk> chunks, boolean save, boolean safe)
	{
		int unloaded = 0;

		for(Chunk i : chunks)
		{
			if(unload(i, save, safe))
			{
				unloaded++;
			}
		}

		return unloaded;
	}

	/**
	 * Unload all given chunks
	 *
	 * @param chunks
	 *            the chunks to unload
	 * @param save
	 *            should these chunks be saved?
	 * @return the amount of chunks unloaded
	 */
	public static int unload(GList<Chunk> chunks, boolean save)
	{
		return unload(chunks, save, true);
	}

	/**
	 * Unload all given chunks
	 *
	 * @param chunks
	 *            the chunks to unload
	 * @return the amount of chunks unloaded
	 */
	public static int unload(GList<Chunk> chunks)
	{
		return unload(chunks, true, true);
	}

	/**
	 * Is the given chunk loaded?
	 *
	 * @param world
	 *            the world
	 * @param x
	 *            the x
	 * @param z
	 *            the z
	 * @return true if it is
	 */
	public static boolean isLoaded(String world, int x, int z)
	{
		return isLoaded(Worlds.getWorld(world), x, z);
	}

	/**
	 * Is the given chunk loaded?
	 *
	 * @param world
	 *            the world
	 * @param x
	 *            the x
	 * @param z
	 *            the z
	 * @return true if it is
	 */
	public static boolean isLoaded(World world, int x, int z)
	{
		for(Chunk i : getLoadedChunks(world))
		{
			if(i.getX() == x && i.getZ() == z)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Load all chunks
	 *
	 * @param chunks
	 *            the chunks
	 */
	public static void load(GList<Chunk> chunks)
	{
		for(Chunk i : chunks)
		{
			i.load();
		}
	}
}
