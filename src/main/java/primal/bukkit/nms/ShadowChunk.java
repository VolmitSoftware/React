package primal.bukkit.nms;

import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Represents a shadow of an actual chunk. Modifications are tracked, and used
 * for converting to fake-chunk packets. Supports skylight, blocklight, biomes,
 * and block changes
 *
 * @author cyberpwn
 *
 */
public interface ShadowChunk
{
	/**
	 * Check if the next flush is a full chunk section or not
	 *
	 * @return true if the next chunk packet will be a full send
	 */
	public boolean isFullModification();

	/**
	 * Rebase the shadow of the real chunk, essentially syncing this shadow to the
	 * real chunk. This dumps any non-flushed modifications
	 */
	public void rebase();

	/**
	 * Rebase the shadow section from the real chunk section.
	 *
	 * @param section
	 *            the section to rebase
	 */
	public void rebaseSection(int section);

	/**
	 * Queue the given chunk section for resending
	 */
	public void queueSection(int section);

	/**
	 * Queue biomes for resending
	 */
	public void queueBiomes();

	/**
	 * Get the source chunk
	 *
	 * @return the source this shadow is... shadowing
	 */
	public Chunk getSource();

	/**
	 * Flush all pending modifications into packets to send
	 *
	 * @return the packets to send
	 */
	public List<Object> flush();

	/**
	 * Flushes only the given section
	 *
	 * @param section
	 *            the section to flush.
	 * @return returns the section to flush. If the chunk has to send a full column,
	 *         it will return a full flush instead. If nothing needs to be flushed
	 *         in that section, an empty list is returned.
	 */
	public List<Object> flushSection(int section);

	/**
	 * Get the bitmask of the entire chunk.
	 *
	 * Due to performance costs, this bitmask may reflect non-flushed (pending)
	 * modifications if a modification creates a new chunk section.
	 *
	 * @return the bitmask
	 */
	public int getEntireMask();

	/**
	 * Get the bitmask of the non-flushed modified sections
	 *
	 * @return the modified bitmask
	 */
	public int getModifiedMask();

	/**
	 * Set skylight
	 *
	 * Does nothing if this chunk doesnt have a sky (dimension is nether or end)
	 *
	 * @param x
	 *            chunk relative x
	 * @param y
	 *            chunk relative y
	 * @param z
	 *            chunk relative z
	 * @param light
	 *            the light level
	 */
	public void setSkyLight(int x, int y, int z, int light);

	/**
	 * Set blocklight
	 *
	 * @param x
	 *            chunk relative x
	 * @param y
	 *            chunk relative y
	 * @param z
	 *            chunk relative z
	 * @param light
	 *            the light level
	 */
	public void setBlockLight(int x, int y, int z, int light);

	/**
	 * Set the biome
	 *
	 * @param x
	 *            chunk relative x
	 * @param z
	 *            chunk relative z
	 * @param bio
	 *            the biome
	 */
	public void setBiome(int x, int z, Biome bio);

	/**
	 * Set the biome
	 *
	 * @param x
	 *            chunk relative x
	 * @param z
	 *            chunk relative z
	 * @param bio
	 *            the biome id
	 */
	public void setBiome(int x, int z, int bio);

	/**
	 * Set a block
	 *
	 * @param x
	 *            chunk relative x
	 * @param y
	 *            chunk relative y
	 * @param z
	 *            chunk relative z
	 * @param type
	 *            the block id
	 * @param data
	 *            the block meta
	 */
	public void setBlock(int x, int y, int z, int type, int data);

	/**
	 * Set a block
	 *
	 * @param x
	 *            chunk relative x
	 * @param y
	 *            chunk relative y
	 * @param z
	 *            chunk relative z
	 * @param material
	 *            the material
	 */
	public void setBlock(int x, int y, int z, Material material, int data);

	/**
	 * Set a block
	 *
	 * @param x
	 *            chunk relative x
	 * @param y
	 *            chunk relative y
	 * @param z
	 *            chunk relative z
	 * @param material
	 *            the material (assumes data = 0)
	 */
	public void setBlock(int x, int y, int z, Material material);

	/**
	 * Set a block
	 *
	 * @param location
	 *            the world relative location (throws exception if outside chunk)
	 * @param material
	 *            the material
	 * @param data
	 *            the data.
	 */
	public void setBlock(Location location, Material material, int data);

	/**
	 * Set a block
	 *
	 * @param location
	 *            the world relative location (throws exception if outside chunk)
	 * @param material
	 *            the material (assumes data = 0)
	 */
	public void setBlock(Location location, Material material);
}
